[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('baseline', 'startup', 'aot', 'aot-quick', 'combined', 'combined-baseline')][string]$Mode,
    [Parameter(Mandatory)][string]$ExpectedImage,
    [Parameter(Mandatory)][ValidatePattern('^[a-z][a-z0-9-]{0,62}$')][string]$ExpectedRevision,
    [ValidatePattern('^[a-z][a-z0-9-]{4,28}[a-z0-9]$')][string]$ProjectId = 'plimap',
    [ValidatePattern('^[a-z]+-[a-z]+[0-9]$')][string]$Region = 'asia-northeast3',
    [ValidatePattern('^[a-z][a-z0-9-]{0,27}$')][string]$ServiceName = 'plimap-api-prod',
    [Parameter(Mandatory)][string]$ResultPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'startup-experiment.ps1')
$Mode = $Mode.ToLowerInvariant()
$approved = "$Region-docker.pkg.dev/$ProjectId/plimap-docker/api@sha256:"
if ($ExpectedImage -notmatch "^$([regex]::Escape($approved))[0-9a-f]{64}$") { throw 'ExpectedImage must be an approved immutable runtime digest.' }
if (Test-Path -LiteralPath $ResultPath) { throw 'Refusing to overwrite experiment evidence.' }
$settings = Get-StartupExperimentSettings -Mode $Mode
$result = [ordered]@{ status = 'failed'; mode = $Mode; image = $ExpectedImage; previousRevision = $ExpectedRevision; candidateRevision = ''; previousMode = ''; deploymentStartupExcluded = $true; rollback = 'not-required'; error = '' }
$promotionAttempted = $false

function Invoke-ExperimentGcloud {
    param([string[]]$Arguments)
    $output = @(& gcloud @Arguments)
    if ($LASTEXITCODE -ne 0) { throw "gcloud $($Arguments[0..2] -join ' ') failed." }
    return ($output -join "`n").Trim()
}

function Get-ExperimentService {
    return (Invoke-ExperimentGcloud @('run', 'services', 'describe', $ServiceName, "--project=$ProjectId", "--region=$Region", '--format=json') | ConvertFrom-Json)
}

function Get-ExperimentFlag {
    param($Service, [string]$Name)
    $values = @($Service.spec.template.spec.containers[0].env | Where-Object name -eq $Name)
    if ($values.Count -ne 1 -or $values[0].PSObject.Properties.Name -notcontains 'value') { throw "Required plain flag is missing: $Name" }
    return [string]$values[0].value
}

function Test-ExperimentProbe {
    param($Probe, [string]$Policy)
    $p = Get-StartupProbePolicy -Policy $Policy
    $delay = if ($Probe.PSObject.Properties.Name -contains 'initialDelaySeconds') { [int]$Probe.initialDelaySeconds } else { 0 }
    return ($Probe.httpGet.path -eq $p.path -and $Probe.httpGet.port -eq $p.port -and $delay -eq 0 -and
        $Probe.timeoutSeconds -eq $p.timeoutSeconds -and $Probe.periodSeconds -eq $p.periodSeconds -and $Probe.failureThreshold -eq $p.failureThreshold)
}

function Get-ExperimentMode {
    param($Service)
    if ((Get-ExperimentFlag $Service 'PLIMAP_STARTUP_MONITORING_ENABLED') -ne 'true') { throw 'Experiments require startup monitoring ON.' }
    $cds = Get-ExperimentFlag $Service 'PLIMAP_APP_CDS_ENABLED'
    $aot = Get-ExperimentFlag $Service 'PLIMAP_SPRING_AOT_ENABLED'
    $probe = $Service.spec.template.spec.containers[0].startupProbe
    if ($cds -eq 'true' -and $aot -eq 'false' -and (Test-ExperimentProbe $probe 'baseline')) { return 'appcds' }
    foreach ($name in @('baseline', 'startup', 'aot', 'aot-quick', 'combined', 'combined-baseline')) {
        $expected = Get-StartupExperimentSettings $name
        if ($cds -eq $expected.appCdsEnabled -and $aot -eq $expected.springAotEnabled -and
            (Test-ExperimentProbe $probe $expected.startupProbePolicy)) { return $name }
    }
    throw 'Current configuration is not a recognized experiment mode.'
}

function Get-OtherRuntimeSpec {
    param($Service)
    # Compare in memory. Never write arbitrary runtime environment values or credentials.
    $spec = $Service.spec.template.spec | ConvertTo-Json -Depth 30 | ConvertFrom-Json
    $spec.containers[0].PSObject.Properties.Remove('startupProbe')
    $spec.containers[0].env = @($spec.containers[0].env | Where-Object name -notin @('PLIMAP_SPRING_AOT_ENABLED', 'PLIMAP_APP_CDS_ENABLED') | Sort-Object name)
    return ($spec | ConvertTo-Json -Depth 30 -Compress)
}

function Get-OtherRuntimeAnnotations {
    param($Service)
    $map = [ordered]@{}
    foreach ($p in ($Service.spec.template.metadata.annotations.PSObject.Properties | Sort-Object Name)) {
        if ($p.Name -in @('run.googleapis.com/client-name', 'run.googleapis.com/client-version', 'run.googleapis.com/operation-id')) { continue }
        $map[$p.Name] = if ($p.Name -eq 'run.googleapis.com/network-interfaces') { $p.Value | ConvertFrom-Json | ConvertTo-Json -Compress } else { $p.Value }
    }
    return ($map | ConvertTo-Json -Compress)
}

function Assert-ExperimentTraffic {
    param($Service, [string]$Revision)
    if (@($Service.status.traffic).Count -ne 1 -or $Service.status.traffic[0].revisionName -ne $Revision -or $Service.status.traffic[0].percent -ne 100) { throw 'Expected a single 100 percent revision.' }
    if ($Service.spec.template.spec.containers[0].image -ne $ExpectedImage) { throw 'Runtime image changed; do not mix experiment images.' }
}

try {
    $before = Get-ExperimentService
    Assert-ExperimentTraffic $before $ExpectedRevision
    if ($before.spec.template.metadata.name -ne $ExpectedRevision) { throw 'An unpromoted revision template exists; inspect it before changing configuration.' }
    $currentMode = Get-ExperimentMode $before
    $result.previousMode = $currentMode
    if ($currentMode -ne 'baseline' -and $Mode -ne 'baseline' -and $currentMode -ne $Mode) { throw 'Restore baseline before switching between startup and AOT experiments.' }
    $result.appCdsEnabled = $settings.appCdsEnabled
    $result.springAotEnabled = $settings.springAotEnabled
    $result.startupProbePolicy = $settings.startupProbePolicy
    $result.startupProbe = Get-StartupProbePolicy -Policy $settings.startupProbePolicy
    if ($currentMode -eq $Mode) {
        $result.status = 'unchanged'
        $result.candidateRevision = $ExpectedRevision
    } else {
        $suffix = 'study-' + $Mode + '-' + [datetime]::UtcNow.ToString('yyyyMMddHHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 4)
        $candidate = "$ServiceName-$suffix"
        $result.candidateRevision = $candidate
        Invoke-ExperimentGcloud @('run', 'services', 'update', $ServiceName, "--project=$ProjectId", "--region=$Region",
            "--revision-suffix=$suffix", '--no-traffic', "--update-env-vars=PLIMAP_SPRING_AOT_ENABLED=$($settings.springAotEnabled),PLIMAP_APP_CDS_ENABLED=$($settings.appCdsEnabled)",
            "--startup-probe=$(Get-StartupProbeArgument -Policy $settings.startupProbePolicy)", '--quiet', '--format=none') | Out-Null
        $pending = Get-ExperimentService
        Assert-ExperimentTraffic $pending $ExpectedRevision
        if ($pending.spec.template.metadata.name -ne $candidate -or (Get-ExperimentMode $pending) -ne $Mode) { throw 'Candidate mode verification failed.' }
        if ((Get-OtherRuntimeSpec $before) -cne (Get-OtherRuntimeSpec $pending) -or
            (Get-OtherRuntimeAnnotations $before) -cne (Get-OtherRuntimeAnnotations $pending)) { throw 'Other runtime settings changed; previous traffic retained.' }
        $revision = Invoke-ExperimentGcloud @('run', 'revisions', 'describe', $candidate, "--project=$ProjectId", "--region=$Region", '--format=json') | ConvertFrom-Json
        if ($revision.spec.containers[0].image -ne $ExpectedImage -or @($revision.status.conditions | Where-Object { $_.type -eq 'Ready' -and $_.status -eq 'True' }).Count -ne 1) { throw 'Candidate revision not Ready on the same image.' }
        # A no-traffic revision may be Retired. Promotion performs the normal startup gate.
        $promotionAttempted = $true
        Invoke-ExperimentGcloud @('run', 'services', 'update-traffic', $ServiceName, "--project=$ProjectId", "--region=$Region", "--to-revisions=$candidate=100", '--quiet', '--format=none') | Out-Null
        $after = Get-ExperimentService
        Assert-ExperimentTraffic $after $candidate
        if ((Get-ExperimentMode $after) -ne $Mode -or (Get-OtherRuntimeSpec $before) -cne (Get-OtherRuntimeSpec $after) -or
            (Get-OtherRuntimeAnnotations $before) -cne (Get-OtherRuntimeAnnotations $after)) { throw 'Post-promotion runtime settings mismatch.' }
        $result.status = 'succeeded'
        $result.otherRuntimeSettingsIdentical = $true
    }
} catch {
    $result.error = $_.Exception.Message
    if ($promotionAttempted) {
        try {
            $live = Get-ExperimentService
            if ($live.spec.template.spec.containers[0].image -eq $ExpectedImage -and @($live.status.traffic | Where-Object { $_.revisionName -eq $result.candidateRevision -and $_.percent -gt 0 }).Count -gt 0) {
                Invoke-ExperimentGcloud @('run', 'services', 'update-traffic', $ServiceName, "--project=$ProjectId", "--region=$Region", "--to-revisions=$ExpectedRevision=100", '--quiet', '--format=none') | Out-Null
                Assert-ExperimentTraffic (Get-ExperimentService) $ExpectedRevision
                $result.rollback = 'succeeded'
            } else { $result.rollback = 'skipped-concurrent-state-change' }
        } catch { $result.rollback = 'failed'; Write-Warning 'Could not restore previous revision; inspect live traffic.' }
    }
    throw
} finally {
    $result.recordedAtUtc = [datetime]::UtcNow.ToString('o')
    $path = [IO.Path]::GetFullPath($ResultPath)
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path)) | Out-Null
    [IO.File]::WriteAllText($path, ($result | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
}
Write-Output "Experiment ${Mode}: $($result.status); image retained; deployment startup excluded from natural cold samples."

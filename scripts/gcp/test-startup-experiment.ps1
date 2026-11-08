$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'startup-experiment.ps1')

function Assert-ExperimentEqual {
    param($Actual, $Expected, [string]$Message)
    if ($Actual -ne $Expected) { throw "$Message (expected=$Expected, actual=$Actual)" }
}

foreach ($policy in @('baseline', 'short')) {
    $p = Get-StartupProbePolicy $policy
    Assert-ExperimentEqual ($p.periodSeconds * $p.failureThreshold) 120 'Startup budget must stay 120 seconds'
    Assert-ExperimentEqual $p.timeoutSeconds 3 'Per-check timeout must remain unchanged'
    Assert-ExperimentEqual $p.path '/actuator/health' 'Health semantics must remain unchanged'
}
try { Assert-StartupExperimentCombination 'true' 'true' 'baseline'; throw 'Missing rejection' }
catch { if ($_.Exception.Message -notmatch 'requires AppCDS OFF') { throw } }
try { Assert-StartupExperimentCombination 'false' 'true' 'short'; throw 'Missing rejection' }
catch { if ($_.Exception.Message -notmatch 'baseline startup') { throw } }
try { Assert-StartupExperimentCombination 'true' 'false' 'short'; throw 'Missing rejection' }
catch { if ($_.Exception.Message -notmatch 'requires AppCDS OFF') { throw } }

function New-ExperimentFixtureState {
    $f = $global:PlimapStartupFixture
    $policy = Get-StartupProbePolicy $f.policy
    $envVars = @(
        @{ name = 'PLIMAP_APP_CDS_ENABLED'; value = 'false' },
        @{ name = 'PLIMAP_STARTUP_MONITORING_ENABLED'; value = 'true' },
        @{ name = 'DB_PASSWORD'; valueFrom = @{ secretKeyRef = @{ name = 'secret-reference-only'; key = '1' } } }
    )
    if ($f.scenario -ne 'missing-flag') { $envVars += @{ name = 'PLIMAP_SPRING_AOT_ENABLED'; value = $f.aot } }
    $memory = if ($f.scenario -eq 'other-setting-change' -and $f.template -ne 'previous-revision') { '2Gi' } else { '1Gi' }
    $image = $f.image
    $active = $f.active
    if ($f.scenario -eq 'concurrent-change' -and $f.promoted) { $active = 'concurrent-revision'; $image = $image.Replace('a' * 64, 'b' * 64) }
    return @{
        spec = @{ template = @{
            metadata = @{ name = $f.template; annotations = @{ 'run.googleapis.com/execution-environment' = 'gen2'; 'run.googleapis.com/network-interfaces' = '[{"network":"test-vpc","subnetwork":"test-subnet"}]' } }
            spec = @{ containerConcurrency = 40; timeoutSeconds = 60; serviceAccountName = 'test-runtime'; containers = @(@{
                image = $image; env = $envVars; resources = @{ limits = @{ cpu = '1'; memory = $memory } }
                startupProbe = @{ httpGet = @{ path = $policy.path; port = $policy.port }; timeoutSeconds = $policy.timeoutSeconds; periodSeconds = $policy.periodSeconds; failureThreshold = $policy.failureThreshold }
                readinessProbe = @{ httpGet = @{ path = '/actuator/health/readiness'; port = 8080 }; periodSeconds = 5; successThreshold = 2 }
            }) }
        } }
        status = @{ traffic = @(@{ revisionName = $active; percent = 100 }) }
    }
}

function gcloud {
    $arguments = @($args)
    $command = $arguments -join ' '
    $global:LASTEXITCODE = 0
    $f = $global:PlimapStartupFixture
    $f.commands.Add($command)
    switch -Regex ($command) {
        '^run services describe ' { return (New-ExperimentFixtureState | ConvertTo-Json -Depth 20 -Compress) }
        '^run services update ' {
            if ($arguments -notcontains '--no-traffic') { throw 'Must create a no-traffic candidate' }
            if (@($arguments | Where-Object { $_ -like '--image=*' }).Count) { throw 'Must not rebuild or change the image' }
            $f.template = 'plimap-api-prod-' + ($arguments | Where-Object { $_ -like '--revision-suffix=*' }).Substring(18)
            $f.aot = (($arguments | Where-Object { $_ -like '--update-env-vars=*' }) -split '=')[-1]
            $probe = $arguments | Where-Object { $_ -like '--startup-probe=*' }
            $f.policy = if ($probe -match 'periodSeconds=4,failureThreshold=30') { 'short' } else { 'baseline' }
            return
        }
        '^run revisions describe ' {
            $ready = if ($f.scenario -eq 'not-ready') { 'False' } else { 'True' }
            return (@{ spec = @{ containers = @(@{ image = $f.image }) }; status = @{ conditions = @(@{ type = 'Ready'; status = $ready; reason = 'Retired' }) } } | ConvertTo-Json -Depth 8 -Compress)
        }
        '^run services update-traffic ' {
            $f.active = (($arguments | Where-Object { $_ -like '--to-revisions=*' }).Substring(15) -split '=')[0]
            if ($f.active -eq $f.template) {
                $f.promoted = $true
                if ($f.scenario -in @('promotion-failure', 'concurrent-change')) { $global:LASTEXITCODE = 1 }
            }
            return
        }
        default { throw "Unexpected cloud call in offline test: $command" }
    }
}
function Invoke-WebRequest { throw 'HTTP must not be called by the offline configuration tests.' }

$directory = [IO.Directory]::CreateDirectory((Join-Path $PSScriptRoot "../../build/startup-script-validation-$([guid]::NewGuid().ToString('N'))")).FullName
$image = 'asia-northeast3-docker.pkg.dev/plimap/plimap-docker/api@sha256:' + ('a' * 64)
$cases = @('startup', 'aot', 'restore-startup', 'restore-aot', 'unchanged', 'stale-revision', 'changed-image', 'other-setting-change', 'not-ready', 'missing-flag', 'wrong-transition', 'promotion-failure', 'concurrent-change')
try {
    foreach ($scenario in $cases) {
        $global:PlimapStartupFixture = @{
            scenario = $scenario; image = $image; active = 'previous-revision'; template = 'previous-revision';
            aot = 'false'; policy = 'baseline'; promoted = $false; commands = [Collections.Generic.List[string]]::new()
        }
        $f = $global:PlimapStartupFixture
        $mode = if ($scenario -eq 'aot') { 'aot' } elseif ($scenario -in @('restore-startup', 'restore-aot', 'unchanged')) { 'baseline' } else { 'startup' }
        if ($scenario -in @('restore-startup', 'wrong-transition')) { $f.policy = 'short' }
        if ($scenario -eq 'restore-aot') { $f.aot = 'true' }
        if ($scenario -eq 'wrong-transition') { $mode = 'aot' }
        if ($scenario -eq 'changed-image') { $f.image = $image.Replace('a' * 64, 'b' * 64) }
        $revision = if ($scenario -eq 'stale-revision') { 'stale-revision' } else { 'previous-revision' }
        $path = Join-Path $directory "$scenario.json"
        $failure = $null
        try { & (Join-Path $PSScriptRoot 'set-startup-experiment.ps1') -Mode $mode -ExpectedImage $image -ExpectedRevision $revision -ResultPath $path }
        catch { $failure = $_.Exception.Message }
        $result = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
        if ($scenario -in @('startup', 'aot', 'restore-startup', 'restore-aot', 'unchanged')) {
            if ($failure) { throw $failure }
            Assert-ExperimentEqual $result.status $(if ($scenario -eq 'unchanged') { 'unchanged' } else { 'succeeded' }) "Successful $scenario"
            Assert-ExperimentEqual $f.aot $(if ($scenario -eq 'aot') { 'true' } else { 'false' }) 'AOT mode'
            Assert-ExperimentEqual $f.policy $(if ($scenario -eq 'startup') { 'short' } else { 'baseline' }) 'Probe policy'
            if ($scenario -eq 'unchanged') { Assert-ExperimentEqual $f.commands.Count 1 'Idempotent mode must not create another revision' }
            Assert-ExperimentEqual $result.deploymentStartupExcluded $true 'Deployment start must not become a natural cold sample'
        } else {
            if (-not $failure) { throw "Expected rejection: $scenario" }
            $pattern = switch ($scenario) {
                'stale-revision' { '100 percent revision' }; 'changed-image' { 'Runtime image changed' }
                'other-setting-change' { 'Other runtime settings changed' }; 'not-ready' { 'not Ready' }
                'missing-flag' { 'Required plain flag' }; 'wrong-transition' { 'Restore baseline' }
                default { 'gcloud run services update-traffic failed' }
            }
            if ($failure -notmatch $pattern) { throw "Unexpected rejection in ${scenario}: $failure" }
            if ($scenario -eq 'concurrent-change') {
                Assert-ExperimentEqual $result.rollback 'skipped-concurrent-state-change' 'Do not overwrite concurrent operations'
            } else {
                Assert-ExperimentEqual $f.active 'previous-revision' 'Previous traffic must remain or be restored'
            }
            if ($scenario -eq 'promotion-failure') { Assert-ExperimentEqual $result.rollback 'succeeded' 'Restore traffic after a failed promotion' }
        }
        $serialized = Get-Content -LiteralPath $path -Raw
        if ($serialized -match 'secret-reference-only|DB_PASSWORD') { throw 'Arbitrary environment data leaked into evidence.' }
        Write-Output "Passed startup experiment scenario: $scenario"
    }
} finally { Remove-Variable -Name PlimapStartupFixture -Scope Global }
Write-Output 'Startup experiment policies and offline configuration transitions passed.'

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "container-image.ps1")
. (Join-Path $PSScriptRoot "startup-experiment.ps1")

function Assert-Equal {
    param($Actual, $Expected, [string]$Message)
    if ($Actual -ne $Expected) { throw "$Message (expected=$Expected, actual=$Actual)" }
}

function Assert-Rejected {
    param([scriptblock]$Action, [string]$Pattern)
    try { & $Action } catch {
        if ($_.Exception.Message -notmatch $Pattern) { throw }
        return
    }
    throw "Expected rejection matching: $Pattern"
}

$repository = "asia-northeast3-docker.pkg.dev/plimap/plimap-docker/api"
$indexDigest = "sha256:" + ("a" * 64)
$runtimeDigest = "sha256:" + ("b" * 64)
$indexImage = "$repository@$indexDigest"
$runtimeImage = "$repository@$runtimeDigest"
$imageType = "application/vnd.oci.image.manifest.v1+json"
$index = @{
    schemaVersion = 2
    mediaType = "application/vnd.oci.image.index.v1+json"
    manifests = @(
        @{ mediaType = $imageType; digest = $runtimeDigest; platform = @{ os = "linux"; architecture = "amd64" } },
        @{ mediaType = $imageType; digest = "sha256:" + ("c" * 64); platform = @{ os = "unknown"; architecture = "unknown" } }
    )
}

# Given an index including an attestation, select only its runtime descriptor.
Assert-Equal (Get-LinuxAmd64ImageFromManifest -Image $indexImage -Manifest $index) $runtimeImage "OCI runtime selection"
$index.mediaType = "application/vnd.docker.distribution.manifest.list.v2+json"
Assert-Equal (Get-LinuxAmd64ImageFromManifest -Image $indexImage -Manifest $index) $runtimeImage "Docker manifest list selection"
foreach ($type in @($imageType, "application/vnd.docker.distribution.manifest.v2+json")) {
    Assert-Equal (Get-LinuxAmd64ImageFromManifest -Image $runtimeImage -Manifest @{ schemaVersion = 2; mediaType = $type }) $runtimeImage "Single manifest compatibility"
}
$runtime = $index.manifests[0]
$index.manifests = @($index.manifests[1])
Assert-Rejected { Get-LinuxAmd64ImageFromManifest -Image $indexImage -Manifest $index } "exactly one"
$runtime.platform.architecture = "arm64"
$index.manifests = @($runtime)
Assert-Rejected { Get-LinuxAmd64ImageFromManifest -Image $indexImage -Manifest $index } "exactly one"
$runtime.platform.architecture = "amd64"
$index.manifests = @($runtime, $runtime)
Assert-Rejected { Get-LinuxAmd64ImageFromManifest -Image $indexImage -Manifest $index } "exactly one"
$index.manifests = @($runtime)
$runtime.digest = "sha256:invalid"
Assert-Rejected { Get-LinuxAmd64ImageFromManifest -Image $indexImage -Manifest $index } "Invalid linux"
$runtime.digest = $runtimeDigest
$runtime.mediaType = "application/vnd.oci.image.index.v1+json"
Assert-Rejected { Get-LinuxAmd64ImageFromManifest -Image $indexImage -Manifest $index } "Invalid linux"
$runtime.mediaType = $imageType
Assert-Rejected { Get-LinuxAmd64ImageFromManifest -Image "$repository`:latest" -Manifest $index } "pinned"
Assert-Rejected { Get-LinuxAmd64ImageFromManifest -Image $indexImage -Manifest @{schemaVersion=2;mediaType="unsupported"} } "Unsupported"
Assert-Rejected { Get-LinuxAmd64ImageFromManifest -Image $indexImage -Manifest @{schemaVersion=1;mediaType=$imageType} } "schema"
$index.mediaType = "application/vnd.oci.image.index.v1+json"

# Run the actual deployment entry point against fail-closed CLI doubles. No cloud or HTTP calls.
function gcloud {
    $arguments = @($args)
    $command = $arguments -join " "
    $global:LASTEXITCODE = 0
    $fixture = $global:PlimapDeploymentFixture
    $fixture.commands.Add($command)
    switch -Regex ($command) {
        '^artifacts docker images describe ' { return $fixture.indexDigest }
        '^iam service-accounts describe |^compute networks describe |^compute networks subnets describe |^storage buckets describe ' { return }
        '^secrets versions describe ' { return '{"state":"ENABLED","name":"projects/test/secrets/test/versions/1"}' }
        '^run services get-iam-policy ' { return '{"bindings":[{"role":"roles/run.invoker","members":["allUsers"]}]}' }
        '^run services describe ' {
            $ingress = "internal-and-cloud-load-balancing"
            if ($fixture.scenario -eq "post-promotion-failure" -and $fixture.active -eq $fixture.candidate) {
                $ingress = "all"
            }
            return (@{
                metadata = @{ annotations = @{ "run.googleapis.com/ingress" = $ingress; "run.googleapis.com/default-url-disabled" = "true" } }
                status = @{ latestReadyRevisionName = $fixture.candidate; traffic = @(@{revisionName=$fixture.active;percent=$fixture.percent}) }
            } | ConvertTo-Json -Depth 6 -Compress)
        }
        '^run deploy ' {
            if ($arguments -notcontains "--no-traffic") { throw "Candidate must start with no traffic" }
            $fixture.deployedImage = ($arguments | Where-Object { $_ -like '--image=*' }).Substring(8)
            $envFile = ($arguments | Where-Object { $_ -like '--env-vars-file=*' }).Substring(16)
            $fixture.environmentText = Get-Content -LiteralPath $envFile -Raw
            $suffix = ($arguments | Where-Object { $_ -like '--revision-suffix=*' }).Substring(18)
            $fixture.candidate = "plimap-api-prod-$suffix"
            return
        }
        '^run revisions describe ' {
            $actual = if ($fixture.scenario -eq "image-mismatch") { $fixture.indexImage } else { $fixture.runtimeImage }
            $ready = if ($fixture.scenario -eq "not-ready") { "False" } else { "True" }
            return (@{spec=@{containers=@(@{image=$actual})};status=@{conditions=@(@{type="Ready";status=$ready})}} | ConvertTo-Json -Depth 6 -Compress)
        }
        '^run services update-traffic ' {
            $allocation = ($arguments | Where-Object { $_ -like '--to-revisions=*' }).Substring(15)
            $fixture.active = ($allocation -split '=')[0]
            $fixture.percent = if ($fixture.scenario -eq "traffic-not-converged" -and $fixture.active -eq $fixture.candidate) { 50 } else { 100 }
            if ($fixture.scenario -eq "promotion-command-failure" -and $fixture.active -eq $fixture.candidate) {
                $global:LASTEXITCODE = 1
            }
            return
        }
        default { throw "Unexpected gcloud call in offline deployment test: $command" }
    }
}

function docker {
    if (($args -join " ") -notlike 'buildx imagetools inspect --raw *') {
        throw "Unexpected Docker call in offline deployment test."
    }
    if ($global:PlimapDeploymentFixture.scenario -eq "registry-failure") {
        $global:LASTEXITCODE = 1
        return
    }
    $global:LASTEXITCODE = 0
    return $global:PlimapDeploymentFixture.manifestJson
}

function Start-Sleep { param($Seconds) }
function Invoke-WebRequest { throw "HTTP must not be called by this offline deployment test." }

$deployPath = Join-Path $PSScriptRoot "deploy-prod.ps1"
$testDirectory = Join-Path ([IO.Path]::GetTempPath()) ([guid]::NewGuid().ToString())
[IO.Directory]::CreateDirectory($testDirectory) | Out-Null
try {
    foreach ($scenario in @("success-index", "success-runtime", "success-single", "success-appcds-off", "success-instrumented", "success-uppercase", "success-aot", "success-startup", "success-aot-quick", "success-combined", "wrong-input", "wrong-repository", "registry-failure", "image-mismatch", "not-ready", "traffic-not-converged", "promotion-command-failure", "post-promotion-failure")) {
        $global:PlimapDeploymentFixture = @{
            scenario = $scenario
            indexDigest = $indexDigest
            indexImage = $indexImage
            runtimeImage = $runtimeImage
            manifestJson = $index | ConvertTo-Json -Depth 6 -Compress
            active = "previous-revision"
            percent = 100
            candidate = ""
            deployedImage = ""
            commands = [Collections.Generic.List[string]]::new()
        }
        $fixture = $global:PlimapDeploymentFixture
        if ($scenario -eq "success-single") {
            $fixture.indexDigest = $runtimeDigest
            $fixture.indexImage = $runtimeImage
            $fixture.manifestJson = @{schemaVersion=2;mediaType=$imageType} | ConvertTo-Json -Compress
        }
        $inputImage = switch ($scenario) {
            "success-runtime" { $runtimeImage }
            "success-single" { $runtimeImage }
            "wrong-input" { "$repository@sha256:" + ("d" * 64) }
            "wrong-repository" { "unapproved.example/api@$indexDigest" }
            default { $indexImage }
        }
        $resultPath = Join-Path $testDirectory "$scenario.json"
        $appCdsEnabled = if ($scenario -in @("success-appcds-off", "success-aot", "success-startup", "success-aot-quick")) { "false" } else { "true" }
        $springAotEnabled = if ($scenario -in @('success-aot', 'success-aot-quick', 'success-combined')) { 'true' } else { 'false' }
        $probePolicy = if ($scenario -eq 'success-startup') { 'short' } elseif ($scenario -in @('success-aot-quick', 'success-combined')) { 'quick' } else { 'baseline' }
        $startupMonitoringEnabled = if ($scenario -in @("success-instrumented", "success-uppercase")) { "true" } else { "false" }
        $appCdsInput = if ($scenario -eq "success-uppercase") { "TRUE" } else { $appCdsEnabled }
        $startupMonitoringInput = if ($scenario -eq "success-uppercase") { "TRUE" } else { $startupMonitoringEnabled }
        $failure = $null
        try {
            & $deployPath -Image $inputImage -DeployCommit ("e" * 40) `
                -ProfileImageBucket "test-profile-images" -VpcNetwork "test-vpc" -VpcSubnet "test-subnet" `
                -AppCdsEnabled $appCdsInput -StartupMonitoringEnabled $startupMonitoringInput `
                -SpringAotEnabled $springAotEnabled.ToUpperInvariant() -StartupProbePolicy $probePolicy.ToUpperInvariant() `
                -ResultPath $resultPath
        } catch { $failure = $_.Exception.Message }
        $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
        if ($scenario -like 'success-*') {
            if ($failure) { throw $failure }
            Assert-Equal $result.status "succeeded" "Successful deployment"
            Assert-Equal $fixture.deployedImage $runtimeImage "Deploy must use the child digest"
            Assert-Equal $result.approvedSourceImage $fixture.indexImage "Source manifest audit record"
            Assert-Equal $fixture.active $fixture.candidate "Traffic promotion"
            Assert-Equal $result.appCdsEnabled $appCdsEnabled "AppCDS audit record"
            Assert-Equal $result.startupMonitoringEnabled $startupMonitoringEnabled "Monitoring audit record"
            Assert-Equal $result.springAotEnabled $springAotEnabled 'AOT audit record'
            Assert-Equal $result.startupProbePolicy $probePolicy 'Probe audit record'
            Assert-Equal ($result.startupProbe.periodSeconds * $result.startupProbe.failureThreshold) 120 'Startup timeout budget'
            $probeArgument = '--startup-probe=' + (Get-StartupProbeArgument $probePolicy)
            if (-not ($fixture.commands | Where-Object { $_.Contains($probeArgument) })) { throw 'Selected probe must reach gcloud.' }
            if (-not $fixture.environmentText.Contains("PLIMAP_APP_CDS_ENABLED: '$appCdsEnabled'") -or
                -not $fixture.environmentText.Contains("PLIMAP_STARTUP_MONITORING_ENABLED: '$startupMonitoringEnabled'") -or
                -not $fixture.environmentText.Contains("PLIMAP_SPRING_AOT_ENABLED: '$springAotEnabled'")) {
                throw "AppCDS/monitoring selection must survive env-vars-file replacement."
            }
        } else {
            if (-not $failure) { throw "Expected failure in $scenario" }
            Assert-Equal $fixture.active "previous-revision" "Previous traffic must remain or be restored"
            switch ($scenario) {
                "wrong-input" {
                    if ($failure -notmatch 'does not match') { throw $failure }
                    Assert-Equal $fixture.deployedImage "" "Invalid image must fail before deploy"
                }
                "wrong-repository" {
                    if ($failure -notmatch 'approved Artifact Registry') { throw $failure }
                    Assert-Equal $fixture.commands.Count 0 "Unapproved repository must fail before registry access"
                }
                "registry-failure" {
                    if ($failure -notmatch 'Could not inspect') { throw $failure }
                    Assert-Equal $fixture.deployedImage "" "Registry failure must fail before deploy"
                }
                "image-mismatch" {
                    if ($failure -notmatch 'candidate image mismatch') { throw $failure }
                    Assert-Equal $result.rollback "not-required" "No promotion on image mismatch"
                }
                "not-ready" {
                    if ($failure -notmatch 'did not become Ready') { throw $failure }
                    Assert-Equal $result.rollback "not-required" "No promotion before Ready"
                }
                "post-promotion-failure" {
                    if ($failure -notmatch 'ingress') { throw $failure }
                    Assert-Equal $result.rollback "succeeded" "Rollback after service verification failure"
                }
                "traffic-not-converged" {
                    if ($failure -notmatch 'did not converge') { throw $failure }
                    Assert-Equal $result.rollback "succeeded" "Rollback after traffic convergence timeout"
                }
                "promotion-command-failure" {
                    if ($failure -notmatch 'gcloud command failed') { throw $failure }
                    Assert-Equal $result.rollback "succeeded" "Rollback if the CLI failed after applying traffic"
                }
            }
        }
        Write-Output "Passed deployment scenario: $scenario"
    }
} finally {
    # Delete only the known files created by this test, never recursively remove a computed path.
    Get-ChildItem -LiteralPath $testDirectory -File | Remove-Item
    Remove-Item -LiteralPath $testDirectory
    Remove-Variable -Name PlimapDeploymentFixture -Scope Global
}
Write-Output "Prod image selection and deployment gate tests passed."

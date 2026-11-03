[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Image,
    [string]$ResultDirectory = (Join-Path $PSScriptRoot "../../build/appcds-validation-$([guid]::NewGuid().ToString('N'))")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $false
if (Test-Path -LiteralPath $ResultDirectory) { throw "Refusing to overwrite existing validation evidence." }
$output = [IO.Directory]::CreateDirectory($ResultDirectory).FullName
$taskId = "plimap-appcds-" + [guid]::NewGuid().ToString("N").Substring(0, 12)
$network = $taskId
$containers = [Collections.Generic.List[string]]::new()
$networkCreated = $false
$summary = [ordered]@{ status = "failed"; image = $Image; purpose = "local functional validation, not a Cloud Run latency benchmark"; modes = @() }

function Invoke-Docker {
    param([Parameter(Mandatory)][string[]]$Arguments)
    $lines = @(& docker @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Docker $($Arguments[0]) failed: $($lines -join [Environment]::NewLine)" }
    return ($lines -join [Environment]::NewLine)
}

function Assert-Status {
    param([string]$Container, [string]$Path, [int]$Expected)
    $status = Invoke-Docker -Arguments @("exec", $Container, "curl", "--silent", "--show-error",
        "--output", "/dev/null", "--write-out", "%{http_code}", "--max-time", "10", "http://localhost:8080$Path")
    if ($status -ne [string]$Expected) { throw "Unexpected status for $Path : $status" }
}

try {
    $summary.imageId = Invoke-Docker -Arguments @("image", "inspect", $Image, "--format", "{{.Id}}")
    $summary.runtime = Invoke-Docker -Arguments @("run", "--rm", "--network=none", "--entrypoint=sh", $Image, "-c",
        "test ! -e /tmp/appcds-training.properties && test ! -e /tmp/appcds-training-credentials.json && test ! -e /tmp/application.jar && sha256sum -c /app/application.jsa.sha256 && cat /app/java-version.txt && test `"`$(id -u)`" = 10001")
    foreach ($case in @("invalid-mode", "missing-archive")) {
        $arguments = if ($case -eq "invalid-mode") {
            @("run", "--rm", "--network=none", "-e", "PLIMAP_APP_CDS_ENABLED=invalid", $Image)
        } else {
            @("run", "--rm", "--network=none", "--entrypoint=sh", $Image, "-c", "rm /app/application.jsa && exec /app/entrypoint.sh")
        }
        $failureOutput = @(& docker @arguments 2>&1) -join "`n"
        $expectedExit = if ($case -eq "invalid-mode") { 64 } else { 1 }
        if ($LASTEXITCODE -ne $expectedExit) { throw "$case must reject before application startup." }
        $expectedMessage = if ($case -eq "invalid-mode") { "must be true or false" } else { "missing_archive" }
        if ($failureOutput -notmatch $expectedMessage) { throw "$case did not report its cause." }
    }

    Invoke-Docker -Arguments @("network", "create", "--internal", $network) | Out-Null
    $networkCreated = $true
    $database = "$taskId-db"
    Invoke-Docker -Arguments @("run", "-d", "--name", $database, "--network", $network,
        "-e", "POSTGRES_DB=plimap_appcds", "-e", "POSTGRES_USER=appcds", "-e", "POSTGRES_PASSWORD=appcds-test-only",
        "--tmpfs", "/var/lib/postgresql/data", "postgis/postgis:17-3.5") | Out-Null
    $containers.Add($database)
    $redis = "$taskId-redis"
    Invoke-Docker -Arguments @("run", "-d", "--name", $redis, "--network", $network,
        "--tmpfs", "/data", "redis:7-alpine", "redis-server", "--appendonly", "no") | Out-Null
    $containers.Add($redis)

    $dbReady = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & docker exec $database pg_isready -U appcds -d plimap_appcds *> $null
        if ($LASTEXITCODE -eq 0) { $dbReady = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $dbReady) { throw "Isolated PostGIS did not become ready." }

    foreach ($mode in @("false", "true")) {
        $name = "$taskId-$mode"
        $properties = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "smoke.properties")).Path
        Invoke-Docker -Arguments @("run", "-d", "--name", $name, "--network", $network,
            "--memory=1g", "--cpus=1",
            "--mount", "type=bind,source=$properties,target=/tmp/appcds-smoke.properties,readonly",
            "-e", "PLIMAP_APP_CDS_ENABLED=$mode", "-e", "JAVA_TOOL_OPTIONS=-Xlog:class+load=info:file=/tmp/class-load.log",
            $Image, "--spring.profiles.active=test", "--spring.config.additional-location=file:/tmp/appcds-smoke.properties",
            "--spring.datasource.url=jdbc:postgresql://${database}:5432/plimap_appcds", "--spring.datasource.username=appcds",
            "--spring.datasource.password=appcds-test-only", "--spring.data.redis.host=$redis", "--spring.data.redis.port=6379") | Out-Null
        $containers.Add($name)
        $ready = $false
        for ($attempt = 0; $attempt -lt 120; $attempt++) {
            # The readiness port is expected to be absent during this new JVM's startup.
            # Execute inside the container: no host port or Docker socket is exposed.
            $health = @(& docker exec $name curl --silent --output /dev/null --write-out '%{http_code}' --connect-timeout 1 --max-time 2 http://localhost:8080/actuator/health/readiness 2>$null) -join ""
            if ($LASTEXITCODE -eq 0 -and $health -eq "200") { $ready = $true; break }
            $running = Invoke-Docker -Arguments @("inspect", $name, "--format", "{{.State.Running}}")
            if ($running -ne "true") { break }
            Start-Sleep -Seconds 1
        }
        $appLog = Invoke-Docker -Arguments @("logs", $name)
        [IO.File]::WriteAllText((Join-Path $output "$mode-application.log"), $appLog)
        if (-not $ready) { throw "Local application mode=$mode did not become ready; inspect saved startup log." }
        Assert-Status -Container $name -Path "/actuator/health" -Expected 200
        Assert-Status -Container $name -Path "/api/v1/members/me" -Expected 401
        Assert-Status -Container $name -Path "/api/v1/auth/csrf" -Expected 200
        Assert-Status -Container $name -Path "/api/v1/pins/map?southWestLat=37.4&southWestLng=126.8&northEastLat=37.7&northEastLng=127.2&zoomLevel=15" -Expected 200
        Invoke-Docker -Arguments @("cp", "${name}:/tmp/class-load.log", (Join-Path $output "$mode-class-load.log")) | Out-Null
        $classLog = Get-Content -LiteralPath (Join-Path $output "$mode-class-load.log") -Raw
        $sharedClasses = [regex]::Matches($classLog, 'com\.example\.plimap\.[^\r\n]*source: shared objects file \(top\)').Count
        if ($mode -eq "true" -and ($sharedClasses -eq 0 -or $classLog -notmatch 'com\.example\.plimap\.PlimapApplication source: shared objects file \(top\)')) {
            throw "AppCDS did not load application classes from the dynamic archive."
        }
        if ($mode -eq "false" -and $sharedClasses -ne 0) { throw "Application archive leaked into the OFF control." }
        if ($appLog -notmatch 'event=APPLICATION_READY elapsed_ms=(\d+)') { throw "Startup monitoring READY evidence is missing." }
        $summary.modes += @{ enabled = $mode; readyMs = [long]$Matches[1]; sharedApplicationClasses = $sharedClasses; health = 200; profile = 401; csrf = 200; map = 200 }
        Invoke-Docker -Arguments @("stop", "--time=20", $name) | Out-Null
        Write-Output "Passed AppCDS enabled=$mode; shared application classes=$sharedClasses"
    }
    $summary.status = "passed"
} finally {
    [IO.File]::WriteAllText((Join-Path $output "summary.json"), ($summary | ConvertTo-Json -Depth 6))
    # Only resources successfully created by this invocation are removed.
    foreach ($name in $containers) {
        if ($name -match '-(true|false)$') {
            $modeName = $Matches[1]
            $diagnostics = @(& docker logs $name 2>&1) -join [Environment]::NewLine
            [IO.File]::WriteAllText((Join-Path $output "$modeName-application.log"), $diagnostics)
        }
        & docker rm -f $name *> $null
    }
    if ($networkCreated) { & docker network rm $network *> $null }
}

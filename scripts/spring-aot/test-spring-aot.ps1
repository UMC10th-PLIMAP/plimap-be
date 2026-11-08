[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Image,
    [string]$ResultDirectory = (Join-Path $PSScriptRoot "../../build/spring-aot-validation-$([guid]::NewGuid().ToString('N'))")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false
if (Test-Path -LiteralPath $ResultDirectory) { throw 'Refusing to overwrite validation evidence.' }
$output = [IO.Directory]::CreateDirectory($ResultDirectory).FullName
$tls = [IO.Directory]::CreateDirectory((Join-Path $output 'tls')).FullName
$taskId = 'plimap-aot-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$containers = [Collections.Generic.List[string]]::new()
$networkCreated = $false
$summary = [ordered]@{ status = 'failed'; image = $Image; purpose = 'isolated prod functional validation, not a Cloud Run benchmark'; modes = @() }

function Invoke-AotDocker {
    param([string[]]$Arguments)
    $lines = @(& docker @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw "Docker $($Arguments[0]) failed: $($lines -join [Environment]::NewLine)" }
    return ($lines -join [Environment]::NewLine)
}

function Assert-AotStatus {
    param([string]$Container, [string]$Path, [int]$Expected, [switch]$InvalidBody)
    $arguments = @('exec', $Container, 'curl', '--silent', '--show-error', '--output', '/dev/null', '--write-out', '%{http_code}', '--max-time', '10')
    if ($InvalidBody) { $arguments += @('--header', 'Content-Type: application/json', '--data', '{}') }
    $status = Invoke-AotDocker ($arguments + "http://localhost:8080$Path")
    if ($status -ne [string]$Expected) { throw "Unexpected status for $Path : $status" }
}

try {
    $summary.imageId = Invoke-AotDocker @('image', 'inspect', $Image, '--format', '{{.Id}}')
    Invoke-AotDocker @('run', '--rm', '--network=none', '--entrypoint=sh', $Image, '-c',
        'test "$(cat /app/spring-aot-profile)" = prod && test ! -e /workspace && test ! -e /tmp/appcds-training.properties && test ! -e /tmp/appcds-training-credentials.json && test "$(id -u)" = 10001') | Out-Null
    foreach ($case in @('invalid-aot', 'combined-modes', 'missing-artifact')) {
        $arguments = switch ($case) {
            'invalid-aot' { @('run', '--rm', '--network=none', '-e', 'PLIMAP_SPRING_AOT_ENABLED=invalid', $Image) }
            'combined-modes' { @('run', '--rm', '--network=none', '-e', 'PLIMAP_SPRING_AOT_ENABLED=true', '-e', 'PLIMAP_APP_CDS_ENABLED=true', $Image) }
            'missing-artifact' { @('run', '--rm', '--network=none', '-e', 'PLIMAP_SPRING_AOT_ENABLED=true', '-e', 'PLIMAP_APP_CDS_ENABLED=false', '--entrypoint=sh', $Image, '-c', 'rm /app/spring-aot-profile && exec /app/entrypoint.sh') }
        }
        $failure = @(& docker @arguments 2>&1) -join "`n"
        $expected = if ($case -eq 'missing-artifact') { 1 } else { 64 }
        if ($LASTEXITCODE -ne $expected) { throw "Invalid AOT case did not reject: $case" }
        $pattern = switch ($case) { 'invalid-aot' { 'must be true or false' }; 'combined-modes' { 'require PLIMAP_APP_CDS_ENABLED=false' }; 'missing-artifact' { 'missing_prod_artifact' } }
        if ($failure -notmatch $pattern) { throw "Invalid AOT case did not report its cause: $case" }
    }

    # A throwaway self-signed certificate keeps Postgres and Redis TLS enabled.
    Invoke-AotDocker @('run', '--rm', '--network=none', '--user=0', '--entrypoint=sh', '--mount', "type=bind,source=$tls,target=/tls", $Image, '-c',
        'openssl req -x509 -newkey rsa:2048 -nodes -keyout /tls/server.key -out /tls/server.crt -days 1 -subj /CN=plimap-aot-validation -addext "subjectAltName=DNS:plimap-aot-db,DNS:plimap-aot-redis" && keytool -importcert -noprompt -alias validation -file /tls/server.crt -keystore /tls/truststore.p12 -storetype PKCS12 -storepass aot-test-only') | Out-Null
    Invoke-AotDocker @('network', 'create', '--internal', $taskId) | Out-Null
    $networkCreated = $true
    $database = "$taskId-db"
    Invoke-AotDocker @('run', '-d', '--name', $database, '--network', $taskId, '--network-alias', 'plimap-aot-db',
        '-e', 'POSTGRES_DB=plimap_aot', '-e', 'POSTGRES_PASSWORD=aot-test-only', '--tmpfs', '/var/lib/postgresql/data',
        '--mount', "type=bind,source=$tls,target=/tls,readonly", 'postgis/postgis:17-3.5', 'bash', '-c',
        'cp /tls/server.key /tmp/server.key && cp /tls/server.crt /tmp/server.crt && chown postgres:postgres /tmp/server.key /tmp/server.crt && chmod 600 /tmp/server.key && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tmp/server.crt -c ssl_key_file=/tmp/server.key') | Out-Null
    $containers.Add($database)
    $redis = "$taskId-redis"
    Invoke-AotDocker @('run', '-d', '--name', $redis, '--network', $taskId, '--network-alias', 'plimap-aot-redis',
        '--tmpfs', '/data', '--mount', "type=bind,source=$tls,target=/tls,readonly", 'redis:7-alpine', 'sh', '-c',
        'cp /tls/server.key /data/server.key && cp /tls/server.crt /data/server.crt && chown redis:redis /data/server.key /data/server.crt && chmod 600 /data/server.key && exec docker-entrypoint.sh redis-server --port 0 --tls-port 6379 --tls-cert-file /data/server.crt --tls-key-file /data/server.key --tls-ca-cert-file /data/server.crt --tls-auth-clients no --appendonly no') | Out-Null
    $containers.Add($redis)
    $redisReady = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $pong = @(& docker exec $redis redis-cli --tls --cacert /data/server.crt ping 2>$null) -join ''
        if ($LASTEXITCODE -eq 0 -and $pong -eq 'PONG') { $redisReady = $true; break }
        if ((Invoke-AotDocker @('inspect', $redis, '--format', '{{.State.Running}}')) -ne 'true') { break }
        Start-Sleep -Seconds 1
    }
    if (-not $redisReady) { throw 'Isolated Redis TLS did not become ready.' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        # The image's temporary initialization server accepts Unix sockets only.
        # Wait for final TCP startup before creating roles or starting the app.
        & docker exec $database pg_isready -h 127.0.0.1 -U postgres -d plimap_aot *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'Isolated PostGIS did not become ready.' }
    $sql = "CREATE ROLE plimap_migrator LOGIN PASSWORD 'aot-test-only'; CREATE ROLE plimap_app LOGIN PASSWORD 'aot-test-only'; GRANT CREATE ON DATABASE plimap_aot TO plimap_migrator; GRANT USAGE, CREATE ON SCHEMA public TO plimap_migrator; ALTER DEFAULT PRIVILEGES FOR ROLE plimap_migrator IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO plimap_app; ALTER DEFAULT PRIVILEGES FOR ROLE plimap_migrator IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO plimap_app;"
    Invoke-AotDocker @('exec', $database, 'psql', '-U', 'postgres', '-d', 'plimap_aot', '-v', 'ON_ERROR_STOP=1', '-c', $sql) | Out-Null
    $properties = Join-Path $output 'prod.properties'
    $dummy = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'generation.properties'))
    $runtime = @(
        'spring.datasource.url=jdbc:postgresql://plimap-aot-db:5432/plimap_aot?sslmode=require',
        'spring.datasource.password=aot-test-only', 'spring.flyway.password=aot-test-only',
        'spring.data.redis.url=rediss://plimap-aot-redis:6379', 'plimap.monitoring.startup.enabled=true'
    ) -join "`n"
    [IO.File]::WriteAllText($properties, $dummy + "`n" + $runtime, [Text.UTF8Encoding]::new($false))
    $credentials = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../appcds/training-credentials.json')).Path
    foreach ($mode in @('false', 'true')) {
        $name = "$taskId-$mode"
        Invoke-AotDocker @('run', '-d', '--name', $name, '--network', $taskId, '--memory=1g', '--cpus=1',
            '--mount', "type=bind,source=$properties,target=/tmp/aot-prod.properties,readonly",
            '--mount', "type=bind,source=$tls/truststore.p12,target=/tmp/aot-truststore.p12,readonly",
            '--mount', "type=bind,source=$credentials,target=/tmp/aot-credentials.json,readonly",
            '-e', 'SPRING_PROFILES_ACTIVE=prod', '-e', 'PLIMAP_APP_CDS_ENABLED=false', '-e', "PLIMAP_SPRING_AOT_ENABLED=$mode",
            '-e', 'GOOGLE_APPLICATION_CREDENTIALS=/tmp/aot-credentials.json', '-e', 'GOOGLE_CLOUD_PROJECT=plimap-aot-validation',
            '-e', 'JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=/tmp/aot-truststore.p12 -Djavax.net.ssl.trustStorePassword=aot-test-only -Xlog:class+load=info:file=/tmp/aot-class-load.log',
            $Image, '--spring.config.additional-location=file:/tmp/aot-prod.properties') | Out-Null
        $containers.Add($name)
        $healthy = $false
        for ($attempt = 0; $attempt -lt 120; $attempt++) {
            $health = @(& docker exec $name curl --silent --output /dev/null --write-out '%{http_code}' --connect-timeout 1 --max-time 2 http://localhost:8080/actuator/health 2>$null) -join ''
            if ($LASTEXITCODE -eq 0 -and $health -eq '200') { $healthy = $true; break }
            if ((Invoke-AotDocker @('inspect', $name, '--format', '{{.State.Running}}')) -ne 'true') { break }
            Start-Sleep -Seconds 1
        }
        $log = Invoke-AotDocker @('logs', $name)
        [IO.File]::WriteAllText((Join-Path $output "$mode-application.log"), $log)
        if (-not $healthy) { throw "Prod AOT mode=$mode failed; inspect saved log." }
        Assert-AotStatus $name '/actuator/health/readiness' 200
        Assert-AotStatus $name '/api/v1/members/me' 401
        Assert-AotStatus $name '/api/v1/auth/csrf' 200
        Assert-AotStatus $name '/api/v1/pins/map?southWestLat=37.4&southWestLng=126.8&northEastLat=37.7&northEastLng=127.2&zoomLevel=15' 200
        Assert-AotStatus $name '/api/v1/auth/app/login' 400 -InvalidBody
        Assert-AotStatus $name '/oauth/authorization/google?frontendOrigin=http://localhost:8080' 302
        Invoke-AotDocker @('cp', "${name}:/tmp/aot-class-load.log", (Join-Path $output "$mode-class-load.log")) | Out-Null
        $classes = Get-Content -LiteralPath (Join-Path $output "$mode-class-load.log") -Raw
        if ($log -notmatch "event=SPRING_AOT_RUNTIME enabled=$mode" -or $log -notmatch 'event=APP_CDS_CONFIGURATION enabled=false') { throw 'Actual runtime mode evidence missing.' }
        if ($mode -eq 'true' -and ($classes -notmatch 'com.example.plimap.PlimapApplication__ApplicationContextInitializer source:' -or $log -notmatch 'Starting AOT-processed PlimapApplication')) { throw 'Generated AOT initializer was not used.' }
        if ($mode -eq 'false' -and $log -match 'Starting AOT-processed PlimapApplication') { throw 'AOT leaked into the OFF control.' }
        if ($log -notmatch 'event=APPLICATION_READY elapsed_ms=(\d+)') { throw 'READY monitoring evidence missing.' }
        $summary.modes += @{ enabled = $mode; readyMs = [long]$Matches[1]; prodProfile = $true; postgresTls = $true; redisTls = $true; health = 200; profile = 401; csrf = 200; map = 200; invalidLogin = 400; oauthRedirect = 302 }
        Invoke-AotDocker @('stop', '--time=20', $name) | Out-Null
        Write-Output "Passed isolated prod Spring AOT enabled=$mode"
    }
    $summary.status = 'passed'
} finally {
    [IO.File]::WriteAllText((Join-Path $output 'summary.json'), ($summary | ConvertTo-Json -Depth 8))
    foreach ($name in $containers) {
        $log = @(& docker logs $name 2>&1) -join [Environment]::NewLine
        [IO.File]::WriteAllText((Join-Path $output "$name-final.log"), $log)
        & docker rm -f $name *> $null
    }
    if ($networkCreated) { & docker network rm $taskId *> $null }
    # Generated local TLS private key only; retain public certificate and diagnostics.
    $privateKey = Join-Path $tls 'server.key'
    if (Test-Path -LiteralPath $privateKey) { Remove-Item -LiteralPath $privateKey }
}

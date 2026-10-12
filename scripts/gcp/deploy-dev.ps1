[CmdletBinding()]
param(
    [string]$ProjectId = "plimap",
    [string]$Region = "asia-northeast3",
    [string]$ServiceName = "plimap-api-dev",
    [string]$Image = "asia-northeast3-docker.pkg.dev/plimap/plimap-docker/api:dev-initial",
    [string]$PublicBaseUrl = "https://dev.plimap.kr",
    [string]$FrontendRedirectUri = "",
    [string]$CorsAllowedOrigins = "",
    [string]$OAuthAllowedFrontendOrigins = "",
    [string]$ProfileImageBucket = "profile-images"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$runtimeServiceAccount = "plimap-api-dev@$ProjectId.iam.gserviceaccount.com"
$secretMap = [ordered]@{
    DB_URL                     = "plimap-dev-db-url"
    DB_USERNAME                = "plimap-dev-db-username"
    DB_PASSWORD                = "plimap-dev-db-password"
    REDIS_URL                  = "plimap-dev-redis-url"
    JWT_SECRET                 = "plimap-dev-jwt-secret"
    TEST_TOKEN_ISSUE_KEY       = "plimap-dev-test-token-issue-key"
    KAKAO_REST_API_KEY         = "plimap-dev-kakao-rest-api-key"
    KAKAO_REST_API_SECRET      = "plimap-dev-kakao-rest-api-secret"
    GOOGLE_CLIENT_ID           = "plimap-dev-google-client-id"
    GOOGLE_CLIENT_SECRET       = "plimap-dev-google-client-secret"
    YOUTUBE_API_KEY            = "plimap-dev-youtube-api-key"
    SUPABASE_URL               = "plimap-dev-supabase-url"
    SUPABASE_SECRET_KEY        = "plimap-dev-supabase-secret-key"
}

$privateNetworkOriginPatterns = @(
    "http://192.168.*:[*]",
    "https://192.168.*:[*]"
)

function Invoke-Gcloud {
    param([Parameter(Mandatory)][string[]]$Arguments)

    & gcloud @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "gcloud command failed: gcloud $($Arguments -join ' ')"
    }
}

function ConvertTo-YamlSingleQuoted {
    param([Parameter(Mandatory)][string]$Value)

    return "'" + $Value.Replace("'", "''") + "'"
}

function Get-HttpsOrigin {
    param([Parameter(Mandatory)][string]$Value)

    $uri = [Uri]::new($Value, [UriKind]::Absolute)
    if ($uri.Scheme -ne "https" -or
        -not [string]::IsNullOrEmpty($uri.UserInfo) -or
        -not $uri.IsDefaultPort -or
        $uri.AbsolutePath -ne "/" -or
        -not [string]::IsNullOrEmpty($uri.Query) -or
        -not [string]::IsNullOrEmpty($uri.Fragment)) {
        throw "PublicBaseUrl must be an HTTPS origin without a path, query, fragment, credentials, or custom port: $Value"
    }

    return $uri.GetLeftPart([UriPartial]::Authority)
}

function Test-PrivateNetworkIpv4 {
    param([Parameter(Mandatory)][string]$HostName)

    if ($HostName -notmatch '^192\.168\.(\d{1,3})\.(\d{1,3})$') {
        return $false
    }

    return [int]$Matches[1] -le 255 -and [int]$Matches[2] -le 255
}

function Get-WebOrigin {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Value,
        [switch]$AllowPreviewPattern,
        [switch]$AllowPrivateNetworkPattern
    )

    $origin = $Value.Trim()
    if ($AllowPreviewPattern -and $origin -eq "https://pr-*.plimap.kr") {
        return $origin
    }
    if ($AllowPrivateNetworkPattern -and $origin -in $privateNetworkOriginPatterns) {
        return $origin
    }

    try {
        $uri = [Uri]::new($origin, [UriKind]::Absolute)
    } catch {
        throw "$Name contains an invalid Origin: $Value"
    }

    $isHttps = $uri.Scheme -eq "https"
    $isLocalHttp = $uri.Scheme -eq "http" -and $uri.Host -in @("localhost", "127.0.0.1", "::1")
    $isPrivateNetworkHttp = $AllowPrivateNetworkPattern -and $uri.Scheme -eq "http" -and (Test-PrivateNetworkIpv4 -HostName $uri.Host)
    if ((-not $isHttps -and -not $isLocalHttp -and -not $isPrivateNetworkHttp) -or
        -not [string]::IsNullOrEmpty($uri.UserInfo) -or
        $uri.AbsolutePath -ne "/" -or
        -not [string]::IsNullOrEmpty($uri.Query) -or
        -not [string]::IsNullOrEmpty($uri.Fragment)) {
        throw "$Name must contain only HTTPS Origins, HTTP localhost Origins, or HTTP 192.168.0.0/16 Origins without paths, query, fragment, or credentials: $Value"
    }

    return $uri.GetLeftPart([UriPartial]::Authority)
}

function Get-AllowedOrigins {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string]$RequiredOrigin,
        [switch]$AllowPreviewPattern,
        [switch]$AllowPrivateNetworkPattern
    )

    $origins = @($Value.Split(",") |
        ForEach-Object { $_.Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        ForEach-Object {
            Get-WebOrigin -Name $Name -Value $_ -AllowPreviewPattern:$AllowPreviewPattern -AllowPrivateNetworkPattern:$AllowPrivateNetworkPattern
        } |
        Select-Object -Unique)

    if ($origins.Count -eq 0) {
        throw "$Name must contain at least one Origin."
    }
    if ($origins -notcontains $RequiredOrigin) {
        throw "$Name must include the public Origin ($RequiredOrigin)."
    }

    return $origins -join ","
}

function Get-HttpsUrl {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string]$ExpectedOrigin
    )

    $uri = [Uri]::new($Value, [UriKind]::Absolute)
    if ($uri.Scheme -ne "https" -or
        -not [string]::IsNullOrEmpty($uri.UserInfo) -or
        -not $uri.IsDefaultPort -or
        -not [string]::IsNullOrEmpty($uri.Fragment)) {
        throw "$Name must be an HTTPS URL without credentials, a custom port, or a fragment: $Value"
    }

    $actualOrigin = $uri.GetLeftPart([UriPartial]::Authority)
    if (-not [string]::Equals(
        $actualOrigin,
        $ExpectedOrigin,
        [StringComparison]::OrdinalIgnoreCase
    )) {
        throw "$Name must use the same origin as PublicBaseUrl ($ExpectedOrigin): $Value"
    }

    return $uri.AbsoluteUri
}

function Assert-HttpStatus {
    param(
        [Parameter(Mandatory)][string]$Uri,
        [Parameter(Mandatory)][int]$ExpectedStatus,
        [ValidateRange(1, 10)][int]$MaxAttempts = 3,
        [ValidateRange(1, 60)][int]$RequestTimeoutSeconds = 20,
        [ValidateRange(0, 60)][int]$InitialDelaySeconds = 2
    )

    $delaySeconds = $InitialDelaySeconds
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $webResponse = $null
        $actualStatus = $null
        $lastErrorMessage = $null

        try {
            $webResponse = Invoke-WebRequest `
                -UseBasicParsing `
                -Uri $Uri `
                -TimeoutSec $RequestTimeoutSeconds
            $actualStatus = [int]$webResponse.StatusCode
        } catch {
            $responseProperty = $_.Exception.PSObject.Properties["Response"]
            $errorResponse = if ($null -ne $responseProperty) {
                $responseProperty.Value
            } else {
                $null
            }
            if ($null -ne $errorResponse) {
                $actualStatus = [int]$errorResponse.StatusCode
            } else {
                $lastErrorMessage = $_.Exception.Message
            }
        }

        if ($actualStatus -eq $ExpectedStatus) {
            return $webResponse
        }

        if ($attempt -eq $MaxAttempts) {
            if ($null -ne $actualStatus) {
                throw "Endpoint verification failed after $MaxAttempts attempts: $Uri (expected=$ExpectedStatus, actual=$actualStatus)"
            }
            throw "Endpoint verification failed after $MaxAttempts attempts: $Uri (expected=$ExpectedStatus, error=$lastErrorMessage)"
        }

        $failureReason = if ($null -ne $actualStatus) {
            "expected=$ExpectedStatus, actual=$actualStatus"
        } else {
            "error=$lastErrorMessage"
        }
        Write-Warning "Endpoint verification attempt $attempt/$MaxAttempts failed: $Uri ($failureReason). Retrying in $delaySeconds seconds."
        Start-Sleep -Seconds $delaySeconds
        $delaySeconds = [Math]::Min($delaySeconds * 2, 10)
    }
}

function Write-EnvironmentFile {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$PublicOrigin,
        [Parameter(Mandatory)][string]$FrontendRedirectUri,
        [Parameter(Mandatory)][string]$CorsAllowedOrigins,
        [Parameter(Mandatory)][string]$OAuthAllowedFrontendOrigins,
        [Parameter(Mandatory)][string]$ProfileImageBucket
    )

    $lines = @(
        "SPRING_PROFILES_ACTIVE: 'dev'",
        "PUBLIC_BASE_URL: $(ConvertTo-YamlSingleQuoted $PublicOrigin)",
        "CORS_ALLOWED_ORIGINS: $(ConvertTo-YamlSingleQuoted $CorsAllowedOrigins)",
        "OAUTH_REDIRECT_URI: $(ConvertTo-YamlSingleQuoted $FrontendRedirectUri)",
        "OAUTH_ALLOWED_FRONTEND_ORIGINS: $(ConvertTo-YamlSingleQuoted $OAuthAllowedFrontendOrigins)",
        "KAKAO_REDIRECT_URI: $(ConvertTo-YamlSingleQuoted "$PublicOrigin/oauth/callback/kakao")",
        "GOOGLE_REDIRECT_URI: $(ConvertTo-YamlSingleQuoted "$PublicOrigin/oauth/callback/google")",
        "PROFILE_IMAGE_STORAGE_PROVIDER: 'supabase'",
        "PROFILE_IMAGE_BUCKET: $(ConvertTo-YamlSingleQuoted $ProfileImageBucket)"
    )

    $utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($Path, $lines, $utf8WithoutBom)
}

if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    throw "gcloud CLI was not found. Check the Google Cloud CLI installation and login."
}

$publicOrigin = Get-HttpsOrigin -Value $PublicBaseUrl
if ([string]::IsNullOrWhiteSpace($FrontendRedirectUri)) {
    $FrontendRedirectUri = "$publicOrigin/app/oauth/callback"
}
$frontendRedirectUrl = Get-HttpsUrl `
    -Name "FrontendRedirectUri" `
    -Value $FrontendRedirectUri `
    -ExpectedOrigin $publicOrigin

if ([string]::IsNullOrWhiteSpace($CorsAllowedOrigins)) {
    $CorsAllowedOrigins = "$publicOrigin,https://admin.plimap.kr,http://localhost:5173,http://192.168.*:[*],https://192.168.*:[*]"
}
$CorsAllowedOrigins = "$CorsAllowedOrigins,https://pr-*.plimap.kr"
$corsOrigins = Get-AllowedOrigins `
    -Name "CorsAllowedOrigins" `
    -Value $CorsAllowedOrigins `
    -RequiredOrigin $publicOrigin `
    -AllowPreviewPattern -AllowPrivateNetworkPattern

if ([string]::IsNullOrWhiteSpace($OAuthAllowedFrontendOrigins)) {
    $OAuthAllowedFrontendOrigins = "$publicOrigin,https://admin.plimap.kr,http://localhost:5173"
}
$OAuthAllowedFrontendOrigins = "$OAuthAllowedFrontendOrigins,https://pr-*.plimap.kr"
$oauthFrontendOrigins = Get-AllowedOrigins `
    -Name "OAuthAllowedFrontendOrigins" `
    -Value $OAuthAllowedFrontendOrigins `
    -AllowPreviewPattern `
    -RequiredOrigin $publicOrigin

foreach ($entry in $secretMap.GetEnumerator()) {
    $versionStates = @(& gcloud secrets versions list $entry.Value `
        --project=$ProjectId `
        --format="value(state)")

    if ($LASTEXITCODE -ne 0 -or $versionStates -notcontains "ENABLED") {
        throw "Secret has no enabled version: $($entry.Value) ($($entry.Key))"
    }
}

$environmentFile = New-TemporaryFile
try {
    Write-EnvironmentFile `
        -Path $environmentFile.FullName `
        -PublicOrigin $publicOrigin `
        -FrontendRedirectUri $frontendRedirectUrl `
        -CorsAllowedOrigins $corsOrigins `
        -OAuthAllowedFrontendOrigins $oauthFrontendOrigins `
        -ProfileImageBucket $ProfileImageBucket

    $secretBindings = ($secretMap.GetEnumerator() | ForEach-Object {
        "$($_.Key)=$($_.Value):latest"
    }) -join ","

    $deployArguments = @(
        "run", "deploy", $ServiceName,
        "--project=$ProjectId",
        "--region=$Region",
        "--image=$Image",
        "--service-account=$runtimeServiceAccount",
        "--execution-environment=gen2",
        "--port=8080",
        "--cpu=1",
        "--memory=512Mi",
        "--concurrency=40",
        "--timeout=60",
        "--min-instances=0",
        "--max-instances=2",
        "--ingress=all",
        "--allow-unauthenticated",
        "--cpu-boost",
        "--deploy-health-check",
        "--startup-probe=httpGet.path=/actuator/health/liveness,httpGet.port=8080,initialDelaySeconds=0,timeoutSeconds=3,periodSeconds=5,failureThreshold=24",
        "--liveness-probe=httpGet.path=/actuator/health/liveness,httpGet.port=8080,initialDelaySeconds=0,timeoutSeconds=3,periodSeconds=10,failureThreshold=3",
        "--readiness-probe=httpGet.path=/actuator/health/readiness,httpGet.port=8080,timeoutSeconds=3,periodSeconds=5,failureThreshold=3",
        "--env-vars-file=$($environmentFile.FullName)",
        "--set-secrets=$secretBindings",
        "--quiet"
    )
    Invoke-Gcloud -Arguments $deployArguments

    $deployedUrl = (& gcloud run services describe $ServiceName `
        --project=$ProjectId `
        --region=$Region `
        --format="value(status.url)").TrimEnd('/')
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($deployedUrl)) {
        throw "Could not read the deployed Cloud Run URL."
    }

    foreach ($healthPath in @(
        "/actuator/health/liveness",
        "/actuator/health/readiness",
        "/actuator/health"
    )) {
        Assert-HttpStatus `
            -Uri "$deployedUrl$healthPath" `
            -ExpectedStatus 200 | Out-Null
    }

    foreach ($documentationPath in @(
        "/swagger-ui/index.html",
        "/v3/api-docs"
    )) {
        Assert-HttpStatus `
            -Uri "$deployedUrl$documentationPath" `
            -ExpectedStatus 404 | Out-Null
    }

    $swaggerResponse = Assert-HttpStatus `
        -Uri "$publicOrigin/swagger-ui/index.html" `
        -ExpectedStatus 200
    if ($swaggerResponse.Content -notmatch "Swagger UI") {
        throw "Public Swagger verification failed: expected Swagger UI content."
    }

    $openApiResponse = Assert-HttpStatus `
        -Uri "$publicOrigin/v3/api-docs" `
        -ExpectedStatus 200
    try {
        $openApiDocument = $openApiResponse.Content | ConvertFrom-Json
    } catch {
        throw "Public OpenAPI verification failed: response is not valid JSON."
    }
    if ([string]::IsNullOrWhiteSpace([string]$openApiDocument.openapi)) {
        throw "Public OpenAPI verification failed: openapi field is missing."
    }

    Write-Output "Cloud Run dev deployment and verification completed: $deployedUrl (Swagger: $publicOrigin)"
} finally {
    Remove-Item -LiteralPath $environmentFile.FullName -Force -ErrorAction SilentlyContinue
}

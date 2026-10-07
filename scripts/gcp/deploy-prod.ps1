[CmdletBinding()]
param(
    [ValidatePattern("^[a-z][a-z0-9-]{4,28}[a-z0-9]$")]
    [string]$ProjectId = "plimap",
    [ValidatePattern("^[a-z]+-[a-z]+[0-9]$")]
    [string]$Region = "asia-northeast3",
    [ValidatePattern("^[a-z](?:[a-z0-9-]{0,33}[a-z0-9])?$")]
    [string]$ServiceName = "plimap-api-prod",
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string]$Image,
    [Parameter(Mandatory)]
    [ValidatePattern("^[0-9a-f]{40}$")]
    [string]$DeployCommit,
    [string]$PublicBaseUrl = "https://plimap.kr",
    [string]$AdminFrontendOrigin = "https://admin.plimap.kr",
    [string]$FrontendRedirectUri = "",
    [string]$CorsAllowedOrigins = "",
    [string]$OAuthAllowedFrontendOrigins = "",
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [ValidatePattern("^[a-z0-9](?:[a-z0-9._-]{1,61}[a-z0-9])$")]
    [string]$ProfileImageBucket,
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [ValidatePattern("^[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?$")]
    [string]$VpcNetwork,
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [ValidatePattern("^[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?$")]
    [string]$VpcSubnet,
    [switch]$PublicSmokeEnabled,
    [string]$ResultPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$runtimeServiceAccount = "plimap-api-prod@$ProjectId.iam.gserviceaccount.com"
$secretMap = [ordered]@{
    DB_URL                = "plimap-prod-db-url"
    DB_USERNAME           = "plimap-prod-db-username"
    DB_PASSWORD           = "plimap-prod-db-password"
    FLYWAY_USERNAME       = "plimap-prod-flyway-username"
    FLYWAY_PASSWORD       = "plimap-prod-flyway-password"
    REDIS_URL             = "plimap-prod-redis-url"
    JWT_SECRET            = "plimap-prod-jwt-secret"
    KAKAO_REST_API_KEY    = "plimap-prod-kakao-rest-api-key"
    KAKAO_REST_API_SECRET = "plimap-prod-kakao-rest-api-secret"
    GOOGLE_CLIENT_ID      = "plimap-prod-google-client-id"
    GOOGLE_CLIENT_SECRET  = "plimap-prod-google-client-secret"
    YOUTUBE_API_KEY       = "plimap-prod-youtube-api-key"
}

$result = [ordered]@{
    status                = "failed"
    deployCommit          = $DeployCommit
    image                 = $Image
    previousRevision      = ""
    candidateRevision     = ""
    candidateVerification = "not-started"
    trafficPromotion      = "not-started"
    serviceVerification   = "not-started"
    publicVerification    = if ($PublicSmokeEnabled) { "not-started" } else { "skipped" }
    rollback              = "not-required"
    secretVersions        = [ordered]@{}
    publicBaseUrl         = ""
    error                 = ""
}
$environmentFile = $null
$trafficPromotionAttempted = $false
$bootstrapRevisionName = "$ServiceName-bootstrap"

function Invoke-Gcloud {
    param([Parameter(Mandatory)][string[]]$Arguments)

    & gcloud @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "gcloud command failed: gcloud $($Arguments -join ' ')"
    }
}

function Get-GcloudText {
    param([Parameter(Mandatory)][string[]]$Arguments)

    $output = @(& gcloud @Arguments)
    if ($LASTEXITCODE -ne 0) {
        throw "gcloud command failed: gcloud $($Arguments -join ' ')"
    }
    return ($output -join "`n").Trim()
}

function Assert-ApprovedImage {
    $approvedRepository = "$Region-docker.pkg.dev/$ProjectId/plimap-docker/api"
    $approvedImagePattern = "^$([regex]::Escape($approvedRepository))@sha256:[0-9a-f]{64}$"
    if ($Image -notmatch $approvedImagePattern) {
        throw "Production image must use the approved Artifact Registry repository and an immutable SHA-256 digest: $approvedRepository"
    }

    $imageDigest = ($Image -split "@", 2)[1]
    $commitImage = "${approvedRepository}:$DeployCommit"
    $commitDigest = Get-GcloudText -Arguments @(
        "artifacts", "docker", "images", "describe", $commitImage,
        "--project=$ProjectId",
        "--format=value(image_summary.digest)"
    )
    if ($commitDigest -notmatch "^sha256:[0-9a-f]{64}$") {
        throw "Could not resolve the Artifact Registry digest for commit image: $commitImage"
    }
    if (-not [string]::Equals(
        $imageDigest,
        $commitDigest,
        [StringComparison]::OrdinalIgnoreCase
    )) {
        throw "Production image digest does not match the image tagged with deploy commit $DeployCommit."
    }
}

function Get-JsonProperty {
    param(
        [AllowNull()][object]$Object,
        [Parameter(Mandatory)][string]$Name
    )

    if ($null -eq $Object) {
        return $null
    }

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }

    return $property.Value
}

function Get-ServiceState {
    param([switch]$AllowMissing)

    $serviceStateJson = @(& gcloud run services describe $ServiceName `
        --project=$ProjectId `
        --region=$Region `
        --format=json 2>$null)
    if ($LASTEXITCODE -ne 0) {
        if ($AllowMissing) {
            return $null
        }
        throw "Could not describe Cloud Run service: $ServiceName"
    }
    if ($serviceStateJson.Count -eq 0) {
        throw "Cloud Run service describe returned an empty response: $ServiceName"
    }

    return ($serviceStateJson -join "`n") | ConvertFrom-Json
}

function Assert-PublicInvoker {
    $policyJson = Get-GcloudText -Arguments @(
        "run", "services", "get-iam-policy", $ServiceName,
        "--project=$ProjectId",
        "--region=$Region",
        "--format=json"
    )
    $policy = $policyJson | ConvertFrom-Json
    $bindings = @(Get-JsonProperty -Object $policy -Name "bindings")
    $publicBindings = @($bindings | Where-Object {
        [string](Get-JsonProperty -Object $_ -Name "role") -eq "roles/run.invoker" -and
        @(Get-JsonProperty -Object $_ -Name "members") -contains "allUsers"
    })
    if ($publicBindings.Count -ne 1) {
        throw "Cloud Run service must be bootstrapped with allUsers roles/run.invoker."
    }
}

function Assert-LoadBalancerOnlyService {
    param([Parameter(Mandatory)][object]$ServiceState)

    $metadata = Get-JsonProperty -Object $ServiceState -Name "metadata"
    $annotations = Get-JsonProperty -Object $metadata -Name "annotations"
    $ingress = [string](Get-JsonProperty `
        -Object $annotations `
        -Name "run.googleapis.com/ingress")
    if ($ingress -ne "internal-and-cloud-load-balancing") {
        throw "Prod Cloud Run ingress must allow only internal traffic and Cloud Load Balancing."
    }

    $defaultUrlDisabled = [string](Get-JsonProperty `
        -Object $annotations `
        -Name "run.googleapis.com/default-url-disabled")
    if ($defaultUrlDisabled -ne "true") {
        throw "Prod Cloud Run default URL must remain disabled."
    }

    $status = Get-JsonProperty -Object $ServiceState -Name "status"
    $serviceUrl = [string](Get-JsonProperty -Object $status -Name "url")
    if (-not [string]::IsNullOrWhiteSpace($serviceUrl) -and $serviceUrl -ne "None") {
        throw "Prod Cloud Run status still exposes a default URL: $serviceUrl"
    }
}

function Assert-ZeroTrafficBootstrap {
    param([Parameter(Mandatory)][object]$ServiceState)

    $metadata = Get-JsonProperty -Object $ServiceState -Name "metadata"
    $labels = Get-JsonProperty -Object $metadata -Name "labels"
    if ([string](Get-JsonProperty -Object $labels -Name "plimap-bootstrap") -ne "prod") {
        throw "Zero-traffic Cloud Run service is not the approved Prod bootstrap service."
    }

    $status = Get-JsonProperty -Object $ServiceState -Name "status"
    $serviceUrl = [string](Get-JsonProperty -Object $status -Name "url")
    if (-not [string]::IsNullOrWhiteSpace($serviceUrl) -and $serviceUrl -ne "None") {
        throw "Zero-traffic Prod bootstrap service must keep the default URL disabled."
    }
}

function Get-ActiveTrafficAllocations {
    param([Parameter(Mandatory)][object]$ServiceState)

    $status = Get-JsonProperty -Object $ServiceState -Name "status"
    $latestReadyRevisionName = [string](Get-JsonProperty -Object $status -Name "latestReadyRevisionName")
    $trafficTargets = @(Get-JsonProperty -Object $status -Name "traffic")
    $allocations = @()

    foreach ($trafficTarget in $trafficTargets) {
        $percentValue = Get-JsonProperty -Object $trafficTarget -Name "percent"
        $percent = if ($null -eq $percentValue) { 0 } else { [int]$percentValue }
        if ($percent -le 0) {
            continue
        }

        $revisionName = [string](Get-JsonProperty -Object $trafficTarget -Name "revisionName")
        if ([string]::IsNullOrWhiteSpace($revisionName)) {
            $latestRevision = Get-JsonProperty -Object $trafficTarget -Name "latestRevision"
            if ($latestRevision -eq $true) {
                $revisionName = $latestReadyRevisionName
            }
        }
        if ([string]::IsNullOrWhiteSpace($revisionName)) {
            throw "Could not resolve the revision name for an active Cloud Run traffic target."
        }

        $allocations += [pscustomobject]@{
            revisionName = $revisionName
            percent      = $percent
        }
    }

    return $allocations
}

function Wait-ForRevisionReady {
    param(
        [Parameter(Mandatory)][string]$RevisionName,
        [Parameter(Mandatory)][string]$ExpectedImage,
        [ValidateRange(1, 30)][int]$MaxAttempts = 15
    )

    $delaySeconds = 1
    $revisionState = $null
    $readyStatus = "missing"
    $readyReason = ""
    $readyMessage = ""

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $revisionStateJson = Get-GcloudText -Arguments @(
            "run", "revisions", "describe", $RevisionName,
            "--project=$ProjectId",
            "--region=$Region",
            "--format=json"
        )
        $revisionState = $revisionStateJson | ConvertFrom-Json
        $status = Get-JsonProperty -Object $revisionState -Name "status"
        $conditions = @(Get-JsonProperty -Object $status -Name "conditions")
        $readyConditions = @($conditions | Where-Object {
            (Get-JsonProperty -Object $_ -Name "type") -eq "Ready"
        })
        if ($readyConditions.Count -gt 1) {
            throw "Cloud Run candidate revision returned multiple Ready conditions: $RevisionName"
        }

        if ($readyConditions.Count -eq 1) {
            $readyStatus = [string](Get-JsonProperty -Object $readyConditions[0] -Name "status")
            $readyReason = [string](Get-JsonProperty -Object $readyConditions[0] -Name "reason")
            $readyMessage = [string](Get-JsonProperty -Object $readyConditions[0] -Name "message")
            if ($readyStatus -eq "True") {
                break
            }
        } else {
            $readyStatus = "missing"
            $readyReason = ""
            $readyMessage = ""
        }

        if ($attempt -eq $MaxAttempts) {
            throw (
                "Cloud Run candidate revision did not become Ready: $RevisionName " +
                    "(status=$readyStatus, reason=$readyReason, message=$readyMessage)"
            )
        }

        Write-Warning (
            "Cloud Run candidate revision is not Ready yet " +
                "($attempt/$MaxAttempts, status=$readyStatus, reason=$readyReason)."
        )
        Start-Sleep -Seconds $delaySeconds
        $delaySeconds = [Math]::Min($delaySeconds * 2, 5)
    }

    $spec = Get-JsonProperty -Object $revisionState -Name "spec"
    $containers = @(Get-JsonProperty -Object $spec -Name "containers")
    if ($containers.Count -ne 1) {
        throw "Cloud Run candidate revision must contain exactly one container: $RevisionName"
    }
    $actualImage = [string](Get-JsonProperty -Object $containers[0] -Name "image")
    if (-not [string]::Equals(
        $actualImage,
        $ExpectedImage,
        [StringComparison]::OrdinalIgnoreCase
    )) {
        throw "Cloud Run candidate image mismatch: expected=$ExpectedImage, actual=$actualImage"
    }
}

function Wait-ForSingleRevisionTraffic {
    param(
        [Parameter(Mandatory)][string]$ExpectedRevision,
        [ValidateRange(1, 30)][int]$MaxAttempts = 15
    )

    $delaySeconds = 1
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $serviceState = Get-ServiceState
        $allocations = @(Get-ActiveTrafficAllocations -ServiceState $serviceState)
        if ($allocations.Count -eq 1 -and
            $allocations[0].revisionName -eq $ExpectedRevision -and
            [int]$allocations[0].percent -eq 100) {
            return
        }

        if ($attempt -eq $MaxAttempts) {
            $actualTraffic = if ($allocations.Count -eq 0) {
                "none"
            } else {
                ($allocations | ForEach-Object {
                    "$($_.revisionName)=$($_.percent)%"
                }) -join ","
            }
            throw "Cloud Run traffic did not converge to $ExpectedRevision=100% (actual=$actualTraffic)."
        }

        Write-Warning "Cloud Run traffic has not converged yet ($attempt/$MaxAttempts)."
        Start-Sleep -Seconds $delaySeconds
        $delaySeconds = [Math]::Min($delaySeconds * 2, 5)
    }
}

function ConvertTo-YamlSingleQuoted {
    param([Parameter(Mandatory)][string]$Value)

    return "'" + $Value.Replace("'", "''") + "'"
}

function Get-HttpsOrigin {
    param([Parameter(Mandatory)][string]$Value)

    try {
        $uri = [Uri]::new($Value.Trim(), [UriKind]::Absolute)
    } catch {
        throw "PublicBaseUrl is not a valid absolute URL: $Value"
    }
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
        [switch]$AllowPrivateNetworkPattern
    )

    $origin = $Value.Trim()
    if ($AllowPrivateNetworkPattern -and $origin -in @(
            "http://192.168.*:[*]",
            "https://192.168.*:[*]"
        )) {
        return $origin
    }

    try {
        $uri = [Uri]::new($origin, [UriKind]::Absolute)
    } catch {
        throw "$Name contains an invalid Origin: $Value"
    }

    $isHttps = $uri.Scheme -eq "https"
    $isPrivateNetworkOrigin = $AllowPrivateNetworkPattern -and
        $uri.Scheme -in @("http", "https") -and
        (Test-PrivateNetworkIpv4 -HostName $uri.Host)
    if ((-not $isHttps -and -not $isPrivateNetworkOrigin) -or
        (-not $isPrivateNetworkOrigin -and -not $uri.IsDefaultPort) -or
        -not [string]::IsNullOrEmpty($uri.UserInfo) -or
        $uri.AbsolutePath -ne "/" -or
        -not [string]::IsNullOrEmpty($uri.Query) -or
        -not [string]::IsNullOrEmpty($uri.Fragment)) {
        throw "$Name must contain only HTTPS Origins or explicit 192.168.0.0/16 Origins without paths, query, fragment, credentials, or invalid ports: $Value"
    }

    return $uri.GetLeftPart([UriPartial]::Authority)
}

function Get-AllowedOrigins {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string]$RequiredOrigin,
        [switch]$AllowPrivateNetworkPattern
    )

    $origins = @($Value.Split(",") |
        ForEach-Object { $_.Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        ForEach-Object { Get-WebOrigin -Name $Name -Value $_ -AllowPrivateNetworkPattern:$AllowPrivateNetworkPattern } |
        Select-Object -Unique)

    if ($origins.Count -eq 0) {
        throw "$Name must contain at least one Origin."
    }
    if ($origins -notcontains $RequiredOrigin) {
        throw "$Name must include the public Origin ($RequiredOrigin)."
    }

    return $origins -join ","
}

function Get-ProdAllowedOrigins {
    param(
        [Parameter(Mandatory)][string]$Name,
        [AllowEmptyString()][string]$Value,
        [Parameter(Mandatory)][string]$PublicOrigin,
        [Parameter(Mandatory)][string]$AdminFrontendOrigin,
        [switch]$AllowPrivateNetworkPattern
    )

    $combinedOrigins = @(
        $Value,
        $PublicOrigin,
        $AdminFrontendOrigin
    ) -join ","

    return Get-AllowedOrigins `
        -Name $Name `
        -Value $combinedOrigins `
        -RequiredOrigin $PublicOrigin `
        -AllowPrivateNetworkPattern:$AllowPrivateNetworkPattern
}

function Get-HttpsUrl {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Value,
        [Parameter(Mandatory)][string]$ExpectedOrigin
    )

    try {
        $uri = [Uri]::new($Value.Trim(), [UriKind]::Absolute)
    } catch {
        throw "$Name is not a valid absolute URL: $Value"
    }
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
        [Alias("ExpectedStatus")]
        [Parameter(Mandatory)][int[]]$ExpectedStatuses,
        [ValidateSet("GET", "OPTIONS")][string]$Method = "GET",
        [hashtable]$Headers = @{},
        [ValidateRange(1, 10)][int]$MaxAttempts = 5,
        [ValidateRange(1, 60)][int]$RequestTimeoutSeconds = 20,
        [ValidateRange(0, 60)][int]$InitialDelaySeconds = 2
    )

    $expectedStatusText = $ExpectedStatuses -join ","
    $delaySeconds = $InitialDelaySeconds
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $webResponse = $null
        $actualStatus = $null
        $lastErrorMessage = $null

        try {
            $webResponse = Invoke-WebRequest `
                -UseBasicParsing `
                -Uri $Uri `
                -Method $Method `
                -Headers $Headers `
                -TimeoutSec $RequestTimeoutSeconds `
                -MaximumRedirection 0
            $actualStatus = [int]$webResponse.StatusCode
        } catch {
            $responseProperty = $_.Exception.PSObject.Properties["Response"]
            $errorResponse = if ($null -ne $responseProperty) {
                $responseProperty.Value
            } else {
                $null
            }
            if ($null -ne $errorResponse) {
                $webResponse = $errorResponse
                $actualStatus = [int]$errorResponse.StatusCode
            } else {
                $lastErrorMessage = $_.Exception.Message
            }
        }

        if ($ExpectedStatuses -contains $actualStatus) {
            return $webResponse
        }

        if ($attempt -eq $MaxAttempts) {
            if ($null -ne $actualStatus) {
                throw "Endpoint verification failed after $MaxAttempts attempts: $Uri (expected=$expectedStatusText, actual=$actualStatus)"
            }
            throw "Endpoint verification failed after $MaxAttempts attempts: $Uri (expected=$expectedStatusText, error=$lastErrorMessage)"
        }

        $failureReason = if ($null -ne $actualStatus) {
            "expected=$expectedStatusText, actual=$actualStatus"
        } else {
            "error=$lastErrorMessage"
        }
        Write-Warning "Endpoint verification attempt $attempt/$MaxAttempts failed: $Uri ($failureReason). Retrying in $delaySeconds seconds."
        Start-Sleep -Seconds $delaySeconds
        $delaySeconds = [Math]::Min($delaySeconds * 2, 10)
    }
}


function Get-HttpHeaderValues {
    param(
        [Parameter(Mandatory)]$Response,
        [Parameter(Mandatory)][string]$Name
    )

    $headersProperty = $Response.PSObject.Properties["Headers"]
    if ($null -eq $headersProperty -or $null -eq $headersProperty.Value) {
        return @()
    }

    $headers = $headersProperty.Value
    if ($null -ne $headers.PSObject.Methods["TryGetValues"]) {
        $values = $null
        if ($headers.TryGetValues($Name, [ref]$values)) {
            return @($values)
        }
        return @()
    }

    try {
        return @($headers[$Name])
    } catch {
        return @()
    }
}

function Test-HttpHeaderContainsToken {
    param(
        [Parameter(Mandatory)]$Response,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$ExpectedToken
    )

    $tokens = @(Get-HttpHeaderValues -Response $Response -Name $Name |
        ForEach-Object { ([string]$_).Split(",") } |
        ForEach-Object { $_.Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) })

    return $null -ne ($tokens | Where-Object {
        [string]::Equals($_, $ExpectedToken, [StringComparison]::OrdinalIgnoreCase)
    } | Select-Object -First 1)
}

function Assert-PublicProdEndpoints {
    param(
        [Parameter(Mandatory)][string]$BaseUrl,
        [Parameter(Mandatory)][string]$AdminFrontendOrigin
    )

    Assert-HttpStatus -Uri "$BaseUrl/" -ExpectedStatus 200 | Out-Null

    $csrfResponse = Assert-HttpStatus `
        -Uri "$BaseUrl/api/v1/auth/csrf" `
        -ExpectedStatus 200
    try {
        $csrfBody = $csrfResponse.Content | ConvertFrom-Json
    } catch {
        throw "Prod CSRF endpoint did not return valid JSON."
    }
    $csrfCode = [string](Get-JsonProperty -Object $csrfBody -Name "code")
    if ($csrfCode -ne "AUTH_CSRF_TOKEN_ISSUED_SUCCESS") {
        throw "Prod CSRF endpoint returned an unexpected response code: $csrfCode"
    }
    $setCookieHeader = @(Get-HttpHeaderValues -Response $csrfResponse -Name "Set-Cookie") -join ";"
    if ($setCookieHeader -notmatch "(?:^|[,;]\s*)XSRF-TOKEN=") {
        throw "Prod CSRF endpoint did not issue the XSRF-TOKEN cookie."
    }

    $corsResponse = Assert-HttpStatus `
        -Uri "$BaseUrl/api/v1/admin/me" `
        -Method "OPTIONS" `
        -Headers @{
            Origin                           = $AdminFrontendOrigin
            "Access-Control-Request-Method"  = "GET"
            "Access-Control-Request-Headers" = "content-type"
        } `
        -ExpectedStatus 200
    $allowedOrigins = @(Get-HttpHeaderValues -Response $corsResponse -Name "Access-Control-Allow-Origin")
    if ($allowedOrigins.Count -ne 1 -or $allowedOrigins[0] -ne $AdminFrontendOrigin) {
        throw "Prod Admin CORS preflight did not allow $AdminFrontendOrigin."
    }
    $allowedCredentials = @(Get-HttpHeaderValues -Response $corsResponse -Name "Access-Control-Allow-Credentials")
    if ($allowedCredentials.Count -ne 1 -or $allowedCredentials[0] -ne "true") {
        throw "Prod Admin CORS preflight did not allow credentials."
    }
    if (-not (Test-HttpHeaderContainsToken `
        -Response $corsResponse `
        -Name "Access-Control-Allow-Methods" `
        -ExpectedToken "GET")) {
        throw "Prod Admin CORS preflight did not allow GET."
    }
    if (-not (Test-HttpHeaderContainsToken `
        -Response $corsResponse `
        -Name "Access-Control-Allow-Headers" `
        -ExpectedToken "content-type")) {
        throw "Prod Admin CORS preflight did not allow content-type."
    }

    foreach ($frontendOrigin in @($BaseUrl, $AdminFrontendOrigin) | Select-Object -Unique) {
        $encodedFrontendOrigin = [Uri]::EscapeDataString($frontendOrigin)
        $oauthResponse = Assert-HttpStatus `
            -Uri "$BaseUrl/oauth/authorization/google?frontendOrigin=$encodedFrontendOrigin" `
            -ExpectedStatuses @(302, 303, 307, 308)
        $locationHeader = [string](@(Get-HttpHeaderValues -Response $oauthResponse -Name "Location")[0])
        if ([string]::IsNullOrWhiteSpace($locationHeader)) {
            throw "Prod OAuth endpoint did not return a Location header for $frontendOrigin."
        }
        try {
            $oauthLocation = [Uri]::new($locationHeader, [UriKind]::Absolute)
        } catch {
            throw "Prod OAuth endpoint returned an invalid Location header for $frontendOrigin."
        }
        if ($oauthLocation.Scheme -ne "https" -or
            $oauthLocation.Host -ne "accounts.google.com" -or
            $oauthLocation.AbsolutePath -ne "/o/oauth2/v2/auth") {
            throw "Prod OAuth endpoint returned an unexpected authorization Location for $frontendOrigin."
        }

        $oauthQuery = @{}
        foreach ($parameter in $oauthLocation.Query.TrimStart("?").Split("&")) {
            if ([string]::IsNullOrWhiteSpace($parameter)) {
                continue
            }
            $pair = $parameter.Split("=", 2)
            $key = [System.Net.WebUtility]::UrlDecode($pair[0])
            $value = if ($pair.Length -eq 2) {
                [System.Net.WebUtility]::UrlDecode($pair[1])
            } else {
                ""
            }
            if (-not $oauthQuery.ContainsKey($key)) {
                $oauthQuery[$key] = @()
            }
            $oauthQuery[$key] += $value
        }

        $redirectUris = @($oauthQuery["redirect_uri"])
        if ($redirectUris.Count -ne 1 -or
            $redirectUris[0] -ne "$BaseUrl/oauth/callback/google" -or
            @($oauthQuery["client_id"]).Count -ne 1 -or
            [string]::IsNullOrWhiteSpace([string]@($oauthQuery["client_id"])[0]) -or
            @($oauthQuery["state"]).Count -ne 1 -or
            [string]::IsNullOrWhiteSpace([string]@($oauthQuery["state"])[0])) {
            throw "Prod OAuth authorization Location is missing required query parameters for $frontendOrigin."
        }
    }
    foreach ($blockedPath in @(
        "/swagger-ui/index.html",
        "/v3/api-docs",
        "/actuator/health"
    )) {
        Assert-HttpStatus -Uri "$BaseUrl$blockedPath" -ExpectedStatus 404 | Out-Null
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
        "SPRING_PROFILES_ACTIVE: 'prod'",
        "PUBLIC_BASE_URL: $(ConvertTo-YamlSingleQuoted $PublicOrigin)",
        "CORS_ALLOWED_ORIGINS: $(ConvertTo-YamlSingleQuoted $CorsAllowedOrigins)",
        "OAUTH_REDIRECT_URI: $(ConvertTo-YamlSingleQuoted $FrontendRedirectUri)",
        "OAUTH_ALLOWED_FRONTEND_ORIGINS: $(ConvertTo-YamlSingleQuoted $OAuthAllowedFrontendOrigins)",
        "KAKAO_REDIRECT_URI: $(ConvertTo-YamlSingleQuoted "$PublicOrigin/oauth/callback/kakao")",
        "GOOGLE_REDIRECT_URI: $(ConvertTo-YamlSingleQuoted "$PublicOrigin/oauth/callback/google")",
        "PROFILE_IMAGE_BUCKET: $(ConvertTo-YamlSingleQuoted $ProfileImageBucket)",
        "PROFILE_IMAGE_PUBLIC_BASE_URL: 'https://storage.googleapis.com'"
    )

    [System.IO.File]::WriteAllLines(
        $Path,
        $lines,
        [System.Text.UTF8Encoding]::new($false)
    )
}

function Write-ResultFile {
    if ([string]::IsNullOrWhiteSpace($ResultPath)) {
        return
    }

    $resultDirectory = Split-Path -Parent $ResultPath
    if (-not [string]::IsNullOrWhiteSpace($resultDirectory)) {
        [System.IO.Directory]::CreateDirectory($resultDirectory) | Out-Null
    }
    $resultJson = $result | ConvertTo-Json -Depth 4
    [System.IO.File]::WriteAllText(
        $ResultPath,
        $resultJson,
        [System.Text.UTF8Encoding]::new($false)
    )
}

try {
    if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
        throw "gcloud CLI was not found. Check the Google Cloud CLI installation and login."
    }
    Assert-ApprovedImage

    $publicOrigin = Get-HttpsOrigin -Value $PublicBaseUrl
    $adminOrigin = Get-WebOrigin `
        -Name "AdminFrontendOrigin" `
        -Value $AdminFrontendOrigin
    if ([string]::IsNullOrWhiteSpace($FrontendRedirectUri)) {
        $FrontendRedirectUri = "$publicOrigin/app/oauth/callback"
    }
    $frontendRedirectUrl = Get-HttpsUrl `
        -Name "FrontendRedirectUri" `
        -Value $FrontendRedirectUri `
        -ExpectedOrigin $publicOrigin

    $corsOrigins = Get-ProdAllowedOrigins `
        -Name "CorsAllowedOrigins" `
        -Value $CorsAllowedOrigins `
        -PublicOrigin $publicOrigin `
        -AdminFrontendOrigin $adminOrigin `
        -AllowPrivateNetworkPattern

    $oauthFrontendOrigins = Get-ProdAllowedOrigins `
        -Name "OAuthAllowedFrontendOrigins" `
        -Value $OAuthAllowedFrontendOrigins `
        -PublicOrigin $publicOrigin `
        -AdminFrontendOrigin $adminOrigin

    Invoke-Gcloud -Arguments @(
        "iam", "service-accounts", "describe", $runtimeServiceAccount,
        "--project=$ProjectId",
        "--quiet"
    )
    Invoke-Gcloud -Arguments @(
        "compute", "networks", "describe", $VpcNetwork,
        "--project=$ProjectId",
        "--quiet"
    )
    Invoke-Gcloud -Arguments @(
        "compute", "networks", "subnets", "describe", $VpcSubnet,
        "--project=$ProjectId",
        "--region=$Region",
        "--quiet"
    )
    Invoke-Gcloud -Arguments @(
        "storage", "buckets", "describe", "gs://$ProfileImageBucket",
        "--project=$ProjectId",
        "--quiet"
    )

    $resolvedSecretVersions = [ordered]@{}
    foreach ($entry in $secretMap.GetEnumerator()) {
        $versionStateJson = Get-GcloudText -Arguments @(
            "secrets", "versions", "describe", "latest",
            "--secret=$($entry.Value)",
            "--project=$ProjectId",
            "--format=json"
        )
        $versionState = $versionStateJson | ConvertFrom-Json
        $state = [string](Get-JsonProperty -Object $versionState -Name "state")
        if ($state -ne "ENABLED") {
            throw "Latest Secret version is not enabled: $($entry.Value) ($($entry.Key))"
        }

        $versionName = [string](Get-JsonProperty -Object $versionState -Name "name")
        $versionMatch = [regex]::Match($versionName, "/versions/(?<version>[1-9]\d*)$")
        if (-not $versionMatch.Success) {
            throw "Could not resolve the numeric Secret version: $($entry.Value) ($($entry.Key))"
        }

        $version = $versionMatch.Groups["version"].Value
        $resolvedSecretVersions[$entry.Key] = $version
        $result.secretVersions[$entry.Key] = "$($entry.Value):$version"
    }

    $serviceState = Get-ServiceState
    Assert-PublicInvoker
    Assert-LoadBalancerOnlyService -ServiceState $serviceState
    $activeTraffic = @(Get-ActiveTrafficAllocations -ServiceState $serviceState)
    if ($activeTraffic.Count -eq 0) {
        Assert-ZeroTrafficBootstrap -ServiceState $serviceState
    } elseif ($activeTraffic.Count -eq 1 -and [int]$activeTraffic[0].percent -eq 100) {
        $result.previousRevision = [string]$activeTraffic[0].revisionName
    } else {
        throw "Existing Cloud Run service must be the approved zero-traffic bootstrap or have exactly one revision serving 100% traffic."
    }

    $environmentFile = New-TemporaryFile
    Write-EnvironmentFile `
        -Path $environmentFile.FullName `
        -PublicOrigin $publicOrigin `
        -FrontendRedirectUri $frontendRedirectUrl `
        -CorsAllowedOrigins $corsOrigins `
        -OAuthAllowedFrontendOrigins $oauthFrontendOrigins `
        -ProfileImageBucket $ProfileImageBucket

    $secretBindings = ($secretMap.GetEnumerator() | ForEach-Object {
        $version = $resolvedSecretVersions[$_.Key]
        "$($_.Key)=$($_.Value):$version"
    }) -join ","

    $shortCommit = $DeployCommit.Substring(0, 12)
    $deploymentTimestamp = Get-Date -Format "yyyyMMddHHmmss"
    $revisionSuffix = "$shortCommit-$deploymentTimestamp"
    $result.candidateRevision = "$ServiceName-$revisionSuffix"

    $deployArguments = @(
        "run", "deploy", $ServiceName,
        "--project=$ProjectId",
        "--region=$Region",
        "--image=$Image",
        "--revision-suffix=$revisionSuffix",
        "--no-traffic",
        "--service-account=$runtimeServiceAccount",
        "--execution-environment=gen2",
        "--port=8080",
        "--cpu=1",
        "--memory=1Gi",
        "--concurrency=40",
        "--timeout=60",
        "--min=0",
        "--max=3",
        "--ingress=internal-and-cloud-load-balancing",
        "--no-default-url",
        "--cpu-throttling",
        "--cpu-boost",
        "--network=$VpcNetwork",
        "--subnet=$VpcSubnet",
        "--vpc-egress=private-ranges-only",
        "--deploy-health-check",
        "--startup-probe=httpGet.path=/actuator/health,httpGet.port=8080,initialDelaySeconds=0,timeoutSeconds=3,periodSeconds=5,failureThreshold=24",
        "--liveness-probe=httpGet.path=/actuator/health/liveness,httpGet.port=8080,initialDelaySeconds=0,timeoutSeconds=3,periodSeconds=10,failureThreshold=3",
        "--readiness-probe=httpGet.path=/actuator/health/readiness,httpGet.port=8080,timeoutSeconds=3,periodSeconds=5,failureThreshold=3",
        "--env-vars-file=$($environmentFile.FullName)",
        "--set-secrets=$secretBindings",
        "--quiet"
    )
    Invoke-Gcloud -Arguments $deployArguments

    Wait-ForRevisionReady -RevisionName $result.candidateRevision -ExpectedImage $Image
    $result.candidateVerification = "passed"

    $trafficPromotionAttempted = $true
    Invoke-Gcloud -Arguments @(
        "run", "services", "update-traffic", $ServiceName,
        "--project=$ProjectId",
        "--region=$Region",
        "--to-revisions=$($result.candidateRevision)=100",
        "--quiet"
    )
    Wait-ForSingleRevisionTraffic -ExpectedRevision $result.candidateRevision
    $result.trafficPromotion = "passed"

    Assert-LoadBalancerOnlyService -ServiceState (Get-ServiceState)
    $result.serviceVerification = "passed"

    $result.publicBaseUrl = $publicOrigin
    if ($PublicSmokeEnabled) {
        Assert-PublicProdEndpoints `
            -BaseUrl $publicOrigin `
            -AdminFrontendOrigin $adminOrigin
        $result.publicVerification = "passed"
    } else {
        Write-Warning "Public smoke test is disabled until plimap.kr DNS and managed TLS are active."
    }

    $result.status = "succeeded"

    Write-Output "Cloud Run prod deployment and verification completed: $($result.candidateRevision)"
} catch {
    $result.error = $_.Exception.Message

    $shouldRollback = $false
    if ($trafficPromotionAttempted) {
        try {
            $failedServiceState = Get-ServiceState
            $failedTraffic = @(Get-ActiveTrafficAllocations -ServiceState $failedServiceState)
            $shouldRollback = @($failedTraffic | Where-Object {
                $_.revisionName -eq $result.candidateRevision -and [int]$_.percent -gt 0
            }).Count -gt 0
        } catch {
            Write-Warning "Could not inspect traffic after deployment failure; rollback will be attempted conservatively."
            $shouldRollback = $true
        }
    }

    if ($shouldRollback) {
        if (-not [string]::IsNullOrWhiteSpace($result.previousRevision)) {
            try {
                Invoke-Gcloud -Arguments @(
                    "run", "services", "update-traffic", $ServiceName,
                    "--project=$ProjectId",
                    "--region=$Region",
                    "--to-revisions=$($result.previousRevision)=100",
                    "--quiet"
                )
                Wait-ForSingleRevisionTraffic -ExpectedRevision $result.previousRevision
                Assert-LoadBalancerOnlyService -ServiceState (Get-ServiceState)
                if ($result.previousRevision -ne $bootstrapRevisionName -and $PublicSmokeEnabled) {
                    Assert-PublicProdEndpoints `
                        -BaseUrl $publicOrigin `
                        -AdminFrontendOrigin $adminOrigin
                }
                $result.rollback = "succeeded"
            } catch {
                $result.rollback = "failed: $($_.Exception.Message)"
            }
        } else {
            $result.rollback = "unavailable-first-deployment"
        }
    }

    throw
} finally {
    if ($null -ne $environmentFile) {
        Remove-Item -LiteralPath $environmentFile.FullName -Force -ErrorAction SilentlyContinue
    }
    Write-ResultFile
}

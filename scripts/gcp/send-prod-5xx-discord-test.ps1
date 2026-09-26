[CmdletBinding()]
param(
    [ValidatePattern("^[a-z][a-z0-9-]{4,28}[a-z0-9]$")]
    [string]$ProjectId = "plimap",
    [ValidatePattern("^[a-z]+-[a-z]+[0-9]$")]
    [string]$Region = "asia-northeast3",
    [ValidatePattern("^[a-z](?:[a-z0-9-]{0,47}[a-z0-9])?$")]
    [string]$SourceServiceName = "plimap-api-prod",
    [switch]$Apply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$responseMessage = [regex]::Unescape(
    '\uC6B4\uC601 5xx Discord \uC54C\uB9BC \uACBD\uB85C \uD14C\uC2A4\uD2B8\uC785\uB2C8\uB2E4.'
)
$logEntry = [ordered]@{
    event = "HTTP_5XX"
    testEvent = $true
    status = 500
    errorCode = "COMMON_500_INTERNAL_SERVER_ERROR"
    method = "GET"
    routeTemplate = "/synthetic/prod-5xx-alert-test"
    responseMessage = $responseMessage
    exceptionType = "java.lang.IllegalStateException"
}

Write-Output "Synthetic Prod 5xx alert test"
Write-Output "  Project / region: $ProjectId / $Region"
Write-Output "  Source service label: $SourceServiceName"
Write-Output "  Discord title: [TEST]"

if (-not $Apply) {
    Write-Output "Plan only. No log entry was written. Re-run with -Apply to send one test event."
    return
}

$accessToken = (@(& gcloud auth print-access-token) -join "").Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Failed to obtain a gcloud access token."
}
if ([string]::IsNullOrWhiteSpace($accessToken)) {
    throw "The gcloud access token is empty."
}

$writeRequest = [ordered]@{
    logName = "projects/$ProjectId/logs/plimap-prod-5xx-alert-test"
    resource = [ordered]@{
        type = "cloud_run_revision"
        labels = [ordered]@{
            project_id = $ProjectId
            service_name = $SourceServiceName
            revision_name = "synthetic-prod-5xx-test"
            configuration_name = $SourceServiceName
            location = $Region
        }
    }
    entries = @(
        [ordered]@{
            insertId = "synthetic-$([Guid]::NewGuid().ToString('N'))"
            timestamp = [DateTime]::UtcNow.ToString("o")
            severity = "ERROR"
            jsonPayload = $logEntry
        }
    )
}
$requestBody = $writeRequest | ConvertTo-Json -Depth 8 -Compress
$headers = @{ Authorization = "Bearer $accessToken" }

try {
    Invoke-RestMethod `
        -Method Post `
        -Uri "https://logging.googleapis.com/v2/entries:write" `
        -Headers $headers `
        -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($requestBody)) | Out-Null
} catch {
    throw "Failed to write the synthetic Prod 5xx log entry."
} finally {
    $headers.Authorization = $null
    $accessToken = $null
}

Write-Output "Synthetic test log was written. Check the Discord channel and Logs Explorer."

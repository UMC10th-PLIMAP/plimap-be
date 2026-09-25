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

$logEntry = [ordered]@{
    event = "HTTP_5XX"
    testEvent = $true
    status = 500
    errorCode = "COMMON_500_INTERNAL_SERVER_ERROR"
    method = "GET"
    routeTemplate = "/synthetic/prod-5xx-alert-test"
    responseMessage = "운영 5xx Discord 알림 경로 테스트입니다."
    exceptionType = "java.lang.IllegalStateException"
}
$jsonPayload = $logEntry | ConvertTo-Json -Compress
$resourceLabels = @(
    "project_id=$ProjectId",
    "service_name=$SourceServiceName",
    "revision_name=synthetic-prod-5xx-test",
    "configuration_name=$SourceServiceName",
    "location=$Region"
) -join ","

Write-Output "Synthetic Prod 5xx alert test"
Write-Output "  Project / region: $ProjectId / $Region"
Write-Output "  Source service label: $SourceServiceName"
Write-Output "  Discord title: [TEST]"

if (-not $Apply) {
    Write-Output "Plan only. No log entry was written. Re-run with -Apply to send one test event."
    return
}

$arguments = @(
    "logging", "write", "plimap-prod-5xx-alert-test", $jsonPayload,
    "--project=$ProjectId",
    "--payload-type=json",
    "--severity=ERROR",
    "--monitored-resource-type=cloud_run_revision",
    "--monitored-resource-labels=$resourceLabels",
    "--quiet"
)
& gcloud @arguments
if ($LASTEXITCODE -ne 0) {
    throw "Failed to write the synthetic Prod 5xx log entry."
}

Write-Output "Synthetic test log was written. Check the Discord channel and Logs Explorer."

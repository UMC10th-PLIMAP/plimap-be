$ErrorActionPreference = "Stop"

$configureScriptPath = Join-Path $PSScriptRoot "configure-prod-5xx-discord-alert.ps1"
$testEventScriptPath = Join-Path $PSScriptRoot "send-prod-5xx-discord-test.ps1"

foreach ($scriptPath in @($configureScriptPath, $testEventScriptPath)) {
    $tokens = $null
    $parseErrors = $null
    [System.Management.Automation.Language.Parser]::ParseFile(
        $scriptPath,
        [ref]$tokens,
        [ref]$parseErrors
    ) | Out-Null
    if ($parseErrors.Count -gt 0) {
        throw "$scriptPath contains PowerShell parse errors."
    }
}

$configureContent = Get-Content -LiteralPath $configureScriptPath -Raw
$testEventContent = Get-Content -LiteralPath $testEventScriptPath -Raw
$expectedTestMessageEscapes = @(
    '\uC6B4\uC601 5xx Discord \uC54C\uB9BC \uACBD\uB85C ',
    '\uD14C\uC2A4\uD2B8\uC785\uB2C8\uB2E4.'
)

$requiredConfigurePatterns = @(
    'if \(-not \$Apply\)',
    'Runtime and build service accounts must be different',
    '--function=forward_prod_5xx_to_discord',
    '--base-image=python313',
    '--build-service-account=\$buildServiceAccountResource',
    '--set-secrets=DISCORD_WEBHOOK_URL=',
    '--no-allow-unauthenticated',
    '--max-retry-attempts=1',
    '\$_\.attribute -eq "type"',
    '\$eventTypeFilter\.value',
    'resource\.labels\.service_name=',
    'jsonPayload\.event="HTTP_5XX"',
    'jsonPayload\.status>=500',
    'jsonPayload\.status<600',
    'roles/secretmanager\.secretAccessor',
    'roles/run\.builder',
    'roles/run\.invoker',
    'roles/pubsub\.publisher',
    'Get-TopicMessageRetentionDuration',
    '--clear-message-retention-duration'
)
foreach ($pattern in $requiredConfigurePatterns) {
    if ($configureContent -notmatch $pattern) {
        throw "Missing required alert configuration: $pattern"
    }
}

if ($configureContent -match 'secrets\s+versions\s+access') {
    throw "The configuration script must not read Secret payloads."
}
if ($configureContent -match 'DISCORD_WEBHOOK_URL\s*=\s*https?://') {
    throw "The configuration script must not contain a Discord webhook URL."
}
if ($configureContent -notmatch 'plimap-prod-5xx-build') {
    throw "The configuration script must use a dedicated build service account."
}
if ($configureContent -match '--message-retention-duration=') {
    throw "The alert topic must not retain acknowledged LogEntries for replay."
}

$pubsubIamBlock = [regex]::Match(
    $configureContent,
    '(?s)"pubsub", "topics", "add-iam-policy-binding".*?"--quiet"'
)
if (-not $pubsubIamBlock.Success) {
    throw "The Pub/Sub publisher IAM binding block is missing."
}
if ($pubsubIamBlock.Value -match '--condition') {
    throw "Pub/Sub topic IAM binding does not support --condition."
}

foreach ($pattern in @(
    'if \(-not \$Apply\)',
    'testEvent\s*=\s*\$true',
    'gcloud auth print-access-token',
    'logging\.googleapis\.com/v2/entries:write',
    'type = "cloud_run_revision"',
    'severity = "ERROR"',
    'Encoding\]::UTF8\.GetBytes',
    '\$requestBody\.Replace\(',
    'The synthetic response message encoding is invalid'
)) {
    if ($testEventContent -notmatch $pattern) {
        throw "Missing required synthetic test protection: $pattern"
    }
}
foreach ($expectedTestMessageEscape in $expectedTestMessageEscapes) {
    if (-not $testEventContent.Contains($expectedTestMessageEscape)) {
        throw "The synthetic test response message Unicode escape is invalid."
    }
}

Write-Output "Prod 5xx Discord alert script tests passed."

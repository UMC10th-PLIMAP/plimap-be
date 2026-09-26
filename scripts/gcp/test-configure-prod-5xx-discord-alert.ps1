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

$requiredConfigurePatterns = @(
    'if \(-not \$Apply\)',
    'Runtime and build service accounts must be different',
    '--function=forward_prod_5xx_to_discord',
    '--base-image=python313',
    '--build-service-account=\$buildServiceAccountResource',
    '--set-secrets=DISCORD_WEBHOOK_URL=',
    '--no-allow-unauthenticated',
    '--max-retry-attempts=1',
    '\$trigger\.eventFilters\.type',
    'resource\.labels\.service_name=',
    'jsonPayload\.event="HTTP_5XX"',
    'jsonPayload\.status>=500',
    'jsonPayload\.status<600',
    'roles/secretmanager\.secretAccessor',
    'roles/run\.builder',
    'roles/run\.invoker',
    'roles/pubsub\.publisher'
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

foreach ($pattern in @(
    'if \(-not \$Apply\)',
    'testEvent\s*=\s*\$true',
    '--monitored-resource-type=cloud_run_revision',
    '--severity=ERROR'
)) {
    if ($testEventContent -notmatch $pattern) {
        throw "Missing required synthetic test protection: $pattern"
    }
}

Write-Output "Prod 5xx Discord alert script tests passed."

[CmdletBinding()]
param(
    [ValidatePattern("^[a-z][a-z0-9-]{4,28}[a-z0-9]$")]
    [string]$ProjectId = "plimap",
    [ValidatePattern("^[a-z]+-[a-z]+[0-9]$")]
    [string]$Region = "asia-northeast3",
    [ValidatePattern("^[a-z](?:[a-z0-9-]{0,47}[a-z0-9])?$")]
    [string]$SourceServiceName = "plimap-api-prod",
    [ValidatePattern("^[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?$")]
    [string]$SinkName = "plimap-prod-http-5xx-sink",
    [ValidatePattern("^[a-zA-Z][a-zA-Z0-9-_.~+%]{2,254}$")]
    [string]$TopicName = "plimap-prod-http-5xx-events",
    [ValidatePattern("^[a-z](?:[a-z0-9-]{0,47}[a-z0-9])?$")]
    [string]$FunctionName = "plimap-prod-5xx-discord",
    [ValidatePattern("^[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?$")]
    [string]$TriggerName = "plimap-prod-5xx-discord-trigger",
    [ValidatePattern("^[a-zA-Z][a-zA-Z0-9_-]{0,254}$")]
    [string]$SecretName = "plimap-prod-discord-webhook-url",
    [ValidatePattern("^[a-z][a-z0-9-]{4,28}[a-z0-9]$")]
    [string]$ServiceAccountId = "plimap-prod-5xx-alert",
    [ValidatePattern("^[a-z][a-z0-9-]{4,28}[a-z0-9]$")]
    [string]$BuildServiceAccountId = "plimap-prod-5xx-build",
    [switch]$Apply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$serviceAccount = "$ServiceAccountId@$ProjectId.iam.gserviceaccount.com"
$buildServiceAccount = "$BuildServiceAccountId@$ProjectId.iam.gserviceaccount.com"
$buildServiceAccountResource = "projects/$ProjectId/serviceAccounts/$buildServiceAccount"
$topicResource = "projects/$ProjectId/topics/$TopicName"
$sinkDestination = "pubsub.googleapis.com/$topicResource"
$functionSource = Join-Path $PSScriptRoot "prod-5xx-discord"
$requiredServices = @(
    "artifactregistry.googleapis.com",
    "cloudbuild.googleapis.com",
    "eventarc.googleapis.com",
    "logging.googleapis.com",
    "pubsub.googleapis.com",
    "run.googleapis.com",
    "secretmanager.googleapis.com"
)
$loggingFilter = @(
    'resource.type="cloud_run_revision"',
    ('resource.labels.service_name="{0}"' -f $SourceServiceName),
    'severity>=ERROR',
    'jsonPayload.event="HTTP_5XX"',
    'jsonPayload.status>=500',
    'jsonPayload.status<600'
) -join " AND "

if ($ServiceAccountId -eq $BuildServiceAccountId) {
    throw "Runtime and build service accounts must be different."
}

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
    return ($output -join [Environment]::NewLine).Trim()
}

function Assert-FunctionSource {
    foreach ($fileName in @("main.py", "requirements.txt", ".gcloudignore")) {
        $path = Join-Path $functionSource $fileName
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Cloud Run function source file is missing: $path"
        }
    }
}

function Assert-SecretMetadata {
    Get-GcloudText -Arguments @(
        "secrets", "describe", $SecretName,
        "--project=$ProjectId",
        "--format=value(name)"
    ) | Out-Null

    $enabledVersion = Get-GcloudText -Arguments @(
        "secrets", "versions", "list", $SecretName,
        "--project=$ProjectId",
        "--filter=state=ENABLED",
        "--limit=1",
        "--sort-by=~createTime",
        "--format=value(name)"
    )
    if ([string]::IsNullOrWhiteSpace($enabledVersion)) {
        throw "Secret $SecretName must have an ENABLED version."
    }
}

function Test-ServiceAccountExists {
    param([Parameter(Mandatory)][string]$Email)

    $account = Get-GcloudText -Arguments @(
        "iam", "service-accounts", "list",
        "--project=$ProjectId",
        "--filter=email=$Email",
        "--format=value(email)"
    )
    return $account -eq $Email
}

function Test-TopicExists {
    $topic = Get-GcloudText -Arguments @(
        "pubsub", "topics", "list",
        "--project=$ProjectId",
        "--filter=name:$topicResource",
        "--format=value(name)"
    )
    return @($topic -split "\r?\n") -contains $topicResource
}

function Test-TriggerExists {
    $trigger = Get-GcloudText -Arguments @(
        "eventarc", "triggers", "list",
        "--project=$ProjectId",
        "--location=$Region",
        "--filter=name:$TriggerName",
        "--format=value(name)"
    )
    return $trigger -eq $TriggerName
}

function Test-SinkExists {
    $sink = Get-GcloudText -Arguments @(
        "logging", "sinks", "list",
        "--project=$ProjectId",
        "--filter=name=$SinkName",
        "--format=value(name)"
    )
    return $sink -eq $SinkName
}

Assert-FunctionSource
Get-GcloudText -Arguments @(
    "projects", "describe", $ProjectId,
    "--format=value(projectId)"
) | Out-Null

Write-Output "Prod 5xx Discord alert configuration"
Write-Output "  Project / region: $ProjectId / $Region"
Write-Output "  Source service: $SourceServiceName"
Write-Output "  Sink / topic: $SinkName / $TopicName"
Write-Output "  Function / trigger: $FunctionName / $TriggerName"
Write-Output "  Secret ID: $SecretName"
Write-Output "  Runtime / build service accounts: $ServiceAccountId / $BuildServiceAccountId"
Write-Output "  Delivery policy: one attempt, no retry or DLQ"

if (-not $Apply) {
    Write-Output "Plan only. No GCP resource was changed. Re-run with -Apply after the Secret is ready."
    return
}

$enableServiceArguments = @("services", "enable") + $requiredServices + @(
    "--project=$ProjectId",
    "--quiet"
)
Invoke-Gcloud -Arguments $enableServiceArguments

Assert-SecretMetadata

if (-not (Test-ServiceAccountExists -Email $serviceAccount)) {
    Invoke-Gcloud -Arguments @(
        "iam", "service-accounts", "create", $ServiceAccountId,
        "--project=$ProjectId",
        "--display-name=PLIMAP Prod 5xx Discord alert",
        "--quiet"
    )
}
if (-not (Test-ServiceAccountExists -Email $buildServiceAccount)) {
    Invoke-Gcloud -Arguments @(
        "iam", "service-accounts", "create", $BuildServiceAccountId,
        "--project=$ProjectId",
        "--display-name=PLIMAP Prod 5xx Discord build",
        "--quiet"
    )
}

Invoke-Gcloud -Arguments @(
    "projects", "add-iam-policy-binding", $ProjectId,
    "--member=serviceAccount:$buildServiceAccount",
    "--role=roles/run.builder",
    "--condition=None",
    "--quiet"
)

Invoke-Gcloud -Arguments @(
    "projects", "add-iam-policy-binding", $ProjectId,
    "--member=serviceAccount:$serviceAccount",
    "--role=roles/eventarc.eventReceiver",
    "--condition=None",
    "--quiet"
)
Invoke-Gcloud -Arguments @(
    "secrets", "add-iam-policy-binding", $SecretName,
    "--project=$ProjectId",
    "--member=serviceAccount:$serviceAccount",
    "--role=roles/secretmanager.secretAccessor",
    "--condition=None",
    "--quiet"
)

if (-not (Test-TopicExists)) {
    Invoke-Gcloud -Arguments @(
        "pubsub", "topics", "create", $TopicName,
        "--project=$ProjectId",
        "--message-retention-duration=86400s",
        "--quiet"
    )
}

Invoke-Gcloud -Arguments @(
    "run", "deploy", $FunctionName,
    "--project=$ProjectId",
    "--region=$Region",
    "--source=$functionSource",
    "--function=forward_prod_5xx_to_discord",
    "--base-image=python313",
    "--build-service-account=$buildServiceAccountResource",
    "--service-account=$serviceAccount",
    "--set-env-vars=GOOGLE_CLOUD_PROJECT=$ProjectId",
    "--set-secrets=DISCORD_WEBHOOK_URL=$($SecretName):latest",
    "--ingress=internal",
    "--no-allow-unauthenticated",
    "--concurrency=1",
    "--timeout=10s",
    "--cpu=1",
    "--memory=256Mi",
    "--min=0",
    "--max=3",
    "--labels=managed-by=plimap-script,purpose=prod-5xx-discord",
    "--quiet"
)
Invoke-Gcloud -Arguments @(
    "run", "services", "add-iam-policy-binding", $FunctionName,
    "--project=$ProjectId",
    "--region=$Region",
    "--member=serviceAccount:$serviceAccount",
    "--role=roles/run.invoker",
    "--condition=None",
    "--quiet"
)

if (Test-TriggerExists) {
    $triggerJson = Get-GcloudText -Arguments @(
        "eventarc", "triggers", "describe", $TriggerName,
        "--project=$ProjectId",
        "--location=$Region",
        "--format=json"
    )
    $trigger = $triggerJson | ConvertFrom-Json
    $expectedTopic = $topicResource
    $actualTopic = [string]$trigger.transport.pubsub.topic
    $actualService = [string]$trigger.destination.cloudRun.service
    $actualServiceAccount = [string]$trigger.serviceAccount
    $maxAttempts = [int]$trigger.retryPolicy.maxAttempts
    $actualEventType = [string]$trigger.eventFilters.type
    if ($actualTopic -ne $expectedTopic -or
        $actualService -ne $FunctionName -or
        $actualServiceAccount -ne $serviceAccount -or
        $maxAttempts -ne 1 -or
        $actualEventType -ne "google.cloud.pubsub.topic.v1.messagePublished") {
        throw "Existing Eventarc trigger differs from the approved configuration."
    }
} else {
    Invoke-Gcloud -Arguments @(
        "eventarc", "triggers", "create", $TriggerName,
        "--project=$ProjectId",
        "--location=$Region",
        "--destination-run-service=$FunctionName",
        "--destination-run-region=$Region",
        "--event-filters=type=google.cloud.pubsub.topic.v1.messagePublished",
        "--transport-topic=$topicResource",
        "--service-account=$serviceAccount",
        "--max-retry-attempts=1",
        "--labels=managed-by=plimap-script,purpose=prod-5xx-discord",
        "--quiet"
    )
}

if (Test-SinkExists) {
    Invoke-Gcloud -Arguments @(
        "logging", "sinks", "update", $SinkName, $sinkDestination,
        "--project=$ProjectId",
        "--log-filter=$loggingFilter",
        "--quiet"
    )
} else {
    Invoke-Gcloud -Arguments @(
        "logging", "sinks", "create", $SinkName, $sinkDestination,
        "--project=$ProjectId",
        "--log-filter=$loggingFilter",
        "--quiet"
    )
}

$writerIdentity = Get-GcloudText -Arguments @(
    "logging", "sinks", "describe", $SinkName,
    "--project=$ProjectId",
    "--format=value(writerIdentity)"
)
if ([string]::IsNullOrWhiteSpace($writerIdentity)) {
    throw "Logging sink writer identity is missing."
}
Invoke-Gcloud -Arguments @(
    "pubsub", "topics", "add-iam-policy-binding", $TopicName,
    "--project=$ProjectId",
    "--member=$writerIdentity",
    "--role=roles/pubsub.publisher",
    "--condition=None",
    "--quiet"
)

Write-Output "Prod 5xx Discord alert resources are configured."
Write-Output "No test event was sent. Use send-prod-5xx-discord-test.ps1 separately."

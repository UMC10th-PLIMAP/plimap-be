function Get-StartupProbePolicy {
    param([ValidateSet('baseline', 'short')][string]$Policy = 'baseline')
    $period = if ($Policy -eq 'short') { 4 } else { 5 }
    $failures = if ($Policy -eq 'short') { 30 } else { 24 }
    return [ordered]@{ path = '/actuator/health'; port = 8080; initialDelaySeconds = 0; timeoutSeconds = 3; periodSeconds = $period; failureThreshold = $failures }
}

function Get-StartupProbeArgument {
    param([ValidateSet('baseline', 'short')][string]$Policy = 'baseline')
    $probe = Get-StartupProbePolicy -Policy $Policy
    return "httpGet.path=$($probe.path),httpGet.port=$($probe.port),initialDelaySeconds=$($probe.initialDelaySeconds),timeoutSeconds=$($probe.timeoutSeconds),periodSeconds=$($probe.periodSeconds),failureThreshold=$($probe.failureThreshold)"
}

function Get-StartupExperimentSettings {
    param([Parameter(Mandatory)][ValidateSet('baseline', 'startup', 'aot')][string]$Mode)
    return [ordered]@{
        appCdsEnabled = 'false'
        springAotEnabled = if ($Mode -eq 'aot') { 'true' } else { 'false' }
        startupMonitoringEnabled = 'true'
        startupProbePolicy = if ($Mode -eq 'startup') { 'short' } else { 'baseline' }
    }
}

function Assert-StartupExperimentCombination {
    param([string]$AppCdsEnabled, [string]$SpringAotEnabled, [string]$StartupProbePolicy)
    if ($StartupProbePolicy -eq 'short' -and $AppCdsEnabled -ne 'false') {
        throw 'The short startup experiment requires AppCDS OFF for isolated comparison.'
    }
    if ($SpringAotEnabled -eq 'true' -and ($AppCdsEnabled -ne 'false' -or $StartupProbePolicy -ne 'baseline')) {
        throw 'Spring AOT requires AppCDS OFF and the baseline startup probe policy for isolated comparison.'
    }
}

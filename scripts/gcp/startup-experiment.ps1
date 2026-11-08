function Get-StartupProbePolicy {
    param([ValidateSet('baseline', 'short', 'quick')][string]$Policy = 'baseline')
    $period = switch ($Policy) { 'short' { 4 }; 'quick' { 3 }; default { 5 } }
    $failures = switch ($Policy) { 'short' { 30 }; 'quick' { 40 }; default { 24 } }
    return [ordered]@{ path = '/actuator/health'; port = 8080; initialDelaySeconds = 0; timeoutSeconds = 3; periodSeconds = $period; failureThreshold = $failures }
}

function Get-StartupProbeArgument {
    param([ValidateSet('baseline', 'short', 'quick')][string]$Policy = 'baseline')
    $probe = Get-StartupProbePolicy -Policy $Policy
    return "httpGet.path=$($probe.path),httpGet.port=$($probe.port),initialDelaySeconds=$($probe.initialDelaySeconds),timeoutSeconds=$($probe.timeoutSeconds),periodSeconds=$($probe.periodSeconds),failureThreshold=$($probe.failureThreshold)"
}

function Get-StartupExperimentSettings {
    param([Parameter(Mandatory)][ValidateSet('baseline', 'startup', 'aot', 'aot-quick', 'combined', 'combined-baseline')][string]$Mode)
    return [ordered]@{
        appCdsEnabled = if ($Mode -in @('combined', 'combined-baseline')) { 'true' } else { 'false' }
        springAotEnabled = if ($Mode -in @('aot', 'aot-quick', 'combined', 'combined-baseline')) { 'true' } else { 'false' }
        startupMonitoringEnabled = 'true'
        startupProbePolicy = switch ($Mode) { 'startup' { 'short' }; { $_ -in @('aot-quick', 'combined') } { 'quick' }; default { 'baseline' } }
    }
}

function Assert-StartupExperimentCombination {
    param([string]$AppCdsEnabled, [string]$SpringAotEnabled, [string]$StartupProbePolicy)
    if ($AppCdsEnabled -notin @('true', 'false') -or $SpringAotEnabled -notin @('true', 'false')) {
        throw 'AppCDS and Spring AOT flags must be true or false.'
    }
    Get-StartupProbePolicy -Policy $StartupProbePolicy | Out-Null
    # Preserve the ordinary AppCDS-only deployment default as well as named experiments.
    if ($AppCdsEnabled -eq 'true' -and $SpringAotEnabled -eq 'false' -and $StartupProbePolicy -eq 'baseline') { return }
    foreach ($mode in @('baseline', 'startup', 'aot', 'aot-quick', 'combined', 'combined-baseline')) {
        $settings = Get-StartupExperimentSettings $mode
        if ($AppCdsEnabled -eq $settings.appCdsEnabled -and $SpringAotEnabled -eq $settings.springAotEnabled -and
            $StartupProbePolicy -eq $settings.startupProbePolicy) { return }
    }
    throw 'Select a supported experiment combination so the configuration can be identified and restored.'
}

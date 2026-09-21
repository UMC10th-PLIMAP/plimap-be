$ErrorActionPreference = "Stop"

foreach ($environment in @("dev", "prod")) {
    $scriptPath = Join-Path $PSScriptRoot "deploy-$environment.ps1"
    $tokens = $null
    $parseErrors = $null
    $scriptAst = [System.Management.Automation.Language.Parser]::ParseFile(
        $scriptPath, [ref]$tokens, [ref]$parseErrors
    )
    if ($parseErrors.Count -gt 0) {
        throw "$scriptPath contains PowerShell parse errors."
    }
    foreach ($functionName in @("ConvertTo-YamlSingleQuoted", "Write-EnvironmentFile")) {
        $function = $scriptAst.Find({
            param($node)
            $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and
                $node.Name -eq $functionName
        }, $true)
        if ($null -eq $function) {
            throw "$functionName was not found in $scriptPath."
        }
        Invoke-Expression $function.Extent.Text
    }

    $environmentFile = New-TemporaryFile
    try {
        $arguments = @{
            Path = $environmentFile.FullName
            PublicOrigin = "https://example.test"
            FrontendRedirectUri = "https://example.test/app/oauth/callback"
            CorsAllowedOrigins = "https://example.test"
            OAuthAllowedFrontendOrigins = "https://example.test"
            ProfileImageBucket = "test-bucket"
        }
        Write-EnvironmentFile @arguments
        $content = Get-Content -LiteralPath $environmentFile.FullName -Raw
        if ($content -notmatch "AUTH_DEMO_ENABLED: 'false'" -or
            $content -notmatch "AUTH_DEMO_MEMBER_ID: '0'") {
            throw "$environment must disable demo login by default."
        }

        Write-EnvironmentFile @arguments -DemoEnabled "true" -DemoMemberId 42
        $content = Get-Content -LiteralPath $environmentFile.FullName -Raw
        if ($content -notmatch "AUTH_DEMO_ENABLED: 'true'" -or
            $content -notmatch "AUTH_DEMO_MEMBER_ID: '42'") {
            throw "$environment lost the configured demo login settings."
        }
    } finally {
        Remove-Item -LiteralPath $environmentFile.FullName -Force
    }
}

Write-Output "Dev and Prod demo environment tests passed."

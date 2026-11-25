<#
.SYNOPSIS
Runs the repeatable clean-install acceptance checks documented for SCRUM-143 / F117.

.DESCRIPTION
Run from any directory after cloning the repository. By default this script uses
npm ci so dashboard dependencies match package-lock.json exactly. Use
-SkipNpmCi only for a quick repeat run on an already verified checkout.
#>
[CmdletBinding()]
param(
    [switch]$SkipNpmCi
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$farmSim = Join-Path $repoRoot 'src\farm-sim'
$dashboard = Join-Path $repoRoot 'src\dashboard'

function Assert-LastExitCode {
    param([Parameter(Mandatory = $true)][string]$Step)
    if ($LASTEXITCODE -ne 0) {
        throw "$Step failed with exit code $LASTEXITCODE."
    }
}

Write-Host '=== 1/8 Environment ===' -ForegroundColor Cyan
& (Join-Path $PSScriptRoot 'check-environment.ps1')
Assert-LastExitCode 'Environment check'

Write-Host '=== 2/8 Java build ===' -ForegroundColor Cyan
& (Join-Path $farmSim 'build.ps1')
Assert-LastExitCode 'Java build'

Write-Host '=== 3/8 Core Java acceptance tests ===' -ForegroundColor Cyan
& (Join-Path $farmSim 'run.ps1') -AcceptanceTests
Assert-LastExitCode 'Core Java acceptance tests'

Write-Host '=== 4/8 F52 scheduled automatic retry ===' -ForegroundColor Cyan
& (Join-Path $farmSim 'run.ps1') -TestClass AutomaticRetryTest
Assert-LastExitCode 'F52 automatic retry test'

Write-Host '=== 5/8 F87 background timeout monitor ===' -ForegroundColor Cyan
& (Join-Path $farmSim 'run.ps1') -TestClass AlertTimeoutMonitorTest
Assert-LastExitCode 'F87 timeout monitor test'

Write-Host '=== 6/8 F93 loopback SMTP delivery ===' -ForegroundColor Cyan
& (Join-Path $farmSim 'run.ps1') -TestClass EmailIntegrationTest
Assert-LastExitCode 'F93 email integration test'

$npm = Get-Command npm.cmd -ErrorAction SilentlyContinue
if (-not $npm) { $npm = Get-Command npm -ErrorAction Stop }

Push-Location $dashboard
try {
    if (-not $SkipNpmCi) {
        Write-Host '=== 7/8 Reproducible dashboard install (npm ci) ===' -ForegroundColor Cyan
        & $npm.Source ci
        Assert-LastExitCode 'npm ci'
    } else {
        Write-Host '=== 7/8 Reusing existing node_modules (-SkipNpmCi) ===' -ForegroundColor Yellow
        if (-not (Test-Path -LiteralPath (Join-Path $dashboard 'node_modules') -PathType Container)) {
            throw 'node_modules does not exist; run without -SkipNpmCi.'
        }
    }

    Write-Host '=== 8/8 Dashboard lint and production build ===' -ForegroundColor Cyan
    & $npm.Source run lint
    Assert-LastExitCode 'Dashboard lint'
    & $npm.Source run build
    Assert-LastExitCode 'Dashboard production build'
} finally {
    Pop-Location
}

Write-Host ''
Write-Host '[PASS] Installation verification completed.' -ForegroundColor Green

<#
.SYNOPSIS
Builds when necessary, selects a compatible Java runtime and starts the farm simulator.
#>
[CmdletBinding(PositionalBinding = $false)]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$AppArgs
)

$ErrorActionPreference = 'Stop'

function Get-JavaMajorVersion {
    param([Parameter(Mandatory = $true)][string]$JavaExe)

    try {
        $startInfo = New-Object System.Diagnostics.ProcessStartInfo
        $startInfo.FileName = $JavaExe
        $startInfo.Arguments = '-version'
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardError = $true
        $startInfo.RedirectStandardOutput = $true
        $process = New-Object System.Diagnostics.Process
        $process.StartInfo = $startInfo
        [void]$process.Start()
        $stderr = $process.StandardError.ReadToEndAsync()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        if (-not $process.WaitForExit(5000)) {
            $process.Kill()
            return $null
        }
        $versionText = $stderr.Result + $stdout.Result
        if ($process.ExitCode -ne 0) { return $null }
        if ($versionText -match 'version\s+"(?<version>\d+(?:\.\d+)*)') {
            $parts = $Matches.version.Split('.')
            if ($parts[0] -eq '1' -and $parts.Count -gt 1) {
                return [int]$parts[1]
            }
            return [int]$parts[0]
        }
    } catch {
        return $null
    }

    return $null
}

function Find-CompatibleJava {
    $candidates = [System.Collections.Generic.List[string]]::new()

    if ($env:JAVA_HOME) {
        $candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe'))
    }

    if ($env:USERPROFILE) {
        $ideaJdks = Join-Path $env:USERPROFILE '.jdks'
        if (Test-Path -LiteralPath $ideaJdks -PathType Container) {
            Get-ChildItem -LiteralPath $ideaJdks -Directory -ErrorAction SilentlyContinue |
                ForEach-Object { $candidates.Add((Join-Path $_.FullName 'bin\java.exe')) }
        }
    }

    $pathJava = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($pathJava) {
        $candidates.Add($pathJava.Source)
    }

    $seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($javaExe in $candidates) {
        if (-not $javaExe -or -not (Test-Path -LiteralPath $javaExe -PathType Leaf)) {
            continue
        }

        $resolvedJava = (Resolve-Path -LiteralPath $javaExe).Path
        if (-not $seen.Add($resolvedJava)) {
            continue
        }

        $javacExe = Join-Path (Split-Path -Parent $resolvedJava) 'javac.exe'
        if (-not (Test-Path -LiteralPath $javacExe -PathType Leaf)) {
            continue
        }
        $major = Get-JavaMajorVersion -JavaExe $resolvedJava
        if ($major -ge 17) {
            return [pscustomobject]@{
                JavaExe = $resolvedJava
                Major = $major
            }
        }
    }

    return $null
}

$java = Find-CompatibleJava
if (-not $java) {
    Write-Host '[ERROR] Khong tim thay Java 17+.' -ForegroundColor Red
    Write-Host 'Cai bang: winget install --id EclipseAdoptium.Temurin.17.JDK -e'
    exit 1
}

$mainClass = Join-Path $PSScriptRoot 'out\farm\Main.class'
if (-not (Test-Path -LiteralPath $mainClass -PathType Leaf)) {
    Write-Host '[INFO] Chua co ban build; dang bien dich truoc khi chay.' -ForegroundColor Yellow
    & (Join-Path $PSScriptRoot 'build.ps1')
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}

Push-Location $PSScriptRoot
try {
    Write-Host "[RUN] Java $($java.Major): $($java.JavaExe)"
    & $java.JavaExe -cp (Join-Path $PSScriptRoot 'out') farm.Main @AppArgs
    $runExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $runExitCode

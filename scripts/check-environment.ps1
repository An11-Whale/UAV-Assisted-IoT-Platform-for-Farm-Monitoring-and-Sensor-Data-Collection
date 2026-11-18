<#
.SYNOPSIS
Validates the JDK, Node.js and npm versions required by the integrated farm demo.
#>
[CmdletBinding()]
param()

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

function Find-CompatibleJdk {
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
                JavacExe = $javacExe
                Major = $major
            }
        }
    }

    return $null
}

function Get-CommandMajorVersion {
    param(
        [Parameter(Mandatory = $true)][string]$Command,
        [Parameter(Mandatory = $true)][string]$VersionArgument
    )

    $resolved = Get-Command $Command -ErrorAction SilentlyContinue
    if (-not $resolved) {
        return $null
    }

    try {
        $versionText = (& $resolved.Source $VersionArgument 2>&1 | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or $versionText -notmatch 'v?(?<major>\d+)') {
            return $null
        }
        return [pscustomobject]@{
            Path = $resolved.Source
            Version = $versionText
            Major = [int]$Matches.major
        }
    } catch {
        return $null
    }
}

$allReady = $true
$jdk = Find-CompatibleJdk
if ($jdk) {
    Write-Host "[OK] JDK $($jdk.Major): $($jdk.JavaExe)" -ForegroundColor Green
} else {
    $allReady = $false
    Write-Host '[FAIL] Khong tim thay JDK 17+ co ca java va javac.' -ForegroundColor Red
    Write-Host '       Cai bang: winget install --id EclipseAdoptium.Temurin.17.JDK -e'
    Write-Host '       Neu IntelliJ da tai JDK, script cung tu dong tim trong %USERPROFILE%\.jdks.'
}

$node = Get-CommandMajorVersion -Command 'node.exe' -VersionArgument '--version'
if ($node -and $node.Major -ge 20) {
    Write-Host "[OK] Node.js $($node.Version): $($node.Path)" -ForegroundColor Green
} else {
    $allReady = $false
    if ($node) {
        Write-Host "[FAIL] Node.js $($node.Version) qua cu; can Node.js 20+." -ForegroundColor Red
    } else {
        Write-Host '[FAIL] Khong tim thay Node.js 20+.' -ForegroundColor Red
    }
    Write-Host '       Cai bang: winget install --id OpenJS.NodeJS.LTS -e'
}

$npm = Get-CommandMajorVersion -Command 'npm.cmd' -VersionArgument '--version'
if (-not $npm) {
    $npm = Get-CommandMajorVersion -Command 'npm' -VersionArgument '--version'
}
if ($npm) {
    Write-Host "[OK] npm $($npm.Version): $($npm.Path)" -ForegroundColor Green
} else {
    $allReady = $false
    Write-Host '[FAIL] Khong tim thay npm.' -ForegroundColor Red
    Write-Host '       npm duoc cai kem Node.js: winget install --id OpenJS.NodeJS.LTS -e'
}

if (-not $allReady) {
    Write-Host ''
    Write-Host 'Moi mo lai terminal sau khi cai, roi chay lai script nay.' -ForegroundColor Yellow
    exit 1
}

Write-Host ''
Write-Host '[READY] Moi truong dap ung yeu cau chay demo.' -ForegroundColor Cyan
exit 0

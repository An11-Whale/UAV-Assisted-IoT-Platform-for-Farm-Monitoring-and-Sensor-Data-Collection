<#
.SYNOPSIS
Validates the JDK, Node.js and npm versions required by the integrated farm demo.
#>
[CmdletBinding()]
param(
    [switch]$SelfTest
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

function Get-CommandVersion {
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
        if ($LASTEXITCODE -ne 0 -or
                $versionText -notmatch 'v?(?<major>\d+)(?:\.(?<minor>\d+))?(?:\.(?<patch>\d+))?') {
            return $null
        }
        $major = [int]$Matches.major
        $minor = if ($Matches.minor) { [int]$Matches.minor } else { 0 }
        $patch = if ($Matches.patch) { [int]$Matches.patch } else { 0 }
        return [pscustomobject]@{
            Path = $resolved.Source
            Version = $versionText
            Major = $major
            Minor = $minor
            Patch = $patch
        }
    } catch {
        return $null
    }
}

function Test-CompatibleNodeVersion {
    param(
        [Parameter(Mandatory = $true)][int]$Major,
        [Parameter(Mandatory = $true)][int]$Minor
    )

    # Vite 8 requires ^20.19.0 or >=22.12.0. The locked ESLint 10
    # toolchain is slightly stricter: ^20.19.0, ^22.13.0 or >=24.
    # Checking the intersection prevents a machine from being reported READY
    # when the dashboard can start but its required lint step cannot run.
    if ($Major -eq 20) { return $Minor -ge 19 }
    if ($Major -eq 22) { return $Minor -ge 13 }
    return $Major -ge 24
}

if ($SelfTest) {
    $cases = @(
        @{ Version = '20.18.0'; Expected = $false },
        @{ Version = '20.19.0'; Expected = $true },
        @{ Version = '21.7.3'; Expected = $false },
        @{ Version = '22.12.0'; Expected = $false },
        @{ Version = '22.13.0'; Expected = $true },
        @{ Version = '23.11.1'; Expected = $false },
        @{ Version = '24.0.0'; Expected = $true },
        @{ Version = '25.1.0'; Expected = $true }
    )
    $failedCases = 0
    foreach ($case in $cases) {
        $parts = $case.Version.Split('.')
        $actual = Test-CompatibleNodeVersion -Major ([int]$parts[0]) -Minor ([int]$parts[1])
        if ($actual -ne $case.Expected) {
            $failedCases++
            Write-Host "[FAIL] Node.js $($case.Version): expected $($case.Expected), got $actual" -ForegroundColor Red
        } else {
            Write-Host "[PASS] Node.js $($case.Version) compatibility = $actual" -ForegroundColor Green
        }
    }
    if ($failedCases -gt 0) { exit 1 }
    Write-Host '[PASS] Node.js compatibility boundary tests.' -ForegroundColor Cyan
    exit 0
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

$node = Get-CommandVersion -Command 'node.exe' -VersionArgument '--version'
if ($node -and (Test-CompatibleNodeVersion -Major $node.Major -Minor $node.Minor)) {
    Write-Host "[OK] Node.js $($node.Version): $($node.Path)" -ForegroundColor Green
} else {
    $allReady = $false
    if ($node) {
        Write-Host "[FAIL] Node.js $($node.Version) khong tuong thich voi Vite 8 + ESLint 10." -ForegroundColor Red
    } else {
        Write-Host '[FAIL] Khong tim thay Node.js.' -ForegroundColor Red
    }
    Write-Host '       Can Node.js 20.19+, 22.13+ hoac 24+ (khong dung major 21/23).'
    Write-Host '       Cai bang: winget install --id OpenJS.NodeJS.LTS -e'
}

$npm = Get-CommandVersion -Command 'npm.cmd' -VersionArgument '--version'
if (-not $npm) {
    $npm = Get-CommandVersion -Command 'npm' -VersionArgument '--version'
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

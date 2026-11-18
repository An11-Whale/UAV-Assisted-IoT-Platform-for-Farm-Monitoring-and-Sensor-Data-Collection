<#
.SYNOPSIS
Discovers a compatible JDK and compiles every farm simulator Java source file.
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

$jdk = Find-CompatibleJdk
if (-not $jdk) {
    Write-Host '[ERROR] Khong tim thay JDK 17+.' -ForegroundColor Red
    Write-Host 'Cai bang: winget install --id EclipseAdoptium.Temurin.17.JDK -e'
    exit 1
}

$sourceRoot = Join-Path $PSScriptRoot 'src'
$outDir = Join-Path $PSScriptRoot 'out'
$sources = @(Get-ChildItem -LiteralPath $sourceRoot -Filter '*.java' -File -Recurse |
    Sort-Object FullName |
    ForEach-Object { $_.FullName })

if ($sources.Count -eq 0) {
    Write-Host "[ERROR] Khong tim thay file Java trong $sourceRoot" -ForegroundColor Red
    exit 1
}

New-Item -ItemType Directory -Path $outDir -Force | Out-Null
Write-Host "[BUILD] JDK $($jdk.Major): $($jdk.JavacExe)"
Write-Host "[BUILD] Bien dich $($sources.Count) file Java..."

& $jdk.JavacExe --release 17 -Xlint:all -encoding UTF-8 -d $outDir @sources
$compileExitCode = $LASTEXITCODE
if ($compileExitCode -ne 0) {
    Write-Host "[ERROR] Bien dich that bai (exit code $compileExitCode)." -ForegroundColor Red
    exit $compileExitCode
}

Write-Host '[OK] Bien dich thanh cong. Chay bang run.bat.' -ForegroundColor Green
exit 0

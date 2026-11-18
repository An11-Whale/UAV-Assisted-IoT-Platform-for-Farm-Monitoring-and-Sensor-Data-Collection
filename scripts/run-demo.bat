@echo off
REM Demo tich hop: farm-sim (server :8080) + dashboard React (:5173). Can JDK 17+ va Node 20+.
REM Chay tu thu muc goc repo:  scripts\run-demo.bat
set "ROOT=%~dp0.."

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0check-environment.ps1"
if errorlevel 1 exit /b %ERRORLEVEL%

call "%ROOT%\src\farm-sim\build.bat"
if errorlevel 1 exit /b %ERRORLEVEL%

start "farm-sim" powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\src\farm-sim\run.ps1" --missions 24 --tick-ms 5 --keep

pushd "%ROOT%\src\dashboard"
if errorlevel 1 exit /b %ERRORLEVEL%
if not exist node_modules (
  call npm install
  if errorlevel 1 (
    popd
    exit /b 1
  )
)
timeout /t 8 /nobreak >nul
call npm run dev
set "DEMO_EXIT=%ERRORLEVEL%"
popd
exit /b %DEMO_EXIT%

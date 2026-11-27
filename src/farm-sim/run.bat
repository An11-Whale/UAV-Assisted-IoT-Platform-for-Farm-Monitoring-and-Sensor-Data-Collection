@echo off
REM Windows wrapper that forwards simulator arguments to run.ps1.
REM Vi du: run.bat --missions 4 --keep
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1" %*
exit /b %ERRORLEVEL%

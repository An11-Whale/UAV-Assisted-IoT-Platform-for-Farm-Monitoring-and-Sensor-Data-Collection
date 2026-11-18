@echo off
REM Bien dich toan bo project bang JDK 17+ (tu dong tim ca %%USERPROFILE%%\.jdks).
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
exit /b %ERRORLEVEL%

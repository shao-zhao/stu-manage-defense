@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Stop.ps1"
exit /b %ERRORLEVEL%

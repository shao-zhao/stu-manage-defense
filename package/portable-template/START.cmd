@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Start.ps1"
exit /b %ERRORLEVEL%

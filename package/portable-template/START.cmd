@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Start.ps1"
set RESULT=%ERRORLEVEL%
if not "%RESULT%"=="0" (
  echo Startup failed. Review the logs folder beside this file.
  pause
  exit /b %RESULT%
)
start "" "http://127.0.0.1:18090"
exit /b 0

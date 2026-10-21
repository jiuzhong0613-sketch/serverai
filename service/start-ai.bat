@echo off
setlocal
cd /d "%~dp0"
set "AUTH_FILE=%USERPROFILE%\.config\mc-ai\auth.txt"
if not exist "%AUTH_FILE%" (
  echo missing %AUTH_FILE%
  exit /b 1
)
for /f "usebackq delims=" %%a in ("%AUTH_FILE%") do set "MC_AI_AUTH=%%a"
node server.js

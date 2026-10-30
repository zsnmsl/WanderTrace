@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\publish-github.ps1" %*
set "commit_exit=%errorlevel%"
echo.
if not "%commit_exit%"=="0" echo Stopped. Read the error above before retrying.
pause
exit /b %commit_exit%

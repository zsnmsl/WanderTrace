@echo off
setlocal
echo WanderTrace: commit source snapshots and publish v2.4.2 / v2.4.3 to GitHub.
echo For an offline preview, run: release.bat -DryRun
echo.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\publish-releases.ps1" %*
set "release_exit=%errorlevel%"
echo.
if not "%release_exit%"=="0" echo Stopped. Read the error above; do not force-push or replace existing tags.
pause
exit /b %release_exit%

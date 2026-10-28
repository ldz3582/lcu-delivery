@echo off
rem ---------------------------------------------------------------------------
rem lcu-delivery local stack launcher (Windows)
rem
rem Batch files are NOT affected by PowerShell execution policy. This machine
rem enforces AllSigned, so calling the .ps1 directly fails with
rem "is not digitally signed" - always go through this wrapper.
rem
rem   dev.cmd            -> start   (double-click friendly)
rem   dev.cmd status     -> show status
rem   dev.cmd restart    -> stop + start
rem   dev.cmd stop       -> stop everything
rem   dev.cmd start -Build -> mvn package first
rem ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0ops\dev-windows.ps1" %*
set "RC=%ERRORLEVEL%"

if not "%RC%"=="0" (
  echo.
  echo [dev.cmd] FAILED with exit code %RC%
  echo [dev.cmd] logs: .local\logs\*.out.log
)

rem keep the window open when started by double-click (no arguments)
if "%~1"=="" pause

endlocal & exit /b %RC%

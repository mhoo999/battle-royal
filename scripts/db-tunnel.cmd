@echo off
rem Double-click wrapper for db-tunnel.ps1, which Windows would otherwise open in Notepad.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0db-tunnel.ps1" %*
pause

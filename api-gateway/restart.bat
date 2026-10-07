@echo off
taskkill /F /IM nginx.exe >nul 2>&1
start nginx.exe
echo Nginx restarted successfully!
pause
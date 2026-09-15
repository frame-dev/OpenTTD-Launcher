@echo off
setlocal
cd /d "%~dp0.."
if not exist "target\openttd-launcher-1.0.0.jar" call "%~dp0build.bat"
if errorlevel 1 exit /b %errorlevel%
java -jar "target\openttd-launcher-1.0.0.jar"

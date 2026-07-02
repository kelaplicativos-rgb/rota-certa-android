@echo off
setlocal

set APP_HOME=%~dp0
set PROPERTIES=%APP_HOME%gradle\wrapper\gradle-wrapper.properties

if not exist "%PROPERTIES%" (
  echo Missing %PROPERTIES% 1>&2
  exit /b 1
)

for /f "tokens=1,* delims==" %%A in ('findstr /b "distributionUrl=" "%PROPERTIES%"') do set DISTRIBUTION_URL=%%B
if "%DISTRIBUTION_URL%"=="" (
  echo Missing distributionUrl in %PROPERTIES% 1>&2
  exit /b 1
)

for %%F in ("%DISTRIBUTION_URL%") do set DISTRIBUTION_FILE=%%~nxF
set DISTRIBUTION_NAME=%DISTRIBUTION_FILE:.zip=%
set GRADLE_VERSION=%DISTRIBUTION_NAME:gradle-=%
set GRADLE_VERSION=%GRADLE_VERSION:-bin=%

if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set DIST_BASE=%GRADLE_USER_HOME%\wrapper\dists\%DISTRIBUTION_NAME%
set INSTALL_DIR=%DIST_BASE%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%INSTALL_DIR%\bin\gradle.bat

if not exist "%GRADLE_BIN%" (
  if not exist "%DIST_BASE%" mkdir "%DIST_BASE%"
  set TMP_ZIP=%DIST_BASE%\%DISTRIBUTION_NAME%.zip
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%DISTRIBUTION_URL%' -OutFile '%TMP_ZIP%'"
  if errorlevel 1 exit /b 1
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%TMP_ZIP%' -DestinationPath '%DIST_BASE%' -Force"
  if errorlevel 1 exit /b 1
)

call "%GRADLE_BIN%" %*

@echo off
setlocal
set GRADLE_VERSION=8.9
set DIST=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-bin.zip
set DIR=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-bin
if not exist "%DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat" (
  if not exist "%DIST%" powershell -NoProfile -ExecutionPolicy Bypass -Command "New-Item -ItemType Directory -Force -Path (Split-Path '%DIST%') | Out-Null; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%DIST%'"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%DIST%' '%DIR%'"
)
call "%DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat" %*

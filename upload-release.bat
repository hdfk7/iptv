@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
rem Build the release APK and upload it with latest.json to the site directory.
rem Usage:
rem   upload-release.bat              build + upload
rem   upload-release.bat -skipbuild   upload existing artifacts only
rem   upload-release.bat -prune       remove older APKs on the server

set "TARGET=%IPTV_DEPLOY_TARGET%"
if "%TARGET%"=="" set "TARGET=root@bastion"
set "REMOTE_DIR=%IPTV_DEPLOY_DIR%"
if "%REMOTE_DIR%"=="" set "REMOTE_DIR=/usr/share/nginx/default/static/iptv"
set "PUBLIC_BASE=%IPTV_PUBLIC_BASE%"
if "%PUBLIC_BASE%"=="" set "PUBLIC_BASE=https://www.hdfk7.cn/static/iptv"

set "SKIP_BUILD=0"
set "PRUNE=0"
for %%a in (%*) do (
    if /i "%%~a"=="-skipbuild" set "SKIP_BUILD=1"
    if /i "%%~a"=="-prune" set "PRUNE=1"
)

cd /d "%~dp0"

if "%SKIP_BUILD%"=="0" (
    echo -- clean build outputs
    call gradlew.bat clean --console=plain
    if errorlevel 1 (
        echo [error] clean failed
        exit /b 1
    )
    echo -- build release
    call gradlew.bat :app:assembleRelease --console=plain
    if errorlevel 1 (
        echo [error] build failed
        exit /b 1
    )
)

set "APK="
for /f "delims=" %%f in ('dir /b /a-d /o-d "app\build\outputs\apk\release\*.apk" 2^>nul') do (
    if not defined APK set "APK=app\build\outputs\apk\release\%%f"
)
set "MANIFEST=app\build\outputs\update\release\latest.json"

if not defined APK (
    echo [error] no release APK found, run gradlew.bat :app:assembleRelease first
    exit /b 1
)
if not exist "%MANIFEST%" (
    echo [error] manifest not found: %MANIFEST%
    exit /b 1
)

for %%f in ("%APK%") do set "APK_NAME=%%~nxf"

set "VERSION_FILE=%TEMP%\iptv-release-version.txt"
powershell -NoProfile -Command "$j = Get-Content -Raw -Encoding UTF8 '%MANIFEST%' | ConvertFrom-Json; $j.apkUrl; $j.versionCode; $j.versionName" > "%VERSION_FILE%"
set "APK_URL="
set "VERSION_CODE="
set "VERSION_NAME="
set /a LINE=0
for /f "usebackq delims=" %%l in ("%VERSION_FILE%") do (
    set /a LINE+=1
    if !LINE!==1 set "APK_URL=%%l"
    if !LINE!==2 set "VERSION_CODE=%%l"
    if !LINE!==3 set "VERSION_NAME=%%l"
)
del "%VERSION_FILE%" >nul 2>nul

if /i not "%APK_URL%"=="%APK_NAME%" (
    echo [error] manifest apkUrl does not match the APK file name, abort
    echo         apkUrl = %APK_URL%
    echo         file   = %APK_NAME%
    exit /b 1
)

echo -- version: versionCode=%VERSION_CODE% versionName=%VERSION_NAME%
echo -- target : %TARGET%:%REMOTE_DIR%

scp -o BatchMode=yes "%APK%" "%MANIFEST%" "%TARGET%:%REMOTE_DIR%/"
if errorlevel 1 (
    echo [error] scp upload failed
    exit /b 1
)

if "%PRUNE%"=="1" (
    echo -- prune older APKs
    ssh -o BatchMode=yes "%TARGET%" "cd '%REMOTE_DIR%' ; ls IPTV-release-*.apk 2>/dev/null | grep -v '%APK_NAME%' | xargs -r rm -f"
)

echo -- files on server
ssh -o BatchMode=yes "%TARGET%" "ls -lh '%REMOTE_DIR%/%APK_NAME%' '%REMOTE_DIR%/latest.json'"

echo -- done
echo    %PUBLIC_BASE%/%APK_NAME%
echo    %PUBLIC_BASE%/latest.json
exit /b 0

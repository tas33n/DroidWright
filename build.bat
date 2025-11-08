@echo off
REM UI Automator App - Build Script for Windows

setlocal enabledelayedexpansion

set PROJECT_DIR=%~dp0
set BUILD_TYPE=%1
if "%BUILD_TYPE%"=="" set BUILD_TYPE=debug

echo.
echo ==========================================
echo UI Automator App - Build Script (Windows)
echo ==========================================
echo.

REM Check Java
echo [1/5] Checking Java installation...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: Java not found. Please install JDK 11+
    echo Download: https://adoptopenjdk.net
    pause
    exit /b 1
)
for /f "tokens=2" %%i in ('java -version 2^>^&1 ^| find "version"') do set JAVA_VERSION=%%i
echo √ Found Java: %JAVA_VERSION%
echo.

REM Check Android SDK
echo [2/5] Checking Android SDK...
if "%ANDROID_HOME%"=="" (
    echo ^ ANDROID_HOME not set. Gradle may download required components.
) else (
    echo √ ANDROID_HOME: %ANDROID_HOME%
)
echo.

REM Make gradlew executable (already .bat on Windows)
echo [3/5] Preparing build tools...
echo √ Gradle wrapper ready
echo.

REM Clean build
echo [4/5] Cleaning previous builds...
call "%PROJECT_DIR%gradlew.bat" -p "%PROJECT_DIR%" clean --no-daemon --quiet
if %errorlevel% neq 0 (
    echo ERROR: Clean failed
    pause
    exit /b %errorlevel%
)
echo √ Clean complete
echo.

REM Build APK
echo [5/5] Building %BUILD_TYPE% APK...
if /i "%BUILD_TYPE%"=="debug" (
    call "%PROJECT_DIR%gradlew.bat" -p "%PROJECT_DIR%" assembleDebug --no-daemon
    set APK_FILE=%PROJECT_DIR%app\build\outputs\apk\debug\app-debug.apk
) else if /i "%BUILD_TYPE%"=="release" (
    if not exist "%PROJECT_DIR%release.keystore" (
        echo ERROR: release.keystore not found
        echo Generate keystore with: keytool -genkey -v -keystore release.keystore ...
        pause
        exit /b 1
    )
    call "%PROJECT_DIR%gradlew.bat" -p "%PROJECT_DIR%" assembleRelease --no-daemon
    set APK_FILE=%PROJECT_DIR%app\build\outputs\apk\release\app-release.apk
) else (
    echo ERROR: Invalid build type. Use 'debug' or 'release'
    pause
    exit /b 1
)

if %errorlevel% neq 0 (
    echo ERROR: Build failed
    pause
    exit /b %errorlevel%
)

echo.
echo ==========================================
echo Build Complete!
echo ==========================================
echo.

if exist "%APK_FILE%" (
    for %%F in ("%APK_FILE%") do set APK_SIZE=%%~zF
    echo √ APK generated successfully
    echo   Location: %APK_FILE%
    echo   Size: %APK_SIZE% bytes
    echo.
    echo Installation:
    echo   adb install -r "%APK_FILE%"
) else (
    echo ERROR: APK file not found
    pause
    exit /b 1
)

pause

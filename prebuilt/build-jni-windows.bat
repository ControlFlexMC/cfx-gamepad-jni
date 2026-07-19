@echo off
REM gamepad-jni native build script (Windows)
REM Double-click to run - automatically invokes MSYS2 MinGW64 to execute build_native.sh
REM
REM Prerequisites:
REM   - MSYS2 installed at C:\msys64 with mingw-w64 toolchain
REM   - JDK 8+ with JAVA_HOME set (or auto-detected)
REM   - Prebuilt SDL3 in prebuilt/sdl/
REM
REM Build artifacts:
REM   prebuilt/jni/windows-x86_64/gamepadjni.dll

setlocal enabledelayedexpansion

REM Pause only in interactive mode (double-clicked), skip when called by other scripts
if "%~1"=="" (set "INTERACTIVE=1") else (set "INTERACTIVE=0")

set "MSYS2_DIR=C:\msys64"

if not exist "%MSYS2_DIR%\usr\bin\bash.exe" (
    echo   ERROR: MSYS2 not found at %MSYS2_DIR%
    echo   Please install MSYS2 from https://www.msys2.org/
    if "%INTERACTIVE%"=="1" pause
    exit /b 1
)

REM Check JAVA_HOME - auto-detect common locations if not set
if "%JAVA_HOME%"=="" (
    if exist "C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot" (
        set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot"
    ) else if exist "C:\Program Files\Java\jdk-17" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-17"
    )
)

echo ================================================================
echo   Build gamepad-jni native library (Windows MinGW64)
echo ================================================================
echo.

if defined JAVA_HOME (
    echo   JAVA_HOME: %JAVA_HOME%
) else (
    echo   JAVA_HOME: not set - will try auto-detection in MSYS2
)
echo.

REM Get the directory where this bat file is located (Windows format)
set "SCRIPT_DIR=%~dp0"
REM Remove trailing backslash
set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

REM Convert backslashes to forward slashes for bash compatibility
set "SCRIPT_DIR=%SCRIPT_DIR:\=/%"

echo   Working directory: %SCRIPT_DIR%
echo.

REM Write a temp script to set JAVA_HOME and run build_native.sh
REM This avoids PSReadLine crashes and handles paths with spaces
set "TEMP_SCRIPT=%TEMP%\gamepad_build_native_%RANDOM%.sh"
(
echo #!/bin/bash
echo if [ -n "'%JAVA_HOME%'" ]; then export JAVA_HOME='%JAVA_HOME%'; fi
echo cd '%SCRIPT_DIR%'
echo ./build_native.sh
) > "%TEMP_SCRIPT%"

REM Run the build script using MSYS2 MinGW64 environment
REM MSYSTEM=MINGW64 ensures gcc/cmake from /mingw64/bin are used
"%MSYS2_DIR%\usr\bin\env.exe" MSYSTEM=MINGW64 PATH=/mingw64/bin:/usr/bin:/c/Windows/System32 "%MSYS2_DIR%\usr\bin\bash.exe" "%TEMP_SCRIPT%"
set "BUILD_ERR=%ERRORLEVEL%"
del "%TEMP_SCRIPT%" 2>nul

if %BUILD_ERR% neq 0 (
    echo.
    echo   ERROR: Native build failed!
    if "%INTERACTIVE%"=="1" pause
    exit /b 1
)

echo.
echo ================================================================
echo   Build complete!
echo ================================================================
echo.
echo   Next: Run build_jar.bat to package JAR, or deploy_gamepad-jni.bat
echo.
if "%INTERACTIVE%"=="1" pause
endlocal

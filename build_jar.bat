@echo off
setlocal enabledelayedexpansion
REM Build gamepad-jni JAR (without compiling JNI native library)
REM Prerequisite: JNI native library already built via prebuilt/build_native.bat

REM Pause only in interactive mode (double-clicked), skip when called by other scripts
if "%~1"=="" (set "INTERACTIVE=1") else (set "INTERACTIVE=0")

set "SCRIPT_DIR=%~dp0"
set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

echo ================================================================
echo   Build gamepad-jni JAR
echo ================================================================
echo.

cd /d "%SCRIPT_DIR%"

REM Run Gradle to build JAR
call gradlew.bat clean jar
if errorlevel 1 (
    echo.
    echo   ERROR: JAR build failed!
    if "%INTERACTIVE%"=="1" pause
    exit /b 1
)

REM Find the JAR file
set "JAR_FILE="
for %%f in ("%SCRIPT_DIR%\build\libs\gamepad-jni-*.jar") do set "JAR_FILE=%%f"

if "%JAR_FILE%"=="" (
    echo   ERROR: No gamepad-jni JAR found in build\libs\
    if "%INTERACTIVE%"=="1" pause
    exit /b 1
)

REM Show native libraries inside JAR
echo.
echo   JAR built: %JAR_FILE%
for %%a in ("%JAR_FILE%") do echo   Size: %%~za bytes
echo.
echo   Native libraries inside JAR:
jar tf "%JAR_FILE%" | findstr /r "^native/[^/]*$" | sort

echo.
if "%INTERACTIVE%"=="1" pause
endlocal

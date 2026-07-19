@echo off
REM
REM Create a GitHub Release for gamepad-jni (Windows)
REM
REM Usage:
REM   build_release.bat <version>
REM
REM   Example:
REM     build_release.bat 1.0.0.8
REM
REM Prerequisites:
REM   - GitHub CLI (gh) installed and authenticated (gh auth login)
REM   - Gradle available (uses gradlew.bat)
REM   - JAVA_HOME set
REM

setlocal enabledelayedexpansion

if "%~1"=="" (
    echo Usage: %~nx0 ^<version^>
    echo Example: %~nx0 1.0.0.8
    exit /b 1
)

set VERSION=%~1
set TAG=v%VERSION%

REM ─────────────────────────────────────────────
REM Check prerequisites
REM ─────────────────────────────────────────────
where gh >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] GitHub CLI (gh) not found
    echo   Install: winget install --id GitHub.cli
    echo   Login:   gh auth login
    exit /b 1
)

where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] java not found. Please set JAVA_HOME.
    exit /b 1
)

gh auth status >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] gh not authenticated. Run: gh auth login
    exit /b 1
)

REM ─────────────────────────────────────────────
REM Confirm
REM ─────────────────────────────────────────────
echo ============================================
echo   GitHub Release: gamepad-jni %TAG%
echo ============================================
echo.
echo   Version:     %VERSION%
echo   Tag:         %TAG%
for /f "tokens=*" %%i in ('git remote get-url origin 2^>nul') do echo   Remote:      %%i
for /f "tokens=*" %%i in ('git branch --show-current 2^>nul') do echo   Branch:      %%i
echo.

set /p CONFIRM="Proceed? [y/N] "
if /i not "%CONFIRM%"=="y" (
    echo Aborted.
    exit /b 0
)

REM ─────────────────────────────────────────────
REM Build the JAR
REM ─────────────────────────────────────────────
echo.
echo Building JAR with version %VERSION%...
call gradlew.bat clean jar -Pversion="%VERSION%"
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Build failed
    exit /b 1
)

for /f "tokens=*" %%i in ('dir /b /od build\libs\gamepad-jni-*.jar 2^>nul') do set JAR_FILE=build\libs\%%i
if "%JAR_FILE%"=="" (
    echo [ERROR] JAR not found after build
    exit /b 1
)

echo [OK] JAR: %JAR_FILE%

REM ─────────────────────────────────────────────
REM Create Git tag
REM ─────────────────────────────────────────────
echo.
echo Creating tag %TAG%...

git rev-parse "%TAG%" >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo [WARN] Tag %TAG% already exists locally.
    set /p RECREATE="Delete and recreate? [y/N] "
    if /i "!RECREATE!"=="y" (
        git tag -d "%TAG%"
    ) else (
        echo Aborted.
        exit /b 0
    )
)

git tag -a "%TAG%" -m "Release %TAG%"
git push origin "%TAG%"

echo [OK] Tag %TAG% pushed

REM ─────────────────────────────────────────────
REM Create GitHub Release
REM ─────────────────────────────────────────────
echo.
echo Creating GitHub Release %TAG%...

set NOTES_FILE=%TEMP%\release-notes-%VERSION%.md
(
echo ## gamepad-jni %TAG%
echo.
echo SDL3 Gamepad JNI wrapper for [Control Flex](https://www.curseforge.com/minecraft/mc-mods/control-flex).
echo.
echo ### Supported Platforms
echo ^| Platform ^| Architectures ^|
echo ^|----------^|---------------^|
echo ^| macOS    ^| aarch64 (Apple Silicon), x86_64 (Intel) ^|
echo ^| Windows  ^| x86_64 ^|
echo ^| Linux    ^| x86_64, aarch64 ^|
echo.
echo ### JitPack
echo Add the JitPack repository and dependency:
echo.
echo ```gradle
echo repositories {
echo     maven { url 'https://jitpack.io' }
echo }
echo.
echo dependencies {
echo     implementation 'com.ifels:gamepad-jni:%VERSION%'
echo }
echo ```
echo.
echo ### Files
echo - `gamepad-jni-%VERSION%.jar`
) > "%NOTES_FILE%"

gh release create "%TAG%" ^
    --title "gamepad-jni %TAG%" ^
    --notes-file "%NOTES_FILE%" ^
    "%JAR_FILE%#gamepad-jni-%VERSION%.jar"

del "%NOTES_FILE%"

echo.
echo ============================================
echo   [OK] Release %TAG% created successfully!
echo ============================================

endlocal

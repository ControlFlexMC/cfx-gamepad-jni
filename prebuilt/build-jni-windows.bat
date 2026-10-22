@echo off
REM ============================================================================
REM  gamepad-jni native build script (Windows)
REM
REM  Usage:
REM    build-jni-windows.bat            build windows-x86_64 (native MinGW-w64)
REM    build-jni-windows.bat x86_64     same, spelled out
REM    build-jni-windows.bat aarch64    cross-build windows-aarch64
REM    build-jni-windows.bat all        both architectures
REM
REM  Double-clicking passes no argument, so it builds x86_64 and pauses at the
REM  end. Any argument means "called from a terminal": no pause.
REM
REM  Prerequisites:
REM    - MSYS2 at C:\msys64 with the mingw-w64 toolchain
REM        pacman -S --needed mingw-w64-x86_64-gcc mingw-w64-x86_64-cmake make
REM    - JDK 8+ (JAVA_HOME, or auto-detected below) -- the win32 JNI headers are
REM      architecture neutral, so the same JDK serves both targets
REM    - prebuilt/sdl/trimmed/ + prebuilt/sdl/include (build-sdl3-windows.bat)
REM    - aarch64 only: a cross compiler. Either
REM        * zig        https://ziglang.org/download/  (on PATH, or ZIG_HOME), or
REM        * llvm-mingw https://github.com/mstorsjo/llvm-mingw (on PATH), or
REM        * WINDOWS_AARCH64_CC already set to a full compiler command
REM
REM  Artifacts (installed by build-jni.sh):
REM    prebuilt/jni/windows-x86_64/gamepadjni.dll
REM    prebuilt/jni/windows-aarch64/gamepadjni.dll
REM
REM  Everything real happens in prebuilt/build-jni.sh; this file only picks the
REM  MSYS2 environment, resolves the ARM64 cross compiler and forwards the
REM  target, so all platforms share one build implementation.
REM ============================================================================

setlocal enabledelayedexpansion

set "MSYS2_DIR=C:\msys64"
set "ARCH=%~1"

REM No argument = double-clicked = interactive (pause at the end).
if "%~1"=="" (set "INTERACTIVE=1") else (set "INTERACTIVE=0")

if /i "%ARCH%"=="--help" goto :usage
if /i "%ARCH%"=="-h" goto :usage
if "%ARCH%"=="" set "ARCH=x86_64"

REM -- Map the target onto build-jni.sh's own options -------------------------
set "BUILD_ARGS="
if /i "%ARCH%"=="x86_64"  set "BUILD_ARGS=--arch x86_64"
if /i "%ARCH%"=="aarch64" set "BUILD_ARGS=--arch aarch64"
if /i "%ARCH%"=="all"     set "BUILD_ARGS=--all"
if not defined BUILD_ARGS (
    echo   ERROR: unknown target "%ARCH%"
    echo   Expected: x86_64, aarch64, or all  ^(try --help^)
    if "%INTERACTIVE%"=="1" pause
    exit /b 1
)

if not exist "%MSYS2_DIR%\usr\bin\bash.exe" (
    echo   ERROR: MSYS2 not found at %MSYS2_DIR%
    echo   Please install MSYS2 from https://www.msys2.org/
    if "%INTERACTIVE%"=="1" pause
    exit /b 1
)

REM -- JDK: use JAVA_HOME, else auto-detect a couple of common installs ------
if "%JAVA_HOME%"=="" (
    if exist "C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot" (
        set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot"
    ) else if exist "C:\Program Files\Java\jdk-17" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-17"
    ) else if exist "C:\Java\temurin.jdk8" (
        set "JAVA_HOME=C:\Java\temurin.jdk8"
    )
)

REM -- ARM64 cross compiler (only when aarch64 is part of the request) -------
REM build-jni.sh checks WINDOWS_AARCH64_CC first, then aarch64-w64-mingw32-clang
REM and zig on its own PATH. That PATH is replaced with the MSYS2 one below, so
REM a Windows-installed zig is invisible to it -- this block resolves it here and
REM hands it over in the form build-jni.sh expects (a full command line).
set "CROSS_CC="
if /i not "%ARCH%"=="x86_64" (
    if defined WINDOWS_AARCH64_CC (
        set "CROSS_CC=%WINDOWS_AARCH64_CC%"
    ) else (
        for /f "delims=" %%I in ('where zig 2^>nul') do (
            if not defined CROSS_CC set "CROSS_CC=%%I cc -target aarch64-windows-gnu"
        )
        if not defined CROSS_CC if defined ZIG_HOME (
            if exist "%ZIG_HOME%\zig.exe" set "CROSS_CC=%ZIG_HOME%\zig.exe cc -target aarch64-windows-gnu"
        )
    )
    if not defined CROSS_CC (
        echo.
        echo   ERROR: no Windows ARM64 cross compiler found, and "%ARCH%" needs one.
        echo     zig         https://ziglang.org/download/   ^(put it on PATH or set ZIG_HOME^)
        echo     llvm-mingw  https://github.com/mstorsjo/llvm-mingw  ^(aarch64-w64-mingw32-clang on PATH^)
        echo     or set WINDOWS_AARCH64_CC to a full compiler command.
        echo.
        echo   Tip: "build-jni-windows.bat x86_64" builds the native target only.
        if "%INTERACTIVE%"=="1" pause
        exit /b 1
    )
)

echo ================================================================
echo   Build gamepad-jni native library (Windows MinGW64)
echo ================================================================
echo.
echo   Target:      %ARCH%  [%BUILD_ARGS%]
if defined JAVA_HOME echo   JAVA_HOME:   %JAVA_HOME%
if defined CROSS_CC  echo   aarch64 CC:  %CROSS_CC%
echo.

REM Get the directory where this bat file is located (Windows format)
set "SCRIPT_DIR=%~dp0"
set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"
set "SCRIPT_DIR=%SCRIPT_DIR:\=/%"
echo   Working directory: %SCRIPT_DIR%
echo.

REM Write a temp script to set JAVA_HOME / the cross compiler and run build-jni.sh.
REM This avoids PSReadLine crashes and handles paths with spaces.
set "TEMP_SCRIPT=%TEMP%\gamepad_build_native_%RANDOM%.sh"
(
echo #!/bin/bash
echo if [ -n "'%JAVA_HOME%'" ]; then export JAVA_HOME='%JAVA_HOME%'; fi
echo if [ -n "'%CROSS_CC%'" ]; then export WINDOWS_AARCH64_CC='%CROSS_CC%'; fi
echo cd '%SCRIPT_DIR%'
echo ./build-jni.sh %BUILD_ARGS%
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
echo   Next: run build_jar.bat to package JAR, or deploy_gamepad-jni.bat
echo.
if "%INTERACTIVE%"=="1" pause
endlocal
exit /b 0

:usage
echo.
echo   Usage: build-jni-windows.bat [x86_64 ^| aarch64 ^| all]
echo.
echo     x86_64    native MinGW-w64 build          ^(default^)
echo     aarch64   cross-build via zig or llvm-mingw
echo     all       both, in that order
echo.
echo   aarch64 needs one of: zig on PATH, ZIG_HOME set, llvm-mingw on PATH,
echo   or WINDOWS_AARCH64_CC set to a full compiler command.
echo.
exit /b 0

@echo off
REM SDL3 Build Script (Windows) - Double-click to run
REM Automatically invokes MSYS2 MinGW64 environment to execute build_sdl_input_only.sh

setlocal

set MSYS2_DIR=C:\msys64

if not exist "%MSYS2_DIR%\usr\bin\bash.exe" (
    echo ERROR: MSYS2 not found at %MSYS2_DIR%
    echo Please install MSYS2 from https://www.msys2.org/
    pause
    exit /b 1
)

echo ================================================================
echo   SDL3 Build (Windows MinGW64)
echo ================================================================
echo.

REM Get the directory where this bat file is located (Windows format)
set "SCRIPT_DIR=%~dp0"

REM Convert backslashes to forward slashes for bash compatibility
set "SCRIPT_DIR=%SCRIPT_DIR:\=/%"

echo Working directory: %SCRIPT_DIR%
echo.

REM Run the build script using MSYS2 MinGW64 environment
REM MSYSTEM=MINGW64 ensures uname -s returns MINGW64_NT-* for proper OS detection
"%MSYS2_DIR%\usr\bin\env.exe" MSYSTEM=MINGW64 PATH=/mingw64/bin:/usr/bin "%MSYS2_DIR%\usr\bin\bash.exe" -lc "cd '%SCRIPT_DIR%' && ./build_sdl_input_only.sh"
 
 
echo.
echo ================================================================
echo   Build finished
echo ================================================================
pause
endlocal

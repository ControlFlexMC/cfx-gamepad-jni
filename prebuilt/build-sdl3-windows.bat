@echo off
REM ================================================================
REM  SDL3 / Windows - refresh prebuilt/sdl/trimmed/windows-*
REM
REM  Default - no argument - COPY:
REM    Copy SDL3.dll from prebuilt/sdl/lwjgl-sdl-3.4.3/<arch>/ into
REM    prebuilt/sdl/trimmed/<arch>/. No compiler, no MSYS2 needed.
REM    These are the official SDL 3.4.14 binaries, byte for byte the
REM    same files Minecraft 26.3+ / LWJGL 3.4.3 loads at runtime.
REM
REM  --build - COMPILE:
REM    Compile a joystick-only SDL3 from third_party/SDL in MSYS2
REM    MinGW64 via build-sdl3.sh, overwriting the copied DLLs.
REM    Requires: git submodule update --init third_party/SDL
REM
REM  Why the split: on Windows the "trimmed" artifacts in git are the
REM  official full-build DLLs, so re-copying them is the faithful
REM  refresh. A real trim is only produced on request via --build.
REM
REM  CMake links prebuilt/sdl/trimmed/windows-*/SDL3.dll directly
REM  (MinGW reads the export table, no import library involved) and
REM  build.gradle packages the same file into the JAR.
REM ================================================================

setlocal EnableExtensions EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
set "SCRIPT_DIR_UNIX=%SCRIPT_DIR:\=/%"

pushd "%SCRIPT_DIR%" || (
    echo ERROR: cannot enter "%SCRIPT_DIR%"
    exit /b 1
)

set "MSYS2_DIR=C:\msys64"
set "SRC_ROOT=sdl\lwjgl-sdl-3.4.3"
set "DST_ROOT=sdl\trimmed"
set "VER_HDR=sdl\include\SDL3\SDL_version.h"
REM The script pushes into prebuilt/, so the SDL checkout one level up is
REM reached through REPO_DIR rather than a bare third_party\ path.
set "REPO_DIR=%SCRIPT_DIR%.."

REM Help is not an error; everything else that lands on :usage is one.
set "USAGE_EXIT=1"

if /i "%~1"=="--build"    goto :compile
if /i "%~1"=="--compile"  goto :compile
if /i "%~1"=="--copy"     goto :copy
if /i "%~1"=="-h"         goto :help
if /i "%~1"=="--help"     goto :help
if /i "%~1"=="/?"         goto :help
if not "%~1"=="" (
    echo ERROR: unknown argument "%~1"
    echo.
    goto :usage
)

REM ================================================================
REM  COPY MODE - default
REM ================================================================
:copy
echo ================================================================
echo   SDL3 / Windows - copy mode - nothing is compiled
echo ================================================================
echo.

call :read_version

set "FAILED=0"
for %%A in (windows-x86_64 windows-aarch64) do call :copy_arch %%A

if "%FAILED%"=="1" goto :copy_failed

echo.
echo ----------------------------------------------------------------
echo   Source   : prebuilt\sdl\lwjgl-sdl-3.4.3\windows-*\SDL3.dll
echo   Target   : prebuilt\sdl\trimmed\windows-*\SDL3.dll
echo   SDL3     : %SDL_VER%
echo.
echo   These Windows files are a copy of the official LWJGL 3.4.3
echo   SDL3 binary - a full build, not the joystick-only trim used on
echo   macOS and Linux. CMake links them; build.gradle packages them.
echo.
echo   To compile a trimmed SDL3 from third_party\SDL instead:
echo       build-sdl3-windows.bat --build
echo ----------------------------------------------------------------
echo.
goto :done

:copy_arch
setlocal EnableDelayedExpansion
set "ARCH=%~1"
set "SRC=%SRC_ROOT%\%ARCH%\SDL3.dll"
set "DST_DIR=%DST_ROOT%\%ARCH%"
set "DST=%DST_DIR%\SDL3.dll"

if not exist "%SRC%" (
    echo   [XX] %ARCH%: source DLL missing
    echo        %SRC%
    endlocal & set "FAILED=1" & exit /b 1
)

if not exist "%DST_DIR%" mkdir "%DST_DIR%" 2>nul

REM Byte compare first, so an unchanged tree reports no-ops instead of
REM silently rewriting three megabytes of DLL on every run.
fc /b "%SRC%" "%DST%" >nul 2>&1
if not errorlevel 1 (
    for %%F in ("%DST%") do set "SIZE=%%~zF"
    echo   [==] %ARCH%: SDL3.dll already up to date - !SIZE! bytes
    endlocal
    exit /b 0
)

copy /y "%SRC%" "%DST%" >nul
if errorlevel 1 (
    echo   [XX] %ARCH%: copy failed - is the file locked by a running game?
    endlocal & set "FAILED=1" & exit /b 1
)

for %%F in ("%DST%") do set "SIZE=%%~zF"
echo   [ok] %ARCH%: SDL3.dll copied - !SIZE! bytes
endlocal
exit /b 0

:copy_failed
echo.
echo ================================================================
echo   COPY FAILED - prebuilt\sdl\trimmed is unchanged for that arch
echo ================================================================
echo.
set "EXITCODE=1"
goto :done

REM ================================================================
REM  COMPILE MODE - --build
REM ================================================================
:compile
if not "%~2"=="" (
    echo NOTE: extra arguments ignored - this wrapper only builds windows-x86_64.
    echo.
)

if not exist "%MSYS2_DIR%\usr\bin\bash.exe" (
    echo ERROR: MSYS2 not found at %MSYS2_DIR%
    echo Please install MSYS2 from https://www.msys2.org/
    set "EXITCODE=1"
    goto :done
)

if not exist "%REPO_DIR%\third_party\SDL\CMakeLists.txt" (
    echo ERROR: third_party\SDL is empty - the git submodule is not checked out.
    echo Run: git submodule update --init third_party/SDL
    set "EXITCODE=1"
    goto :done
)

echo ================================================================
echo   SDL3 / Windows - compile mode
echo ================================================================
echo   Source : third_party\SDL  - trimmed joystick/gamepad build
echo   Target : prebuilt\sdl\trimmed\windows-x86_64\SDL3.dll
echo.
echo   This OVERWRITES the official SDL3.dll with a MinGW build.
echo   Re-run without arguments to restore the copied official DLL.
echo ================================================================
echo.

REM MSYSTEM=MINGW64 makes uname -s report MINGW64_NT-* so build-sdl3.sh
REM takes its Windows branch instead of bailing out on an unknown OS.
"%MSYS2_DIR%\usr\bin\env.exe" MSYSTEM=MINGW64 PATH=/mingw64/bin:/usr/bin "%MSYS2_DIR%\usr\bin\bash.exe" -lc "cd '%SCRIPT_DIR_UNIX%' && ./build-sdl3.sh"

if errorlevel 1 (
    echo.
    echo ================================================================
    echo   BUILD FAILED - see the compiler output above
    echo ================================================================
    set "EXITCODE=1"
    goto :done
)

echo.
echo ================================================================
echo   Build finished
echo ================================================================
goto :done

REM ================================================================
REM  Helpers
REM ================================================================
:read_version
set "SDL_VER=unknown"
if not exist "%VER_HDR%" exit /b 0
set "V_MAJOR="
set "V_MINOR="
set "V_MICRO="
for /f "tokens=3" %%v in ('findstr /b /c:"#define SDL_MAJOR_VERSION" "%VER_HDR%" 2^>nul') do set "V_MAJOR=%%v"
for /f "tokens=3" %%v in ('findstr /b /c:"#define SDL_MINOR_VERSION" "%VER_HDR%" 2^>nul') do set "V_MINOR=%%v"
for /f "tokens=3" %%v in ('findstr /b /c:"#define SDL_MICRO_VERSION" "%VER_HDR%" 2^>nul') do set "V_MICRO=%%v"
if defined V_MAJOR if defined V_MINOR if defined V_MICRO set "SDL_VER=%V_MAJOR%.%V_MINOR%.%V_MICRO%"
exit /b 0

:help
set "USAGE_EXIT=0"
goto :usage

:usage
echo ================================================================
echo   SDL3 / Windows helper
echo ================================================================
echo.
echo   build-sdl3-windows.bat              copy lwjgl-sdl-3.4.3 DLLs
echo                                       into prebuilt\sdl\trimmed
echo                                       ^(default, no compilation^)
echo   build-sdl3-windows.bat --build      compile a trimmed SDL3 from
echo                                       third_party\SDL via MSYS2
echo   build-sdl3-windows.bat --help       this text
echo.
echo   CMake and Gradle both read prebuilt\sdl\trimmed\windows-*.
echo.
set "EXITCODE=%USAGE_EXIT%"
goto :done

:done
popd
if "%EXITCODE%"=="0" set "EXITCODE="
if defined EXITCODE (
    echo.
    echo Exit code: %EXITCODE%
    pause
    endlocal
    exit /b %EXITCODE%
)
pause
endlocal
exit /b 0

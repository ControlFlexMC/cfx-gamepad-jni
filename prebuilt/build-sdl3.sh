#!/bin/bash
#
# SDL3 trimmed build script — joystick/gamepad input only.
# Video, audio, rendering etc. are all disabled. No windowing backends needed.
# Unified: macOS, Linux, Windows (MSYS2 MinGW64)
#
# Build artifacts:
#   macOS:
#     prebuilt/sdl/darwin-aarch64/libSDL3.0.dylib   - Apple Silicon
#     prebuilt/sdl/darwin-x86_64/libSDL3.0.dylib    - Intel Mac
#   Linux:
#     prebuilt/sdl/linux-x86_64/libSDL3.so           - Linux x64
#     prebuilt/sdl/linux-aarch64/libSDL3.so          - Linux ARM64
#   Windows:
#     prebuilt/sdl/windows-x86_64/SDL3.dll           - Windows x64
#     prebuilt/sdl/windows-x86_64/lib/libSDL3.dll.a  - Import library (for linking)
#   Common:
#     prebuilt/sdl/include/SDL3/                     - Header files
#
# Usage:
#   All platforms:
#     cd prebuilt
#     chmod +x build-sdl3.sh
#     ./build-sdl3.sh                  # Build for default architecture(s)
#     ./build-sdl3.sh --all            # Build all architectures
#     ./build-sdl3.sh --arch x86_64    # Build for a specific architecture
#
#   macOS defaults:    native architecture
#   Linux defaults:    native architecture
#   Windows defaults:  x86_64
#
#   Windows (double-click):
#     Simply double-click build-sdl3-windows.bat
#
# Prerequisites:
#   macOS:         cmake (brew install cmake)
#   Linux:         cmake gcc (sudo apt install cmake build-essential)
#                  cross-compile: gcc-aarch64-linux-gnu (optional)
#   Windows:       MSYS2 MinGW64 with cmake, gcc
#                  (pacman -S mingw-w64-x86_64-cmake mingw-w64-x86_64-gcc)
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDL_SOURCE_DIR="${SCRIPT_DIR}/../third_party/SDL"
BUILD_ROOT="${SCRIPT_DIR}/sdl/build"
INSTALL_DIR="${SCRIPT_DIR}/sdl"

# ─────────────────────────────────────────────
# OS detection
# ─────────────────────────────────────────────
OS_NAME="$(uname -s)"

if [ "$OS_NAME" != "Darwin" ] && [ "$OS_NAME" != "Linux" ] && [[ "$OS_NAME" != MINGW* ]] && [[ "$OS_NAME" != MSYS* ]]; then
    echo "Error: Unsupported operating system '${OS_NAME}'"
    echo "  Supported: Darwin (macOS), Linux, MINGW*/MSYS* (Windows via MSYS2)"
    exit 1
fi

# macOS minimum deployment target (reduce system version dependency)
MACOS_DEPLOYMENT_TARGET="10.13"

# Build type
BUILD_TYPE="Release"

# ─────────────────────────────────────────────
# Native architecture detection
# ─────────────────────────────────────────────
NATIVE_ARCH="$(uname -m)"
case "$NATIVE_ARCH" in
    aarch64|arm64) NATIVE_ARCH="aarch64" ;;
    x86_64|amd64)  NATIVE_ARCH="x86_64" ;;
esac

# ─────────────────────────────────────────────
# CLI argument parsing
# ─────────────────────────────────────────────
BUILD_ALL=false
TARGET_ARCH=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --all) BUILD_ALL=true; shift ;;
        --arch) TARGET_ARCH="$2"; shift 2 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

# ─────────────────────────────────────────────
# Determine architectures to build
# ─────────────────────────────────────────────
if [ "$BUILD_ALL" = true ]; then
    case "$OS_NAME" in
        Darwin)         ARCHS=("arm64" "x86_64") ;;
        Linux)          ARCHS=("x86_64" "aarch64") ;;
        MINGW*|MSYS*)   ARCHS=("x86_64") ;;
    esac
elif [ -n "$TARGET_ARCH" ]; then
    ARCHS=("$TARGET_ARCH")
else
    # Default per-platform
    case "$OS_NAME" in
        Darwin)         ARCHS=("$NATIVE_ARCH") ;;       # macOS: native only
        Linux)          ARCHS=("$NATIVE_ARCH") ;;       # Linux: native only
        MINGW*|MSYS*)   ARCHS=("x86_64") ;;             # Windows: x86_64
    esac
fi

# ─────────────────────────────────────────────
# SDL3 trim options: disable unnecessary subsystems and features (common)
# ─────────────────────────────────────────────
SDL_CMAKE_COMMON_OPTIONS=(
    # ── Build type ──
    -DCMAKE_BUILD_TYPE="${BUILD_TYPE}"

    # ── Build shared library ──
    -DSDL_STATIC=OFF
    -DSDL_SHARED=ON

    # ── Statically link third-party deps (bundled into SDL library) ──
    -DSDL_DEPS_SHARED=OFF

    # ── Do not build test library and examples ──
    -DSDL_TEST_LIBRARY=OFF
    -DSDL_TESTS=OFF
    -DSDL_EXAMPLES=OFF

    # ── Install ──
    -DSDL_INSTALL=ON

    # ═══════════════════════════════════════
    # Subsystem switches
    # ═══════════════════════════════════════

    # ❌ Disable: Video — not needed for joystick/gamepad input.
    #            Disabling video avoids the Cocoa/X11/Wayland requirement entirely.
    -DSDL_VIDEO=OFF

    # ✅ Keep: Joystick + Hidapi (joystick/gamepad functionality)
    -DSDL_JOYSTICK=ON
    -DSDL_HIDAPI=ON
    -DSDL_HIDAPI_JOYSTICK=ON
    -DSDL_VIRTUAL_JOYSTICK=ON
    # ❌ Disable: libusb (macOS uses native IOKit HID; Linux uses hidraw/evdev; Windows uses native HID)
    -DSDL_HIDAPI_LIBUSB=OFF

    # ❌ Disable: Audio subsystem
    -DSDL_AUDIO=OFF

    # ❌ Disable: GPU subsystem
    -DSDL_GPU=OFF

    # ❌ Disable: Render subsystem
    -DSDL_RENDER=OFF

    # ❌ Disable: Camera subsystem
    -DSDL_CAMERA=OFF

    # ❌ Disable: Haptic (force feedback/vibration)
    -DSDL_HAPTIC=OFF

    # ❌ Disable: Power (power management)
    -DSDL_POWER=OFF

    # ✅ Keep: Sensor (sensors, required by joystick gyroscope/accelerometer)
    -DSDL_SENSOR=ON

    # ❌ Disable: Dialog (file dialog)
    -DSDL_DIALOG=OFF

    # ❌ Disable: Tray (system tray)
    -DSDL_TRAY=OFF

    # ═══════════════════════════════════════
    # Disable all graphics rendering features
    # ═══════════════════════════════════════
    -DSDL_OPENGL=OFF
    -DSDL_OPENGLES=OFF
    -DSDL_VULKAN=OFF
    -DSDL_METAL=OFF
    -DSDL_RENDER_D3D=OFF
    -DSDL_RENDER_D3D11=OFF
    -DSDL_RENDER_D3D12=OFF
    -DSDL_RENDER_METAL=OFF
    -DSDL_RENDER_VULKAN=OFF
    -DSDL_RENDER_GPU=OFF
    -DSDL_OPENVR=OFF

    # ═══════════════════════════════════════
    # Other unnecessary features
    # ═══════════════════════════════════════
    -DSDL_DBUS=OFF
    -DSDL_IBUS=OFF
    -DSDL_LIBURING=OFF
    -DSDL_LIBUDEV=OFF
)

# Linux: skip the X11/Wayland hard-error check when video is disabled.
# Without this, SDL's cmake/macros.cmake FATAL_ERRORs even with SDL_VIDEO=OFF.
if [ "$OS_NAME" = "Linux" ]; then
    SDL_CMAKE_COMMON_OPTIONS+=(-DSDL_UNIX_CONSOLE_BUILD=ON)
fi

# ─────────────────────────────────────────────
# Platform-specific CMake options
# ─────────────────────────────────────────────
# Common video backend disables (all platforms)
SDL_VIDEO_BACKEND_OPTIONS=(
    -DSDL_COCOA=OFF
    -DSDL_X11=OFF
    -DSDL_WAYLAND=OFF
    -DSDL_KMSDRM=OFF
    -DSDL_RPI=OFF
    -DSDL_ROCKCHIP=OFF
    -DSDL_VIVANTE=OFF
    -DSDL_OFFSCREEN=OFF
    -DSDL_DUMMYVIDEO=OFF
)

case "$OS_NAME" in
    Darwin)
        SDL_CMAKE_PLATFORM_OPTIONS=(
            "${SDL_VIDEO_BACKEND_OPTIONS[@]}"
        )
        ;;
    Linux)
        SDL_CMAKE_PLATFORM_OPTIONS=(
            "${SDL_VIDEO_BACKEND_OPTIONS[@]}"
            -DSDL_DIRECTX=OFF
            -DSDL_XINPUT=OFF
            -DSDL_WASAPI=OFF
        )
        ;;
    MINGW*|MSYS*)
        SDL_CMAKE_PLATFORM_OPTIONS=(
            "${SDL_VIDEO_BACKEND_OPTIONS[@]}"
            # Windows-specific: DirectX (video), XInput (joystick — keep this one)
            -DSDL_DIRECTX=OFF
            -DSDL_XINPUT=ON
            # WASAPI (audio, not needed)
            -DSDL_WASAPI=OFF
        )
        ;;
esac

# ─────────────────────────────────────────────
# Helper functions
# ─────────────────────────────────────────────
log_info() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  $1"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
}

get_cpu_count() {
    if [ "$OS_NAME" = "Darwin" ]; then
        sysctl -n hw.logicalcpu 2>/dev/null || echo 4
    else
        nproc 2>/dev/null || echo 4
    fi
}

# ─────────────────────────────────────────────
# Check build environment
# ─────────────────────────────────────────────
check_prerequisites() {
    log_info "Checking build environment (${OS_NAME})"

    if ! command -v cmake &>/dev/null; then
        echo "❌ Error: cmake not found"
        case "$OS_NAME" in
            Darwin)
                echo "   Please install: brew install cmake" ;;
            Linux)
                echo "   Ubuntu/Debian: sudo apt install cmake"
                echo "   Fedora/RHEL:   sudo dnf install cmake" ;;
            MINGW*|MSYS*)
                echo "   Please install: pacman -S mingw-w64-x86_64-cmake"
                echo "   Make sure to use MSYS2 MinGW64 environment: export PATH=/mingw64/bin:\$PATH" ;;
        esac
        exit 1
    fi
    echo "✅ cmake: $(cmake --version | head -1)"

    if ! command -v make &>/dev/null && ! command -v ninja &>/dev/null; then
        echo "❌ Error: make or ninja not found"
        exit 1
    fi

    if command -v ninja &>/dev/null; then
        CMAKE_GENERATOR="Ninja"
        echo "✅ ninja: $(ninja --version)"
    else
        CMAKE_GENERATOR="Unix Makefiles"
        echo "✅ make: $(make --version | head -1)"
    fi

    # Windows: check gcc
    if [[ "$OS_NAME" == MINGW* ]] || [[ "$OS_NAME" == MSYS* ]]; then
        if ! command -v gcc &>/dev/null; then
            echo "❌ Error: gcc not found"
            echo "   Please install: pacman -S mingw-w64-x86_64-gcc"
            echo "   Make sure to use MSYS2 MinGW64 environment: export PATH=/mingw64/bin:\$PATH"
            exit 1
        fi
        echo "✅ gcc: $(gcc --version | head -1)"
    elif [ "$OS_NAME" = "Linux" ]; then
        if ! command -v gcc &>/dev/null && ! command -v clang &>/dev/null; then
            echo "❌ Error: C compiler not found"
            echo "   Ubuntu/Debian: sudo apt install build-essential"
            echo "   Fedora/RHEL:   sudo dnf install gcc"
            exit 1
        fi
        echo "✅ $(gcc --version 2>/dev/null | head -1 || clang --version 2>/dev/null | head -1)"
    fi

    if [ "$OS_NAME" = "Darwin" ]; then
        echo "✅ macOS Deployment Target: ${MACOS_DEPLOYMENT_TARGET}"
    fi
}

# ─────────────────────────────────────────────
# Build a single architecture
# ─────────────────────────────────────────────
build_arch() {
    local arch="$1"
    local build_dir="${BUILD_ROOT}/${arch}"
    local install_dir="${BUILD_ROOT}/${arch}-install"

    log_info "Building SDL3 for ${arch} (${OS_NAME})"

    rm -rf "${build_dir}"
    mkdir -p "${build_dir}"

    local cmake_args=(
        -S "${SDL_SOURCE_DIR}" -B "${build_dir}"
        -G "${CMAKE_GENERATOR}"
        -DCMAKE_INSTALL_PREFIX="${install_dir}"
        "${SDL_CMAKE_COMMON_OPTIONS[@]}"
        "${SDL_CMAKE_PLATFORM_OPTIONS[@]}"
    )

    if [ "$OS_NAME" = "Darwin" ]; then
        cmake_args+=(
            -DCMAKE_OSX_ARCHITECTURES="${arch}"
            -DCMAKE_OSX_DEPLOYMENT_TARGET="${MACOS_DEPLOYMENT_TARGET}"
        )
    elif [ "$OS_NAME" = "Linux" ]; then
        # Cross-compile from x86_64 to aarch64
        if [ "$arch" = "aarch64" ] && [ "$NATIVE_ARCH" != "aarch64" ]; then
            if ! command -v aarch64-linux-gnu-gcc &>/dev/null; then
                echo "❌ Error: cross-compiler aarch64-linux-gnu-gcc not found"
                echo "   Install: sudo apt install gcc-aarch64-linux-gnu"
                exit 1
            fi
            cmake_args+=(
                -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc
                -DCMAKE_SYSTEM_PROCESSOR=aarch64
                -DCMAKE_SYSTEM_NAME=Linux
            )
            echo "   Cross-compiling with aarch64-linux-gnu-gcc"
        fi
    fi
    # Windows: no additional arch-specific cmake args needed

    cmake "${cmake_args[@]}"

    cmake --build "${build_dir}" --config "${BUILD_TYPE}" -j "$(get_cpu_count)"

    cmake --install "${build_dir}" --config "${BUILD_TYPE}"

    # Windows: Strip all symbols to reduce DLL size (MinGW/GCC embeds DWARF debug info by default)
    # Official SDL3 releases are ~2.5MB; without strip MinGW produces ~4.4MB
    if [[ "$OS_NAME" == MINGW* ]] || [[ "$OS_NAME" == MSYS* ]]; then
        if command -v strip &>/dev/null; then
            local dll_file
            for dll_file in "${install_dir}/bin/"SDL3.dll "${build_dir}/"SDL3.dll; do
                if [ -f "${dll_file}" ]; then
                    local before_size after_size
                    before_size=$(wc -c < "${dll_file}")
                    strip --strip-all "${dll_file}"
                    after_size=$(wc -c < "${dll_file}")
                    echo "   Stripped SDL3.dll: ${before_size} -> ${after_size} bytes"
                    break
                fi
            done
        fi
    fi

    echo "✅ ${arch} build complete: ${install_dir}"
}

# ─────────────────────────────────────────────
# Organize artifacts - macOS
# ─────────────────────────────────────────────
organize_output_macos() {
    local arm64_install="${BUILD_ROOT}/arm64-install"
    local x86_64_install="${BUILD_ROOT}/x86_64-install"

    log_info "Organizing build artifacts (macOS)"

    # Clean old artifact directories (keep build/ cache)
    rm -rf "${INSTALL_DIR}/darwin-aarch64"
    rm -rf "${INSTALL_DIR}/darwin-x86_64"
    rm -rf "${INSTALL_DIR}/include"

    # Separate by architecture (for JAR packaging)
    mkdir -p "${INSTALL_DIR}/darwin-aarch64"
    mkdir -p "${INSTALL_DIR}/darwin-x86_64"

    # Copy arm64 dylib
    for dylib in "${arm64_install}"/lib/libSDL3*.dylib; do
        if [ -f "${dylib}" ] && [ ! -L "${dylib}" ]; then
            cp "${dylib}" "${INSTALL_DIR}/darwin-aarch64/"
        fi
    done

    # Copy x86_64 dylib
    for dylib in "${x86_64_install}"/lib/libSDL3*.dylib; do
        if [ -f "${dylib}" ] && [ ! -L "${dylib}" ]; then
            cp "${dylib}" "${INSTALL_DIR}/darwin-x86_64/"
        fi
    done

    # Copy headers (arm64 and x86_64 headers are identical, pick either)
    if [ -d "${arm64_install}/include" ]; then
        cp -R "${arm64_install}/include" "${INSTALL_DIR}/"
    elif [ -d "${x86_64_install}/include" ]; then
        cp -R "${x86_64_install}/include" "${INSTALL_DIR}/"
    else
        echo "  ⚠️  Header directory not found, skipping"
    fi

    echo ""
    echo "✅ Artifact organization complete"
}

# ─────────────────────────────────────────────
# Organize artifacts - Linux
# ─────────────────────────────────────────────
organize_output_linux() {
    log_info "Organizing build artifacts (Linux)"

    # Clean old artifact directories
    rm -rf "${INSTALL_DIR}/linux-x86_64"
    rm -rf "${INSTALL_DIR}/linux-aarch64"
    rm -rf "${INSTALL_DIR}/include"

    mkdir -p "${INSTALL_DIR}/linux-x86_64"
    mkdir -p "${INSTALL_DIR}/linux-aarch64"

    for arch in "${ARCHS[@]}"; do
        local staging="${BUILD_ROOT}/${arch}-install"
        local target
        case "$arch" in
            x86_64)  target="${INSTALL_DIR}/linux-x86_64" ;;
            aarch64) target="${INSTALL_DIR}/linux-aarch64" ;;
            *)       echo "  ⚠️  Unknown arch: ${arch}, skipping output"; continue ;;
        esac

        if [ ! -d "${staging}" ]; then
            echo "  ⚠️  Staging dir ${staging} not found, skipping"
            continue
        fi

        # Copy shared library, rename to libSDL3.so
        for sofile in "${staging}/lib"/libSDL3.so.*.*; do
            if [ -f "${sofile}" ] && [ ! -L "${sofile}" ]; then
                cp "${sofile}" "${target}/libSDL3.so"
                echo "  ✅ Copied ${arch}: libSDL3.so"

                # Strip debug symbols
                local strip_cmd="strip"
                if [ "$arch" = "aarch64" ] && [ "$NATIVE_ARCH" != "aarch64" ] && command -v aarch64-linux-gnu-strip &>/dev/null; then
                    strip_cmd="aarch64-linux-gnu-strip"
                fi
                if command -v "$strip_cmd" &>/dev/null; then
                    local before_size after_size
                    before_size=$(wc -c < "${target}/libSDL3.so")
                    "$strip_cmd" --strip-all "${target}/libSDL3.so" 2>/dev/null || "$strip_cmd" "${target}/libSDL3.so" 2>/dev/null || true
                    after_size=$(wc -c < "${target}/libSDL3.so")
                    echo "     Stripped: ${before_size} -> ${after_size} bytes"
                fi
            fi
        done
    done

    # Copy headers (identical for both architectures, use x86_64 if available)
    local header_source="${BUILD_ROOT}/x86_64-install"
    if [ -d "${header_source}/include" ]; then
        cp -R "${header_source}/include" "${INSTALL_DIR}/"
        echo "  ✅ Copied headers"
    elif [ -d "${BUILD_ROOT}/aarch64-install/include" ]; then
        cp -R "${BUILD_ROOT}/aarch64-install/include" "${INSTALL_DIR}/"
        echo "  ✅ Copied headers"
    else
        echo "  ⚠️  Header directory not found, skipping"
    fi

    echo ""
    echo "✅ Artifact organization complete"
}

# ─────────────────────────────────────────────
# Organize artifacts - Windows
# ─────────────────────────────────────────────
organize_output_windows() {
    local staging_dir="${BUILD_ROOT}/x86_64-install"

    log_info "Organizing build artifacts (Windows)"

    # Clean old artifact directories
    rm -rf "${INSTALL_DIR}/windows-x86_64"
    rm -rf "${INSTALL_DIR}/include"

    mkdir -p "${INSTALL_DIR}/windows-x86_64"
    mkdir -p "${INSTALL_DIR}/windows-x86_64/lib"

    # Copy SDL3.dll
    local dll_found=0
    if [ -f "${staging_dir}/bin/SDL3.dll" ]; then
        cp "${staging_dir}/bin/SDL3.dll" "${INSTALL_DIR}/windows-x86_64/"
        echo "  ✅ Copied SDL3.dll from staging/bin"
        dll_found=1
    fi
    if [ "${dll_found}" = "0" ] && [ -f "${BUILD_ROOT}/x86_64/SDL3.dll" ]; then
        cp "${BUILD_ROOT}/x86_64/SDL3.dll" "${INSTALL_DIR}/windows-x86_64/"
        echo "  ✅ Copied SDL3.dll from build dir"
        dll_found=1
    fi
    if [ "${dll_found}" = "0" ]; then
        echo "  ⚠️  SDL3.dll not found at expected location, searching build tree..."
        local found_dll
        found_dll=$(find "${BUILD_ROOT}/x86_64" -name "SDL3.dll" -type f 2>/dev/null | head -1)
        if [ -n "${found_dll}" ]; then
            cp "${found_dll}" "${INSTALL_DIR}/windows-x86_64/"
            echo "  ✅ Found: ${found_dll}"
            dll_found=1
        fi
    fi
    if [ "${dll_found}" = "0" ]; then
        echo "  ❌ Error: SDL3.dll not found!"
    fi

    # Copy import library (needed for linking, not needed at runtime)
    # Place in lib/ subdirectory, separate from runtime DLL
    if [ -f "${staging_dir}/lib/libSDL3.dll.a" ]; then
        cp "${staging_dir}/lib/libSDL3.dll.a" "${INSTALL_DIR}/windows-x86_64/lib/"
        echo "  ✅ Copied libSDL3.dll.a -> windows-x86_64/lib/"
    fi
    if [ -f "${staging_dir}/lib/SDL3.dll.a" ]; then
        cp "${staging_dir}/lib/SDL3.dll.a" "${INSTALL_DIR}/windows-x86_64/lib/"
        echo "  ✅ Copied SDL3.dll.a -> windows-x86_64/lib/"
    fi

    # Copy headers
    if [ -d "${staging_dir}/include" ]; then
        cp -R "${staging_dir}/include" "${INSTALL_DIR}/"
        echo "  ✅ Copied headers"
    else
        echo "  ⚠️  Header directory not found"
    fi

    echo ""
    echo "✅ Artifact organization complete"
}

# ─────────────────────────────────────────────
# Verify artifacts - macOS
# ─────────────────────────────────────────────
verify_output_macos() {
    log_info "Verifying build artifacts (macOS)"

    echo "📁 Install directory: ${INSTALL_DIR}"
    echo ""

    # Check per-architecture dylibs
    echo "📦 Per-architecture dylibs (for JAR packaging):"
    if ls "${INSTALL_DIR}"/darwin-aarch64/libSDL3*.dylib &>/dev/null; then
        ls -lh "${INSTALL_DIR}"/darwin-aarch64/libSDL3*.dylib
    fi
    if ls "${INSTALL_DIR}"/darwin-x86_64/libSDL3*.dylib &>/dev/null; then
        ls -lh "${INSTALL_DIR}"/darwin-x86_64/libSDL3*.dylib
    fi
    echo ""

    # Check dynamic library dependencies (should all be system libraries)
    echo "🔗 Dynamic library dependencies (should all be system libraries):"
    for dylib in "${INSTALL_DIR}"/darwin-aarch64/libSDL3*.dylib; do
        if [ -f "${dylib}" ]; then
            otool -L "${dylib}" | grep -v "libSDL3" | sed 's/^/  /'
            break
        fi
    done
    echo ""

    # Check headers
    if [ -d "${INSTALL_DIR}/include/SDL3" ]; then
        local header_count
        header_count=$(find "${INSTALL_DIR}/include/SDL3" -name "*.h" | wc -l | tr -d ' ')
        echo "📄 Header files: ${header_count} (${INSTALL_DIR}/include/SDL3/)"
    fi
}

# ─────────────────────────────────────────────
# Verify artifacts - Linux
# ─────────────────────────────────────────────
verify_output_linux() {
    log_info "Verifying build artifacts (Linux)"

    echo "📁 Install directory: ${INSTALL_DIR}"
    echo ""

    # Check per-architecture shared libraries
    echo "📦 Per-architecture shared libraries (for JAR packaging):"
    for arch in x86_64 aarch64; do
        local target
        case "$arch" in
            x86_64)  target="${INSTALL_DIR}/linux-x86_64" ;;
            aarch64) target="${INSTALL_DIR}/linux-aarch64" ;;
            *)       continue ;;
        esac
        if ls "${target}"/libSDL3*.so* &>/dev/null 2>&1; then
            ls -lh "${target}"/libSDL3*.so*
        fi
    done
    echo ""

    # Check shared library dependencies
    for sofile in "${INSTALL_DIR}"/linux-x86_64/libSDL3*.so*; do
        if [ -f "${sofile}" ] && [ ! -L "${sofile}" ]; then
            echo "🔗 Shared library dependencies ($(basename "$sofile")):"
            if command -v ldd &>/dev/null; then
                ldd "${sofile}" | sed 's/^/  /'
            elif command -v objdump &>/dev/null; then
                objdump -p "${sofile}" 2>/dev/null | grep NEEDED | sed 's/^/  /'
            fi
            break
        fi
    done
    echo ""

    # Check headers
    if [ -d "${INSTALL_DIR}/include/SDL3" ]; then
        local header_count
        header_count=$(find "${INSTALL_DIR}/include/SDL3" -name "*.h" | wc -l | tr -d ' ')
        echo "📄 Header files: ${header_count} (${INSTALL_DIR}/include/SDL3/)"
    fi
}

# ─────────────────────────────────────────────
# Verify artifacts - Windows
# ─────────────────────────────────────────────
verify_output_windows() {
    log_info "Verifying build artifacts (Windows)"

    echo "📁 Install directory: ${INSTALL_DIR}"
    echo ""

    # Check SDL3.dll
    echo "📦 Windows x86_64 artifacts:"
    if [ -f "${INSTALL_DIR}/windows-x86_64/SDL3.dll" ]; then
        local dll_size
        dll_size=$(wc -c < "${INSTALL_DIR}/windows-x86_64/SDL3.dll")
        echo "  ✅ SDL3.dll: ${dll_size} bytes"
    else
        echo "  ❌ SDL3.dll not found!"
    fi

    # Check import library
    if ls "${INSTALL_DIR}"/windows-x86_64/lib/*.dll.a &>/dev/null; then
        ls -lh "${INSTALL_DIR}"/windows-x86_64/lib/*.dll.a
    fi
    echo ""

    # Check DLL dependencies (using objdump)
    if command -v objdump &>/dev/null; then
        echo "🔗 DLL dependencies:"
        if [ -f "${INSTALL_DIR}/windows-x86_64/SDL3.dll" ]; then
            objdump -p "${INSTALL_DIR}/windows-x86_64/SDL3.dll" 2>/dev/null | grep "DLL Name" | sed 's/^/  /' || echo "  (Unable to read dependencies)"
        fi
    fi
    echo ""

    # Check headers
    if [ -d "${INSTALL_DIR}/include/SDL3" ]; then
        local header_count
        header_count=$(find "${INSTALL_DIR}/include/SDL3" -name "*.h" | wc -l | tr -d ' ')
        echo "📄 Header files: ${header_count} (${INSTALL_DIR}/include/SDL3/)"
    fi
}

# ─────────────────────────────────────────────
# Print usage - macOS
# ─────────────────────────────────────────────
print_usage_macos() {
    log_info "Integration instructions (macOS)"

    cat <<EOF
JAR packaging structure (separated by architecture):

  your-project.jar
  └── native/
      ├── darwin-aarch64/
      │   └── libSDL3.0.dylib    ← Apple Silicon (M1/M2/M3/...)
      └── darwin-x86_64/
          └── libSDL3.0.dylib    ← Intel Mac

Build artifact locations:
  arm64:    ${INSTALL_DIR}/darwin-aarch64/
  x86_64:   ${INSTALL_DIR}/darwin-x86_64/
  Headers:  ${INSTALL_DIR}/include/SDL3/

Trimmed features: Video, Audio, GPU, Render, Camera, Haptic, Power, Dialog, Tray
                  OpenGL, Vulkan, Metal, D3D and all windowing backends
Retained features: Joystick/Gamepad, Sensor
All dependencies are macOS system frameworks; no extra libraries need to be installed.
EOF
}

# ─────────────────────────────────────────────
# Print usage - Linux
# ─────────────────────────────────────────────
print_usage_linux() {
    log_info "Integration instructions (Linux)"

    cat <<EOF
JAR packaging structure (separated by architecture):

  your-project.jar
  └── native/
      ├── linux-x86_64/
      │   └── libSDL3.so          ← Linux x64
      └── linux-aarch64/
          └── libSDL3.so          ← Linux ARM64

Build artifact locations:
  x86_64:   ${INSTALL_DIR}/linux-x86_64/
  aarch64:  ${INSTALL_DIR}/linux-aarch64/
  Headers:  ${INSTALL_DIR}/include/SDL3/

Trimmed features: Video, Audio, GPU, Render, Camera, Haptic, Power, Dialog, Tray
                  OpenGL, Vulkan, X11, Wayland and all windowing backends
Retained features: Joystick/Gamepad, Sensor
Runtime deps:      libc, libm (standard system libs only, no extra packages required)
EOF
}

# ─────────────────────────────────────────────
# Print usage - Windows
# ─────────────────────────────────────────────
print_usage_windows() {
    log_info "Integration instructions (Windows)"

    cat <<EOF
JAR packaging structure:

  your-project.jar
  └── native/
      └── windows-x86_64/
          └── SDL3.dll            ← Windows x64

Build artifact locations:
  SDL3.dll:    ${INSTALL_DIR}/windows-x86_64/
  Import lib:  ${INSTALL_DIR}/windows-x86_64/lib/   (for linking only, not needed at runtime)
  Headers:     ${INSTALL_DIR}/include/SDL3/

Trimmed features: Video, Audio, GPU, Render, Camera, Haptic, Power, Dialog, Tray
                  OpenGL, Vulkan, D3D, DirectX and all windowing backends
Retained features: Joystick/Gamepad, Sensor, XInput
All dependencies are Windows system libraries; no extra libraries need to be installed.
EOF
}

# ─────────────────────────────────────────────
# Main flow
# ─────────────────────────────────────────────
main() {
    log_info "SDL3 trimmed build - Input devices only (Joystick/Gamepad)"
    echo "Platform:      ${OS_NAME}"
    echo "Build targets: ${ARCHS[*]}"
    echo "Source dir:    ${SDL_SOURCE_DIR}"
    echo "Build dir:     ${BUILD_ROOT}"
    echo "Artifact dir:  ${INSTALL_DIR}"

    check_prerequisites

    for arch in "${ARCHS[@]}"; do
        build_arch "$arch"
    done

    case "$OS_NAME" in
        Darwin)
            organize_output_macos
            verify_output_macos
            print_usage_macos
            ;;
        Linux)
            organize_output_linux
            verify_output_linux
            print_usage_linux
            ;;
        MINGW*|MSYS*)
            organize_output_windows
            verify_output_windows
            print_usage_windows
            ;;
    esac

    log_info "✅ All done!"
}

main "$@"

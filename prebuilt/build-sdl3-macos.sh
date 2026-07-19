#!/bin/bash
#
# SDL3 trimmed build script - only retain Keyboard/Mouse/Joystick input functionality
# Supports macOS + Windows (MSYS2 MinGW64) platforms
#
# Build artifacts:
#   macOS:
#     prebuilt/sdl/darwin-aarch64/libSDL3.0.dylib   - Apple Silicon
#     prebuilt/sdl/darwin-x86_64/libSDL3.0.dylib    - Intel Mac
#   Windows:
#     prebuilt/sdl/windows-x86_64/SDL3.dll           - Windows x64
#     prebuilt/sdl/windows-x86_64/lib/libSDL3.dll.a  - Import library (for linking)
#   Common:
#     prebuilt/sdl/include/SDL3/                     - Header files
#
# Usage:
#   macOS:
#     cd prebuilt
#     chmod +x build_sdl_input_only.sh
#     ./build_sdl_input_only.sh
#
#   Windows (MSYS2 MinGW64 environment):
#     Open "MSYS2 MSYS" terminal, then:
#       export PATH="/mingw64/bin:$PATH"
#       cd /c/Users/.../prebuilt
#       ./build_sdl_input_only.sh
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDL_SOURCE_DIR="${SCRIPT_DIR}/../third_party/SDL"
BUILD_ROOT="${SCRIPT_DIR}/sdl/build"
INSTALL_DIR="${SCRIPT_DIR}/sdl"

# Detect operating system
OS_NAME="$(uname -s)"

# macOS minimum deployment target (reduce system version dependency)
MACOS_DEPLOYMENT_TARGET="10.13"

# Build type
BUILD_TYPE="Release"

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

    # ✅ Keep: Video (keyboard/mouse event capture depends on video subsystem's platform backend)
    -DSDL_VIDEO=ON

    # ✅ Keep: Joystick + Hidapi (joystick/gamepad functionality)
    -DSDL_JOYSTICK=ON
    -DSDL_HIDAPI=ON
    -DSDL_HIDAPI_JOYSTICK=ON
    -DSDL_VIRTUAL_JOYSTICK=ON
    # ❌ Disable: libusb (macOS uses native IOKit HID; Windows uses native HID)
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

# ─────────────────────────────────────────────
# Platform-specific CMake options
# ─────────────────────────────────────────────
if [ "$OS_NAME" = "Darwin" ]; then
    SDL_CMAKE_PLATFORM_OPTIONS=(
        # Video backend: only keep Cocoa (macOS window system, source of keyboard/mouse events)
        -DSDL_COCOA=ON
        -DSDL_X11=OFF
        -DSDL_WAYLAND=OFF
        -DSDL_KMSDRM=OFF
        -DSDL_RPI=OFF
        -DSDL_ROCKCHIP=OFF
        -DSDL_VIVANTE=OFF
        -DSDL_OFFSCREEN=OFF
        -DSDL_DUMMYVIDEO=OFF
    )
elif [[ "$OS_NAME" == MINGW* ]] || [[ "$OS_NAME" == MSYS* ]]; then
    SDL_CMAKE_PLATFORM_OPTIONS=(
        # Video backend: Windows (source of keyboard/mouse events)
        # Note: Windows does not need SDL_WINDOWS_VIDEO explicitly enabled; CMake handles it automatically
        -DSDL_COCOA=OFF
        -DSDL_X11=OFF
        -DSDL_WAYLAND=OFF
        -DSDL_KMSDRM=OFF
        -DSDL_RPI=OFF
        -DSDL_ROCKCHIP=OFF
        -DSDL_VIVANTE=OFF
        -DSDL_OFFSCREEN=OFF
        -DSDL_DUMMYVIDEO=OFF
        # Windows-specific: DirectX (required by video backend), XInput (required by joystick)
        -DSDL_DIRECTX=ON
        -DSDL_XINPUT=ON
        # WASAPI (audio, not needed)
        -DSDL_WASAPI=OFF
    )
else
    echo "❌ Error: Unsupported operating system '${OS_NAME}'"
    echo "   Currently only supports macOS and Windows (MSYS2 MinGW64)"
    exit 1
fi

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
    elif [[ "$OS_NAME" == MINGW* ]] || [[ "$OS_NAME" == MSYS* ]]; then
        nproc 2>/dev/null || echo 4
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
        if [ "$OS_NAME" = "Darwin" ]; then
            echo "   Please install: brew install cmake"
        elif [[ "$OS_NAME" == MINGW* ]] || [[ "$OS_NAME" == MSYS* ]]; then
            echo "   Please install: pacman -S mingw-w64-x86_64-cmake"
            echo "   Make sure to use MSYS2 MinGW64 environment: export PATH=/mingw64/bin:\$PATH"
        fi
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
    fi

    if [ "$OS_NAME" = "Darwin" ]; then
        echo "✅ macOS Deployment Target: ${MACOS_DEPLOYMENT_TARGET}"
    fi
}

# ─────────────────────────────────────────────
# Build single architecture (macOS)
# ─────────────────────────────────────────────
build_arch_macos() {
    local arch="$1"
    local build_dir="${BUILD_ROOT}/${arch}"
    local install_dir="${BUILD_ROOT}/${arch}-install"

    log_info "Building architecture: ${arch} (macOS)"

    rm -rf "${build_dir}"
    mkdir -p "${build_dir}"

    cmake -S "${SDL_SOURCE_DIR}" -B "${build_dir}" \
        -G "${CMAKE_GENERATOR}" \
        -DCMAKE_OSX_ARCHITECTURES="${arch}" \
        -DCMAKE_OSX_DEPLOYMENT_TARGET="${MACOS_DEPLOYMENT_TARGET}" \
        -DCMAKE_INSTALL_PREFIX="${install_dir}" \
        "${SDL_CMAKE_COMMON_OPTIONS[@]}" \
        "${SDL_CMAKE_PLATFORM_OPTIONS[@]}"

    cmake --build "${build_dir}" --config "${BUILD_TYPE}" -j "$(get_cpu_count)"

    cmake --install "${build_dir}" --config "${BUILD_TYPE}"

    echo "✅ ${arch} build complete: ${install_dir}"
}

# ─────────────────────────────────────────────
# Build Windows x86_64
# ─────────────────────────────────────────────
build_windows_x86_64() {
    local build_dir="${BUILD_ROOT}/windows-x86_64"
    local install_dir="${BUILD_ROOT}/windows-x86_64-install"

    log_info "Building architecture: x86_64 (Windows MinGW)"

    rm -rf "${build_dir}"
    mkdir -p "${build_dir}"

    cmake -S "${SDL_SOURCE_DIR}" -B "${build_dir}" \
        -G "${CMAKE_GENERATOR}" \
        -DCMAKE_INSTALL_PREFIX="${install_dir}" \
        "${SDL_CMAKE_COMMON_OPTIONS[@]}" \
        "${SDL_CMAKE_PLATFORM_OPTIONS[@]}"

    cmake --build "${build_dir}" --config "${BUILD_TYPE}" -j "$(get_cpu_count)"

    cmake --install "${build_dir}" --config "${BUILD_TYPE}"

    # Strip all symbols to reduce DLL size (MinGW/GCC embeds DWARF debug info by default)
    # Official SDL3 releases are ~2.5MB; without strip MinGW produces ~4.4MB
    if command -v strip &>/dev/null; then
        local dll_file
        for dll_file in "${install_dir}/bin/"SDL3.dll "${build_dir}/"SDL3.dll; do
            if [ -f "${dll_file}" ]; then
                local before_size after_size
                before_size=$(wc -c < "${dll_file}")
                strip --strip-all "${dll_file}"
                after_size=$(wc -c < "${dll_file}")
                echo "  Stripped SDL3.dll: ${before_size} -> ${after_size} bytes"
                break
            fi
        done
    fi

    echo "✅ Windows x86_64 build complete: ${install_dir}"
}

# ─────────────────────────────────────────────
# Organize artifacts - macOS (separate by architecture)
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
# Organize artifacts - Windows
# ─────────────────────────────────────────────
organize_output_windows() {
    local staging_dir="${BUILD_ROOT}/windows-x86_64-install"

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
    if [ "${dll_found}" = "0" ] && [ -f "${BUILD_ROOT}/windows-x86_64/SDL3.dll" ]; then
        cp "${BUILD_ROOT}/windows-x86_64/SDL3.dll" "${INSTALL_DIR}/windows-x86_64/"
        echo "  ✅ Copied SDL3.dll from build dir"
        dll_found=1
    fi
    if [ "${dll_found}" = "0" ]; then
        echo "  ⚠️  SDL3.dll not found at expected location, searching build tree..."
        find "${BUILD_ROOT}/windows-x86_64" -name "SDL3.dll" -type f 2>/dev/null | while read -r dll; do
            cp "${dll}" "${INSTALL_DIR}/windows-x86_64/"
            echo "  ✅ Found: ${dll}"
            dll_found=1
        done
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

Trimmed features: Audio, GPU, Render, Camera, Haptic, Power, Dialog, Tray
                  OpenGL, Vulkan, Metal and other graphics rendering
Retained features: Keyboard, Mouse, Joystick/Gamepad, Sensor, basic event system
All dependencies are macOS system frameworks; no extra libraries need to be installed.
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

Trimmed features: Audio, GPU, Render, Camera, Haptic, Power, Dialog, Tray
                  OpenGL, Vulkan, D3D rendering
Retained features: Keyboard, Mouse, Joystick/Gamepad, Sensor, basic event system
All dependencies are Windows system libraries; no extra libraries need to be installed.
EOF
}

# ─────────────────────────────────────────────
# Main flow
# ─────────────────────────────────────────────
main() {
    log_info "SDL3 trimmed build - Input devices only (Keyboard/Mouse/Joystick)"
    echo "Platform:      ${OS_NAME}"
    echo "Source dir:    ${SDL_SOURCE_DIR}"
    echo "Build dir:     ${BUILD_ROOT}"
    echo "Artifact dir:  ${INSTALL_DIR}"

    check_prerequisites

    if [ "$OS_NAME" = "Darwin" ]; then
        # macOS: build both architectures separately
        build_arch_macos "arm64"
        build_arch_macos "x86_64"
        organize_output_macos
        verify_output_macos
        print_usage_macos
    elif [[ "$OS_NAME" == MINGW* ]] || [[ "$OS_NAME" == MSYS* ]]; then
        # Windows: build x86_64
        build_windows_x86_64
        organize_output_windows
        verify_output_windows
        print_usage_windows
    fi

    log_info "✅ All done!"
}

main "$@"

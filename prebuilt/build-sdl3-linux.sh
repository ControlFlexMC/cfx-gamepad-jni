#!/bin/bash
#
# SDL3 trimmed build script for Linux — joystick/gamepad input only.
# Video (X11/Wayland), audio, rendering etc. are all disabled.
# No X11 or Wayland dev packages required.
#
# Build artifacts:
#   prebuilt/sdl/linux-x86_64/libSDL3.so         - Linux x64
#   prebuilt/sdl/linux-aarch64/libSDL3.so        - Linux ARM64
#   prebuilt/sdl/include/SDL3/                   - Header files
#
# Usage:
#   cd prebuilt
#   chmod +x build-sdl3-linux.sh
#   ./build-sdl3-linux.sh              # Build for native architecture
#   ./build-sdl3-linux.sh --all        # Build both x86_64 and aarch64
#   ./build-sdl3-linux.sh --arch aarch64  # Build for specific architecture
#
# Prerequisites (minimal):
#   Ubuntu/Debian:  sudo apt install cmake gcc build-essential
#   Fedora/RHEL:    sudo dnf install cmake gcc make
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDL_SOURCE_DIR="${SCRIPT_DIR}/../third_party/SDL"
BUILD_ROOT="${SCRIPT_DIR}/sdl/build"
INSTALL_DIR="${SCRIPT_DIR}/sdl"

# Detect operating system
OS_NAME="$(uname -s)"

if [ "$OS_NAME" != "Linux" ]; then
    echo "Error: this script is for Linux only. Detected OS: ${OS_NAME}"
    exit 1
fi

# Build type
BUILD_TYPE="Release"

# Detect native architecture
NATIVE_ARCH="$(uname -m)"
if [ "$NATIVE_ARCH" = "aarch64" ]; then
    NATIVE_ARCH="aarch64"
else
    NATIVE_ARCH="x86_64"
fi

# Parse arguments
BUILD_ALL=false
TARGET_ARCH=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --all) BUILD_ALL=true; shift ;;
        --arch) TARGET_ARCH="$2"; shift 2 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

# Determine architectures to build
if [ "$BUILD_ALL" = true ]; then
    ARCHS=("x86_64" "aarch64")
elif [ -n "$TARGET_ARCH" ]; then
    ARCHS=("$TARGET_ARCH")
else
    ARCHS=("$NATIVE_ARCH")
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
    #            Disabling video avoids the X11/Wayland dev package requirement.
    -DSDL_VIDEO=OFF

    # Linux: skip the X11/Wayland hard-error check when video is disabled.
    # Without this, SDL's cmake/macros.cmake:414 still FATAL_ERRORs even with SDL_VIDEO=OFF.
    -DSDL_UNIX_CONSOLE_BUILD=ON

    # ✅ Keep: Joystick + Hidapi (joystick/gamepad functionality)
    -DSDL_JOYSTICK=ON
    -DSDL_HIDAPI=ON
    -DSDL_HIDAPI_JOYSTICK=ON
    -DSDL_VIRTUAL_JOYSTICK=ON
    # ❌ Disable: libusb — not needed; joystick works through hidraw (/dev/hidraw*)
    #            and evdev (/dev/input/event*) kernel interfaces directly.
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
# Linux-specific CMake options (video is off, no windowing backends needed)
# ─────────────────────────────────────────────
SDL_CMAKE_PLATFORM_OPTIONS=(
    # All video backends disabled (SDL_VIDEO=OFF above)
    -DSDL_X11=OFF
    -DSDL_WAYLAND=OFF
    -DSDL_COCOA=OFF
    -DSDL_KMSDRM=OFF
    -DSDL_RPI=OFF
    -DSDL_ROCKCHIP=OFF
    -DSDL_VIVANTE=OFF
    -DSDL_OFFSCREEN=OFF
    -DSDL_DUMMYVIDEO=OFF
    # Linux-specific: irrelevant without video
    -DSDL_DIRECTX=OFF
    -DSDL_XINPUT=OFF
    -DSDL_WASAPI=OFF
)

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
    nproc 2>/dev/null || echo 4
}

# ─────────────────────────────────────────────
# Check build environment
# ─────────────────────────────────────────────
check_prerequisites() {
    log_info "Checking build environment (Linux)"

    if ! command -v cmake &>/dev/null; then
        echo "❌ Error: cmake not found"
        echo "   Ubuntu/Debian: sudo apt install cmake"
        echo "   Fedora/RHEL:   sudo dnf install cmake"
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

    if ! command -v gcc &>/dev/null && ! command -v clang &>/dev/null; then
        echo "❌ Error: C compiler not found"
        echo "   Ubuntu/Debian: sudo apt install build-essential"
        echo "   Fedora/RHEL:   sudo dnf install gcc"
        exit 1
    fi
    echo "✅ $(gcc --version 2>/dev/null | head -1 || clang --version 2>/dev/null | head -1)"

    # Video is disabled — no X11/Wayland needed
}

# ─────────────────────────────────────────────
# Build for a given architecture
# ─────────────────────────────────────────────
build_arch() {
    local arch="$1"
    local build_dir="${BUILD_ROOT}/${arch}"
    local install_dir="${BUILD_ROOT}/${arch}-install"

    log_info "Building SDL3 for ${arch} (Linux)"

    rm -rf "${build_dir}"
    mkdir -p "${build_dir}"

    local cmake_args=(
        -S "${SDL_SOURCE_DIR}" -B "${build_dir}"
        -G "${CMAKE_GENERATOR}"
        -DCMAKE_INSTALL_PREFIX="${install_dir}"
        "${SDL_CMAKE_COMMON_OPTIONS[@]}"
        "${SDL_CMAKE_PLATFORM_OPTIONS[@]}"
    )

    # Cross-compile from x86_64 to aarch64
    if [ "$arch" = "aarch64" ] && [ "$NATIVE_ARCH" != "aarch64" ]; then
        if ! command -v aarch64-linux-gnu-gcc &>/dev/null; then
            echo "Error: cross-compiler aarch64-linux-gnu-gcc not found"
            echo "  Install: sudo apt install gcc-aarch64-linux-gnu"
            exit 1
        fi
        cmake_args+=(
            -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc
            -DCMAKE_SYSTEM_PROCESSOR=aarch64
            -DCMAKE_SYSTEM_NAME=Linux
        )
        echo "  Cross-compiling with aarch64-linux-gnu-gcc"
    fi

    cmake "${cmake_args[@]}"

    cmake --build "${build_dir}" --config "${BUILD_TYPE}" -j "$(get_cpu_count)"

    cmake --install "${build_dir}" --config "${BUILD_TYPE}"

    echo "✅ ${arch} build complete: ${install_dir}"
}

# ─────────────────────────────────────────────
# Organize artifacts
# ─────────────────────────────────────────────
organize_output() {
    log_info "Organizing build artifacts (Linux)"

    # Clean old artifact directories (keep build/ cache)
    rm -rf "${INSTALL_DIR}/linux-x86_64"
    rm -rf "${INSTALL_DIR}/linux-aarch64"
    rm -rf "${INSTALL_DIR}/include"

    mkdir -p "${INSTALL_DIR}/linux-x86_64"
    mkdir -p "${INSTALL_DIR}/linux-aarch64"

    for arch in "${ARCHS[@]}"; do
        local staging="${BUILD_ROOT}/${arch}-install"
        local target
        if [ "$arch" = "x86_64" ]; then
            target="${INSTALL_DIR}/linux-x86_64"
        else
            target="${INSTALL_DIR}/linux-aarch64"
        fi

        if [ ! -d "${staging}" ]; then
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
    else
        echo "  ⚠️  Header directory not found, skipping"
    fi

    echo ""
    echo "✅ Artifact organization complete"
}

# ─────────────────────────────────────────────
# Verify artifacts
# ─────────────────────────────────────────────
verify_output() {
    log_info "Verifying build artifacts (Linux)"

    echo "📁 Install directory: ${INSTALL_DIR}"
    echo ""

    # Check per-architecture shared libraries
    echo "📦 Per-architecture shared libraries (for JAR packaging):"
    for arch in x86_64 aarch64; do
        if [ "$arch" = "x86_64" ]; then
            local target="${INSTALL_DIR}/linux-x86_64"
        else
            local target="${INSTALL_DIR}/linux-aarch64"
        fi
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
# Print usage
# ─────────────────────────────────────────────
print_usage() {
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
# Main flow
# ─────────────────────────────────────────────
main() {
    log_info "SDL3 trimmed build — Joystick/Gamepad input only"
    echo "Platform:      ${OS_NAME}"
    echo "Native arch:   ${NATIVE_ARCH}"
    echo "Build targets: ${ARCHS[*]}"
    echo "Source dir:    ${SDL_SOURCE_DIR}"
    echo "Build dir:     ${BUILD_ROOT}"
    echo "Artifact dir:  ${INSTALL_DIR}"

    check_prerequisites

    for arch in "${ARCHS[@]}"; do
        build_arch "$arch"
    done

    organize_output
    verify_output
    print_usage

    log_info "✅ All done!"
}

main "$@"

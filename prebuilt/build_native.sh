#!/bin/bash
#
# Build the gamepad-jni native library
# Supports macOS + Windows (MSYS2 MinGW64) platforms
#
# Build artifacts:
#   macOS:
#     prebuilt/jni/darwin-aarch64/libgamepadjni.dylib  - Apple Silicon
#     prebuilt/jni/darwin-x86_64/libgamepadjni.dylib   - Intel Mac
#   Windows:
#     prebuilt/jni/windows-x86_64/gamepadjni.dll       - Windows x64
#   Linux:
#     prebuilt/jni/linux-x86_64/libgamepadjni.so       - Linux x64
#
# Usage:
#   macOS:
#     cd prebuilt
#     chmod +x build_native.sh
#     ./build_native.sh              # Build for current architecture
#     ./build_native.sh --all        # Build for all macOS architectures (arm64 + x86_64)
#     ./build_native.sh --arch arm64 # Build for specific macOS architecture
#
#   Windows (MSYS2 MinGW64):
#     Double-click build_native.bat
#     Or in MSYS2 terminal:
#       export PATH=/mingw64/bin:$PATH
#       cd /c/Users/.../prebuilt
#       ./build_native.sh
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# gamepad-jni project root (one level up from prebuilt/)
GAMEPAD_JNI_ROOT="${SCRIPT_DIR}/.."
BUILD_ROOT="${SCRIPT_DIR}/jni/build"
INSTALL_DIR="${SCRIPT_DIR}/jni"

# Detect OS
OS_NAME="$(uname -s)"

# macOS minimum deployment target
MACOS_DEPLOYMENT_TARGET="10.13"

# Build type
BUILD_TYPE="Release"

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

# ─────────────────────────────────────────────
# Check prerequisites
# ─────────────────────────────────────────────

if [ -z "${JAVA_HOME:-}" ]; then
    # Try platform-specific auto-detection
    if [ "$OS_NAME" = "Darwin" ]; then
        JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || echo "")"
    fi
    if [ -z "${JAVA_HOME:-}" ]; then
        echo "Error: JAVA_HOME not set and cannot be auto-detected"
        echo "   Please set JAVA_HOME to your JDK installation path"
        exit 1
    fi
fi

# On Windows/MSYS2, convert Windows-style JAVA_HOME to Unix path for CMake
if [[ "$OS_NAME" == MINGW* ]] || [[ "$OS_NAME" == MSYS* ]]; then
    if [[ "$JAVA_HOME" == *\\* ]] || [[ "$JAVA_HOME" == [A-Za-z]:* ]]; then
        JAVA_HOME_UNIX="$(cygpath -u "$JAVA_HOME" 2>/dev/null || echo "$JAVA_HOME")"
        JAVA_HOME="$JAVA_HOME_UNIX"
    fi
fi

echo "JAVA_HOME: ${JAVA_HOME}"

if ! command -v cmake &>/dev/null; then
    echo "Error: cmake not found."
    echo "   macOS:   brew install cmake"
    echo "   Windows: pacman -S mingw-w64-x86_64-cmake"
    echo "   Linux:   sudo apt install cmake"
    exit 1
fi
echo "cmake: $(cmake --version | head -1)"

# Determine number of CPU cores
if [ "$OS_NAME" = "Darwin" ]; then
    NCPUS="$(sysctl -n hw.logicalcpu 2>/dev/null || echo 4)"
else
    NCPUS="$(nproc 2>/dev/null || echo 4)"
fi

# ─────────────────────────────────────────────
# Determine architectures to build
# ─────────────────────────────────────────────

if [ "$OS_NAME" = "Darwin" ]; then
    if [ "$BUILD_ALL" = true ]; then
        ARCHS=("arm64" "x86_64")
    elif [ -n "$TARGET_ARCH" ]; then
        ARCHS=("$TARGET_ARCH")
    else
        ARCHS=("$(uname -m)")
        echo "Building for current architecture: ${ARCHS[0]}"
    fi
else
    ARCHS=("native")
    echo "Building for native platform"
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

# ─────────────────────────────────────────────
# Build function
# ─────────────────────────────────────────────

build_arch() {
    local arch="$1"
    local build_dir="${BUILD_ROOT}/${arch}"

    log_info "Building gamepad-jni for ${arch} (${OS_NAME})"

    rm -rf "${build_dir}"
    mkdir -p "${build_dir}"

    # Configure
    local cmake_args=(-DCMAKE_BUILD_TYPE=Release -DJAVA_HOME="${JAVA_HOME}")
    if [ "$OS_NAME" = "Darwin" ] && [ "$arch" != "native" ]; then
        cmake_args+=(-DCMAKE_OSX_ARCHITECTURES="${arch}" -DCMAKE_OSX_DEPLOYMENT_TARGET="${MACOS_DEPLOYMENT_TARGET}")
    fi

    cmake -S "${GAMEPAD_JNI_ROOT}" -B "${build_dir}" "${cmake_args[@]}"

    # Build
    cmake --build "${build_dir}" --config Release -j "${NCPUS}"

    echo "${arch} build complete"
}

# ─────────────────────────────────────────────
# Organize output - macOS
# ─────────────────────────────────────────────

organize_output_macos() {
    log_info "Organizing build artifacts (macOS)"

    # Clean old output directories
    rm -rf "${INSTALL_DIR}/darwin-aarch64"
    rm -rf "${INSTALL_DIR}/darwin-x86_64"

    mkdir -p "${INSTALL_DIR}/darwin-aarch64"
    mkdir -p "${INSTALL_DIR}/darwin-x86_64"

    # Copy arm64 dylib
    local arm64_build="${BUILD_ROOT}/arm64"
    if [ -f "${arm64_build}/gamepadjni.dylib" ]; then
        cp "${arm64_build}/gamepadjni.dylib" "${INSTALL_DIR}/darwin-aarch64/libgamepadjni.dylib"
        echo "  Copied arm64: $(ls -lh "${INSTALL_DIR}/darwin-aarch64/libgamepadjni.dylib" | awk '{print $5}')"
    elif [ -d "${arm64_build}" ]; then
        find "${arm64_build}" -name "*.dylib" -type f | while read -r dylib; do
            cp "${dylib}" "${INSTALL_DIR}/darwin-aarch64/"
            echo "  Copied arm64: $(basename "$dylib")"
        done
    fi

    # Copy x86_64 dylib
    local x86_64_build="${BUILD_ROOT}/x86_64"
    if [ -f "${x86_64_build}/gamepadjni.dylib" ]; then
        cp "${x86_64_build}/gamepadjni.dylib" "${INSTALL_DIR}/darwin-x86_64/libgamepadjni.dylib"
        echo "  Copied x86_64: $(ls -lh "${INSTALL_DIR}/darwin-x86_64/libgamepadjni.dylib" | awk '{print $5}')"
    elif [ -d "${x86_64_build}" ]; then
        find "${x86_64_build}" -name "*.dylib" -type f | while read -r dylib; do
            cp "${dylib}" "${INSTALL_DIR}/darwin-x86_64/"
            echo "  Copied x86_64: $(basename "$dylib")"
        done
    fi

    echo ""
    echo "Artifacts organized"
}

# ─────────────────────────────────────────────
# Organize output - Windows
# ─────────────────────────────────────────────

organize_output_windows() {
    log_info "Organizing build artifacts (Windows)"

    # Clean old output directory
    rm -rf "${INSTALL_DIR}/windows-x86_64"

    mkdir -p "${INSTALL_DIR}/windows-x86_64"

    local native_build="${BUILD_ROOT}/native"

    # Copy gamepadjni.dll
    local dll_found=0
    if [ -f "${native_build}/gamepadjni.dll" ]; then
        cp "${native_build}/gamepadjni.dll" "${INSTALL_DIR}/windows-x86_64/"
        echo "  Copied gamepadjni.dll from build dir"
        dll_found=1
    fi
    if [ "${dll_found}" = "0" ]; then
        # Search in cmake-native subdirectory
        for dll in "${BUILD_ROOT}"/*/gamepadjni.dll "${BUILD_ROOT}"/*/gamepadjni.dll; do
            if [ -f "$dll" ]; then
                cp "$dll" "${INSTALL_DIR}/windows-x86_64/"
                echo "  Copied gamepadjni.dll: $(basename "$dll")"
                dll_found=1
                break
            fi
        done
    fi
    if [ "${dll_found}" = "0" ]; then
        # Search more broadly
        find "${BUILD_ROOT}" -name "gamepadjni.dll" -type f 2>/dev/null | while read -r dll; do
            cp "${dll}" "${INSTALL_DIR}/windows-x86_64/"
            echo "  Found and copied: ${dll}"
            dll_found=1
        done
    fi

    # Also check the CMake post-build copy location (native_build at project root)
    local legacy_dir="${GAMEPAD_JNI_ROOT}/native_build/windows-x86_64"
    if [ -f "${legacy_dir}/gamepadjni.dll" ] && [ "${dll_found}" = "0" ]; then
        cp "${legacy_dir}/gamepadjni.dll" "${INSTALL_DIR}/windows-x86_64/"
        echo "  Copied gamepadjni.dll from legacy native_build dir"
    fi

    # Strip DLL to reduce size (MinGW embeds DWARF debug info)
    if command -v strip &>/dev/null && [ -f "${INSTALL_DIR}/windows-x86_64/gamepadjni.dll" ]; then
        local before_size after_size
        before_size=$(wc -c < "${INSTALL_DIR}/windows-x86_64/gamepadjni.dll")
        strip --strip-all "${INSTALL_DIR}/windows-x86_64/gamepadjni.dll"
        after_size=$(wc -c < "${INSTALL_DIR}/windows-x86_64/gamepadjni.dll")
        echo "  Stripped gamepadjni.dll: ${before_size} -> ${after_size} bytes"
    fi

    echo ""
    echo "Artifacts organized"
}

# ─────────────────────────────────────────────
# Verify output
# ─────────────────────────────────────────────

verify_output() {
    log_info "Verifying build artifacts"

    echo "Install directory: ${INSTALL_DIR}"
    echo ""

    # List all native libraries
    echo "Artifacts:"
    find "${INSTALL_DIR}" -type f \( -name "*.dylib" -o -name "*.so" -o -name "*.dll" \) | sort | while read f; do
        echo "  $(ls -lh "$f" | awk '{print $5, $NF}')"
    done
}

# ─────────────────────────────────────────────
# Print usage info
# ─────────────────────────────────────────────

print_usage() {
    log_info "Integration info"

    cat <<EOF
JAR packaging structure:

  gamepad-jni.jar
  └── native/
      ├── darwin-aarch64/
      │   ├── libSDL3.0.dylib
      │   └── libgamepadjni.dylib
      ├── darwin-x86_64/
      │   ├── libSDL3.0.dylib
      │   └── libgamepadjni.dylib
      └── windows-x86_64/
          ├── SDL3.dll
          └── gamepadjni.dll

Build artifacts:
  ${INSTALL_DIR}/

Next steps:
  1. Run build_jar.bat (or gradle jar) to package JAR
  2. Or run deploy_gamepad-jni.bat for full deploy
EOF
}

# ─────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────

main() {
    log_info "gamepad-jni native build"
    echo "Platform:     ${OS_NAME}"
    echo "Project root: ${GAMEPAD_JNI_ROOT}"
    echo "Build dir:    ${BUILD_ROOT}"
    echo "Install dir:  ${INSTALL_DIR}"

    for arch in "${ARCHS[@]}"; do
        build_arch "$arch"
    done

    if [ "$OS_NAME" = "Darwin" ]; then
        organize_output_macos
    elif [[ "$OS_NAME" == MINGW* ]] || [[ "$OS_NAME" == MSYS* ]]; then
        organize_output_windows
    fi

    verify_output
    print_usage

    log_info "All builds complete!"
}

main "$@"

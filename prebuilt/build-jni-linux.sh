#!/bin/bash
#
# Build the gamepad-jni native library for Linux
#
# Build artifacts:
#   prebuilt/jni/linux-x86_64/libgamepadjni.so   - Linux x64
#   prebuilt/jni/linux-aarch64/libgamepadjni.so  - Linux ARM64
#
# Usage:
#   cd prebuilt
#   chmod +x build-jni-linux.sh
#   ./build-jni-linux.sh              # Build for native architecture
#   ./build-jni-linux.sh --all        # Build both x86_64 and aarch64
#   ./build-jni-linux.sh --arch aarch64  # Build for specific architecture
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# gamepad-jni project root (one level up from prebuilt/)
GAMEPAD_JNI_ROOT="${SCRIPT_DIR}/.."
BUILD_ROOT="${SCRIPT_DIR}/jni/build"
INSTALL_DIR="${SCRIPT_DIR}/jni"

# Detect OS
OS_NAME="$(uname -s)"

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
    JAVA_HOME="$(readlink -e /etc/alternatives/java 2>/dev/null | sed 's:/jre/bin/java::; s:/bin/java::' || echo "")"
    if [ -z "${JAVA_HOME:-}" ] && [ -d "/usr/lib/jvm" ]; then
        # Find the first JDK in /usr/lib/jvm
        JAVA_HOME="$(find /usr/lib/jvm -maxdepth 1 -type d -name 'java-*-openjdk*' -o -name 'jdk-*' -o -name 'java-*-oracle' 2>/dev/null | head -1)"
    fi
    if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/jdk" ]; then
        # Find the first JDK in ~/jdk
        JAVA_HOME="$(find "$HOME/jdk" -maxdepth 1 -type d -name 'jdk-*' 2>/dev/null | head -1)"
    fi
    if [ -z "${JAVA_HOME:-}" ]; then
        echo "Error: JAVA_HOME not set and cannot be auto-detected"
        echo "   Please set JAVA_HOME to your JDK installation path"
        echo "   On Ubuntu/Debian: sudo apt install openjdk-11-jdk"
        echo "   Fedora/RHEL:   sudo dnf install java-11-openjdk-devel"
        exit 1
    fi
fi

echo "JAVA_HOME: ${JAVA_HOME}"

if ! command -v cmake &>/dev/null; then
    echo "Error: cmake not found."
    echo "   Ubuntu/Debian: sudo apt install cmake"
    echo "   Fedora/RHEL:   sudo dnf install cmake"
    exit 1
fi
echo "cmake: $(cmake --version | head -1)"

if ! command -v gcc &>/dev/null && ! command -v clang &>/dev/null; then
    echo "Error: C compiler not found."
    echo "   Ubuntu/Debian: sudo apt install build-essential"
    echo "   Fedora/RHEL:   sudo dnf install gcc"
    exit 1
fi

# Determine number of CPU cores
NCPUS="$(nproc 2>/dev/null || echo 4)"

# Detect native architecture
NATIVE_ARCH="$(uname -m)"
if [ "$NATIVE_ARCH" = "aarch64" ]; then
    NATIVE_ARCH="aarch64"
else
    NATIVE_ARCH="x86_64"
fi

# ─────────────────────────────────────────────
# Determine architectures to build
# ─────────────────────────────────────────────

if [ "$BUILD_ALL" = true ]; then
    ARCHS=("x86_64" "aarch64")
elif [ -n "$TARGET_ARCH" ]; then
    ARCHS=("$TARGET_ARCH")
else
    ARCHS=("$NATIVE_ARCH")
    echo "Building for native architecture: ${ARCHS[0]}"
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

    log_info "Building gamepad-jni for ${arch} (Linux)"

    rm -rf "${build_dir}"
    mkdir -p "${build_dir}"

    local cmake_args=(-DCMAKE_BUILD_TYPE="${BUILD_TYPE}" -DJAVA_HOME="${JAVA_HOME}")

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

    cmake -S "${GAMEPAD_JNI_ROOT}" -B "${build_dir}" "${cmake_args[@]}"

    cmake --build "${build_dir}" --config "${BUILD_TYPE}" -j "${NCPUS}"

    echo "${arch} build complete"
}

# ─────────────────────────────────────────────
# Organize output
# ─────────────────────────────────────────────

organize_output() {
    log_info "Organizing build artifacts (Linux)"

    for arch in "${ARCHS[@]}"; do
        local install_arch_dir
        if [ "$arch" = "x86_64" ]; then
            install_arch_dir="${INSTALL_DIR}/linux-x86_64"
        elif [ "$arch" = "aarch64" ]; then
            install_arch_dir="${INSTALL_DIR}/linux-aarch64"
        else
            install_arch_dir="${INSTALL_DIR}/linux-${arch}"
        fi

        # Clean old output
        rm -rf "${install_arch_dir}"
        mkdir -p "${install_arch_dir}"

        local build_dir="${BUILD_ROOT}/${arch}"

        # Copy libgamepadjni.so
        if [ -f "${build_dir}/libgamepadjni.so" ]; then
            cp "${build_dir}/libgamepadjni.so" "${install_arch_dir}/libgamepadjni.so"
            echo "  Copied ${arch}: $(ls -lh "${install_arch_dir}/libgamepadjni.so" | awk '{print $5}')"
        else
            # Search for the .so file
            find "${build_dir}" -name "*.so" -type f | while read -r sofile; do
                cp "${sofile}" "${install_arch_dir}/"
                echo "  Copied ${arch}: $(basename "$sofile")"
            done
        fi

        # Strip debug symbols to reduce size
        local strip_cmd="strip"
        if [ "$arch" = "aarch64" ] && [ "$NATIVE_ARCH" != "aarch64" ] && command -v aarch64-linux-gnu-strip &>/dev/null; then
            strip_cmd="aarch64-linux-gnu-strip"
        fi
        if command -v "$strip_cmd" &>/dev/null && [ -f "${install_arch_dir}/libgamepadjni.so" ]; then
            local before_size after_size
            before_size=$(wc -c < "${install_arch_dir}/libgamepadjni.so")
            "$strip_cmd" --strip-all "${install_arch_dir}/libgamepadjni.so" 2>/dev/null || "$strip_cmd" "${install_arch_dir}/libgamepadjni.so" 2>/dev/null || true
            after_size=$(wc -c < "${install_arch_dir}/libgamepadjni.so")
            echo "  Stripped libgamepadjni.so: ${before_size} -> ${after_size} bytes"
        fi
    done

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

    echo "Artifacts:"
    find "${INSTALL_DIR}" -type f -name "*.so" | sort | while read f; do
        echo "  $(ls -lh "$f" | awk '{print $5, $NF}')"
    done
    echo ""

    # Check shared library dependencies
    for sofile in "${INSTALL_DIR}"/linux-*/libgamepadjni.so; do
        if [ -f "${sofile}" ]; then
            echo "Dependencies of $(basename "$(dirname "$sofile")")/libgamepadjni.so:"
            if command -v ldd &>/dev/null; then
                ldd "${sofile}" | sed 's/^/  /'
            elif command -v objdump &>/dev/null; then
                objdump -p "${sofile}" 2>/dev/null | grep NEEDED | sed 's/^/  /'
            fi
            break
        fi
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
      ├── linux-x86_64/
      │   ├── libSDL3.so
      │   └── libgamepadjni.so
      └── linux-aarch64/
          ├── libSDL3.so
          └── libgamepadjni.so

Build artifacts:
  ${INSTALL_DIR}/

Next steps:
  1. Run build-sdl3-linux.sh to build SDL3 for Linux
  2. Run gradle jar to package JAR
EOF
}

# ─────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────

main() {
    log_info "gamepad-jni native build (Linux)"
    echo "Platform:     ${OS_NAME}"
    echo "Project root: ${GAMEPAD_JNI_ROOT}"
    echo "Build dir:    ${BUILD_ROOT}"
    echo "Install dir:  ${INSTALL_DIR}"

    for arch in "${ARCHS[@]}"; do
        build_arch "$arch"
    done

    organize_output
    verify_output
    print_usage

    log_info "All builds complete!"
}

main "$@"

#!/bin/bash
#
# Build the gamepad-jni native library
# Unified: macOS, Linux, Windows (MSYS2 MinGW64)
#
# Build artifacts:
#   macOS:
#     prebuilt/jni/darwin-aarch64/libgamepadjni.dylib  - Apple Silicon
#     prebuilt/jni/darwin-x86_64/libgamepadjni.dylib   - Intel Mac
#   Linux:
#     prebuilt/jni/linux-x86_64/libgamepadjni.so       - Linux x64
#     prebuilt/jni/linux-aarch64/libgamepadjni.so      - Linux ARM64
#   Windows:
#     prebuilt/jni/windows-x86_64/gamepadjni.dll       - Windows x64
#
# Usage:
#   All platforms:
#     cd prebuilt
#     chmod +x build-jni.sh
#     ./build-jni.sh                  # Build for native architecture
#     ./build-jni.sh --all            # Build all architectures
#     ./build-jni.sh --arch aarch64   # Build for specific architecture
#
#   macOS defaults:    native architecture
#   Linux defaults:    native architecture
#   Windows defaults:  x86_64
#
#   Windows (double-click):
#     Simply double-click build-jni-windows.bat
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
GAMEPAD_JNI_ROOT="${SCRIPT_DIR}/.."
BUILD_ROOT="${SCRIPT_DIR}/jni/build"
INSTALL_DIR="${SCRIPT_DIR}/jni"

# ─────────────────────────────────────────────
# OS detection
# ─────────────────────────────────────────────
OS_NAME="$(uname -s)"

if [ "$OS_NAME" != "Darwin" ] && [ "$OS_NAME" != "Linux" ] && [[ "$OS_NAME" != MINGW* ]] && [[ "$OS_NAME" != MSYS* ]]; then
    echo "Error: Unsupported operating system '${OS_NAME}'"
    echo "  Supported: Darwin (macOS), Linux, MINGW*/MSYS* (Windows via MSYS2)"
    exit 1
fi

# macOS minimum deployment target
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
    case "$OS_NAME" in
        Darwin)         ARCHS=("$NATIVE_ARCH") ;;
        Linux)          ARCHS=("$NATIVE_ARCH") ;;
        MINGW*|MSYS*)   ARCHS=("x86_64") ;;
    esac
fi

# ─────────────────────────────────────────────
# JAVA_HOME detection
# ─────────────────────────────────────────────
detect_java_home() {
    if [ -n "${JAVA_HOME:-}" ]; then
        # Already set — validate and convert if needed
        if [[ "$OS_NAME" == MINGW* ]] || [[ "$OS_NAME" == MSYS* ]]; then
            if [[ "$JAVA_HOME" == *\\* ]] || [[ "$JAVA_HOME" == [A-Za-z]:* ]]; then
                JAVA_HOME="$(cygpath -u "$JAVA_HOME" 2>/dev/null || echo "$JAVA_HOME")"
            fi
        fi
        echo "JAVA_HOME: ${JAVA_HOME} (from environment)"
        return
    fi

    case "$OS_NAME" in
        Darwin)
            JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || echo "")"
            ;;
        Linux)
            # Try alternatives symlink first
            JAVA_HOME="$(readlink -e /etc/alternatives/java 2>/dev/null | sed 's:/jre/bin/java::; s:/bin/java::' || echo "")"
            # Try /usr/lib/jvm
            if [ -z "${JAVA_HOME:-}" ] && [ -d "/usr/lib/jvm" ]; then
                JAVA_HOME="$(find /usr/lib/jvm -maxdepth 1 -type d \( -name 'java-*-openjdk*' -o -name 'jdk-*' -o -name 'java-*-oracle' \) 2>/dev/null | head -1)"
            fi
            # Try ~/jdk
            if [ -z "${JAVA_HOME:-}" ] && [ -d "$HOME/jdk" ]; then
                JAVA_HOME="$(find "$HOME/jdk" -maxdepth 1 -type d -name 'jdk-*' 2>/dev/null | head -1)"
            fi
            ;;
    esac

    if [ -z "${JAVA_HOME:-}" ]; then
        echo "Error: JAVA_HOME not set and cannot be auto-detected"
        case "$OS_NAME" in
            Darwin)   echo "   Please set JAVA_HOME or install a JDK" ;;
            Linux)    echo "   Ubuntu/Debian: sudo apt install openjdk-11-jdk"
                      echo "   Fedora/RHEL:   sudo dnf install java-11-openjdk-devel" ;;
            MINGW*|MSYS*) echo "   Please set JAVA_HOME to your JDK installation path" ;;
        esac
        exit 1
    fi

    echo "JAVA_HOME: ${JAVA_HOME} (auto-detected)"
}

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
        echo "Error: cmake not found."
        case "$OS_NAME" in
            Darwin)         echo "   macOS:   brew install cmake" ;;
            Linux)          echo "   Ubuntu/Debian: sudo apt install cmake"
                            echo "   Fedora/RHEL:   sudo dnf install cmake" ;;
            MINGW*|MSYS*)   echo "   Windows: pacman -S mingw-w64-x86_64-cmake" ;;
        esac
        exit 1
    fi
    echo "cmake: $(cmake --version | head -1)"

    # Windows: check gcc
    if [[ "$OS_NAME" == MINGW* ]] || [[ "$OS_NAME" == MSYS* ]]; then
        if ! command -v gcc &>/dev/null; then
            echo "Error: gcc not found"
            echo "  Install: pacman -S mingw-w64-x86_64-gcc"
            exit 1
        fi
        echo "gcc: $(gcc --version | head -1)"
    elif [ "$OS_NAME" = "Linux" ]; then
        if ! command -v gcc &>/dev/null && ! command -v clang &>/dev/null; then
            echo "Error: C compiler not found."
            echo "  Ubuntu/Debian: sudo apt install build-essential"
            echo "  Fedora/RHEL:   sudo dnf install gcc"
            exit 1
        fi
    fi
}

# ─────────────────────────────────────────────
# Build a single architecture
# ─────────────────────────────────────────────
build_arch() {
    local arch="$1"
    local build_dir="${BUILD_ROOT}/${arch}"

    log_info "Building gamepad-jni for ${arch} (${OS_NAME})"

    rm -rf "${build_dir}"
    mkdir -p "${build_dir}"

    local cmake_args=(
        -S "${GAMEPAD_JNI_ROOT}" -B "${build_dir}"
        -DCMAKE_BUILD_TYPE="${BUILD_TYPE}"
        -DJAVA_HOME="${JAVA_HOME}"
    )

    case "$OS_NAME" in
        Darwin)
            cmake_args+=(
                -DCMAKE_OSX_ARCHITECTURES="${arch}"
                -DCMAKE_OSX_DEPLOYMENT_TARGET="${MACOS_DEPLOYMENT_TARGET}"
            )
            ;;
        Linux)
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
            ;;
    esac
    # Windows: no additional cmake args needed (arch is always x86_64)

    cmake "${cmake_args[@]}"
    cmake --build "${build_dir}" --config "${BUILD_TYPE}" -j "$(get_cpu_count)"

    echo "${arch} build complete"
}

# ─────────────────────────────────────────────
# Organize artifacts - macOS
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
    if [ -f "${arm64_build}/libgamepadjni.dylib" ]; then
        cp "${arm64_build}/libgamepadjni.dylib" "${INSTALL_DIR}/darwin-aarch64/libgamepadjni.dylib"
        echo "  Copied arm64: libgamepadjni.dylib"
    elif [ -d "${arm64_build}" ]; then
        find "${arm64_build}" -name "*.dylib" -type f | while read -r dylib; do
            cp "${dylib}" "${INSTALL_DIR}/darwin-aarch64/"
            echo "  Copied arm64: $(basename "$dylib")"
        done
    fi

    # Copy x86_64 dylib
    local x86_64_build="${BUILD_ROOT}/x86_64"
    if [ -f "${x86_64_build}/libgamepadjni.dylib" ]; then
        cp "${x86_64_build}/libgamepadjni.dylib" "${INSTALL_DIR}/darwin-x86_64/libgamepadjni.dylib"
        echo "  Copied x86_64: libgamepadjni.dylib"
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
# Organize artifacts - Linux
# ─────────────────────────────────────────────
organize_output_linux() {
    log_info "Organizing build artifacts (Linux)"

    for arch in "${ARCHS[@]}"; do
        local install_arch_dir
        case "$arch" in
            x86_64)  install_arch_dir="${INSTALL_DIR}/linux-x86_64" ;;
            aarch64) install_arch_dir="${INSTALL_DIR}/linux-aarch64" ;;
            *)       install_arch_dir="${INSTALL_DIR}/linux-${arch}" ;;
        esac

        rm -rf "${install_arch_dir}"
        mkdir -p "${install_arch_dir}"

        local build_dir="${BUILD_ROOT}/${arch}"

        if [ -f "${build_dir}/libgamepadjni.so" ]; then
            cp "${build_dir}/libgamepadjni.so" "${install_arch_dir}/libgamepadjni.so"
            echo "  Copied ${arch}: libgamepadjni.so"
        else
            find "${build_dir}" -name "*.so" -type f | while read -r sofile; do
                cp "${sofile}" "${install_arch_dir}/"
                echo "  Copied ${arch}: $(basename "$sofile")"
            done
        fi

        # Strip debug symbols
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
# Organize artifacts - Windows
# ─────────────────────────────────────────────
organize_output_windows() {
    log_info "Organizing build artifacts (Windows)"

    rm -rf "${INSTALL_DIR}/windows-x86_64"
    mkdir -p "${INSTALL_DIR}/windows-x86_64"

    local build_dir="${BUILD_ROOT}/x86_64"

    # Copy gamepadjni.dll
    local dll_found=0
    if [ -f "${build_dir}/gamepadjni.dll" ]; then
        cp "${build_dir}/gamepadjni.dll" "${INSTALL_DIR}/windows-x86_64/"
        echo "  Copied gamepadjni.dll"
        dll_found=1
    fi
    if [ "${dll_found}" = "0" ]; then
        find "${BUILD_ROOT}" -name "gamepadjni.dll" -type f 2>/dev/null | while read -r dll; do
            cp "${dll}" "${INSTALL_DIR}/windows-x86_64/"
            echo "  Found and copied: ${dll}"
            dll_found=1
        done
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

    echo "Artifacts:"
    find "${INSTALL_DIR}" -type f \( -name "*.dylib" -o -name "*.so" -o -name "*.dll" \) | sort | while read f; do
        echo "  $(ls -lh "$f" | awk '{print $5, $NF}')"
    done
    echo ""

    # Check shared library dependencies (platform-specific)
    case "$OS_NAME" in
        Darwin)
            for dylib in "${INSTALL_DIR}"/darwin-*/libgamepadjni.dylib; do
                if [ -f "${dylib}" ]; then
                    echo "Dependencies of $(basename "$(dirname "$dylib")")/libgamepadjni.dylib:"
                    otool -L "${dylib}" | grep -v "libgamepadjni" | sed 's/^/  /'
                    break
                fi
            done
            ;;
        Linux)
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
            ;;
        MINGW*|MSYS*)
            if command -v objdump &>/dev/null && [ -f "${INSTALL_DIR}/windows-x86_64/gamepadjni.dll" ]; then
                echo "DLL dependencies:"
                objdump -p "${INSTALL_DIR}/windows-x86_64/gamepadjni.dll" 2>/dev/null | grep "DLL Name" | sed 's/^/  /' || true
            fi
            ;;
    esac
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
EOF

    case "$OS_NAME" in
        Darwin)
            cat <<EOF
      ├── darwin-aarch64/
      │   ├── libSDL3.0.dylib
      │   └── libgamepadjni.dylib
      └── darwin-x86_64/
          ├── libSDL3.0.dylib
          └── libgamepadjni.dylib
EOF
            ;;
        Linux)
            cat <<EOF
      ├── linux-x86_64/
      │   ├── libSDL3.so
      │   └── libgamepadjni.so
      └── linux-aarch64/
          ├── libSDL3.so
          └── libgamepadjni.so
EOF
            ;;
        MINGW*|MSYS*)
            cat <<EOF
      └── windows-x86_64/
          ├── SDL3.dll
          └── gamepadjni.dll
EOF
            ;;
    esac

    cat <<EOF

Build artifacts:
  ${INSTALL_DIR}/

Next steps:
  1. Run build-sdl3.sh to build SDL3 (if not already done)
  2. Run gradle jar to package JAR
EOF
}

# ─────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────
main() {
    log_info "gamepad-jni native build"
    echo "Platform:      ${OS_NAME}"
    echo "Build targets: ${ARCHS[*]}"
    echo "Project root:  ${GAMEPAD_JNI_ROOT}"
    echo "Build dir:     ${BUILD_ROOT}"
    echo "Install dir:   ${INSTALL_DIR}"

    detect_java_home
    check_prerequisites

    for arch in "${ARCHS[@]}"; do
        build_arch "$arch"
    done

    case "$OS_NAME" in
        Darwin)         organize_output_macos ;;
        Linux)          organize_output_linux ;;
        MINGW*|MSYS*)   organize_output_windows ;;
    esac

    verify_output
    print_usage

    log_info "All builds complete!"
}

main "$@"

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
#     prebuilt/jni/windows-x86_64/gamepadjni.dll       - Windows x64 (native: MinGW + CMake)
#     prebuilt/jni/windows-aarch64/gamepadjni.dll      - Windows ARM64 (cross-compiled)
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
# Windows ARM64 cross-compile (from an x86_64 Windows host):
#   CMake cannot drive this target — the host MSYS2/MinGW toolchain is x86_64-only
#   and ships no aarch64 sysroot. A clang/lld cross toolchain is used instead;
#   either of these, whichever is found on PATH:
#     zig          https://ziglang.org            (zig cc -target aarch64-windows-gnu)
#     llvm-mingw   https://github.com/mstorsjo/llvm-mingw   (aarch64-w64-mingw32-clang)
#   JNI headers come from JAVA_HOME, or from JNI_HEADERS_DIR when the host has no
#   Windows JDK (the win32 JNI headers are architecture neutral):
#
#     JNI_HEADERS_DIR=/d/toolchains/jni-headers ./build-jni.sh --arch aarch64
#
#   WINDOWS_AARCH64_CC overrides the compiler command, e.g.
#     WINDOWS_AARCH64_CC="aarch64-w64-mingw32-clang" ./build-jni.sh --arch aarch64
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
# Windows ARM64 cross toolchain
# ─────────────────────────────────────────────
# Filled by detect_windows_aarch64_cc(); empty means "not available".
AARCH64_CC_CMD=()

# Resolve the cross compiler for windows-aarch64. clang + lld is what makes this
# viable: lld resolves SDL3 straight from the DLL's export table, so
# prebuilt/sdl/windows-aarch64/ needs no import library (the x86_64 .dll.a was
# deleted in 0e24ee3 for the same reason).
detect_windows_aarch64_cc() {
    AARCH64_CC_CMD=()

    if [ -n "${WINDOWS_AARCH64_CC:-}" ]; then
        # Deliberate word splitting: the override may carry several words.
        # shellcheck disable=SC2206
        AARCH64_CC_CMD=(${WINDOWS_AARCH64_CC})
        echo "  cross compiler: ${AARCH64_CC_CMD[*]} (from WINDOWS_AARCH64_CC)"
        return 0
    fi

    if command -v aarch64-w64-mingw32-clang &>/dev/null; then
        AARCH64_CC_CMD=(aarch64-w64-mingw32-clang)
        echo "  cross compiler: aarch64-w64-mingw32-clang (llvm-mingw)"
        return 0
    fi

    if command -v zig &>/dev/null; then
        AARCH64_CC_CMD=(zig cc -target aarch64-windows-gnu)
        echo "  cross compiler: zig cc -target aarch64-windows-gnu (zig $(zig version 2>/dev/null || echo '?'))"
        return 0
    fi

    return 1
}

# ─────────────────────────────────────────────
# Determine architectures to build
# ─────────────────────────────────────────────
if [ "$BUILD_ALL" = true ]; then
    case "$OS_NAME" in
        Darwin)         ARCHS=("arm64" "x86_64") ;;
        Linux)          ARCHS=("x86_64" "aarch64") ;;
        MINGW*|MSYS*)
            # aarch64 is cross-compiled and needs a toolchain the host may not
            # have; --all builds what it can instead of failing outright.
            ARCHS=("x86_64")
            if detect_windows_aarch64_cc &>/dev/null; then
                ARCHS+=("aarch64")
            else
                echo "Note: skipping windows-aarch64 — no aarch64 cross toolchain found"
                echo "      install zig or llvm-mingw, or set WINDOWS_AARCH64_CC"
            fi
            ;;
    esac
elif [ -n "$TARGET_ARCH" ]; then
    if [[ "$OS_NAME" == MINGW* || "$OS_NAME" == MSYS* ]]; then
        if [ "$TARGET_ARCH" = "aarch64" ]; then
            if ! detect_windows_aarch64_cc; then
                echo "Error: no Windows ARM64 cross toolchain found"
                echo "  Install one of:"
                echo "    zig         https://ziglang.org/download/"
                echo "    llvm-mingw  https://github.com/mstorsjo/llvm-mingw"
                echo "  Or set WINDOWS_AARCH64_CC to your compiler command."
                exit 1
            fi
        elif [ "$TARGET_ARCH" != "x86_64" ]; then
            echo "Error: Windows supports --arch x86_64 (native) and --arch aarch64 (cross)"
            exit 1
        fi
    fi
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
        # The windows-aarch64 cross build needs only the (architecture neutral) JNI
        # headers, so a headers-only directory is enough when no JDK is installed.
        if [ -n "${JNI_HEADERS_DIR:-}" ]; then
            echo "JAVA_HOME not set; JNI headers come from JNI_HEADERS_DIR=${JNI_HEADERS_DIR}"
            return
        fi

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
# True when at least one requested architecture is built with CMake + the host
# compiler. A windows-aarch64-only run needs neither, so the checks below are
# skipped for it.
needs_host_toolchain() {
    local arch
    for arch in "${ARCHS[@]}"; do
        if [[ "$OS_NAME" == MINGW* ]] || [[ "$OS_NAME" == MSYS* ]]; then
            [ "$arch" = "aarch64" ] || return 0
        else
            return 0
        fi
    done
    return 1
}

check_prerequisites() {
    log_info "Checking build environment (${OS_NAME})"

    if ! needs_host_toolchain; then
        echo "Cross-compile only (windows-aarch64): cmake/gcc on the host are not used"
        return
    fi

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
# Windows ARM64 cross-compile
# ─────────────────────────────────────────────
build_windows_aarch64() {
    local build_dir="$1"
    local sdl3_dll="${GAMEPAD_JNI_ROOT}/prebuilt/sdl/windows-aarch64/SDL3.dll"

    if [ ! -f "${sdl3_dll}" ]; then
        echo "Error: ${sdl3_dll} not found (bundled SDL3 for Windows ARM64)"
        exit 1
    fi

    # JNI headers come from JAVA_HOME (any JDK — the win32 JNI headers are
    # architecture neutral), or from JNI_HEADERS_DIR laid out as
    # <dir>/jni.h + <dir>/win32/jni_md.h.
    local jni_inc jni_inc2
    if [ -n "${JAVA_HOME:-}" ]; then
        jni_inc="${JAVA_HOME}/include"
        jni_inc2="${JAVA_HOME}/include/win32"
    else
        jni_inc="${JNI_HEADERS_DIR}"
        jni_inc2="${JNI_HEADERS_DIR}/win32"
    fi
    [ -f "${jni_inc}/jni.h" ] || { echo "Error: ${jni_inc}/jni.h not found"; exit 1; }
    [ -f "${jni_inc2}/jni_md.h" ] || { echo "Error: ${jni_inc2}/jni_md.h not found"; exit 1; }
    echo "  JNI headers: ${jni_inc}"

    # The SDL3 DLL is passed as a link input on purpose: lld reads its export table
    # directly, so no .dll.a/.lib import library is needed (the x86_64 one was
    # deleted in 0e24ee3 for the same reason). -Wl,-s strips at link time — the
    # host MinGW strip is an x86_64-only binutils build and cannot read ARM64.
    echo "  SDL3 (ARM64): ${sdl3_dll}"
    "${AARCH64_CC_CMD[@]}" -shared -O2 -Wl,-s \
        -o "${build_dir}/gamepadjni.dll" \
        "${GAMEPAD_JNI_ROOT}/src/main/c/gamepad_jni.c" \
        -I "${GAMEPAD_JNI_ROOT}/prebuilt/sdl/include" \
        -I "${jni_inc}" \
        -I "${jni_inc2}" \
        "${sdl3_dll}"

    if [ ! -f "${build_dir}/gamepadjni.dll" ]; then
        echo "Error: cross-compile produced no DLL"
        exit 1
    fi

    # Assert the image really is ARM64 (PE machine 0xaa64). Nothing downstream
    # checks machine type — verifyNativeSymbols only scans for exported symbol names
    # — so a silently x86_64 build would ship and fail on the one platform it is
    # meant for. e_lfanew lives at 0x3C; the machine field is 4 bytes into the PE
    # signature that follows it.
    local e_lfanew machine
    e_lfanew=$(od -An -tu4 -j60 -N4 "${build_dir}/gamepadjni.dll" | tr -d ' \n')
    machine=$(od -An -tx2 -j$((e_lfanew + 4)) -N2 "${build_dir}/gamepadjni.dll" | tr -d ' \n')
    if [ "$machine" != "aa64" ]; then
        echo "Error: built image is not ARM64 (PE machine=0x${machine}, expected 0xaa64)"
        exit 1
    fi
    echo "  Verified PE machine: ARM64 (0xaa64)"
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

    # Windows ARM64 never goes through CMake: the host MinGW toolchain is
    # x86_64-only and has no aarch64 sysroot, so the single translation unit is
    # handed straight to the clang/lld cross compiler.
    if [[ "$OS_NAME" == MINGW* || "$OS_NAME" == MSYS* ]] && [ "$arch" = "aarch64" ]; then
        build_windows_aarch64 "${build_dir}"
        echo "${arch} build complete"
        return
    fi

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

    local arch install_arch_dir build_dir
    for arch in "${ARCHS[@]}"; do
        install_arch_dir="${INSTALL_DIR}/windows-${arch}"
        build_dir="${BUILD_ROOT}/${arch}"

        rm -rf "${install_arch_dir}"
        mkdir -p "${install_arch_dir}"

        if [ -f "${build_dir}/gamepadjni.dll" ]; then
            cp "${build_dir}/gamepadjni.dll" "${install_arch_dir}/"
            echo "  Copied ${arch}: gamepadjni.dll"
        else
            # Only the DLL is copied out of the build tree: the cross toolchain also
            # drops an import library (gamepad_jni.lib) next to it, which must not
            # reach prebuilt/jni/.
            find "${build_dir}" -name "gamepadjni.dll" -type f 2>/dev/null | while read -r dll; do
                cp "${dll}" "${install_arch_dir}/"
                echo "  Found and copied: ${dll}"
            done
        fi

        # Strip x86_64 (MinGW embeds DWARF debug info). The ARM64 artifact is
        # already stripped at link time with -Wl,-s; the host strip is an x86_64-only
        # binutils build and cannot process an ARM64 image.
        if [ "$arch" != "aarch64" ] && command -v strip &>/dev/null && [ -f "${install_arch_dir}/gamepadjni.dll" ]; then
            local before_size after_size
            before_size=$(wc -c < "${install_arch_dir}/gamepadjni.dll")
            strip --strip-all "${install_arch_dir}/gamepadjni.dll"
            after_size=$(wc -c < "${install_arch_dir}/gamepadjni.dll")
            echo "  Stripped gamepadjni.dll: ${before_size} -> ${after_size} bytes"
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
            # State the architecture of every Windows artifact: the ARM64 one is
            # cross-compiled, so "what machine is this really" is worth reporting.
            local dll
            for dll in "${INSTALL_DIR}"/windows-*/gamepadjni.dll; do
                [ -f "${dll}" ] || continue
                if command -v file &>/dev/null; then
                    echo "$(basename "$(dirname "${dll}")")/gamepadjni.dll: $(file -b "${dll}")"
                fi
            done
            if command -v objdump &>/dev/null && [ -f "${INSTALL_DIR}/windows-x86_64/gamepadjni.dll" ]; then
                echo "windows-x86_64 DLL dependencies:"
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
      ├── windows-aarch64/
      │   ├── SDL3.dll
      │   └── gamepadjni.dll
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

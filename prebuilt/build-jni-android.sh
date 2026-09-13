#!/bin/bash
#
# Build libgamepadjni.so for Android ABIs (arm64-v8a / armeabi-v7a / x86_64).
#
# SDL3 itself is NOT built here and NOT shipped: on Android the launcher APK
# provides libSDL3.so (its ART side also installs the org.libsdl.app.* Java glue).
# We only need an Android libSDL3.so at LINK time, taken from the official SDL3
# Android release artifact.
#
# Build artifacts:
#   prebuilt/jni/android-arm64-v8a/libgamepadjni.so
#   prebuilt/jni/android-armeabi-v7a/libgamepadjni.so
#   prebuilt/jni/android-x86_64/libgamepadjni.so
#   prebuilt/link-only/android-<abi>/libSDL3.so   (link only, never packaged)
#
# Prerequisites: Android NDK (r26+), cmake, curl, python3.
#
# Usage:
#   ./build-jni-android.sh                       # finds the NDK automatically
#   ./build-jni-android.sh --api 21              # override min API level

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
GAMEPAD_JNI_ROOT="${SCRIPT_DIR}/.."
BUILD_ROOT="${SCRIPT_DIR}/jni/build-android"
LINK_ONLY="${SCRIPT_DIR}/link-only"
INSTALL_DIR="${SCRIPT_DIR}/jni"

# SDL3 version used only as the Android link target. Must stay aligned with what
# the target launchers ship: Amethyst 1.1.7 / FoldCraftLauncher 1.3.3.1 /
# Zalith Launcher 2 all carry the identical SDL-release.aar (SDL 3.4.12).
SDL3_VERSION="3.4.12"

ABIS=(arm64-v8a armeabi-v7a x86_64)

API_LEVEL=21
while [[ $# -gt 0 ]]; do
    case "$1" in
        --api) API_LEVEL="$2"; shift 2 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

log_info() { echo "[build-jni-android] $*"; }

# ── Locate the NDK ───────────────────────────────────────────
if [[ -z "${ANDROID_NDK_ROOT:-}" ]]; then
    for base in "${ANDROID_HOME:-}/ndk" "${ANDROID_SDK_ROOT:-}/ndk" "$HOME/Library/Android/sdk/ndk"; do
        if [[ -n "$base" && -d "$base" ]]; then
            ANDROID_NDK_ROOT="$base/$(ls "$base" | sort -V | tail -1)"
            break
        fi
    done
fi
if [[ -z "${ANDROID_NDK_ROOT:-}" || ! -d "${ANDROID_NDK_ROOT}" ]]; then
    echo "Error: Android NDK not found."
    echo "  Set ANDROID_NDK_ROOT, or install the NDK via Android Studio's SDK Manager."
    echo "  Expected layout: <sdk>/ndk/<version>/build/cmake/android.toolchain.cmake"
    exit 1
fi
log_info "NDK: ${ANDROID_NDK_ROOT}"
log_info "API level: ${API_LEVEL}"

TOOLCHAIN="${ANDROID_NDK_ROOT}/build/cmake/android.toolchain.cmake"
if [[ ! -f "${TOOLCHAIN}" ]]; then
    echo "Error: ${TOOLCHAIN} not found."
    exit 1
fi

# The NDK's llvm-strip is the only reliable stripper for these ABIs.
STRIP="$(ls "${ANDROID_NDK_ROOT}"/toolchains/llvm/prebuilt/*/bin/llvm-strip 2>/dev/null | head -1 || true)"

# ── Fetch the link-time SDL3 (official Android package, prefab layout) ──
fetch_link_sdl3() {
    local zip="${LINK_ONLY}/SDL3-devel-${SDL3_VERSION}-android.zip"
    mkdir -p "${LINK_ONLY}"
    if [[ ! -f "$zip" ]]; then
        local url="https://github.com/libsdl-org/SDL/releases/download/release-${SDL3_VERSION}/SDL3-devel-${SDL3_VERSION}-android.zip"
        log_info "Downloading ${url}"
        curl -fSL --retry 3 -o "$zip" "$url"
    else
        log_info "Using cached $(basename "$zip")"
    fi
    python3 - "$zip" "$LINK_ONLY" "$SDL3_VERSION" "${ABIS[@]}" <<'PYEOF'
import io, os, shutil, sys, zipfile

zip_path, out_root, ver = sys.argv[1], sys.argv[2], sys.argv[3]
abis = sys.argv[4:]
with zipfile.ZipFile(zip_path) as z:
    aar_name = f"SDL3-{ver}.aar"
    with z.open(aar_name) as fh:
        aar = zipfile.ZipFile(io.BytesIO(fh.read()))
        for abi in abis:
            member = f"prefab/modules/SDL3-shared/libs/android.{abi}/libSDL3.so"
            dest_dir = os.path.join(out_root, f"android-{abi}")
            os.makedirs(dest_dir, exist_ok=True)
            dest = os.path.join(dest_dir, "libSDL3.so")
            with aar.open(member) as src, open(dest, "wb") as dst:
                shutil.copyfileobj(src, dst)
            print(f"[build-jni-android] link-only SDL3 -> android-{abi}/libSDL3.so")
PYEOF
}

fetch_link_sdl3

# ── Build each ABI ───────────────────────────────────────────
JOBS="$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)"
for abi in "${ABIS[@]}"; do
    log_info "=== Building ${abi} ==="
    build_dir="${BUILD_ROOT}/${abi}"
    rm -rf "${build_dir}"
    mkdir -p "${build_dir}"
    cmake -S "${GAMEPAD_JNI_ROOT}" -B "${build_dir}" \
        -DCMAKE_TOOLCHAIN_FILE="${TOOLCHAIN}" \
        -DANDROID_ABI="${abi}" \
        -DANDROID_PLATFORM="android-${API_LEVEL}" \
        -DANDROID_NDK_ROOT="${ANDROID_NDK_ROOT}" \
        -DCMAKE_BUILD_TYPE=Release
    cmake --build "${build_dir}" --config Release -j"${JOBS}"

    # Strip like the desktop natives are: ~100 KB -> ~25 KB per ABI, with every
    # JNI entry point kept in .dynsym.
    stripped="${INSTALL_DIR}/android-${abi}/libgamepadjni.so"
    if [[ -x "${STRIP}" ]]; then
        "${STRIP}" --strip-unneeded "${stripped}"
        log_info "stripped ${abi}"
    else
        log_info "WARNING: llvm-strip not found; ${abi} left unstripped"
    fi
done

log_info "Done. Artifacts:"
for abi in "${ABIS[@]}"; do
    ls -la "${INSTALL_DIR}/android-${abi}/libgamepadjni.so"
done

# ── Regression guard: RELRO must fit inside the mapping ──────
#
# bionic rounds the PT_GNU_RELRO range out to the RUNTIME page size (4 KB on most
# devices, including Android 10) before mprotect'ing it. If the rounded range
# reaches past the end of the last PT_LOAD mapping, mprotect fails with ENOMEM and
# dlopen dies with:
#   dlopen failed: can't enable GNU RELRO protection for "...": Out of memory
#
# That is exactly what -Wl,-z,common-page-size=16384 caused: it inflated RELRO's
# p_memsz by one page. max-page-size alone does not, so only that is set in
# CMakeLists.txt. This check keeps a future change from reintroducing it.
#
# Note this checks the 4 KB case only; 16 KB devices are covered by p_align=0x4000,
# which CMAKE's target_link_options still applies.
python3 - "${INSTALL_DIR}" "${ABIS[@]}" <<'PYEOF'
import struct, sys, pathlib

install_dir, abis = sys.argv[1], sys.argv[2:]
PAGE = 0x1000
failed = False

for abi in abis:
    p = pathlib.Path(install_dir) / f"android-{abi}" / "libgamepadjni.so"
    d = p.read_bytes()
    is64 = d[4] == 2
    e = '<' if d[5] == 1 else '>'
    if is64:
        phoff, = struct.unpack_from(e + 'Q', d, 0x20)
        phentsize, phnum = struct.unpack_from(e + 'HH', d, 0x36)
    else:
        phoff, = struct.unpack_from(e + 'I', d, 0x1C)
        phentsize, phnum = struct.unpack_from(e + 'HH', d, 0x2A)

    load_end = 0
    relro_va = relro_msz = None
    aligns = set()
    for i in range(phnum):
        v = struct.unpack_from(e + ('IIQQQQQQ' if is64 else 'IIIIIIII'), d, phoff + i * phentsize)
        if is64:
            p_type, p_va, p_msz, p_align = v[0], v[3], v[6], v[7]
        else:
            p_type, p_va, p_msz, p_align = v[0], v[2], v[5], v[7]
        if p_type == 1:
            load_end = max(load_end, p_va + p_msz)
            aligns.add(p_align)
        elif p_type == 0x6474E552:
            relro_va, relro_msz = p_va, p_msz

    mapped_end = (load_end + PAGE - 1) & ~(PAGE - 1)
    relro_end = (relro_va + relro_msz + PAGE - 1) & ~(PAGE - 1)
    ok = relro_end <= mapped_end
    if not ok:
        failed = True
    print(f"[build-jni-android] {abi}: p_align={sorted(hex(a) for a in aligns)} "
          f"RELRO_end={hex(relro_end)} mapped_end={hex(mapped_end)} "
          f"{'OK' if ok else 'OVERFLOWS THE MAPPING -> dlopen will fail with ENOMEM'}")

if failed:
    sys.exit("[build-jni-android] FATAL: RELRO does not fit the mapping; "
             "check -Wl,-z,common-page-size in CMakeLists.txt")
PYEOF

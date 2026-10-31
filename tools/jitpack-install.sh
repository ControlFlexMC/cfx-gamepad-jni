#!/bin/bash
#
# JitPack install hook (see jitpack.yml).
#
# Native libraries are build output and are not committed, so JitPack cannot
# build the JAR itself. Instead this hook downloads the JAR that the
# build-natives workflow attached to the GitHub release for the ref being built,
# verifies it against the published checksums, and installs it into the local
# Maven repository that JitPack serves from.
#
# JitPack sets VERSION to the ref it is building. Release tags are the only
# supported refs: a commit hash or branch-SNAPSHOT has no release assets, and
# building the natives on JitPack is not possible (single Linux x86_64 host).

set -euo pipefail

: "${VERSION:?VERSION is not set — this script is meant to run inside JitPack}"

VER="${VERSION#v}"
JAR="cfx-gamepad-jni-${VER}.jar"
REPO="https://github.com/ControlFlexMC/cfx-gamepad-jni/releases/download"

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

# JitPack passes the ref as requested: a dependency on 0.9.5 and one on v0.9.5
# can arrive as either spelling, so try both before giving up.
TAG=""
for candidate in "${VERSION}" "v${VER}" "${VER}"; do
    if curl -fsSL -o "${WORK}/${JAR}" "${REPO}/${candidate}/${JAR}" 2>/dev/null; then
        TAG="${candidate}"
        break
    fi
done

if [ -z "${TAG}" ]; then
    echo "No GitHub release found for '${VERSION}' (tried: ${VERSION}, v${VER}, ${VER})." >&2
    if [[ "${VERSION}" =~ ^[0-9a-f]{7,40}$ ]]; then
        echo "Commit builds are not supported: the native libraries only exist as release assets." >&2
        echo "Depend on a release tag (for example 0.9.5) instead of a commit hash or branch-SNAPSHOT." >&2
    fi
    exit 1
fi

echo "── fetched ${JAR} from ${TAG}"
curl -fsSL -o "${WORK}/checksums.txt" "${REPO}/${TAG}/checksums.txt"

EXPECTED="$(awk -v jar="${JAR}" '$2 ~ jar"$" {print $1}' "${WORK}/checksums.txt" | head -1)"
ACTUAL="$(sha256sum "${WORK}/${JAR}" | awk '{print $1}')"

if [ -z "${EXPECTED}" ]; then
    echo "No checksum entry for ${JAR} in checksums.txt — refusing to install an unverified jar." >&2
    exit 1
fi
if [ "${EXPECTED}" != "${ACTUAL}" ]; then
    echo "Checksum mismatch for ${JAR}: expected ${EXPECTED}, got ${ACTUAL}" >&2
    exit 1
fi
echo "── verified ${JAR} (sha256 ${ACTUAL})"

# Install under both spellings so the coordinate resolves whichever form JitPack
# looks up for this build.
for V in "${VER}" "${TAG}"; do
    mvn -q install:install-file \
        -Dfile="${WORK}/${JAR}" \
        -DgroupId=com.github.ControlFlexMC \
        -DartifactId=cfx-gamepad-jni \
        -Dversion="${V}" \
        -Dpackaging=jar
    echo "── installed com.github.ControlFlexMC:cfx-gamepad-jni:${V}"
done

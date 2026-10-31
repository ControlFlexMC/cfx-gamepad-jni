#!/bin/bash
#
# JitPack install hook (see jitpack.yml).
#
# Native libraries are build output and are not committed, so JitPack cannot
# build the JAR itself. Instead this hook downloads the JAR that the
# build-natives workflow attached to the GitHub release for the tag being built,
# verifies it against the published checksums, and installs it into the local
# Maven repository that JitPack serves from.
#
# JitPack sets VERSION to the ref it is building (a tag such as v0.9.5).

set -euo pipefail

: "${VERSION:?VERSION is not set — this script is meant to run inside JitPack}"

TAG="${VERSION}"
VER="${VERSION#v}"
JAR="cfx-gamepad-jni-${VER}.jar"
BASE="https://github.com/ControlFlexMC/cfx-gamepad-jni/releases/download/${TAG}"

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

echo "── fetching ${JAR} from ${TAG}"
curl -fsSL -o "${WORK}/${JAR}" "${BASE}/${JAR}"
curl -fsSL -o "${WORK}/checksums.txt" "${BASE}/checksums.txt"

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

# Install under both the bare version (what the Gradle dependency string uses,
# e.g. 0.9.5) and the tag as written (v0.9.5), so the coordinate resolves
# whichever form JitPack looks up for this build.
for V in "${VER}" "${TAG}"; do
    mvn -q install:install-file \
        -Dfile="${WORK}/${JAR}" \
        -DgroupId=com.github.ControlFlexMC \
        -DartifactId=cfx-gamepad-jni \
        -Dversion="${V}" \
        -Dpackaging=jar
    echo "── installed com.github.ControlFlexMC:cfx-gamepad-jni:${V}"
done

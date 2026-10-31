#!/bin/bash
#
# JitPack install hook (see jitpack.yml).
#
# Native libraries are build output and are not committed, so JitPack cannot
# build the JAR itself. This hook downloads the JAR that the build-natives
# workflow attached to the GitHub release for the ref being built, verifies it
# against the published checksums, and hands it to JitPack two ways:
#
#   1. installed into the local Maven repository (~/.m2/repository), and
#   2. copied into build/libs next to a matching pom.xml,
#
# because JitPack discovers artifacts by scanning both the project directory and
# ~/.m2 — and it needs Maven's normal output (no -q) to see what was installed.
#
# JitPack sets VERSION to the ref it is building. Release tags are the only
# supported refs: a commit hash or branch-SNAPSHOT has no release assets, and
# building the natives on JitPack is not possible (single Linux x86_64 host).

set -euo pipefail

: "${VERSION:?VERSION is not set — this script is meant to run inside JitPack}"

VER="${VERSION#v}"
ARTIFACT="cfx-gamepad-jni"
GROUP="com.github.ControlFlexMC"
JAR="${ARTIFACT}-${VER}.jar"
REPO="https://github.com/ControlFlexMC/cfx-gamepad-jni/releases/download"

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

# JitPack passes the ref as requested: a dependency on 0.9.5.2 and one on
# v0.9.5.2 can arrive as either spelling, so try both before giving up.
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
        echo "Depend on a release tag (for example ${VER}) instead of a commit hash or branch-SNAPSHOT." >&2
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

write_pom() {
    cat > "$1" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>${GROUP}</groupId>
  <artifactId>${ARTIFACT}</artifactId>
  <version>$2</version>
  <packaging>jar</packaging>
  <name>cfx-gamepad-jni</name>
  <description>Java JNI wrapper around the SDL3 Gamepad API, built by GitHub Actions.</description>
</project>
POM
}

# Publish under the project directory as well: JitPack looks for pom.xml both
# here and in ~/.m2.
mkdir -p build/libs
cp "${WORK}/${JAR}" "build/libs/${JAR}"
write_pom "build/libs/${ARTIFACT}-${VER}.pom" "${VER}"

# Install both spellings so the coordinate resolves whichever form JitPack looks
# up. -B and no -q: JitPack reads the "[INFO] Installing ..." lines.
for V in "${VER}" "${TAG}"; do
    write_pom "${WORK}/pom-${V}.xml" "${V}"
    mvn -B -Dstyle.color=never install:install-file \
        -Dfile="${WORK}/${JAR}" \
        -DpomFile="${WORK}/pom-${V}.xml"
    echo "── installed ${GROUP}:${ARTIFACT}:${V}"
done

#!/bin/bash
#
# Local dev build: package cfx-gamepad-jni JAR with -SNAPSHOT suffix
#
# Prerequisite: JNI native libraries have been placed under prebuilt/jni/
#
# The base version is read from gradle.properties.
# Local builds automatically append -SNAPSHOT to distinguish from releases.
#
# Usage:
#   chmod +x build-snapshot.sh
#   ./build-snapshot.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${SCRIPT_DIR}"

PROPS_FILE="gradle.properties"

# Read base version from gradle.properties
VERSION=$(grep -E '^version=' "${PROPS_FILE}" | cut -d'=' -f2 | xargs)
if [ -z "${VERSION}" ]; then
    echo "❌ Error: 'version=' not found in ${PROPS_FILE}"
    exit 1
fi

# Local dev builds always use -SNAPSHOT
SNAPSHOT_VERSION="${VERSION}-SNAPSHOT"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Packaging cfx-gamepad-jni JAR (local dev build)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  Base version:     ${VERSION}   (from ${PROPS_FILE})"
echo "  Build version:    ${SNAPSHOT_VERSION}"
echo ""

# Check Gradle
if ! command -v gradle &>/dev/null; then
    echo "❌ Error: gradle not found"
    exit 1
fi

# Build JAR and publish to Maven Local (override version with -SNAPSHOT suffix)
gradle clean jar publishToMavenLocal -Pversion="${SNAPSHOT_VERSION}"

# Show result
JAR_FILE=$(ls build/libs/cfx-gamepad-jni-*.jar 2>/dev/null | head -1)
if [ -n "${JAR_FILE}" ] && [ -f "${JAR_FILE}" ]; then
    echo ""
    echo "✅ JAR packaging complete: ${JAR_FILE}"
    echo "   Size: $(ls -lh "${JAR_FILE}" | awk '{print $5}')"
    echo ""
    echo "📦 Native libraries in JAR:"
    jar tf "${JAR_FILE}" | grep -E "^native/" | grep -v "/$" | sort
else
    echo "❌ JAR file not found"
    exit 1
fi

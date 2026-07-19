#!/bin/bash
#
# Package gamepad-jni JAR (without compiling JNI native libraries)
#
# Prerequisite: JNI native libraries have been compiled via prebuilt/build_native.sh
#               and placed under prebuilt/jni/
#
# Usage:
#   chmod +x build_jar.sh
#   ./build_jar.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${SCRIPT_DIR}"

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Packaging gamepad-jni JAR"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Check Gradle
if ! command -v gradle &>/dev/null; then
    echo "❌ Error: gradle not found"
    exit 1
fi

# Clean and package
gradle clean jar

# Show result
JAR_FILE=$(ls build/libs/gamepad-jni-*.jar 2>/dev/null | head -1)
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

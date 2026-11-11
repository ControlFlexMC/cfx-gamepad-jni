#!/usr/bin/env bash
#
# Publish cfx-gamepad-jni to ~/.m2 at the JitPack consumer coordinate:
#   com.github.ControlFlexMC:cfx-gamepad-jni:<version>
#
# The version is read from 'version=' in gradle.properties, so the local
# artifact always matches what JitPack will serve for the same release.
#
# Usage:
#   ./tools/publish_maven_local.sh
#   ./tools/publish_maven_local.sh -x verifyNativeSymbols   # see the failure note below
#
set -euo pipefail

TOOLS_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${TOOLS_DIR}/.." && pwd)"
PROPS_FILE="${PROJECT_DIR}/gradle.properties"

GROUP_ID="com.github.ControlFlexMC"
ARTIFACT_ID="cfx-gamepad-jni"

# Prefer the wrapper: it pins the Gradle version this project is built with, which
# is what CI and publish-release.sh use. GRADLE_BIN overrides it for the odd host
# where the wrapper cannot run; a bare `gradle` is the last resort.
GRADLE_CMD=()
if [[ -x "${PROJECT_DIR}/gradlew" ]]; then
    GRADLE_CMD=("${PROJECT_DIR}/gradlew")
elif [[ -n "${GRADLE_BIN:-}" ]] && command -v "${GRADLE_BIN}" >/dev/null 2>&1; then
    GRADLE_CMD=("${GRADLE_BIN}")
elif command -v gradle >/dev/null 2>&1; then
    GRADLE_CMD=(gradle)
fi

log_step() {
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "  [$1] $2"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
}

log_info() {
    echo "  $1"
}

fail() {
    echo "❌ $1" >&2
    exit 1
}

read_prop() {
    grep "^${1}=" "${PROPS_FILE}" | cut -d'=' -f2- | tr -d ' '
}

detect_java_home() {
    if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
        return 0
    fi
    local candidate
    for candidate in \
        /Library/Java/JavaVirtualMachines/microsoft-17.jdk/Contents/Home \
        /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
        /Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home \
        /Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
    do
        if [[ -x "${candidate}/bin/java" ]]; then
            export JAVA_HOME="${candidate}"
            export PATH="${JAVA_HOME}/bin:${PATH}"
            return 0
        fi
    done
}

[[ -f "${PROPS_FILE}" ]] || fail "gradle.properties not found: ${PROPS_FILE}"
[[ ${#GRADLE_CMD[@]} -gt 0 ]] || fail "no Gradle available: no ./gradlew, no GRADLE_BIN, no gradle in PATH"

VERSION="$(read_prop version)"
[[ -n "${VERSION}" ]] || fail "version is empty in gradle.properties"

M2_DIR="${HOME}/.m2/repository/com/github/ControlFlexMC/${ARTIFACT_ID}/${VERSION}"
POM="${M2_DIR}/${ARTIFACT_ID}-${VERSION}.pom"
JAR="${M2_DIR}/${ARTIFACT_ID}-${VERSION}.jar"

log_step "1/3" "Read version"
log_info "version:     ${VERSION}"
log_info "coordinate:  ${GROUP_ID}:${ARTIFACT_ID}:${VERSION}"

detect_java_home
if [[ -n "${JAVA_HOME:-}" ]]; then
    log_info "JAVA_HOME:   ${JAVA_HOME}"
fi

# publishToMavenLocal depends on jar, and jar depends on verifyNativeSymbols —
# so a native library that drifted from JniGamepad.java fails the publish here
# rather than at runtime in a consumer.
log_step "2/3" "publishToMavenLocal"
cd "${PROJECT_DIR}"
if ! "${GRADLE_CMD[@]}" publishToMavenLocal "$@"; then
    echo ""
    echo "❌ publishToMavenLocal failed." >&2
    echo "   If it stopped at verifyNativeSymbols, the packaged natives still export an" >&2
    echo "   older Java API than JniGamepad.java declares. Rebuild them first:" >&2
    echo "     cd prebuilt && ./build-jni.sh --all     # this host's platforms only" >&2
    echo "     build natives workflow                  # the full 9-platform matrix" >&2
    echo "   A jar built with stale natives is not shippable. For a local-only build:" >&2
    echo "     ./tools/publish_maven_local.sh -x verifyNativeSymbols" >&2
    exit 1
fi

log_step "3/3" "Verify local artifact"
[[ -f "${JAR}" ]] || fail "JAR not found: ${JAR}"
[[ -f "${POM}" ]] || fail "POM not found: ${POM}"

if grep -q "com.github.ControlFlexMC.${ARTIFACT_ID}" "${POM}"; then
    fail "POM looks like a JitPack wrapper (multi-artifact). Check publishing {} in build.gradle."
fi

JAR_SIZE="$(du -h "${JAR}" | cut -f1 | tr -d ' ')"

echo "  Natives bundled in JAR:"
jar tf "${JAR}" | grep -E "^native/" | grep -v "/$" | sort | sed 's/^/    /'

echo ""
echo "╔════════════════════════════════════════════════════════╗"
echo "║  ✅ Maven Local publish succeeded                      ║"
echo "╠════════════════════════════════════════════════════════╣"
echo "║  ${GROUP_ID}:${ARTIFACT_ID}:${VERSION}"
echo "║  ${JAR}  (${JAR_SIZE})"
echo "╚════════════════════════════════════════════════════════╝"
echo ""
echo "Consumer:"
echo "  compileOnly '${GROUP_ID}:${ARTIFACT_ID}:${VERSION}'"
echo ""

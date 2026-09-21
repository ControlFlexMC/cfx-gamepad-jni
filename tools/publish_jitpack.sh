#!/usr/bin/env bash
#
# Publish cfx-gamepad-jni to JitPack by tagging and pushing to GitHub.
# JitPack builds from the git tag; the consumer coordinate becomes:
#   com.github.ControlFlexMC:cfx-gamepad-jni:<tag>
#
# The version is read from 'version=' in gradle.properties and tagged as
# v<version>, matching the tags already in this repo (v0.8.5 … v0.9.0) and the
# scheme used by publish-release.sh. Because JitPack takes the version from the
# tag name, consumers resolve the v-prefixed coordinate:
#   com.github.ControlFlexMC:cfx-gamepad-jni:v<version>
#
# NOTE: tag creation overlaps with publish-release.sh, which also tags and
# pushes (and additionally uploads the JAR to a GitHub Release). Both refuse to
# run when the tag already exists, so pick one path per release — this script
# stops at "JitPack serves the POM", publish-release.sh stops at "release asset
# uploaded".
#
# Usage:
#   ./tools/publish_jitpack.sh              # tag + push + GitHub release + wait for JitPack
#   ./tools/publish_jitpack.sh --no-release # skip `gh release create`
#   ./tools/publish_jitpack.sh --rebuild    # do not retag; only trigger / wait for JitPack
#   ./tools/publish_jitpack.sh --no-wait    # do not poll JitPack (still tag/push)
#
set -euo pipefail

TOOLS_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "${TOOLS_DIR}/.." && pwd)"
PROPS_FILE="${PROJECT_DIR}/gradle.properties"

GROUP_ID="com.github.ControlFlexMC"
ARTIFACT_ID="cfx-gamepad-jni"
GIT_REMOTE="origin"
JITPACK_OWNER="ControlFlexMC"
JITPACK_REPO="cfx-gamepad-jni"

# The Gradle wrapper is not usable in this repo (gradle/wrapper/gradle-wrapper.jar
# was never committed), so invoke the system Gradle the same way the other
# top-level scripts (publish-release.sh, build-snapshot.sh) do.
GRADLE_BIN="${GRADLE_BIN:-gradle}"

usage() {
    sed -n '2,23p' "$0"
}

SKIP_RELEASE=false
REBUILD_ONLY=false
NO_WAIT=false
for arg in "$@"; do
    case "${arg}" in
        --no-release) SKIP_RELEASE=true ;;
        --rebuild)    REBUILD_ONLY=true ;;
        --no-wait)    NO_WAIT=true ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "❌ Unknown argument: ${arg}"
            echo "Usage: $0 [--no-release] [--rebuild] [--no-wait]"
            exit 1
            ;;
    esac
done

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

log_warning() {
    echo "  ⚠️  $1"
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

require_clean_worktree() {
    if [[ -n "$(git status --porcelain)" ]]; then
        fail "Working tree is dirty. Commit (or stash) before publishing to JitPack."
    fi
}

wait_for_jitpack() {
    local tag="$1"
    local pom_url="https://jitpack.io/com/github/${JITPACK_OWNER}/${JITPACK_REPO}/${tag}/${ARTIFACT_ID}-${tag}.pom"
    local log_url="https://jitpack.io/com/github/${JITPACK_OWNER}/${JITPACK_REPO}/${tag}/build.log"
    local tmp_pom
    tmp_pom="$(mktemp)"
    local tmp_log
    tmp_log="$(mktemp)"
    local elapsed=0
    local timeout=360
    local interval=10

    log_info "POM: ${pom_url}"
    log_info "log: ${log_url}"

    # First request kicks off the JitPack build.
    curl -fsSL "${log_url}" -o "${tmp_log}" || true

    while (( elapsed <= timeout )); do
        if curl -fsSL "${pom_url}" -o "${tmp_pom}" 2>/dev/null; then
            if grep -q "com.github.${JITPACK_OWNER}.${ARTIFACT_ID}" "${tmp_pom}"; then
                rm -f "${tmp_pom}" "${tmp_log}"
                fail "JitPack POM is a multi-artifact wrapper. Keep a single maven-publish publication."
            fi
            if ! grep -q "<artifactId>${ARTIFACT_ID}</artifactId>" "${tmp_pom}"; then
                rm -f "${tmp_pom}" "${tmp_log}"
                fail "JitPack POM artifactId is not ${ARTIFACT_ID}."
            fi
            echo ""
            echo "  JitPack POM:"
            sed 's/^/    /' "${tmp_pom}"
            rm -f "${tmp_pom}" "${tmp_log}"
            return 0
        fi
        log_info "waiting for JitPack… ${elapsed}s / ${timeout}s"
        sleep "${interval}"
        elapsed=$((elapsed + interval))
        curl -fsSL "${log_url}" -o "${tmp_log}" 2>/dev/null || true
        if grep -q "Build failed\|BUILD FAILED" "${tmp_log}" 2>/dev/null; then
            echo ""
            echo "----- JitPack build.log (tail) -----"
            tail -n 40 "${tmp_log}"
            rm -f "${tmp_pom}" "${tmp_log}"
            fail "JitPack build failed. See ${log_url}"
        fi
    done

    rm -f "${tmp_pom}" "${tmp_log}"
    fail "Timed out waiting for JitPack (${timeout}s). Check ${log_url}"
}

[[ -f "${PROPS_FILE}" ]] || fail "gradle.properties not found: ${PROPS_FILE}"

VERSION="$(read_prop version)"
[[ -n "${VERSION}" ]] || fail "version is empty in gradle.properties"

TAG="v${VERSION}"

cd "${PROJECT_DIR}"

log_step "1/4" "Read version"
log_info "version:     ${VERSION}"
log_info "tag:         ${TAG}"
log_info "coordinate:  ${GROUP_ID}:${ARTIFACT_ID}:${TAG}"

if [[ "${REBUILD_ONLY}" == "true" ]]; then
    log_step "2/4" "Skip tag/push (--rebuild)"
    if ! git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
        fail "Tag ${TAG} does not exist locally. Run without --rebuild first."
    fi
    log_info "existing tag: $(git rev-parse --short "refs/tags/${TAG}")"
else
    require_clean_worktree
    command -v "${GRADLE_BIN}" >/dev/null 2>&1 || fail "${GRADLE_BIN} not found in PATH"

    log_step "2/4" "Verify build, then tag and push"
    detect_java_home
    if [[ -n "${JAVA_HOME:-}" ]]; then
        log_info "JAVA_HOME: ${JAVA_HOME}"
    fi
    # `build` runs check -> verifyNativeSymbols, so stale per-platform JNI
    # binaries fail here instead of shipping a release that throws
    # UnsatisfiedLinkError in a consumer.
    "${GRADLE_BIN}" build

    if git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
        fail "Git tag ${TAG} already exists. Use --rebuild to only refresh JitPack."
    fi
    if git ls-remote --tags "${GIT_REMOTE}" "refs/tags/${TAG}" | grep -q .; then
        fail "Git tag ${TAG} already exists on ${GIT_REMOTE}."
    fi

    git tag -a "${TAG}" -m "cfx-gamepad-jni ${VERSION}"
    log_info "created annotated tag ${TAG}"

    git push "${GIT_REMOTE}" HEAD
    git push "${GIT_REMOTE}" "${TAG}"
    log_info "pushed HEAD and tag ${TAG} to ${GIT_REMOTE}"

    if [[ "${SKIP_RELEASE}" == "true" ]]; then
        log_info "skipping GitHub release (--no-release)"
    elif command -v gh >/dev/null 2>&1; then
        if gh release view "${TAG}" >/dev/null 2>&1; then
            log_warning "GitHub release ${TAG} already exists"
        else
            gh release create "${TAG}" \
                --title "cfx-gamepad-jni ${TAG}" \
                --notes "cfx-gamepad-jni ${VERSION}"
            log_info "created GitHub release ${TAG}"
        fi
    else
        log_warning "gh not found; skipped GitHub release. Tag push is enough for JitPack."
    fi
fi

log_step "3/4" "Trigger JitPack"
if [[ "${NO_WAIT}" == "true" ]]; then
    log_info "skipping wait (--no-wait)"
    log_info "https://jitpack.io/#${JITPACK_OWNER}/${JITPACK_REPO}/${TAG}"
else
    wait_for_jitpack "${TAG}"
fi

log_step "4/4" "Done"

echo ""
echo "╔════════════════════════════════════════════════════════╗"
echo "║  ✅ JitPack publish triggered                          ║"
echo "╠════════════════════════════════════════════════════════╣"
echo "║  ${GROUP_ID}:${ARTIFACT_ID}:${TAG}"
echo "║  https://jitpack.io/#${JITPACK_OWNER}/${JITPACK_REPO}/${TAG}"
echo "╚════════════════════════════════════════════════════════╝"
echo ""
echo "Consumer:"
echo "  repositories { maven { url 'https://jitpack.io' } }"
echo "  compileOnly '${GROUP_ID}:${ARTIFACT_ID}:${TAG}'"
echo ""

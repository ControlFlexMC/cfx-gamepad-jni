#!/bin/bash
#
# Create a GitHub Release for cfx-gamepad-jni
#
# The release version is read from gradle.properties.
# Edit gradle.properties to bump the version before running:
#   version=0.8.7
#
# Local dev builds (build-snapshot.sh) automatically append -SNAPSHOT.
# Releases use the bare version from gradle.properties.
#
# This script:
#   1. Reads version from gradle.properties
#   2. Commits the version bump + pushes
#   3. Builds the JAR (gradle clean jar)
#   4. Creates a Git tag
#   5. Creates a GitHub Release via gh CLI
#   6. Uploads the JAR as a release asset
#
# Usage:
#   chmod +x publish-release.sh
#   ./publish-release.sh
#
# Prerequisites:
#   - GitHub CLI (gh) installed and authenticated (gh auth login)
#   - Gradle available in PATH
#   - JAVA_HOME set
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "${SCRIPT_DIR}"

PROPS_FILE="gradle.properties"

# ─────────────────────────────────────────────
# Read version from gradle.properties
# ─────────────────────────────────────────────
if [ ! -f "${PROPS_FILE}" ]; then
    echo "❌ Error: ${PROPS_FILE} not found"
    exit 1
fi

VERSION=$(grep -E '^version=' "${PROPS_FILE}" | cut -d'=' -f2 | xargs)

if [ -z "${VERSION}" ]; then
    echo "❌ Error: 'version=' not found in ${PROPS_FILE}"
    exit 1
fi

TAG="v${VERSION}"

# ─────────────────────────────────────────────
# Check prerequisites
# ─────────────────────────────────────────────
if ! command -v gh &>/dev/null; then
    echo "❌ Error: GitHub CLI (gh) not found"
    echo "   Install: brew install gh    (macOS)"
    echo "   Login:   gh auth login"
    exit 1
fi

if ! command -v gradle &>/dev/null; then
    echo "❌ Error: gradle not found"
    exit 1
fi

if ! gh auth status &>/dev/null; then
    echo "❌ Error: gh not authenticated. Run: gh auth login"
    exit 1
fi

# ─────────────────────────────────────────────
# Confirm
# ─────────────────────────────────────────────
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  GitHub Release: cfx-gamepad-jni ${TAG}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  Version:     ${VERSION}   (from ${PROPS_FILE})"
echo "  Tag:         ${TAG}"
echo "  Remote:      $(git remote get-url origin 2>/dev/null || echo 'unknown')"
echo "  Branch:      $(git branch --show-current)"
echo ""
echo "  The script will:"
echo "    1. Commit version bump (${PROPS_FILE})"
echo "    2. Build ${TAG} JAR"
echo "    3. Push commit + tag to origin"
echo "    4. Create GitHub Release + upload JAR"
echo ""

read -rp "Proceed? [y/N] " CONFIRM
if [ "${CONFIRM}" != "y" ] && [ "${CONFIRM}" != "Y" ]; then
    echo "Aborted."
    exit 0
fi

# ─────────────────────────────────────────────
# Commit version bump
# ─────────────────────────────────────────────
echo ""
echo "Committing version bump: ${VERSION}..."

if ! git diff --quiet -- "${PROPS_FILE}" 2>/dev/null; then
    git add "${PROPS_FILE}"
    git commit -m "Set version ${VERSION}"
else
    # Check if already committed
    if git diff --cached --quiet -- "${PROPS_FILE}" 2>/dev/null && \
       ! git log --oneline -1 --format="%s" | grep -q "Set version ${VERSION}"; then
        echo "⚠️  ${PROPS_FILE} is unchanged. Did you edit it?"
        echo "   Expected version=${VERSION} in ${PROPS_FILE}"
        exit 1
    fi
fi

# ─────────────────────────────────────────────
# Build the JAR
# ─────────────────────────────────────────────
echo ""
echo "Building JAR (version ${VERSION})..."
gradle clean jar

JAR_FILE=$(ls build/libs/cfx-gamepad-jni-*.jar 2>/dev/null | head -1)
if [ -z "${JAR_FILE}" ] || [ ! -f "${JAR_FILE}" ]; then
    echo "❌ Error: JAR not found after build"
    exit 1
fi

echo "✅ JAR: ${JAR_FILE}"
echo "   Size: $(ls -lh "${JAR_FILE}" | awk '{print $5}')"

# ─────────────────────────────────────────────
# Push commit
# ─────────────────────────────────────────────
echo ""
echo "Pushing commit to origin..."
git push origin "$(git branch --show-current)"

# ─────────────────────────────────────────────
# Create Git tag
# ─────────────────────────────────────────────
echo ""
echo "Creating tag ${TAG}..."

if git rev-parse "${TAG}" >/dev/null 2>&1; then
    echo "⚠️  Tag ${TAG} already exists locally."
    read -rp "Delete and recreate? [y/N] " RECREATE
    if [ "${RECREATE}" = "y" ] || [ "${RECREATE}" = "Y" ]; then
        git tag -d "${TAG}"
        git push origin --delete "${TAG}" 2>/dev/null || true
    else
        echo "Aborted."
        exit 0
    fi
fi

git tag -a "${TAG}" -m "Release ${TAG}"
git push origin "${TAG}"

echo "✅ Tag ${TAG} pushed"

# ─────────────────────────────────────────────
# Create GitHub Release
# ─────────────────────────────────────────────
echo ""
echo "Creating GitHub Release ${TAG}..."

NOTES=$(cat <<EOF
## cfx-gamepad-jni ${TAG}

SDL3 Gamepad JNI wrapper for [Control Flex](https://www.curseforge.com/minecraft/mc-mods/control-flex).

### Supported Platforms
| Platform | Architectures |
|----------|---------------|
| macOS    | aarch64 (Apple Silicon), x86_64 (Intel) |
| Windows  | x86_64 |
| Linux    | x86_64, aarch64 |

### JitPack / Maven
Add the JitPack repository and dependency to use this version:

**build.gradle:**
\`\`\`gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.ControlFlexMC:cfx-gamepad-jni:${VERSION}'
}
\`\`\`

Or via the GitHub coordinate:
\`\`\`gradle
dependencies {
    implementation 'com.github.ControlFlexMC:cfx-gamepad-jni:${TAG}'
}
\`\`\`

### Files
- \`cfx-gamepad-jni-${VERSION}.jar\` — JAR with bundled native libraries for all platforms
EOF
)

# Create release first (without asset — avoids 502 on uploads blocking the whole thing)
gh release create "${TAG}" \
    --title "cfx-gamepad-jni ${TAG}" \
    --notes "${NOTES}"

echo "✅ Release ${TAG} created"

# Upload JAR asset with retry (GitHub uploads occasionally return 502)
echo ""
echo "Uploading JAR asset..."
UPLOAD_SUCCESS=0
for i in 1 2 3 4 5; do
    echo "  Attempt ${i}/5..."
    if gh release upload "${TAG}" "${JAR_FILE}" 2>&1; then
        UPLOAD_SUCCESS=1
        break
    fi
    echo "  Upload failed, retrying in 10s..."
    sleep 10
done

if [ "${UPLOAD_SUCCESS}" -eq 0 ]; then
    echo ""
    echo "⚠️  Asset upload failed after 5 attempts."
    echo "  Retry manually: gh release upload ${TAG} ${JAR_FILE}"
    exit 1
fi

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  ✅ Release ${TAG} published successfully!"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  JitPack status: https://jitpack.io/#ControlFlexMC/cfx-gamepad-jni/${TAG}"
echo ""

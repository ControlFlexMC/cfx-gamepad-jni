#!/usr/bin/env bash
#
# Refresh prebuilt/pe-timestamp from the HEAD commit time.
#
# Run this when the native sources change (src/main/c, CMakeLists.txt, the build
# scripts, the trimmed SDL3) and commit the result together with that change.
# Do not run it for Java-only changes: the DLL bytes would stay identical, and a
# moving timestamp would give the same binary a new hash — which is exactly what
# Smart App Control and Defender key their reputation on.
#
# The value is not read from the environment: the repository is the single source
# of truth, so any machine building this tag produces the same bytes.

set -euo pipefail

here=$(cd "$(dirname "$0")" && pwd)
file="${here}/pe-timestamp"

stamp=$(git -C "${here}" log -1 --format=%ct)
current=$(grep -vE '^[[:space:]]*(#|$)' "${file}" | head -1 | tr -d '[:space:]' || true)

if [ "${stamp}" = "${current}" ]; then
    echo "pe-timestamp unchanged (${stamp})"
    exit 0
fi

tmp=$(mktemp)
awk -v stamp="${stamp}" '
    !done && $0 !~ /^[[:space:]]*(#|$)/ { print stamp; done = 1; next }
    { print }
    END { if (!done) print stamp }
' "${file}" > "${tmp}"
mv "${tmp}" "${file}"

echo "pe-timestamp: ${current:-<none>} -> ${stamp}"
echo "Commit prebuilt/pe-timestamp together with the native change."

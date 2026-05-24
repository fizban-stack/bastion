#!/bin/bash
# Bastion Docker entrypoint
# Runs the Bastion daemon in background/headless mode.
#
# Environment variables:
#   BASTION_MODE       — daemon mode: background (default), tray, gui
#   BASTION_DATA_DIR   — where to store connection data (default: /data)
#   BASTION_PORT       — override the beacon API port (default: 21721)
#   BASTION_API_KEY    — set a static API key for the beacon (optional)

set -euo pipefail

BASTION_MODE="${BASTION_MODE:-background}"
BASTION_DATA_DIR="${BASTION_DATA_DIR:-/data}"
BASTION_PORT="${BASTION_PORT:-21721}"

# Ensure data directory exists with correct permissions
mkdir -p "${BASTION_DATA_DIR}"

echo "=== Bastion $(cat /app/version 2>/dev/null || echo 'dev') ==="
echo "    Mode:     ${BASTION_MODE}"
echo "    Data dir: ${BASTION_DATA_DIR}"
echo "    Port:     ${BASTION_PORT}"
echo ""

# Build JVM system properties
BASTION_JVM_PROPS=(
    "-Dio.xpipe.app.mode=${BASTION_MODE}"
    "-Dio.xpipe.app.dataDir=${BASTION_DATA_DIR}"
    "-Dio.xpipe.beacon.port=${BASTION_PORT}"
    # Monocle headless platform — ensures no display is required even if JavaFX modules are loaded
    "-Dglass.platform=Monocle"
    "-Dmonocle.platform=Headless"
    # Disable update checks (no update server configured for self-built fork)
    "-Dio.xpipe.app.disableUpdateCheck=true"
)

# Append API key if set
if [[ -n "${BASTION_API_KEY:-}" ]]; then
    BASTION_JVM_PROPS+=("-Dio.xpipe.app.apiKey=${BASTION_API_KEY}")
fi

# Convert array to Gradle -D flags
GRADLE_ARGS=()
for prop in "${BASTION_JVM_PROPS[@]}"; do
    GRADLE_ARGS+=("${prop}")
done

cd /app

exec ./gradlew :app:run \
    "${GRADLE_ARGS[@]}" \
    --no-daemon \
    --console=plain \
    -x test \
    "$@"

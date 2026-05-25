#!/bin/bash
# Launches Bastion in GUI mode inside the webtop XFCE session.
# Called by XFCE autostart via bastion.desktop.

# Wait a moment for the desktop to fully initialize
sleep 3

BASTION_DATA_DIR="${BASTION_DATA_DIR:-/config/bastion}"
BASTION_PORT="${BASTION_PORT:-21721}"

mkdir -p "${BASTION_DATA_DIR}"

echo "[bastion] Starting Bastion GUI — data: ${BASTION_DATA_DIR}, port: ${BASTION_PORT}"

cd /opt/bastion

exec ./gradlew :app:run \
    -Dio.xpipe.app.mode=gui \
    -Dio.xpipe.app.dataDir="${BASTION_DATA_DIR}" \
    -Dio.xpipe.beacon.port="${BASTION_PORT}" \
    -Dio.xpipe.app.disableUpdateCheck=true \
    -Dio.xpipe.app.fullVersion=true \
    --no-daemon \
    --console=plain \
    2>&1 | tee "${BASTION_DATA_DIR}/bastion.log"

#!/bin/bash
# Container init script for Bastion (runs as root via s6 before desktop starts)
# Fixes file ownership to match the runtime PUID/PGID set in docker-compose.yml

PUID="${PUID:-1000}"
PGID="${PGID:-1000}"
BASTION_DATA_DIR="${BASTION_DATA_DIR:-/config/bastion}"

echo "[cont-init] Setting up Bastion for UID=${PUID} GID=${PGID}"

# Create Bastion data directory owned by abc
mkdir -p "${BASTION_DATA_DIR}"
chown -R "${PUID}:${PGID}" "${BASTION_DATA_DIR}"

# Give abc write access to the Bastion build directory
# (Gradle writes lock files and incremental build state here at runtime)
chown -R "${PUID}:${PGID}" /opt/bastion
chmod -R u+rw /opt/bastion

# Create Gradle user home (cache) inside /config so it persists
mkdir -p /config/.gradle
chown -R "${PUID}:${PGID}" /config/.gradle

echo "[cont-init] Bastion setup complete"

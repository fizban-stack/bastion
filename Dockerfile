# ─── Stage 1: Build Bastion JARs ─────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS builder

RUN apt-get update && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Cache dependency downloads: copy only the build descriptor files first.
# If these don't change, the next RUN layer is a cache hit.
COPY gradlew gradlew.bat settings.gradle build.gradle version ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q 2>/dev/null || true

# Copy full source and compile
COPY . .
RUN chmod +x gradlew && ./gradlew :app:assemble \
    $(find ext -maxdepth 1 -name 'build.gradle' -exec dirname {} \; \
      | xargs -I{} basename {} \
      | grep -v '^ext$' \
      | sed 's/^/:/' \
      | sed 's/$/:assemble/' \
      | tr '\n' ' ') \
    --no-daemon -x test \
    2>&1


# ─── Stage 2: Webtop + Bastion ────────────────────────────────────────────────
# Webtop provides: Xvnc virtual display, KasmVNC web server (port 3000/3001),
# XFCE desktop, and s6-overlay process supervision — all in one image.
# We layer the Bastion app and JDK 25 on top.
FROM lscr.io/linuxserver/webtop:ubuntu-xfce

# ── JDK 25 (copied directly from the Temurin builder — no apt repo needed) ───
COPY --from=builder /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# ── System packages needed at runtime ────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    openssh-client \
    sshpass \
    curl \
    libgtk-3-0 \
    libxtst6 \
    libxi6 \
    && rm -rf /var/lib/apt/lists/*

# ── Bastion app (source + pre-built JARs from builder) ───────────────────────
COPY --from=builder /build /opt/bastion
RUN chmod +x /opt/bastion/gradlew

# ── Gradle user home → inside the /config volume (abc's home in webtop) ──────
# This makes Gradle caches persist across container recreations.
ENV GRADLE_USER_HOME=/config/.gradle

# ── Custom container-init script (runs before the desktop starts) ─────────────
# Fixes file ownership for the abc user (UID=PUID, configurable at runtime)
# and creates the Bastion data directory.
COPY docker/cont-init-bastion.sh /etc/cont-init.d/99-bastion.sh
RUN chmod +x /etc/cont-init.d/99-bastion.sh

# ── XFCE autostart: launches Bastion GUI when the desktop session starts ──────
COPY docker/bastion.desktop /etc/xdg/autostart/bastion.desktop
COPY docker/start-bastion-gui.sh /opt/bastion/docker/start-bastion-gui.sh
RUN chmod +x /opt/bastion/docker/start-bastion-gui.sh

# ── Ports ──────────────────────────────────────────────────────────────────────
# 3000  — KasmVNC web interface (HTTP) — open this in a browser
# 3001  — KasmVNC web interface (HTTPS)
# 21721 — Bastion beacon API (CLI, scripts, external integrations)
EXPOSE 3000 3001 21721

# /config is webtop's standard persistent volume (user home, settings, etc.)
# Bastion data is stored at /config/bastion inside this volume.
VOLUME /config

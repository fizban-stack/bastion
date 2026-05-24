# Bastion

**Bastion** is an open-source, fully-unlocked connection hub for your entire server infrastructure. Built on top of [xpipe](https://github.com/xpipe-io/xpipe) with the commercial license system removed — every feature is free, forever.

Bastion works on top of your installed command-line tools (SSH, Docker, and others) and requires no setup on remote systems. It integrates with your favorite text editors, terminals, shells, VNC/RDP clients, password managers, and command-line tools. The platform is extensible — anyone can add support for new tools or implement custom functionality via the module system.

---

## What's different from xpipe

| | xpipe | Bastion |
|---|---|---|
| License | Source-available (EULA) | Open source fork |
| Pro features | Paywall (workspaces, SSH certs, team vault, services) | **All unlocked** |
| RDP/VNC | Unlocked in Pro | **Unlocked for everyone** |
| License server | Required at runtime | **Removed** |
| Window title | "XPipe [Community/Pro]" | "Bastion" |
| Data directory | `~/.xpipe` | `~/.bastion` |

## Supported connections

- **SSH** — connections, config files, tunnels, and SSH certificates
- **Containers** — Docker + Compose, Podman, LXD, and Incus
- **Virtual machines** — Proxmox PVE, Hyper-V, KVM, VMware Player/Workstation/Fusion
- **Networks** — Tailscale, Netbird, Teleport
- **Cloud** — AWS, Hetzner Cloud
- **Remote desktop** — RDP and VNC (fully unlocked)
- **Windows environments** — WSL, Cygwin, MSYS2
- **Kubernetes** — clusters, pods, containers
- **PowerShell** — Remote Sessions

## Building

Bastion uses Gradle. Standard Java 21+ and Gradle setup applies:

```bash
git clone https://github.com/your-org/bastion
cd bastion
./gradlew build
```

The `FreeLicenseProvider` in `ext/uacc` replaces the commercial license module and unlocks all features at build time. No external license server or key is needed.

## Architecture

Bastion inherits xpipe's modular Java architecture:

- `core/` — data model, OS abstractions, serialization
- `beacon/` — IPC daemon protocol (JSON over HTTP)
- `app/` — JavaFX UI, extension loading, preferences
- `ext/base/` — SSH, file browser, scripting primitives
- `ext/proc/` — shell and process management
- `ext/system/` — container/VM integrations (Docker, LXD, Podman, K8s)
- `ext/uacc/` — **Bastion addition**: `FreeLicenseProvider` — unlocks all features

## License

This fork is derived from [xpipe](https://github.com/xpipe-io/xpipe). The xpipe source is released under its own license terms. Bastion modifications (primarily `ext/uacc/`) are released under the MIT License.

## Relationship to upstream

Bastion tracks upstream xpipe releases for bug fixes and new protocol support. The key divergence is the `ext/uacc` module which provides an always-free `LicenseProvider` implementation instead of the commercial one.

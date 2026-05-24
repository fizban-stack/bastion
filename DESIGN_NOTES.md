# Bastion — Design Notes (v2 analysis + feature proposals)

> Generated 2026-05-24. Companion to `IMPROVEMENTS.md`. Sections A/B/C/D are independent and can be picked up in any order; section E is the recommended rollout.
>
> **A.** Six new improvement items beyond the existing IMPROVEMENTS.md
> **B.** Session recording for RDP & VNC (toggleable, cross-platform)
> **C.** CLI session transcription (toggleable, three modes)
> **D.** Drive passthrough across RDP / SSH / containers
> **E.** Unified "Sessions" subsystem + prioritized rollout

---

## A. Six New Improvement Areas

### A1. Replace `getSimpleName().equals("LicenseRequiredException")` with `instanceof` checks
*Priority: High · Effort: Trivial · Type: Correctness bug*

`ShellControl.java:123`, `LxdContainerStore.java:117`, `PodmanContainerStore.java:152`, `IncusContainerStore.java:117` all compare against the simple class name as a *string*. After the rebrand the class still exists, but this pattern is fragile — a future refactor that renames or moves the class will silently break feature gating, and these checks were dead weight already with `FreeLicensedFeature.throwIfUnsupported()` being a no-op. Replace each with `instanceof LicenseRequiredException` or delete the branch entirely since the no-op provider means it can never fire. Same edit shape in all four files; one PR.

### A2. Build a `RemoteDesktopDockEntry` lifecycle hook
*Priority: High · Effort: Medium · Type: Architectural enabler*

`RemoteDesktopWindow.trackExternal(...)` in `MstscRdpClient.java:199` is the canonical wrapper around every external RDP launch (`window.trackInternal(...)` is the VNC twin in `InternalVncClient.java:32`). Both currently return after registering. Add `onTracked(Consumer<RemoteDesktopDockEntry> hook)` and `onUntracked(Consumer<RemoteDesktopDockEntry> hook)` SPI registration so subsystems (recording, audit log, drive-mount lifecycle) can react to remote-desktop session start/end without each RDP/VNC client knowing about them. This is the single biggest leverage point for feature B and D — without it the recording subsystem has to instrument every client.

### A3. Centralize the seven RDP-client launch paths
*Priority: Medium · Effort: Medium · Type: Refactor*

`MstscRdpClient`, `FreeRdpClient`, `KrdcRdpClient`, `RemminaRdpClient`, `DevolutionsRdpClient`, `RemoteDesktopAppRdpClient`, `WindowsAppRdpClient`, `CustomRdpClient` each independently:

- Build their command line
- Spawn a process
- Track the window via `RemoteDesktopWindow`
- Clean up a temp .rdp file

Extract a `BaseExternalRdpClient` abstract class with a templated `launch()` that handles the spawn + track + cleanup, with subclasses overriding only `buildCommand(RdpLaunchConfig)`. Same exercise for the ten VNC clients in `app/vnc/`. After this refactor, adding a new client is ~40 lines instead of ~200, and feature B (recording) hooks into one place.

### A4. RDP config has no validator
*Priority: Medium · Effort: Low · Type: Defense-in-depth*

`RdpConfig.parseContent()` (`app/util/RdpConfig.java:36`) accepts anything that splits on `:` — no validation that keys are known RDP file keys, no sanitization of values. `MstscRdpClient` writes this directly to a temp file passed to `mstsc.exe`. A maliciously-crafted store entry could inject arbitrary `.rdp` directives (autoreconnection, `redirectprinters`, `audiocapturemode`). Add an allowlist of recognized keys and reject unknown ones, or escape them into a quoted form. The risk is moderate today because store entries are user-authored, but feature D (drive passthrough) widens the attack surface.

### A5. The `ProcessBuilder` shell-injection audit
*Priority: Medium · Effort: Medium · Type: Security*

`LocalExec.executeAsync()` and `sc.command(...).execute()` are used everywhere external processes get launched. Most call sites use the safe `varargs` form (string array), but several construct a shell command string from user-controlled values (store names, connection titles, paths with spaces). Examples worth auditing: `TerminalLauncher.constructTerminalInitFile()` (`app/terminal/TerminalLauncher.java:39`), and the WSL/Cygwin/MSYS2 paths in `ext/proc`. Even when only the maintainer can author stores, store-import features mean attacker-controlled JSON can land. Audit with `rg "sc.command\(\".*\$\{|sc.command\(\".*%s" --type java` and convert any concatenated command strings to `CommandBuilder` arg arrays.

### A6. The dock window is JavaFX-tightly-coupled
*Priority: Low · Effort: High · Type: Architecture*

`RemoteDesktopWindow` is a JavaFX `Stage` subclass that handles BOTH window-positioning math AND session lifecycle. The math (dock bounds, scaling) needs the UI thread; the lifecycle doesn't. Extract a `RemoteDesktopSession` POJO that owns store-entry, icon, color, process handle, and listeners — the JavaFX `Stage` becomes a view over the session. This makes the session reachable from non-UI threads (recording subsystem, audit log writer) without bouncing through `Platform.runLater(...)`. Optional but unlocks easier testability.

---

## B. Screen Recording for RDP & VNC

### B1. Architectural choice — capture-at-the-OS-window, not per-client

Bastion supports **7 external RDP clients × 10 VNC clients = 17 client integrations**, plus the `InternalVncClient` that uses an in-process VNC session. Each external client spawns a child process; Bastion never sees the RDP/VNC protocol stream because the external client owns the network connection.

> **Therefore: recording belongs at the OS window-capture layer, not in any individual client.**

This is the single architectural decision that makes feature B tractable. The alternative — instrumenting each client — would require 17 separate integrations against three or four wire protocols Bastion doesn't currently parse.

### B2. Hook point

`RemoteDesktopWindow.trackExternal(...)` (called from `MstscRdpClient.java:199`, `FreeRdpClient.java:62`, and every other RDP client) and `RemoteDesktopWindow.trackInternal(...)` (called from `InternalVncClient.java:32`) are the **two universal entry points** for "an RDP/VNC session just started." If the `onTracked` hook from improvement A2 lands, recording becomes:

```java
RemoteDesktopWindow.onTracked(entry -> {
    if (AppPrefs.get().sessionRecording.get() && entry.shouldRecord()) {
        SessionRecorder.start(entry);
    }
});
RemoteDesktopWindow.onUntracked(entry -> SessionRecorder.stopIfActive(entry));
```

`SessionRecorder` is a new class in `app/src/main/java/io/xpipe/app/sessions/` (new package). It spawns `ffmpeg` as a child process and captures the OS window the client opened.

### B3. Cross-platform capture strategy

| OS | Strategy | Notes |
|---|---|---|
| **Windows** | `ffmpeg -f gdigrab -i title="<window title>"` | Window titles for mstsc are predictable; `RemoteDesktopWindow` already knows the title. |
| **macOS** | `ffmpeg -f avfoundation -i "<screen>:none"` | AVFoundation indexes screens, not windows — need to record the full screen of the displaying monitor and crop based on dock bounds. |
| **Linux X11** | `ffmpeg -f x11grab -i :0.0+x,y -s WxH` | Use `RemoteDesktopWindow.getDockBounds()` to pass the geometry. |
| **Linux Wayland** | `wf-recorder -g "x,y wxh"` or `gpu-screen-recorder` via pipewire portal | Portal denial must surface a one-time toast — do not silently fail. |

`SessionRecorder` picks the backend at startup via `OsType.getLocal()`. A `BASTION_RECORD_BACKEND=ffmpeg|wfrecorder|...` env override is provided for users with non-default setups.

### B4. Storage layout

```
~/.bastion/
  sessions/
    {iso8601}_{store-slug}_{session-id}/
      manifest.json              # store entry summary, started/ended, codec, duration, sha256
      recording.mp4              # the video (or .webm if VP9 chosen)
      events.jsonl               # session lifecycle events (start, dock-resize, end)
      thumbnail.png              # first-frame poster
```

`manifest.json` is the source of truth — recordings without a manifest are orphaned and pruned on startup. The `sessions/_index.jsonl` file is appended on every session-end with a one-line summary (id, slug, started, duration, size) for fast listing.

### B5. Settings UX

Add three properties to `AppPrefs.java` after line 207 (the `windowOpacity` block):

```java
public final BooleanProperty sessionRecording = map(Mapping.builder()
        .property(new SimpleBooleanProperty(false))   // OPT-IN — never default-on
        .key("sessionRecording")
        .restartRequired(false)
        .build());

public final ObjectProperty<RecordingFormat> sessionRecordingFormat = map(...)   // MP4 / WEBM
public final Property<Integer> sessionRecordingRetentionDays = map(...)          // 0 = never delete
public final Property<Long> sessionRecordingMaxBytes = map(...)                  // disk budget
```

Surface in a new `SessionsCategory.java` (mirroring `RdpCategory.java:32`) registered via `PrefsProvider` SPI. The category page shows the global toggle, retention controls, codec/bitrate, and a "Show sessions folder" button.

### B6. Per-connection override

The `DataStoreEntry` carries arbitrary metadata via `getStoreCache()`. Add `entry.recordSessions()` returning `Optional<Boolean>` (override) or empty (follow global). In the store-entry context menu add a "Record sessions" tri-state checkbox: inherit / force-on / force-off. This lets users globally disable recording but keep one production-jump-box recorded for compliance.

### B7. Privacy posture (anti-foot-gun)

- **Default OFF.** Never silently default-on.
- **First-time banner.** On first toggle-on, a modal explains where files are stored, that they're unencrypted by default, and the retention default.
- **Active indicator.** A small red dot in `RemoteDesktopWindow`'s title bar when recording is live, and an entry in the tray menu listing active recordings.
- **Pause button.** Per-session pause/resume via `RemoteDesktopWindow` chrome.
- **Encryption-at-rest, optional.** A v2 follow-up — encrypt recordings with the existing vault key. Out of scope for v1, but the manifest format should reserve an `encryption: { algorithm, key_id }` field so v1 recordings can be re-encrypted later.

### B8. Failure modes

| Failure | Detection | Handling |
|---|---|---|
| ffmpeg not installed | `ProcessBuilder` returns nonzero on `ffmpeg -version` at startup | One-time toast linking to install docs; recording stays toggleable but inert |
| Wayland portal denied | xdg-desktop-portal returns 1 | Toast "Screen capture blocked by system portal"; per-session retry button |
| Disk full | ffmpeg exits with `ENOSPC` | Toast + auto-pause recording + mark session `recording: partial` in manifest |
| Window title collision | gdigrab can match wrong window | Bastion already prepends a unique session marker to titles — extend that pattern so the title contains the session UUID |

---

## C. CLI Transcription

### C1. Hook point

**Single tap: `ShellControl`** (`app/process/ShellControl.java`). Every shell session — SSH, local, Docker exec, LXD exec, Podman exec, Kubernetes exec, WSL, Cygwin — runs through this interface. The lifecycle methods `onInit`, `onExit`, `onKill` (lines 103, 160, 165) mark session bounds; `ShellView` (returned by `view()` at line 55) is the IO surface.

Two implementation paths:

| Path | What it captures | Tradeoff |
|---|---|---|
| **(a) ShellControl wrapper** | Every byte through `writeLine`/`readLine`/`view().output()` | Captures everything Bastion sends/receives; misses bytes typed in a *spawned external terminal* (terminal owns the PTY directly) |
| **(b) Terminal init-file injection** | Asciinema/script wraps the user's shell when terminal launches | Captures everything a user types/sees in the external terminal; misses Bastion-driven shells |

> **Both are needed.** They cover complementary surfaces. Implement (a) first — it's a single tap and covers all programmatic shell operations.

### C2. Format

Three modes:

| Mode | File | Format | Use case |
|---|---|---|---|
| **Off** | — | — | Default |
| **Commands** | `commands.jsonl` | `{"ts":..., "session":..., "command":..., "exitCode":...}` | Audit / search across history |
| **Full** | `transcript.cast` | [asciinema cast v2](https://docs.asciinema.org/manual/asciicast/v2/) | Replay with `asciinema play` |

Asciinema is a published, stable JSON-line format with mature players (`asciinema play`, `asciinema-player.js`); reusing it means recordings are inspectable outside Bastion and shareable as URLs.

### C3. Hook implementation

In `ShellSession.start()` (`app/ext/ShellSession.java:63`), wrap the started `ShellControl` if transcription is enabled:

```java
shellControl.start();
if (AppPrefs.get().sessionTranscription.get() != TranscriptionMode.OFF) {
    shellControl = TranscriptionWrapper.wrap(shellControl, entry, mode);
}
```

`TranscriptionWrapper` is a delegating proxy that intercepts `writeLine`, `command`, and `view()` to capture IO into the session manifest. Disabled mode is identity-passthrough (zero overhead).

### C4. Settings UX

Add to `AppPrefs.java`:

```java
public final ObjectProperty<TranscriptionMode> sessionTranscription = map(Mapping.builder()
        .property(new SimpleObjectProperty<>(TranscriptionMode.OFF))
        .key("sessionTranscription")
        .build());
```

Surface in the same `SessionsCategory` as recording (one panel covers both).

### C5. Privacy posture

- **Default OFF.**
- **Pause on echo-off.** When the shell sets `stty -echo` (the universal signal for "I'm about to read a password"), pause transcription until echo is restored. ShellControl already tracks dialect state — extending it to track echo state is a small addition.
- **Pattern-mask.** A configurable list of regex patterns (default: `password`, `passwd`, `token`, `secret`, `apikey` followed by `=` or `:`) masks the value in the transcript. Source of truth: `~/.bastion/transcription-masks.yaml`.
- **Per-session pause.** A keyboard shortcut (`Ctrl+Shift+P` while in the dock window) toggles pause for the current session.

### C6. Synergy with recording (key insight)

When BOTH features are on for an RDP/VNC + concurrent shell session against the same host, write them into the **same** `~/.bastion/sessions/{id}/` directory with one manifest cross-referencing the video and the transcript. This makes the recording skip-to-command navigation possible: a future UI improvement can let the user click a command in the transcript and seek the video to that timestamp.

---

## D. Drive Passthrough

### D1. RDP — already supported by the protocol, missing in Bastion

mstsc and FreeRDP both support drive redirection natively. Bastion just doesn't emit the directives.

**mstsc (`.rdp` file format):**

```
drivestoredirect:s:HostDriveName
```

`RdpConfig.overlay()` (`app/util/RdpConfig.java:59`) is the existing extension point. Add to `MstscRdpClient.getAdaptedConfig()` (line 268):

```java
private RdpConfig applyDrivePassthrough(RdpConfig c, RdpLaunchConfig launch) {
    var entry = launch.getEntry();
    var mounts = RdpDriveMounts.forEntry(entry);   // list of {name, localPath}
    if (mounts.isEmpty()) return c;
    var spec = mounts.stream().map(m -> m.name).collect(Collectors.joining(";"));
    return c.overlay(Map.of("drivestoredirect", RdpConfig.TypedValue.string(spec)));
}
```

`RdpDriveMounts` is a new utility in `app/util/` keyed on store-entry ID. The UI for managing mounts lives in the store-entry context menu under "Drive passthrough...".

**FreeRDP (`/drive` flag):**

In `FreeRdpClient.launch()` (line 45) append per-mount `/drive:NAME,PATH` flags before launch. Each mount is one flag. FreeRDP supports mounting arbitrary host directories under any drive letter — strictly more flexible than mstsc which can only redirect existing drives.

### D2. SSH — SSHFS mount, not protocol-native

SSH doesn't have RDP-style drive redirection; the equivalent is mounting the remote filesystem locally via SSHFS (FUSE on Linux/macOS, `winfsp` + `sshfs-win` on Windows). Bastion already has the SFTP code path for the file browser — adding "mount as drive" is a small UI addition:

1. Add a "Mount as drive" action to the store-entry context menu when the entry implements `FileSystemStore`.
2. On click, shell out to `sshfs user@host:/remote/path /local/mountpoint -o ServerAliveInterval=15,reconnect`.
3. Track mount points in `~/.bastion/sshfs-mounts.json`.
4. On session disconnect or app exit, run `fusermount -u /local/mountpoint` (Linux) / `umount` (macOS) / `taskkill` the sshfs.exe (Windows).

Detection: probe `sshfs --version` at startup; offer install instructions if missing. The mount UI is gated behind that detection.

### D3. Containers — bind-mount support at start

`PodmanContainerStore`, `LxdContainerStore`, and `IncusContainerStore` each open a shell into an existing container via `lxc exec`, `podman exec`, or `incus exec`. **You can't add a bind mount to a running container** — bind mounts are configured at create time. Two paths:

| Approach | What it does | Tradeoff |
|---|---|---|
| **Mount-at-create** | When the user creates a new container via Bastion's `docker run` / `podman run` wrapper, expose a "Mounts" tab in the create dialog | Clean; only works for new containers |
| **lxc config device add** (LXD/Incus) | Live-add a disk device to a running container | Native LXD feature; Podman & Docker have no equivalent |

Implement mount-at-create for all three, and `lxc/incus config device add` for the LXD path. Don't pretend Podman/Docker can do live mounts.

For Docker specifically — `PodmanContainerStore` is a sibling of a `DockerContainerStore` that already exists in upstream — the same logic applies. Mount config goes in `docker run -v HOST:CONTAINER`.

### D4. Security UX (anti-foot-gun)

Drive passthrough is the most dangerous of the three features — a compromised remote machine gets read/write access to host paths.

- **Always named, never wildcard.** Refuse `drivestoredirect:s:*` (mount all local drives). Bastion's UI only lets you add named directories.
- **Read-only by default.** First-time mount UI defaults to read-only; opt-in to read-write.
- **Confirmation banner.** First mount per connection shows "This will give the remote machine access to: <paths>. Continue?"
- **Audit trail.** Every mount emits an event into `~/.bastion/sessions/{id}/events.jsonl` (the same file recording uses) so there's a record of what was exposed.
- **Auto-unmount.** SSHFS mounts and container live-mounts unmount on session close. Tracked in a sidecar `sshfs-mounts.json` so dangling mounts from crashed sessions get cleaned up at startup.
- **Path validation.** The host path must exist and must not be a sensitive default (`$HOME/.ssh`, `$HOME/.bastion`, `$HOME/.aws`, `/etc/shadow`, etc.) — show a warning, require explicit override.

### D5. Storage layout

`~/.bastion/mounts.json`:

```json
{
  "sshfs": [
    {"id":"...","entryId":"...","remote":"user@host:/srv","local":"/tmp/bastion-mnt-001","readOnly":true,"created":"2026-05-24T..."}
  ],
  "rdp": [
    {"entryId":"...","name":"Documents","localPath":"/Users/me/Documents","readOnly":false}
  ],
  "container": [
    {"entryId":"...","containerId":"...","hostPath":"/srv/data","containerPath":"/mnt/data","readOnly":true}
  ]
}
```

---

## E. Unified "Sessions" Subsystem + Rollout

### E1. The unifying insight

Recording, transcription, and drive-mount audit logs all want the same thing: **a session-keyed directory in `~/.bastion/sessions/{id}/`** with a `manifest.json` and per-feature artifacts. Building these as three separate subsystems means three indexes, three retention policies, three UIs.

> Build them as one. The package is `io.xpipe.app.sessions`. One `SessionManifest`, one `SessionRegistry`, one retention policy, one `SessionsCategory` in prefs, one "Sessions" tab in the main UI listing past sessions with thumbnails (video), command counts (transcript), and mount summaries (drive). Each feature is a recorder that writes into the same session directory.

### E2. Prioritized rollout

| # | Item | Why first | Effort |
|---|---|---|---|
| 1 | A1 — `instanceof LicenseRequiredException` | Trivial, fixes existing bug | <1h |
| 2 | A2 — `RemoteDesktopWindow.onTracked/onUntracked` hooks | Unblocks B and D | ~1d |
| 3 | E — `io.xpipe.app.sessions` package skeleton: `SessionManifest`, `SessionRegistry`, `SessionsCategory` | Foundation everything else uses | ~1-2d |
| 4 | C — Transcription mode `Commands` (cheapest, highest forensic value) | Single ShellControl tap; minimal UI | ~2d |
| 5 | B — Recording, Linux X11 backend only | Prove the ffmpeg pattern on one OS first | ~3d |
| 6 | B — Windows + macOS backends | Replicate the pattern | ~3d |
| 7 | B — Wayland (pipewire portal) backend | Last because most fragile | ~2-3d |
| 8 | D — RDP drive passthrough (mstsc first, FreeRDP after) | Highest user value, simplest path | ~2d |
| 9 | D — SSHFS mount | Reuses existing SFTP code | ~2d |
| 10 | C — Transcription mode `Full` (asciinema) | Builds on Commands mode | ~2d |
| 11 | D — Container bind-mount (create-time + LXD live-add) | Last because each container runtime is bespoke | ~3d |
| 12 | A3 — RDP/VNC client base classes | Cleanup after features land — easier to extract patterns once the recording hook proves the abstraction is right | ~3d |
| 13 | A4 / A5 — RDP config validator + shell-injection audit | Hardening pass after feature surface stabilizes | ~2d each |
| 14 | A6 — Decouple `RemoteDesktopWindow` from JavaFX `Stage` | Optional architectural cleanup; do only if E/B reveal pain | ~5d |

Total feature work (items 2-11): **~3-4 weeks of focused effort.** The first three items in E2 (A1, A2, E skeleton) are the unblockers and deliver no user-visible feature on their own — but skipping them forces feature B to be written against each of seventeen clients independently, which is the failure mode this design is built to prevent.

### E3. What this design deliberately does NOT do

- It does not propose recording inside individual RDP/VNC clients. The OS window-capture layer is the right primitive.
- It does not propose protocol-stream interception. Bastion doesn't own the protocols.
- It does not propose default-on for any feature. All three are opt-in.
- It does not propose wildcard drive sharing. Every mount is named.
- It does not propose new external dependencies beyond ffmpeg (recording) and sshfs (drive mount), both gracefully degraded if missing.
- It does not propose adding a new Java module. Everything lands in `app/src/main/java/io/xpipe/app/sessions/` — the `app` module already exports what's needed.

---

*End. Companion to IMPROVEMENTS.md; either can be picked up independently.*

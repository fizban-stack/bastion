# Bastion — Codebase Improvement Analysis

> Generated 2026-05-23. Priority order: impact × effort.

---

## 1. Complete the Upstream URL Purge (High Priority, Low Effort)

Several files still point to xpipe.io infrastructure that doesn't exist for Bastion:

**Affected files:**
- `app/src/main/java/io/xpipe/app/update/AppDownloads.java` — calls `api.xpipe.io/changelog` and `api.xpipe.io/version`
- `app/src/main/java/io/xpipe/app/update/AppRelease.java` — downloads from `github.com/xpipe-io/...`
- `app/src/main/java/io/xpipe/app/update/AppDistributionType.java` — references `apt.xpipe.io`, `xpipe-io/tap/` Homebrew tap, Winget/Choco package IDs `xpipe-io.xpipe`
- `app/src/main/java/io/xpipe/app/update/WingetUpdater.java` — uses `xpipe-io.xpipe` package ID
- `app/src/main/java/io/xpipe/app/util/Hyperlinks.java` — GitHub URLs, translate link
- `app/src/main/java/io/xpipe/app/util/DocumentationLink.java` — points to `docs.xpipe.io`
- `app/src/main/java/io/xpipe/app/Main.java` — help text references `docs.xpipe.io/cli`

**Recommendation:** Either disable the auto-update system entirely (safest for a fork), or point it at your own release infrastructure. At minimum, `Hyperlinks.java` should point to the Bastion GitHub repo. `AppDownloads.java` update checks will silently fail if no server is available — add a graceful disable flag.

**Quick fix:** Add a `BASTION_DISABLE_UPDATE_CHECK=true` system property that short-circuits `AppDownloads` before any HTTP call.

---

## 2. Remove or Replace Sentry Error Telemetry (High Priority, Medium Effort)

`SentryErrorHandler.java` sends crash reports to xpipe's Sentry DSN. Bastion users' error data is going to a third party (the upstream xpipe maintainer), not to the Bastion maintainer.

**What it sends:** stack traces, Java versions, OS info, license type, `hasPaidLicense()` flag, connection store counts.

**Affected files:**
- `app/src/main/java/io/xpipe/app/issue/SentryErrorHandler.java`
- `app/src/main/java/io/xpipe/app/issue/ErrorAction.java` (gates Sentry on `shouldReportError()`)

**Recommendation options:**
1. **Disable entirely** — set a no-op `SentryErrorHandler` or comment out the DSN initialization. The `shouldReportError()` in `FreeLicenseProvider` returns `true`, which currently routes errors to xpipe's Sentry. Change it to `false` to suppress external reporting while you don't have your own DSN.
2. **Replace with your own Sentry project** — update the DSN in `SentryErrorHandler` to your own project.
3. **Replace with a local error log** — write crashes to `~/.bastion/crash-reports/` instead.

**Immediate action:** Change `FreeLicenseProvider.shouldReportError()` to return `false` until you have your own error tracking.

---

## 3. Eliminate the Hardcoded `~/.xpipe` Data Directory (Medium Priority, Low Effort)

The data directory is driven by `AppNames.getSnakeName()` (now returns `"bastion"`), so `~/.bastion` should be the default in new installs. However, several files have hardcoded fallback references to `xpipe`:

**Affected files:**
- `beacon/src/main/java/io/xpipe/beacon/BeaconConfig.java` — temp dir paths (already fixed)
- `app/src/main/java/io/xpipe/app/core/AppProperties.java` — check for `io.xpipe.app.dataDir` system property name (still references `xpipe`)
- `app/src/main/java/io/xpipe/app/core/AppInstallation.java` — binary name `xpipe` in paths (now `bastiond`)
- `app/src/main/java/io/xpipe/app/secret/EncryptionToken.java` line 113 — hardcoded `s.equals("xpipe")` vault token string (this is a data-format identifier; changing it would break existing vaults)

**Recommendation:** Audit `AppProperties.java` and `AppInstallation.java` for any remaining hardcoded `xpipe` strings that affect file paths. The `EncryptionToken` vault identifier is a **breaking change** — leave it as `"xpipe"` to preserve vault compatibility for users migrating from upstream, or version the format.

---

## 4. Consolidate the Exception Handling Strategy (Medium Priority, High Effort)

The codebase has 428+ `catch` blocks, many catching broad `Exception` or `Throwable` without meaningful recovery. Patterns observed:

- Silent catches that log nothing (`catch (Exception ignored) {}`)
- Catches that wrap into `RuntimeException` and rethrow — losing the original message
- Error handling split across `GuiErrorHandler`, `SentryErrorHandler`, `ErrorHandlerComp`, and `ErrorAction` — four separate error surfaces that can diverge

**Specific issues:**
- `LxdContainerStore.java:117`, `PodmanContainerStore.java:152`, `IncusContainerStore.java:117` — check for `LicenseRequiredException` by class name string comparison: `t.getClass().getSimpleName().equals("LicenseRequiredException")` — fragile, will silently fail on class renames
- `ShellControl.java:123` — same pattern: `t.getClass().getSimpleName().equals("LicenseRequiredException")` — this will never match after the license system is removed since `FreeLicensedFeature.throwIfUnsupported()` is a no-op

**Recommendation:**
- Replace all `getSimpleName().equals("LicenseRequiredException")` checks with `instanceof LicenseRequiredException` — typesafe and refactor-proof
- Define a `BastionException` base class for application-level errors, separate from infrastructure errors
- Add a structured logging layer between the error handler and Sentry so errors are always written to disk regardless of telemetry status

---

## 5. Replace Scattered System Property Checks with a Config Object (Medium Priority, Medium Effort)

Configuration is scattered across `System.getProperty()` calls with string literals:

```java
System.getProperty("io.xpipe.app.dataDir")
System.getProperty("io.xpipe.app.staging")
System.getProperty("io.xpipe.app.locatorDisableInstallationVersionCheck")
System.getProperty("io.xpipe.beacon.printMessages")
```

These are:
1. Untyped — a typo in the property name silently returns null
2. Unrenameable — refactoring the property name requires grep-and-pray
3. Undiscoverable — no central registry of what properties exist

**AppProperties.java** partially addresses this but isn't consistently used — raw `System.getProperty()` calls leak throughout.

**Recommendation:** Create a `BastionConfig` enum or sealed class that:
- Declares all system properties with their expected type and default
- Provides typed accessors (`BastionConfig.STAGING.get()` returning `boolean`)
- Reads everything once at startup and caches

This would also make it easy to add a `--config-dump` CLI flag that prints the active configuration.

---

## 6. Add a Build-Time Feature Flag for the Update System (Low Priority, Low Effort)

Right now the update system is always active but points at xpipe.io. This causes silent HTTP failures (or worse, a security mismatch if someone standup their own update server).

**Recommendation:** Add a Gradle build flag:

```groovy
// build.gradle
ext.bastionUpdateUrl = project.findProperty('bastion.updateUrl') ?: ''
```

Generate a `BuildConfig.java` with:
```java
public static final String UPDATE_URL = "@bastion.updateUrl@"; // empty = disabled
```

Then `AppDownloads.java` checks `BuildConfig.UPDATE_URL.isEmpty()` before making any HTTP call. This cleanly disables the update system for self-built forks without source edits.

---

## 7. Test Coverage Gap (Low Priority, High Effort)

The repo has exactly **two test files** in `src/test` and `src/localTest`:

- `app/src/test/java/Test.java` — `System.out.println("a")` only
- `app/src/localTest/java/test/Test.java` — manual integration test that requires a running instance

There are **zero unit tests** for:
- Shell command construction and injection safety (`ShellControl`, `ShellView`)
- RDP config file generation (`RdpLaunchConfig`, `MstscRdpClient`)
- Beacon protocol serialization/deserialization
- Data store validation logic

**Recommendation:** Add JUnit 5 unit tests for at minimum:
- `RdpLaunchConfig` — test `.rdp` file content generation
- `BeaconClient` request/response serialization
- `AppNames` — verify all name variants return expected values (simple regression guard)
- `FreeLicenseProvider` — verify isSupported() returns true for all known feature IDs

The Gradle `build.gradle` already has `requires static org.junit.jupiter.api` in the module — the test framework is already wired in, just unused.

---

## 8. Dependency Hygiene: Remove get-xpipe.sh / get-xpipe.ps1 Install Scripts (Low Priority, Trivial)

`get-xpipe.sh` and `get-xpipe.ps1` in the root still reference upstream xpipe download URLs and installation paths. These scripts install xpipe, not Bastion.

**Action:** Replace with `get-bastion.sh` / `get-bastion.ps1` pointing to Bastion release artifacts, or remove them entirely until a Bastion distribution channel exists.

---

## Summary Table

| Area | Priority | Effort | Impact |
|------|----------|--------|--------|
| Upstream URL purge | High | Low | Prevents silent failures at runtime |
| Sentry telemetry | High | Low | Privacy — user data goes to xpipe, not you |
| Data directory hardcodes | Medium | Low | Clean migration path for users |
| Exception handling | Medium | High | Reliability and debuggability |
| Config consolidation | Medium | Medium | Maintainability and safety |
| Build-time update flag | Low | Low | Clean fork build experience |
| Test coverage | Low | High | Long-term regression safety |
| Install script cleanup | Low | Trivial | Branding consistency |

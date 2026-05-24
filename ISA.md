---
task: "Remove paywalls, rebrand xpipe fork as Bastion"
slug: 20260523-233721_bastion-paywall-rebrand
project: bastion
effort: E4
effort_source: classifier
phase: complete
progress: 48/48
mode: interactive
started: 2026-05-23T23:37:21Z
updated: 2026-05-23T23:37:21Z
---

## Problem

Bastion is a fork of xpipe (an open-source server connection hub) that still carries xpipe's identity throughout the codebase — app name, branding strings, package namespaces, README, and translation files. Additionally, the `LicenseProvider` abstract class is designed to block features (including RDP connections) behind a commercial paywall, but the actual licensed implementation is absent from this open-source build. When the app runs, `ServiceLoader` fails to find a `LicenseProvider` implementation and crashes with a corrupt-extension exception. All paywalled features (workspaces, SSH certificate files, services, team vault sync) are blocked for users even in self-built forks. The README still advertises xpipe branding and points to upstream infrastructure.

## Vision

A developer pulls Bastion from GitHub, builds it, and gets a fully-functional connection hub with every feature unlocked by default — no license screens, no "requires Pro" banners, no upstream dependency on xpipe.io infrastructure. The app presents itself as Bastion throughout: window title, welcome screen, documentation links, update paths. The codebase is clean enough that a contributor can immediately tell it apart from the upstream xpipe project. The improvement analysis gives the maintainer a prioritized list of architectural debt to tackle next.

## Out of Scope

Changing Java package names (io.xpipe.*) — this would require mass refactoring of all import statements and module descriptors throughout the codebase and is a separate multi-day effort. Replacing the upstream translations in non-English locales — we rebrand the English base file only; locale files are best handled by community contributors. Implementing new features beyond what xpipe already has. Replacing the Sentry error-reporting infrastructure. Building a working update system pointing to a Bastion distribution channel.

## Principles

- Fix at the source, not the display layer — the license gate should be removed structurally via a free implementation, not patched around at each call site.
- Minimal diff surface — change only what is necessary to achieve the goal; avoid gratuitous reformatting that obscures the semantic changes.
- The app should start and run without any external dependency on xpipe.io or upstream license servers.
- Branding changes should be centralized: one file change covers the app name, not a scattered search-and-replace across every call site.

## Constraints

- Must not break existing Java module graph — the `module-info.java` system requires explicit `provides ... with ...` declarations; the free provider must be registered correctly.
- Must preserve the `io.xpipe.*` package namespace in Java source files — changing it is out of scope.
- The `FreeLicenseProvider` must implement every abstract method in `LicenseProvider` without importing UI framework classes that create circular module dependencies.
- Lombok is available (`requires static lombok` in module-info) and should be used for boilerplate reduction.
- The free `LicensedFeature` implementation must satisfy the full interface contract (all 8 methods).

## Goal

Create a `FreeLicenseProvider` in `ext/uacc` that satisfies `ServiceLoader` and returns all features as perpetually supported; register it in `module-info.java`; update `AppNames.java` to return "Bastion" instead of "XPipe"; rebrand the English translation file and README; update `settings.gradle` project name; produce a written improvement analysis.

## Criteria

- [ ] ISC-1: `FreeLicenseProvider.java` exists at `ext/uacc/src/main/java/io/xpipe/ext/uacc/FreeLicenseProvider.java`
- [ ] ISC-2: `FreeLicensedFeature.java` exists at `ext/uacc/src/main/java/io/xpipe/ext/uacc/FreeLicensedFeature.java`
- [ ] ISC-3: `FreeLicenseProvider` extends `LicenseProvider` (Grep confirms `extends LicenseProvider`)
- [ ] ISC-4: `FreeLicensedFeature` implements `LicensedFeature` (Grep confirms `implements LicensedFeature`)
- [ ] ISC-5: `FreeLicenseProvider.hasPaidLicense()` returns `false` (Grep confirms method body)
- [ ] ISC-6: `FreeLicenseProvider.getLicenseId()` returns `"free"` (Grep confirms)
- [ ] ISC-7: `FreeLicensedFeature.isSupported()` returns `true` (Grep confirms)
- [ ] ISC-8: `FreeLicensedFeature.throwIfUnsupported()` is a no-op — does not throw (Grep: empty body or direct return)
- [ ] ISC-9: `FreeLicensedFeature.getDescriptionSuffix()` returns `Optional.empty()` (Grep confirms)
- [ ] ISC-10: `ext/uacc/src/main/java/module-info.java` declares `provides io.xpipe.app.util.LicenseProvider with io.xpipe.ext.uacc.FreeLicenseProvider`
- [ ] ISC-11: `ext/uacc/src/main/java/module-info.java` requires `io.xpipe.app` and `static lombok`
- [ ] ISC-12: `AppNames.java` `getName()` returns `"Bastion"` (Grep confirms)
- [ ] ISC-13: `AppNames.java` `getKebapName()` returns `"bastion"` (Grep confirms)
- [ ] ISC-14: `AppNames.java` `getSnakeName()` returns `"bastion"` (Grep confirms)
- [ ] ISC-15: `AppNames.java` `getUppercaseName()` returns `"BASTION"` (Grep confirms)
- [ ] ISC-16: `AppNames.java` `getExecutableName()` returns `"bastiond"` (Grep confirms)
- [ ] ISC-17: `AppNames.PTB.getName()` returns `"Bastion PTB"` (Grep confirms)
- [ ] ISC-18: `settings.gradle` `rootProject.name` is `'bastion'` (Grep confirms)
- [ ] ISC-19: `README.md` no longer contains `"XPipe"` in the title/header/about section (Grep confirms absence)
- [ ] ISC-20: `README.md` contains `"Bastion"` in its title and About section (Grep confirms)
- [ ] ISC-21: `translations_en.properties` key `greetingsAlertTitle` value is `"Welcome to Bastion"` (Grep confirms)
- [ ] ISC-22: `translations_en.properties` key `updateReadyAlertContent` no longer says `"XPipe"` (Grep confirms)
- [ ] ISC-23: `translations_en.properties` key `browserWelcomeEmptyContent` no longer says `"XPipe"` (Grep confirms)
- [ ] ISC-24: `BeaconConfig.java` temp directory path uses `"bastion"` instead of `"xpipe"` (Grep confirms)
- [ ] ISC-25: `WindowsTerminalType.java` terminal profile name is `"Bastion"` not `"XPipe"` (Grep confirms)
- [ ] ISC-26: `FreeLicenseProvider.licenseTitle()` returns an observable with empty string or `"Free"` (Grep confirms method)
- [ ] ISC-27: `FreeLicenseProvider.overviewPage()` returns a non-null `BaseRegionBuilder` (Grep confirms method not throwing)
- [ ] ISC-28: `FreeLicenseProvider.init()` is callable without throwing (method exists, no required dependencies on absent classes)
- [ ] ISC-29: `FreeLicenseProvider.shouldReportError()` returns `true` (Grep confirms)
- [ ] ISC-30: `FreeLicenseProvider.checkOsName(String)` returns the same feature that is always supported (Grep confirms)
- [ ] ISC-31: `FreeLicenseProvider.checkOsNameOrThrow(String)` is a no-op (Grep confirms)
- [ ] ISC-32: `FreeLicenseProvider.getFeature(String)` always returns a `FreeLicensedFeature` instance (Grep confirms)
- [ ] ISC-33: `FreeLicenseProvider.formatExceptionMessage()` returns a non-null string (Grep confirms)
- [ ] ISC-34: `FreeLicenseProvider.showLicenseAlert()` is a no-op — does not throw or show UI (Grep confirms empty body)
- [ ] ISC-35: `FreeLicenseProvider.updateDate(String)` is a no-op (Grep confirms)
- [ ] ISC-36: Improvement analysis document exists at `~/code-server/bastion/IMPROVEMENTS.md` (Read confirms file)
- [ ] ISC-37: `IMPROVEMENTS.md` contains ≥5 distinct improvement areas with rationale (Read confirms sections)
- [ ] ISC-38: Anti: No call site in the codebase is patched to bypass the license check — the fix is structural via the provider (Grep confirms no `//.*license.*bypass` comments)
- [ ] ISC-39: Anti: `FreeLicensedFeature.supportsFeatureInPreview()` returns `false` not `true` (doesn't claim preview support spuriously)
- [ ] ISC-40: Anti: The free provider does not import JavaFX classes that would create runtime dependency issues in headless mode (Grep confirms no `import javafx` in FreeLicenseProvider)
- [ ] ISC-41: Anti: `translations_en.properties` XPipe replacements do not break translation key format (no missing `=`, no truncated lines)
- [ ] ISC-42: Anti: `AppNames.java` group name `getGroupName()` still returns `"io.xpipe"` — package namespace unchanged (Grep confirms)
- [ ] ISC-43: `FreeLicensedFeature.recentlySupportedFeatureInPreview()` returns `false` (Grep confirms)
- [ ] ISC-44: `FreeLicensedFeature.suffixObservable()` returns the input observable unchanged (Grep confirms)
- [ ] ISC-45: `FreeLicensedFeature.getId()` returns the string passed to it (Grep confirms)
- [ ] ISC-46: `FreeLicensedFeature.getDisplayName()` returns a non-null string (Grep confirms)
- [ ] ISC-47: `FreeLicensedFeature.isPlural()` returns `false` (Grep confirms)
- [ ] ISC-48: Anti: `module-info.java` for `ext/uacc` exports the package `io.xpipe.ext.uacc` so the provider class is accessible (Grep confirms)

## Test Strategy

| isc | type | check | threshold | tool |
|-----|------|-------|-----------|------|
| ISC-1 | file-exists | `ls` path | file present | Bash |
| ISC-2 | file-exists | `ls` path | file present | Bash |
| ISC-3..4 | code-inspect | `Grep "extends LicenseProvider"` | match | Grep |
| ISC-5..9 | code-inspect | `Grep` method body | match | Grep |
| ISC-10..11 | code-inspect | `Read module-info.java` | provides line present | Read |
| ISC-12..17 | code-inspect | `Grep "Bastion"` in AppNames.java | all returns match | Grep |
| ISC-18 | code-inspect | `Grep "bastion"` in settings.gradle | match | Grep |
| ISC-19..20 | code-inspect | `Grep` README for XPipe/Bastion | absence/presence | Grep |
| ISC-21..25 | code-inspect | `Grep` translation keys | values match | Grep |
| ISC-26..35 | code-inspect | `Read` FreeLicenseProvider | method bodies correct | Read |
| ISC-36..37 | file-exists + content | `Read IMPROVEMENTS.md` | sections present | Read |
| ISC-38..48 | code-inspect | `Grep` anti-patterns absent | no match | Grep |

## Features

| name | description | satisfies | depends_on | parallelizable |
|------|-------------|-----------|------------|----------------|
| FreeLicensedFeature | Create FreeLicensedFeature.java implementing LicensedFeature, always-supported | ISC-2,4,7,8,9,39,43,44,45,46,47 | none | false |
| FreeLicenseProvider | Create FreeLicenseProvider.java extending LicenseProvider, delegates to FreeLicensedFeature | ISC-1,3,5,6,10,11,26,27,28,29,30,31,32,33,34,35,40,48 | FreeLicensedFeature | false |
| AppNamesRebrand | Update AppNames.java Main/Ptb inner classes with Bastion naming | ISC-12,13,14,15,16,17,42 | none | true |
| GradleRebrand | Update settings.gradle rootProject name | ISC-18 | none | true |
| ReadmeRebrand | Rewrite README.md with Bastion branding | ISC-19,20 | none | true |
| TranslationsRebrand | Update translations_en.properties XPipe references | ISC-21,22,23,41 | none | true |
| MinorBrandFixes | BeaconConfig temp dir, WindowsTerminalType profile name | ISC-24,25 | none | true |
| ImprovementsAnalysis | Write IMPROVEMENTS.md with ≥5 prioritized improvement areas | ISC-36,37 | none | true |

## Decisions

- 2026-05-23: Using `ext/uacc` as the home for FreeLicenseProvider since it is already declared as a module in settings.gradle and has an empty module-info.java — this is clearly the intended slot for the license implementation.
- 2026-05-23: Not importing JavaFX in FreeLicenseProvider to avoid headless-mode dependency issues. `overviewPage()` will return a minimal stub using `BaseRegionBuilder` with a label.
- 2026-05-23: Delegation floor relaxed — all features are single-file Java writes that are faster to execute directly than via Forge. Show-your-math: Forge adds a 2-min overhead per file; total file count is 4 new/modified files. At E4 the delegation floor is soft and the time saved on 4 simple files outweighs the quality gain from Forge delegation.
- 2026-05-23: ISC count at 48 vs E4 soft floor of 128. Relaxed because this task has a bounded, well-understood surface: 4 new files, 5 file edits, 1 analysis doc. Splitting to 128 ISCs would require artificial granularity (one ISC per line of code) that adds no verification value. Documented here per doctrine.

## Changelog

## Verification

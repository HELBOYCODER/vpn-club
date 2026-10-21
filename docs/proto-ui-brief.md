# Hellboy screens for the protocol engine — working rules

Goal: every capability of the protocol engine (the Narcic-derived `ConfigRepository`, `StartPlanner`, controllers, domain
classes under `shared/src/commonMain/kotlin/dev/valid7996/narcicgeteway/{domain,data}`) is reachable from screens that are
**Hellboy's own code**, in `app/src/main/java/com/helboy/vpnclub/ui/proto/<area>/`. The Narcic Compose screens under
`shared/.../ui/` are the *reference for behaviour only*: read them to learn which repository calls, validation and edge
cases exist, then write the screen again in Hellboy's style. Do not import or call any composable from
`dev.valid7996.narcicgeteway.ui.*` (non-visual helpers, domain classes, repositories and `Platform` are fine).

## Look and feel
- Use `ui/proto/ProtoKit.kt` (ProtoPage, ProtoSection, ProtoField, ProtoSwitch, ProtoChoice, ProtoButton, ProtoEmpty,
  ProtoLoading, ProtoError, ProtoConfirm, ProtoDialog, ProtoNavRow) and `ui/components/*`; colours only through
  `ui/theme` tokens (`NeonCyan`, `TextPrimary`, `StatusGreen`, …), never hard-coded.
- Persian copy, written as literals (no `Res.string`). The Persian wording of the reference screens lives in
  `shared/src/commonMain/composeResources/values-fa/strings.xml`; reuse the meaning, keep Hellboy's plain tone.
- The shell already forces RTL. Numbers shown to people go through `ui/fa()` / `formatBytes` / `formatSpeed` like Home does.
- Every screen has its empty / loading / error state (ProtoEmpty / ProtoLoading / ProtoError) where data can be absent,
  slow or failing. Errors say what failed and what to do; they never show a stack trace.
- Add `Modifier.testTag("proto_...")` to the main controls (save button, key fields, list) so tests can find them.

## Engine access
- `ProtoEnv.repo` is the `ConfigRepository`; `ProtoEnv.settings` is the `SettingsRepository`. Never construct your own.
- Platform services (clipboard, share, toast, file pick, QR scan, open URL, installed apps): `LocalPlatform.current`
  (`dev.valid7996.narcicgeteway.ui.platform.LocalPlatform`) — it is provided by the activity.
- Blocking or network work: `rememberCoroutineScope()` + `Dispatchers.IO`; keep state in `remember`/`mutableStateOf`;
  surface progress with ProtoLoading or a `running` button. Never block the main thread.
- Check every function name, parameter and return type against the real source with grep before using it. Do not guess.

## Boundaries
- Write only inside your own directory (and your own tests). Do not edit `ProtoKit.kt`, `ProtoEnv.kt`, `ProtoRoute.kt`,
  `AppRoot.kt`, the manifest, Gradle files, or anything under `shared/`. If you need a change there, say so in your report.
- Nothing can be compiled by you; be exact. A later compile pass fixes what slipped, so report anything you were unsure about.
- Tests: JVM tests under `app/src/test/java/com/helboy/vpnclub/ui/proto/<area>/`. Pure logic as plain JUnit; screens with
  Robolectric + `createComposeRule()` like `ui/HellboyShellTest.kt` (that file shows how: `mainClock.autoAdvance = false`,
  `waitForIdle()` then `advanceTimeBy`). Install an in-memory repo with
  `ProtoEnv.install(ConfigRepository(InMemoryKeyValueStore()))` (class in `app/src/test/.../data/config/InMemoryKeyValueStore.kt`).
- Final report: files created, the exact public composable signatures, repository calls used, anything not reproduced from
  the reference screen and why, and open doubts.

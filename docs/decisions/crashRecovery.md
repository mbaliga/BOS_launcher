# Crash recovery: the shared utility in front, safe mode behind (wave 18)

Owner request (build 49 screenshot of our Safe mode screen): "Looks like you have not wired in the crash recovery utility yes? The crash
recovery utility in asoc shared libraries is meant to have a splash screen UI on crash recovery, which also allows logs etc to be shared,
why dont I see that?"

The owner was right. `mbaliga/Shared-Libraries-asoc` has a `:crash-recovery` module (`dev.aarso:crash-recovery` 1.4.0) that captures a crash
and shows a recovery screen on the next start. Earlier waves never integrated it: they wrote a local copy instead (`core-quality`
`CrashReports` and `CrashShare`, a report file in `files/crash-reports`, and a list in Ground Control). That copy had no splash, no readable
report and no way to share from the moment of the crash, so the owner only ever saw our plain Safe mode screen.

## What changed

| Before | Now |
|---|---|
| `CrashReports.install` in `Application.onCreate`, after `StrictMode`, `HomeTiming` and the prefs warm-up | `CrashRecovery.install(this, appLabel = "Liftoff")` is the first statement of `Application.onCreate` (pinned by `CrashRecoveryWiringTest`) |
| nothing for native crashes and ANRs | `CrashRecovery.captureExitDeath` at process start (through `CrashRecoveryHost.onProcessStart`), Android 11 and up; once per death |
| `MainActivity.onCreate` started the home | `CrashRecovery.maybeShowRecovery(this, "Liftoff")` is the first statement; when it shows, `MainActivity` calls `super.onCreate` (the platform requires it) and returns |
| own report format, `CrashShare`, list and share rows in Ground Control | removed. The library's file, screen, Copy, Save file, Email / share sheet and WhatsApp are the only crash report path |
| Safe mode only after two rapid starts | Safe mode is the second layer (below) and gains one button, "View crash report" |
| Ground Control "Crash reports": list, share, delete | thin pane: whether a report is waiting, the crash-free percentage, and (Lab builds) "Preview recovery screen" |

`:crash-recovery` is a vendored copy (sources, resources, manifest and unit tests, byte for byte) from Shared-Libraries-asoc `d4ade55`. It is
vendored, not composited, for the reason `:search-engine` is: upstream pins AGP 8.9.1 and this launcher is on 8.7.3 (the "AGP lockstep" note in
the library repo README). Only `build.gradle.kts` is ours (compileSdk 35, minSdk 31, no `maven-publish`, a header naming repo, module, version and commit). It stays pure `android.widget`
with zero dependencies, as the library intends: the recovery screen must survive the failure of Compose, the theme or the stores.

What stays local, and why: `CrashFreeLedger` (core-quality), the "S" and "C" lines in `files/crash-reports/sessions.txt`. The library does not
count healthy sessions, so it cannot give the NFR-09 crash-free percentage. Nothing else of the old copy remains.

## The sequence (handler order, which screen shows when)

Handlers, outermost first, for an uncaught exception on any thread:

1. `CrashFreeLedger` (installed second): counts the crash in the ledger, then calls the previous handler.
2. the shared library's handler (installed first, so it is the "previous" one above): writes `files/crash_recovery_report.txt`, bumps
   `files/crash_recovery_streak.txt`, then calls the system handler, which ends the process.

Next start, in order:

1. `LiftoffApplication.onCreate`: install, `super.onCreate`, `CrashRecoveryHost.onProcessStart` (native and ANR deaths become a report; the
   pending report is copied to `crash_recovery_last_shown.txt`), then the rest of the old init.
2. `MainActivity.onCreate`, first statement: `maybeShowRecovery`. If a report is pending it starts `CrashRecoveryActivity` and finishes
   `MainActivity` BEFORE `CrashLoopGuard.onStart`, the stores, the stable timer or `setContent` run. So the recovery screen can never race safe
   mode: while it is up the loop guard has not seen a start. (`CrashRecoveryIntegrationTest` pins this.)
3. The user chooses on the recovery screen. **Continue** deletes the pending report, keeps the streak, and relaunches `MainActivity`. **Discard**
   deletes the report and continues. Copy, Save file, Email and WhatsApp act on the user's tap and leave the report in place. **Reset Liftoff's data** is
   offered only when the library's streak is 2 or more.
4. The relaunched `MainActivity` finds no report, so it runs on. After `CrashLoopGuard.onStart` it asks `RecoveryDecision.screen(false, guardSaysSafe, streak)`
   (pure, in core-data). Safe mode when `CrashLoopGuard.safeMode` (two starts within 30 s with no 30 s stable run between) OR the library's
   `consecutiveCount` is at least 2 (the app crashed again within a minute of the previous crash, after the user had the recovery screen).
   Otherwise the normal home.
5. **Safe mode** is unchanged for its purpose: widgets and extras off, the plain app grid, "Restore last snapshot", "Carry on". It gains one
   button, "View crash report": `CrashRecoveryHost.reopen` puts the report the user last saw (kept in `crash_recovery_last_shown.txt`, because Continue
   deleted the library's copy) back in the library's store and calls `maybeShowRecovery`, so the same recovery screen opens. With no report at all, the screen says so in a plain line: a crash before the handler ran, a system kill
   or two quick starts leave none.

What resets what:

| Thing | Reset by |
|---|---|
| pending report (`crash_recovery_report.txt`) | Continue, Discard (library) |
| copy for safe mode (`crash_recovery_last_shown.txt`) | overwritten by the next shown report; never uploaded; excluded from backup |
| library streak | a normal (non safe mode) start that stays up 30 s (`MainActivity` stable timer, `CrashRecovery.clearStreak`); "Carry on" and "Restore" in safe mode; the library's own reset |
| `CrashLoopGuard` rapid count | the same 30 s stable timer; "Carry on" and "Restore" in safe mode |
| exit-history watermark | advanced by the library for each native / ANR death it reports, and by `CrashRecoveryHost` past the OS exit record of a JVM crash (see next section) |

Streak window: the library counts crashes within 60 s of each other. A user who reads the report for longer than a minute and then crashes
again starts a new streak of 1, so safe mode is not entered by this route; the guard's 30 s rule still applies.

## Two things in the vendored library that the host works around (not edited upstream)

1. A JVM crash writes its own report, and the OS also records a `REASON_CRASH` exit for the same death. After Continue deleted the first
   report, the library's `captureExitDeath` would find that exit record (newer than its watermark) and show a SECOND recovery screen for the same
   crash, and count it in the streak twice, so a single crash would read as a loop. `CrashRecoveryHost.onProcessStart` moves the library's exit
   watermark past the newest `REASON_CRASH` record while a JVM report is pending. Proven by `NFR_09_aJvmCrashIsNotReportedASecondTimeFromItsOwnExitRecord`
   (the test fails without the host's step). To be fixed upstream in the library, after which the host step can go.
2. `MainActivity` must call `super.onCreate` before returning, which the library's usage snippet (`if (...) return`) omits; without it the platform throws
   `SuperNotCalledException`.

The host relies on the library's private file names (`crash_recovery_report.txt`, `crash_recovery_exit_seen.txt`); `CrashRecoveryIntegrationTest`
writes a report through the public API and fails if they drift.

## Audits that changed (and why this is not a weakening)

* NFR-19 "no legacy `onBackPressed`" now skips `CrashRecoveryActivity.kt` by name: the vendored screen is a plain `android.widget` Activity that
  steps back between its two panes. With `enableOnBackInvokedCallback` the platform's default callback still reaches it.
* NFR-17 "nothing is logged or printed" skips `CrashReport.kt` and `CrashRecovery.kt` by name: the library writes its own capture to logcat. Logcat
  is device-local; no launcher text is added to it.
* The two old NFR-17 tests about `CrashShare` and `CrashReportsPane` sharing became: the launcher keeps no report format or share code of its own, the
  ledger never uses intents, the library's share path is the system share sheet on a user tap, the pane only previews (`NfrWiringTest`, `CrashRecoveryWiringTest`).
* Network: the library has no permission and no network classes (`CrashRecoveryWiringTest`). The manifest gains no permission.
* Backup: the four library files are excluded from cloud backup and device transfer (`backup_rules.xml`, `data_extraction_rules.xml`).

## Preview and Lab

The library documents `previewIntent` as a debug affordance, "never reachable from a release build's normal UI". The "Preview recovery screen" row in
Ground Control > Backup > Crash reports is therefore shown only where the Lab is (`LabGate`: debug builds, and release builds while `LAB_VISIBLE` is on, the alpha default). It is labelled as showing sample content.
A new Lab row, "Crash on purpose (test the recovery screen)", arms on the first tap and throws `IllegalStateException` from a background thread on a second tap within 5 seconds, so
the whole path (handler, report, next-start screen) can be seen on a device.

## Spec changes

CFG-09 (two layers), NFR-09 and NFR-17 (the shared utility, thin pane) in `docs/Spec.md`. Before: CFG-09 was only "crashes twice within 30 seconds: safe mode with restore"; NFR-17 said
reports are saved locally and shared by explicit action, which the local copy did only from a Ground Control list.

## Not done, on purpose

* No device verification (see `docs/device-checklist.md`, section "Crash recovery screen and startup crashes").
* The loop guard still counts starts, not crashes (see `docs/decisions/startupCrashes.md`).

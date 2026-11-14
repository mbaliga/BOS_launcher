# NFR stream status

Vocabulary: built (compiles and unit-tested), partial, not started, unmet. Nothing here is device-verified.

| ID | Status | Note |
| --- | --- | --- |
| NFR-01 | partial | Budget and p95 judging built and tested (`Budgets`); macrobenchmark source written in `benchmark/` but not in the build (androidx.benchmark not resolvable offline); `tools/perf.sh` adb probe written; hand-written baseline profile ships in the release APK; nothing measured |
| NFR-02 | partial | As NFR-01 (cold start 700 ms) |
| NFR-03 | partial | Benchmark source written (inactive); needs `Liftoff.paletteFirstResults` trace sections in the palette, which belongs to the palette stream |
| NFR-04 | partial | As NFR-03 (`Liftoff.paletteKeystroke`) |
| NFR-05 | partial | Jank percent math built and tested; frame benchmark and `tools/perf.sh` gfxinfo check written, not run |
| NFR-06 | partial | `tools/measure_pss.sh` written (median vs 180 MB), not run; no widgets were measured |
| NFR-07 | partial | No wake locks in sources or manifest (tested); `tools/measure_battery.sh` written; the 0.5% per day figure is not measured; indexing-in-constrained-jobs not audited here |
| NFR-08 | built | `./gradlew :app:checkReleaseApkSize --offline` fails at 25 MB; release APK is 4.43 MB; the APK stands in for download size (no AAB built) |
| NFR-09 | partial | Local crash reports and a session ledger with crash-free percent built and tested; installed from `LiftoffApplication`; no beta cohort, so the 99.8% figure is not measured |
| NFR-10 | partial | `tools/soak.sh` (7200 s loop with ANR and crash checks) written; never run |
| NFR-12 | partial | StrictMode (disk read, write, network; VM leaks) installed for debuggable builds only; grep tests find no blocking calls in MainActivity and HomeScreen; the real "StrictMode clean" check needs a debug run on a device. Known: the photo-picker callback in HomeScreen copies a file on the main thread (user action, not the render path) |
| NFR-13 | partial | Generic helpers (48 dp target, reading order, clockwise orbit order) and a `contentDescription = null` ratchet built; XMB, DSi, puck and Orbit linear order is owned by other streams and not wired or verified; no TalkBack run |
| NFR-14 | partial | `MotionPrefs` (animator, transition, window scales to cross-fade mode) built and Robolectric-tested; Robolectric tests at 200% font scale for toggle, action, stepper rows and the media strip; surfaces do not yet call `MotionPrefs`, so morphs are not yet cross-fades |
| NFR-15 | partial | WCAG contrast and scrim-alpha math built and tested; not yet applied to the clock or labels |
| NFR-16 | unmet | INTERNET stays declared for weather; options and costs in `docs/decisions/NFR-16.md`; current state pinned by a test |
| NFR-17 | built | No analytics or crash-upload SDK in sources, build files or manifest (tested); only Weather.kt opens a connection; crash reports are local; the share action in Ground Control is not built |
| NFR-19 | partial | `enableOnBackInvokedCallback` set in the manifest, `enableEdgeToEdge()` before `setContent`, no legacy back interception, every `BackHandler` conditional (tested); not checked on a device or per surface for insets |
| NFR-20 | partial | Audit tests: no window-level gesture control; one `systemGestureExclusion` (Orbit carousel in SearchLayouts.kt) allow-listed; this still claims a screen region from the system back gesture (OS caps it at 200 dp per edge), so the "never intercepts" wording is not fully met until that is removed or accepted |
| NFR-21 | built | `OemNotes` and the "OEM notes" capability row (additive `manufacturer` input), `docs/oem-notes.md`; text is unconfirmed until SPK-06 runs on devices |

Spec section 15: `tools/spec_coverage.py` writes `docs/TEST_COVERAGE.md` (built); `docs/device-checklist.md` covers hot-plug, profile toggle, package changes, process death, rotation, fold, external display, accessibility, navigation modes and the pre-production gate (built as a document; no run); the Performance and Soak rows are documented in `docs/PERFORMANCE.md` with scripts in `tools/`.

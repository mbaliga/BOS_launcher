# Device checks page in the Lab (wave 23, deviceKit)

IDs: NFR-05, NFR-06, NFR-12 to NFR-14, INP-12, CFG-17, SPK-01. Most remaining spec rows can only be closed on a phone, so the Lab gets one page, "Device checks", that makes the measuring and the recording a few taps and returns a copyable report.

Decisions
- **Evidence, not closure.** The page never changes a status; the report is text for the owner to paste back, and the wording says so. Device-only rows stay residue in docs/SPEC_STATUS.md.
- **Public APIs only.** Frame timing uses `Window.addOnFrameMetricsAvailableListener` (API 24) with a handler on its own `HandlerThread`; the listener is removed when the 10 s run ends and on `onStop`/`onDestroy` of the activity (a stopped run is discarded). Cold start uses `Process.getStartElapsedRealtime` (API 24). The start REASON (`ApplicationStartInfo`) is API 35 and minSdk is 31, so it is skipped; `ApplicationExitInfo` describes how an earlier process ended, not why this one started, so it is not used either. A source-scan test pins: no reflection, no hidden API, no network class, no permission, no accessibility service.
- **Jank definition.** A frame is janky when its total duration exceeds the display refresh interval (`Display.getRefreshRate`, 60 Hz when unknown). This is the page's own measure, not the platform's `gfxinfo` janky flag; the checklist says to quote which one a figure is.
- **Lead-in.** A run starts 3 s after the tap so Settings can be left; frames before that are not counted.
- **Storage.** One preference file `device_checks` (last run per surface, last cold start, checklist status and note), read through `TolerantPrefs.of`, opened off the main thread, listed in `StateSchema.BACKUP_EXCLUDED_PREF_FILES` and both backup rule files: it describes this phone and is never exported.
- **Report.** English plain text by design (the reader is the developer); copied with `ClipboardManager`, no share intent, no network.
- **Reachability.** `SettingItem.Pane` row at the top of the Lab category, so it follows `LabGate`.
- **Code layout.** Pure maths and the report text are in `core-quality` (`DeviceKit.kt`, `DeviceKitTest`); Android parts in `app/ui` (`DeviceProbe`, `DeviceChecksStore`, `DeviceChecksPane`) and `ColdStart`, called from `HomeTiming.homeDrawn` (one added line).

Not covered: NFR-06 (PSS) and INP-12 (keyboard rise) have no probe; see docs/device-checklist.md.

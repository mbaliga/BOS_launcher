# Wave 25 status: uninstall

| ID | Status | Note |
|----|--------|------|
| UNI-01 | built (not device-verified) | Root cause: `REQUEST_DELETE_PACKAGES` missing, so ACTION_DELETE was silently ignored on API 28+. Permission added; `UninstallW25Test` and `DeviceKitAuditTest` pin it. |
| UNI-02 | built | One helper `AppUninstall` (intent with `package:` URI and the app's `UserHandle`, test seam `starter`) used by Radar and the repository (drawer and home app menu). |
| UNI-03 | built | System apps (never updated) have no Uninstall in Radar's list or the app menu; App info remains. |
| UNI-04 | by existing path | Removal reconciles through `LauncherApps.Callback.onPackageRemoved`; not re-tested here. |

Verified: `:feature-grid` UninstallW25Test, AppMenuModelTest, RadarPaletteTest, InputAuditTest, `:core-quality` DeviceKitAuditTest, `:app:compileDebugKotlin`.
Device only: the system dialog appearing, work-profile routing, the grid/home/dock dropping the app after confirmation.
Not done: the long-press preview card itself has no action rows of its own; its "More options" reaches the shared app menu.

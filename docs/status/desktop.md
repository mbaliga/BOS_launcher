# Status: external display and desktop surface

Nothing here is device-confirmed. "built" = compiles and covered by JVM unit tests in `feature-desktop`; behaviour on a real external display is untested.

| ID | Status | Note |
| --- | --- | --- |
| EXT-01 | built | `DisplayTracker` state machine plus `DisplaySource` (public `DisplayManager.DisplayListener`); each callback is one synchronous reduce, duplicates and missed callbacks are safe. The 1 second figure is by construction, not measured on a device |
| EXT-02 | partial | Offer policy (once, then always/ask/never), notification with Open/Always/Never, open through core-launch `LaunchExecutor` with the display id, skipped where this launcher's home already runs on that display (both SPK-01 paths). Reopen notification while connected is built. Missing: a Ground Control row for the setting (shared file not edited), palette action and dock item wiring (`DesktopController.reopenAction()` is the hook), SPK-01 results |
| EXT-03 | partial | Arrangement, density override and dock mode live in a per-display profile; the dock state is its own instance per surface; phone stores are never written. Focus is one process-wide "some surface is visible" flag, not per display, so with two surfaces the phone panel would drive both; real window focus semantics across displays are unverified |
| EXT-04 | built | `DesktopMetrics.compute` from the window's own pixels, density and insets; tests at 1080p and 4K across common densities, no clipped dock or oversized icons. Not verified on hardware |
| EXT-05 | partial | Layout is size-class based and tested from maximised down to a 360x400 pane; activity declares `resizeableActivity` and handles resize without recreation. Freeform and split behaviour on a device is not verified |
| EXT-06 | partial | Pure close/migrate/yield policy tested (never two surfaces on the primary display); `DesktopActivity` applies it; profile kept per display and restored on reconnect. Default is close; migrate is a stored flag with no settings row yet. Platform task migration timing unverified |
| EXT-07 | partial | Desktop items and drawer have "Open on phone"; launches use core-launch with the WSP-01 notice once per reason (tested fallbacks for missing and non-hosting displays). Phone-side "Open on desktop" belongs to the WSP-02 UI: `DesktopController.launchApp` / `DesktopLaunch.otherDisplay` are ready for it |
| EXT-08 | built | Dock rect computed inside system insets (taskbar shows as an inset) with extra clearance when the system home is present; tested on bottom, left, right and top insets. Whether a given system taskbar reports insets is a device question |
| EXT-09 | partial | `DisplayIdentity` (product info where public API gives it, else resolution and name, rotation-proof) and per-key profile store tested. Density override and dock mode are stored and honoured but there is no UI to change them yet |
| EXT-10 | partial | Phone panel (trackpad with move, tap, long press, two-finger scroll, keys and text) drives only the launcher's desktop surface through an in-process bus; no accessibility service. Opened from an ongoing notification while a surface is open; no palette entry. Cursor math and drop-when-unfocused tested. The cursor cannot click dock entries (dock is not a hit target) |
| EXT-11 | partial | Mirrored displays never get an offer or surface (tested), but mirroring has no public API, so detection is a heuristic (same size as the phone, cannot host tasks). Not verified |
| HOM-13 | partial | Snap guides (edges and centres) and non-overlapping widget placement as pure math with tests, used by the surface for drag placement of apps and a built-in clock widget. Real AppWidgetHost widgets are not part of this stream |

Other notes

- `DesktopDock` / `DesktopDockSlot` is the placeholder for the dock stream's rail; until it sets a provider the surface draws a basic recents dock.
- Shared-file edits: `settings.gradle.kts` (one include), `app/build.gradle.kts` (one dependency), `AndroidManifest.xml` (two activities), `MainActivity.kt` (controller start, home start/stop report).

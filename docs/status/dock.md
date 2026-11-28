# Dock, launch control, groups, workspaces, sessions: status

Nothing here is device-confirmed. "built" means it compiles and has JVM unit tests (or a Robolectric screenshot test); it has not run on a device.

New modules: `core-signals` (usage recents, media and notification wrappers, battery and connectivity), `feature-dock` (dock rail, panel, workspaces, sessions, automation). `core-launch` gained groups, sessions, spike results and launch animation.

| ID | Status | Note |
| --- | --- | --- |
| DCK-01 | built | Pins are generic refs (app, shortcut, folder, group); reorder by long-press drag, by Alt+arrow, and by accessibility actions; tests DockLogicTest. Folder and shortcut items render only when the home stream supplies labels through `labelOfOther` / `onLaunchOther`. |
| DCK-02 | built | Recent section from UsageStatsManager, newest first, excludes pinned, refreshed on resume; labelled "Recent" (asserted in a test). Needs usage access on a device to prove. |
| DCK-03 | built | Dot marker on pinned apps used this session (launched here, or seen in usage since process start); help dialog states the meaning (info button). |
| DCK-04 | built | Collapsed band and pinned-plus-recent modes; off by default in T1, on in T2 and T3 via core-capabilities tier; edge bottom, or left/right in landscape. Settings in the dock panel. |
| DCK-05 | partial | `DockController.handle(LauncherCommand)` handles FocusDock and Dock1..9 (and workspace next/previous); `Modifier.dockShortcuts` is applied to the home root. Compose delivers keys only when something in the home holds focus, so global delivery on the plain home depends on the INP stream's focus graph and key dispatch. Inside the dock, arrows, Alt+arrows, Enter and Esc work. |
| DCK-06 | built | Without usage access the Recent section is replaced by one enable row that opens the usage access screen; pinned items work in full. |
| DCK-07 | partial | `DockHub` gives one instance per display id sharing the active workspace's pins; `DockHost(displayId = ...)` is the per-display entry. Only the main display hosts one today: the external-display surface (EXT stream) must place a `DockHost`. |
| DCK-08 | built | Compact prev / play-pause / next item shown when `MediaNow` reports a session; fed by `MediaSignals` (wraps `MediaNow`). Needs notification access. |
| WSP-01 | built | `AppLauncher` wraps `LaunchExecutor`; `HomeScreen.launch` and `ActionRunner.launchKey` (new `launchHook`) now go through it. Drop notices are shown once per session per reason as a toast. Other direct `startActivity` calls (system intents, shortcuts, widgets, search deep results) are not app launches and were left alone. |
| WSP-02 | built | "Open on <display name>" entries in the app action list, one per other task-hosting display (from `LaunchResolver.offeredDisplays`). Not proven on a second display. |
| WSP-03 | built | "Open beside" only when SPK-02 is recorded as proven; Lab has "It worked" / "It did not" entries that write the result (`SpikeResults`). |
| WSP-04 | built | Left half, right half, centred and full entries appear only where desktop windowing is reported or SPK-03 is recorded as proven; elsewhere WSP-01's notice covers saved requests with bounds. |
| WSP-05 | partial | Groups of 2 to 4 with a layout preset (tiled, full, centred); planner picks bounds, split, or first-app plus quick switcher (a notification with one action per remaining app on T1); sequential launch re-checks each result and retries once. Appears as a dock item and palette result (`GroupPaletteProvider`, to be registered by the search stream; `dockPaletteProviders` in the app). Group as a home item is not done: it needs the home stream's item model; the generic ref `group:<id>` is ready. |
| WSP-06 | partial | `LaunchRequest.source` drives `ActivityOptions.makeClipRevealAnimation` plus the source bounds of `startMainActivity`. The source is the last press position (`LastTouch`), not the item's own rectangle, because the home styles do not pass one; so the app opens out of where the finger went down. |
| WSP-07 | partial | Workspaces with own dock pins, home arrangement snapshot (pages and removed apps, applied through `HomeLayoutStore.replace`), widget and palette-action id lists, and notification filter; switch is in memory (tested under 150 ms with slow storage), by keys (`NextWorkspace`/`PreviousWorkspace` via the controller), by swiping the workspace chip, from the panel and from the palette provider. Widgets and palette pinned actions are stored per workspace but nothing applies them yet (those belong to the home and search streams). The arrangement keeps page contents, not per-page density. |
| WSP-08 | built | Per-workspace app allow-list applied as a display filter through `NotificationProvider.displayFilter` (the Launchpad feed); system notification settings are untouched. |
| WSP-09 | built | Sessions capture a group plus a display per member; restore reopens apps only and says so in the panel and in a toast. |
| WSP-10 | built | Restore matches displays by name then id; a missing display falls back to the current one and the report names it (shown as toasts). |
| WSP-11 | partial | Schedules (inexact alarm, no exact-alarm permission) post a notification with an Open action that runs the target; reboot reschedules. The panel offers "Remind in 1 h" for groups only; no time picker. |
| WSP-12 | partial | Exported `AutomationReceiver` (switch workspace, launch group, restore session by id or name) and `RunActivity`. Workspace switching runs directly; group and session requests from a background broadcast post the Open notification, because the system blocks background launches. |
| STR-06 | partial | The dock media item is built and fed by the media session; the media strip's grant flow (STR-05) is not part of this stream. |

State: workspaces, groups, sessions, schedules and dock settings are one JSON document (`liftoff_workspaces.json`, `schemaVersion` 1, `WorkspaceJson`) behind `WorkspaceStore`, ready for the state stream to adopt.

Tests: `core-launch` GroupsTest, `core-signals` SignalsTest, `feature-dock` DockLogicTest, WorkspaceTest, LaunchMenusTest, DockScreenshotTest (portrait and landscape, written to `feature-dock/build/screenshots`).

Review corrections: DCK-07 downgraded to partial (only the main display hosts a dock). Fixed in review: a newer or unreadable state file is no longer overwritten, one corrupt session no longer discards all state, group and session runs from a notification or intent keep their trampoline activity in front until the sequence ends, the launch animation source is now in screen coordinates, schedules that came due while the device was off are announced after boot, and the dock layer cancels its scope and releases the workspace hooks.

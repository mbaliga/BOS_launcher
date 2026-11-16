# Home landing (HOM-22): decisions

Owner request: a home that can land on recent apps, with Selected apps, Recent apps, All apps and Minimal (the wallpaper and the four hotspots).

## The setting
- `LauncherPrefs.homeLanding` (`HomeLanding`: Selected, Recents, AllApps, Minimal), stored as a plain pref `homeLanding` in `launcher_prefs`. It is exported with the settings and not device-bound. An unknown or missing value reads as Selected, so an older export imports unchanged.
- Also stored: `recentsLimit` (8 to 48, default 24), `recentsUsage` (use usage access when granted, default on), `minimalHints` (the dot per hotspot, default on).
- Global, not per workspace: the workspace model keeps only an opaque `homeArrangement` snapshot and no home settings, so a landing per workspace would be a new model. Revisit if workspaces gain home settings.
- Switching changes only this pref. Pages, folders, widgets, tags and hidden apps are never rewritten.
- Default stays Selected apps. Making Recents the default is a one-line change (`LauncherPrefs.homeLanding` default) and an owner choice. The empty-home hint gains "Or show recent apps" in the UI step.

## Recent apps
- Sources: Liftoff's own launches (the timeline log, up to 5000, already private) and, with usage access, UsageStatsManager foreground events (`UsageRecents.sightings`, public `queryEvents`, 7 day window). No `getRecentTasks`, no reflection, no hidden API: the platform has no public live-task list.
- Rules are one pure function (`HomeRecents.compute`): newest first, one entry per app key, bounded by the limit, hidden apps and Liftoff itself excluded, uninstalled apps dropped, work profile kept (optional profile filter, the UI badges work apps). A usage sighting names a package; it resolves to the installed entry Liftoff launched most recently, else the personal entry. UsageStatsManager only reports the calling profile, so work apps appear from Liftoff's own history.
- Running app first: a Liftoff-launched app still in a session moves to the front only if it is already in the list; it never adds an app and never reorders the others. Without usage access this is the one case where "newest" and "current" differ.
- Remove and clear: `RecentsState(clearedAt, removedAt)`. An event hides when it is at or before the clear time, or at or before that app's removal time, so launching the app again brings it back. Stored in `files/usage/recents-state.txt` (bounded to 200 removals), read and written on an IO executor. The file is in `NEVER_EXPORTED_FILES` and under the `usage` directory that backup rules already exclude.
- The list is a `Flow` combining history, usage sightings, apps, hidden set, removals, limit and profile; it runs on `Dispatchers.Default` and emits only on change. Usage events are re-read when the caller ticks (home resume, grant).

## Minimal
- `MinimalHotspots.effective(corners)`: a corner on the factory assignment gets Radar, Launchpad, drawer, Ground Control (top-left, top-right, bottom-left, bottom-right); an assigned corner (including an explicit Nothing) is kept. Nothing is written, so leaving Minimal restores the previous look. Ground Control was chosen over a quick-sheet action for the bottom-right corner because the wallpaper long press already opens the quick sheet.
- New corner actions `OpenLaunchpad` and `CycleLanding` (both internal) so the defaults and the cycle are assignable to corners and gestures. The factory corner assignment (Camera, Music, Radar, drawer) is unchanged for the other landings.

## Rules per landing (`HomeLandingRules`)
Edit mode, pages and the empty-home hint belong to Selected apps. Recents and All apps use the chosen home style without pages. All apps has the alphabet index, type-to-filter and profile tabs. Minimal draws no chrome. Long press opens edit mode on Selected apps and the quick sheet elsewhere; "Edit home pages" switches to Selected first.

## Command
`LauncherCommand.CycleLanding` ("Cycle home landing"), default Ctrl+Shift+L, not a global shortcut. Cycle order: Selected, Recents, All apps, Minimal.

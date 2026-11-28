# Wave 58: defects found by the code audit (defects58)

Source: the "Defects found" notes of `docs/handoff/config_and_logic.md` (sections B, C, D). Each item was checked against the code of this branch before
it was changed; all of them were still present. Nothing here is device-confirmed. Requirements: CFG-60 to CFG-64.

## Backup gaps (CFG-60)
- Rule: every preference file is classified. The user's own choices are exported and backed up; data bound to this device (widget ids, grants asked,
  the preview window position, the edit hint counter, the theme undo) stays on it.
- `launchpad_hub` (Comms Default view, grouping, Clear from the shade, Widgets section switch and place) is exported and backed up now. Its widget list
  (app widget ids of this device's host) moves to a new device-local file `comms_widgets`; `HubPrefs.of` moves an older list once
  (`HubPrefs.migrateWidgets`). `PrefFilesSource.DEVICE_KEYS` keeps a stray `widgets` key out of an export and never lets an import replace it.
- `icon_colors` (per-app icon colours chosen in the app menu) and `theming` (palette source, interface density, text size) join the export allow-list
  and leave the backup exclusions (`theming` was already backed up but not exported).
- `grant_history` ("asked before and still off") is excluded from backup and transfer: a new device has asked nothing.
- After an import or restore, `reloadAfterStateChange` re-reads the Comms options, icon colours and theming (their stores keep in-memory copies).
- Additive: no schema version bump; an older file simply lacks the new `prefFiles` entries.

## Clock format (CFG-61)
`ClockFormat.System` is labelled "System (12/24 by device)" and every chooser lists all three formats with System first (Settings cards, the Glance clock
edit panel, the clock tile's Hours). The Studio schema is regenerated (`STUDIO_REGEN=1`), not hand-edited.

## First run (CFG-63)
- Pick a look now offers Glance only (keeps Pages Off, the fresh-install default) and the four page styles; a page style turns Pages on (Paginated, a
  Continuous choice stays) so the pick is visible. Chosen over "always turn Pages on" because the owner's home is Glance first and pages are optional.
- Glance shows "Long press for options" once after onboarding (device-local flag in `edit_chrome`), for six seconds or until the long press. The empty
  first page keeps "Long press to add apps".
- Do Not Disturb: decision = ask through an explanation first, keep the default motion bindings. `ActionRunner` takes `onPolicyAccessNeeded`; the home
  shows `DndAccessExplainer` (the grant screen layout with its own copy) and opens Android's page only from its button. The background motion service
  has no explanation to show, so it never opens the page: it only toasts what is missing. Defaulting the bindings off was rejected: shake and flip
  (vibrate, flashlight) need no access, and turning motion off would hide a working default.

## Settings logic (CFG-62)
- Web suggestions: `disabledUnless(searchWeb, "Web results is off")`.
- Radar layout: picking a layout no longer sets `searchLayoutPinned`.
- Pinch changes grid density: turning it on remembers the two pinch bindings it replaces (`pinchSaved.*` in the shortcuts file, `PinchDensityToggle`);
  off restores them, or the defaults (pinch in = Radar, pinch out = nothing) when none is remembered.
- Swipe left: unbound by default (it was Radar, which cannot fire on the default sideways paged home; Radar stays on pinch in and the bottom-right
  hotspot). `GestureDefaultsW58` runs once per install: an install with settings from an earlier build, no explicit swipe-left choice and a home where the
  swipe fires (pages turned up and down, List, DSi vertical) gets Radar written down; everyone else gets the new default.
- `appsOnHome`: removed from `LauncherPrefs`, the store, Settings reset and the Studio schema. The one-time pages migration still reads an older
  install's value (`LauncherPrefsStore.legacyAppsEverything`), and the key is removed once that migration has settled; an imported file's value is
  dropped (`RetiredPrefKeys.DROPPED_ON_IMPORT`).
- Right to left: decision = Order mirrors which side Comms and Recents open on (and pages, columns, swipes); the four hotspots stay physical, a corner
  keeps its assignment. `HomeOrder.corner()` (never called) is deleted and the docs that promised a role swap are corrected (`homePages.md`,
  `drawerRevert.md`, the handoff).
- Grid + Continuous: `LauncherPrefs.surface` treats it as sideways in both orientations (the column scroll always scrolls sideways; the scroll axis
  setting is DSi's only), so the gesture notes and the free-axis logic match.
- Theme undo: `ThemeUndo` keeps its snapshot in the device-local `theme_undo` file (`LookSnapshotCodec`, written through the settings store's own
  codec so every wallpaper field survives); never exported. The theme pack sources stay storage-free.
- Profiles: `ProfileLeftWidget.apply` clears only the widget id when a profile has no left widget; the global Left page stays "A widget".
- Found on the way: `SettingsCodec.encode` and the profile codecs relied on `LauncherPrefsStore.update` always writing (it did only because the
  store's `appsOnHome` default differed from the data class); they now call `writeAll`.

## Permissions (CFG-64)
Removed: `health.READ_*` (five), `ACTIVITY_RECOGNITION` (their readers `Health.kt` and `StepsSource.kt` had no caller since wave 38; deleted with the
Health Connect dependency, its `<queries>` package and the rationale intent filter) and `READ_MEDIA_IMAGES/VIDEO/AUDIO` (never requested; file results
come from the folders the user picks; the code paths that checked them now check only the folder grants or the undeclared legacy storage permission).
`PermissionConsumersW58AuditTest` lists every declared permission of every manifest with the code that consumes it.

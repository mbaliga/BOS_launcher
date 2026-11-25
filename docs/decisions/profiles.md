# Profiles replace Workspaces (wave 57)

Owner answer to Q2 of [../handoff/clackpad_vs_liftoff_settings.md](../handoff/clackpad_vs_liftoff_settings.md) (item 8): "Named switchable setting profiles like Work, Calm, Desk are good, with app group suggestions- like work apps, personal apps, etc."

Spec rows: CFG-45 (page and New profile), CFG-46 (settings that ride along), CFG-47 (app group suggestions), CFG-48 (switching), CFG-49 (migration, Backup, Studio). Nothing here was run on a device.

## One concept, not two
Owner policy: keep only the newest option. A profile **is** a workspace plus two things, so Workspaces is not kept beside it:
- the workspace contents, unchanged: home pages and what was removed from them, the dock, the widget slots (left page and corners), the Radar actions pinned for it, what the calm centre shows ([../handoff/config_and_logic.md](../handoff/config_and_logic.md) A11);
- **carries**: the settings groups that ride along, and **settings**: their values;
- **groupLinks**: app groups linked to it (pinned to its dock when chosen).

The model stays `Workspace` / `WorkspaceManager` in `feature-dock` (renaming the classes would touch every stream for no user gain). Every user-facing word is Profile: the Settings row (where the Workspaces row was, so the other settings stream only sees a title change), the page, the dock sheet tile ("Profiles and groups"), the dock pill's TalkBack name and actions, Radar ("Switch to Work", subtitle Profile, "Switch to next profile"), the Radar app action "Add to a profile", the keyboard shortcut names. "workspace" stays a hidden search word in Settings and in Radar. The old Workspaces page (`WorkspacesContent`) and its Add workspace dialog are deleted; the calm-centre and Radar-action rows moved to the Profiles page as they were.

## Settings that ride along (CFG-46)
Five groups (`ProfileGroup`, core-data): Look, Home behaviour, Radar, Side panel and Comms, Sound and vibration. Every key is a key of `web/liftoff-studio/schema.json` and every value is held as the JSON text of what the Backup holds at that place:

| Group | Keys (Studio schema) | Where the app keeps them |
| --- | --- | --- |
| Look | themeMode, accent, uiFont, iconShape, iconTint, iconPlate, calmStyle, calmRegular, wpGlanceKind (only `solid` or `gradient`), wpGlanceSolid, wpGlanceFrom, wpGlanceTo, wpGlanceAngle | launcher_prefs |
| Home behaviour | homePages, glanceHotspots, hotCornersOn, hotCornerDwell, cornerStyle, clockStyle, clockFormat, glanceClockDate, glanceClockSize, alwaysKeyboard, kbOpenP, kbOpenL; gestures.* (all 14); dock.enabled, dock.edge, dock.mode | launcher_prefs; shortcut store (`gesture.X`); the dock file (`DockSettings`) |
| Radar | searchLayout, searchLayoutPinned, searchMotion, radarBackground, radarIntensity, searchWeb, webEngine, webSuggestions, searchContacts, searchFiles, searchActions, searchDeep | launcher_prefs |
| Side panel and Comms | leftPage, launchpadRail, lpTheme, rightSide, recentsLimit, recentsUsage | launcher_prefs |
| Sound and vibration | uiSounds, uiSoundVolume, hapticStrength | launcher_prefs; `launcher_haptics` file |

Value encoding (the Backup's own): prefs values as in `settings.prefs` (enum names, ARGB numbers, floats as the export writes them, the hotspot list as its stored comma string), gestures as `action:X` or `""`, `dock.enabled` as `true`, `false` or `null` (Studio shows these as on, off, auto), the haptic strength as its id. Device-bound values never ride along (pictures, widget ids); a Glance picture or live wallpaper kind is not captured (only Solid and Gradient). The groups and their keys are written into the generated schema as `profileGroups`, so Studio and the app cannot drift.

Left out on purpose: the Comms filters and rules (their own store, `launchpad_rules`, and STR-13 groups: a separate concept); the Comms size (a pref file kept with the Side panel in Studio, could be added later); the Glance clock place and size.

## Precedence (CFG-48)
While a profile is active, a key it carries is the profile's: the live value is the profile's value, and the key's global value waits in `DockWorkspaceState.globalSettings`. Every other key is global.
- A change while a profile is active is written to that profile for the keys it carries (written through by `ProfileSettingsSync` watching the stores, and captured again on the way out), and stays global for the rest.
- Switching A to B is one pure step (`ProfileSwitch.plan`): A keeps its carried keys as they are now; the global values are the live ones with A's kept-aside values put back; B's values go over them for the keys B carries; only the values that change are written, the launcher_prefs keys in one store update; the kept-aside values become B's.
- A carried key with no value in the profile takes the global value.
- Adding a group to the active profile keeps the live values (they become its own, the same values are kept aside as global); dropping one writes the global values back. Adding a group to another profile starts it from the global values.
Tests: `ProfileSettingsW57Test` (pure), `ProfileManagerW57Test` (through the manager).

## Switching (CFG-48)
`WorkspaceManager.switchTo` first plans and writes the settings, then saves and restores the workspace contents (the settings are written before the home is applied, so a workspace's widget slot wins over a carried left-side setting). It works with the home not composed (an automation broadcast): the settings apply at once, the home is restored when the hooks attach, as before. "Switched to Work" is a toast (TalkBack reads it) from the app's hooks. The last switch can be undone once ("Switch back to Home" on the Profiles page). Entry points: the Profiles page, the dock's sheet (a chip row under its tiles) and pill (swipe and TalkBack next / previous), Radar, the Home sheet (a chip row at its top, under the title; the sheet is otherwise unchanged), the keyboard shortcuts. There is no quick-settings tile in the app, so none was added.

## Starters (CFG-45)
Start from: Current setup (a copy of the active profile with the live values of what it carries), Empty (one empty page, empty dock; the old "empty" workspace inherited the home on first switch), and three starters that are Empty plus:
- **Work**: carries Radar with files on; page 1 holds the suggested work apps; the calm centre shows the work apps; the Work apps group is pre-chosen.
- **Calm**: carries Look and Home behaviour with Pages Off (Glance only; pages stay stored), the Edge dots calm style and two hotspots (bottom); the calm centre shows the messaging and alert apps (the calm-centre filter is by app; a filter by Comms category, Messages and Alerts, is future).
- **Desk**: carries Home behaviour with the dock on, at the left side in landscape (portrait keeps it at the bottom) and the keyboard open with Radar in both orientations ("keyboard rows on" read as this; there is no other keyboard-rows setting).
The carried groups can be changed on the page before saving; a starter value of an unticked group is dropped.

## App group suggestions (CFG-47)
`AppGroupSuggester` (core-data, pure, no network, no Android import): precedence work profile, package tables of well-known apps, the words "bank" and "pay" (a package or label word that starts or ends with pay), `ApplicationInfo.category`, then utility words. One group per app. A kind with two or more apps is offered with every app it found, best first; the App groups store keeps 2 to 4 apps (they open together), so the first four are ticked and the page caps the choice at four. Saved suggestions are App groups like any other and leave the suggestion list. The app reads the category through `LauncherApps.getApplicationInfo` off the main thread (`rememberSuggestFacts`).

## Migration and Backup (CFG-49)
No schema version bump: the workspace document gains optional fields (`carries`, `settings`, `groupLinks` per workspace; `globalSettings` at the top), written only when set, so a workspace from before profiles keeps its exact document. A stored workspace decodes as a profile that carries nothing and switches exactly as before (tested: no settings write). The Backup carries the fields inside the `workspaces` section and `dock.globalSettings`; importing a Backup from before profiles clears a stale `globalSettings` when it brings its own workspaces. Studio: `schema.profileGroups`, `phoneProfiles(ix, doc)` and `setPhoneProfile(ix, doc, id, values)` in `core.js`, node-tested round trip; the Studio page has no profile editor UI for phone profiles yet (its own browser profiles are separate).

## Undo
Delete this file's code paths: `ProfileSettings.kt`, `ProfileStarters.kt`, `AppGroupSuggester.kt` (core-data), `ProfileSwitching.kt`, `ProfilesPage.kt` (feature-dock), `ProfilesUi.kt` (app); restore `WorkspacesContent` from git; the optional fields are ignored by older builds' decoders (they drop them on their next write).

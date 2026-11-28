# Wave 57: profiles (Profiles extend and replace Workspaces)

Owner answer (Q2): "Named switchable setting profiles like Work, Calm, Desk are good, with app group suggestions- like work apps, personal apps, etc." Decision note: `docs/decisions/profiles.md`. Spec rows CFG-45 to CFG-49 added to `docs/Spec.md` and `docs/SPEC_STATUS.md` (the hygiene audit needs one status row per spec ID). Nothing here was run on a device.

| ID | Status | Note |
| --- | --- | --- |
| CFG-45 | built, not device-verified | Settings row Profiles where Workspaces was (hidden alias "workspace"); Profiles page: drawn active card, list with Switch / Rename / Duplicate / Settings that ride along / Delete, Switch back row, suggested app groups, calm centre and Radar actions rows; New profile page (name, Start from cards: Current setup, Empty, Work, Calm, Desk; settings cards; suggested groups with Edit). `ProfilesPageW57Test`, `ProfileSettingsW57Test` (starters), `ProfilesHomeSheetW57Test` |
| CFG-46 | built, not device-verified | `ProfileGroup` five groups of Studio schema keys; values as Backup JSON texts; prefs round trip; Glance wallpaper kind only Solid/Gradient; carries changed on the active and on another profile. `ProfileSettingsW57Test`, `ProfileManagerW57Test` |
| CFG-47 | partial, not device-verified | `AppGroupSuggester` offline tables (real package names), work profile, bank/pay words, ApplicationInfo.category; listed, edited (2 to 4 apps), saved as App groups, pinned to a profile's dock. `AppGroupSuggesterW57Test`, `ProfilesPageW57Test`. Limits below |
| CFG-48 | built, not device-verified | one-step settings write (one launcher_prefs update) then the workspace contents; Switch back; precedence (carried keys write through to the profile, the rest global, global values kept aside); switch with the home not composed; dock keys; switch from the page, dock sheet chips, Home sheet chips, Radar (both providers). `ProfileManagerW57Test`, `ProfilesPageW57Test`, `DockSheetW55Test` sheet_14, `ProfilesHomeSheetW57Test`, `ProfileSwitchPointsW57Test`, `ProfilesRadarW57Test` |
| CFG-49 | built, not device-verified | old workspace documents decode as profiles with no settings and switch exactly as before (no settings write); new fields only written when set; Backup export/import round trip incl. `dock.globalSettings`; Studio `schema.profileGroups` (generated), `phoneProfiles` / `setPhoneProfile` with node tests. `ProfileManagerW57Test`, `StateLiveWiringTest` CFG_49, `ProfileSettingsW57Test` CFG_49, `web/liftoff-studio/tests/studio.test.js` |

## What changed where
- core-data: `ProfileSettings.kt` (groups, texts, prefs read/write, the pure switch plan), `ProfileStarters.kt`, `AppGroupSuggester.kt`; `StateLiveSources.kt` carries `globalSettings` in the dock object.
- feature-dock: `Workspace` gains `carries`, `settings`, `groupLinks`; `DockWorkspaceState.globalSettings`; `WorkspaceManager` profile API (switch with settings, `undo` / `switchBack`, `syncActiveSettings`, `setCarries`, `createProfile`, `linkGroup`); `ProfileSwitching.kt` (settings port over the app's stores, dock keys); `ProfilesPage.kt` (page, New profile, suggestion page, chip row); `WorkspacesContent` deleted; panel, sheet, rail and Radar wording.
- app: `ProfilesUi.kt` (category facts, write-through sync, "Switched to" toast); `profilesCategory` replaces `workspacesCategory`; Home sheet chip row; one-line edits in `SettingsCategories.kt`, `SettingsOneUiModel.kt`, `SettingsOneUiSummaries.kt` (the row title Workspaces to Profiles).
- search-engine, core-input, feature-grid: "Switch to <name>", "Switch to next profile", "Next profile", "Add to a profile", "Add to which profile?".
- core-design: `L10nW57profiles.kt` (65 strings, 8 languages).
- web/liftoff-studio: `profileGroups` in the generated schema, `phoneProfiles` / `setPhoneProfile`, README section, 2 node tests (26 pass).

## Tests changed because the owner replaced the behaviour
- `WorkspacePagesW41Test`: renders `ProfilesContent`; page_01 (explanation card) now checks the active profile card; page_03 "Make active" is "Switch"; rename and name-taken texts say profile; row summaries end with what rides along; page_10 and page_11 (the Add workspace dialog with Empty / Copy) deleted, the New profile page replaces it (`ProfilesPageW57Test` CFG_45_*); page_33 title is Profiles.
- `DockIconsOnlyW25Test`, `DockFixW26Test`, `DockStyleUiW39Test`, `W4RadarInputDockTest`, `DockStackW26SmokeTest`: the pill is "Profile Home", its actions "Switch to the next / previous profile", the sheet tile "Profiles and groups".
- `DockExtTest`: pinned action "Switch to next profile". `RadarMenuChooserTest`: "Add to a profile".
- `OneUiModelTest`, `SettingsHomeW52Test`, `SettingsCoreW56AppTest`, `DockDeskReviewW24RulesTest`: the category is Profiles ("1 profile", "3 profiles"; "workspaces" still finds it).

## Screenshots read
`feature-dock/build/screenshots/w57-profiles-dark.png`, `-light`, `-fontscale2`, `-switched`, `w57-new-profile.png`, `w57-new-profile-bottom.png`, `w57-suggestion.png`; `app/build/screenshots/w57-home-sheet-profiles.png`. Fixed from them: "Settings: No settings ride along" read twice (now "No settings ride along"); the carried-settings caption listed group details mid-sentence (now the group names).

## Not matched / limits
- An App group keeps 2 to 4 apps (the existing store's rule: they open together), so a suggestion of more apps is cut to four ticked; the Work starter still puts every suggested work app on page 1 and in the calm centre.
- The Calm starter's "notifications muted except Messages/Alerts" is by app (messaging and alert apps), because the calm-centre filter is per app; a per-category filter is future.
- "Keyboard rows on" (Desk) is read as the keyboard opening with Radar in both orientations; there is no other keyboard-rows setting.
- Schedules for profiles: not done (future, as asked).
- No quick-settings tile exists in the app, so there is no quick-settings switch; the keyboard shortcuts and the dock pill step profiles.
- The model classes keep their names (`Workspace`, `WorkspaceManager`, `RunKind.Workspace`, intent actions) so automation intents and other streams keep working.
- Studio has the round-trip functions but no page UI for phone profiles.
- Shared files with the parallel Settings stream: the Profiles row lives in `DesktopSettingsPane.kt` (`profilesCategory`); `SettingsCategories.kt` (two lines), `SettingsOneUiModel.kt` (two words) and `SettingsOneUiSummaries.kt` (one line) change "Workspaces" to "Profiles". A merge with the six-tab restructure must keep the category key "Profiles".

## Found by the full app run
Reading the app categories on a background coroutine (and then a thread) that wrote into the composition later made `DockWiringTest` and `DockDialogsW24Test` fail when they ran in the same sandbox after the dock panel test (the base commit passes them; reproduced 3 times). The facts are now read once per app during composition and cached for the process (`SuggestFactsCache`; `LauncherApps.getApplicationInfo` is an in-memory system call, no file). On a phone with many apps the first opening of the Profiles page makes one such call per app on the main thread (device-only cost, not measured).
The Counts line of `docs/SPEC_STATUS.md` was already stale on the base commit (167 counted, 170 rows); it now matches the 175 rows.

## Device-only
The toast and TalkBack announcement, the feel of a switch (several stores change in one frame), `LauncherApps.getApplicationInfo` categories on real apps and work profiles, a Backup moved between two phones, the chip rows on a small screen with long names.

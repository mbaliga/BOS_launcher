# Liftoff Launcher: configuration outside Settings, and the logic that links settings

Branch claude/clever-mccarthy-804ej7, derived from code on 2026-11-22 (read-only). Paths are relative to the repo root.
Abbreviations: HS = app/src/main/kotlin/com/bos/sphere/HomeScreen.kt; SC = app/src/main/kotlin/com/bos/sphere/ui/SettingsCategories.kt;
HM = core-data/.../core/data/HomeModel.kt; LS = core-data/.../core/data/LauncherStores.kt; GM = core-data/.../GlanceModel.kt; HO = core-data/.../HomeOrder.kt;
HC = core-data/.../HomeChrome.kt; HSN = core-data/.../HomeShown.kt; QS = app/.../ui/HomeQuickSheet.kt. "Settings" = Ground Control (the Settings screens; same thing, four names are in use: Settings, Ground Control, Home settings, Liftoff settings).
Everything below is code-verified unless marked (verify). Nothing was run on a device.

Terms used by the code: **Glance** = the calm first stop of the home (clock, music line, hotspots). **Pages** = the setting Off / Paginated / Continuous. **Hotspots** = the four corner slots as drawn on every home page. **Disc** = the all-apps radial drawer. **Comms** = Launchpad, the notification hub on the left edge. **Radar** = search.

---------------------------------------------------------------------------------------------------
## A. Configuration surfaces outside the Settings screens

Legend for "Also in Settings?": DUP = same stored value is also editable in Settings; ONLY-HERE = no Settings row; PARTIAL = Settings has a related but not identical control.

### A1. Long press on empty home
Dispatch (HS:1180 paged grid; HS:622-626 every other surface and Glance):
1. Paged grid and the home has NO items at all (`FreshHome.isEmpty`, GlanceModel/HomePageOps.kt:115): enters edit mode with the page editor open and the Apps tab focused (HS:1180). Nothing else is offered.
2. Else if the Long press gesture is bound (Settings > Gestures > Long press): runs that binding INSTEAD of everything below (HS:623, 1180).
3. Else Glance: opens the Quick sheet (HSN:16 `longPress(Glance)=QuickSheet`; HS:624).
4. Else paged grid with items: opens the Quick sheet with the "This page" block (HS:1180, page passed only if Paginated: HS:1760).
5. Else (Pages = Continuous, List, DSi, PSP-by-gesture): `HomeShownRules.longPress(Pages)` returns `EditHome` (HSN:16) but the code does NOT edit; it opens Settings > Home screen (HS:625). The enum name is misleading.
6. PSP has no empty space: a 44 dp invisible band at the top opens the Quick sheet (HS:942-946, `SettingsHoldBand`), though the Settings hint says "Hold the clock band for Settings" (SC Style hint).
7. Right-click / Menu key on empty home opens the Quick sheet for the current page (HS:655).

**Quick sheet** (QS; black popup; `HomeSheetAction`/`ContextMenus.forHomeSheet` core-input/Input.kt:275-292 is the shared model):
| Block | Control | Writes | Also in Settings? | Persistence |
|---|---|---|---|---|
| Pages | chips Off / Paginated / Continuous (QS:102-103) | `homePages` | DUP (SC:303) | launcher_prefs `homePages`; exported |
| Order | chips Left to right / Right to left (QS:104-105) | `homeOrder` | DUP (SC:268) | `homeOrder`; exported |
| Hotspots | chips Four / Two (bottom) / One / None (QS:109-113); Custom is not a chip | `glanceHotspots` | DUP (SC:818-828 preset + 4 toggles; page editor Settings tab has 4 toggles too) | `glanceHotspots` CSV of physical corners |
| This page (only when Pages = Paginated, Grid style, Glance not on screen) | "Align with top" / "Align with bottom" (QS:132-133) | GLOBAL `gridBias` = 0 or 1 (not per page, despite the heading) | PARTIAL: Settings "Height in page" slider (SC:359) is the same pref | `gridBias` |
| This page | "Make this home" (QS:134) | `homePage` (index the Home button returns to) | ONLY-HERE | `homePage` |
| Transition | chips Slide / Fade / Zoom / Cube / Flip / Stack (QS:139-142) | GLOBAL `pageTransition` | ONLY-HERE (no Settings row) | `pageTransition` |
| This page | "Delete page" (danger chip, QS:143-147); asks if the page holds items (HS:704) | layout | DUP ("Delete this page" in Page N category, SC:244) | home_layout |
| Rows | "Wallpaper" (opens Settings > Home screen top, HS:1763) and "Ground Control" (Settings root) | none | n/a | n/a |
Dropped from the sheet in wave 34 but still modelled in `forHomeSheet` (never rendered): Add page, Edit home, Files/usage/timeline, Widgets/folders/icons, Time/Weather/Battery (QS:161 `GONE`). docs/Spec.md HOM-21 still says the sheet holds "Glance content chips", which are gone with the retired glanceContent.

### A2. Long press on an app, folder, widget, tile
| Where | Result | Source |
|---|---|---|
| App on a paged page, NOT in edit mode | App menu dialog (`AppContextMenu`); dragging after the press moves the icon without edit mode (GridHome.kt:655-675) | HS:1177 `selectItem` returns false outside edit |
| App on Continuous / List / DSi / PSP | same App menu, plus a "Home settings" row (only when `!prefs.isPaged`, HS:1662) | AppContextMenu.kt:62 |
| Clock tile or Settings tile (a page tile) | enters edit mode AND selects the tile (HS:1184 `selectItem(key, enterEdit=true)`) | |
| Any item while edit mode is on | selects it: item action bar (Resize, Remove, Settings) and the item sheet (A3) | HS:404-408 |
| Result in Radar or drawer disc | same App menu (HS:1422, 1462) | |
| Home from the Recents panel | App menu plus "Remove from recents" and "Clear recents" (HS:1659) | |
So on a built paged home there is no long-press path to edit mode other than a tile; "Edit home" is Settings > Home screen (SC:262) or the tray "Edit" of an empty home. docs/decisions/editChrome.md line 9 says "Long press ... open the page itself"; the code does not.

**App menu rows** (`ContextMenus.forApp`, Input.kt:249-266; AppMenuModel.kt): Open; Open on another display / Open beside / dock pin rows (dock group, only with a second display or split support); App info; Tags; Add to home (when not on a page); Icon colour; Hide / Unhide; Home settings (non-paged only); Uninstall (not system apps); Remove from home; Move to folder; plus from HomeItemsHost.kt:485-497: New folder with this app / Add to folder X / Take out of folder / Change icon.
| Control | Also in Settings? | Persistence |
|---|---|---|
| Hide / Unhide | DUP (Hidden apps pane lists and unhides) | `hidden_apps` file; exported inside layout `hidden` (StateCodec.kt:128) |
| Icon colour (per app tint) | ONLY-HERE (Settings has global "Icon colour" none/accent/mono, SC:590) | `icon_colors`; NEVER exported, excluded from system backup (StateSchema.kt:104; backup_rules.xml:9) |
| Change icon (per app override, icon pack choice) | PARTIAL: icon pack and "themed icons" are ONLY in the "Widgets, folders and icons" item picker (HomeItemsHost.kt:649, 683) | `home_icons`; exported |
| Tags | ONLY-HERE | `home_tags`; exported |
| Add / remove from home | DUP via page editor Apps tab | home_layout |
| Open with (default app for Music, Browser...) | Settings App drawer > Open with (SC:406); also asked in place when an action has no chosen app (HS:1746 "Open %s with" dialog) | `action_apps`; exported |

### A3. Edit mode: top bar, tray, hint, item action bar, item sheet
Entry: tile long press (A2), empty fresh home (A1), Settings > Edit home (HS:449, turns Pages on to Paginated with a toast if Pages Off: HS:444), Settings > Add page, "+ Page" etc.
* **Top bar** (HomeEditChrome.kt:229-316): Cancel (asks "Discard changes?" only if something changed), title "Editing page N of M" (tap opens the page editor), Undo, Redo, Done. Esc and system Back are Cancel.
* **Tray** (HomeEditChrome.kt:401-404): Apps, Widgets, Folders, "+ Page". "+ Page" is shown when Pages != Off (HS:1859) but Settings "Add page" only when Paginated (SC:261) and Spec HOM-22 says Add page is offered only in Paginated (inconsistent, Continuous shows it).
* **Hint** "Drag to move. Tap an item to resize or change it." shown for the first 3 edit entries only (EditChromeHint.MAX_SHOWS, EditChromeRules.kt:12; counter in device-local `edit_chrome`, not backed up; HS:384).
* **Item action bar** (ItemActionBar.kt:130-132): Resize (widgets only), Remove, Settings (opens the item sheet).
* **Item sheet** (ItemSheetModel.kt:62-80; ItemSheet.kt:94-111):
| Item | Rows | Also in Settings? | Persistence |
|---|---|---|---|
| Clock tile | Style (Digital/Analog), Hours (System/12/24; hidden if Analog), Show date | ONLY-HERE (Settings only has "Clock tile on this page", SC:242). Separate from the Glance clock settings (SC:805-813). Global to every clock tile. | `clockTileAnalog/Format/Date` in launcher_prefs; written immediately, but Cancel restores them (HS:692-697 `configSnapshot`) |
| App | Open, App info, Change icon, Hide from launcher (switch), Uninstall | DUP/ONLY-HERE as A2 | |
| Folder | Open, Rename, Remove folder | n/a | home_extras |
| Widget | Reconfigure (if provider has a config activity), Size presets 2x1 / 2x2 / 4x2 clipped to the grid | n/a | home_extras; presets clamp to provider limits (ItemSheet.kt:108-123) |
| Shortcut / link / Settings tile | Open | | |
Transaction scope of edit mode: layout, extras (folders/widgets/links), dock pins and the clock tile options are undone by Cancel/Undo. Prefs changed from the page editor's Settings tab, the Quick sheet or Settings are NOT (see B11).

### A4. Page editor (inside edit mode)
Opened from the edit title, the empty-home hint, or Edit home on an empty home. Pieces: page strip (reorder by drag, Move earlier/later, Delete page, New page; PageEditorStrip.kt:189-254), live card with swing, and four tabs (PageEditorTabs.kt:32 `EditorTab`): Apps (filter, Personal/Work chips, tap adds, drag onto any card), Widgets (Choose a widget), Folders (New folder, plus App shortcut / Web link / File / App group tiles at :131-135), **Settings**.
**Settings tab** (PageEditorTabs.kt:221-238):
| Selected card | Controls | Also in Settings? |
|---|---|---|
| a page | name "Page N", and under heading "All pages": "Pages use the same wallpaper as Glance" (global, flagged as global in the UI) | DUP (SC:798) |
| Glance card | "Show music"; one toggle per physical corner (hotspots) | DUP (SC:815, 824) |
Per-page layout (shape, rows/columns, rings, density-follows-home) is NOT in the page editor; it is only in Settings "Page N" (SC:195-246) and via pinch (HS:485-535). Per-page properties are only those in `PageLayout` (HM:626): shape, portrait grid, landscape grid, rings. Wallpaper, transition, margins, icon size are global.

### A5. Glance gestures and Glance-only chrome
Glance is one stop of the outer pager (HO:74 `hasPagesStop`, HS:1118). Its gesture layer is the paged-horizontal surface (HS:629-631), so only vertical swipes, pinch, double tap and long press are bindable.
| Gesture on Glance | Effect | Config | Persistence |
|---|---|---|---|
| Tap on clock | runs the Clock corner action (opens the clock app) HS:1135 | none (cannot be rebound; ONLY style/size/date in Settings) | |
| Tap elsewhere | pops the music controls notch when music plays; hides after `glanceControlsSeconds` (2-10, default 4) (GM:60-90, GlanceHome.kt:168) | Settings stepper "Controls hide after" (SC:826) | `glanceControlsSec` |
| Long press | A1 | | |
| Swipe up / down, pinch in/out, double tap | bound gestures (defaults: pinch in Radar, swipe up All apps, swipe down Notifications; HM:555-563) | Settings > Gestures only (no in-place editor) | `shortcuts` file `gesture.*`; exported |
| Sideways swipe | the pager (to page 1), or from the edge: Comms | `homeOrder` mirrors it | |
| Edge swipe left edge (right in RTL), handle | opens Comms (HS:1003 `launchpadEdgeDrag`); edge zone 48 dp on Glance, 28 dp elsewhere (HC:57-58) | `leftPage` | |
| Edge swipe opposite edge | Recents panel / dock rail / nothing per `rightSide` | Settings "Right side" (SC:275) | |
| Indicator row dots | tap = go to that stop (HS:457 `showDot`) | `glanceIndicator` (SC:827) | |
There is NO in-place edit of Glance content. The retired glanceContent / glanceWeather / minimalHints keys are dropped on load (GM:13-30, RetiredPrefKeys.kt).

### A6. Hotspots / corners
| Interaction | Effect | Source |
|---|---|---|
| Long press an enabled hotspot (any home page) | opens the Corner editor: single action/app/shortcut, palette (fan-out folder, max 40 items), complication cluster (max 4), peek widget, or Clear | HS:861, 1674; Corners.kt:466, 527, 560; Editors.kt:77-130 |
| Long press an EMPTY enabled corner | same editor (Corners.kt:570-573) | |
| Disabled hotspot | not drawn, no dwell, no long press: its assignment can only be edited in Settings > Corners (HS:468-469) | |
| Tap | runs the item; a palette on a pointer/keyboard path runs only its FIRST item and a widget/complication/empty slot is no hotspot (GM `MinimalHotspots.itemOf`, HS:663 `onCornerActivate`) |
| Hot corner (mouse) | dwell reveal then click (`hotCornersOn`, `hotCornerDwellMs` 0..1000; SC:437-438) | HS:658 |
Persistence: assignments in `shortcuts` file (physical corner key `corner.X`; landscape set `lcorner.X`); enabled set in launcher_prefs `glanceHotspots`; widget slots hold device-bound ids (DeviceBound.kt). Destructive clears take an automatic snapshot (LS:446 `DestructiveEdits.before`).
Duplicate surfaces for the same assignment: corner editor (long press), Settings > Corners rows (SC:439-441), workspace switch (A11).

### A7. Radar (search) in-place options
Radar opens from a corner, pinch, a typed character (HS:583 `TypeToSearch`), Ctrl chord, or "Search" action. In place it can change:
| Control | Effect | Also in Settings? | Persistence |
|---|---|---|---|
| Pinch or Ctrl+scroll | result text scale 0.8-2.2, default 1.15 (RadarScale.kt:14-25, SearchLayouts.kt:802) | ONLY-HERE | `radar_ui` `resultScale`; exported (StateLiveSources ALLOWED) |
| Long-press an app result > alias | per-app alias (SearchOverlay.kt:501, 727) | PARTIAL: Settings "Search history and aliases" pane | `palette` file (aliases exported in `paletteAliases`; habits excluded) |
| Long-press result > Options / preview | App menu (A2) | | |
| "N hidden apps match: Show" row | unhides that app AND deletes every Comms Hide rule naming that package (HS:1410) | DUP of Hidden apps pane | |
| Settings results (every Settings row is a Radar hit; opens page on that row via `SettingsFocus`) | HS:1403; only when `searchActions` is ON and the query has 2+ chars (SearchOverlay.kt:396-397) | n/a | |
| Scope wheel / category (Apps, Web, People, Files, Everything) | session state, not stored | | |
| Web note "Suggestions come from X. Turn off in settings." | one-time (`webNoteSeen` in radar_ui, which IS exported) | | |
Layout, background, web engine, web suggestions, files, deep, contacts, actions, motion are Settings-only (SC:616-639). Nothing in Radar writes launcher_prefs.

### A8. App drawer disc (long press and in-place controls)
| Control | Effect | Also in Settings? | Persistence |
|---|---|---|---|
| Grouping labels on the disc (A-Z, Category, Recent) | writes `wheelGrouping` (HS:1459) | DUP (SC:402 "Wheel groups") | launcher_prefs |
| "Work" label (only if a work profile exists) | toggles `wheelWorkOnly`: disc lists only work apps; ignored with no work profile (HS:1474) | ONLY-HERE (no Settings row anywhere) | `wheelWorkOnly` |
| Pause / Resume work apps pill | calls system quiet mode (`WorkProfileState.setWorkPaused`) | ONLY-HERE (the home pause chip is the same action, B-section) | system state, not stored by Liftoff |
| Ring: tap | runs segment shortcut; drag turns the ring; hub = Back | | |
| Ring: long press a FILLED segment | shows its name pill (PuckSections.kt:255-259) | | |
| Ring: long press an EMPTY segment | closes the disc and opens Settings > App drawer (HS:1483) | | |
| App long press on the disc | App menu (A2) | | |
Ring segment COUNT (6-8), the 8 shortcut slots, and "labels under glyphs" are Settings only (App drawer > Ring segments / Drawer shortcuts / Show labels, PuckActionsPane.kt:41-81). Persistence: `puck_actions` (exported).

### A9. Dock and desktop menus
Dock exists on phone only if enabled (`DockSettings.enabled ?: tier != T1`, DockModel.kt:77: OFF by default on a phone, ON on tablets/desktop) or forced by Right side = Dock rail on Glance (HC:52).
| Surface | Controls | Also in Settings? |
|---|---|---|
| Long press the dock (W55) | the dock sheet (DockSheet.kt): a share-sheet style bottom sheet (a side sheet beside a side dock in landscape) that pushes the whole home up or sideways by its size; cards for Dock style, Position, Mode; switches Show the dock (not when forced), group chips, Desktop item; tiles Edit dock (in place on the dock: wiggle, drag, x, plus, compact bar with Done), Workspaces and groups (opens that page), About the dock | The same options, under a live dock preview, on Settings > Desktop & dock > Dock (DockSettingsContent); Dock style is only there (the Home screen row was removed) and in theme packs |
| Long press a dock item | App info, Move earlier/later, Replace, Remove from dock, Pin to dock (DockMenu.kt:78-84); Alt+arrows and drag reorder | ONLY-HERE (plus Edit dock page) |
| Dock sheet "Show the dock" | sets dock `enabled=false` / true | DUP of Desktop & dock > Dock |
| External display / DeX "Desktop" | offer-on-connect, disconnect behaviour, per-display profiles | Settings > Desktop & dock > "Dock and external display" (DesktopSettingsPane.kt:54, 80-97); file `desktop_surface` excluded from backup |
Dock settings (enabled, mode Collapsed/Pinned+recent, edge, style, group shelf) live in the workspace/dock JSON (`dock` export section), not launcher_prefs.

### A10. Comms in-place configuration
| Control | Effect | Also in Settings? | Persistence |
|---|---|---|---|
| Menu panel: Group by (Time/App/Sender/Conversation/Flat/App and conversation) and Show (per-app filter) | default grouping `LaunchpadPrefs.defaultMode` (Launchpad.kt:388 `onSetDefaultMode`); filter is session state | PARTIAL: Settings > Comms > "Grouping & rules" (SC:515) | exported in `launchpad` section |
| Swipe an item (default: dismiss both ways) | `Dismiss` = MARK READ and, only if `clearFromShade`, cancel from the shade (Launchpad.kt:528, 387) | Settings gestures pane (SC:502) exposes bindings; "Clear from the shade" toggle SC:497 | `launchpad_hub` (NOT exported, B10) |
| Long press item > chips Open, Mark read/unread, Delete from history, Actions | per-item: Snooze, Reply, Mute app, Hide app, Pin sender, Move to group, Make a rule from this | PARTIAL (rules list in Settings pane) | rules in `launchpad` section |
| "Add a rule" from an item | writes a `Rule` (Hide, Silent, Pin, Important, AutoClear n min, GroupApp/Sender/Conversation, Flat) with matchers package/sender/channel/conversation/text (LaunchpadRules.kt:16-80) | DUP (Settings > Comms > Grouping & rules pane lists/removes) | |
| Group rule editor (conditions, negate, time window, contacts, last name; first match wins) | Comms **groups** (people-based), a different system from Rules | Settings > Comms > "Groups and rules" opens it (SC:499) | `launchpad_groups` (excluded from backup; section in export) |
| Menu panel Show list: All Comms, then Messages / Notifications / Alerts (wave 53), then the apps | one radio list; a category (pure `NotificationClass.categoryOf`, Alerts > Messages > Notifications, owner Treat-as rule first) filters the live list and the Unread / Read tabs; unread badge per category, pinned rows never count; definitions in docs/decisions/commsCategories.md | Default view row in Settings > Comms also offers the three | `LaunchpadPrefs.defaultCategory` in `launchpad_rules`, exported (`launchpad` section) |
| Long press item > chips Make this a Message / a Notification / an Alert; rules list action Treat as ... | adds a `Rule` with action AsMessages / AsNotifications / AsAlerts (sender in its app, else channel, else app) | DUP (Settings > Comms > Grouping & rules) | rules, `launchpad` section |
| Settings > Comms > Show pinned at the top (default on) | ongoing / persistent notifications (flags ongoing, foreground service, no-clear; sticky categories) lead the list in a Pinned block with a pin glyph; locked (no dismiss, no mark read), never unread, compact row above three | ONLY-HERE | `LaunchpadPrefs.pinnedAtTop` in `launchpad_rules`, exported |
| Pinch / two-finger swipe | pinch = filter Unread - All - Read (commsRead.md); two-finger swipe = switch view / mark visible read | Comms gestures pane rebinds actions (HubGestures.kt; LaunchpadGestures.defaults LaunchpadRules.kt:420-427) | `launchpad` section |
| Tap the Comms clock | opens "Sound and Vibration" sheet: system volumes, and the "Launcher vibration" Off/Light/Medium/Strong slider (SoundVibrationSheet.kt:165; SoundVibrationState.kt:58) | the vibration strength is ONLY-HERE; Settings > System & sound has only interface sounds on/off + volume (SC:701-702) | `launcher_haptics`; exported |
| Status glyphs | Connectivity sheet (Wi-Fi toggle opens system panel) | ONLY-HERE | |
| Ctrl+Plus / Ctrl+Minus | Comms size 75-125% | DUP (Settings stepper) | `comms_scale`; exported |
Hint text of LaunchpadGesture.SwipeItemRight says "opens the palette of actions (fixed)" yet its default binding is Dismiss and `allowed()` lets it be rebound (LaunchpadRules.kt:390-430); verify what the UI honours.

### A11. Workspaces and app groups (not Settings, not per-page)
A Workspace (`Workspace`, Workspaces.kt:13-22) snapshots: home page layout + removed set + dock pins (AppWorkspaceHooks.captureHome, DockLayer.kt:57-63), left-page widget and corner widgets (captureWidgets DockLayer.kt:82-91), and a notification scope ("What the calm centre shows", DockPages.kt:249). Switching restores them (`applyHome`, `applyWidgets`, `applyNotificationScope`). It does NOT carry wallpaper, hotspot assignments (other than widget slots), Pages, gestures or theme. Switch via dock pill, Ctrl+workspace shortcuts, Radar command ("Switch to workspace N", HS:1440), or Settings. "App groups" (2-4 apps launched together, sessions, schedules) are a separate system from Comms groups and from workspaces; the same word "group" appears in three places (App groups under Desktop & dock, Groups and rules in Comms, group chats).

### A12. Themes, backup and other file-based entry points
* Theme pack (`.liftofftheme`, <=64 KB JSON): Settings > Look > Themes (Import / Export / Reset / Undo) AND opening such a file from another app (ThemeImportActivity, manifest :124-137) shows a preview, applies nothing until Apply. Carries only mode, accent, icon shape, icon tint, dock style, and a SOLID or GRADIENT wallpaper (ThemePack.kt).
* Backup > Export setup / Export settings only / Import setup / Import layout from another launcher / Snapshots (last 5, taken before imports, before page delete, after stable starts) / Restore (BackupPane.kt:152-238). Onboarding step 3 imports the same file.
* Lab (hidden unless debug) and Device checks: not user configuration.

### A13. First-run and permission prompts
| Surface | What it asks | Source |
|---|---|---|
| Onboarding overlay shown over Glance while `onboardingDone` is false (HS:1885; StateScreens.kt:49-93) | 3 steps, each skippable: "Make it home" (role request, falls back to home settings), "Pick a look" (writes `homeStyle` only: Pages / List / PSP / DSi), "Bring your setup" (import file; on success finishes). Asks NO permissions (OnboardingFlow.upFrontGrants is empty, OnboardingFlow.kt:17) | OnboardingFlow.kt:6-21; Onboarding.kt:50-53 |
| "Make default" whisper | one dim line 2.5 s after start, hidden after 9 s, only while Liftoff is not the default home (DefaultLauncherBanner.kt) | HS:1506 |
| GrantGate explainer (`GrantPrompter`, HS:985; `GrantHooks.interceptor`) | one screen "what is read / stays on device / button / Not now" before the system page or prompt, for: Notifications, UsageAccess, Contacts, Folders, Microphone (GrantGate.kt:37-90) | |
| Direct system prompts WITHOUT the explainer | POST_NOTIFICATIONS when "When home isn't showing" motion is switched on (HS:748-756); DND policy-access page when a silent/DND action runs (ActionRunner.kt:222-262) | |
| Widget bind / configure | Android's own consent per widget (WidgetHosting.kt) | |
| "Fill the empty space?" dialog after Settings closes with a capacity change (HS:825-834, 1736) | reflow pages | |
| Restore-previous-pages notice (one-time after the wave 30 reset of an auto-filled home; kept 30 days) | HS:288-330 | |

---------------------------------------------------------------------------------------------------
## B. Logic and dependencies (IF ... THEN ..., with implementing file:line)

### B1. Pages, Glance, home style, grid
1. IF Glance is ON and Pages = Off THEN the home has ONE stop (Glance); no pages are drawn, the edit hint/fresh-home long press never appear, the chosen Style (Grid/List/PSP/DSi) is invisible. HO:74 `hasPagesStop = mode != Off || !glanceOn`; HS:252.
2. IF Glance is OFF and Pages = Off THEN the first page alone is home, other pages stay stored and hidden. HO:86 `visiblePages`, HS:322.
3. IF Pages = Paginated THEN one dot per page plus Glance; IF Continuous THEN one scroll stop; IF Off THEN dots only for Glance (shown if `glanceIndicator`). HO:92-105.
4. IF Pages is Off THEN "Edit home" / "placing an item from Glance" first switches Pages to Paginated and toasts "Pages are now on (Paginated)..." (HS:444-452, 1750-1759). Same for Add page when not already Paginated (HS:429-436).
5. IF Pages = Continuous THEN `isPaged` is false: Settings hides the Page N category, per-page grid, Default density, Page turning, Label size, "Height in page" and top/bottom margin sliders (SC `paged` gates at 144, 321, 358, 367, 371); the quick sheet omits "This page"; the item bar uses `scrollSurface`; the Add page tray chip still shows. HM:578; HS:1760.
6. IF homeStyle != Grid (List / PSP / DSi) THEN pages concept is replaced by "every app, A-Z / categories / tiles"; Settings shows Style-specific rows (Scroll direction only for DSi SC:313; Icon size hidden for PSP SC:347; Spacing variants SC:351-356). `continuousColumns` is used only by Grid+Continuous (HS:485 `changeDensity`), DSi is hard-coded 3 across (HS:1219).
7. IF `scrollAxis` governs Continuous gesture surface THEN: `LauncherPrefs.surface()` treats Grid+Continuous like DSi (`scrollsHorizontally` by axis, default `Long` = portrait false = Vertical; HM:589-600) but ColumnScrollHome always scrolls horizontally (ColumnScrollHome.kt:139) and Settings offers the axis only for DSi. Result: on Continuous in portrait the gesture annotations and `homeGestures` free-axis logic point the wrong way (verify on device).
8. IF `pageMode = Single` (Settings > App drawer > "Pages: One page", SC:404) THEN only page 1 is shown whatever Pages says; other pages preserved (HS:322). A second control also labelled "Pages" (Home screen, Off/Paginated/Continuous, SC:303) exists. `pageMode` shows only on paged homes.
9. IF `appsOnHome` != PinnedOnly THEN it is forced to PinnedOnly on every start (HS:317); no UI exists for it. Fresh install writes PinnedOnly (HomePageOps.kt:112); an existing install reads Everything (LS:276) then gets forced (and its auto-filled home is reset once to one empty page, with "Restore previous pages" for 30 days; PagesExplicit.kt, HS:296-330).
10. IF adaptiveGrid is ON THEN `AdaptivePrefs.effective` replaces portrait/landscape grids with window-derived ones (AdaptivePrefs.kt:50-53; HS:228 usage); Settings still shows "Default density" and its steppers (SC:322-340, gated only by `paged`), which then have no visible effect; pinch moves `gridDensity` instead (HS:498-503); per-page grids still override.
11. IF a page has its own portrait/landscape grid (`PageLayout.portrait/landscape != null`) THEN pinch changes that page only (HS:508-519); ELSE pinch edits the GLOBAL grid for the orientation and may trigger the "Fill the empty space?" dialog on Settings close (HS:520-534, 825-834). Shape Circle: capacity = 1+6+12+18 by rings (HM:644-658); Coverflow capacity 40.
12. IF spacingV < 0 THEN "Same spacing both ways" (iconSpacing used for both); else separate Spacing across / down (SC:351-353).
13. IF page transition != Slide THEN the effect applies to Paginated grid only, never to the outer Glance pager, and no reduced-motion check exists in `pageEffect` (GridHome.kt:1064).
14. IF the Home button is pressed THEN everything closes, pager goes to stop 0 (Glance if on) and the grid moves to `homePage` (HS:837-843). "Make this home" therefore only shows when Glance is off.
15. IF Glance is switched ON while the user is on pages THEN the pager scrolls to stop 1 so they stay put (HS:262).
16. IF a swipe goes back past the first page THEN it hands over to Glance only when Glance is on, not editing, no card open; else it opens Comms (HO:111; HS:1085).
17. IF Pages Off hides pages THEN the stored layout keeps them; `preserve` set keeps keys of hidden pages through saves (HS:324-330).

### B2. Left page (Comms) and right side
18. IF `leftPage` = Off THEN the edge handle and swipe vanish (HC:39); the corner actions "Notifications" and "Open Comms" open the standalone hub instead (HS:569-571); hub Rail/WaitingIndicator both off (HC:40-41).
19. IF leftPage = Launchpad THEN "Unread strip" ON draws the Rail; OFF draws the waiting dot/pill. Settings shows "Unread strip" regardless (SC:500). HC:40-41.
20. IF leftPage = Widget THEN the swipe opens the chosen full-screen widget; the "Notifications" corner action opens that widget page, not notifications (HS:569). `leftWidgetId` is device-bound (DeviceBound.kt) and cleared after a platform restore (PlatformRestoreGuard.kt:48-55) leaving leftPage=Widget with no widget.
21. IF a Workspace without a left widget is activated while leftPage = Widget THEN leftPage is set to Off and the id cleared, changing a global setting (DockLayer.kt:90-92).
22. IF rightSide = RecentsPanel THEN handle + edge swipe + corner/gesture "Open recents" open the panel (HC:42; HS:972); IF DockRail THEN the swipe reveals the dock only if the dock is enabled (on Glance forced: HC:52, HS:920), otherwise a toast says why (HS:537-548); IF Off THEN "Open recents" toasts "The right side is off..." (HS:556).
23. IF recentsUseUsageAccess is ON AND usage access is not granted THEN only apps opened from Liftoff are listed, and the panel shows a grant row (HS:1074); the toggle never prompts itself (SC:283).
24. IF homeOrder = RightToLeft THEN pager `reverseLayout`, Comms/Recents edges swap, columns fill from the right (HS:953, 1118, HO:12-60). The enum comment says hotspot left/right roles swap, but `HomeOrder.corner()` has no caller in main code (verify; Settings hint at SC:269 does not claim it). Language does NOT change the order: Arabic with Left to right keeps LTR home (HS:953 comment).

### B3. Hotspots, corners, gestures
25. IF a corner is disabled in `glanceHotspots` THEN it draws nothing and runs nothing; its assignment is kept (HS:468-469, GM:50-55). Preset chips Four/Two/One/None; a 3-corner set is "Custom": the quick sheet shows no chip selected, Settings highlights "Four" (SC:818-821).
26. IF cornersFollowDevice THEN the stored PHYSICAL corner follows rotation (Corners.kt:94 `cornerPlacements`) and the enabled set uses the same mapping (GM:50). OFF = screen corners.
27. IF cornersPerOrientation THEN landscape uses `lcorner.*` slots, which fall back to the portrait slot snapshot taken at store construction (LS:455-457 `readLandscapeCorners`); later portrait edits do not update the landscape flow until restart (verify).
28. IF a corner slot is a Palette/Complication/Widget THEN touch draws the full control, but hot-corner/keyboard activation runs only the first palette item and ignores widgets/complications (GM `itemOf`, HS:663).
29. IF `cornerIconMode` = AppIcon THEN corner glyph = real icon of the app an action opens; the corner styles Holo / Sony quarter / Bubbles change drawing only.
30. IF the long-press gesture is bound THEN the Quick sheet and Settings jump are unreachable by long press on empty space (HS:623, 1180). Nothing warns about it.
31. IF a swipe is along the home's scroll axis THEN the gesture cannot fire: `unavailableReason` (HM:546-560); Settings annotates "not on <Style>: ..." but still stores the binding. Defaults: SwipeLeft = Radar is dead on the default horizontal paged home; SwipeRight reserved for Comms when a left page is on (HM:552-556).
32. IF "Pinch changes grid density" is toggled THEN PinchIn/PinchOut are overwritten with Denser/Sparser; toggling off sets PinchIn=Radar and PinchOut=nothing, losing any prior custom binding (SC:527-546). Visible only when the surface has a density (not List/PSP/DSi).
33. IF motionEnabled THEN sensors listen while home resumes (HS:731-738); IF `motionInBackground` THEN a foreground service runs and POST_NOTIFICATIONS is requested without explainer (HS:748-756). Default bindings (shake=vibrate mode, flick twice=silent, flip twice=flashlight) are active from first run; the silent/DND ones need DND access, which opens the system page the first time (ActionRunner.kt:222-262).

### B4. Drawer disc
34. IF ringSegments (6-8) is lowered THEN slots beyond N are kept (store always keeps 8; PuckActions.kt:32-34, 138-170) and reappear when raised.
35. IF a ring slot is an Action needing hardware/permission THEN it is dimmed/unavailable: Flashlight without a flash, Do-not-disturb and Silent without DND policy access (`PuckAvailability`, PuckActions.kt:204-209).
36. IF the work profile does not exist THEN `wheelWorkOnly` is ignored and the Work label is not drawn (workProfile.md; HS:1470-1475).
37. IF a work profile is paused THEN its apps are dimmed, disabled and unlaunchable in the disc; home chip shown on non-Glance surfaces unless the disc is open or editing (HC:44, HS:1793); Radar and search still list them.
38. IF `wheelGrouping` = Recent THEN the grouping uses the launch history (HS:1460 `recentKeys`).
39. IF a user hides an app THEN it disappears from home, disc, Radar and Recents (`apps` filtered, HS:200-203) but stays in layout keys; Radar shows "N hidden apps match" (HiddenAppsModel.kt:50).

### B5. Radar and web
40. IF `searchLayout` is chosen in Settings THEN `searchLayoutPinned` becomes true (SC:620-623); IF pinned is false and a keyboard/pointer is in use THEN Radar opens Flat (`RadarLayoutPolicy.effective`, SearchOverlay.kt:312). Turning the toggle off is undone by the next layout pick.
41. IF `searchWeb` is OFF THEN no web rows, no Play Store rows, and live suggestions are off even if `webSuggestions` is ON (SearchOverlay.kt:374 `liveOn = webSuggestions && searchWeb`); Settings still shows Web suggestions enabled (SC:632).
42. IF webSuggestions is ON THEN typed text (2-100 chars, not URL/email/phone) is sent after an 800 ms pause to the chosen engine in Web/Everything scopes and to Google Play in Apps/Everything; Off = no network from the launcher at all (radarWebSuggest.md; WebSuggestHttp.kt; manifest INTERNET, no runtime prompt). Default is ON on a fresh install.
43. IF the web engine is Ecosia or Startpage THEN no suggestion endpoint exists, only the search row (HM:102-117).
44. IF `searchActions` is OFF THEN Radar also stops returning Settings rows (SearchOverlay.kt:396), because settings results ride on "Actions".
45. IF `searchContacts` is ON and READ_CONTACTS is missing THEN Radar asks via `onRequestContactsPermission` through the explainer (HS:1424).  IF `searchFiles` is ON and there is no folder grant or media permission THEN the Files scope shows "Choose folders" via the explainer (HS:1419).
46. IF `searchMotion` is OFF OR system animations are reduced THEN Filmstrip/Orbit stay still (SearchLayouts.kt:336).
47. IF `radarBackground` = TrueBlack THEN no wallpaper behind Radar; Dimmed shows the Glance wallpaper at 25% (SC:627).

### B6. Comms: notifications, read state, rules
48. IF the user dismisses an item in Comms (swipe, or the Dismiss action) THEN it is always marked read (`LaunchpadSettings.markRead`, toast "Marked as read", Launchpad.kt:528) and ONLY IF `clearFromShade` (default true) is it also cancelled from the system shade (Launchpad.kt:387). With the toggle off, "dismiss" = mark read; the item stays in the shade.
49. IF "Clear all" (Comms or Settings "Clear all notifications") THEN it cancels everything from the shade regardless of `clearFromShade` (Launchpad.kt:388, SC:518); history stays. "Delete history" removes history only; "Mark all as read" marks live and history.
50. IF a notification is read and the system cancels it THEN it stays listed in All via the merged history until "Delete from history" / "Delete history" / the 5000-entry bound (commsRead.md). The Read/Unread/All filter is state of the All view, set by pinch or Ctrl+U.
51. IF rules conflict THEN most specific matcher wins (sender > text > channel > app > global), ties to most recent; a Hide rule is rescued only by a strictly more specific Pin/Important (LaunchpadRules.kt:79, 200-221). AutoClear removes an item n minutes after posting (:221).
52. IF an app-wide Hide rule exists THEN the app is listed under Hidden apps with source "Hidden in Comms by a rule"; unhiding from there or from Radar clears all Hide rules naming that package, including narrower ones (HiddenAppsModel.kt:30-60, HS:1410).
53. IF a Priority group matches THEN its rows bypass digest/silent rules and count in the indicator; Quiet group items never count (docs/decisions/groups.md). IF a group condition needs contacts or last name THEN without READ_CONTACTS or a configured last name it never matches (GroupRuleEditor.kt:203-204).
53a. IF a notification is ongoing (FLAG_ONGOING_EVENT / FOREGROUND_SERVICE / NO_CLEAR) or a live one of a sticky category (transport, navigation, stopwatch, service, progress with a bar, call with the call style, workout, location sharing) THEN it is pinned: `NotificationClass.isPinned`; shown in the Pinned block at the top (setting Show pinned at the top), never unread (excluded from counts and the indicator, also in Priority groups), cannot be dismissed or marked read, folded to a compact row above three; Pin-by-rule rows join the block when there are no groups. Details: docs/decisions/commsCategories.md.
53b. IF a category rule exists THEN it decides Messages / Notifications / Alerts; else promo or recommendation = Notifications; else alert signals (alarm, reminder, event, stopwatch category, time-sensitive mark, OTP text, security or bank text, urgent arrival, err/sys/status with high importance) = Alerts; else message signals (msg, email, call, missed call, MessagingStyle, social with a person) = Messages; else Notifications.
54. IF notification access is not granted THEN Comms is a GrantGate screen (Launchpad.kt:811), badges are empty (HomeBadges.kt:54), the music line has no session (MediaNow.kt:165), and the dock/recents use their own grants.
55. IF `badgesOn` is OFF THEN "Count in badges" is hidden (SC:370).
56. IF a Workspace defines a notification scope THEN the notification feed (`NotificationProvider.displayFilter`, NotificationAndMedia.kt:21) is narrowed to those packages while it is active (DockLayer.kt:80).

### B7. Wallpaper
57. IF `pagesSameAsGlance` (default true) THEN the pages and the Continuous scroll draw the Glance wallpaper; ELSE they use the `wpPages*` set (Wallpaper.kt:52-53). The Settings row "Pages wallpaper" appears only when not same (SC:799).
58. Five kinds per surface: Solid, Gradient, Signal (live two-slot background, default Spire + Breathing dots), System live wallpaper (launcher draws nothing, optional dim), Picture (still / GIF / WebP / video). All kinds' settings are kept when switching kind (Wallpaper.kt:33).
59. IF the kind becomes Picture AND the picture plays AND `powerOk` was never confirmed THEN it starts as a Still and the power dialog "Animated wallpapers use more power" is asked when animation is turned on; a standing warning line stays while an animated/video wallpaper is set (BackgroundPane.kt:76, PicturePane.kt:179, 215-225; PowerWarning WallpaperPicture.kt:121).
60. IF battery saver is on, system animations are reduced, or the surface is not visible THEN animated pictures play as Still with a note (`GifPolicy.plan`, WallpaperPicture.kt:136-141, PicturePlayback.kt:162). Bounce needs a GIF (animated WebP loops).
61. IF a signal-driven wallpaper/indicator is used THEN the Indicator metric (Signal or Battery) feeds colours; thresholds low/mid/high set in Look > Indicator (SC:644-690). Calm and Alert effects are independent and either may be null (HM:347-348).
62. Picture files, video and the picture-describing prefs are device-local: never exported or backed up; a restored install whose kind is Picture draws the solid colour (StateSchema.kt:62-66).
63. Settings backdrop (Wallpaper dimmed vs Pitch black; dim slider only for Wallpaper SC:608) uses the Glance wallpaper.

### B8. Look, clock, language, motion
64. Clock style: IF Off THEN date/colour/size rows are hidden (SC:806); IF a digital style THEN Clock format row appears and lists only 12-hour / 24-hour, so the stored default `System` is displayed as "24-hour" (SC:807-810); Word and analog styles hide it. The clock TILE has its own System/12/24 (ItemSheet.kt:95) and `ClockFormat.System` there. Three unrelated clock settings: Glance clock (Settings), clock tile (item sheet), "Time" complication.
65. IF themeMode = Amoled/Dark/Light THEN the Glance surface and Comms use `HyleTheme` (HS:762-766); accent feeds icon tint = Accent.
66. IF a theme pack is applied THEN only mode, accent, icon shape, icon tint, dock style, and the GLANCE wallpaper change; the Pages wallpaper is untouched if `pagesSameAsGlance` is false; the previous look is kept in ONE in-memory undo that is lost on process death (ThemeLook.kt:49-60, 88-93; ThemeApplier :107-112). "Reset to default look" also only restores the Glance wallpaper (ThemeLook.kt:32-35).
67. Language (`UiLanguage` 8 languages + System): changes strings only (`L10n.setLanguage`, HS:766); it never changes home order, Glance direction or page order. Right-to-left layout of Settings follows the platform locale, the home follows `homeOrder` (HS:953).
68. Reduced motion has NO launcher setting; it reads the three system animation scales (`rememberMotionPrefs`, ReducedMotion.kt:21-39) and: snaps the pager (HS:256), shortens edit chrome to 120 ms (HomeEditChrome.kt:131), replaces morphs with fades (Comms card, notch), stops Radar drift, plays GIFs as stills, disables the marquee. It does not gate page transitions (B1-13).
69. Interface sounds: `uiSounds` + `uiSoundVolume` (HS:389-390); Launcher vibration strength is a separate store reachable only in Comms (A10) and filters every haptic (LauncherHaptics.kt:26-30).
70. `hideStatusBar` (default true) hides the system status bar on home and re-applies on every Home press (HS:799-805).
71. `restoreOpenSurfaces` (default off) reopens the disc or Radar after restart, never in safe mode (HS:342).
72. `alwaysKeyboard` lifts corners above the keyboard (HS:850 `bottomClear`); `keyboardOnOpen*` are per orientation and independent.

### B9. Dock
73. IF dock enabled setting is null THEN phones (tier T1) have no dock, tablets/DeX do (DockModel.kt:77); IF Right side = Dock rail THEN Glance forces the rail visible (HC:52).
74. IF the dock has no pinned items and nothing recent THEN it is not drawn and reserves no room (HS:922 `DockVisibility.shown`, DockModel.kt:191).
75. Dock style Standard / Transparent / 3D is stored in the dock settings, travels with the `dock` export section, and is the only look setting there; the Settings row in Home screen says the dock on/off lives under Desktop & dock (DockThemesSettings.kt:34).
76. While editing, the dock rail is dimmed (not removed) and the bottom hotspots dim to 40% and become inert (HS:1096, 1243; EditChromeGeometry FIXED_DIM).
77. Dock chrome on Glance is hidden unless Right side = Dock rail (HC:43).

### B10. Backup, restore, migrations
78. Export file = `liftoff-state` v3 (StateSchema.CURRENT) with sections layout, settings, shortcuts, launchpad, paletteAliases, workspaces, sessions, widgets, dock, prefFiles (StateSchema.kt:46-65). Settings document is separable (`liftoff-settings` v2). Unknown fields survive round trips.
79. NEVER exported and excluded from system backup: launch history, search habits, icon_colors (per-app icon tint), palette file/index, wallpapers (pictures/videos), notification history, timeline log, recents state, `launchpad_hub`, `launchpad_groups`, `desktop_surface`, `liftoff_misc`, `preview_window`, `edit_chrome` (StateSchema.kt:94-105; backup_rules.xml). Consequences: Comms "Default view" and "Clear from the shade" (stored in `launchpad_hub`, HubModel.kt:105-124; not in PrefFilesSource ALLOWED, StateLiveSources.kt:237) are lost on export/import, backup restore and device transfer; per-app icon colours are lost too.
80. Device-bound keys are stripped on import: `leftWidgetId`, `bgPicture`, `onboardingDone`, picture keys (StateSchema.kt:62-70). IF the platform restores a backup on a new device THEN `PlatformRestoreGuard` removes corner and left-page widgets (they become "Widgets to add again", BackupPane.kt:199-209) (PlatformRestoreGuard.kt:25-55).
81. Every import takes a snapshot first; destructive edits (delete a page, clear a corner or gesture, delete a Comms group/rule/link) take one too; last 5 kept (CFG-04; LS:446, 473).
82. RetiredPrefKeys: keys of retired features (background gallery, drawerStyle/View/Sort/Rail/Switch, glanceWeather, suggestion, minimalHints, networkDots, switcherStyle, prefixes, etc.) are deleted once per install (marker `retired_pref_keys_w38` in no-backup dir) and dropped on import (`DROPPED_ON_IMPORT`, RetiredPrefKeys.kt:20-30, 55-65). Migration-source keys (`indEffects`, `glanceContent`) are removed only after their replacements exist (`AFTER_MIGRATION`). Clock keys (ClockRetired, GM:116), `homeLanding/landingChosen/homeScroll/pagesModeChosen` (GM:77) are deleted on the next prefs save (LS:104-120).
83. `homePages` migration: stored value wins; else old `homeScroll=Continuous` => Continuous; `pagesModeChosen=true` => Paginated; else Paginated if the layout holds built pages, otherwise Off; written once at store init (GM:121-129, LS:55-59). `glanceEnabled` is true for every old landing.
84. Indicator migration: old `indEffects` set => `indCalm`/`indAlert` once (LS:99-108).
85. Fresh-vs-existing detection is "the file already has `style` or `pCols`" (LS:257, 276), so a fresh install that restores partial prefs counts as existing.

### B11. Edit mode / page editor interactions with prefs
86. Edit mode Done commits layout; Cancel reverts layout, extras, dock pins and clock tile options only (HS:692-697). Quick sheet / page-editor Settings tab / hotspot toggles / wallpaper-same toggle write prefs immediately and survive Cancel.
87. IF edit mode is active THEN the outer pager scrolling, Comms/Recents edge drags and the back-swipe to Glance are disabled (HS:1119, 1003, HO:111). IF the pager settles on Glance THEN edit mode ends (HS:633, HSN:18).
88. IF Settings is opened while a page is current THEN "Page N" is that page (HS:419 `openSettings(page)`); the page category exists even with Pages Off or when Glance is on (it uses `isPaged` only).
89. IF a density change leaves gaps THEN on Settings close the user is asked to fill (HS:825-834).

---------------------------------------------------------------------------------------------------
## C. Permissions and grants map

| Access | Declared | Needed by (setting / feature) | Requested where | Without it |
|---|---|---|---|---|
| Notification access (listener) | BIND_NOTIFICATION_LISTENER_SERVICE | Comms (whole left page), notification badges (`badgesOn`), Glance/pages music line (`showMusic`) and Comms/Notch media controls, unread rail, Comms Timeline | GrantGate "Show notifications here" from Comms (Launchpad.kt:811), Settings > System & sound > Notification access (HS:1611), Settings > Comms > Access pane (SC:516), corner "Notifications" | Comms shows the grant screen; no badges; no music line or controls; hub empty; the Settings toggles stay on but do nothing; no prompt when toggling `badgesOn` or `showMusic` |
| Usage access | PACKAGE_USAGE_STATS | Recents panel "Include apps opened from elsewhere" (`recentsUseUsageAccess`), dock "Recent", Comms Timeline usage block | GrantGate "Show recent apps" from the Recents panel row (HS:1074), dock (DockHost.kt:228), UsageTimelineBlock.kt:204 | Only apps opened from Liftoff are listed; dock shows no recent; usage block asks |
| Contacts | READ_CONTACTS | Radar People scope (`searchContacts`), Comms group conditions (person/last name), person links | GrantGate "Find people" from Radar (HS:1424), Groups pane (LaunchpadGroupsPane.kt:189), Access pane | No people in Radar; group rules needing contacts never match (GroupRuleEditor.kt:204) |
| Folders (SAF tree grants) | none (picker) | Radar Files scope (`searchFiles`), "Search folders" pane | GrantGate "Search your files" (HS:1419), Access pane | Files scope empty |
| Media read | READ_MEDIA_IMAGES/VIDEO/AUDIO | Radar file index (FileIndexAndroid.kt:67-77) | NEVER requested at runtime (no request code exists; grep RequestPermission shows only mic, contacts, POST_NOTIFICATIONS) | media index only if the user grants it in system App info; folders still work |
| Microphone | RECORD_AUDIO | Radar voice button | GrantHooks.ask Microphone (VoiceEntry.kt:196), Access pane | voice search unavailable |
| Do Not Disturb policy access | ACCESS_NOTIFICATION_POLICY | Corner/gesture/ring actions Ring mode, Vibrate (only when leaving/entering silent), Silent, Do not disturb; default shake/flick bindings | System page opened directly with a toast, no explainer (ActionRunner.kt:222-262); Settings > System & sound > Do Not Disturb access (SC:714) | action toasts "Allow Do Not Disturb access..." and opens the page; ring slots show dimmed (PuckAvailability) |
| Post notifications | POST_NOTIFICATIONS | foreground service notification of background motion gestures | system prompt when `motionInBackground` turns on (HS:748-756), no explainer | service runs; toast "Motion gestures still run; their notification is hidden" |
| Foreground service (special use) | FOREGROUND_SERVICE(_SPECIAL_USE) | `motionInBackground` (MotionGestureService) | none | n/a |
| Internet | INTERNET (normal, no prompt) | ONLY Radar live suggestions (`webSuggestions` && `searchWeb`) via WebSuggestHttp.kt; everything else is offline | n/a; privacy switch = Settings > Radar > Web suggestions (default ON) | Off = no network at all; with it off Radar shows a single search row |
| Home / role | n/a (role request) | being the default launcher | Onboarding step 1, "Make default" whisper, Settings > System > Set as default home | home works as an app; work-profile quiet mode "needs the launcher to be the default home" (workProfile.md) |
| QUERY_ALL_PACKAGES, REQUEST_DELETE_PACKAGES | declared | app inventory, uninstall confirmation | n/a | |
| Package queries / `READ_SEARCH` | declared | third-party search providers approved one by one in Settings > Radar > "Search providers from other apps" (PAL-17) | per-provider approval in the pane | provider results absent |
| Widget bind | system consent | corner/left/page widgets | WidgetHosting.kt, per widget | widget not added; after restore "Widgets to add again" |
| Declared but unused | ACTIVITY_RECOGNITION, health.READ_* (steps, floors, elevation, distance, calories), ACCESS_WIFI_STATE (used by Connectivity sheet only), VIBRATE | no live consumer: `StepsSource`/`Health.kt` are not referenced outside core-data (status strip removed in wave 38); `HealthSourcePref` enum has no reader | n/a | Android lists these permissions in App info although nothing asks |
Not present at all: location (weather and `ACCESS_*_LOCATION` removed), accessibility service, overlay, WRITE_SETTINGS (so the vibration slider is launcher-only), SYSTEM_ALERT_WINDOW. The Access pane (Settings > Comms > "Access Liftoff asks for", AccessLogic.kt:15) lists only the five `GrantKind`s, so DND access, POST_NOTIFICATIONS and media are not in it.
Grants are asked "just in time" with one explainer screen (CFG-07, GrantGate.kt:62-90); "asked before and still off" shows a second variant text. A grant is never requested from onboarding.

---------------------------------------------------------------------------------------------------
## D. Defaults and first-run state (brand-new install)

What the user sees: onboarding overlay (3 skippable steps) over the Glance home. After it: the Glance page only (Pages = Off), black/dark background with the default two-slot Signal wallpaper (Spire + Breathing dots) and calm animated dots effect, a big digital clock (system 12/24 format, no date, size Large), the music line when something plays (needs notification access), four hotspots, one page indicator dot only if `glanceIndicator` (default on), no dock (phone), no pages, one empty stored page, no hint visible (the "Long press to add apps" hint only shows on the Pages surface, which does not exist while Pages is Off). Long press on Glance opens the Quick sheet; the Pages chips there (or Settings > Edit home) are the way to get pages.

| Item | First-run value | Notes / differs from the data-class default? |
|---|---|---|
| homePages | Off | store writes it once at init (LS:55-59) |
| glanceEnabled | true | |
| appsOnHome | PinnedOnly | NON-DEFAULT: data class default is Everything (HM:341); store picks PinnedOnly for fresh installs (LS:276) and HS:317 forces it every start |
| homeStyle | Grid ("Pages") unless changed in onboarding | the choice has no visible effect until Pages is turned on (B1-1) |
| onboardingDone | false until finished/skipped | derived true when the prefs file already has style/pCols (LS:257) |
| home layout | one empty page id 1, `seeded=true` (FreshHome.emptyLayout, HomePageOps.kt:109) | nothing placed for the user |
| Corners | Camera TL, Music TR, Radar BR, All apps BL (HM:464-469) | `MinimalHotspots.defaults` (Radar, Comms, drawer, Settings) exist but are unused since wave 16 |
| Hotspots enabled | all four | |
| Gestures bound | PinchIn Radar, SwipeDown Notifications, SwipeUp All apps, SwipeLeft Radar (unavailable on a horizontal paged home), Shake Vibrate mode, Flick twice Silent, Flip twice Flashlight; LongPress/DoubleTap/PinchOut/FaceDown unbound (HM:555-563) | motion gestures ON in foreground (`motionEnabled` true, sensitivity 0.5, background off) |
| Order | Left to right | |
| leftPage | Launchpad (Comms), rail off (dot form), theme Dark | |
| rightSide | Recents panel; `recentsLimit` default from HomeRecents; usage-access toggle ON | |
| Dock | off on phone (tier default), style Standard, mode pinned+recent | |
| Theme | Dark, accent 0xFF8E7BFF, icon shape Rounded, tint None, plate ON, language System | |
| Icon scale 0.82, labels OFF, badges ON with counts | `iconSpacing` 0, `gridBias` 0.4, margins 0 | |
| Grid | DensityPreset.Default portrait/landscape (HM:62) | adaptive grid off |
| Page turning | Sideways both orientations | |
| Page transition | Slide | settable only in the Quick sheet |
| Indicator | Signal metric, calm AnimatedDots, alert none, intensity 0.6, speed 0.5 | |
| Radar | layout Filmstrip, unpinned (flat when a keyboard is used), background Dimmed, web ON, web suggestions ON, actions/files/deep/contacts ON, motion ON, engine DuckDuckGo | the only network use is ON by default |
| Sounds / haptics | interface sounds ON, volume 0.4, launcher vibration Medium | |
| Status bar | hidden on home | |
| Restore open screens | off | |
| Always-on keyboard | off; keyboard on open off both orientations | |
| Hot corners | ON, 150 ms | |
| Comms | default view All, clearFromShade ON, scale 100% | stored in non-exported `launchpad_hub` |
| Drawer disc | 8 ring segments, defaults flashlight, alarm clock, notifications, Do not disturb, Radar, camera, calculator, system settings; labels off; grouping A-Z; work-only off | |
| Edit hint counter | 0 (shows 3 times) | device-local |
| Safe mode | after two crashes at start: widgets/extras off (CrashLoopGuard, CFG-09) | |
Migrations on first start for an UPGRADED install: layout auto-fill reset (B1-9), `homePages` from old scroll/paginated choice (B10-83), `glanceEnabled` true, retired keys purged once.

---------------------------------------------------------------------------------------------------
## E. Duplicate / ONLY-HERE summary for the IA redesign

Settings that live ONLY outside Settings: page transition and Make this home (Quick sheet); clock tile style/hours/date (item sheet); icon pack and themed-icons (item picker sheet reached from Settings > Home screen > "Widgets, folders and icons"); per-app icon colour and tags (app menu); wheel Work-only scope and work pause (disc); Radar result text size (pinch); Launcher vibration strength (Comms Sound sheet); Comms Default view and Clear from the shade ARE in Settings but their storage is not backed up.
Settings that exist in 2+ places: Pages (Quick sheet, Settings, and implicit via Edit home), Order, Hotspots (Quick sheet chips, Settings preset + 4 toggles, page editor 4 toggles, corner long press), Pages-same-wallpaper (Settings, page editor), Show music (Settings, page editor), Align top/bottom vs "Height in page", Wheel grouping (disc, Settings), Dock position/hide/style (dock menu, Settings), corner assignments (corner long press, Settings Corners), Delete page (Quick sheet, page editor strip, Page N), Hide app (menu, item sheet, Hidden apps pane, Radar row), Unread Comms options (Comms menu, Settings Comms).
Same label, different meaning: "Pages" (Home screen: Off/Paginated/Continuous; App drawer: Multiple/One page), "Settings" (the tile, the category, Ground Control), "Home settings" (corner action; app-menu row; Settings), "Groups" (App groups, Comms groups), "Clock" (Glance clock, clock tile, Time complication, Clock action), "Rules" (Comms rules vs group rules), "Calm centre" (workspace filter label for a Glance that no longer shows notifications; the filter now narrows the Comms feed).

# Wave 48: popupPages (CFG-21)

Owner rule: full-page menus (One UI style) where there are popups, not where the options are already outside; follow Samsung One UI.

| ID | Status | Note |
| --- | --- | --- |
| CFG-21 | built | The remaining popups were sorted by what they hold. Choice from many became a full page; editors with several fields became a full page with the large title centred in the empty band under the back arrow and the Save and Cancel text buttons in a footer under the content; small confirmations and short entry stay One UI dialogs. Details below. |
| STR-13 | built | The Launchpad rule editor's two condition-kind dropdowns are one full page of radio rows. |

## Converted
- **Launchpad rule editor, condition kind** (feature-launchpad, `GroupKindPage`): "Add a condition" and the kind chip of an existing condition no longer open a `DropdownMenu`. Both open a full-screen page (back arrow, title, one radio row per kind, the current kind selected); a pick applies and returns, back changes nothing. Changing the kind keeps the Not flag, as before. The module does not depend on feature-grid, so the page is drawn in Launchpad's own colours (`LocalLp`). New string "Condition" in `L10nW48popupPages.kt` (8 languages).
- **`GlassDialog` editors** (feature-grid): the one chrome all of these use was rebuilt on `OneUiFullPage` plus `OneUiPageFrame` (the settings page frame), so the corner editor, the gesture binding editor, the drawer-colour editor and the keyboard-shortcuts sheet are now One UI pages: large title centred in its band collapsing into the bar, content in the list, a footer of flat accent buttons that stays under the scrolling content. The `scrollable` parameter went (its only user was the app picker).
- **Single app picker** (`AppPickerDialog`, used by drawer shortcuts, the Radar action apps, the Home gesture/action pickers): a page with the search field and the suggested and all apps as lazy rows; the "Ask me each time" reset is a flat text button under the list.
- **Drawer shortcut slot** (`PuckActionsPane`, app): the Empty / App... / actions list is an `OneUiRadioPage` (was a `GlassDialog` of rows); "App..." still opens the app picker page and Back from it returns to the slot page.
- **Edit dock** (feature-dock, `EditDockDialog`): a page (pinned items in one rounded card with the three-dot menu, Add apps button). Every change still applies at once. The old Done button is the back arrow.
- **New app group** and **Save session** (feature-dock): pages with the fields, the layout segmented control, the apps row and a Save button (the dialog tag `oneui.dialog.confirm` kept; Save is disabled until the group has a name and 2 to 4 apps).

## Stayed a popup, and why
- **Schedule ("Remind me to open ...")** stays a One UI dialog: it is a clock time picker with an hour/minute stepper and an "In 1 h" shortcut, which One UI itself shows as a dialog.
- Confirmations (`OneUiConfirmDialog`, the page delete confirm, Restore pages), the short text entries (`OneUiTextEntryDialog`: workspace and group naming), the rail's About dialog and the dock style chooser stay dialogs or already were pages.
- Context menus (the dock menu, the item three-dot menus, the Desktop surface item menus, the timeline event menu, the search scope menu) stay menus, per the wave 46 rule.
- Not touched, other modules' own looks: `RadarPalette`, `RadarPicker`, `HomeItemsDialogs`, `WidgetPicker`, `FolderUi`, `DrawerTabs`, `SearchLayouts` dialogs, `AppContextMenu`, `FilesUi`, `GrantSupport`, `CrashRecoveryActivity`. Not reviewed for conversion in this wave; they are choices or sheets with their own surfaces (the Radar and Files ones are not settings popups).
- The Save button of the new pages is in the footer, not in the top bar: the shared One UI top bar has no trailing action and its file is shared with other streams. Moving it would be a change to the settings top bar.

## Tests
- Updated: `W4RadarInputDockTest.INP_20_everyDialogWindowInTheDockModuleStartsWithTheDialogHook` (the audit also counts `OneUiFullPage(` users, since the dock's pages are page windows that take the hook through `DockPagesScope`); `PuckActionsPaneW30Test` and `DrawerRingW38Test` (the option "Maps" is below the fold on the page, so the test scrolls to it).
- New: `PopupPagesW48Test` (feature-grid, 3: editor is a page with large title and footer, back leaves, app picker page); `LaunchpadGroupsCases` x2 orientations (3 each: kind page adds and returns, back adds nothing, change keeps Not and shows the current kind selected); `EditDockPageW48Test` (feature-dock, 3: page not dialog and hook called, row menu reorder/unpin and Add apps, back and empty text); `PuckActionsPaneW30Test.CFG_21_w48_...` (app: radio page, current selected, back changes nothing).
- The group editor and session pages hold text fields, which never let the Compose test rule idle inside a dialog window (see the note in `W4RadarInputDockTest`); they are driven by the existing `DockDialogsW24Test` (app), which passes against the pages unchanged (typed keys, picker over editor, B closes, Save).

## Test run
- feature-grid, feature-launchpad, feature-dock, core-design, core-quality: all green (full module runs).
- app (full run, before the fix below): 11 failures: `DockWiringTest` x8 and `PackageRemovedW26SmokeTest` (the known flaky ones, Compose-not-idle / timing), `PageEditorAppTest.HOM_12_ed10_continuousTap...` (passes alone; not touched by this change, treated as flaky), and `DockDialogsW24Test.INP_20_theSessionDialog...`. That last one was the "dialog left showing" failure: the test reopened the session window straight after Save, before the first window's dismiss had run, and asserted emptiness with one idle. Fixed in the test: `ShadowDialog.reset()` per test, `showing()` ignores windows of a destroyed activity, and `noneShowing()` idles in steps until the window is gone, asserted after Save as well as at the end. Three consecutive runs of the class green afterwards.
- `DockWiringTest` and `PackageRemovedW26SmokeTest` were not re-run after the change; they do not touch these pages.

## Not matched, device-only
Nothing here is device-verified. No screenshots were taken: the pages are separate windows, which the activity-view screenshot helpers do not capture. Not checked: how the pages look and scroll on a device, the IME raising over a page, controller keys on the new pages on real hardware (the hook is called, tested), TalkBack reading of the page windows, the enter animation of the page windows.

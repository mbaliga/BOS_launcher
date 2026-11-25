# Wave 57: settingsTabs (CFG-40 to CFG-44)

Owner: "Keep our style of tabs however, so that the 6 sections are navigable, clear and consistent." Nothing was run on a device. Verified by JVM unit tests, Robolectric UI tests and by reading screenshots (`app/build/screenshots/w57-*.png`: the six tabs dark, Home light, Home scrolled with the tabs pinned, the landscape rail; `feature-grid/build/screenshots/w57-tabs-indicator.png`). Decisions: `docs/decisions/settingsTabs.md`.

| ID | Status | Note |
| --- | --- | --- |
| CFG-40 | built (device untested) | Six tabs placed by one table (`SettingsTabLayout`); every built row lands in exactly one tab, nothing orphaned or twice, every table row real, counts per tab (Home 87, Look 21, Apps 9, Radar 16, Side panel 20, System 17 in the fullest state), the owner's section headers, a reset per tab. Root list and category pages removed. |
| CFG-41 | built (device untested) | Liftoff text tabs (capitals, tracking, accent line that slides with the swipe, dim others; tighten padding and tracking before the row scrolls), `HorizontalPager`, collapsing header with the tab row pinned under the bar, remembered tab (`liftoff_misc`), deep links open the tab and flash the row, search hit switches tab, landscape vertical rail, RTL mirrored, Ctrl+Tab / Ctrl+Shift+Tab / L1 / R1, tab list semantics. |
| CFG-42 | built (device untested) | System > Access and privacy: notification, usage, contacts, folders, microphone (existing explainers), Do Not Disturb and default home with their states; replaces the three System rows and the Comms access page. |
| CFG-43 | partial | Launcher vibration on the shared `LauncherHapticSettings` store; Open Liftoff Studio page and Import a Studio file. The Studio address is a placeholder (example.com) until the site is published. |
| CFG-44 | built (device untested) | Page list cards in Home > Pages pick the page the per-page rows edit; "Ground Control" reads "Settings" in the UI (sheet row, shortcut command, toasts), "ground control" kept as a hidden Radar search word. |

## Owner clauses, where they are
- Six sections navigable: tab row (tap, swipe, keys), each section's rows one swipe of its list away; two taps at most for anything but editors.
- Our style of tabs: the Comms capitals and the page editor's accent line (`LiftoffTabRow`); the page editor's own row was not restyled (other stream's file; noted in the decision).
- Clear and consistent: one placement table, the same tab look in portrait and in the landscape rail; Finish setup stays above the tabs.

## Old tests changed (behaviour replaced by the owner's tabs)
- `SettingsOneUiW40Test` (app): root-list, summary, section-table and root screenshot tests removed (replaced by `SettingsTabsW57ModelTest` and `SettingsTabsW57ShotsTest`); deep links, Edit home, Hidden apps and search now go through the tabs.
- `SettingsHomeW52Test`: the "Pages" root row, summary lines and the Workspaces root row tests replaced by: Side panel tab name, comms search keyword, Workspaces first in Apps, font cards.
- `SettingsCoreW56AppTest`: categories by tab (Home, System), the section tests check the tab placement headers and unique ids, every tab has a reset.
- `SettingsOneUiUiTest` (feature-grid): root list tests replaced by tab tests (settings home with tabs, tap, back closes from a tab, search hit switches tab, deep link opens the tab, the title leaves with a drag, landscape rail, RTL tab order, tab list semantics); slide and Esc tests use a row's page; the focus walk allows for the tabs before the rows; root and category screenshots removed (the real tabs are shot in the app).
- `OneUiModelTest`: grouping, accent, summary and category navigation tests removed; key recognition and back rules now on the tabs.
- `SettingsCoreW56Test`, `SettingsCoreW56UiTest`, `SettingsLinesW54Test` (root divider test removed: no root rows), `SettingsPagesW46Test` (scroll to the row first, the title collapses with a drag), `SettingsTitleFontW52Test` (a section's name is its tab), `InputTouchPathsUiTest.INP_07_back_dismisses_ground_control` (one Back closes Settings from a tab), `AppSmokeTest.HOM_22`, `TourBase`, `EditIntegrateW47AppTest`, `PageEditorAppTest` (navigation helper `SettingsTestNav`), `DockDeskReviewW24RulesTest` (the toasts say Settings, Side panel, Home, Dock).
- `SettingsOneUiSummaries.kt` deleted with the root list (its tests were in the W40 and W52 files above).

## Tests added
`SettingsTabsW57ModelTest`, `SettingsTabsW57ShotsTest`, `SettingsTestNav` (app); `SettingsTabsW57UiTest` with its JVM model tests (feature-grid). Strings: `L10nW57settingsTabs.kt` (38 strings, 8 languages).

## Results
See the end of this file.

## Device-only
Swipe feel and the indicator on a real screen, TalkBack wording ("Tab, 2 of 6"), a real controller's L1 / R1, a hardware keyboard's Ctrl+Tab, the collapsing header with a fling, the Studio link opening a browser, the Access and privacy states after returning from Android's pages.

# Wave 56: settingsCore (CFG-28 to CFG-33)

Nothing was run on a device. Verified by JVM unit tests, Robolectric UI tests and by reading screenshots (`feature-grid/build/screenshots/settings-w56-*.png`: root with the Finish setup row, the Finish setup page, a slider with its default tick and Default action, dimmed rows with their reasons, the reset confirm card, the Undo line, a search hit inside a pane; `app/build/screenshots/settings-w56-app-*.png`: the real Home screen with Pages Off and the clock Off, paged, Corners with hot corners off, Look, the root with Finish setup).

| ID | Status | Note |
| --- | --- | --- |
| CFG-28 | built (device untested) | Hidden keywords per row, pane rows as entries with stable ids (Backup, Access, Open with), hit opens the pane page and flashes the row. Themes, Drawer shortcuts, Workspaces, Search providers panes not declared (other streams' files). |
| CFG-29 | built (device untested) | Default tick, flat Default action, units on every slider, Auto for Spacing down. |
| CFG-30 | built (device untested) | Reset this page (8 pages) and Reset all settings (Backup), snapshot first, stacked confirm, Undo line. Preferences only; user data never reset. |
| CFG-31 | partial | Conditional rows of Pages, Home screen, Apps, Side panel and Hot corner delay stay dimmed with a reason; sections keyed by row id. Not done: Look block rows, the Gestures density row, dock rows when the dock is forced. |
| CFG-32 | built (device untested) | Finish setup row and page, refreshed on resume. |
| CFG-33 | built | Report-only help diet (below). |

See `docs/decisions/settingsCore.md` for the decisions.

## Changed old tests
- `HomeUnifyRowsTest.HOM_01_w28_addPage...`: Add page is now present for Off and Continuous but dimmed with a reason (CFG-31), so it asserts the reason instead of absence.
- `WallpaperRowsW26Test`, `PanelWidgetsSettingsW52Test`, `GlanceFixedW38SmokeTest` (GFX_41), `HomeUnifyReview2FlowTest`: the row is there and dimmed with its reason instead of absent.
- `SettingsOneUiW40Test` (section table rows exist): matches a row by id first, then title.

## Tests added
`SettingsCoreW56Test` and `SettingsCoreW56UiTest` (feature-grid), `PrefsResetW56Test` (core-data), `SettingsCoreW56AppTest` and `SettingsCoreW56ShotsTest` (app). Strings: `L10nW56settingsCore.kt` (36, 8 languages; hidden search synonyms are English only).

## Results
core-data 739, feature-grid 1682, core-quality 220 green. app 687: five failed on the first full run; four encoded vanishing rows and were updated (WallpaperRowsW26Test, PanelWidgetsSettingsW52Test, GlanceFixedW38SmokeTest GFX_41, HomeUnifyReview2FlowTest; reason: CFG-31 keeps the rows dimmed) and `DrawerRevertW29Test.HOM_01_w29_theAppsHotspotOnAPageOpensTheWheelDisc` failed under machine load and passed alone on rerun (not related to the settings). The five were rerun and pass; the full app suite was not rerun after those edits. core-design TranslationCoverage green. Full `assembleRelease` not run.

## Help diet report (CFG-33, nothing fails)
rows: 158

prose rows: 64 (40 percent). A row counts when its summary has more than eight words or 60 characters. Offenders (characters), from `app/build/help-diet-w56.txt`:

Pages > Density follows the home default (46); Pages > Rows · landscape (46); Pages > Clock tile on this page (38); Pages > Delete this page (51); Home screen > Calm home screen (Glance) (110); Home screen > Order (122); Home screen > Right side (85); Home screen > Include apps opened from elsewhere (88); Home screen > Clock colour (60); Home screen > Show music (69); Home screen > Hotspots (84); Home screen > Controls hide after (seconds) (58); Home screen > Surface indicator (63); Home screen > Style (73); Home screen > Pages (158); Home screen > Scroll direction (129); Home screen > Grid follows window size (121); Home screen > Default density (32); Home screen > Same spacing both ways (74); Home screen > Notification badges (115); Home screen > Page turning · portrait (51); Home screen > Always-on keyboard (76); Home screen > Keyboard opens with Radar and drawer · portrait (60); Home screen > Keyboard shortcuts (64); Home screen > Hide status bar (67); Look > Font (98); Look > Icon colour (63); Look > Calm style (122); Look > Also show regular battery and signal (104); Look > Show (89); Corners > Corner style (100); Corners > Icon type (84); Corners > Follow device rotation (64); Corners > Separate corners for landscape (76); Corners > Hot corners with a mouse (66); Gestures > Pinch changes grid density (50); Gestures > Swipe left (49); Gestures > Swipe right (49); Gestures > On this home (129); Gestures > Motion sensitivity (61); App drawer > Ring segments (109); App drawer > Show labels under glyphs (56); App drawer > Pages (64); Radar > Keep this layout with a keyboard (67); Radar > Radar background (107); Radar > Keep moving (74); Radar > Web engine (57); Radar > Web results (43); Radar > Web suggestions (112); Radar > Deep results (62); Side panel > Left page (89); Side panel > Comms theme (67); Side panel > Comms size (107); Side panel > Default view (101); Side panel > Show pinned at the top (222); Side panel > Widgets section (127); Side panel > Clear from the shade when dismissed in Comms (109); Side panel > Read history (53); Side panel > Groups and rules (51); Side panel > Unread strip (46); Side panel > Mark all as read (64); Side panel > Clear all notifications (58); Side panel > Delete history (73); Backup > Crash reports (53)

## Device-only checks
TalkBack reading of dimmed rows and the Finish setup page, the system pages the Finish setup buttons open (default home role, notification listener, usage access, Do Not Disturb access), the flash of a pane row after a search hit, Undo restoring a snapshot on a real install, the look of the tick and dimming on real screens and the light theme, font scale 2.

## Not done
Dock rows when the dock is forced; Look block conditional rows (Backdrop dimming, Also show regular battery and signal); Gestures density row; pane entries for Themes, Drawer shortcuts, Workspaces and Search providers; translated search synonyms; the help diet only reports.

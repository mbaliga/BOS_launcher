# Wave 58: settingsPolish (CFG-65 to CFG-69)

Nothing was run on a device. Verified by JVM unit tests, Robolectric UI tests and by reading screenshots of the six real tabs in dark (`app/build/screenshots/w58-tab-{home,look,apps,radar,sidepanel,system}-dark-N.png`, a tall mdpi screen, the long tabs in two to four parts). Decisions: `docs/decisions/settingsPolish.md`.

| ID | Status | Note |
| --- | --- | --- |
| CFG-65 | built (device untested) | The help diet is a failing guard: every grey line is a state or at most six words, allow-list empty (capped at 5 percent, tested). All 64 offenders of the wave 56 report and the ones found since (Looks, the Try it rows, the per-style hints, the gesture rows' "not on" lines, the reasons longer than six words) rewritten; 164 rows in the walked configurations, 0 offenders. |
| CFG-66 | built (device untested) | Backdrop dimming, Also show regular battery and signal, Pinch changes grid density stay dimmed with the reason; dock options dimmed while the dock is off, Show the dock dimmed with why where a surface forces the dock. |
| CFG-67 | built (device untested) | Search entries with stable ids for Themes (4), Drawer shortcuts (each segment + Reset), Profiles (5), Search providers (each provider app), Search folders (2), Search history (1); the panes draw the rows under the same ids and the hit's row lights. |
| CFG-68 | built | One-sentence section notes under six headers (Glance, Theme, Calm styles, Web, Comms, Sound and haptics). |
| CFG-69 | built | Headers renamed or merged so none repeats a row of its section; sentence case, unique, no orphans, a reset on every tab, the same control for the same kind of choice (tests); six tabs shot and read. |

## Where the help went (examples)
- Deleted where the cards say it: home Style (Pages home), Pages, Corner style, Clock style, page Layout, Left page, Comms theme, Right side, Radar background, Wheel groups, Settings backdrop, Calm style, Widgets position, Launcher vibration, Page turning, Keyboard opens with Radar.
- Kept as a few words: Web suggestions "Sends what you type to <engine>", Web engine "Gets only what you send", Delete this page "Its items stay in the drawer", Clear all "The history stays", Delete history "Notifications in the shade stay", Notification badges "Needs notification access", Include apps opened from elsewhere "Needs usage access", PSP / List / DSi Style "Hold the clock band for Settings" / "Long-press the margin for Settings" / "Long-press empty space for Settings".
- Moved to a section note: Glance (Glance is the calm first screen; your pages follow it), Theme (a look never replaces your own wallpaper picture), Calm styles (never cover the wallpaper), Web (suggestions also send typing to the Play Store), Comms (pinned items never count as unread or get dismissed), Sound and haptics (Android's own settings stay).
- Destructive actions keep their full confirm texts (unchanged).

## Screenshots read (dark)
Home (4 parts): the Glance note under its header, clock cards with no repeated caption, Home pages with Order "Mirrors Comms, Recents and columns", Page and Layout cards, Density follows the home default "Home default: 3 × 6 · 5 × 2", the per-page steppers dimmed "Follows the home default", Scroll direction dimmed "Needs the DSi home style", Grid density dimmed "Needs Grid follows window size", gesture rows "Nothing · Home scrolls this way", On this home "Up / down swipes are free". Look: Theme note, Looks "Mode, accent, icons, dock, font, calm", Font and icons, Pages wallpaper dimmed "Same as Glance", Calm styles note, Also show regular battery and signal dimmed "Calm style is Regular", Backdrop dimming dimmed "Backdrop is Pitch black". Apps: Profiles and groups ("Work, Calm, Desk and your own", "Apps that open together"), App drawer. Radar: source switches with short lines, the Web note naming the Play Store, Web suggestions "Sends what you type to DuckDuckGo". Side panel: Comms rows with short lines, Rules and gestures, Right edge. System: General, Sound and haptics with its note, Permissions, Backup and restore. Fixed after reading: the page Layout lines repeated the cards (removed), App groups' line was a list (now "Apps that open together").

## Changed old tests (behaviour replaced on purpose)
- `SettingsTabsW57ModelTest.CFG_40_theSectionsOfEachTabAreTheOwnersSections`: the new header names (CFG-69).
- `SettingsCoreW56AppTest`: Home has 7 sections (Dock and displays merged); the short reasons "Fixed on the PSP bar", "Same as Glance".
- `SettingsOneUiW40Test.CFG_21_theBigTabsAreCutIntoSectionsWithHeaders`: at least 7 Home sections.
- `SettingsVisualW54Test.CFG_23_...`: the Clock style row has no grey line (cards); Order reads "Mirrors Comms, Recents and columns".
- `LooksCalibratorsW56Test.CFG_26_w56_theLookPageStartsWithTheLooksRailAboveLightDark`: the Looks line is "Mode, accent, icons, dock, font, calm" (the wallpaper sentence is the Theme note).
- `RadarWebSettingsW43Test.PAL_06_...`: the source scan looks for "Sends what you type to %s" in the row and the Play Store in the Web note.
- `WorkspacePagesW41Test.DCK_04_page_31_...`: the dock is switched off last (with it off, the option cards are dimmed and take no touch).
- `CalmStyleRowsW55Test` (3): the Calm style row has no grey line (the cards; the wallpaper sentence is the Calm styles note); Also show regular battery and signal is always listed and dimmed with "Calm style is Off / Regular" instead of absent.
- `CommsOptionsW29Test.rowsHold` (font scale 2): scrolls Read history into view before measuring it (shorter lines put it in the list's prefetch range, composed but not placed, height 0); `Groups and rules` keeps a short summary ("The manager in Comms").
- `HiddenAppsSettingsW38Test.HOM_05_w38_...`: the header is "Hidden and default apps".
- `L10nW28homeUnify.kt`: the two strings the code no longer uses ("Adds a page after the last one" and the long Pages hint) were removed from the wave 28 table in all eight languages, because `HomeUnifyW28AuditTest` requires every string of that table to be used. Other replaced strings stay in their older tables (no test asks for their removal).
- `SPEC_STATUS.md` Counts line recomputed by the hygiene audit (it was already behind: 185 rows before this wave's five).

## Tests added
`SettingsPolishW58AppTest` (13), `SettingsPolishW58PanesTest` (3), `SettingsPolishW58ShotsTest` (6 screenshot tests) in app; `SettingsPolishW58UiTest` (6) in feature-grid; `DockOptionsW58Test` (4) in feature-dock. Strings: `L10nW58settingsPolish.kt` (109, eight languages).

## Results (`--offline --max-workers=2`, one Gradle build at a time)
Last runs, after the final edits: feature-grid 1720 tests, 0 failed; feature-dock 339/0; core-quality 220/0 (RepoHygieneAuditTest with the recomputed Counts line, TEST_COVERAGE.md regenerated by `tools/spec_coverage.py`: 185 of 190 IDs with tests, no M or S without); core-design 32/0 (TranslationCoverageTest); app 739/0. Earlier runs on the way: one app run was killed by the kernel for memory (exit 137, another worktree was building) and was rerun; one full app run had 7 failures, all old tests that encoded a replaced behaviour (listed above) and fixed; core-quality failed twice (the wave 28 strings audit and the Counts line) and was fixed. `assembleRelease` was not run.

## Device-only
TalkBack reading of the dimmed dock blocks and switches, the look of the section notes and dimmed rows at font scale 2 and in light, the flash of pane rows after a search hit on a real screen, Search providers found on a device with a provider app installed.

## Not done
- "Grouping & rules" and "Groups and rules" are near-identical row names in Side panel (a wording decision for the owner).
- The Settings Dock page does not know when Glance forces the dock; only the dock's sheet shows Show the dock dimmed with the reason.
- Replaced English strings were left in the older translation tables (allowed by the tests; removing them touches eight shared tables).

# Wave 57: previewAll (CFG-50 to CFG-54)

Owner answer to Q1 (`docs/handoff/clackpad_vs_liftoff_settings.md`): "Radar, the disc, Comms and corners, everything- whatever is being configured." Decision record: `docs/decisions/previewAll.md`. Nothing here is device-confirmed.

| ID | Status | Note |
| --- | --- | --- |
| CFG-50 | partial | Topics per page and row through one table (`PreviewTopics`, feature-grid `PreviewTopic.kt`), row declaration `previewTopic(...)`, unknown tabs judged by their rows; the window follows the open page and the row last touched or focused, cross-fades (instant under reduced motion), its bar title is the caption, TalkBack reads one line, hidden on system pages. Device-only: the feel of the switch while touching and scrolling rows; TalkBack on a device |
| CFG-51 | partial | Home and Look: the home capture. Glance: the real `GlanceSurface` with the current clock. Corners: four rings, the selected one pulsing. Gestures: animated hint, static arrow under reduced motion. Dock: the real rail with its settings. 60 ms coalescing, skip unchanged, not composed while minimised, no settings recomposition per preview frame. Not matched: see below |
| CFG-52 | partial | The real Radar screen and the real disc composed in an inert sandbox at the screen's size and drawn scaled; sample query from the real app list, made-up web suggestions, Sample badge, no network, no focus or keyboard taken |
| CFG-53 | partial | The real Comms column with anonymised sample notifications and a Sample badge; a real notification in the feed never shows (tested). The Widgets section is not drawn |
| CFG-54 | built | Parity test over the real settings in three configurations; every row that changes a preference maps to a topic or is allow-listed with its reason |

## How another stream sets a topic (the six-tab settings)
- Nothing is required: a tab title that is not in the table is judged by the topics of its rows.
- To be exact: `PreviewTopics.category("<English tab title>", PreviewTopic.Glance)` once (or an entry in `PreviewTopics.CATEGORIES`).
- A row: `SettingItem.Toggle(...).previewTopic(PreviewTopic.Radar)`, or an entry in `PreviewTopics.ROWS` by row id (`rowId`) or English title. A corner or gesture is picked up from the row title.
- `PreviewAllW57AppTest.CFG_54_everySettingThatChangesAVisualPreferenceMapsToAPreviewTopic` names any row left at None.

## Tests
- `PreviewAllW57UiTest` (feature-grid, Robolectric native graphics, 26): mapping (category defaults, rows with corner and gesture, declaration wins, unknown tab by majority, registration), the window per page (Radar, system page hidden), row touch cross-fade (both layers mid-fade), pulsing corner and its description, gesture hint, real Glance surface following the clock style, dock renderer and its description, coalescing (fewer pictures than changes, at most one per 60 ms, unchanged value does nothing), real Radar (sample query from the apps, real app matches, sample web rows, which go away with web suggestions off), Radar follows the layout, real disc with the ring shortcuts, nothing in the sandbox focused, screen reader sees one line, side panel renderer with Sample badge, no settings recomposition while the hint plays for 3 s and after a prefs change, nothing composed while minimised, seven screenshots.
- `PreviewAllW57AppTest` (app, 5): the parity test (every switch, choice, slider and stepper of the built settings in three home configurations counts as changing a preference; the setters are not called, because some rows write process-wide stores, Comms size and pinned, which leaked into later tests in a first version), the allow-list holds only None rows, every real category's topic, the real Comms column with sample rows and never a real notification (a real item is published to the feed), the real dock rail with the user's apps; two screenshots.
- Old tests: `PreviewWindowUiTest` (23) and `SettingsPreviewDockUiTest` (10) unchanged and green (the picture is still one labelled node without children; the caption moved to the bar so the pixel test still sees the home across the whole picture).
- `RepoHygieneAuditTest`: the Counts line of `docs/SPEC_STATUS.md` was already behind before this wave (170 rows, "of 167"); it now matches the 175 rows.
- Strings: `L10nW57previewAll.kt` (es fr de pt hi zh ja ar).

## Screenshots (read)
`feature-grid/build/screenshots/previewAll_{home,glance,corners,gestures,radar,disc,look}.png` and `_window.png` crops; `app/build/screenshots/previewAll_{sidepanel,dock}.png`. Read and judged: Radar shows the field with "ma", Mail and Maps, the Play Store and web suggestion rows "ma sample 1..3" and the Sample badge; the disc sits at the bottom-left with the ring (Notifications, Alarm, Flashlight), the A-Z / Category / Recent groups and the hub; Glance shows the big digital clock over the wallpaper; Corners rings all four, the top-right one in accent; Gestures shows two arrows pointing in (static, reduced motion); the side panel shows the Comms header, a pinned "Sample song" and the sample rows with unread dots; the dock shows the rail at the bottom with the home chip and initials tiles. Fixed after reading: corner rings were drawn over the bar (stage now clipped); the in-picture caption covered the disc hub and a home corner (moved to the bar title); the gesture arrows were faint over light icons (dark halo added).

## Not matched / known
- Glance away from the Glance page uses the current backdrop layer (the Pages wallpaper when it differs) under the Glance surface.
- The dock magnification is not drawn as a sample bulge (only named in the description); the dock rail clips its last tile with four apps at phone width, as the real rail does with the same margins.
- The hub's Widgets section is not in the side panel preview.
- The disc is drawn with its default anchor (bottom-left), not the corner the user last opened it from.
- Rows inside a pane do not change the topic; the pane's row decides.

## Device-only
Frame cost of a sandboxed Radar or Comms while Settings is open and a slider drags; the look at real sizes and with real wallpapers (live wallpapers draw once, in the backdrop); TalkBack on a device; touch and scroll feel.

## Module runs (`--offline --max-workers=2`, one at a time)
feature-grid 1722 tests, 0 failed; feature-launchpad 823/0; feature-dock 305/0; core-quality 220/0; core-design 32/0; app 702 tests: the last full run had 1 failure, `AppSmokeTest.NFR_11_aRecreateOfTheActivityInTheSameProcessIsNotTakenForACrashLoop` ("component is not displayed"), which passes when the class is run alone (27/0). An earlier full run failed `TourBusySmokeTest` and `SettingsHomeW52Test` (Comms size 115%): the first parity test called every row's setter, and some rows write process-wide stores; the test no longer calls setters and both pass, alone and in the last full run.

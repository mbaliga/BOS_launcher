# Wave 52: settingsFont (Settings home rows, centred capital titles, UI font)

Decision record: `docs/decisions/settingsFont.md`. Licences: `docs/licenses/`. Nothing here is device verified.

| ID | Status | Note |
| --- | --- | --- |
| CFG-21 | built (headless) | Owner feedback on the One UI Settings home. "Pages" replaces "Page 1" (summary `Page 1 of 3 · Grid`); "Side panel" replaces "Comms" (summary `Comms · 100%` / `Widgets · 2`; "comms" stays a search keyword); Workspaces is a top-level row with a count; every settings title is centred both ways, in capitals, in Space Grotesk; a Font setting of cards on the Look page (8 fonts, applied live through the theme typography, stored as `uiFont`, in the settings file, optional in theme packs). Device-only: the look of the capitals and the fonts at real density, the visual fit of long titles in 8 languages, the cards' scroll feel, the live switch on a running home. |

## Built
- core-design: `Type.kt` (`hyleTypography(family)`, `FontCatalog`, `HyleFont`, `titleCaps`, `rememberUiFontFamily`, `rememberTitleFontFamily`), `Theme.kt` (typography from the chosen family), `res/font/*.ttf`, `res/raw/keep_fonts.xml`, `L10nW52settingsFont.kt` (es fr de pt hi zh ja ar).
- core-data: `LauncherPrefs.uiFont` + `UiFonts`, store read and write, `ThemePack` optional `uiFont` (parse, encode, capture, apply, undo, preview chips).
- feature-grid: `OneUiTitle.kt` (one title composable), `SettingsOneUi.kt` (centred large and small titles, single-pane category honours `bare` and the live subtitle), `OneUiDialogs.kt` (dialog title), `SettingsOneUiModel.kt` (Pages, Side panel, Workspaces, the cards of the root), `SettingsIndex.kt` (category keywords), `FontChoice.kt`.
- app: `SettingsCategories.kt` (Pages, Side panel, Font row, Workspaces category), `DesktopSettingsPane.kt` (`workspacesCategory`, Workspaces gone from Desktop and dock), `SettingsOneUiSummaries.kt`, the font applied in `MainActivity`, `DesktopActivity`, `ThemeImportActivity`, `HomeScreen`.

## Tests added
- `core-design/FontCatalogW52Test` (7): default and ids, every named file exists in the one font folder and none is unused, size under 1 MB, missing file gives the system sans (never loads it), static weights, typography carries the family in every style, live state, capitals with locale rules and scripts without case.
- `core-data/UiFontW52Test` (7): default, settings file round trip, unknown id read as default, stored in the preference file, theme pack optional field, unknown font refuses a pack, reset/undo/capture.
- `feature-grid/SettingsTitleFontW52Test` (13): title centre within 1 dp of the page centre, vertical centre inside the band under the back arrow (equal gaps), category page title, capitals with plain-word semantics, collapsed small title, no-case script, titles in Space Grotesk while the theme typography follows the UI font, one card per font drawn in its own family, a tap selects and the selected card is the stored one, the id lists agree, dialog title, screenshot.
- `app/SettingsHomeW52Test` (6): Pages row and its summary, Side panel row and summary, "comms" and "launchpad" and "widgets" search, top-level Workspaces row with count above Look and gone from Desktop and dock, Look summary with the font and the Font rail of 8 cards, screenshot of the Settings home.

## Existing tests changed
- `feature-grid/OneUiModelTest`: the card order (new first two cards, Side panel), "Page 1" and "Page 7" recognition replaced by "Pages" (the number titles are gone), accent of Pages.
- `feature-grid/SettingsOneUiUiTest`: the sample category and its row tag are "Side panel".
- `app/CommsOptionsW29Test`: the category is looked up and searched as "Side panel".
- `app/SettingsOneUiW40Test`: the Look summary now ends with the font ("Dark · Rounded icons · Space Grotesk").
- `app/DockDeskReviewW24RulesTest`: the "workspaces" search runs over the Workspaces category plus Desktop and dock.
- `app/AppSmokeTest.HOM_22_groundControl...`: scrolls the lazy category page to the Order row before asserting (it sits on the window's bottom edge).
- `feature-grid/SettingsOneUiUiTest` (landscape two-pane): asserts Home screen and Look are on the first screen; App drawer moved one row down with the new card split.

## Results (headless, this branch)
core-design, core-data, core-quality, feature-grid (1557 tests), feature-dock and app (537 tests, after the three test edits above) all pass with `--offline --max-workers=2`. Screenshots read: `app/build/screenshots/w52-settings-home.png` (Pages, Home screen, Workspaces in the first card; centred SETTINGS) and `feature-grid/build/screenshots/w52-font-picker.png` (centred LOOK, the Font rail with cards "Liftoff 12:30 Aa", the chosen card outlined).

## Fonts
Obtained from raw.githubusercontent.com/google/fonts (the sandbox could fetch them), cut to Latin with fontTools (variable fonts keep the weight axis only). All eight files are in `core-design/src/main/res/font`: raw 719,320 bytes in total (about 355 KB deflated). APK size delta was not measured with an assemble run; expect +0.35 to +0.72 MB. Licences: `docs/licenses` (OFL texts and a table).

## Not matched, or device-only
- The wheel drawer's curved labels and the hot corner labels use `Paint`, not theme text, so they keep the system font (files owned by other streams).
- Glyphs outside Latin fall back to the system font per glyph (Cyrillic, Greek, Devanagari, CJK, Arabic are not in the cut files).
- The Side panel's widget count is not wired: the widgets feature lives in another stream; `settingsSummaries(sidePanelWidgets = ...)` takes it and `HomeScreen` passes 0 for now.
- Titles are Space Grotesk even when another UI font is chosen (the owner asked for titles in Space Grotesk).
- Device-only: how the capitals and fonts look at real density and font scale, long titles in 8 languages, the live font switch on a running home, the card rail scroll feel.

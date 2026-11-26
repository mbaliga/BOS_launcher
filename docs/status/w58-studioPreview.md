# Wave 58: studioPreview (CFG-70 to CFG-74)

Branch `w58-studioPreview`. Decision record: `docs/decisions/studioPreview.md`. Nothing here was run on a device or on the owner's site.

| ID | Status | Note |
| --- | --- | --- |
| CFG-70 | built | Studio's six tabs and their sections are the app's, in the app's order. Each schema field names its app row; the generator (`StudioSchemaW56Test`) reads tab and section from `SettingsTabLayout.kt`; `StudioTabsW58Test` (app) checks placement, that every named row is built, and the rows' own setters (every export key a row changes is shown in a section whose rows change it). Schema regenerated with `STUDIO_REGEN=1`. |
| CFG-71 | partial | Studio Profiles panel in APPS > Profiles: list with Active badge, New from Current setup / Empty / Work / Calm / Desk (generated from `ProfileStarters`), Rename, Duplicate, Delete, Switch (the phone's precedence), carried groups as check cards, suggested app groups as editable lists saved as App groups (with a link to a profile). Node tests; `StudioProfilesW58Test` (feature-dock) imports a Studio-made Backup with the app's codecs. Limits below. |
| CFG-72 | partial | Studio preview: Signal approximated in the theme colours (no placeholder), Landscape switch with side docks, magnification sample bulge, draggable clock (pointer and arrow keys) writing the clock place. Node tests, Playwright at 1280 and 390. |
| CFG-73 | partial | App Preview: `DockRail(demoPointer)` sample bulge (optional, default off), side panel Widgets section as sample cards, Glance topic draws the Glance's own wallpaper on a page. Tests and screenshots read. |
| CFG-74 | partial | `LiftoffStudio.URL` is the one constant ("set when published"); the row reads "Not published yet" while it is the placeholder. The site is not published. |

## Owner / brief items, where they are
1. Studio = app: `StudioSchemaW56Test` (`appRowOf`, `appLayout`, CFG_70 test), `StudioTabsW58Test` (3 tests), `schema.json` (`row`, `tabs[].sections`), `app.js` renders the sections in order (sections with nothing in a file say "Set on the phone"; Looks shows the quick looks).
2. Profiles UI: `core.js` (`withPhoneProfiles`, `createPhoneProfile`, `renamePhoneProfile`, `duplicatePhoneProfile`, `deletePhoneProfile`, `setPhoneCarries`, `switchPhoneProfile`, `syncActivePhoneProfile`, `backupApps`, `suggestGroups`, `addAppGroup`), `app.js` (`renderPhoneProfiles`), schema `profileStarts` and `appSuggest` (generated; core-data test classifies every table entry with the app's suggester). Export writes the list in the Backup (`workspaces`, `dock.groups`, `dock.globalSettings`, `dock.activeId`); the fixture `tests/fixtures/studio-profiles-backup.json` is read by the app's codecs.
3. Studio preview: `preview.js` (`BG_LIVE`, `signalDefs`, `dockLayer`, `magnifyScales`, `render(..., {landscape})`, `clockFromBox`), `app.js` (`wireClockDrag`, Landscape switch).
4. App preview gaps: `DockRail` / `MagnifyField.demo` / `DockPreviewStage`, `CommsPreview` (`PanelWidgetsPreview`), `PreviewStage` (`PreviewGlanceBackdrop`).
5. Studio row: `StudioPane.kt`.

## Tests run (all `--offline --max-workers=2`, one Gradle build at a time)
- Node: `node --test web/liftoff-studio/tests/studio.test.js`: 36 of 36 (10 new: sections, starters, rename/duplicate/delete, carries and switching, suggestions, Backup round trip, Signal, landscape, magnification, clock drag).
- New JVM / Robolectric: `StudioSchemaW56Test` (10, two new), `StudioTabsW58Test` (3), `StudioProfilesW58Test` (2), `DockDemoPointerW58Test` (4), `DockMagnifyUiW56Test` (19, unchanged, still green), `PreviewGlanceBackdropW58UiTest` (3), `CommsPreviewWidgetsW58Test` (3), `StudioPreviewW58AppTest` (4).
- Module runs: see "Module results" below.
- `python3 tools/spec_coverage.py --check`: 0 M/S without a test (the tool now also counts the Studio node tests, `test('CFG-72: ...')`).

## Screenshots read (`docs/status/w58-studioPreview/`)
Playwright (chromium, dark): `desktop-*` at 1280 wide, `phone-*` at 390 wide: home, APPS with a Studio-made profile list and suggestions, the New profile form, Look with the Spire Signal wallpaper and the Large magnification bulge, landscape Glance and Pages with the dock at the left, the clock after a drag (sliders followed: 0.5/0.14 to 0.27/0.44 on the phone width). No console error, no failed request. App: `app-preview-sidepanel-widgets.png`, `app-preview-dock-bulge.png`, `app-preview-glance-on-page.png` (Robolectric).
Fixed after reading: the phone profile list laid out in one row (the class `phone` clashed with the preview's `.phone` rule; renamed); the magnified dock ran off the left edge and covered the group chips (the row now shrinks to fit and the chips move up); the landscape drawing was cropped at 390 wide (it now fits the width); the caption overflowed the sticky preview on a phone (shortened, two lines at most).

## Limits (honest)
- Suggestions in Studio use the package tables, the work profile and the bank / pay words only: a Backup carries no app labels, categories or installed-app list, so the phone may suggest more. A file naming fewer than two apps of a kind shows "Suggestions run on the phone".
- A profile list started in Studio (no Backup imported) replaces the phone's list on import; the panel says so. A profile's home and dock other than the starters' one page are not edited in Studio. Import of the Studio file on a device was not tried (JVM codecs only).
- Studio's Signal wallpapers are still frames drawn after the app's painters, not measured against them; landscape covers the Glance and Pages; the side dock overlaps a top-left hotspot glyph in landscape (the app's real spacing was not reproduced).
- The app Preview's Glance wallpaper is a still frame (a live wallpaper does not run twice); the Widgets cards are placeholders; the dock bulge is a fixed pointer over the second icon.
- The Studio site is not published; `LiftoffStudio.URL` is the placeholder.

## Device-only
The Preview's look at real sizes, a Studio Backup imported on a phone, the clock drag on a touch screen browser.

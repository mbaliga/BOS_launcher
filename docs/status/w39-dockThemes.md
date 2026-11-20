# Wave 39: dockThemes (dock styles, theme packs, theme store hook)

Requirement rows used for test naming: DCK-04 (dock settings: the style is a dock setting), CFG-10 (theming: theme packs), CFG-01 (the dock style travels with export and backup). No Spec or SPEC_STATUS rows were edited. Decision note: `docs/decisions/dockThemes.md`.

| ID | Status | Note |
| --- | --- | --- |
| DCK-04 (dock style) | built, not device-verified | Standard (unchanged drawing, default), Transparent (no panel or border, per-icon halo, white glyphs on dark discs, ringed active dot), 3D (gradient slab, top highlight, bevel, inner shadow, contact shadow, raised icons with their own shadow and a masked mirrored reflection; light and dark variants). Persisted as `dock.style`. Chosen from the dock's long-press menu ("Dock style" dialog) and Ground Control, Home screen, "Dock style" (three radio rows with previews). Tests `DockStyleTokensW39Test`, `DockStyleUiW39Test`, `ThemesW39Test.DCK_04_style_40_...`. |
| DCK-04 (tilt) | not built | The optional gyroscope parallax of the 3D shelf was skipped (see the decision note). |
| CFG-10 (theme packs) | built, not device-verified | `.liftofftheme` JSON (also `.json`): name, author, version, formatVersion, mode, accent, icon shape, icon colour, dock style, Glance wallpaper solid or gradient. 64 KB, 80-character, colour and closed-set validation, unknown keys ignored, every error listed. Export (CreateDocument), import (OpenDocument behind the `ThemeFiles` seam), preview with before and after chips, Apply and Cancel, one in-memory undo, reset to default look, all under Ground Control, Look, Themes. Opening a `.liftofftheme` file lands on the same preview (`ThemeImportActivity`). Tests `ThemePackW39Test`, `ThemesW39Test`. |
| CFG-01 (classification) | built | The only new persisted state is the dock style inside the existing `dock` section (exported, backed up, additive, no schema bump); packs and undo are not stored. Tests `DockStyleStateW39Test`, `DockStyleTokensW39Test.DCK_04_style_05_...`. |
| Theme store | hook only, by owner decision | `ThemeStore.ADDRESS` is null; no URL, no browse row, no network code. Owner's idea recorded as future work, address unknown. |

## Verified

- core-data: all unit tests (including 18 theme pack tests and 3 state tests), core-design (translation coverage for 64 new strings in es, fr, de, pt, hi, zh, ja, ar), core-quality audits, all feature-dock tests (31 UI tests with 13 screenshots and 9 token tests), `ThemesW39Test` (24 tests with 5 screenshots).
- Screenshots read: `feature-dock/build/screenshots/w39-dock-{standard,transparent,3d}-{dark,light}-{portrait,landscape}.png`, `w39-dock-style-chooser-dark.png`; `app/build/screenshots/w39-theme-preview-{dark,light}.png`, `w39-theme-error-dark.png`, `w39-themes-rows-dark.png`, `w39-dock-style-pane-dark.png`.
- Full `testDebugUnitTest assembleRelease` result: see the end of this file.

## Observations from the screenshots

- Transparent over a light wallpaper shows a clearly visible dark halo ring behind every icon: legible, but heavier than a soft shadow. It is one constant (`HALO_ALPHA`) to soften if the owner finds it too strong on a device.
- Transparent over a dark wallpaper: the halo is almost invisible, icons and the white glyph read normally.
- 3D: the slab, highlight, bevel and contact shadow read clearly in light and dark and in landscape (shadow falls inward from the left edge); the reflection is a faint faded strip under each icon, easy to miss on the dark slab.
- The screenshots draw the dock over a Compose gradient, not the real wallpaper, and use plain coloured tiles for icons.

## Device-only checks

- The real document pickers (Import theme, Export current look) and the "open with" route from a file manager (`ThemeImportActivity` intent filters, content Uri types vary by file manager).
- Real wallpapers: halo legibility on busy photos; 3D contact shadow not clipped by the parent or the gesture area at the bottom edge; reflection and highlight on real icons with different shapes.
- Frame cost of the 3D slab on a low-end phone (it is a handful of draw calls; unmeasured).
- Long-press menu popup look (the popup is a separate window and does not appear in Robolectric screenshots).

## Notes for the merge

Shared files touched minimally: `SettingsCategories.kt` (two lines: the Dock style row in Home, the Themes row in Theme), `AndroidManifest.xml` (one activity), `L10n.kt` (two appended expressions), `DockHost.kt` (two lines), `DockRail.kt`, `DockMenu.kt`, `DockModel.kt`, `WorkspaceJson.kt` (small additions). `INTERNET` was already declared; nothing here adds a permission or a network class.

## Full run

`./gradlew testDebugUnitTest assembleRelease --offline --continue --max-workers=2`: 3735 tests, 3 failed under memory pressure from the other builds on the machine (the Kotlin daemon was also killed, so assembleRelease was rerun alone and passed, `app-release.apk` built). Rerun on their own: `PageEditorAppTest` (13) and `DrawerRevertW29Test` (9) pass; `PackageRemovedW26SmokeTest` is the known load-flaky one and passes on its own. Nothing in the three touches this wave's code beyond the dock rail being on screen.

Old tests that encoded replaced behaviour: none needed updating (the dock menu gains an entry only when the host passes `onSetStyle`, so the existing menu tests are unchanged).

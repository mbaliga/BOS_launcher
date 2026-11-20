# Wave 40: settingsOneUI (Ground Control as a One UI style list)

Requirement rows used for test naming: CFG-21 (new, the One UI style settings), CFG-05 (settings screen, Radar results land on their row), CFG-13 (settings navigation), CFG-18 (preview window default anchor). Spec.md rows CFG-05, CFG-13 and the new CFG-21, and the SPEC_STATUS rows CFG-05, CFG-13, CFG-18, CFG-21, were edited as the stream brief asked. Decision note: `docs/decisions/settingsOneUI.md`.

| ID | Status | Note |
| --- | --- | --- |
| CFG-21 root | built, not device-verified | One plain list of every category in rounded cards (26 dp, 12 dp gaps, inset hairlines), a coloured icon tile, 17 sp title, live grey summary, chevron; a large "Settings" title in the upper third that collapses into a small centred one; a rounded search field (all rows of all categories by title, hint and category) whose result opens the page on the lit row. No strip, puck or tabs. Tests `OneUiModelTest`, `SettingsOneUiUiTest.CFG_21_theRootListsEveryCategory...`, `...searchFinds...`, `SettingsOneUiW40Test`. |
| CFG-21 category pages | built, not device-verified | Slide in from the end, back arrow, collapsing title, sectioned cards, One UI switch, choice rows with the value at the end opening a radio list page, inline sliders and steppers, action rows (destructive ones ask first), panes and the per-setting preview rails inside the pages, 56 dp rows, 48 dp targets, headings and list semantics. |
| CFG-21 two-pane | built, not device-verified | Width 600 dp or more and landscape: list at the start (about 40 percent), the selected page at the end, a row's page opens inside the end pane. |
| CFG-21 RTL, font scale 2, reduced motion | built | Mirrored arrows, chevrons, slide direction and switch (settings are no longer forced left-to-right); rows wrap at font scale 2; reduced motion is a quick fade. Tests in `SettingsOneUiUiTest`. |
| CFG-13 | partial (changed) | Cross-media-bar keys removed with the strip; Tab and arrows through the normal focus traversal, Esc steps back. See SPEC_STATUS. |
| CFG-05 | built | Radar results still land on the exact row: the row is scrolled into view and lit (selected), instead of the cursor ring. |
| CFG-18 | partial | Default anchor now supplied by the host (`LocalPreviewTop`); the title band is at least 172 dp so the default window is clear of the first rows. |

## Entry points
The Home sheet's Ground Control button, the Launcher settings shortcut (Ctrl+comma), the Launcher settings corner action, the Settings home tile and the app menu's Settings now open the root list (`CATEGORY_ROOT`); the Wallpaper button opens Home screen, an empty drawer section opens App drawer, a Radar settings result opens its page on the row. Back from any of the page entries goes to the root first.

## Old tests changed or removed
- `GroundControlNavTest` removed (the reducer and the cursor ring are gone). `GKey` stays for Radar's key host.
- `InputTouchPathsUiTest.INP_07_back_dismisses_ground_control`: a deep link now needs two Backs (category, then root).
- `ReducedMotionUiTest.NFR_14_settingsSpaceOpensAndClosesWithACrossFade...`: probes a row instead of the category name (the name is now also the page title).
- `ScreenshotTest.detailVisualHomeStyle`: the breadcrumb page is gone; it shoots the picker alone.
- App tests that went to Ground Control and expected the Home screen page now go through the root row: `TourBase.quickGoTo` and `openGroundControlHome`, `PageEditorAppTest.openEditor` (a long press on an empty continuous home opens Settings, which now covers the home and takes the next touch, so the test no longer long-presses through it), `HomeUnifyReview2FlowTest` (back twice), `AppSmokeTest.HOM_22_groundControlHome...` (clicks the root row by text), `TourFreshSmokeTest` (the category walking comment).
- Core-quality audits that named `SettingsSpace.kt` for back handling, motion and unlabelled sites now name `SettingsOneUi.kt` (see the commit).

## Test runs
Pure JVM and Robolectric: `:feature-grid:testDebugUnitTest` (whole module, green), `:app:testDebugUnitTest` (whole module: green except the known load-flaky `PackageRemovedW26SmokeTest`, `DockWiringTest`, `DockDialogsW24Test`), `:core-quality` and `:core-design` (TranslationCoverageTest, the audits; green). New: `OneUiModelTest` 18, `SettingsOneUiUiTest` 38, `SettingsOneUiW40Test` 16.

## Verified (Robolectric, JVM) and not
Verified by tests: grouping and summaries, cards and sections, collapse maths, back stack, deep links, search across categories, switch, choice, slider, stepper, confirm, two-pane, RTL, font scale 2, headings and lists, 48 dp targets, reduced motion, Esc. Screenshots read: dark and light root, a category page, search results, the radio page, two-pane landscape, and the real categories (root, Home screen, Look, two-pane).

Device-only: scroll and collapse feel (no snapping), the predictive Back animation, slide timing, a real hardware keyboard and D-pad focus order, the wallpaper backdrop under the 94 percent page, TalkBack reading order, One UI's fonts and haptics.

## Not matched to One UI
No new categories (Wallpaper and Dock are rows inside Home screen; One UI has them as entries); choices open a page, not a popup; page rows have no icons (One UI pages have none either, the root has); the destructive-confirm sheet keeps the older look; the large title band is a fixed third of the window, not One UI's snapping header; the old per-setting preview rails and some panes keep their older tactile widgets (grey bevelled steppers) inside the new cards.

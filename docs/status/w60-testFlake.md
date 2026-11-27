# Wave 60: testFlake (CFG-67 flake investigation, CFG-77 page editor link)

Nothing was run on a device. Decisions: `docs/decisions/testFlake.md` (the full evidence trail for item 1).

| ID | Status | Note |
| --- | --- | --- |
| CFG-67 | investigated, not fully resolved | The app suite's rare full-run-only "Compose did not get idle" flake (`SettingsPolishW58PanesTest`) was reproduced repeatedly and investigated at length (varying the predecessor via a temporary instrumented run, heap size, `forkEvery`, worker count, and a control run on an effectively unmodified checkout). One real gap was found and fixed (a test composing the Hotspots editor's pulse without forcing reduced motion, the one place that didn't match the rest of the codebase's established pattern) and a regression test added for that shape of bug. None of these, individually or together, eliminated the flake in a subsequent full run; it also reproduces on the unmodified-equivalent checkout. Reported honestly as an unresolved, environment/accumulation-sensitive flake rather than claimed fixed. See `docs/decisions/testFlake.md` for the full evidence. |
| CFG-77 | partial (device untested), link added | The page editor's Settings tab (Glance focused) now has an "Edit hotspots" row under a small "Hotspots" heading, opening the same `HotspotsEditorPage` as Settings > Home > Corners and the Home sheet tile (same stored `glanceHotspots` preference, same corner editor). No old per-corner toggles existed in this tab to remove (wave 59 had already taken them out); nothing else changed there. |

## CFG-67: the flake, in detail

See `docs/decisions/testFlake.md`. In short: reproduced on every one of seven full `:app:testDebugUnitTest` runs in this investigation, always and only in the full run (never alone, never with a handful of classes), with the exact `androidx.test.espresso.AppNotIdleException: Compose did not get idle after ~2-3 million attempts in 60 SECONDS` from the brief. The test's own predecessor (found with a temporary, removed, `TestListener`) was different across runs (a pure data test with no Compose rule, once; an unrelated Crash Recovery test, once), and the specific failing test also varied (`SettingsPolishW58PanesTest` in five of seven runs; `DefectsW58AppTest` and `ui.DockWiringTest` in the other two) — ruling out one single leaked animation from one single predecessor as the whole story. A real, separate gap was found and fixed anyway (see below) but a full run with only that fix still failed the same way. Neither raising the test heap (2g → 3g, the exact lever a wave-22 comment in `app/build.gradle.kts` already used once for this same class of bug) nor `forkEvery` (250: no change; 50: markedly worse, 25 failures and a 29-minute run instead of 3-6 failures and 5-9 minutes) nor `--max-workers=1` (still failed) stopped it. A control run with this wave's `app/build.gradle.kts` and test-infra changes reverted (keeping the unrelated CFG-77 production code, whose path the failing test never exercises) still failed the same way, which is the "fails on an unmodified checkout too" evidence asked for.

The one concrete fix kept: `GlanceTopW59AppTest.CFG_77_inSettingsATapTogglesAndPersistsAndEditOpensTheCornerEditor` is the only app or feature-grid test that composes the Hotspots editor (whose selected corner pulses with a real `rememberInfiniteTransition`) without forcing reduced motion or driving the clock manually, unlike every other such test (`HotspotsEditorW59UiTest`, `GlanceTopW59SmokeTest`'s CFG_75/76 tests). Fixed to match: `ReducedMotionTesting.force(MotionPrefs(0f, 0f, 0f))` before composing, reset to `null` in `@After`. A new regression test, `HotspotsEditorW59UiTest.CFG_77_wave60_disposingTheEditorCancelsItsPulseSoTheHostStaysIdle`, composes the pulse with real motion, removes the editor from composition, and asserts `waitForIdle()` returns promptly (it passes, which is further evidence the investigated flake isn't exactly this shape, but it's a correctness guard worth having).

**Recommended but not applied** (a CI/process change, out of this wave's code scope): retry the `:app:testDebugUnitTest` Gradle task once on failure, or split it into two or three `--tests` batches run as separate invocations, rather than tuning `forkEvery` within one invocation (tried here; one setting made things worse).

## CFG-77: the page editor link, in detail

`PageEditorTabs.kt`'s `SettingsContent` (the Settings tab, Glance focused) gained:
- `PageEditorSettingsActions.onEditHotspots: (() -> Unit)?` (null hides the row; nothing else changes).
- A small "Hotspots" heading and an "Edit hotspots" row (`ActionTile`, now with an optional right-aligned summary and test tag `pe.settings.editHotspots`), its summary the same `HotspotsEditorModel.summary(prefs.glanceHotspots)` ("N of 4 on") the Settings and Home-sheet rows already show.
- Threaded through `PageEditorHost`'s new `onEditHotspots` parameter, `EditCardWiring.kt`'s `EditCardOverlay`'s new parameter, to `HomeScreen.kt`'s existing `hotspotsPage` boolean (the same one the Home sheet's Hotspots tile already sets), so the row opens the identical `HotspotsEditorPage` / `HotspotsEditorFor` instance, reading and writing the same stored `glanceHotspots` preference and the same corner editor (`editCorner`/`editCornerLandscape`).

New strings: `L10nW60testFlake.kt` ("Edit hotspots", eight languages), wired into `L10n.kt`'s two combined expressions.

## Tests added or changed
- `app/src/test/kotlin/com/bos/sphere/GlanceTopW59AppTest.kt`: `CFG_77_inSettingsATapTogglesAndPersistsAndEditOpensTheCornerEditor` now forces reduced motion (see above); `@After` resets it.
- `app/src/test/kotlin/com/bos/sphere/PageEditorAppTest.kt`: new `HOM_12_pe52_editHotspotsOpensTheEditorAndAnEditThereReflectsOnTheRow` (real app: opens the row, the Hotspots page opens, a corner toggled there is reflected in the page editor's own row summary once dismissed).
- `feature-grid/src/test/kotlin/com/bos/sphere/feature/grid/PageEditorUiTest.kt`: new `HOM_12_pe51_editHotspotsRowOpensTheEditor` (row shown, tap calls the callback, screenshot `page_editor_glance_focus_edit_hotspots.png`) and `HOM_12_pe51b_editHotspotsRowIsGoneWithNoCallerWired` (no row, no stray "Hotspots" heading, with no caller — keeps `HOM_12_pe24b`'s existing assertion that the old per-corner switches are gone true for the right reason); `host()`'s test helper gained an `onEditHotspots` parameter.
- `feature-grid/src/test/kotlin/com/bos/sphere/feature/grid/HotspotsEditorW59UiTest.kt`: new `CFG_77_wave60_disposingTheEditorCancelsItsPulseSoTheHostStaysIdle` (the flake regression test, see above).
- No existing test asserted the old per-corner toggles in this tab (wave 59 had already removed them and updated `PageEditorUiTest.HOM_12_pe24b` and `PageEditorAppTest.HOM_12_pe50` to check for their absence); those two tests are unchanged and still pass.

## Screenshots read
`feature-grid/build/screenshots/page_editor_glance_focus_edit_hotspots.png`: the Glance Settings tab with "Show music" then a "Hotspots" heading and the "Edit hotspots" row (rounded tile, same style as the Folders tab's action tiles, "4 of 4 on" right-aligned). Nothing needed fixing.

## Results (`--offline --max-workers=2`, one Gradle build at a time; feature-grid and the targeted app tests green on every run in this wave)
- `:feature-grid:testDebugUnitTest --tests HotspotsEditorW59UiTest --tests PageEditorUiTest`: 54/0.
- `:app:testDebugUnitTest --tests GlanceTopW59SettingsTest --tests GlanceTopW59SmokeTest --tests PageEditorAppTest --tests SettingsPolishW58PanesTest`: 26/0.
- Full `:app:testDebugUnitTest` (761 tests): failed with the CFG-67 flake in every one of seven runs during the investigation (see above and `docs/decisions/testFlake.md`); the final full run's exact result is in this wave's commit message / the agent's closing report (run fresh at the end, not re-summarized here to avoid going stale).

## Not done
- CFG-67's flake is not fixed with certainty (see above); a CI-side mitigation (retry or batching) is recommended but not applied, as it is outside this wave's code scope.
- CFG-77 remains otherwise as wave 59 left it (device-only items: the miniature's look with a real wallpaper capture, TalkBack, hardware keyboard, rotation).

# Wave 60: polish60 (CFG-80)

Nothing was run on a device. Verified by JVM unit tests (WCAG math) and Robolectric pixel tests (`PixelCopy`, the same harness
`GlanceTopFadeW59PixelTest` uses), and by reading the screenshots they produce. Decision: `docs/decisions/polish60.md`.

| ID | Status | Note |
| --- | --- | --- |
| CFG-80 | built (device untested) | **Naming:** the Side panel's "Groups and rules" action (opens Comms' own live group/rule manager overlay) renamed "Manage groups and rules", so it reads as the different, more specific thing it is next to the "Grouping & rules" pane beside it (default grouping mode, rules list, per-app delivery — unchanged). The old title is kept as a hidden search synonym of the new one. **Contrast:** the Glance music line and the big wallpaper clock now share one halo (`GlanceInk.shadow`, feature-grid) — a tight, near-opaque, centred shadow in the opposite tone of the text — behind their ink, so both stay legible over a wallpaper the app cannot sample a colour from (a photo or the system's own live wallpaper; the three kinds the app paints itself were already covered by the NFR-15 scrim). The clock's own, weaker shadow (wave 59) is replaced by the shared one; the wave 59 marquee edge fade is untouched. |

## The naming collision, resolved
"Grouping & rules" kept its name (already distinct: a grouping mode and a rules list, both static settings). "Groups and rules"
(the action) is renamed "Manage groups and rules": it opens a live manager, not a setting, and "manage" says so. See
`docs/decisions/polish60.md` for the full reasoning, including why the manager overlay's own header (inside Comms) was left as
"Groups and rules" (a different context — no collision once you are already in it).

Changed: `SettingsCategories.kt` (the row), `SettingsTabLayout.kt` (the Side panel section's row list), `SettingsRowMeta.kt`
(a `KEYWORDS` entry: the old title "Groups and rules" is a hidden synonym of the new one; a synonym was also added for "Grouping &
rules" itself), `L10nW60polish60.kt` (the new string, 8 languages), `L10n.kt` (wired into `englishKeys`/`tableFor`).

## The contrast fix
`WallpaperSurfaces.kt` already says why a picture or the system's own live wallpaper gets no legibility scrim: "a picture or the
system wallpaper has no colour the app knows" — a photo or a live wallpaper can be any luminance, anywhere on screen, so no sampled
scrim is possible the way it is for the solid/gradient/signal kinds (`HomeLegibility`). This is exactly the gap the wave 59
screenshot found (the dark theme's light ink read weakly over plain white). The fix does not try to sample the unsampleable
wallpaper: it draws a halo behind the text instead, independent of what is behind it — the clock already had a step in this
direction (a soft offset `Shadow`); the music line had none at all (which is why only it, not the clock, showed up weak in the
wave 59 screenshot). Both now use one shared, strengthened, centred halo (`GlanceInk.shadow(dark)`): opposite tone of the ink,
`alpha = 0.85f`, `blurRadius = 10f`, `Offset.Zero` (centred, so a moving marquee line reads the same at both edges, unlike the old
clock shadow's `Offset(0f, 2f)`).

## Screenshots read
- `feature-grid/build/screenshots/w60_line_white_dark.png`, `w60_line_black_light.png`: "Daft Punk · Discovery" reads clearly in
  both — a soft dark ring around the light glyphs on white, a soft light ring around the dark glyphs on black.
- `feature-grid/build/screenshots/w60_clock_white_dark.png`, `w60_clock_black_light.png`: the big clock digits ("11:38") read
  clearly over both extremes, the halo visible as a thin outline following each stroke.

## Tests added
- `GlanceLegibilityW60Test` (feature-grid, plain JUnit, no Robolectric — mirrors `GlanceTopRulesW59Test`'s convention of pure-math
  tests on the glance package's own objects): WCAG contrast math (the same formula as `core.quality.Contrast`, repeated locally
  since feature-grid does not depend on core-quality, as `GlanceTopFadeW59PixelTest` already repeats its own luminance helper)
  proving the halo clears 3:1 for the clock's ink and the music line's ink, over literal white and literal black, in both themes;
  a second test that the halo is the opposite tone of the ink and centred (not a copy of the ink, not offset).
- `GlanceLegibilityW60PixelTest` (feature-grid, Robolectric, `PixelCopy`, same harness as `GlanceTopFadeW59PixelTest`): renders the
  music line and the clock over a full white (dark theme) and a full black (light theme) background — the hard case for each
  theme — screenshots them, and asserts the halo actually changed pixels under the text versus a flat reference.

## Changed old tests (behaviour replaced on purpose)
- `CommsOptionsW29Test` (app): every assertion of the row title "Groups and rules" → "Manage groups and rules"
  (`STR_01_w29_theCommsCategoryListsEveryMovedOptionOnce`'s row-count list, `STR_01_w29_readHistoryAndGroupsAndRulesOpenCommsOnTheirScreens`'s
  `row(...)` lookup, `STR_01_w29_theActionRowsHaveASummaryLikeEveryOtherRow`'s row list); the search-synonym test
  (`STR_01_w29_settingsSearchFindsEveryMovedRow`) keeps the query "groups and rules" but now expects it to resolve to the renamed
  row, which is the hidden-alias behaviour in action, not just a title rename.
- `ClockBlock.kt`'s local `shadow` value is no longer its own `Shadow(...)`: it is `GlanceInk.shadow(dark)`, which is stronger
  (opaque 0.85 vs. the old 0.45/0.6) and centred (no offset). No test named the old shadow's exact values, so nothing else needed
  a change; the clock's existing screenshot/UI tests (`GlanceClockW41Test` and others) do not assert pixel colour.

## Results (`--offline --max-workers=2`, one Gradle build at a time)
`core-design:testDebugUnitTest` (incl. `TranslationCoverageTest`, `SurfaceContrastTest`): green. `core-quality:testDebugUnitTest`
(incl. `RepoHygieneAuditTest`, Counts line recomputed to `built 131, partial 68, not started 4, unmet 2, removed 1 (of 206)`,
`docs/TEST_COVERAGE.md` regenerated by `tools/spec_coverage.py`: 201 of 206 IDs with tests, no M or S without): green.
`feature-grid:testDebugUnitTest`, full module run: green (every test, `GlanceLegibilityW60Test` and
`GlanceLegibilityW60PixelTest` included). `app:testDebugUnitTest`, full module run: 760 tests, 3 failed, all three in
`SettingsPolishW58PanesTest` (`CFG_67_theRadarPanesLightTheirRows`, `CFG_67_aThemesHitLightsItsRow`,
`CFG_67_aDrawerShortcutsHitLightsThatSegmentOrTheReset`), every one an `AppNotIdleException` ("Compose did not get idle... in 60
SECONDS") — the pre-existing Robolectric timing flake the brief named another agent as fixing in parallel, not caused by this
wave's changes (that file is untouched here, and the three tests pass cleanly rerun in isolation:
`./gradlew :app:testDebugUnitTest --tests '*SettingsPolishW58PanesTest*' --rerun-tasks`, green). Targeted reruns of every test
this wave's changes could plausibly affect were also green on their own, before and after the full runs: `CommsOptionsW29Test`,
`SettingsTabsW57ModelTest`, `SettingsVisualW54Test`, `SettingsOneUiW40Test`, `PreviewAllW57AppTest`, `SettingsHomeW52Test`,
`SettingsTestNav`.

## Not done
- No device run: the look over a real photo or the system's own live wallpaper (the two kinds CFG-80's contrast half targets) is
  not seen on a phone; nothing here is device-confirmed.
- The halo is one fixed strength for every wallpaper, not tuned per photo (the live sampling that would need is exactly what this
  decision avoids for the Picture/System kinds — see the decision note).
- `LaunchpadGroupsW24.kt`'s own manager-overlay header keeps the plain "Groups and rules" title (a different context from the
  Settings row; see the decision note for why).

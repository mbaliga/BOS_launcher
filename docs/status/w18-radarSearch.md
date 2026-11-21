# w18-radarSearch: status

Stream: Radar search cleanup, input at the top only, Orbit rings by use. Owner message on build 49 (two screenshots). Decisions: `docs/decisions/radarSearchCleanup.md` (what was removed, how to restore) and `docs/decisions/radarSearch.md` (what changed and why, what the old spec said). Nothing here was confirmed on a device; device rows are in `docs/device-checklist.md`, "Radar search cleanup, query line at the top, Orbit tiers (wave 18, radarSearch)".

Why `w bows of denmark` showed a bang list (verified from the code, not guessed): `w` was the Web prefix of PAL-13, so Radar scoped the query to the Web section and searched `bows of denmark`; that section carried the DuckDuckGo bang chips (`WebRow.BangHints`) under the header "Web Suggestions". The "Filenames / Name contains" group is the separate refinement list and stays.

Fact check against the brief: INP-12 is "raise the keyboard on open, per orientation", not a bottom/top option. No such setting existed (the bottom position was hard-coded), so no pref was removed for it; the layout code that put the line at the bottom is gone.

| ID | status | note |
| --- | --- | --- |
| PAL-13 | removed (owner decision) | Prefix parsing, empty-state chips, Ground Control "Search prefixes", the `!bang` chips and syntax, prefs, strings (8 languages) and tests removed; Spec row struck through with the old text. Tests: `PAL_19_aLeadingLetterAndSpaceIsPlainText` (PaletteTest), `PAL_06_aLeadingLetterAndSpaceIsPlainTextForTheWebRows` (SearchModelTest), `PAL_13_removed_theEmptyStateShowsNoPrefixChips`, `PAL_06_aLeadingLetterAndSpaceIsPlainTextAndTheWebRowsStay` (RadarSearchW18UiTest). |
| PAL-19 | done | The wheel is the only scope control; the wheel no longer writes letters into the query; `ScopeWheel` kept (`ScopeWheel.kt`); Left/Right still moves it (RadarKeyboardTest unchanged). Spec row updated. |
| PAL-06 | done | Web hand-off and store lookup rows stay and are the last results; bang rows removed; URL queries still run first on Enter (`PAL_06_aUrlQueryStillRunsOnEnterThroughTheWebRow`, renamed from the bang-or-URL test, assertion unchanged). |
| PAL-07 | done | Orbit order is ring order, so Up/Down walks innermost ring first (`PAL_07_arrowKeysWalkTheRingsInOrderFromTheInnermost`). Flat's resting list is now walkable (`flatTiles` no longer empty at rest). |
| PAL-12 | done | `PAL_12_anItemKeepsItsRingWhileItsRankIsUnchanged`, `PAL_12_aRingChangesOnlyWhenTheRankCrossesARingBoundary` (RadarRingsTest); `HubOrdering`/`StableOrder` unchanged. |
| PAL-09 | done | Decayed use (`RadarHabits`, half-life 14 days) from the launch log and `PaletteHost.learn`; tests in RadarRingsTest (`PAL_09_*`). `HomeScreen.kt` passes `launchHistory = history` (one added line). |
| PAL-18 | done, device pending | Orbit rings are tiers (`RadarRings.kt`, capacities 5, 9, 13, 17, geometry-limited), faint ring guides, TalkBack headings and per-tile tier, hint "Rings fill up as you use Radar" in all three layouts, Flat at rest lists "Most used" (it showed nothing before), Filmstrip at rest in the same order. Tests: RadarRingsTest, `PAL_18_*` in RadarSearchW18UiTest (portrait and landscape). Screenshots (Robolectric, `feature-grid/build/screenshots/`): `w18-radar-orbit-portrait-dark.png`, `w18-radar-orbit-portrait-light.png`, `w18-radar-orbit-landscape-dark.png`, `w18-radar-orbit-landscape-light.png`, `w18-radar-orbit-portrait-moving.png`, `w18-radar-orbit-empty-portrait.png`, `w18-radar-flat-resting-portrait.png`, `w18-radar-flat-typed-portrait.png`, `w18-radar-filmstrip-portrait.png`. Read: orbit portrait dark, orbit landscape dark, flat typed, flat resting (apps have no icon art in tests, so only captions show). The typed-results view stays the Flat list. |
| PAL-20 | done | Preview behaviours unchanged and still covered (`W6RadarPreviewUiTest` passes in all three layouts with the new top line). |
| INP-12 | done, device pending | Query line at the top in every layout and orientation (`INP_12_*QueryLineIsAtTheTop*`, six layouts-by-orientation tests plus typing in Orbit and Filmstrip), focus in the field on open asserted; the keyboard-on-open prefs are unchanged. Whether the soft keyboard rises and the results end above it is an IME behaviour, device row. |

## Existing tests changed because they encoded the old behaviour

- `ScopePrefixesTest` and `RadarPaletteScreenshotTest`: deleted with the feature they tested (prefix parsing, prefix chips).
- `RadarPaletteTest.PAL_13_prefixLettersAreEditableAndOneSettingWithTheWheel`: deleted (prefix editor). `PAL_06_aBangOrUrlQueryStillRunsOnEnterThroughTheWebRow` renamed to `..aUrlQuery..`, assertion unchanged.
- `PaletteTest.PAL_13_*` (three tests): replaced by `PAL_19_aLeadingLetterAndSpaceIsPlainText`; `PAL_13_scopeLimitsProviders` renamed `PAL_19_scopeFromTheWheelLimitsProviders` and now passes the scope as an argument instead of typing `s ` or `a `, same assertions.
- `SearchModelTest.appsContactsWebOrder` (no bang chip row any more), `noHintsForOtherEngines` and `bangPutsWebFirst` (removed with bangs), `prefixIsKeptAndCapped` (bang line dropped); `WebSearchTest.bangs` removed.
- `W6RadarPreviewUiTest` (PAL-20, Orbit and Filmstrip): the apps are now opened in the test's launch history, because the resting Orbit and Filmstrip show what is used, not the recents list; assertions unchanged.
- `ScreenshotTest.searchShot` now passes a synthetic launch history so the Orbit and Filmstrip shots show the tiers instead of the new empty-history hint.

## Notes

- `docs/TEST_COVERAGE.md` and `docs/SPEC_STATUS.md` were not edited (generated or lead-owned); the PAL-13 and PAL-19 rows of TEST_COVERAGE name tests that no longer exist.
- Shared files touched with small additive edits: `HomeScreen.kt` (one line), `L10n.kt` (two appended terms), new table `L10nW18radarSearch.kt`; obsolete prefix and bang strings deleted from `L10nWave2Fill.kt` and `L10nLaunchpad.kt`.

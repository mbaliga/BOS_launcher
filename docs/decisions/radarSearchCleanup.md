# Radar search cleanup (wave 18): the typed prefix and the bang list are removed

Owner message on build 49 (two screenshots, Radar with the query `w bows of denmark`):

> The search suggests typing an alphabet, then a space and the query, that doesnt work for web, or anything else for that
> matter and it looks ugly so remove it.

## What the old behaviour was, and why `w bows of denmark` showed a bang list

- PAL-13 gave Radar a "letter, space, query" syntax. `a` apps, `c` contacts, `f` files, `s` settings and actions, `w` the web
  (and `>` for actions in the engine's own table). Radar parsed the first character of the query (`ScopePrefixes.parse`,
  feature-grid) and, when it was a prefix letter followed by a space or a colon, limited the results to that scope and
  searched for the rest only.
- `w bows of denmark` therefore meant "scope: the web, text: `bows of denmark`". Radar showed only the Web section:
  the header "Web Suggestions", the four DuckDuckGo bang chips (`!g Google`, `!gi Google Images`, `!yt YouTube`,
  `!w Wikipedia`, from `WebSearch.popularBangs`, added by `sections()` when the engine was DuckDuckGo), then
  "Search Play Store for ..." and "Search DuckDuckGo for ...". The bang chips rewrote the query text (`!g bows of denmark`)
  and relied on DuckDuckGo interpreting the `!g` token; nothing else in the app used them. The "Filenames: Name contains ..."
  group below was the separate refinement-suggestion list (it stays).
- The empty state listed the prefixes as chips (`PrefixHints`, also drawn in Flat's resting state) and the wheel wrote the
  same letters into the query when the user picked a scope (`ScopePrefixes.applyWheel`), so the typed text and the wheel
  were one setting (PAL-19).

## What was removed

| Where | Removed |
|---|---|
| feature-grid | `ScopePrefixes` (parse, mapping, `applyWheel`, `effectiveScope`; `ScopeWheel` stays and moved to `ScopeWheel.kt`), `PrefixHints` (empty-state chips), `PrefixEditing` and `PrefixEditorPane` (Ground Control "Search prefixes"), `Tile.Bang` and its row and face, the Bang handling in `SearchData`, `SearchOverlay` and `submitSearch` |
| feature-search | `WebRow.BangSearch`, `WebRow.BangHints` and everything in `sections()` that produced them; the bang checks in `suggest()` |
| core-data | `Bang`, `WebSearch.bang`, `hasBang`, `popularBangs`, `bangLabel`; the Settings hint that taught `!g`, `!gi`, `!yt` |
| search-engine | `PrefixTable` and its parsing in `PaletteEngine.plan`; `plan(raw, scope)` and `search(raw, scope)` take the scope from the caller (the wheel) instead |
| prefs | `PalettePrefs.scopeMapping`, `setScopeMapping`, `loadPrefixes`, `savePrefixes` (the stored keys `scopeMapping` and `prefixes` in the private `palette` prefs file are no longer read; they are harmless and leave with a reset or reinstall) |
| strings | "Search prefixes", the three prefix explanations, "Back to the default letters", "Try", the bangs hint, in all eight languages |
| tests | `ScopePrefixesTest`, `RadarPaletteScreenshotTest` (PAL-13 chips), the PAL-13 tests of `PaletteTest` and `RadarPaletteTest`, the bang test of `WebSearchTest`, the bang assertions of `SearchModelTest` |

Kept: the category wheel (the scope control, PAL-19), the scope menu beside the query line, the web hand-off rows ("Search
Play Store for ...", "Search <engine> for ...", "Open <url>"), URL detection, and the refinement suggestions. A typed `!g cats`
is now just text; the DuckDuckGo row still sends it to DuckDuckGo, which acts on the `!` itself. That is DuckDuckGo's feature,
not Radar's, and Radar no longer advertises or parses it.

New tests: `PAL_19_aLeadingLetterAndSpaceIsPlainText` (PaletteTest), `PAL_06_aLeadingLetterAndSpaceIsPlainTextForTheWebRows`
(SearchModelTest), `PAL_06_aLeadingLetterAndSpaceIsPlainTextAndTheWebRowsStay` and `PAL_13_removed_theEmptyStateShowsNoPrefixChips`
(RadarSearchW18UiTest).

## How to restore

Everything above is in one commit of the `w18-radarSearch` branch together with the other wave 18 Radar changes. To bring the
prefix and bang features back alone, `git revert` that commit and re-apply the input-at-top and rings parts (decisions
`radarSearch.md`); or check out the removed files from the parent of the commit (`git checkout <commit>^ -- <path>`).

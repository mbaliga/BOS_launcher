# Status: wave 29, Timeline usage legend and filter row (stream w29-timelineClean)

Nothing here is device-confirmed. Robolectric proves structure, bounds and semantics only.

| ID | Status | Note |
| --- | --- | --- |
| USG-30 | built | No two unmerged text nodes of the usage block intersect at font scale 1.0 and 2.0 and Comms 75/100/125, portrait and landscape (`UsageLegendW29Cases`); the legend starts below the stack, the block ends above the list. |
| USG-31 | built | Legend lines at least 24 dp, 8 dp apart, five lines, block height 230..300 dp, touch height 48 dp. |
| USG-32 | built | Percentage in one place (stack where it fits, else legend line). |
| USG-33 | built | Tap on a legend line highlights its part, second tap clears (selected semantics). |
| TLF-01 / TLF-02 | built | Filter row: one row, one selected per group, 48 dp targets, ALL with the long spoken description, scrolls at font 2.0 and Comms 125. |
| USG-19 | updated | Screenshots `feature-launchpad/build/screenshots/w29-*.png` (dark, light, portrait, landscape, font 2.0 comms 125), read. |

Old tests updated: `UsageBlockCases.USG_10_...` (height range 120..185 dp became 230..300 dp for the five-line legend). No test encoded the old pill chips by label (the app tests use only `timeline.list` and row texts); none deleted.
Device-only: touch feel of the 32 dp options with the 48 dp expansion, real font and display sizes.
Decisions: `docs/decisions/timelineClean.md`. Strings: `L10nW29timelineClean.kt`.

## Adversarial review findings (all fixed, each with a test that failed first)

| # | Finding | Fix | Test |
| --- | --- | --- | --- |
| 1 | At Comms 125 percent (any font scale) the week chart was cropped on the right: `VizSteps` clamps every column to 48 dp, 7 x 48 dp is wider than the 125 percent screen. The owner's "cropped" complaint, left in the usage block. | The usage block passes `minCell = 36.dp` (the framework still widens each tap target to 48 dp). | `USG_35_theWeekColumnsStayInsideTheScreenAtComms125*` (failed 2 of 3 before) |
| 2 | `rememberStackLabelFits` returned one entry per non-empty part, so an empty part shifted every later index (wrong part got the percent, or lost it, and the highlight hit the wrong part). | One entry per input part; `VizStack` maps labels and highlight through the original index. | `USG_37_theLabelFitListHasOneEntryPerPartEvenWhenAPartIsEmpty` |
| 3 | `rememberStackLabelFits` measured text on every recomposition (main-thread layout work, five measurements per frame while a legend line is tapped). | Wrapped in `remember` keyed by parts, width, density and font scale. | covered by USG_37 and USG_30 (behaviour unchanged) |
| 4 | The two filter groups were plain rows: screen readers did not announce "range" and "kind" as radio groups. | Each group is a `selectableGroup()`. | `TLF_03_eachFilterGroupIsASelectableGroupForScreenReaders` |
| 5 | Filter labels were letter-spaced 1 sp in every language; spacing breaks joined Arabic letters and Devanagari headlines. | `filterLetterSpacingSp` gives 0 for Arabic and Devanagari. | `TLF_04_arabicAndHindiLabelsAreNotLetterSpaced...` |
| 6 | A saved legend pick could outlive its part (data change under the same day): every part was then dimmed to 40 percent with nothing highlighted. | `clampPick` turns a stale index into "none". | `USG_38_aPickedLegendLineThatNoLongerExistsHighlightsNothing` |
| 7 | Guard added: the app name never collapses to zero width at font 2.0, Comms 125, 300 dp (the overlap test would pass trivially with a zero-width name). | none needed, it holds | `USG_36_theAppNameKeepsRoom...` |

Reviewed, not changed: adjacent 48 dp tap targets of legend lines overlap by design (24 dp lines, 8 dp gaps); Compose picks the nearest centre. The Timeline filter wiring in `HomePanels` (prefs persistence) is app-module code with no Robolectric host here; device-only.

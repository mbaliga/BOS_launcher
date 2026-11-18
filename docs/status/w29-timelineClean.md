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

# Wave 29 usageSimplify: Timeline without filters, simpler usage block

Owner: remove the range and kind pills from the Timeline; the usage block was a mess. Decision: `docs/decisions/usageSimplify.md`. Nothing here is device-confirmed: everything below was verified on the JVM with Robolectric, with screenshots read by eye.

| ID | Status | Note |
|---|---|---|
| HOM-18 (timeline) | changed | No filter row. One behaviour: last 100 opens with scheduled launches, newest first, day headers (tests `HomeInfoW3Test.HOM_18_theTimelineIsTheLast100Opens...`, `TimelineFixedW29Test`). |
| HOM-17 (usage tile range) | unchanged | The home Usage tile keeps `panel.usage` and its settings row. |
| CFG-03 (retired keys) | changed | `panel.timeline.depth` and `panel.timeline.scheduled` join `RetiredPrefKeys` (`RetiredPrefKeysTest.CFG_03_w29_theTimelineFilterPrefs...`). |
| Usage block | changed | Day line, seven thin columns, ranked list of five rows with textured 4 dp bars, hairline; no stack, legend or percentages (`UsageSimplifyW29Cases`, `UsageBlockCases`, `UsageShapingTest.USG_40_*`). |
| Loading, jump, Allow button | fixed | `USG_50_*` (placeholder holds the height, reopen shows the last answer), `USG_49_*` (accent 48 dp text button). |
| L10n | changed | `L10nW29usageSimplify.kt` ("Loading screen time"); seven obsolete strings and the empty `L10nW29timelineClean.kt` removed. |

## Deleted or rewritten tests
- `UsageLegendW29Cases` + Portrait/Landscape tests: deleted (legend, stack-label fits, filter row `TLF_01` to `TLF_04`, `filterLetterSpacingSp`); the overlap matrix (font 1.0/2.0 x Comms 75/100/125, portrait and landscape) is rewritten as `UsageSimplifyW29Cases` `USG_44_*` over the new block.
- `UsageBlockCases`: `USG_10`, `11`, `12`, `13`, `14`, `15`, `16`, `17`, `18` rewritten from stack/legend to rows/bars (height range, `usage.row.*` tags, no percentage, bars carry no hue).
- `UsageReviewW28Test`: `USG_32` (stack in RTL) rewritten as the bar in RTL; `USG_33` no longer measures the stack.
- `UsageShapingTest`: the `VizStackGeometry.widths` assertions replaced by `VizBarGeometry.length`.
- `HomeInfoW3Test.HOM_18_depthCutsOpens...`: rewritten to the fixed behaviour.

## Not done / device-only
- Nothing was run on a device; the usage permission flow, real app icons and the real Timeline scroll are JVM-verified only.
- The block is about 300 dp at 100 percent, not 260: five 36 dp rows are 180 dp by themselves.
- At Comms 125 percent on a 360 dp phone the seven 48 dp columns can reach about 26 dp into the right margin (not clipped).

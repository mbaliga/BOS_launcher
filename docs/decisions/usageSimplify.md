# Decision: Timeline without filters, and a simpler usage block (wave 29, usageSimplify)

Owner (two messages, build 58): "Remove the last 30 launches 100 launches etc pills and with scheduled launches and opens only from the timeline section" and "The usage block is a mess and hard to read." The previous stream (timelineClean) had re-laid the legend and flattened the pills; the owner wants them gone and the block readable at a glance.

## 1. The Timeline has no filter
- The filter row (range 30 / 100 / ALL, kind SCHEDULED / OPENS ONLY) is removed from the Timeline view (`CommsFilterRow`, `FilterOpt`, `TimelineFilterRow.kt` deleted); no replacement control.
- One fixed behaviour: the last 100 opens (`TimelineView.OPENS`) with the scheduled launches, newest first, grouped by day headers as before (scheduled ones, being upcoming, come first).
- Deleted: `TimelineDepth`, `HomePanelPrefs.timelineDepth`, `HomePanelPrefs.timelineScheduled`, the "Timeline panel" block of the Home panels settings page (its two pill rows), the `depth` / `withScheduled` state of the Timeline panel. `TimelineView.build` lost its two parameters.
- Persisted keys `panel.timeline.depth` and `panel.timeline.scheduled` (file `home_state`) are listed in `RetiredPrefKeys` (so an upgrade removes them once; the purge marker name carries a new suffix so it runs again) and are never read; an old install loses nothing else. The home Usage tile's own range (`panel.usage`) is a different feature and stays.
- Strings deleted from their tables: "Last 30 opens", "Last 100 opens", "With scheduled launches", "Opens only", "Timeline panel", "Everything recorded (up to 5000)", "Everything recorded, up to 5000" (the whole `L10nW29timelineClean` table, now empty), "%s, %s, %d percent".

## 2. The usage block: three parts
Top to bottom, 16 dp style margins of the list (the block adds none), 12 dp between the parts (`CommsStyle.USAGE_GAP_DP`), a 1 dp hairline under the block, content-sized (no fixed height). Everything is in the Comms scale (`CommsStyle.USAGE_*`) and follows the font scale.

1. **Day line**: the selected day's name (muted, 14 sp) and its total as the one big figure (stat, 28 sp, light), left-aligned, on one baseline; at font scale above 1.3 the caption stacks over the figure and the figure steps down to 22 sp so "2 h 34 min" still fits.
2. **Week**: `steps`, seven thin columns (at most 16 dp wide, 3 dp rounded tops, from one baseline) in cells of at least 48 dp of real screen (`commsTarget`, so at Comms 125 percent they still fit); today in the accent, the selected column in the primary ink, the rest in muted ink, the day initial under each, the only number is the selected column's compact figure above it. Tap selects.
3. **Ranked list**: up to five rows, the top four apps then Other (`UsageShaping.rows`): each at least 36 dp high (grows with large text), a 20 dp coloured launcher icon (Other has an empty slot), the app name (weight 1, one line, ellipsis), the duration right-aligned in muted ink, and under both a 4 dp bar (`VizBar`, the toolkit's `bars`) as long as the row's share of the day's longest row, filled with the app's texture (solid, hatch, dots, fade; stipple for Other; texture rules from usageViz are unchanged), on a faint 8 percent track. The longest row is a full bar (Other, being a sum, can be it). Bars start at the reading start (right in RTL). No percentages anywhere, no split bar, no legend.
   - The row is a 48 dp touch target (framework minimum of the Comms host); a tap highlights the row and dims the others to 40 percent, a second tap clears; a pick belongs to its day. Semantics: button, selected, "App, duration".

Kept: the missing-permission state (now a quiet "Screen time" line over an "Allow usage access" text button in the accent, 15 sp medium, 48 dp), the quiet empty line, the accessible chart summary (without percentages) and the "Show as table" action (the table replaces the week columns, the list stays), app-language day names, RTL mirroring, no animation, font scale 2.0, light theme.

Height: about 300 dp at 100 percent with five rows and the hairline (the owner's "about 260" is not reachable with five 36 dp rows plus a 28 sp line and the week: rows alone are 180 dp). It never clips: nothing in it has a fixed height.

## 3. The three open points of the previous review
- **Loading is not 1 dp**: while the answer is read off the main thread a quiet placeholder of the same three parts (a short bar, seven stubs, five icon and bar lines, 8 percent ink, no text, one spoken phrase "Loading screen time") holds the finished block's height; `produceState` starts from the last answer for the same repository, so reopening the Timeline shows the numbers at once.
- **The list does not jump**: the placeholder and the finished block are built from the same constants; when the answer is "no access" or "empty" the block shrinks once (unavoidable: it has less to say).
- **The Allow button**: a text button in the accent (the toolkit's yellow on dark, the darker olive on light), 48 dp.

## Removed from the viz port
`VizStack`, `VizLegend`, `VizSwatch`, `rememberStackLabelFits`, `VizStackGeometry` (the stack and its legend); `VizBar` and `VizBarGeometry` are added. `VizSteps` columns are thinner (radius 3 dp, at most 16 dp) and the figure above the selected column is 14 sp regular.

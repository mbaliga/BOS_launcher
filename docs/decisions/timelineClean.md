# Decision: Timeline usage legend and filter row (wave 29, timelineClean)

Owner (build 58, annotated screenshot): the usage block's legend overlapped the percentage labels under the stack and itself, and the Timeline's filter pills were large, purple and wrapped onto two rows.

## Legend
- One entry per line under the stack: swatch (14 dp), the coloured app icon (16 dp), the name (weight 1, ellipsizes), the percentage (only where the stack has none), the duration right-aligned in muted ink. Text is the Comms note size (`CommsStyle.NOTE_SP`), so it follows the Comms scale.
- A line is 24 dp or more (it grows with large text; nothing has a fixed height), 8 dp apart, up to five lines. The block grows with the lines: about 273 dp at 100 percent with five lines.
- The whole line is the tap target (48 dp touch target: Compose widens a smaller clickable to the view configuration's minimum, which `CommsScaleHost` keeps at 48 dp of real screen; nothing is drawn larger). A tap highlights that app's stack part (the others at 40 percent), a second tap clears it. Semantics: button, selected.
- The percentage has ONE place: on the stack part when its label fits (`rememberStackLabelFits`, the same `est <= width - 4` rule the stack draws by), otherwise in the legend line. The stack takes the answer as `labelFits`, so the two can never both print or both miss it.

## Filter row
- `CommsFilterRow` replaces the two `SettingChips` pill rows on the Timeline body: one horizontally scrolling row of 12 sp letter-spaced capitals, muted ink, the chosen option in the primary ink, medium weight and a 2 dp accent underline; two groups split by a 1 dp vertical hairline: range `30 100 ALL`, kind `SCHEDULED | OPENS ONLY`. No fills. Options are 32 dp high; touch targets reach 48 dp through the framework minimum.
- Persisted state is unchanged (`panel.timeline.depth`, `panel.timeline.scheduled`). Each option is a radio (selected, `Role.RadioButton`); spoken names stay the long ones ("Last 30 opens", "Last 100 opens", "Everything recorded, up to 5000" new, "With scheduled launches", "Opens only"). The Ground Control settings rows keep the old labels and pills (outside this stream).
- At font scale 2.0 and Comms 125 percent the row scrolls instead of wrapping.

## Known limit
The steps chart's right-most label ("2.6 h") can touch the edge at 125 percent in the narrow test host; it is not part of this stream.

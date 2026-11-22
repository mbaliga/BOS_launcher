# Wave 50: glanceClockEdit (move and adjust the Glance clock in place)

| ID | Status | Note |
| --- | --- | --- |
| HOM-23 clock edit mode | built, not device-verified | Long press on the clock enters an in-place edit mode (frame, drag, options panel); long press on empty Glance space is the unchanged home long-press action. `GlanceClockEditW50Test` (Robolectric). |
| HOM-23 clock place | built | Fractions of the safe area (centre x, top y), clamped, per orientation, default = the old place (`ClockPlacementW50Test`, `GlanceClockEditW50Test.HOM_23_dragMoves...`). |
| HOM-23 snap | built | 10 dp to the centre lines, haptic tick on catching; maths in `ClockPlacementW50Test`; the haptic itself is device only. |
| HOM-23 options | built | Style (full radio page), size, 12/24, date, colour, live; Done (box) over Reset position and Cancel (text); Cancel, Back, Esc restore the snapshot. |
| HOM-23 accessibility | built, TalkBack not device-checked | Custom actions Edit, Move up/down/left/right, Larger, Smaller (`HOM_23_talkBackCanEdit...`); arrow keys and Esc while editing, Menu key on the focused clock; RTL test. |
| HOM-23 backup | built | Four new float keys in `launcher_prefs`; `StateCodecTest.CFG_01_everySettingIsCarried` covers them. |
| INP-03 / INP-17 | unchanged | The clock stays one focus node; it gained the Menu key action. |

## Tests run
- core-data: whole module (`ClockPlacementW50Test`, `GlanceModelTest`, `StateCodecTest`) pass.
- feature-grid: `GlanceClockEditW50Test` (20 tests) pass; whole module run, see the final report.
- Screenshots read: `feature-grid/build/screenshots/w50_clock_edit_portrait.png` (phone, panel below the clock, frame and corner dots, Done box over text actions), `w50_clock_edit_landscape.png` (panel on the left, clock on the right).

## Old tests changed or removed
None changed for a fixed position: the factory place is unchanged, so the wave 41 clock tests still hold.

## Device only
Drag feel and haptic tick, TalkBack actions, hardware keyboard Menu key, tablet and foldable sizes, look over live wallpapers.

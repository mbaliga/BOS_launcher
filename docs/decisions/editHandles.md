# editHandles (wave 45, stream A): selection, handles, live resize, ghost, limits

Owner feedback: "how the home and pages should look like while being edited as well as the widgets, the handles and editing experience is bonkers". Diagnosis in `docs/research/home-editing.md` section 1. This stream builds section 2b. The chrome (top bar, tray, scale and scrim) and the item action bar are other streams.

## Decisions (owner, asked and answered)
- Icons push aside, widgets block (a widget cannot overlap another item; a reject haptic says so), an icon dropped on an icon makes a folder.
- One Done only: the in-widget Done pill is removed; the top bar of the chrome stream has the single Done.

## What was built
- `core-catalog/EditHandles.kt` (pure): `ProviderLimits` -> `CellLimits` (min rounds up, max rounds down, a single-axis provider keeps its size on the other axis, a minimum above the current size never forces a shrink), `HandleSet` (which of eight handles), `ResizeMath` (a handle drag in cells or pixels; west and north handles move the origin; clamps to limits and grid and says why), `EditCollision` (widgets block, icon on icon = folder, icons push), `GhostSnap`, `OriginFit` (the index at which the packer puts an item on a wanted cell), `CellEdit` (one-cell moves and resizes for accessibility and keyboard), `EditHandlesApply` (the host's edit).
- `feature-grid/WidgetEditUi.kt`: `SelectedWidgetFrame` (outline, handles, live content as one graphicsLayer, dashed ghost, size chip, brighter dots, spring on release), `selectionOutline` for icons, folders and tiles, `editTouchShield`, `childrenOutOfBounds`.
- `EditSelection.kt`: `EditSelectionState`, the hook of the item action bar (selected item id, type and bounds; `onRemove`, `onDuplicate`; `fallbackBar` false hides the temporary Remove button).
- `ItemEditor.kt`: the non-drag edits (accessibility actions, keyboard) with announcements. `SelectionHook.kt`: the live region and the 48 dp Remove fallback. `WidgetDropMath.kt`: where a dragged item lands.
- `HomeItemsHost.providerLimits` reads `resizeMode`, `minResize*`, `maxResize*`, `targetCell*`, `minWidth/Height` for the phone home (the desktop surface read them already); the host applies a resize through `EditHandlesApply` (span, and re-ordering when the origin moved).

## Choices worth knowing
- The layout model is an ordered list per page packed first-free into the grid (`TilePacker`), not free positions. "Grow up or left" therefore changes the span and re-orders the widget so the packer puts it on the new origin (`OriginFit`); the new rectangle is only accepted when it is free of other items, so nothing else moves for a resize. Icons get no free placement either: pushing aside is the reflow of the list, which was already animated.
- Knob position: its centre sits 6 dp outside the content edge (the research says "centred on the outline"; the outline is 3 dp outside, so a knob centred on it would still cover 2 dp of the content). The 12 dp knob is inside a 48 dp hit area.
- Hit testing: the handles are half outside the widget, so the widget's slot declares `childrenOutOfBounds` and the selected widget gets a higher z-index; the knob consumes its press at once (a custom drag) so the page's gesture arbiter never reads it as a grab of the icon below.
- Hosted widget views swallow touches, so in edit mode a shield sits above them (takes the events, consumes none) and the arbiter sees tap, long press and drag. Outside edit mode nothing changes.
- Edit-mode tap on an icon or widget selects instead of opening; the press-and-move grab (icons at once, widgets by long press) is the old arbiter path and selects what it drags.
- The old remove badge stays on icons, folders and tiles (HOM-12 tests, and no bar exists yet) and is gone from widgets (it covered their content); the Remove button next to the selection is a fallback.
- Reject haptic: Compose 1.7 has only LongPress and TextHandleMove, and the launcher routes all haptics through `StrengthHaptics`; the reject buzz is the long-press one, the snap tick the text-handle one. A `Reject` type needs Compose 1.8.
- Not done here: the cells' "half cell" Nova-style snapping, duplicate (only the hook), stacks have free limits.

## Removed
`WidgetResizeFrame` (3 handles of 28 dp, the Done pill) and the audit line pinning its "Resize handle" string. Tests updated: `WidgetEditHandleW24Test` (the badge on widgets and stacks became tap-to-select plus the Remove button), `InputW3UiTest` (a widget's Shift+arrow resizes through the rectangle path), `W4MotionAuditTest` (the accessibility pin points at `ItemEditor`).

# Wave 45: editHandles (HOM-07, HOM-12, NFR-13, INP-09)

| ID | Status | Note |
| --- | --- | --- |
| HOM-07 | built, feel unverified | Handles per provider limits (`resizeMode`, `minResize*`, `maxResize*` read on the phone home), eight handles for both axes, only the allowed edges for one axis, none for fixed; 12 dp knob in a 48 dp hit area outside the content; top and left handles move the origin (re-order to the new origin); live content (graphicsLayer), dashed ghost, size chip, error state, brighter dots, spring on release; limits and neighbours clamp. Tests `EditHandlesTest` (pure), `EditHandlesUiTest`, `EditSelectionGridTest`. |
| HOM-12 | built, feel unverified | One selection for every item (tap selects, again or empty space deselects, edit mode off clears), outline 3 dp outside, drag shows the ghost, icons push aside live, widgets block (refused on drop), icon on icon highlights the target and files; in-widget Done and 28 dp handles removed; `EditSelectionState` hook; 48 dp Remove fallback. |
| NFR-13 | built | Every item node carries Move left/right/up/down, Make wider/narrower/taller/shorter (widgets) and Remove with a live-region announcement. |
| INP-09 | built | Arrows move the selected item one cell (widgets by cell, blocked by what is in the way), Shift+arrows resize one cell within limits, Delete or Backspace removes (`LauncherInput.deleteHook`). |

## Tests run
`:core-catalog:testDebugUnitTest`, `:core-design:testDebugUnitTest`, `:core-quality:testDebugUnitTest` green. `:app:testDebugUnitTest` (438 tests): the failures left are `DockStackW26SmokeTest`, `GlanceEdgesSmokeTest` (the same Glance indicator tags) and `PackageRemovedW26SmokeTest` (known flaky under load); not run on the base commit. `:feature-grid:testDebugUnitTest` (1367 tests): green except 5 tests in `GlanceHomeUiTest` and `HomePagesW26ScreenshotTest` about the Glance page indicator (`glance.indicator.right.Recents`, the "Show the dock" dot); they concern the indicator the base commit changed ("tests to follow") and do not touch this stream. I did not run them on the base commit.

## Tests updated, with the reason
- `WidgetEditHandleW24Test`: the remove badge on widgets and stacks is gone; a tap selects (hosted view shielded) and the Remove button removes.
- `InputW3UiTest.INP_09_...widget_resizes...`: a widget's Shift+arrow now goes through the rectangle resize path (limits, origin), not `ItemAction.Resize`.
- `HomeLandingItemsW24Test` (app): the two tests that clicked the widget's remove badge now remove through the item node's "Remove" accessibility action.
- `W4MotionAuditTest.NFR_13_...`: the accessibility pin moved from the handle to `ItemEditor`.

## Screenshots (`feature-grid/build/screenshots/45-*.png`, read)
Dark: `45-edit-widget-selected` (outline 3 dp outside, eight knobs outside the corners and edge middles, content untouched, Remove button above the item), `45-edit-mid-resize-ghost-chip` (3 x 3 into icons: ghost, outline and chip in the error colour), `45-edit-mid-resize-free` (2 x 3 into free cells: primary colour, brighter dots), `45-edit-icon-drag-push-aside` (dragged icon, others reflowed, dashed ghost on the landing cell), `45-edit-widget-selected-landscape` (small widget: corners only, as the 96 dp rule says; the Remove button overlaps neighbouring icons there). Rendered with Robolectric, not a device.

## Device only (not verified)
Feel of the drag and the spring, haptics (reject is the long-press buzz, see the decision), conflicts of edge handles with system gestures and the pager, real `AppWidgetHostView` touch handling behind the shield, TalkBack reading order and announcements, RTL (mirrored columns are handled in code and not screenshotted), scrolling homes (list, XMB, DSi, column scroll get east/south handles only, and a handle at the outer edge of a lazy list may be clipped).

## Not covered by a test
The ring on the folder target while an icon hovers over another (it needs the drop dwell timer), reject haptics, RTL ghost placement.

## Not matched
Handle centred exactly on the outline (6 dp out instead); half-cell snapping; duplicate; the item action bar and the chrome are other streams; a widget moved to another page is placed by the ordinary index drop (blocking is checked on the target page only when it exists).

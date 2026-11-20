# Liftoff home and page editing: research and design (wave 44)

Legend: [S] = from a source I fetched or searched (URL in section 5); [C] = read in the repo; [J] = my own judgement. Nothing here was device-tested.

## 1. Diagnosis

Home edit mode with a widget selected (home-edit-widget.jpg; `WidgetEditUi.kt`, `HomeExtrasUi.kt`):
1. Handles are 28 dp circles, visible size = hit size, below the 48 dp minimum [C: `Handle()` `size(28.dp)`; S: Android/Material 48 dp]. They are placed ON the edge midpoints, so half of each sits over widget content (the bottom-end handle covers the Amazon logo, the right handle covers the price). Only 3 of 8 handles exist (E, S, SE), so a widget cannot be grown up or left; drag is the only visual path.
2. Resize is applied on release only: no live preview, no ghost, no size label [C: `onDragEnd` -> `deltaForDrag`]. The user cannot see which cell count they will get or that they hit a limit.
3. Limits are invisible. The provider's resize rules (`resizeMode`, `minResizeWidth/Height`) are read only for the desktop surface [C: `DesktopWidgetProvider.kt:43-51`]; the phone home uses `WidgetSizing.spanFor(minWidth...)` and `canGrow`, ignores `maxResize*` and `resizeMode` [C: grep found no other use]. A handle that does nothing looks broken.
4. The "Done" pill is 12 sp text, 10x4 dp padding (about 28 dp high), drawn INSIDE the widget's top-left corner and covering its content ("Deal" badge area in the screenshot); low contrast (white on 90% soft violet). It exits only widget resize, not edit mode, so two different "Done"s exist (this and the bottom bar's) [C].
5. The outline is a 2 dp pale violet stroke at 12 dp radius that clips against the widget's own 16 dp clip and the widget's own rounded corners (visible double outline in the screenshot). No scrim on the rest of the page, so selection is only weakly marked.
6. No grid feedback: faint dots exist in the background but no cell grid, no snap ghost, no drop-target highlight. Neighbouring items (clock tile, Aadhaar icon) look identical to non-edit mode: nothing says "this is editable".
7. Chrome conflicts: the left notification tray stays visible and overlaps the widget's left edge zone; the dock stays and the page indicator stays at the bottom, so the bottom-end handle is 130 px from the dock and the system gesture bar; right-edge handle (x about 740 of 905) sits near the back-gesture edge. (The home activity is exempt from the 200 dp exclusion cap [S], and the repo bans `setSystemGestureExclusionRects` [C: NFR-20 audit], so edge handles must be kept inside the page's own inset instead.)
8. No toolbar in this state: no Undo/Redo, no Remove, no Duplicate near the selection; the bottom edit bar belongs to the page editor, not this mode.

Clock tile editor (clock-tile-editor.jpg; `Editors.kt:313 TileMenuDialog`, `HomeScreen.kt:1752`):
9. A full-screen dark dialog with one sentence, one big pill and a Cancel pill. The sentence "Long-press and drag to move it." is an instruction, not an option. The tile's real options (style, 12/24 h, date) live elsewhere (Settings row "Clock tile on this page") [C: `SettingsCategories.kt:242`]. 95% of the screen is dead space; "Remove" is the primary action (violet) while Cancel is secondary, so the loudest control is destructive.

Page editor (frames pe_04, pe_09; `PageEditorHost.kt`, `PageEditorTabs.kt`, `PageEditorGeometry.kt`, `pageEditor.md`):
10. The live page card is positioned half off the right screen edge (card x starts at 52% and runs to and past 100%): a phone-shaped card is cropped, so the user never sees a whole page. The "strip" is therefore not a strip of pages, it is one oversized card plus peeks of neighbours above and below.
11. Left column: a hamburger, four text tabs, a Settings block and a "Delete page 1" outlined button take the left 38%, but with Apps/Widgets/Folders unselected the content area is mostly black. A rail of three tiny circular buttons (up, down, x) floats mid-screen, detached from the card, 48 dp disabled-looking circles with 10 dp glyphs. Delete page appears twice (rail x and Settings button).
12. Stray artefacts: a grey 24 dp circle floats in the card (a drag ghost / strip seam leftover; I could not determine which from the frames [unverified]); a lone 6 px square near the bottom; a dashed "+" card with no label.
13. Hint toast "Editing home: drag items to rearrange, long-press for more" sits in the status-bar safe area at the very top, overlapping the camera region and looks like a notification, not a hint; it is not dismissible and not repeated contextually.
14. Undo / Redo / Add page / Done float as four pills at the bottom, Undo and Redo are dim by default so they read as disabled; "Done" is the same violet as the tile editor's destructive "Remove from page" (one colour, two meanings). Add page exists in three places (bar, hamburger, strip plus) [C: editorDebt.md].
15. Page identity: Settings tab says "Page 1 of 1" and "Pages use the same wallpaper as Glance (Applies to the pages and the scroll alike)": a global toggle presented as a per-page setting; the text wraps at 4 lines in a 140 dp column.

## 2. Recommended design

Principles [J, grounded in 5]: (1) edit the real page in place, scaled slightly, never in a separate screen for single-item options; (2) one selection model for every item type; (3) every drag has a non-drag equivalent; (4) show the result before committing (ghost, label); (5) one Done, one Add path, one Delete path; (6) chrome that must stay (four hotspots, music line, dots) is dimmed but not hidden, and never overlaps an editable cell.

### 2a. Home in edit mode
- Enter: long-press empty area, Edit home, Ground Control. The live page animates scale 1.0 -> 0.88 (Material "emphasized" 300 ms; reduced motion: 120 ms fade), content stays fully live. Scale leaves 28 dp side margins so edge handles have room off the system edge zone. Background dim: wallpaper scrim 35% black; page rect gets a 1 dp 12% white hairline and a 20 dp radius to read as "the canvas".
- Grid dots: 2 dp dots at every cell corner at 18% white (currently decorative; make them the real grid), brightened to 40% inside a 1-cell radius of an active drag. No full grid lines.
- Fixed chrome: four hotspots, music line and page dots are dimmed to 40% alpha and non-interactive for edit, except page dots, which become the page switcher. The dock stays (it is a drop target for icons) but dims; the side notification tray is hidden in edit mode (it shows on every page and collides with the left column) [J].
- Toolbar: a 56 dp top bar inside the safe area: left "Cancel" (text button, 48 dp), centre title "Editing page 2 of 4" (14 sp, doubles as the hint place), right "Done" (filled tonal, 48 dp high). Undo and Redo are 48 dp icon buttons beside Done, enabled/disabled colour at 100%/38% with a tooltip and a badge of the history depth only on long-press. Bottom tray (72 dp, above the nav inset): three chips "Apps", "Widgets", "Folders" that open a half-height sheet (picker), plus a "+" page chip at the end. The picker sheet replaces the left column tabs while on the home; the full page editor (2d) is entered from the title or from the page dots.
- Hint: one line under the top bar for the first 3 entries only ("Drag to move. Tap an item to resize or change it."), then never again [J]. Not a toast.

### 2b. Selection and handles
Wireframe, home edit mode with a widget selected (phone portrait, dp; page scaled):

```
+--------------------------------------------+
| Cancel     Editing page 2 of 4   [<][>] Done|  56 top bar
|                                            |
|   (clock)  (icon)          . . . . . .     |  dots = cell corners
|  +-- 2 selected scrim: rest of page -35% --+|
|  O=========================================O|  corner knobs 12 dp visible,
|  |  Deal card (widget, live)               ||  48 dp hit, centred 6 dp OUTSIDE
|  |                                  [ 3 x 2 ]|  size label chip, 24 dp high,
|  O                                         O|  follows the active corner
|  +-------[ Resize | Remove | Settings ]----+|  action bar 48 dp, below widget
|   ghost: dashed 2 dp rounded rect = snapped |
|   cells while dragging                      |
|     (dock dimmed)                           |
| [Apps] [Widgets] [Folders]  [+ page]       |  72 tray
+--------------------------------------------+
```
- Selected state: 2 dp outline in primary at 100%, radius = widget radius + 2 dp (outer, drawn 3 dp OUTSIDE the content bounds so it never clips the content); non-selected items stay unscrimmed and get no outline; tap on empty area deselects. Drag lift: scale 1.04, 8 dp shadow, 12 ms haptic `CLOCK_TICK` on lift.
- Handles: 4 corners + 4 edge midpoints for widgets that allow both axes; only the allowed ones for single-axis providers (hide the rest, do not grey them) [S: resizeMode]. Visible knob 12 dp filled circle with 2 dp white ring; hit area 48x48 dp centred on the knob, which sits on the outline (half outside). If widget is smaller than 96 dp in a dimension, edge handles are dropped and only corners remain (avoids overlapping hit zones); corner priority over edge in overlap [J].
- Live resize: while dragging the widget outline follows the finger smoothly, the ghost (dashed, 15% primary fill) snaps to whole cells, the label chip reads "3 x 2" (cells) and turns error colour with a 10 ms reject haptic when the limit is hit (min/max from `minResizeWidth/Height`, `maxResizeWidth/Height`, `resizeMode` of the provider, converted to cells via the cell pitch; plus free space). Commit on release with a 120 ms settle animation; haptic `GESTURE_END` on snap changes (`SEGMENT_TICK` per cell).
- Collision: default is "push aside" for icons (they reflow like a list, as in Pixel Launcher/iOS) and "blocked" for widgets (ghost turns error colour, release snaps back). Dropping onto an icon creates a folder only for icon-on-icon with a 400 ms hover [J, matches iOS/Pixel behaviour from experience; not re-verified].
- Icons: selected icon gets a 2 dp outline ring, a corner remove badge is NOT used (accidental taps); removal is in the action bar. Folders: selection opens the same action bar with Rename, Open, Remove. Tiles (clock): same bar with Settings (see 2c).
- Edge safety: keep all handles at least 16 dp inside the window edges by scaling the page to 0.88 (above) so no handle sits in the back-gesture zone (about 24-30 dp from edge [S: gesture nav docs]); the repo's rule against gesture exclusion stays.

### 2c. Item action menu replacing full-screen editors
Selection shows a 48 dp action bar (pill, 8 dp radius, elevated) anchored below the item (above if no room): `Resize`, `Remove`, `Settings`, overflow `...` (Duplicate where it makes sense). It replaces `TileMenuDialog`.
Settings opens a modal bottom sheet (Material 3 `ModalBottomSheet`, partial height, 16 dp corners, drag handle), never a full screen:
```
+-----------------------------+
|  ====                       |  drag handle 32x4
|  Clock tile            [x]  |  title 20 sp, 48 dp close
|  Style      ( Digital | Analog )   segmented, 48 dp
|  24-hour    [ switch ]             48 dp row
|  Date       [ switch ]
|  Size       ( 2x1 | 2x2 )          presets
|  --------------------------
|  [Remove from page]  (outlined, error colour text)   [Done] (filled)
+-----------------------------+
```
Widgets: Settings = "Reconfigure" (provider's configure activity if present), size presets from provider limits (S/M/L as cells), and "Remove". Folder: name field, icon style, remove. Remove is outlined error, never the primary; deletion is immediate with a 6 s Undo snackbar (existing notice component).
Long-press on any item in edit mode = select + open the action bar; a long press outside edit mode keeps entering edit (and the Android shortcuts popup for apps).

### 2d. Page editor
Fix the card first: the live page card must be fully on screen. Paginated (portrait):
```
+------------------------------------------------+
| Cancel          Pages (4)         [<][>]  Done |
|  Apps | Widgets | Folders | Settings  (tabs)   |  48 high, tabs are on TOP, scrollable
|  --------------------------------------------- |
|  tab content panel (grid, search) 40% height    |
|  ---------------------------------------------  |
|  [pg1][ pg2 *][pg3][ + ]   <- strip, 96x170 cards, 12 gap,
|   Glance  focused is 112x198 with 2 dp outline   |  horizontal, drag to reorder
|  --------------------------------------------- |
|  focused page, shrunken live (about 55% scale)   |
+------------------------------------------------+
```
I recommend the owner's side layout only in landscape/tablet; in portrait the vertical strip costs width the content needs. Alternative if the owner insists on the side strip in portrait: strip width 72 dp at the END, tabs on the START in a 120 dp rail, live card centred with at least 16 dp both sides, never cropped [J].
- Strip: cards 72x128 dp (96x170 focused), page number under each, Glance first with a lock glyph (not draggable, not deletable). Tapping a card swings it to focus (keep the existing 350 ms swing). Long-press then drag reorders (haptic on pick-up and on each slot change; neighbours slide 150 ms). Move earlier/later buttons are not floating: they live as TalkBack custom actions and as "Move left/right" in the page's overflow menu (card `...`, 48 dp) with Delete there.
- Add: the trailing "+" card, labelled "Add page" (12 sp), same 72x128, dashed outline. It is the only visual add path; the top-bar menu keeps "Add page" for keyboard. Remove the bottom pill row.
- Delete: card overflow -> Delete page; non-empty pages ask once with a count ("Delete page 2 and its 7 items?"), then 6 s Undo snackbar. Empty-page state: the focused empty page shows a centred 48 dp "+" and "Add apps, widgets or folders" text opening the tab content.
- Continuous: same strip rotated across the top is replaced by one horizontal row of column-groups with a draggable viewport frame over the rows (iOS page-dot thumbnails analog) [J]; tabs below. Unchanged behaviour of scroll-to-page from wave 37.
- Settings tab: global items get a "Applies to all pages" subtitle (wallpaper), per-page items (name, hide page = iOS-style hide, delete) under "This page". Hide page is a recommendation from iOS [S] and needs a model field (see decisions).
- Done and Cancel in the top bar; Undo/Redo icon buttons beside them.

### 2e. Transitions
Enter: 300 ms emphasized scale to 0.88 + dim + top bar slides in 150 ms; grid dots fade in 150 ms. Exit: reverse, 250 ms. Page swing: keep 350 ms FastOutSlowIn tilt. Item pick-up: 100 ms scale 1.04. Reduced motion: cross-fades 120 ms [S: Material motion; existing swing already honours it].

### 2f. Non-drag alternatives
- Every item: TalkBack custom actions "Move left/right/up/down by one cell", "Move to page N", "Resize wider/narrower/taller/shorter", "Remove", "Settings" on the item node (the pattern is documented in the Compose accessibility codelab [S]). Today only the resize handle carries them [C] and the handle is itself a 28 dp node, so move the actions to the item.
- Keyboard/D-pad: Enter selects, arrows move one cell, Shift+arrows resize (exists [C: `GridKeyboardMove.kt`, `WidgetResizeFrame`]), Delete removes, Esc deselects then exits, Tab cycles handles.
- Announce results: "Resized to 3 by 2 cells", "Moved to page 2, row 1, column 3" via `LiveRegion`.
- Touch targets: 48 dp everywhere in the editor; strip card 72x128 passes.

### 2g. Remove
- `TileMenuDialog` full-screen (replace with sheet), the "Long-press and drag to move it." text.
- The inside-widget 12 sp "Done" pill; a separate Done for resize.
- The floating three-circle rail; the duplicate Delete page in Settings; the hamburger menu (Undo/Redo/Done move to the top bar, Add page to the strip).
- The bottom 4-pill bar, the top toast hint, the side notification tray in edit mode, the half-cropped card, stray ghost circle.
- Page-wide "Page 1 of 1" text block in Settings.

## 3. Implementation plan (parallel streams)

| # | Stream | Files | Size | Risks | Tests |
|---|---|---|---|---|---|
| A | Selection, handles, live resize, ghost, label, limits | `WidgetEditUi.kt`, `HomeExtrasUi.kt`, `HomeItemsHost.kt`, `DesktopWidgetProvider.kt` (reuse its resize rules), `core-catalog/Widgets.kt` (`WidgetSizing`) | L | provider limits to cells conversion; handle overlap on small widgets; gesture conflict with page pager | pure tests for cell clamping from min/max/resizeMode; Robolectric handle hit-size >= 48 dp; ghost snap |
| B | Item action bar and item sheet (replaces `TileMenuDialog`); clock tile real options | `Editors.kt`, `HomeScreen.kt:1752`, `SettingsCategories.kt:242`, new `ItemSheet.kt` | M | clock options model must exist (style, 12/24 h; check prefs); sheet over shrunken card | compose tests: sheet content per item type; Remove with Undo |
| C | Home edit chrome: top bar, bottom tray, scale/dim/grid dots, hint, hide tray/dim fixed chrome | `HomeEditBar.kt`, `HomeScreen.kt`, `EditCardGeometry.kt`, `GlanceEdges.kt` | M | scale must not break hotspot hit tests; indicator and dock interplay; first-run hint storage | Robolectric top bar state, undo/redo enablement; screenshot test if available |
| D | Page editor layout (card never cropped, strip with reorder, add card, overflow delete, empty state, tabs on top in portrait) | `PageEditorHost.kt`, `PageEditorGeometry.kt`, `PageEditorTabs.kt`, `EditCardWiring.kt` | L | reorder in continuous mode, existing swing maths, many existing tests asserting current geometry | geometry unit tests (card inside window for all sizes and RTL), reorder ops via `HomePageOps` |
| E | Accessibility and keyboard: node-level custom actions, live announcements, focus order | `WidgetEditUi.kt`, `GridKeyboardMove.kt`, `KeyboardEditOps.kt`, a11y sweep tests | M | duplicate nodes (handle + item); TalkBack order not verifiable here | `a11ySweep` style tests; semantics assertions |
| F | Motion and haptics polish, remove list (dead code, strings, L10n keys) | `EditCardHost.kt`, `PageCarousel.kt`, `AddCarousel.kt`, L10n files | S | deleting code with tests attached (`pageEditor.md` says kept for tests) | existing suites; spec rows HOM-07, HOM-12 updated |

Order: C and A first (the owner's screenshot), B next, D in parallel with E, F last. Update Spec HOM-07/HOM-12 and add a `docs/decisions/homeEditing.md`.

## 4. Decisions for the owner (default in bold)
1. Page strip in portrait: top/bottom horizontal strip (**yes**) vs side strip as in the mock-up (side kept for landscape/tablet only).
2. Hide a page (iOS-style) as an extra to Delete: **add later, not now**.
3. Push-aside for icons and block for widgets: **yes**; folder creation by dropping icon on icon: **yes**.
4. Side notification tray hidden while editing: **yes**.
5. Clock tile options to expose in its sheet: **style (digital/analog), 12/24 h, date line, size 2x1/2x2**; confirm which already exist in prefs.
6. Hint: **first 3 entries only** as a line under the top bar; and keep one Done (top bar) and remove the in-widget Done: **yes**.

## 5. Sources and what I could not verify
Sources used:
- Android widget sizing attributes, 48 dp cell table, `targetCell*`, `maxResize*`: https://developer.android.com/develop/ui/views/appwidgets/layouts
- Widget design: fill the bounds, breakpoints: https://developer.android.com/design/ui/mobile/guides/widgets/sizing
- AppWidgetProviderInfo reference (resizeMode, min/max resize): https://developer.android.com/reference/android/appwidget/AppWidgetProviderInfo
- Launcher3 resize frame (four edge handles): https://android.googlesource.com/platform/packages/apps/Launcher3/+/d97da07a54/res/layout/app_widget_resize_frame.xml
- Old Android widget pattern (long press then drag handles): https://stuff.mit.edu/afs/sipb/project/android/docs/design/patterns/widgets.html
- Nova subgrid (half-cell snapping): https://androidcentral.com/subgrid-positioning-why-i-keep-coming-back-nova-launcher
- iOS jiggle mode, page dots thumbnails to hide and reorder pages: https://support.apple.com/guide/iphone/move-apps-on-the-home-screen-iphd2fc8ce30/14.0/ios and https://www.macrumors.com/how-to/rearrange-delete-home-screen-pages-ios/
- Touch target 48 dp: https://support.google.com/accessibility/android/answer/7101858
- Compose accessibility custom actions: https://developer.android.com/codelabs/jetpack-compose-accessibility
- Gesture exclusion (200 dp cap, home activity exempt per the search summary): https://developer.android.com/training/gestures/gesturenav

Not verified: One UI Home, OxygenOS, Niagara, Lawnchair, Smart Launcher edit-mode behaviour (searches returned nothing usable; the corresponding choices above are my judgement from general knowledge); Material 3's own drag-and-drop/handle guidance (no page found; the 12 dp knob inside 48 dp hit area is my design, based on the 48 dp rule); the exact semantics of the 200 dp exclusion budget; the cause of the stray grey circle in the page-editor frames; current iOS widget-resize handle behaviour (sources conflicted); all numbers in dp are proposals, not measured against the real layout.

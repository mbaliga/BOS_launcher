# Home pages (wave 26, HOM-01, HOM-22, HOM-23): decisions

Owner request (clauses 4 to 9): "Home view will only be glance, no option to change. User can add a page after that and choose if successive pages are a scroll or paginated. Glance can be turned on or off. If it's off, the first page or scroll appears. In scroll view, the icons stack to fill the occupy-able middle space in portrait or landscape view column by column. [...] User can choose between left to right or right to left ordering. Right to left means the notifications are on the right edge and the columns get filled right to left."
Owner bug on build 54: "I can't add pages after the glance calm home page."

## The model
- The home is a deck: **Glance** (the calm screen, when on) followed by the **pages** the user builds. `HomeShown` (Glance, Pages) is runtime state of the deck, not a setting. The old `HomeLanding` (Selected apps, Recent apps, All apps, Minimal) and every code path behind it (the landing selector in Ground Control, the quick sheet, the Ctrl+Shift+L cycle command and its corner/gesture action, the Recent apps and All apps landing screens, the Lab entry, the search-index entry) are deleted. The Recents panel on the other edge stays; it was never a landing.
- Settings (all plain exported prefs in `launcher_prefs`): `glanceEnabled` (default on), `homeOrder` (`HomeOrder`, default Left to right), `pagesModeChosen` (false until the user chose once), `homeScroll` (Paginated or Scroll; the enum names `Paged` and `Continuous` stay for stored values, only the labels changed).
- "User can at most turn off the calm (empty screen)": Glance off means home shows the first page (paginated) or the scroll; the Home button returns to Glance when it is on, else to the first page. Turning the switch off while Glance is showing moves to the pages.
- "Calm home screen (Glance)" On/Off is in Ground Control, Home, and in the long-press quick sheet in the place of the removed HOME LANDING selector (tag `quick.glance`).

## Adding pages (the build 54 bug)
- Reachable from Glance: the quick sheet has an "Add page" button (`quick.addpage`) and "Edit home pages"; Ground Control, Home has "Add page", "Edit home pages", the pages mode and the order. Before, "Add a page after this" existed only inside a page's own settings, which Glance never has.
- `HomeDeck.addPage(pages, afterIndex)`: from Glance the page goes after the last one; with no page yet, or only the one untouched blank page a fresh install starts with, that blank page is page 1 (no second blank page is stacked on it). From a page the new page goes right after it.
- The first time, the add-page flow asks once "Scroll or Paginated" (`PagesModeDialog`, tags `addpage.scroll`, `addpage.paginated`); the answer is saved (`pagesModeChosen`) and Ground Control, Home, Pages after Glance changes it. Home then moves to the new page.
- Reaching pages: a swipe forward in the configured order, or a tap on the deck dots. Glance counts as the first stop: Glance draws "deck dots" (`glance.deckdots`, one dot per stop), the paginated page indicator gets a leading Glance dot (`pages.glancedot`) that returns to Glance, and the scroll shows the Glance dot too. A swipe back at the first page (or the scroll's start) returns to Glance when it is on, else it opens the Launchpad as before. Edge drags (Launchpad, Recents) keep priority within the edge zone.

## Scroll mode (Windows 8 start screen look)
- One continuous full-screen surface after Glance that scrolls horizontally with inertia and no page snapping (`ColumnScrollHome`). `ColumnFill` (core-catalog, pure math) fills it column by column: rows = floor(available height / target cell), where the available height is the window minus the top band (status bar, top hotspots) and the bottom band (bottom hotspots, gesture area); the cell is then stretched so the rows fill it exactly; a column fills top to bottom, then the next. Widgets span cells and are packed first-fit in column-major order. Portrait has more rows, landscape fewer and more columns, nothing is hard coded per size.
- The four hotspots (or the user's two, three, one, none: `glanceHotspots`) are the corner layer, drawn over the surface unchanged. The old "Icons across" stepper and the vertical continuous scroll are gone with the old continuous style.

## Paginated mode
- The existing grid pages: each page has its own cell grid, the user adds each page and what goes on it (edit mode, add carousel), page dots, swipe, hotspots maintained. Pages start empty ("apps hidden behind the add drawer").

## Order (`HomeOrder`)
- One abstraction, independent of the app language. The home is laid out in physical coordinates, so each function takes and returns physical x and sides; the four combinations (order x language direction) draw the same.
- Left to right (default): Launchpad (notifications) on the left edge, Recents on the right, pages and columns progress left to right, the scroll starts at the left. Right to left: Launchpad handle, slide-in and shrunken home card on the RIGHT, the Recents panel on the left, columns and cells fill from the right, the scroll starts at the right, paginated order and the swipe directions are mirrored (`reverseLayout`), the Glance hotspots' left and right roles swap (top-left becomes top-right) and the deck dots start at the right.
- This replaces the earlier rule that an RTL app language mirrored the Launchpad: only the order does now. An Arabic user who wants the mirrored home sets Order to Right to left.

## Migration (HomeModelMigration, JVM-tested)
- Every old landing value (Selected, Recents, AllApps, Minimal, Glance, unknown, missing) maps to Glance on. Pages, folders, widgets and items live in the layout, not in the landing, so nothing is touched: a user who had Selected apps keeps those pages as the pages after Glance, and one who had Recent apps or All apps gets Glance and the same pages. The old `homeScroll` Continuous becomes the Scroll mode and counts as a choice made; Paged stays Paginated and asks once on the first add. The keys `homeLanding` and `landingChosen` are deleted on the next save. A stored new value always wins over the old landing. The settings document (export and import) follows the same path, so an old file imports cleanly.

## Not done / device only
- Nothing here is device-confirmed: touch, haptics, the real window insets of the bands and the live feel of inertia need a device. Robolectric drives the real UI for the add-page flow, the swipe and the four order x language combinations.
- The wallpaper model and pickers (stream pagesWallpaper) and the arc drawer are untouched.

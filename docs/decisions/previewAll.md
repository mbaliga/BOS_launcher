# previewAll (wave 57, CFG-50 to CFG-54)

Owner answer to Q1 of `docs/handoff/clackpad_vs_liftoff_settings.md` ("Should the floating Preview also show Radar, the drawer disc, Comms and corners, or stay home-only?"): "Radar, the disc, Comms and corners, everything- whatever is being configured."

## What it is
The floating Preview window of wave 39 (`PreviewWindow`, movable, resizable, minimisable, remembered per orientation, device-local) now shows whatever the open settings page or the row last touched or focused configures. The window, its geometry, keys, actions and storage are unchanged.

| Topic | What the window draws |
| --- | --- |
| Home, Look | the home capture (`captureHome` over `captureWallpaper`), the current page, re-rendered live |
| Glance | the real `GlanceSurface` with the current prefs (clock style, size, colour, date, its edited position) over the fixed backdrop; the exact capture while the home shows Glance; a "<corner> hotspot" row rings that corner |
| Corners | the home capture with the four corners ringed, the selected corner pulsing (a thicker still ring under reduced motion) |
| Gestures | the home capture with a small hint of the gesture: a moving dot (swipe), two dots (pinch), ripples (double tap, long press), a tilting phone (motion gestures); a static arrow under reduced motion |
| Dock | the real `DockRail` placed as `DockLayer` places it, with style, edge, mode and magnification strength, over the wallpaper; the user's first apps as sample tiles when nothing is pinned |
| Radar | the real `SearchScreen` (layout, background, keep moving, web settings) with a two-letter sample query from the real app list and made-up web and Play Store suggestions; a "Sample" badge |
| App drawer | the real open disc (`WheelContent`, inert) with ring segments, shortcuts, labels and wheel groups |
| Side panel | the real Comms column (`LaunchpadContent`) with the user's theme, size, categories and pinned block, fed with anonymised sample notifications; a "Sample" badge |
| None | the window is hidden (System and sound, Hidden apps, Backup, Capabilities, Lab, About, Finish setup) |

## Decisions
- **One table** (`PreviewTopics` in feature-grid `PreviewTopic.kt`). Resolution of a row: its own declaration (`item.previewTopic(PreviewTopic.Radar)`, kept in `ItemMeta.previewTopic`), then `PreviewTopics.ROWS` by row id (`rowId`) or English title, then a gesture or hotspot title (adds the gesture or corner), then `PreviewTopics.CATEGORIES` by the page's English title, then, for a page nobody listed, the topic most of its rows map to. A corner row on Corners adds its corner. The root list shows the home.
- **For the six-tab settings (another stream)**: nothing has to change for it to work, since unknown tabs are judged by their rows. To be exact, register each tab once, e.g. `PreviewTopics.category("Surfaces", PreviewTopic.Glance)`, or add the English title to `PreviewTopics.CATEGORIES`; a row that belongs to another surface than its tab calls `.previewTopic(...)` or gets a `PreviewTopics.ROWS` entry. The parity test (`PreviewAllW57AppTest.CFG_54_*`) tells which rows are left over.
- **Who knows what is configured**: `SettingsSpace` holds a `PreviewFocusState` (`LocalPreviewFocus`). The One UI host writes the page in a `SideEffect` and every row reports a touch (seen in the initial pass, never consumed) or keyboard focus (`Modifier.previewRow`). Only the window reads it, so the settings page never recomposes for the preview (tested with a recomposition counter while the gesture hint plays).
- **Sandboxes**: a non-home surface is composed off-screen at the home's own size (the real screen's dp, insets and density, so the real layouts come out exactly) and drawn scaled into the window by one layer transform (`PreviewSandbox`); rasterisation happens at the window's size. Rendering with a reduced density was rejected: window insets come in real pixels and would blow up. In the sandbox (`LocalPreviewSandbox` in core-design) Radar and Comms take no focus, raise or lower no keyboard, do not take a "Groups and rules" request meant for the real panel and do not register the drag-start hook; the disc is composed with `interactive = false`.
- **Cost**: the sandboxed renderers read the prefs through `rememberCoalesced` (at most one change per 60 ms while a slider drags, the latest wins, an equal value does nothing); the picture is not composed while minimised (as in wave 39); the home capture is a layer redraw, as before. No new data stream: the renderers use the app list, the dock state and the Comms settings the app already holds; Radar has no hub, no contacts, no files and a fake suggestion source that never opens a connection.
- **Privacy (the decision asked for in the brief)**: the side panel preview always shows anonymised sample rows, never the user's notifications, even though Comms is visible to the user on this device: Settings is screenshotted and shared (bug reports, the owner's own review), and the window's job is to show the look, not the content. Radar shows the user's own app names (not private) and made-up web rows.
- **Caption and screen reader**: the window bar's title is the caption ("Radar"; the home keeps "Preview"), so nothing covers the picture. The picture is one node, `clearAndSetSemantics`, read as "Preview of Radar, Orbit layout" (the home keeps "Live preview of your home").
- **Touches**: unchanged from wave 39: the window consumes every touch; a one-finger touch on the picture does nothing, two fingers resize.

## Not matched
> Wave 58 ([studioPreview.md](studioPreview.md)): the three items below are now drawn (sample bulge, Widgets sample cards, the Glance's own wallpaper on a page).

- Glance off the Glance page draws the current backdrop layer (the Pages wallpaper when it differs) under the Glance surface, not the Glance wallpaper itself, so a second live wallpaper never runs.
- The dock's magnification is not drawn as a sample bulge (the rail only magnifies under a pointer; adding a sample pointer would change `DockRail`, which another stream works on); its strength is in the description.
- The hub's Widgets section is not in the side panel preview (the hub state reads the real history).

## Undo
Drop `PreviewTopic.kt`, `PreviewStage.kt`, `PreviewSurfacesApp.kt`, `CommsPreview.kt`, `DockPreviewStage.kt`, `PreviewSandboxLocal.kt`, the `previewTopic` field of `ItemMeta`, the `SideEffect` and the `Box(Modifier.previewRow(item))` in `SettingsOneUi.kt`, the sandbox guards in `SearchOverlay.kt` and `Launchpad.kt`, `WheelPreviewContent`, and pass nothing but the capture to `SettingsPreviewDock`.

## Superseded in part by wave 59 (glanceTop)

The Hotspots presets (Four, Two (bottom), One, None), the four per-corner hotspot switches and the Home sheet's Hotspots cards are gone: one Hotspots editor (a live miniature of the home; a tap turns a corner on or off, a long press or Edit configures it) is the first block of Settings > Home > Corners and opens from the Home sheet's Hotspots tile. See `docs/decisions/glanceTop.md`.

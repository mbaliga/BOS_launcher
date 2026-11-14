# Home organisation: status (stream "home")

Status vocabulary: built = compiles and covered by JVM unit tests; partial = some of it is built or wired; not started; unmet. Nothing here is device-confirmed.

New module: `core-catalog` (pure models and tests, plus Android catalog classes: `AppCatalog`, `IconPackLoader`, `RecentFilesSource`, `WallpaperBridge`, stores). Surface contract and frame in `feature-grid` (`HomeSurfaceContract.kt`, `HomeFrame.kt`).

| ID | Status | Note |
| --- | --- | --- |
| HOM-01 | partial | `HomeSurfaceContract` (plan, focus targets, item actions) with Grid, List, XMB and DSi adapters; tests show every surface accounts for the same items, focus reaches every item, actions give the same state on every surface. The existing composables are not yet driven by the plan objects |
| HOM-02 | partial | All five item kinds plus dock modelled, edited and persisted (`HomeState`, `HomeEdit`, `HomeStateCodec`, `HomeStateStore`); tested. GridHome and HomeScreen do not yet render or store shortcut, folder, widget-tile or link keys, so none can be placed from the UI yet |
| HOM-03 | partial | Done: no dark tile behind legacy icons (plate derived from the icon's own colours, wired in `AppIcon`), icon-pack `appfilter.xml` parsing and loader, precedence rules, override codec and store, themed-icon colours (all tested). Not wired: icon-pack picker UI, per-item override UI, monochrome (themed) layer rasterisation in `AppIcon` |
| HOM-04 | partial | Folder model, create by drop or from list, rename, reorder, take out, dissolve, search by name: pure and tested. `FolderTile` and `FolderSheet` (tap, click, Enter) are built but not placed into GridHome, and drag-onto-item is not hooked to `dropOnto` |
| HOM-05 | partial | Wired into the drawer: type-to-filter bar, personal and work tabs, tag chips, tags editor from the app menu, hidden apps still excluded. Alphabetical index is the existing rail. "Show hidden apps" view is not built. Rules tested in `Drawer.kt` tests |
| HOM-06 | partial | Profile rules, tabs, work pause or resume chip (`requestQuietModeEnabled`) and private-space gate (API 35+, `private_space` capability, unlocked only) built and tested. Work badge relies on the system badge in `getBadgedIcon`. Before Android 15 any secondary profile is treated as work. Not device-checked |
| HOM-07 | partial | Searchable picker wired into `WidgetPicker`; resize by handles and Shift+arrows built as `WidgetResizeFrame` (not placed into a screen); span maths and clamping tested; restore plan (keep, rebind, placeholder) tested but not called at startup; reconfigure and remove are the existing flows |
| HOM-08 | partial | Shared tile layout (`TilePacker`) and widget band for List, XMB, DSi (`WidgetBand`) built, tested for no overlap at several window sizes; not yet used by the live composables |
| HOM-09 | partial | Badges (dot with optional count) on grid app icons from `NotificationProvider`; notification preview list in the app menu (long-press or right-click when the menu opens). Work-profile badges not shown (the feed carries no profile). Settings toggle not added (`BadgeSettings` exists without UI) |
| HOM-10 | partial | `PackageReconcile` (diff, prune from pages, dock, folders; coalescer under 1 s) tested; `LauncherApps.Callback` now also reacts to suspend and unsuspend and the flow is conflated. Archived state is not read separately (the system keeps listing the app). The live home still uses its existing refresh path |
| HOM-11 | partial | Size classes, adaptive grid dimensions, per-size-class arrangements with nearest-class seeding and a persisted store: pure and tested. GridHome still derives its grid from window metrics as before and does not yet load per-class arrangements |
| HOM-12 | partial | `UndoStack` and `EditSession` (last 20 changes, redo) and the pure move, resize, file-into-folder, remove operations are tested. There is no explicit edit-mode screen yet |
| HOM-13 | partial | Model and maths only (snap guides, widget no-overlap, clamp); no desktop surface exists to host it |
| HOM-14 | partial | `WidgetStack` (swipe, wheel, keys, add, remove) tested; no stack UI |
| HOM-15 | partial | `RecentFilesModel` and `RecentFilesSource` (granted folder trees only) built; model tested; no home panel UI |
| HOM-16 | built | System-wallpaper background already existed; added scroll-offset forwarding from the pager and an accent derived from the wallpaper colours (icon accent); maths tested. Not device-checked |
| HOM-19 | built | `HomeFrame` extracted and used by GridHome and HomeScreen; tests for equal band in both orientations, same app area rotated, band strips never overlapping the app area |
| HOM-20 | built | `ClockBandFit` (drop weather line first, then scale the clock, never drop the dots) applied to the bottom clock in `StripHost`; tests over short sides 320 to 1200 dp. The weather line is dropped rather than scaled separately |

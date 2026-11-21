# Wave 31 filesViews: the Files tab as a list or grid of real previews

Nothing was run on a device. Verified by JVM and Robolectric tests with a fake thumbnail loader, and by reading the rendered screenshots (`app/build/screenshots/files-w31-{list,grid}-{dark,light}.png`). The thumbnails in those pictures are generated placeholder bitmaps from the fake loader (flat colour with two shapes), not real media, so they show the layout, tiles, badges and fade targets, not what the platform providers return. The real panel is always on the dark panel surface; the light pictures only prove the cells read on a light ground. The preview dialog is a separate window and is not in the captures; its content is asserted through the semantics tree.

| ID | Status | Note |
|---|---|---|
| HOM-15 | built (device untested) | Toggle (48 dp, action label, state description, persisted, default List, restored on next open): `FilesViewsW31Test.HOM_15_w31_the*`. List rows (48 dp preview, middle-ellipsised name keeping the extension, age, size, kind) and grid cells (adaptive, 3 columns at 411 dp, 2-line name, age and size): `..._listRows...`, `..._gridCells...`. Tile per kind, video duration badge, failure fallback, placeholder to loaded, cache reuse, cancellation of scrolled-away loads, 2000 files stay lazy, font scale 2 and RTL: same class. Quick Look preview (long-press, details, Open, Close, a11y long-click label "Preview"): same class. Pure model, formatting and LRU: `FilesPreviewModelW31Test`. |

## Changed existing tests
- `W4MotionAuditTest`: `FilesUi.kt` added to the gated-animation list (its one `animateFloatAsState` goes through `rememberMotionPrefs().gate`).

## Device only / not done
- Whether the real providers return thumbnails for each kind (ExternalStorage and Downloads providers, third-party document providers), PDF first-page rendering on large or encrypted PDFs, APK icon through `/proc/self/fd` on this OS version, video frame and duration, album art: all unverified on a device. Anything that fails shows the type tile.
- The swipe-down dismissal of the preview is implemented but only Close, scrim tap and Back-less dismissal paths are covered by tests; the drag gesture itself is not tested.
- No Back-key test for the preview dialog (platform dismissal handled by `Dialog`).
- The "62 days ago" age text is still the existing English-only `RecentFilesModel.age`.
- Sort and filter, folder browsing into subfolders and multi-select are not part of this request.

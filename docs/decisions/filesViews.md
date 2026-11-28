# Files tab: list and grid of previews (wave 31, HOM-15)

Owner request: a button to switch the Files tab between grid and list, with rich, live thumbnails the way the iOS Files, Finder and Preview apps show them; clarified as real content previews plus a Quick Look-style large preview.

> Wave 53 (filesCollections.md) replaced the Folders rows, the "Choose a folder" line, the Recent files heading and the 10/20/50 files-per-folder chips with a source switcher and a 2000 file cap; the toggle now sits beside the sort and group button. Where this note says "Folders header row" or "direct children", read the wave 53 note. The preview, thumbnail and Quick Look decisions stand.

## Decisions
- **One toggle, one pref.** `HomePanelPrefs.filesView` (`panel.files.view` in `home_state`, read through TolerantPrefs, so it is already classified and exported with the file). Default List. It applies to the Folders results and the Recent files together. The 48 dp button sits in the "Folders" header row, shows the layout it switches to, and announces the current one (state description) and its action (content description).
- **Data source unchanged.** The tab lists documents under the folder trees the user granted (SAF document URIs; no paths, no new permission). `RecentFile` gained `sizeBytes` (read from `COLUMN_SIZE`, -1 when unknown). Because there are no paths, previews go through the URI: `ContentResolver.loadThumbnail` (the same call the Files home tile has used since wave 5) for images, video and audio; `MediaMetadataRetriever` for a video frame, its duration and embedded album art when the provider has no thumbnail; `ImageDecoder` as the image fallback; `PdfRenderer` on the document's file descriptor for a PDF's first page (white sheet, thin border, soft shadow); `getPackageArchiveInfo` on the descriptor's `/proc/self/fd` path for an APK's own icon. Documents, text, archives and unknown files are never loaded: they get the coloured type tile with the extension.
- **Loading.** Off the main thread (IO pool, at most three decodes at once), requested at the cell size rounded to 32 px, cached in a 24 MB weighted LRU (`WeightedLruCache`, a failure is cached too as a 64 byte entry), cancelled with the cell (the platform call is also told to stop through its `CancellationSignal`). A neutral tile shows while loading, the preview fades in over 180 ms (instant under reduced motion; the fade reads `rememberMotionPrefs().gate`), any failure or exception falls back to the type tile.
- **Names.** List rows keep the extension whole (the stem ellipsises, the extension is its own text). Grid cells use two lines.
- **Quick Look.** A long-press on a file (and the "Preview" long-click action for accessibility services) opens a centred dialog: large preview, name, kind, size, date and an Open button; Close, Back, a tap outside or a swipe down dismisses it. It shows only data the row already has.
- **Seam.** `FileThumbs` is an interface provided through `LocalFileThumbs`; tests use fakes, so no real media is read in tests.

## Undo
Remove the toggle and call the old `Line` rows again; `panel.files.view` is then an unused key (add it to `RetiredPrefKeys`).

# Wave 53: filesCollections (HOM-15)

Nothing was run on a device. Verified by JVM unit tests and Robolectric UI tests with a fake file index and fake thumbnails, and by reading the generated screenshots (`app/build/screenshots/files-w53-*.png`: list grouped by date, grid grouped by type, a collection grouped by size, RTL grid grouped by folder, the empty state, the sort and group sheet, the chooser, the editor (new and editing) and the chooser in RTL). The sheet, chooser and editor are drawn on the activity for those pictures (their real windows are dialogs and are not captured). Real folder grants, the system folder picker, the Radar index scan on real storage, real thumbnails and the media listing are device-only and unverified.

| ID | Status | Note |
|---|---|---|
| HOM-15 | built (device untested) | Source switcher (All, collections, folders) with a fixed Add tile; full-page chooser with nine ready-made collections as cards (glyph and example), toggle, move up and down, remove; New collection and Edit pages (types, extensions, name contains, folders, age, size at least and at most); sort (Name, Date modified, Date added when known, Size, Type; both directions) and group (None, Date, Type, Folder, Size band) on a sheet of cards; sticky headers with counts in list and grid; per-source memory with a global default; 2000 file cap with a note; empty states that say how to add a folder or edit a collection; storage in `home_state`. |

## Tests
- `FilesCollectionsW53Test` (core-catalog, pure): rule matcher rows, built-ins, extensions, saved-collection operations and codec (awkward text, damaged values), date buckets with an injected clock and zones (week start, DST, future dates, Auckland and Los Angeles), size bands, name sort (natural, German and Swedish locales, Unicode, empty names, composed and decomposed), date, size, type and added sorts, grouping by each key, unique section ids, the 2000 cap, 20,000 files arranged within a time bound, which files each source shows, index row mapping (seconds to milliseconds).
- `FilesCollectionPrefsW53Test` (Robolectric): defaults, persistence, per-source and default options, set for all, Large files default, bounded memory, damaged values.
- `FilesCollectionsW53UiTest` (Robolectric, 41 tests): switcher contents and selection, filtering per collection and folder, radio semantics and 48 dp, last source, vanished source, reindex update, sheet cards and states, Done box above plain text, each sort order, group sections and counts, heading semantics, sticky header (list) and pinned header (grid), state description, Date added visibility, cap note, per-source memory, set for all and reset, Large files default, chooser cards and examples, toggling, reordering, removing (with confirmation), folder list and Add a folder, editor validation, saving types, extension, age, size, folder rules and filtering by them, editing and deleting, Cancel and Back, empty states (no folder, no match, empty folder), focus-node ids, font scale 2 with RTL, landscape.
- `FilesCollectionsW53ShotsTest`: the pictures above.
- `FilesCollectionsBackupW53Test` (core-data): `home_state` is exported and not excluded; the four keys round trip through an export and import.
- `FilesCollectionsW53AuditTest` (core-quality): no permission request or provider query in the new code, IO and default pools, the cap, TolerantPrefs on `home_state`, focus nodes, the old rows removed.

## Changed or removed existing tests
- `FilesViewsW31Test`: `show()` builds the new content (chips, arranged files); the panel tests use a fake data source; `HOM_15_w31_theHeaderKeepsFoldersAndChooseAFolder` replaced by `..._withNoFolderTheTabSaysHowToAddOneAndKeepsTheSwitcher`; `..._aFolderShowsAFolderTileAndKeepsItsPinnedLabel` and `..._gridFoldersAreCellsToo` deleted (folder rows no longer exist; folders are chips, covered by the new tests).
- `HomeInfoW3Test.HOM_15_filesCanBeNarrowedToOnePinnedFolder` deleted with `FilesView.filter` (replaced by `FilesSources.select` and its tests).
- `docs/TEST_COVERAGE.md` regenerated.

## Not matched, decisions to know
- **Date added** is in the code and the sheet but hidden: neither the folder grants nor the file index carry a date added. It appears if a source supplies one.
- **Recents** is "changed in the last 30 days", not "opened": the launcher cannot see when a file was opened without usage access to other apps' files.
- **Downloads** matches a file whose own (immediate) folder is called Download or Downloads, or the granted folder at its top; a file in a subfolder of Download is not matched (the index keeps one folder name, not a path).
- **Media listing**: not requested. If the owner granted media access in system settings, collections include those files; All and folders do not.
- **Backup**: collections and choices are exported with `home_state`. Folder references inside them are device-bound and match nothing on another device.
- No drag-to-reorder: order changes by Move up and Move down buttons (also reachable by keyboard and screen reader).
- The Files per folder setting (HS27) is gone; the stored key still sizes the Files home tile read.
- `SPEC_STATUS.md` was not edited (HOM-15 stays partial; `docs/Spec.md` HOM-15 row extended).

## Device-only checks
Pick a real folder and see All, a folder chip and each collection fill from the index (first open scans; time on a large folder), Recents and Large files on real data, grouping by date across midnight and a week boundary on the device clock, thumbnails in grouped grids, that a revoked folder drops out, backup and restore on a second device (collections come back; folder chips and folder rules need the folders granted again), TalkBack reading of chips, headers, cards and the sheet, keyboard and controller focus through the chips, Add tile, chooser and editor (Enter and Menu), a hardware keyboard in the editor fields, 2000 and more files scrolling smoothly.

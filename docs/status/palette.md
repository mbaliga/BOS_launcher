# Palette engine status (stream: palette)

Status vocabulary: built = compiles and unit-tested; partial; not started; unmet. Nothing here was run on a device.

Engine code: `search-engine/src/main/kotlin/dev/aarso/search/palette/` (pure Kotlin, JVM tests in `search-engine/src/test`). Android adapters: `feature-search/.../palette/`. Hook into the existing UI: `AndroidSearchHub(context, extraProviders)` in core-data plus one `remember` block in `HomeScreen.kt`.

| ID | Status | Note |
| --- | --- | --- |
| PAL-01 | partial | `TypeToSearch.onKey(...)` (feature-search) returns `PaletteEntry.Open(char)` for a printable key and `Open("")` for the rebindable OpenPalette chord; tested. The input stream still has to call it from home key dispatch. |
| PAL-02 | partial | `AppMatcher`/`AppProvider`: label, package, alias, initials, one typo, profiles; JVM bound test (500 apps, avg under 50 ms per keystroke). The Radar UI still uses its own `AppSearchCore`; wiring `AppProvider` into the UI belongs to the Radar stream. |
| PAL-03 | partial | Providers and hub mapping exist and are tested at engine level; nothing in the UI calls `PaletteHitRunner.run` yet, so running a shortcut or a launcher command is not wired end to end. (Review fix: shortcut hits were mapped to the Apps category, which the hub drops; they now surface as Actions.) |
| PAL-04 | built | `SettingsIndex` (40 public `android.settings.*` actions), hidden when `resolveActivity` is null. Handler visibility on Android 11+ depends on package visibility; unverified on device. |
| PAL-05 | partial | Calculator and Units are built and tested, and the hub carries `copyText`; Enter-to-copy needs the UI to call `PaletteHitRunner.run`, which nothing does yet. |
| PAL-06 | partial | Engine row is built and always last, tested; the engine is not yet what the Radar UI renders (it keeps its own web row), so the guarantee holds for the engine only. |
| PAL-07 | not started | Not in this stream (keyboard operation of the UI). |
| PAL-08 | partial | List derivation is built and tested against `ContextMenus`; the Radar UI does not display it yet. |
| PAL-09 | partial | `Ranker` and `UsageStore` are built and tested; nothing in the app calls `onSelected` or `PalettePrefs.saveUsage` yet, so no selection is learned on device until the Radar stream wires it. |
| PAL-10 | partial | Engine provider built and tested (call, message, email, open; one enable row without permission). The Android `ContactSource` lookup is not wired into `AndroidPalette`. |
| PAL-11 | partial | `FileIndex` (incremental `reconcile`, encode/decode), `FileIndexer` (MediaStore and SAF persisted trees), `FileIndexJobService` (periodic, battery and storage not low) and `FileIndexScheduler` (called from HomeScreen). Not persisted via JobScheduler across reboot (needs RECEIVE_BOOT_COMPLETED); the index is on disk and the job is re-armed at launcher start. No UI yet to add a folder (`FileGrants.pickerIntent/persist` are ready). |
| PAL-12 | partial | `PaletteSession` and `StableMerge` built and tested with virtual time; no UI drives the session yet. |
| PAL-13 | partial | `PrefixTable` built and tested; the empty-state list and the editor UI do not exist yet. |
| PAL-14 | built | `PaletteProvider` interface (query, suggestions, cancel, results with actions) and `ProviderRegistry`; test adds a provider with no other change. |
| PAL-15 | partial | Row builder and provider tested; neither is registered in the app or shown. |
| PAL-16 | not started | Not requested. |
| PAL-17 | not started | C priority; the documented contract is read-only content provider (PAL-22). No permission-guarded registration API. |
| PAL-21 | built | Radar and the engine fetch nothing. The "Web Suggestions" header in `SearchLayouts.kt` only labels the Web category rows (the hand-off row and bang hints), which are built locally from the typed text; no suggestion request exists anywhere in feature-grid or feature-search. No change needed. |
| PAL-22 | built | Existing `DeepSearch` implements `docs/SEARCH_PROVIDER.md`; added contract tests and `SearchMerge.categories`, which omits empty categories when Fylz and Fotoz are absent. |

## Hooks other streams need

- Input stream: call `TypeToSearch.onKey(...)` from home key dispatch (PAL-01).
- Radar UI stream: build `AndroidPalette(context).engine()` and a `PaletteSession`; render `PaletteState.results` (each has `actions`, use `PaletteMenus.label`); persist `PrefixTable` through `PalettePrefs`. `SearchHit` gained `copyText` and `extras`; run hits with `PaletteHitRunner.run`.
- Shared files touched: `app/.../HomeScreen.kt` (searchHub construction, 4 lines), `core-data` `SearchHits.kt`/`AndroidSearchHub.kt` (owned). `feature-search/build.gradle.kts` and `search-engine/build.gradle.kts` changed (owned modules).

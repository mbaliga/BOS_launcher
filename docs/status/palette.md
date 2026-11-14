# Palette engine status (stream: palette)

Status vocabulary: built = compiles and unit-tested; partial; not started; unmet. Nothing here was run on a device.

Engine code: `search-engine/src/main/kotlin/dev/aarso/search/palette/` (pure Kotlin, JVM tests in `search-engine/src/test`). Android adapters: `feature-search/.../palette/`. Hook into the existing UI: `AndroidSearchHub(context, extraProviders)` in core-data plus one `remember` block in `HomeScreen.kt`.

| ID | Status | Note |
| --- | --- | --- |
| PAL-01 | partial | `TypeToSearch.onKey(...)` (feature-search) returns `PaletteEntry.Open(char)` for a printable key and `Open("")` for the rebindable OpenPalette chord; tested. The input stream still has to call it from home key dispatch. |
| PAL-02 | partial | `AppMatcher`/`AppProvider`: label, package, alias, initials, one typo, profiles; JVM bound test (500 apps, avg under 50 ms per keystroke). The Radar UI still uses its own `AppSearchCore`; wiring `AppProvider` into the UI belongs to the Radar stream. |
| PAL-03 | built | `ShortcutProvider` (static and dynamic, under their app; `ShortcutCache` reads LauncherApps, needs default-home role) and `LauncherActionProvider` (open settings, edit home, switch workspace by name). Hub surfaces them as Actions hits. Dispatch of launcher commands goes through `PaletteHitRunner.Outcome.Command`; host must handle it. |
| PAL-04 | built | `SettingsIndex` (40 public `android.settings.*` actions), hidden when `resolveActivity` is null. Handler visibility on Android 11+ depends on package visibility; unverified on device. |
| PAL-05 | built | `Calculator` (precedence, parentheses, percent, sqrt, pi) and `Units` (length, mass, temperature, volume, speed, data); `CalculatorProvider` row pinned on top with Copy as primary; hub hit carries `copyText`, run via `PaletteHitRunner`. |
| PAL-06 | built | `WebFallback` is always the last row (`pinnedLast`); only a URL string in the payload. Test scans the whole engine source tree for network classes. Note: the app still declares INTERNET (weather), see NFR-16. |
| PAL-07 | not started | Not in this stream (keyboard operation of the UI). |
| PAL-08 | built | Engine `ResultActionId` mirrors core-input `MenuAction`; `PaletteMenus.appActions(MenuContext)` derives the list from `ContextMenus`, tested equal for long-press, right-click and palette. UI display is the Radar stream. |
| PAL-09 | built | `Ranker` + `UsageStore`: match blended with frequency and recency, boost capped below prefix-vs-substring gap, persisted as app-private string (`PalettePrefs`), `reset`. |
| PAL-10 | built | `ContactsProvider`: call, message, email, open; without permission exactly one enable row, only in the Contacts scope. Android `ContactSource` lookup lambda is not wired in `AndroidPalette` (the existing Radar contact path stays). |
| PAL-11 | partial | `FileIndex` (incremental `reconcile`, encode/decode), `FileIndexer` (MediaStore and SAF persisted trees), `FileIndexJobService` (periodic, battery and storage not low) and `FileIndexScheduler` (called from HomeScreen). Not persisted via JobScheduler across reboot (needs RECEIVE_BOOT_COMPLETED); the index is on disk and the job is re-armed at launcher start. No UI yet to add a folder (`FileGrants.pickerIntent/persist` are ready). |
| PAL-12 | built | `PaletteSession` + `StableMerge`: slow providers stream, per-provider timeout, selection prefix never reordered, close cancels, reopen with same query restores and resumes only unfinished providers. Tested with virtual time. |
| PAL-13 | built | `PrefixTable` (parse to scope plus rest, set, rename, remove, reset, serialize/deserialize, validation). Defaults a, f, s, c, w, `>`. Engine only; the empty-state list and editor UI are the Radar stream (`entries()` feeds them). |
| PAL-14 | built | `PaletteProvider` interface (query, suggestions, cancel, results with actions) and `ProviderRegistry`; test adds a provider with no other change. |
| PAL-15 | built | `StoreLookupProvider`/`WebFallback.storeRow` (market and web URLs). Not in the hub adapter list yet. |
| PAL-16 | not started | Not requested. |
| PAL-17 | not started | C priority; the documented contract is read-only content provider (PAL-22). No permission-guarded registration API. |
| PAL-21 | built | Radar and the engine fetch nothing. The "Web Suggestions" header in `SearchLayouts.kt` only labels the Web category rows (the hand-off row and bang hints), which are built locally from the typed text; no suggestion request exists anywhere in feature-grid or feature-search. No change needed. |
| PAL-22 | built | Existing `DeepSearch` implements `docs/SEARCH_PROVIDER.md`; added contract tests and `SearchMerge.categories`, which omits empty categories when Fylz and Fotoz are absent. |

## Hooks other streams need

- Input stream: call `TypeToSearch.onKey(...)` from home key dispatch (PAL-01).
- Radar UI stream: build `AndroidPalette(context).engine()` and a `PaletteSession`; render `PaletteState.results` (each has `actions`, use `PaletteMenus.label`); persist `PrefixTable` through `PalettePrefs`. `SearchHit` gained `copyText` and `extras`; run hits with `PaletteHitRunner.run`.
- Shared files touched: `app/.../HomeScreen.kt` (searchHub construction, 4 lines), `core-data` `SearchHits.kt`/`AndroidSearchHub.kt` (owned). `feature-search/build.gradle.kts` and `search-engine/build.gradle.kts` changed (owned modules).

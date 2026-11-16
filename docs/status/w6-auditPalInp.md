# Wave 6 audit: Palette (Radar) and input

Auditor pass over PAL-01 to PAL-23 and INP-01 to INP-20 against `docs/Spec.md`, `docs/SPEC_STATUS.md`, code and tests. Nothing here was run on a device. "Verified" means read in code, traced to a screen or registry, and matched to a test that would fail if it broke.

## Fixed in this pass (each with a test that fails without the fix)

| Area | Defect | Fix | Test |
| --- | --- | --- | --- |
| INP-16 | The hot-corner layer sits on the root above every surface, so with Radar, the drawer, Ground Control or a menu open the pointer in a corner still revealed the bubble and a click ran the corner action through the overlay. | The layer ignores pointer events while `LauncherInput.overlayOpen`. | `W6HotCornerLayerUiTest` (the layer itself, not only the tracker; touch never activates, click before dwell does not, click after dwell does, overlay blocks) |
| PAL-05 | `nm` meant nautical mile (nanometre is the usual reading); a decimal comma ("1,5 * 2") gave no answer in comma-decimal locales. | `nm` is the nanometre, `nmi` the nautical mile; a lone comma between digits is a decimal mark when no point and no three-digit group is present. | `W6CalculatorAuditTest` |
| PAL-09 | "Forget what Radar learned" cleared app picks only; the people habits (last channel and time per contact, used for "recent people") and the remembered results per query survived. | `RadarLearning.forget` clears all three; the Ground Control row calls it. | `W6RadarLearningTest` |
| PAL-11 | A revoked media permission left the "media" entries in the index (only revoked folders were dropped), so those files stayed searchable while a folder grant existed. | `FileIndexer.staleSources` drops "media" when no media permission is held. | `W6FileIndexAuditTest` |
| PAL-17 / PAL-22 | The deep-search source was trusted by authority name alone: another app could register `<package>.search` first. Rows could carry any uri scheme. | The provider's owning package must equal the approved package; row and thumbnail uris must be content, file, http or https (as `docs/SEARCH_PROVIDER.md` allows). | `W6DeepSearchAuditTest` |
| INP-10 | `ShortcutRegistry.import` accepted a file that put two commands on one chord. | A clashing later part is dropped; swaps still import. | `W6ImportAuditTest` |
| INP-04 | The spec constraint "the shortcut sheet says launcher shortcuts apply only while a launcher surface has focus" was missing (only the Meta note existed). | `KeyLabels.SCOPE_NOTE` shown above the Meta note; translated in eight languages (`L10nW6auditPalInp.kt`). | `W6SheetScopeUiTest` |
| PAL-20 / localisation | The preview dialog buttons "OPEN" and "OPTIONS" and the puck's "Search apps" description were English literals. | Now `L10n.tr`. | `W6RadarPreviewUiTest` (renders the real Radar in Flat, Orbit and Filmstrip, opens the preview by long-press and by right-click: six tests, which previously did not exist for PAL-20 beyond the text-scale maths) |
| PAL-01 (accessibility) | The always-open keyboard's symbol keys had no spoken names and the placeholder was an English literal. | Keys have a Button role and names; placeholder uses the existing "Search apps" string. | `W6SearchKeyboardAuditTest` |

## Dead code found (left in place; deleting would remove tests, which is not allowed)

- `PaletteSession` (search-engine): the screen does not use it; streaming is done by the hub collector in `SearchScreen`. Tested only.
- `AndroidPalette.engine()` and the engine pieces only it builds (`PaletteEngine`, `ProviderRegistry` use, `ContactsProvider`, `StoreLookupProvider`): no screen calls them; the hub adapters (`hubProviders`) are the live path. Tested only.
- `PaletteMenus` (feature-search, action-id mapping and labels): only its own test calls it; Radar draws `ContextMenus.forApp` rows directly.
- `GlobalShortcutProvider`, `NoGlobalShortcuts`, `GlobalShortcuts` (INP-14): by design a seam with nothing wired.
- `ScopeStack`, `SavedSearchDraft`, `CorpusUpdate`, `DocumentAdapter`, `FieldValue` (search-engine query-language model): no caller outside their own file and tests.

## Table

| ID | Status | Note |
| --- | --- | --- |
| PAL-01 | built | Verified: `LauncherInput.onKeyEvent` reaches `typeHook` only when no surface is open and no layer is active; `HomeScreen` installs `TypeToSearch.onKey`; swipe, pinch and the visible search corner use `internal(CornerActionId.Search)`; Ctrl+/ is a registry command. Tests `PAL_01_*` cover the hook, rebinding, fields and open surfaces. Added spoken names for the always-open keyboard keys. Device entry through a real IME not run. |
| PAL-02 | built | Verified `AppSearch.rank` runs `AppMatcher` (label, alias, package, initials, one typo) over all profiles (`AndroidApps.records` iterates `LauncherApps.profiles`); 500-app budget test exists; operator queries keep the old engine. Timing on a phone not measured. |
| PAL-03 | partial | Unchanged: nested shortcuts, workspace and command actions are covered by tests; `LauncherApps.getShortcuts` needs the default-home role on a device, so what a real phone lists is unverified. |
| PAL-04 | built | Verified every `android.settings.*` action string against the public constants; entries without a resolving activity are hidden (`resolves`); a failed start is swallowed silently by `runCatching` in the host (no toast). Handler visibility on Android 11+ unverified on a device. |
| PAL-05 | built | Verified answer-first ordering and copy through `PaletteHitRunner`. Fixed: nanometre vs nautical mile, decimal comma. Output always uses a point (copied text is locale independent by choice). |
| PAL-06 | built | Verified web row is last, engine from Ground Control, no network client in Radar or engine (tests assert the package has none). Absent only when the user turns web results off. |
| PAL-07 | built | Verified `onRadarKey` handler and the three-layout model test; focus return after the preview closes. Not run on a hardware keyboard. |
| PAL-08 | partial | Unchanged: chooser flow is driven in `RadarMenuChooserTest`; "open adjacent" needs the device spike and "other display" needs a second display. |
| PAL-09 | built | Fixed the reset gap (people habits and remembered results now cleared). Remaining notes: people are ordered by habit (recency of last channel), not by the blended ranker; the launch history the home keeps and which seeds app ranking is the home's own recents and is not cleared by this row. |
| PAL-10 | built | Verified enable-row-only path without permission and channel buttons with it. |
| PAL-11 | built | Fixed stale media entries after revocation. Index file is app-private and atomically replaced; the periodic job is rescheduled at launcher start (not persisted across reboot by itself, so the index survives reboot but background reconciliation resumes once the launcher starts). Concurrent save from the job and the app is last-writer-wins (benign). |
| PAL-12 | built | Verified collector cancel on close, stable merge, query cache restore. `PaletteSession` is dead (see above) but the acceptance is met by the hub path. |
| PAL-13 | built | Verified prefix mapping persisted and shown in the empty state; clash refused. |
| PAL-14 | built | `PaletteProvider` and `ProviderRegistry` are live through `AndroidPalette.hubProviders()`; adding a provider there needs no screen change (test). Engine-only providers noted under dead code. |
| PAL-15 | built | Store row sits before the browser hand-off; test exists. |
| PAL-16 | not started | No on-device model is bundled and nothing may be downloaded. Unchanged. |
| PAL-17 | built | Hardened: owner-package check and uri scheme allow-list. Manifest permission, `queries` entry, approval pane verified. Approved but denied sources stay "unavailable" until the process restarts (known, minor). |
| PAL-18 | built | Verified keyboard and pointer default to Flat unless pinned; tests exist. |
| PAL-19 | built | Verified one mapping drives wheel and prefixes; Left/Right wheel keys tested. |
| PAL-20 | built | Previously untested beyond the maths: now six rendered tests (long-press and right-click in all three layouts). Fixed English literals in the preview. Pinch and Ctrl+scroll covered by `RadarScaleTest` maths and the gesture modifier at the root; caption scaling in Orbit and Filmstrip not asserted by pixel. |
| PAL-21 | built | Verified no network classes in Radar or engine (tests assert); the web row only builds a URL. |
| PAL-22 | built | Contract mapping verified; hardened as PAL-17. |
| PAL-23 | partial | Unchanged: capture and snippet tooling exists; tuning on a phone and committing values is device-only. |
| INP-01 | partial | Unchanged: graph, layers and bridges are tested on JVM; reachability of every element by a person on every surface not exercised on a device. |
| INP-02 | partial | Unchanged: hover and secondary click tested through seams; the system pointer drawing is not observable here. |
| INP-03 | built | Verified `ContextMenus.forApp` feeds the dialog and all three triggers call one callback; tests assert equality. |
| INP-04 | built | Verified registry, persistence, onStop save, `onProvideKeyboardShortcuts`. Added the missing "only while a launcher surface has focus" sentence to the sheet. |
| INP-05 | partial | Unchanged: routing and focus-dock logic tested; every shortcut on every surface not run on a device. |
| INP-06 | built | Verified key, pointer and touch sensing feed one tracker and only affordances change. A system Back key from a navigation button also flips the mode to keyboard (cosmetic). |
| INP-07 | built | Verified the audit test names real tests (existence check) and the touch paths. |
| INP-08 | built | Verified edge-band suppression and the assignable gestures. |
| INP-09 | built | Verified `KeyboardMove` edges (short last row, page crossings) and the live Shift+arrow hook; tests exist. |
| INP-10 | built | Fixed import clash. Rebind UI conflict naming and Meta refusal verified. |
| INP-11 | partial | Unchanged: platform drop delivery (SPK-05) is device-only. |
| INP-12 | built | Verified per-orientation pref and focus request in Radar and drawer; the soft keyboard actually rising is an IME behaviour not checked. |
| INP-13 | built | Verified press-only start, on-device-only recogniser, hidden otherwise, stop on release or dispose; the permission prompt precedes any start. No device run. |
| INP-14 | partial | Unchanged: needs the elevated flavour; seam only (dead by design). |
| INP-15 | built | Verified kinds and the stored form; styles share one assignment source. |
| INP-16 | built | Fixed the overlay click-through and added the first test of the real layer. Hover delivery to the activity at the corners remains a device question. |
| INP-17 | built | Verified corner nodes and default shortcuts; keyboard shortcut runs the corner action even with an overlay open (explicit key press, kept). |
| INP-18 | built | Verified puck key handler pushes ahead of the registry, the scroll wheel carry and the key contract. |
| INP-19 | built | Verified `PuckLayout` is what `WheelDrawer` draws from; no-overlap and in-window tests. |
| INP-20 | partial | Unchanged: controller keys map for activity and dialog windows with tests; Start and Select are not mapped to anything, so "drive every surface" is limited to D-pad, A, B, X and shoulders; no physical controller run. |

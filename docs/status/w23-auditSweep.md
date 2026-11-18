# Wave 23 stream auditSweep: independent sweep of waves 17 to 22

I did not write this code, so the sweep read it as a stranger would: `git log --stat` of waves 17 to 22, then the touched sources for lifecycle and state, threads, untranslated strings, hard-coded colours, dead code, races and crash paths. Decisions and the three calls that are design choices: `docs/decisions/auditSweep.md`. Nothing here is device-verified: every check is a unit test or Robolectric. `docs/SPEC_STATUS.md` is not edited; the statuses below are what this stream supports.

## Defects found and fixed (file and line are of the fixed tree; each has a test unless marked)

| # | Where | Defect | Test |
| --- | --- | --- | --- |
| 1 | `search-engine/src/test/.../PaletteTest.kt` (the helper `fe`, PAL_11 reconcile test) | The wave 20 cleanup deleted a test helper and a live-code test with the removed providers while two later tests still used the helper: the module's tests did not compile (`:search-engine:test` failed to build since wave 20). Helper and `PAL_11_reconcileIsIncremental` restored. | `PAL_11_reconcileIsIncremental` and the module compiling |
| 2 | `core-data/.../TimelineLog.kt:71,77` (`load`) | A launch recorded before the file read finished, on a log already at its maximum, never reached the file: the rewrite was decided by comparing sizes (equal after trimming), and the read merge ignored the store's own `max`. Now compares content and passes `max`. | `HOM_18_aLaunchRecordedWhileAFullLogLoadsStillReachesTheFile` (failed before) |
| 3 | `crash-recovery/.../CrashRecoveryActivity.kt:595` (`discard`) | Discard on the recovery PREVIEW deleted the real waiting crash report and showed "Report deleted". A preview must be inert (rule stated in the same file). | `NFR_17_discardInAPreviewNeverDeletesTheRealWaitingReport` |
| 4 | `feature-grid/.../VoiceEntry.kt` (`PushToTalkButton`) | A listening microphone (tap-toggled for TalkBack, or a held key) stayed open when the app left the screen (switching app does not close Radar). Now released on pause. | `INP_13_pausingTheScreenStopsAListeningMicrophone` |
| 5 | `core-data/.../NotificationManager.kt` (`NotificationProvider`, `SphereNotificationListener`) | A late `onDestroy` of an old listener instance unbound the new instance and emptied the feed; and when the system dropped the binding (`onListenerDisconnected`) the last notifications stayed on screen. | `STR_05_aLateReleaseByTheOldInstanceKeepsTheNewOneBound` (`BoundSlotTest`) for the first; the disconnect override has no unit test (needs a bound service) |
| 6 | `feature-grid/.../SearchData.kt` (`buildSuggestionGroups`, new `localizeChip`) | Radar refinement chips ("Package contains", "Kind:", "Used:", "Is:", "Launches", "x I used yesterday") were English on every phone, and the "Name contains" chip was removed by comparing with the TRANSLATED words so on a translated phone it showed twice (once English). | `SuggestionChipsW23Test` (three tests) |
| 7 | `core-data/.../ActionRunner.kt`, `MotionGestureService.kt:57`, `HomeScreen.kt:644` | Toasts of the corner and gesture actions (flashlight, do not disturb, ringer, missing app, shortcut) and two notices were English. `ActionRunner` takes a `translate` function (core-data cannot see the string tables). | `ActionToastsW23Test` |
| 8 | `core-design`: `L10nWave2Fill`... | "Previous" (Glance transport) was missing in all eight tables: `TranslationCoverageTest` failed on the tree. Added (`L10nW23auditSweep.kt`). | `TranslationCoverageTest`, `W13TranslationDeepScanTest` |
| 9 | `HomeScreen.kt:1232,1259` (quiet Glance edge handles) | The handle bars were white, invisible on the light theme's light grey Glance home. Now the Glance ink (`GlanceHandleFade.tint`). | `HOM_23_theQuietHandleIsLightOnDarkAndDarkOnLight` |
| 10 | `HomeScreen.kt` (weather observer) | The lifecycle observer captured `weatherWanted` at first composition, so turning weather on later never refreshed on resume. Reads the current value now. | none (a closure over composition state; compiles and is covered by the smoke tours only) |
| 11 | `core-signals/.../Recents.kt` | `UsageRecents.recents` repeated the foreground-event rule of `UsageEventKinds`; now one rule. | existing `STR_14`/`DCK_02` tests |

## Dead code removed (nothing in main code reached it)

* `DockLayer.dockPaletteProviders` (also created a never-cancelled coroutine scope), and the dock-module classes only it used: `PaletteProvider`, `PaletteHit`, `GroupPaletteProvider`, `WorkspacePaletteProvider` (`feature-dock/.../Providers.kt`; `PaletteMatch` stays, the dock search uses it). Tests removed with them: `WSP_05_groupIsAPaletteResultAndRunsFromIt` (tested only the removed class); `WSP_07_nextAndPreviousWrapAndPaletteProviderSwitches` renamed `WSP_07_nextAndPreviousWrap` and its provider lines dropped (the wrap assertions stay).
* `WebFallback.storeRow`, `PaletteStrings.searchStore`, `ResultActionId.SearchStore` and the string "Search the app store for “%s”" in all eight tables (the store row was removed in wave 20 but its strings and action id stayed).
* Unused private composables: `SettingsCategories.ChipRowItem`, `WheelDrawer.GroupingChip` (3 animations; the motion audit allow-list count for `WheelDrawer.kt` is now 2), `SettingsRows.RoundStep`; public `HomeClock` (`GridHome.kt`).
* `PerformanceMonitor.logFrameMetrics`, `AppCatalog.widgetProviders`, `AppRepository.currentUser`, `PaletteEngine.cancelProviders`, `SignalLogic.kindWord` (duplicate of `signalDescription`) and `SignalLogic.settle` (a pure copy of the debounce that nothing called; its test now runs the real `settled` operator on virtual time; `core-signals` gets the coroutines-test test dependency).
* `VoiceInputManager` (a no-op object with one caller) and its call in `HomeScreen`.
* `SearchChrome.QueryDisplay`: the typed-query branch (with a 28 dp clear button) that the wave 22 note said was removed but was still in the code; called only with an empty query. Two parameters gone, one call site in `SearchOverlay.kt` changed.
* Pref `networkDots` (written and read by the store, offered by no screen); added to `RetiredPrefKeys` (marker renamed per wave, see the decision doc) and dropped on import.
* Strings "No matches for “%s”" and "Type to find apps, people and the web. Try kind: or used:today" (no caller) removed from all eight tables.
* `HomeScreen.contactSource`: `if (true) ... else null` reduced to the call.

## Swept and found sound (no change)

Activity recreate: `MainActivity` declares `configChanges` for rotation, size, density, font scale, layout direction, so a rotation or font change is not a recreate; the surfaces that are restored after process death are the owner-decided drawer and Radar only. Receivers and observers in `GridHome`, `Complications`, `GlanceHome`, `IndicatorSource`, `MotionGestureService`, `ReducedMotion`, `ThemingSettings` unregister. Executors are single-thread daemon threads. `TolerantPrefs` wraps every getter; `PendingIntentLauncher.open` never reports success for a refused intent. `!!`, `first()` and `coerceIn(0, size - 1)` sites were each read against an empty list, zero or one page: all guarded (empty lists handled before, `coerceAtLeast(0)` on the bound, or non-empty by construction). Wave 17 to 22 colours that are always dark by design (Radar glass, recovery screen) are not theme bugs.

## Three window sizes

New `TourSizesW23SmokeTest` runs one tour (Launchpad, Recents, Radar with typing, Ground Control, the quick sheet, Back and Esc, a rotation) at 240 x 320 dp, the 280 x 653 dp folded cover and 1280 x 800 dp; all three pass. The existing tours stay at 360 x 720 dp. Not exercised: real touch, a real GPU.

## Per ID

| ID | Status | Note |
| --- | --- | --- |
| HOM-22 | partial (unchanged) | Recents source read in full: `HomeRecents`, `RecentsStateStore`, `TimelineLogStore`. Fixed the timeline load race (2) and the duplicated usage-event rule (11). The earlier partial reason stands. |
| HOM-23 | built | Glance handle bars were invisible on the light theme (9); the weather refresh on resume (10); "Previous" untranslated (8). Tour passes at the three sizes. |
| STR-01 | built | Launchpad edge and waiting indicator re-read; the handle tint fix (9) applies to its Glance handle. Tour at three sizes. |
| STR-14 | built | Recents panel host and the right-edge swipe re-read: no defect beyond (2) and (11). Tour at three sizes. |
| STR-04 | built | Delivery policies untouched; notification listener binding fixed (5). |
| STR-05 | built | `MediaNow` and `PendingIntentLauncher` re-read, no defect; the listener bind race and stale feed on disconnect fixed (5). Device steps unchanged. |
| STR-07 | built | Screenshot cases untouched; nothing in the sweep touches them. |
| PAL-18 | built | Chips localized and the duplicated "Name contains" chip fixed (6); the dead typed-query branch removed; the mic released on pause (4). Orbit layout itself still not asserted by the tour. |
| PAL-02 | built | The matcher untouched. Dead app-store row, action id and string removed; the module's tests compile again (1). |
| CFG-09 | built | Preview Discard no longer deletes a real report (3). Recovery screen words remain English by design (decision doc, residue). |
| NFR-11 | partial (unchanged) | Re-read `OpenSurfaceStore`: once per process, never in safe mode; rotation is not a recreate (`configChanges`). Device items stay open. |
| NFR-12 | partial (unchanged) | No new main-thread I/O found in waves 17 to 22 sources; the pinned first store read is unchanged. `UsageRecents`, the timeline and recents files are all read off the main thread. |
| NFR-19 | partial (unchanged) | Three-size tour added; Back handling audits green. The system Back gesture and predictive animation remain device items. |

## Residue (needs the owner or a device)

* Recovery screen translation (library is English only by design): needs an owner call and a resource table in `:crash-recovery`.
* Merge of the four small battery readers (Glance, Complications, IndicatorSource, SystemState): a refactor across shared files, not done in a parallel wave.
* `LaunchpadWaitingIndicator` reads its description as one node but its count text is still a separate TalkBack stop; confirm on TalkBack.
* `DesktopSurface` and `feature-sphere` carry public helpers with no caller (`rememberSphereState`, `rememberLauncherSurfaceState`, `nearestIndex`, `RowMode`): `feature-sphere` is a legacy module; deleting it is the owner's call.
* Everything the earlier notes list as device-only.

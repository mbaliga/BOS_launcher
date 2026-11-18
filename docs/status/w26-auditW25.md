# Wave 26 status: auditW25 (audit of wave 25)

| ID | Status | Note |
|----|--------|------|
| LST-09 | fixed | The drawer's details line ("category, N launches, last used") and `relativeTime` were English only; now `DrawerOrdering.detailsLine`, translated, singular and plural, "mo ago" added; drawer section titles ("Today", "Earlier", categories) go through L10n. `DrawerDetailsW26Test`. |
| STR-03x | fixed | Toast "The right side is off..." named the category "Home"; it is "Home screen" now (new key in `L10nW26auditW25`, old key removed). |
| PAL-18 | fixed | The wheel's labels were cut at font 2.0 (EVERYTHING became EVERYTHIN) and the lowest label could reach the strip; labels now shrink in steps. `RadarWheelBottomW26UiTest` (12 cases: Orbit idle/typed, Filmstrip, keyboard, 320x480, landscape, Actions and Files selected, font 2.0): 10 failed before the fix. Screenshots read: EVERYTHING fits, nothing in the mic bar. |
| HUB-16 | fixed | A half-made "Delete history" confirmation stayed pending when the options were closed and opened again; now reset. `HubTest.HUB_16_aHalfMade...` |
| HUB-13 | covered | Empty states (no history, no unread, nothing read, filters) and the "notification access off" banner with its grant button had no test; added. |
| HUB-21 | covered | Hub labels from enums and `when` branches exist in all eight languages (`HubStringsW26Test`; the source scan cannot see them). |
| NFR-13 | fixed + extended | Wave 23 sweep extended (`A11ySweepTourTest.NFR_13_wave25Surfaces...`): Launchpad side panel, hub Unread with a filter set, options, delete confirmation, Files, Usage, Timeline, portrait and landscape. Found: Files "Choose a folder" had no role; the Usage and Timeline chips had no role, no selected state and were 31 dp high. Fixed (48 dp, radio role, selected). `HubChip` had no minimum width (a 42 dp "Day" chip shared a hit area): fixed and asserted. The Radar surfaces were already in the wave 23 sweep. |
| UNI-04 | tested | `PackageRemovedW26SmokeTest`: a fake `onPackageRemoved` (Robolectric launcher-apps shadow) removes the app from the drawer, Radar and the home layout; the dock keeps the pin dimmed as "App unavailable" for the HOM-10 grace period (7 days). Not changed: see decisions/auditW25.md. |
| (d) | removed | Dead constants of wave 25: `BlackSpec.PRESSED_ALPHA`, `GlanceNotch.LINE_GAP_DP`, `WheelRowHalfVisible`. No test removed (none referenced them). The old MediaStrip, `mediaControls` pref, LaunchpadHeader/MenuLabel and the old HomePanelsSheet are already gone from main code (searched: only a legacy key in `UpgradeSmokeTest` and a rule-test name remain, both intentional). |

## Flaky tests
`TourBusySmokeTest`, `TourRecreateSmokeTest`, `DockWiringTest`, `DockDialogsW24Test` (23 tests together) run 5 times each way: five runs with nothing of mine running, five with four CPU burners added. 10 of 10 runs green, 0 failures. Not reproducible. The machine was shared with other agents' builds the whole time (load average above 20), and the runs hit "Gradle build daemon has been stopped" several times when another stream stopped the shared daemon; an interrupted run looks like a failure in a log that is only parsed for results. The audit cannot name an unstable construct. Reviewed by reading: no snapshot state is written outside composition in the hub, the menu or the dock code (hub state is mutated from composition callbacks; the history store is a `MutableStateFlow` behind one daemon executor).

## Lifecycle review (read, not device-run)
- Rotation: the activity declares the configuration changes, so nothing recreates. Process death or recreation: the panel, hub view, filters, Radar, drawer and dock menu all start from the home (`remember`, as `leftOpen` itself); consistent, not changed.
- Leaks: `NotificationHistory` has one shared daemon executor for the process; `LaunchpadHubNav` is collected in a `LaunchedEffect` (cancelled with the Launchpad); `ShortcutIcons` is count-bounded (48), misses included.
- Main-thread I/O: history read, append and rewrite run on the executor; `HubPrefs` use TolerantPrefs like the other prefs.

## Not checked / device only
Real two-finger feel, the system uninstall dialog, TalkBack speech and order, RTL layout on a device (the menu motion maths is JVM-tested for RTL; the hub tabs use the standard mirrored row). Light-theme colours: the hub uses the Launchpad theme tokens; its fixed colours (undo bar, scrim, blue button) sit on surfaces that are dark or saturated in both themes. Nothing here is device-confirmed.

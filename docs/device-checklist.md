# Device checklist (scripted manual run)

Spec section 15, "Device" layer: the cases that unit tests cannot cover. Run on every reference device (low, mid-range, large phone, tablet or foldable, plus the external display setup). Mark each line with date and device. Nothing in this repository has been run on a device; a ticked box here is the only device evidence.

Preparation: install the release build, set Liftoff as the home app, add 10 widgets, set a distinct layout (non-default home style, two workspaces if available), note the battery level, enable StrictMode logging with a debug build for the NFR-12 pass (`adb logcat -s StrictMode`).

Record with: `adb logcat -b all -d > run.txt` after each section, and look for `FATAL EXCEPTION`, `ANR in`, and `StrictMode` lines.

## 1. Display hot-plug (EXT, NFR-11)
- [ ] Connect an external display by cable. Within 3 s the launcher surface on the phone is unchanged; a desktop surface is offered or opened on the display (SPK-01 result recorded in docs/capabilities.md).
- [ ] Launch an app to the external display from the palette; it opens there (or the report says the placement was not honoured).
- [ ] Unplug the cable with an app open on the display. Activities move to the phone, the launcher does not crash, the layout is intact.
- [ ] Replug 5 times in a row. No leaked windows (`adb shell dumpsys window displays`), no crash.
- [ ] Connect with the phone locked, then unlock. Same result.

## 2. Profile toggle (work profile, private space)
- [ ] With a work profile: pause it in system settings. Work apps disappear or show as paused; resume brings them back without restarting the launcher.
- [ ] Private space (Android 15+, home role held): lock and unlock it. Its apps appear and disappear; search results and the drawer follow.
- [ ] Layout slots that held a profile app show a placeholder, not a crash, while the profile is off.

## 3. Package changes
- [ ] Install an app from adb (`adb install`): it appears in the drawer and palette within 5 s without restarting.
- [ ] Uninstall it: icon and any home slot update; no ghost entries.
- [ ] Update an installed app in place: icon refreshes.
- [ ] Disable and re-enable an app (`adb shell pm disable-user --user 0 <pkg>`).
- [ ] Update Liftoff itself over the top (`adb install -r`): all settings, layout and workspaces survive.

## 4. Process death and reboot (NFR-11)
- [ ] `adb shell am kill com.bos.sphere` from home, then press Home: layout, settings, widgets, workspaces and hidden apps are intact. Open surfaces (palette, drawer, settings) are closed by design (`stateNotNeeded`, and Home always resets them); if the owner wants the open surface restored too, that is a decision to record against NFR-11.
- [ ] Same with the palette open and text typed, and with the drawer open: the launcher returns to a clean home; nothing durable is lost.
- [ ] Developer options "Don't keep activities" on: navigate through palette, drawer, settings and back. No state lost.
- [ ] Reboot. Liftoff is still the default home app; state intact.
- [ ] Force three crashes in under a minute (debug build) and confirm safe mode appears (no fallback to the stock launcher).

## 5. Rotation and size changes
- [ ] Rotate on home, in the palette, in the drawer, in settings. No relayout glitch, no lost text, no crash.
- [ ] Split-screen and freeform (where supported): resize the window; layout adapts.

## 6. Fold (foldable only)
- [ ] Fold and unfold on home, with the palette open, and with the drawer open. Layout switches without losing state (HOM-01 window-size handling).
- [ ] Tent and tabletop postures leave the surface usable.

## 7. External display and input
- [ ] Keyboard and mouse attached: keyboard shortcuts work on launcher surfaces, hover works, right click opens the context menu (INP).
- [ ] Hover reaches the home activity at the screen corners (INP-16 check).
- [ ] Controller input reaches the launcher (INP-20 check).

## 8. Accessibility (NFR-13, NFR-14, NFR-15)
- [ ] TalkBack on: reach and activate every action on home, drawer, palette, settings, with each home style (XMB, DSi, puck, Orbit expose a linear order).
- [ ] Font size at the largest and Display size at the largest (about 200%): no clipped or overlapping text on home, palette, settings.
- [ ] Remove animations (Settings > Accessibility): surface changes are cross-fades.
- [ ] Bright wallpaper and dark wallpaper: clock and labels stay readable.

## 9. Navigation modes (NFR-19, NFR-20)
- [ ] Three-button navigation: Home, Back, Recents behave as the system intends from every surface.
- [ ] Gesture navigation: swipe-up Home, swipe-up-hold Recents, edge-swipe Back. Back previews (predictive back) animate on Android 14+ where the app supports it. Record any OEM that restricts this in docs/oem-notes.md (SPK-06).
- [ ] Edge-to-edge: no content under the status or navigation bar that should not be there, on notch and punch-hole devices, in both orientations.

## 10. Performance and soak (see section 12 of this file)
- [ ] `tools/perf.sh` meets NFR-01 and NFR-02; macrobenchmark run meets NFR-01 to NFR-05 (benchmark/README.md).
- [ ] `tools/measure_pss.sh` with 10 widgets: 180 MB or less.
- [ ] `tools/measure_battery.sh` overnight: 0.5% per day or less, no wake locks.
- [ ] `tools/soak.sh` 2 hours: no ANR, no crash.

## 11. Pre-production gate (spec section 15)
- [ ] Every M and S requirement passes on all reference devices (docs/TEST_COVERAGE.md for the tests, this file for the device pass).
- [ ] All NFR budgets met in CI on the mid-range device.
- [ ] 14 days as the daily-driver launcher on two devices with no crash on start and no forced fallback to the stock launcher.
- [ ] Build 31 settings migrate to the new store without loss (install build 31, set up, install this build over it, compare).
- [ ] Core flavour contains no hidden-API calls, no accessibility service, no network permission (NFR-16 is open: see docs/decisions/NFR-16.md; `PrivacyAuditTest` and `CompatibilityAuditTest` cover the source side).
- [ ] Capability report is accurate on every reference device (compare each row with reality; copy the report from the Lab page into docs/capabilities.md).

## 12. Scripts at a glance
| Test plan row | Script | Budget |
| --- | --- | --- |
| Performance | `tools/perf.sh`, `benchmark/` | NFR-01 to NFR-05 |
| Memory | `tools/measure_pss.sh` | NFR-06 |
| Battery | `tools/measure_battery.sh` | NFR-07 |
| Install size | `./gradlew :app:checkReleaseApkSize --offline` | NFR-08 |
| Soak | `tools/soak.sh` (nightly, 7200 s) | NFR-09, NFR-10 |
| Coverage by ID | `python3 tools/spec_coverage.py` | spec section 15 |

## 13. NFR pass/fail record (one row per requirement)
Run `tools/device_nfr.sh` on the mid-range reference device for the rows marked script, then write the number in the last column and the date and build. A blank cell means not measured. Nothing in this table has been run.

| ID | Exact step | Pass when | Recorded number (device, build, date) |
| --- | --- | --- | --- |
| NFR-01 | script: `tools/device_nfr.sh` (perf.sh warm loop; macrobenchmark `HomeBenchmarks` warm) | warm home p95 at most 150 ms | |
| NFR-02 | script: same run; `HomeBenchmarks` cold start with the `Liftoff.homeInteractive` metric | cold start p95 at most 700 ms | |
| NFR-03 | script: `PaletteBenchmarks` open (`Liftoff.paletteOpen`, `Liftoff.paletteFirstResults`) | p95 at most 100 ms | |
| NFR-04 | script: `PaletteBenchmarks` keystroke (`Liftoff.paletteKeystroke`); the span covers app results only, so also eyeball provider rows | p95 at most 50 ms | |
| NFR-05 | script: perf.sh gfxinfo and `FrameTimingMetric`; scroll home pages, drawer, open the palette, open Ground Control | janky frames at most 1% | |
| NFR-06 | script: `tools/measure_pss.sh` with 10 widgets on home | median PSS at most 180 MB | |
| NFR-07 | `tools/measure_battery.sh 8` overnight, idle, unplugged; then `adb shell dumpsys batterystats` for wake locks | at most 0.5% per day and no held wake lock from Liftoff | |
| NFR-09 | beta cohort: Ground Control > Backup > Crash reports shows the local figure per device; sum across the cohort | crash-free at least 99.8% over the cohort | |
| NFR-10 | `tools/soak.sh` 7200 s (`tools/device_nfr.sh --soak`) | no ANR and no crash in logcat | |
| NFR-11 | section 4 of this file (kill, reboot, package update, profile toggle, display hot-plug) | every step in section 4 leaves state intact | |
| NFR-12 | debug build, `adb logcat -s StrictMode` while doing section 8 and one cold start | no StrictMode disk or network violation from Liftoff frames on the main thread other than the first prefs read in `MainActivity.onCreate` (known, recorded) | |
| NFR-13 | TalkBack on, every home style (Grid, List, Psp, Dsi), Continuous and Paged, Orbit, Filmstrip and Flat search; swipe through the whole screen | each item announces a name, order follows the intended order (Orbit: best match first), every action reachable | |
| NFR-14 | Settings > Accessibility > Remove animations (or the three animation scales to 0), then open: palette, drawer (all styles), Ground Control, Launchpad, notifications hub, page home, XMB, Orbit | no spring, slide or loop; only fades and snaps; the Launchpad still slides briefly and linearly (known gap) | |
| NFR-15 | each background kind (black, wallpaper bright and dark, solid, gradient, texture, live scenes, Horizon, a bright photo): look at clock and labels; run an accessibility contrast check (Accessibility Scanner) on home, palette, settings | primary text 4.5:1; live scenes and Horizon are the unsampled cases to look at first | |
| NFR-17 | Ground Control > Backup > Crash reports: force a crash on a debug build, share it, delete all | the share sheet is the only exit; no network traffic from Liftoff (check with a packet capture or `dumpsys netstats`) | |
| NFR-19 | section 9, three-button and gesture navigation, on a notch device | every step in section 9 passes | |

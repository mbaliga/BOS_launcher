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

## State, backup and grants (wave 3 state stream)

- [ ] NFR-18: `adb shell bmgr backupnow com.bos.sphere`, uninstall, reinstall, `adb shell bmgr restore` (or a device transfer): after the first start no corner or left-page widget is bound, the picked background picture is gone, and Ground Control > Backup lists the widgets under "Widgets to add again" with a Dismiss action. Layout, settings and Launchpad rules are intact.
- [ ] NFR-18: an ordinary app update (install over the top) keeps every widget and the background picture.
- [ ] CFG-02: from a setup file exported on another install, Add on each listed widget shows the system bind screen (when needed) and the provider's configure screen, then the widget lands in its corner. Repeat with a widget whose provider lives in the work profile (recorded limit: main profile only).
- [ ] CFG-04: remove a Launchpad rule, then Backup > restore the newest snapshot: the rule is back.
- [ ] CFG-07: from a clean install, press the microphone button in Radar, open the Launchpad access prompt, the Recents panel's access row and the dock's notification access: each shows the one-screen explanation before the system route.
- [ ] CFG-01: Ground Control > Home > Keyboard shortcuts: change a shortcut, export the setup, import it on a second install: the same key works there.
## Edit card and empty home (wave 8 editCard)

- [ ] HOM-12: long press empty home, or quick sheet, Edit. The home shrinks to a rounded card on the left (right in RTL) with the page carousel on the right and the add carousel below; drag, drop onto an app, resize a widget and Undo all work on the card; no dropped frames during the transition.
- [ ] HOM-12: Delete page shows an Undo notice and one tap restores it; deleting the last page leaves an empty page; Move up, Move down and long-press drag reorder; rotate keeps the arrangement; Esc, Back and Done leave.
- [ ] HOM-12: animations off: no shrink, the carousel fades in; TalkBack reads Page N of M and the buttons; keyboard arrows and Tab reach the card, the thumbnails, the buttons and the add carousel.
- [ ] HOM-01: fresh data: one empty page, hint "Long press to add apps", empty dock; adding from the carousel works; updating over an existing install leaves the layout as it was.

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

## Launchpad groups (wave 10 groups)

- [ ] STR-13: Ground Control > Launchpad > Groups: create "Work" (Normal) and "Family" (Priority, pick a colour). Open Launchpad: the section titles are Family, Work, Everything else; the selected one is large and dark, the others small and grey.
- [ ] STR-13: from a chat row open the menu (long press, then swipe right and More) > Move to group... > pick the person scope > Work. The same person's chats from a second messaging app (same name, or "Name (WhatsApp)") land in Work too.
- [ ] STR-13: with the contacts grant off, the rule editor says contact conditions never match; turn the grant on from Groups settings (explanation first, CFG-07) and the "in my contacts" and "starred" conditions start to match; turn it off again and nothing crashes.
- [ ] STR-13: set My last name, add the rule "sender last name is my last name" to Family: a message from someone with that surname shows in Family, in front of other sections, even if its app is on a digest.
- [ ] STR-13, STR-09: a Priority notification arrives while the home is showing: the left-edge indicator is tinted, shows a star and TalkBack reads "N waiting, M priority". System Do Not Disturb behaves as before (nothing changed by groups).
- [ ] STR-13, INP-01..03: with a keyboard only, reach a group title with the arrow keys, select it with Enter; in the manager, Move up and Move down reorder groups and rules; with a mouse, right-click a row opens the same menu.
- [ ] STR-13, NFR-13: TalkBack reads each group title with its priority, each manager button with the group's name (for example "Move Work up"), and the rule summary and "matches N current notifications" line change as a live region while editing.
- [ ] STR-13, CFG-04: delete a group, then Ground Control > Backup > restore the newest snapshot: the group, its assignments and rules are back. Export the setup, import on a second install: groups, links and rules arrive; cloud backup and device transfer do not carry them (bmgr restore leaves them empty).
- [ ] STR-13: with 100+ real notifications from many apps, Launchpad opens and scrolls without a stall and the section order matches the manager.
- [ ] HOM-22: without usage access, launch four apps from Liftoff, set the landing to Recent apps (when the UI lands): they appear newest first; relaunching one moves it to the front; the empty state explains how to include apps opened elsewhere.
- [ ] HOM-22: grant usage access (Settings > Special app access), open an app from the system's own app list, return to Liftoff: the app joins the Recent landing without restarting. Revoke the grant: it stays out of new sightings and nothing crashes. No work-profile app opened inside the work profile appears from usage events (UsageStatsManager reports the calling profile); work apps launched from Liftoff do.
- [ ] HOM-22: "Remove from recents" removes one app, "Clear recents" empties the list, launching an app again brings it back. Restart the phone: removals persist. Export the setup and inspect it: no recents or removals in the file; bmgr restore leaves the removals empty.
- [ ] HOM-22, INP-16, INP-17: on Minimal with a mouse, resting in each corner reveals the hotspot after the dwell, a click runs it; with a keyboard, Ctrl+Shift+Q, W, A and S run the four corners and Ctrl+Shift+L cycles the landing; with TalkBack the four hotspots are announced as buttons.
- [ ] HOM-22: switch the landing four times from Ground Control > Home, the quick sheet (long press the wallpaper) and Ctrl+Shift+L: each shows its surface with a short cross-fade (an instant swap with animations off); switching back to Selected apps shows the same pages, folders and widgets.
- [ ] HOM-22: Recent apps in each home style (grid, list, XMB, DSi), portrait and landscape, light and dark, right to left: newest first, no pages, long press shows the app menu with Remove from recents and Clear recents; with no history the one-line empty state shows and "Allow usage access" opens the explanation then the system screen.
- [ ] HOM-22, HOM-05: All apps landing: A to Z, the alphabet index jumps in the grid and DSi styles and the list rail still scrubs, type-to-filter and the Work and Personal tabs work, hidden apps stay out; a hardware keyboard reaches the filter field.
- [ ] HOM-22, INP-16, INP-17: Minimal shows only the wallpaper and four faint dots (no clock, strip, dock, rail, page dots); the edge swipe still opens Launchpad, a long press opens the quick sheet, and TalkBack reads the four hotspots as buttons in reading order; "Show corner dots" off hides the dots and the hotspots still work.
- [ ] HOM-01, HOM-22: on a fresh install the hint shows "Long press to add apps" and "Or show recent apps"; tapping the second switches to Recent apps with the empty state, and the quick sheet's "Edit home pages" returns to the empty page in edit mode.

## Radar background and signal view (wave 14 signalFx)

- [ ] PAL-24: open Radar in portrait and landscape, dark theme: a field of faint dots, brightest in the middle and gone at the edges, each swelling and fading in a slow wave (about 5 to 6 s); a faint ring scope with a soft wedge turning once per 6 s and a thin ring every 5 s. Compare with the owner's recordings. Nothing flashes; result text stays easy to read in every layout (Orbit, Filmstrip, Flat) while typing.
- [ ] PAL-24: Ground Control > Radar > Radar background: Sweep off removes rings, wedge, ping and centre dot but keeps the dots; Signal view Off removes the lines; Intensity Subtle is visibly fainter; the preview strip shows each "Preview as" state.
- [ ] PAL-24, CFG-17: Lab > Radar signal: tap Weak, Fair, Good, Excellent in turn and open Radar after each: weak = one or two slow faint pulses on two lines, each step adds lines, speed and brightness, excellent = eight lines with evenly spaced pulses (gentle, never flashing). No signal shows comic lines from the screen edges, jittering about 8 times a second, middle calm. Live returns to the real state.
- [ ] PAL-24: real signal changes: with Radar open walk out of Wi-Fi range, then back; switch Wi-Fi off with mobile data on; the level follows within a second or two without flicker. Airplane mode on: the comic lines fade in; airplane mode off: they fade out in about 400 ms and the pulses return. A captive-portal Wi-Fi reads weak, not no signal.
- [ ] PAL-24, NFR-14: Remove animations on: no sweep rotation, no ping, no comic jitter, dots still, pulses frozen at evenly spaced spots; the level is still readable from how many lines carry pulses and how bright they are.
- [ ] PAL-24, NFR-13: with TalkBack on, opening Radar announces the signal once ("Signal: good, Wi-Fi"); the background itself is not focusable or read.
- [ ] PAL-24: battery and frame rate: 10 minutes on Radar with Excellent forced, screen on, against 10 minutes with the sweep and signal view off: note the battery percentage difference. With the Radar open, press the power button and lock the phone: no frames are drawn (profile GPU rendering shows none); switch to a floating window so Radar loses focus: the animation stops. Dev options > Profile GPU rendering: bars stay under the 16 ms line on the target device.
- [ ] PAL-24: Ground Control > Home > Background > Live scenes > Breathing dots: near-white dots on near black, glowing and fading in waves; the default background is unchanged.
- [ ] NFR-17: with a packet capture while Radar is open for a minute: no traffic from Liftoff caused by the signal view (only the system callback; the manifest lists ACCESS_NETWORK_STATE and nothing new).

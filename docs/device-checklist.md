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

- [ ] NFR-18: `adb shell bmgr backupnow com.bos.sphere`, uninstall, reinstall, `adb shell bmgr restore` (or a device transfer): after the first start no corner or left-page widget is bound, the picked background picture is gone, and Ground Control > Backup lists the widgets under "Widgets to add again" with a Dismiss action. Layout, settings and Launchpad rules are intact. Launchpad groups, people links and the last name (`launchpad_groups`) are excluded from backup and transfer by design: after the restore the groups list is empty until the setup file is imported.
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
- [ ] HOM-22: without usage access, launch four apps from Liftoff, set the landing to Recent apps (Ground Control > Home, the quick sheet or Ctrl+Shift+L): they appear newest first; relaunching one moves it to the front; the empty state explains how to include apps opened elsewhere.
- [ ] HOM-22: grant usage access (Settings > Special app access), open an app from the system's own app list, return to Liftoff: the app joins the Recent landing without restarting. Revoke the grant: it stays out of new sightings and nothing crashes. No work-profile app opened inside the work profile appears from usage events (UsageStatsManager reports the calling profile); work apps launched from Liftoff do.
- [ ] HOM-22: "Remove from recents" removes one app, "Clear recents" empties the list, launching an app again brings it back. Restart the phone: removals persist. Export the setup and inspect it: no recents or removals in the file; bmgr restore leaves the removals empty.
- [ ] HOM-22, INP-16, INP-17: on Minimal with a mouse, resting in each corner reveals the hotspot after the dwell, a click runs it; with a keyboard, Ctrl+Shift+Q, W, A and S run the four corners and Ctrl+Shift+L cycles the landing; with TalkBack the four hotspots are announced as buttons.
- [ ] HOM-22: switch the landing four times from Ground Control > Home, the quick sheet (long press the wallpaper) and Ctrl+Shift+L: each shows its surface with a short cross-fade (an instant swap with animations off); switching back to Selected apps shows the same pages, folders and widgets.
- [ ] HOM-22: Recent apps in each home style (grid, list, XMB, DSi), portrait and landscape, light and dark, right to left: newest first, no pages, long press shows the app menu with Remove from recents and Clear recents; with no history the one-line empty state shows and "Allow usage access" opens the explanation then the system screen.
- [ ] HOM-22, HOM-05: All apps landing: A to Z, the alphabet index jumps in the grid and DSi styles and the list rail still scrubs, type-to-filter and the Work and Personal tabs work, hidden apps stay out; a hardware keyboard reaches the filter field.
- [ ] HOM-22, INP-16, INP-17: Minimal shows only the wallpaper and four faint dots (no clock, strip, dock, rail, page dots); the edge swipe still opens Launchpad, a long press opens the quick sheet, and TalkBack reads the four hotspots as buttons in reading order; "Show corner dots" off hides the dots and the hotspots still work.
- [ ] HOM-01, HOM-22: on a fresh install the hint shows "Long press to add apps" and "Or show recent apps"; tapping the second switches to Recent apps with the empty state, and the quick sheet's "Edit home pages" returns to the empty page in edit mode.

## Cross-cutting audit (wave 13)

- [ ] NFR-13: TalkBack on, Ground Control > Launchpad > Groups: type your last name into the field. TalkBack reads the typed text back, not only the field name (the field carries a content description; if it hides the typed text on a device, report it).
- [ ] NFR-13: Launchpad roomy list: the "+N more" line and each title in the strip at the top are announced as a button and a tab and can be hit with a thumb without touching the neighbour (48 dp targets, wave 13).
- [ ] NFR-13: edit card, page carousel: Add page, Move up, Move down and Delete page are announced with the page number and can be hit reliably. Known gap, not changed in wave 13: the add carousel's tab labels (Apps, Widgets, Folders, Shortcuts) and the Personal and Work chips are smaller than 48 dp because the add band is a fixed 132 to 136 dp high; judge whether they are usable and decide on a taller band.
- [ ] NFR-13, font scale 200 percent: Launchpad (roomy list, groups manager, rule editor), the edit card, the landing switcher in the quick sheet and the Recent apps landing. Text wraps or ellipsizes, nothing is cut off, every button stays reachable by scrolling.
- [ ] NFR-17: with the groups set up (people, rules, last name), trigger "Copy report" in Capabilities and share a crash report from Ground Control; neither contains a name, a sender or a notification text.
- [ ] CFG-01, CFG-03: export the setup, edit the file by hand to add an unknown field inside one group, one person and one rule and an unknown condition kind in a rule, import it, export again: the unknown fields are still there, and the rule with the unknown condition shows as off.
## Radar background and signal view (wave 14 signalFx)

- [ ] PAL-24: open Radar in portrait and landscape, dark theme: a field of faint dots, brightest in the middle and gone at the edges, each swelling and fading in a slow wave (about 5 to 6 s); a faint ring scope with a soft wedge turning once per 6 s and a thin ring every 5 s. Compare with the owner's recordings. Nothing flashes; result text stays easy to read in every layout (Orbit, Filmstrip, Flat) while typing.
- [ ] PAL-24 (changed in wave 15, see the Background section below): the Radar-only pane with its Sweep, Signal view and Comic switches was removed; Radar, Glow lines, Breathing dots and Radar sweep are now items of Ground Control > Look > Background and Radar shows the same two slots as the home.
- [ ] PAL-24, CFG-17: Lab > Radar signal: tap Weak, Fair, Good, Excellent in turn and open Radar after each: weak = one or two slow faint pulses on two lines, each step adds lines, speed and brightness, excellent = eight lines with evenly spaced pulses (gentle, never flashing). No signal shows comic lines from the screen edges, jittering about 8 times a second, middle calm. Live returns to the real state.
- [ ] PAL-24: real signal changes: with Radar open walk out of Wi-Fi range, then back; switch Wi-Fi off with mobile data on; the level follows within a second or two without flicker. Airplane mode on: the comic lines fade in; airplane mode off: they fade out in about 400 ms and the pulses return. A captive-portal Wi-Fi reads weak, not no signal.
- [ ] PAL-24, NFR-14: Remove animations on: no sweep rotation, no ping, no comic jitter, dots still, pulses frozen at evenly spaced spots; the level is still readable from how many lines carry pulses and how bright they are.
- [ ] PAL-24, NFR-13: with TalkBack on, opening Radar announces the signal once ("Signal: good, Wi-Fi"); the background itself is not focusable or read.
- [ ] PAL-24: battery and frame rate: 10 minutes on Radar with Excellent forced, screen on, against 10 minutes with the sweep and signal view off: note the battery percentage difference. With the Radar open, press the power button and lock the phone: no frames are drawn (profile GPU rendering shows none); switch to a floating window so Radar loses focus: the animation stops. Dev options > Profile GPU rendering: bars stay under the 16 ms line on the target device.
- [ ] PAL-24 (changed in wave 15): Breathing dots is one of the nine background items now (a slot of Ground Control > Look > Background), no longer a live scene; the default background is now Spire + Breathing dots.
- [ ] NFR-17: with a packet capture while Radar is open for a minute: no traffic from Liftoff caused by the signal view (only the system callback; the manifest lists ACCESS_NETWORK_STATE and nothing new).

## Background reset: two signal-driven slots (wave 15 backgrounds)

Nothing here has been confirmed on a device. Compare every item with the owner's reference images 27 to 34 and the screenshots the render tests write to `feature-grid/build/screenshots/bg-*.png`.

- [ ] HOM-16: first start after updating from an older build (any old background: a photo, a gradient, a live scene, the system wallpaper): the home shows Spire + Breathing dots, nothing crashes, and Ground Control > Look > Background shows Slot 1 Spire and Slot 2 Breathing dots. The old photo file is gone from the app's storage (no `background_*.img` under files or no_backup).
- [ ] HOM-16: import an old export file (taken before this build) in Ground Control > Backup: it is accepted, the background becomes the default pair, every other setting is applied.
- [ ] HOM-16: Ground Control > Look > Background: every one of the ten options in each slot has a thumbnail; with Spire in slot 1 the other four living wallpapers in slot 2 are greyed with "Only one living wallpaper", and Spire itself says "Already in the other slot"; picking an effect or None works; None in both slots gives pure black on the home. TalkBack reads each option with its reason and says "disabled".
- [ ] HOM-16, PAL-24: Preview signal: tap No signal, Weak, Fair, Good, Excellent. Each item changes its own way, calm and slow: Beam reaches further, widens and its tip whitens (sparkle beads at fair and above); Spire rises and its white core brightens; Halo's arc grows from a short bright arc to a nearly closed ring with brighter haze; Laser's beam grows, its arc draws further, the floor glow brightens; Shaft widens and brightens; Bottom glow's bloom rises (weak is the quiet line, excellent the full bloom); Breathing dots breathe deeper over a wider area; Radar sweep's wedge brightens and pings come more often (none at no signal); Glow lines pulse on more lines. Compare with the references (30 Beam, 31 Spire, 32 Halo, 33 Laser, 34 Shaft, 27 to 29 Bottom glow).
- [ ] HOM-16, PAL-24: Lab > Radar signal rows still force the state the real background shows on the home and on Radar; the level changes ease over about a second (no jump). With no signal every item falls to a dim resting state and thin comic lines close in from the edges; they fade out in about 400 ms when signal returns (airplane mode on and off).
- [ ] HOM-16: portrait and landscape, dark and light theme, on a phone and a tablet or foldable (window resizes): Beam enters from the bottom in portrait and from the right in landscape; the other items keep their composition and scale cleanly, nothing is stretched or cut oddly; on a light theme the items are a quiet wash and text stays easy to read.
- [ ] HOM-16, NFR-15: clock, labels, icons and Radar results stay easy to read over every item at Excellent; where a scrim is added it is light. Lab shows the last legibility line.
- [ ] HOM-16, NFR-05, NFR-14: 10 minutes on the home with a living wallpaper plus Breathing dots against both slots None, screen on: note the battery difference. Dev options > Profile GPU rendering stays under the 16 ms line. Lock the phone and switch to another app: no frames are drawn (the clock stops). Remove animations on: every item is one still picture at the current level.
- [ ] HOM-16: Settings backdrop "Background, dimmed" shows the chosen items still, dimmed, behind Ground Control; "Pitch black" stays black.
- [ ] HOM-16: Intensity Subtle is visibly fainter than Normal for every item.
- [ ] NFR-13: with TalkBack on, the home and Radar announce the signal once ("Signal: good, Wi-Fi") and the background is not focusable.
## Recents panel (wave 15, STR-14)

- [ ] STR-14: from the home, drag in from the right edge: the home shrinks into the same rounded card as Launchpad, pushed to the LEFT, following the finger; a short drag springs back, a long one or a fling settles open in about 0.45 s. A thin sliver of the card stays at the left edge; tap it, drag it back, press Back or Esc: home returns. Compare the card, scrim and sliver with Launchpad on the other side.
- [ ] STR-14, INP-08: the edge swipe does not fight the system back gesture on a gesture-navigation device (the right edge back gesture of the system may win in the first few dp; the 28 dp zone and the handle are the fallback). The slim handle on the right edge opens the panel with a tap; the corner and gesture action "Open recents" and Ctrl+R open it.
- [ ] STR-14: launch three apps from Liftoff, go home, open the panel: they are listed newest first, one row each, with "Recent" and how long ago; the one line under the title says Android does not tell a launcher which apps are still running. Relaunch one: it moves to the top.
- [ ] STR-14: grant usage access, open an app from a notification and from the system share sheet, leave it, return to Liftoff and open the panel: the apps appear ordered by when you left them; without the grant only apps opened from Liftoff appear and the empty state offers "Allow usage access".
- [ ] STR-14: long press, right-click (mouse) and the Menu key on a row open the app menu with "Remove from recents"; the app disappears and returns after you use it again. "Clear all" empties the list. Tap a row: the app opens.
- [ ] STR-14, RTL: switch to Arabic: the card goes to the RIGHT, the sliver and handle are on the right, the swipe comes from the LEFT edge; Launchpad is then on the left edge.
- [ ] STR-14: Ground Control > Home > Right side: Recents panel, Dock rail, Off. With Dock rail and the dock turned on, the right-edge swipe reveals the dock rail; with the dock off nothing is turned on by it. With Off there is no handle and no swipe.
- [ ] STR-14, NFR-14: Remove animations on: the card snaps and the panel cross-fades. Font size 200 percent and display size large: rows and the header wrap, nothing overlaps the sliver, Clear all is reachable.
- [ ] STR-14, NFR-13: TalkBack reads each row as "Name, Recent, N min ago", the sliver as "Home card. Return to home", the handle as "Open recents". With a keyboard, Tab and arrows reach each row and Clear all; Enter opens, Menu opens the menu, Esc returns.
- [ ] STR-14, NFR-05: with the profiler on a device, open and close the panel ten times: no more than 1 percent janky frames; the home keeps its state (scroll position, open folder) after returning.

## Glance home (wave 16, HOM-23)

Nothing here has been confirmed on a device. Compare with the owner's screenshots 24 (portrait), 25 (portrait with controls) and 26 (landscape), and the render tests' images in `feature-grid/build/screenshots/glance_*.png`.

- [ ] HOM-22, HOM-23: update from an older build that used the default Selected apps: the home now lands on Glance; a build where Recent apps or All apps was chosen keeps it. Ground Control > Home > Home landing shows Glance. Choosing Selected apps keeps it after a restart; your pages are untouched.
- [ ] HOM-23: fresh install: pure black, large thin clock centred, date under it in grey, the glow lines flanking it (vertical brackets in portrait, horizontal in landscape), four hotspot glyphs in the corners (camera top left, music top right, apps bottom left, search bottom right) and the small indicator at the bottom centre that fades after a few seconds. Rotate: the same four actions stay under the same physical corners.
- [ ] HOM-23, STR-06: play music with notification access granted: the title and artist appear at the top centre (a long title scrolls; nothing is shown when playback stops). Tap an empty spot: the black pill drops from the top with previous, play/pause and next above the title; the buttons work; it pops out after 4 s, restarts its timer when you touch it, stays while a finger is down and goes on a tap outside. Remove animations: a fade.
- [ ] HOM-23: Glance content presets (Time; Time + Date; Date + Weather; Time + Date + Weather) and the free toggles (including Battery) change the centre at once; the clock follows the 12 or 24 hour system setting; tap the clock opens the clock app.
- [ ] HOM-23, NFR-16: with Weather on, the ambience follows the real conditions (clear day, clear night, clouds, rain, snow, fog, thunder) and is calm; the temperature sits next to the date; weather off shows no layer. A packet capture shows no new traffic.
- [ ] HOM-23, NFR-14, NFR-05: screen off or another app in front: nothing animates; remove animations: a still composition; thunder flashes at most once every 20 s.
- [ ] HOM-23, INP-16, INP-17: Hotspots Four, Two, One, None; a disabled corner draws nothing and tapping or resting a mouse there does nothing; with None the edge swipes, long press (quick sheet with Glance content, Hotspots, Background, Landing, Edit home pages) and keyboard shortcuts still work. Tab and arrows reach each enabled hotspot.
- [ ] HOM-23, NFR-13: TalkBack order: clock, date, weather, now playing, controls (after the tap, or Enter on the music line), hotspots; the indicator reads once.
- [ ] HOM-23: light theme (quiet light surface, dark type), Arabic (indicator and panels mirror, the clock digits do not), font size 200 percent and large display size (the clock grows, the date wraps, nothing touches the hotspots or the music line).

## Glance edges (wave 17, HOM-23, STR-01, STR-09, STR-14, INP-08, NFR-13, NFR-20)

Nothing here has been confirmed on a device. Owner bug report on build 48: "The glance needs to have launchpad on the left. Why is that missing?" Decisions: `docs/decisions/glanceEdges.md`. Robolectric renders: `feature-grid/build/screenshots/glance_handles_*.png`.

- [ ] STR-01, HOM-23, NFR-20: gesture navigation phone, Glance home: drag in from the PHYSICAL left edge. Record what happens: the system back gesture zone (about 24 to 30 dp) can take the first dp and cancel the stream; the Glance edge zone is 48 dp so a swipe that starts a little further in (where the handle is) should open Launchpad. NFR-20 forbids `systemGestureExclusion`, so the launcher never claims the zone. Note the dp at which the swipe starts to work, per device.
- [ ] STR-01, HOM-23: the handle tap is the reliable path: tap the slim bar on the left edge (48 dp wide, 160 dp tall, centred): Launchpad opens. Same with three-button navigation.
- [ ] HOM-23: the left and right handles are barely there: visible at low alpha after a touch, almost invisible after about 4 seconds, back on any touch; with Remove animations on they change instantly. Pure black surface still reads calm next to the owner's references.
- [ ] STR-09, STR-13, HOM-23: with notification access on and an unread notification, a small dot shows above the left handle (the group colour and a star for a Priority group); tap it opens Launchpad; nothing waiting, no dot.
- [ ] HOM-23: tap the list glyph at the bottom centre (Launchpad) and the right-hand dot: Launchpad and the recents panel open; the home glyph does nothing; each target is 48 dp (TalkBack: "Open Launchpad", "Open recents", "Glance home").
- [ ] STR-14, HOM-23: drag in from the right edge, and tap the right handle: recents opens. Right side set to Dock rail or Off: no right handle.
- [ ] STR-01: Launchpad set to Off in Ground Control: no left handle, dot or indicator glyph; the edge swipe does nothing; nothing looks dead. Rail setting on: the rail shows on Glance as on the other landings.
- [ ] STR-01, STR-14, RTL: Arabic: the Launchpad handle and swipe are on the RIGHT edge, recents on the LEFT; the indicator mirrors.
- [ ] HOM-23, NFR-13: TalkBack: swipe to the handles (focusable buttons labelled "Open Launchpad" and "Open recents"), double tap opens; with a keyboard Tab reaches the indicator buttons.

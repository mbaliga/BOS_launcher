# Wave 25 integration (merge of launchpadHub, hubData, musicNotch, dockIcons, radarFix, actionIcons, uninstall)

The first full run on the merged state had 4 groups of failures. Each was re-run alone and then with the whole module in this worktree.

| ID | Status | Note |
|---|---|---|
| 1 TourBusySmokeTest CFG_09, TourRecreateSmokeTest NFR_11 ("a global snapshot write is pending") | not reproducible, no code change | Pass alone (both classes, 14 of 14) and in the full `:app:testDebugUnitTest` run (225 of 225). Audited every snapshot-state write the merge added (HubState, LaunchpadHubNav, LaunchpadMenuState, notch title, dock menu): all of them run inside composition callbacks or composition-scoped coroutines; `LaunchpadHubNav` is a StateFlow collected in a `LaunchedEffect`, and the history store's executor never touches snapshot state. The marquee in the notch is gated by the motion preference. Conclusion: the failures came from the heavily loaded first run (six agents on 4 CPUs), where an in-flight frame write could still be pending at the check. |
| 2 DockWiringTest INP_01_*, INP_05_* ("Compose did not get idle after 60 SECONDS") | not reproducible, no code change | All 8 tests pass alone and in the full module run. The new rail and long-press menu have no infinite animation (the only infinite one, the notch marquee, is gated by motion prefs and not in the dock). A 60 s idle timeout under load is the same load effect as item 1. |
| 3 DockDialogsW24Test INP_20 (typed keys not reaching the dock panel field) | not reproducible, no code change | Passes alone and in the full module run (4 of 4). |
| 4 LaunchpadHubW25 Portrait/Landscape STR_02_topBarHasASlotForTheViewSwitcher | fixed (test updated to the owner decision) | The hub switcher is one flat 48 dp tab row directly under the title bar (BlackBerry Hub style), no longer in the header. The test now asserts the row sits directly below the header (menu button) and spans the full width. |
| 4 visual pass (hub-all, hub-options, launchpad-hub-*-30 screenshots read) | fixed | Filter chips and option chips were 14 dp pills at 13 sp; they are now flat 4 dp-corner chips at 12 sp with 4 dp vertical padding (48 dp touch target kept); the filter row starts at 16 dp like rows, headers and the options panel, with 8 dp gaps. No doubled dividers, no clipped tabs were found. |

Not device-verified; all checks are Robolectric. Items 1 to 3 are honest "could not reproduce"; if they recur on a quiet machine they need a fresh look.

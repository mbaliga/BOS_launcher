# Recents panel (wave 15, stream w15-recents): decisions

Owner request: "Left Hand Side is the notifications (Launchpad), Maybe RHS is where we put the recent apps, or in the dock - dock must remain optional." and "Can open apps that are now dismissed to the background be shown here?"

## What changed in the spec

| Row | Old text | New behaviour |
| --- | --- | --- |
| STR-14 (new) | none | Recents panel: the right-hand mirror of Launchpad, a swipe in from the right edge, handle, Ctrl+R and the "Open recents" corner or gesture action shrink the home into the Launchpad card pushed to the left with a sliver; the freed background lists recent apps. |
| STR-01 | The card goes right (left in RTL) for Launchpad. | Unchanged for Launchpad. Added: the same geometry, card layer, scrim and settle also serve STR-14, parameterized by the edge (`CardEdge.forRecents`). |
| HOM-22 | The Recent landing used Liftoff's history plus foreground usage events. | The landing and the panel share ONE source (`HomeRecents`); usage events now include background events (ACTIVITY_PAUSED, MOVE_TO_BACKGROUND). |
| INP-08 | Gestures assignable. | "Open recents" is an assignable action with a shortcut (Ctrl+R). |
| Section 3 privacy row | Dock and the Recent landing show recent apps. | The panel too; and the platform cannot say whether an app process is alive; the panel says so. |

## Decisions

- **No fork of the geometry.** `LaunchpadCardGeometry`, `homeCardLayer`, `LaunchpadCardOverlay` and `LaunchpadRevealState` are reused with a second instance and the opposite edge. `CardEdge.forRecents(rtl)` is the only new geometry code; Launchpad behaviour and its tests are unchanged. Two `homeCardLayer` modifiers are chained on the home card; they are mutually exclusive (opening one closes the other) and each is the identity at fraction 0.
- **Honest data.** Android has no public recent-tasks API for launchers and no way to know that a process is alive. The panel is "apps you left most recently": Liftoff launches, plus with usage access the public `UsageStatsManager` events (RESUMED, PAUSED and the deprecated MOVE_TO_FOREGROUND and MOVE_TO_BACKGROUND). An app's time is the latest of its Liftoff launches and its usage events; one row per app. Each row says "Recent" and how long ago; one line under the title states what the list cannot know. No hidden API, no reflection, no accessibility service.
- **Privacy class kept.** `usage/recents-state.txt` (removals and clears) is still in `NEVER_EXPORTED_FILES` and excluded from backup. "Clear all" and "Remove from recents" use the same store as the landing, so a removed app returns when it is used again.
- **Right side setting** (`LauncherPrefs.rightSide`, plain pref `rightSide`, exported with the other prefs): `RecentsPanel` (default, for everyone including existing installs: a missing or unknown value parses to it), `DockRail` (the right-edge swipe asks the dock controller to focus and reveal its rail, exactly like "Focus the dock"; it never turns the dock on), `Off` (no handle, no swipe, the action shows a toast).
- **Edge and RTL.** The swipe starts in the 28 dp edge zone on the physical right edge (the left in right-to-left), the card goes to the opposite side of Launchpad's. The handle is a slim bar at the same edge, hidden on Minimal like Launchpad's. No system gesture exclusion is used.
- **Focus.** The panel is a focus layer (7, above Launchpad's 4, below menus at 8); each row and "Clear all" is a node; Esc is the shared Dismiss command; Back uses the predictive-back handler.
- **Glance indicator hook.** `RightSideDestinations.of(rightSide)` lists the destinations on the right (Recents, Dock) for the surface indicator of HOM-23, which is built by another stream and reads it later.
- **Ctrl+R** was free in the shortcut registry; it is rebindable like the others.

## Not done here

Device verification (see `docs/device-checklist.md`, "Recents panel (wave 15)"). The Glance surface indicator itself (another stream).

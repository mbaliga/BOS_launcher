# Glance edges (wave 17): Launchpad on the left, recents on the right

Owner bug report on build 48 (authoritative): "The glance needs to have launchpad on the left. Why is that missing?" The Glance home had just become the default landing and the owner could not get to Launchpad from it.

## 1. Reproduction (touch swipes injected on a fresh state, Robolectric, `GlanceEdgesSmokeTest`)

Run on the unmodified build-48 code (base 928f742), default landing Glance, default `leftPage` (Launchpad):

| Entry point | Result on build 48 |
| --- | --- |
| Touch swipe from the physical left edge (x inside `RevealMath.EDGE_DP`, moving right) | works (card slides, Launchpad shown) |
| Same with `leftPage` set explicitly to its default | works |
| Same after the music controls popped in | works |
| Swipe starting at the top-left / bottom-left hotspot corner | works |
| Keyboard shortcut (Ctrl+N, `OpenLaunchpad`) | works |
| Corner action `OpenLaunchpad` on a hotspot | works |
| Right edge swipe for recents (also from a corner, also with controls in) | works |
| A visible way in (handle, waiting indicator, tappable indicator glyph) | MISSING: nothing is drawn, nothing is tappable |

So the injected-swipe pipeline was never the fault: the root `launchpadEdgeDrag` modifiers run on the Initial pointer pass above the Glance surface's own tap and gesture layer, `leftPage` defaults to Launchpad, and the hotspots (corner boxes) do not cover the edge zone at mid height. No interception-order, enabled-flag, or default problem exists.

## 2. Root causes

1. **`HomeLandingRules.showsChrome(landing)` was false for `Minimal`**, the enum value Glance reuses (wave 12 wrote "Minimal draws no rail, handle or waiting dot; the edge swipe still opens Launchpad", wave 16 inherited it). That one flag hid the 5 dp left handle and its 24 dp tap strip, the waiting-notifications indicator, and the STR-14 right handle on Glance. A flag named "chrome" that also meant "the entry points" is the regression source.
2. **The only remaining way in was an edge swipe**, which is undiscoverable and, on a gesture-navigation phone, starts inside the system back-gesture zone (about 24 to 30 dp, which the old 28 dp edge zone sits entirely within). NFR-20 forbids `systemGestureExclusion`, so the app cannot claim it. Robolectric has no system gesture layer, so it cannot show this; it is a device row (below).
3. The Glance surface indicator drew a list glyph (Launchpad) and right-hand dots as pure decoration (one merged, non-clickable description).

## 3. What changed

- **Explicit chrome table** `HomeChromeRules` (core-data `HomeChrome.kt`), replacing `showsChrome`: per landing and element (left handle, waiting indicator, rail, right handle, media strip, dock, work-paused banner) with the settings that switch it on. `HomeChromeRulesTest` checks every landing x element x relevant setting. `HomeScreen` asks the table for the handles, the waiting indicator, the rail, the media strip, the dock and the banner.
- **Glance handles**: `GlanceEdgeHandle` (feature-grid `GlanceEdges.kt`): a 3 dp x 56 dp bar at 30 percent alpha, 6 percent after 4 s idle (tween 600 ms, instant under reduced motion), back on any touch of the screen (`glanceWakeOnTouch`, an Initial-pass observer that consumes nothing) or of the handle. Tap target 48 dp x 160 dp at the physical edge, labelled button ("Open Launchpad" left, "Open recents" right), focusable. Left in LTR, right in RTL for Launchpad; mirrored for recents. The right handle follows the Right side setting (Recents panel only).
- **Waiting indicator**: `LaunchpadWaitingIndicator(quiet = true)`, a small dot above the left handle in the Priority accent (wave 10) or the usual blue, a 48 dp target, spoken as "N waiting. Launchpad" (or with the priority count). Not drawn when nothing waits or access is missing.
- **Rail**: not drawn on Glance unless `prefs.launchpadRail` is on (then the rail replaces the pill, as on the other landings; the quiet handle stays).
- **Indicator buttons**: the list glyph ("Open Launchpad") is a 48 dp button and focus node (`glance.indicator.launchpad`); W44: the right-side dots ("Open recents", "Show the dock", `glance.indicator.right.<Destination>`) were removed, owner: "A dot is showing where there is no page"; the home glyph ("Glance home") stays non-interactive. The one merged description was dropped.
- **Edge zone on Glance** is 48 dp (`HomeChromeRules.edgeZoneDp`), the handle width, so a swipe can start where the handle is, a little past the system back zone. Elsewhere it stays 28 dp. The Launchpad card geometry and the Glance clock layout are untouched.
- Launchpad Off: no handle, no dot, no indicator glyph, shortcut and corner action keep their existing fall-backs (the notification hub for the corner action). Right side Off or Dock rail: no recents handle; the dot opens the dock rail or says so.

## 4. What the old spec said

HOM-22 (Glance): "Edge swipes, gestures, shortcuts and a long press ... keep working" and the code comment "no rail, handle or waiting dot". STR-01: "Also opened by a visible handle, a click, and a shortcut" held on every landing except Glance, which broke that sentence. HOM-23 had "a list glyph for Launchpad" in the indicator with no action. Rows now read as above.

## 5. Honest limits

- The 48 dp zone and the handle tap are the reliable path on gesture navigation; a swipe from the first dp of the physical edge may be taken by the system back gesture (`ACTION_CANCEL` reaches the launcher). Not verified on a device (`docs/device-checklist.md`, "Glance edges (wave 17)").
- Screenshots are Robolectric renders, not a device.

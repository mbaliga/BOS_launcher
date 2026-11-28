# Dock: icons only, options behind a long press (wave 25, owner item 4)

Owner: "Dock looks weird - show only icons please. Remove the I icon and put all its edit options behind a long press rather than ellipses."

## What the dock draws now

- Pinned and recent icons only. No text anywhere: the workspace name pill is a round home-glyph button (name kept as its description), the
  "Recent" label is a thin rule, the usage-access sentence is a ring-with-plus icon button. The "i" and "..." buttons are removed.
- The same `DockRail` draws the bottom rail, the side rail and the desktop surface's dock, so all variants are icons-only.
- The media strip is not touched here (another stream removes it).
- Alt+1 to 9 numbers stay (keyboard hint, off unless a keyboard is in use); they are the one deliberate bit of text-like drawing.

## Long press

| Press on | Menu |
| --- | --- |
| An icon | the app's shortcuts and open choices (host-supplied), App info, Move earlier, Move later, Replace with another app, Remove from dock (a recent icon: Pin to dock) |
| The dock itself (W55: replaced by the dock sheet, docs/decisions/dockSheet.md; was) | Edit dock, Workspaces and groups, Dock settings, Dock at the bottom/left/right (landscape phone only), Hide dock (not on the desktop surface or a forced rail), About the dock |

- A long press on a pinned icon opens its menu at once; moving on from the press is still the reorder drag and closes the menu.
- "Edit dock" is a small dialog: pinned items with move and unpin, and Add apps (the app picker). "Workspaces and groups" and "Dock settings"
  both open the one existing dock panel (it holds both); they stay two entries because the owner's list names both.
- Not gesture-only: the menu key and Shift+F10 on a focused icon open its menu (on the dock, the dock's); every entry is also a TalkBack
  custom action (dock node: the dock entries; icon: move, unpin, replace, app info, "Dock item options"; home button: next/previous workspace).
- Optional dock (dockOptional.md) unchanged: with the dock off, Ground Control still opens the same panel. "Hide dock" sets the same
  setting Ground Control does, so it is restored there.

## Tests

`DockIconsOnlyW25Test` (no text nodes bottom and side, no About/overflow buttons, tap launches, menus and every entry run, recent icon menu, menu key and Shift+F10, TalkBack actions exist and run).
Updated: `W4RadarInputDockTest` (the focus graph no longer lists the two buttons; the help dialog is reached through the menu; seven dialog windows now, the Edit dock dialog being the new one).
The popup is a separate window, so the Robolectric screenshots show the dock but not the open menu; its look is device-only.

## Wave 26 (owner feedback on build 54): taps, an empty dock, and the position indicator

Owner: "Tapping on the dock opens dock settings, long press should do that. If there are no recents or pinned, then hide the dock instead of showing just home. The dock overlaps with the position indicator."

### Taps

- The only tap that opened the panel was the round Home/workspace glyph (`WorkspaceChip` was `clickable(onOpenPanel)`). It now calls `DockActions.onGoHome`: the phone home goes to its home page (`gridState.goToPage(homePage)`, nothing visible when already there). A tap on an icon launches it (unchanged), a tap on the dock background does nothing (the rail has only a long-press detector; the collapsed band's tap still expands the dock, that is not settings).
- The workspace swipe on the glyph stays: `detectHorizontalDragGestures` only fires after the touch slop and a 40 px travel, so a tap cannot trigger it.
- The panel opens only from the long-press menu ("Dock settings", "Workspaces and groups"), from Ground Control (Desktop & dock), and from the menu key / Shift+F10 / TalkBack actions on the dock.

### Empty dock is hidden

- Rule (`DockVisibility`, `DockContent.hasItems`): the dock is drawn only when the dock is on for this surface AND it has a pinned item (that resolves to an installed app, group or home item) or a recent item (while the Recent section is on). The usage-access button alone does not count. With nothing to show `DockHost` composes nothing: no rail, no Home glyph, no collapsed strip.
- `DockHost` publishes what it holds in `DockPresence.hasItems`; the home's `HomeBottomStack.plan` takes `enabled = dock on && DockPresence.hasItems`, so an empty dock reserves no space (bottom stack, side reserve, media strip all as with the dock off). The presence value is kept while an overlay hides the rail so the home does not jump. It starts false and is set after the first composition and the first recents read.
- Getting the first pin with the dock hidden: app long-press menu and Radar action "Add to dock", dragging an app onto where the dock would be is not possible (no rail), Ground Control > Desktop & dock > "Workspaces, groups and dock settings" now has "Add apps to the dock" (app picker) at the top of the panel, and "Pin" on an app group in the same panel. The dock-optional rules stand (dock off: nothing reserved; Ground Control still opens the panel).
- Glance with Right side = Dock rail and an empty dock shows no rail (the indicator dot still exists; pin something to see the rail).
- Recents arrive asynchronously; a dock that only has recents appears after the first read of them.

### Dock vs position indicator

- Cause 1 (pages): the paged home's container was padded by the dock reserve, which lifted the page dots, clock and corner icons together with the apps, so the dots rode up into the rail. The reserve is now handed to `GridHome` (`reserveStart/End/Bottom`) and only the app area gives it up; the dots stay in the band.
- Cause 2 (Glance): the stack plan did not know about the Glance indicator (52 dp tall including its 48 dp targets) at all, and assumed the page dots were at 18 dp when they sit at about 46 dp with no bottom clock.
- Rule chosen: the dock sits ABOVE the position indicator (Glance indicator or page dots, and the bottom clock), with `HomeBottomStack.GAP_DP` = 8 dp clear between them (was 4). `HomeBottomStack.lowerStackDp` gives the stack height as a maximum of clock-and-dots, page dots (46 dp) and Glance indicator (52 dp), never a sum. Side rails stop 76 dp from the top and bottom edges, so they are beside the indicator row (46 to 52 dp) and the four corner hotspots; the bottom rail is inset 76 dp each side, clear of the corner hotspots.
- The indicator's drawing is untouched (another stream changes what the indicator counts); only the stack-plan numbers and the page-dot constants in `HomeBottomStack` mirror its present size. If that stream changes the indicator's height, change `GLANCE_INDICATOR_TOP_DP` / `PAGE_DOTS_TOP_DP`.

### Tests

`DockFixW26Test` (tap on Home glyph goes home, tap on background nothing, panel only from the menu entries, empty dock no node, rail returns on first pin and goes again on unpin, visibility rule), `BottomStackTest` (three W26 tests: eight dp gap at nine sizes, every edge, both indicators; corner hotspots; lower stack maxima; no dock reserves nothing), `DockStackW26SmokeTest` (real `MainActivity`: bounds of the dock and the indicator, Glance and two-page home, at 360x640, 411x891, 891x411, 1280x800, 280x653; empty dock draws nothing and the dots do not move when a pin arrives). Updated: `W4RadarInputDockTest.INP_07_...` (the tap on the workspace chip asserted the panel opened; now it asserts Home and no panel; renamed).

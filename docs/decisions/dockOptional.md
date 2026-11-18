# The dock stays optional: what works without it (wave 24)

Owner rule: the dock is optional; nothing important may be reachable only from the dock, and every dock feature must degrade cleanly
with the dock off. Wave 24 walked each dock feature with the dock setting off (and on the default Glance landing, which draws no dock
unless its right side is the dock rail).

## Survey

| Dock feature | With the dock off (or a landing without one) | Change |
| --- | --- | --- |
| Pinned items, Alt+1 to Alt+9 | Launch the Nth pin from the pin list (INP-05, existing) | none |
| Workspace step, Ctrl+RB / Ctrl+LB | The controller steps workspaces itself (DCK-05, existing) | none |
| Groups, workspaces, sessions as Radar results | Listed and run from Radar (`dockPaletteProviders`, `dockSearchProvider`) | none |
| Group shelf on the home | Drawn when its own setting is on; the dock only moves it up | none |
| **The dock panel** (create, switch and delete workspaces; create app groups; save and restore sessions; schedule an app; Radar actions per workspace; calm centre filter; the Desktop item) | **Reachable only from the rail's "..." button**: with the dock off none of it could be reached | Ground Control, Desktop & dock, "Workspaces, groups and dock settings" opens the same `DockPanelHost`, with the dock on or off; it installs the controller dialog hook itself (INP-20) |
| **Focus the dock, Ctrl+H** | **Closed Radar, the drawer, the puck, Launchpad or Ground Control, then focused nothing** (no rail came); the same on Glance with the dock on | `FocusDockRouting.noDock`: when no rail can appear, nothing is closed and a toast says why (dock off, or this landing draws no dock) |
| Right side = Dock rail, swipe or Ctrl+R | On a landing other than Glance with the dock off: nothing at all happened | The same toast ("The dock is off..."); since the second review it is the same route as Ctrl+H, so the drawer or Radar over the rail closes first |
| Ctrl+H or Ctrl+R while the home is being edited (second review) | The rail steps aside in edit mode, so nothing happened | A third reason, "Finish editing the home first..." |
| Add to dock (app menu, Radar) | Adds the pin; the pin is used by Alt+1 to 9 and when the dock is turned on | none (see below) |

## Decisions

- The panel row lives on the Desktop & dock page (the page that already holds "Dock on the phone"), reusing the rail button's label
  so a person recognises it. Opening it never turns the dock on (tested).
- Ctrl+H with no rail to come answers with a toast and closes nothing. It still consumes the key (the shortcut was meant for the
  launcher; passing it on would type an "h" into a focused Radar field).
- Finding the panel row (second review): a Ground Control pane is indexed by its title only, so "Dock and external display" was not
  found by "workspaces" or "groups" (in Ground Control or in Radar's settings results). The Desktop & dock page now also lists
  "Workspaces, groups and sessions", which opens the same entry.
- The rule (`FocusDockRouting.noDock`) mirrors `DockLayer`/`DockHost`: a landing that forces the rail (Glance with right side Dock rail)
  always has one; otherwise the landing must draw the dock and the dock setting must be on for the current tier.

## Owner observation (not changed)

- "Add to dock" with the dock off keeps the pin silently: it does work (Alt+1 to 9, and the rail when it is turned on), but nothing
  visible happens. Whether the menu should say "(dock is off)" or hide the row is an owner choice.

## Tests

`DockOptionalW24SmokeTest` (the real `MainActivity`: Ctrl+H with the dock off leaves the drawer and Radar open and toasts; on Glance it
toasts the landing reason; with the dock on it still closes the drawer and the rail takes focus; Ctrl+R with right side Dock rail and the
dock off toasts; on Glance it still reveals the rail), `DockDialogsW24Test.INP_20_withTheDockOffGroundControlOpensTheDockPanelAndControllerBClosesIt`.
Without the HomeScreen change three of the composed tests fail. Second review: `DockDeskReviewW24SmokeTest` (Ctrl+R closes the drawer over
the rail; Ctrl+H closes the Recents panel the rail steps aside for) and `DockDeskReviewW24RulesTest` (the edit-mode reason, settings
search, the toasts' Ground Control path in nine languages).

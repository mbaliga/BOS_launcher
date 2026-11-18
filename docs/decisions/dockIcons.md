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
| The dock itself | Edit dock, Workspaces and groups, Dock settings, Dock at the bottom/left/right (landscape phone only), Hide dock (not on the desktop surface or a forced rail), About the dock |

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

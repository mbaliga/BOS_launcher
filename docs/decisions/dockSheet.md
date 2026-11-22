# The dock sheet and the Settings Dock page (wave 55)

Owner request: "The dock' popup options should instead be like the sharesheet style, pushing up the screen so the top is outside the viewframe, and you get a wysiwyg editor. The settings does not have the dock style and some other options visible there."

## What replaces what

- The dock's long-press popup menu (Edit dock, Workspaces and groups, Dock settings, Dock style, Dock at ..., Hide dock, About the dock) is **deleted** (`DockMenuModel.dock`, the dock-level popup, the Dock style dialog and chooser, the "Edit dock" popup route, the panel's Dock page). A long press on the dock background (not on an icon), the menu key / Shift+F10 and the TalkBack action "Dock options" open the **dock sheet** instead.
- An **icon's** long press is unchanged: a small context menu (shortcuts, App info, Move earlier / later, Replace, Remove, Pin). The owner asked for exactly that.
- Home screen > Dock style is gone. The style has one place in Settings: Desktop & dock > Dock.

## The sheet (`DockSheet.kt`)

- A bottom sheet in the share-sheet shape (grabber, centred 20 sp title "Dock", 28 dp physical top corners, the theme's background so it reads in light and dark), at most 640 dp wide and centred, at most 62% of the window high, scrolling inside. No scrim: the dock above is the editor.
- While it is open the **whole home** (`DockSheetHost` wraps `HomeScreen` in `MainActivity`, and the desktop surface's dock) is translated up by the sheet's own height minus the navigation-bar inset the dock already clears, so the dock sits directly above the sheet with its own 8 dp margin and the top of the home is outside the viewframe. One `Animatable` progress (0 to 1) drives the sheet's slide and the home's push (a spring; a snap with reduced motion), so dragging the sheet down pulls the home back with it. The push follows the measured size live: Edit dock shrinks the sheet and the home follows.
- **Landscape**: a dock on a side gets a **side sheet** on the dock's side (the dock's edge follows the layout direction, so "Left" is the start edge; the sheet uses physical edges and corners), at most 380 dp or 46% of the width, full height; the home is pushed sideways by its width so the dock stays beside it. A bottom dock in landscape (and portrait, tablets, desktop) gets the bottom sheet; tablets and desktop cap and centre it. Choosing another Position card moves the dock and the sheet follows to its side. The geometry is pure (`DockSheetGeometry`) and tested for LTR and RTL.
- While open, the home underneath is **not touchable**: a tap on it closes the sheet (and presses nothing under it); in edit mode only the dock's own bounds (read live in the home's coordinates, so a push in progress does not matter) take touches, so a tap on a home icon cannot launch an app mid-edit. This is a pointer filter on the home (`blockHome`), not a scrim.
- Close: drag the header (grabber and title) toward its edge past 70% or a fast fling; a short drag springs back; a drag from scrolled-to-top content is not used (header only). Back, Esc and the controller's B close it; in edit mode they first leave editing. TalkBack: pane title "Dock", the title is a heading. The dock dialog hook (`DockInput.dialogHook`, the shared focus layer) is composed inside the sheet and inside the About dialog; where a real dialog window exists its controller window hook applies, and the sheet itself handles Esc and B on its root, since an in-composition sheet has no window of its own.

### Content (the same `DockOptionsBody` as the Settings page)

Three entry tiles (Edit dock, Workspaces and groups, About the dock) like the Home quick sheet's, then drawn cards: Dock style (Standard, Transparent, 3D: the dock's own miniature drawing), Position (Bottom, Left, Right; portrait says it is always at the bottom and that the side applies in landscape; absent on a surface that fixes its edge), Mode (Collapsed band, Pinned and recent); then switches: Show the dock (absent where the dock is forced), app group chips on the home, Desktop item in the dock; and "Use the default for this device" while the owner has chosen on or off. Every control writes the one store (`WorkspaceManager.setDock`, `setPins`) and the real dock redraws at once.

### Edit dock (in place)

The sheet shrinks to a compact bar (title "Edit dock", one hint line, a primary Done button) and the rail becomes the editor: recents and the enable row are hidden, a collapsed band is expanded, every pinned icon wiggles a few degrees (an infinite loop that asks the motion preference: reduced motion draws it still) and wears an x (32 dp touch area, "Remove %s from the dock"), a plain drag reorders (no long press first; the drop index is the same `DockReorder` maths as before), a ring with a plus is the first tile after the Home glyph and opens the app picker as a page. An empty dock shows only the plus while editing. Tapping an icon does not launch it while editing.

## What was found in the dock model (audit for the Settings page)

`DockSettings` holds exactly five values: `enabled` (null = device default), `mode`, `edge`, `groupShelf`, `style`. There is **no** label, size, icon size, recents count, auto-hide or haptics setting in the code: the dock draws 40 dp icons only (docs/decisions/dockIcons.md), recents are capped by the constant `recentLimit = 6`, and haptics are the launcher-wide vibration setting. So "show labels" has no meaning today and is not offered. Before this wave the Settings page showed enabled, mode, edge, Add apps, the Desktop item and Use the default; **missing**: Dock style (it lived under Home screen), the group-chips switch (it lived only under App groups) and Edit dock. A test (`DCK_04_page_34`) fails when a field is added to `DockSettings` without a control.

## Settings > Desktop & dock > Dock

A live preview first (the real `DockRail` drawn with the current style, position, mode and pins, as initials tiles, inert; four sample tiles and a caption when nothing is pinned, since the real dock would be hidden), then `DockOptionsBody` (the same cards and switches), then Edit dock (the older list page, kept here because a settings page has no dock to drag on), Add apps to the dock, the Desktop item row, and (inside the panel) Workspaces and App groups, then Use the default. The mode choice is two cards instead of a radio page; position is three cards instead of a segmented control.

## Related fix

The wide app picker answers with package names (it also feeds the calm centre, which stores packages) but dock pins store app keys. "Add apps to the dock" therefore dropped every existing key pin and stored the new ones by package, which the rail could not resolve. `DockPinPicking` maps the pick back to keys; tested through the sheet's plus tile and the Settings page.

## Workspaces and groups

The sheet's tile closes the sheet and opens the panel page (`DockPanel`), which is now only Workspaces (first) with a row on to App groups. The panel's Dock page is deleted: its content is the sheet and the Settings page.

## Not built / limits

- No scrim and no blur behind the sheet (the owner wants the dock visible).
- Dragging from the scrolled content area does not close the sheet; only the header drags.
- Focus does not return to the dock rail when the sheet closes (the controller focus request is not available from the sheet).
- A surface with no `DockSheetHost` above it (a preview, a test of the rail alone) offers no sheet.
- The wiggle is a few degrees of rotation only; no jiggle on the x badge.

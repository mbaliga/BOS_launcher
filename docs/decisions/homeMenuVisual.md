# Wave 51 homeMenuVisual: the Home quick sheet as visual cards

Owner request: "I want the settings options to be visual as far as possible", shown on the long-press Home sheet with its text pills.

## What changed
- Every choice is an `OptionCard` (`HomeQuickSheetCards.kt`): a 64 dp tile with a Canvas illustration (no bitmaps, themed: white, soft white, faint, accent for the ring) over a 12 sp label. The selected card has a 2.5 dp accent ring and a check badge; the card is a `selectable` radio button and the visible label is its text, so TalkBack reads the same label as before and the selected state is in the semantics. The old `Chip` pills, `ChipRow` and the outline tokens are deleted.
- Illustrations: Pages = phone with a grid / grid and dots / columns running off the edge with an arrow; Order = three page tiles and an arrow, drawn in PHYSICAL coordinates so Right to left points left whatever the app language (the sheet's layout itself mirrors in RTL); Hotspots = phone with dots in the corners the preset turns on; This page = phone with the content block at the top or bottom, a house for Make this home; Transition = two-card pictures for Slide, Fade, Zoom, Cube, Flip and Stack.
- Groups are wrapping `FlowRow`s of 80 dp cards (no hidden sideways scroll, so a card is never off screen; font scale 2 wraps labels to up to three lines). Cards are at least 48 dp in both directions. The focus ring (white 2.5 dp) shows for keyboard and controller focus.
- Three entry tiles on top: Add apps, Add widgets, Edit home. They call the edit tray's own entry points in `HomeScreen`: `editHome()` (turns Pages on first when they are Off), then `addAppOpen = true` (the app picker the tray's Apps button opens) or `homeItems.widgetRequest++` (the widget picker the tray's Widgets button opens). Nothing of the widget browse itself was touched.
- Delete page stays a plain red text row (a destructive action, not a choice). Wallpaper and Ground Control rows are unchanged.

## Not done
- No looped animation on the selected card: the illustrations are static (the owner called it a bonus), so no infinite animation exists to gate on the motion prefs.
- Keyboard focus uses Compose's own focus on the cards (the sheet is a popup window, outside the home's `launcherItem` focus graph, and the old pills did not use it either).
- Other sheets: none of the other pill sets in the app/ui files is a small fixed set of options (the theme preview chips in `ThemesUi.kt` are colour swatches, not choices), so none was converted.

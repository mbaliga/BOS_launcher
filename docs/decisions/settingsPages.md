# settingsPages (wave 46, CFG-21)

> **Wave 57 (settingsTabs): the "category page" title band below is now the one Settings home title band (a section has no page title of its own in portrait; in landscape the end pane carries the section's title) (`docs/decisions/settingsTabs.md`).**

Owner feedback: "Use full page menus where there are popups, not when the Options are outside already! Why is the left page options outside as well as inside? this is there for many options. Move the settings title to the centre of the empty space."

## One presentation per option set, never two
Each kind of settings row offers its options in exactly one way (`OneUiModel.presentation`, a pure table tested on the JVM):
- **Choice with a rail of preview cards** (Left page, Comms theme, Icon shape, Home style and every other `SettingItem.Choice` with `preview`): the cards are the control. The row is the title and its hint over the cards and nothing else: no chevron, no page of its own, no caption repeating the picked value under the cards (`OneUiRailRow` lost its `onOpen`, its chevron and its caption; the picked value is still read out in the row's description). Before, the same options were also a radio page behind the title's chevron.
- **Choice without cards**: the title, the hint, the value in grey at the end and a chevron; a tap opens a full settings page (`OneUiSubPage`: back arrow, large collapsing title, the one-line hint under the title, the options as radio rows in one rounded card). Picking a row applies it and returns; the back arrow returns without changing anything.
- **Segmented** (two or three short options, `OneUiSegmented`): inline, no chevron. No `SettingItem` kind produces it today; the dock and groups pages use it directly.
- Switch, slider, stepper: inline. Action: a link (a confirm dialog first when destructive). Pane: a page of its own.
No presentation is both inline and a page (`Presentation.inline` excludes `chevron` and `opensPage`).

## Popups versus pages
Dialogs stay only for **confirmations** (`OneUiConfirmDialog`, the wallpaper power warning, backup confirms) and **short text entry** (`OneUiTextEntryDialog`, the schedule and session dialogs). Context menus (three-dot per item, the dock's long-press menu, the page editor menu) stay menus.
Every picker is a page now. `OneUiRadioDialog` and `OneUiCheckDialog` are replaced by `OneUiRadioPage` and `OneUiCheckPage` (`OneUiPickerPages.kt`): a full-screen window (`OneUiFullPage`, like the dock panel of wave 41, so a pane that sits in a list can open one) hosting `OneUiPageFrame`. The radio page applies on tap and returns; the check page has checkbox rows and a Done button (off outside min and max), the back arrow leaves without applying. Converted: the dock's Dock mode, what the calm centre shows, Apps of which group, Radar actions, the app pickers of the dock (`AppPicker`), the desktop pane's "when a display connects" and "when it is unplugged", and the dock's long-press "Dock style" (`DockStyleDialog`, tiles on a page). Tags (`oneui.dialog.option.N`, `oneui.dialog.confirm`) are kept, plus `oneui.picker.page`.

## Title position
The large title (and its one-line subtitle) is centred vertically in the band between the top bar and the first row (`OneUiModel.centredTop`, used by a layout modifier of the title item), left aligned as before. The band keeps its height (a third of the window, 172 to 260 dp; 112 dp for the list of the landscape two-pane), so the title leaves the band exactly as before when the list scrolls: it moves up with the item and fades while the small centred title fades in. In landscape each pane centres in its own band. The floating preview window's default anchor is top-end under the bar; the title is at the start, so they do not meet at the default position.

## Not converted, on purpose
- `GlassDialog` editors (corner editor, gesture binding, the single app picker `AppPickerDialog`, the tile menu, the drawer shortcut editor): full-screen pages with a small centred title and a text-button footer; they hold forms and a searchable list, not an option set, and keep their chrome (no large collapsing title).
- The Launchpad rules editor's condition-kind `DropdownMenu` (feature-launchpad, Comms, Grouping and rules) and the group assign sheet: that module has its own look and does not depend on feature-grid. Still a dropdown.
- The Home sheet, the folder rename, the alias and tag dialogs, Radar's own scope menu.
- "Edit dock" (a list editor) and the schedule dialog (a time stepper) stay dialogs: short entry, not option sets.

## Undo
Revert the wave 46 commit: rails get their chevron, caption and second page back, pickers are dialogs again, the title sits at the bottom of its band.

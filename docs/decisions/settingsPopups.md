# settingsPopups (wave 41, CFG-21, WSP-05, WSP-07, WSP-08, DCK-04)

Owner feedback: "Workspaces are not intuitive to configure." and "The popups look ugly af in setting. Please follow oneUI better." The screenshot was the dock panel: a floating rounded card over the Desktop & dock page (its big title clipped behind), chips for choices, purple text links, a bullet list of workspaces and a cryptic "Calm centre shows all apps / Choose" row.

## What a workspace is here
A named home setup: its home pages (and what was removed from them), its dock, its widget slots (left page and corners), the Radar actions pinned for it, and which apps the calm centre shows while it is active (`Workspace`, `WorkspaceManager`). App groups, sessions and scheduled launches are **shared** by all workspaces. Switching saves the arrangement being left and applies the one entered (WSP-07); it is reachable from the Workspaces page, Radar ("Switch to workspace") and the dock menu.

## Popups
- **Pane = page.** A pane already opened as a page in the wave 40 host; the floating card was a dialog the pane's "Workspaces, groups and dock settings" row opened. That row and the dialog are gone: the dock panel (`DockPanel`) is a full page in the same chrome (`OneUiPageFrame`), reached from the dock's menu and chip, and Ground Control, Desktop & dock has the same three pages as panes: Dock, Workspaces, App groups. `SettingItem.Pane` gained `bare` (the pane lays out its own cards) and `pageSubtitle` (the live "Active: Home" under the large title).
- **Dialog** (`OneUiDialog`): centred, 28 dp corners, centred 20 sp title (a heading, the card is a pane titled for TalkBack), grey 15 sp body, flat accent text buttons in a row, 56 dp, hairline above and between, a destructive primary in red, never filled. `ConfirmDialog`, the Ground Control confirm, the wallpaper power warning, the backup confirmations, the schedule, session and group dialogs, "About the dock", "Edit dock" and the dock style dialog use it. A dialog hosts' own window hook (the dock's controller layer, INP-20) comes through `LocalOneUiDialogHook`.
- **Choices**: 2 or 3 short options are a segmented control (`OneUiSegmented`, rule `OneUiSegmentedRule`: at most 3 options, at most 14 characters each: dock position, group layout); more or longer ones are a value row with a radio dialog (`OneUiRadioDialog`, 56 dp rows, the pick applies and closes: dock mode, what the display does on connect and unplug, the calm centre); several picks are a checkbox dialog (`OneUiCheckDialog`, min and max: apps, Radar actions).
- **Text entry** (`OneUiTextEntryDialog`): one underlined field, keyboard raised, the text selected, confirm off while blank or while an `error` is returned (a workspace name already used).
- **Menu** (`OneUiMenu`, `OneUiMenuRows`): 16 dp corners, raised, 48 dp rows, no dividers, destructive red. The workspace and group rows' three-dot menus, the dock's long-press menu and the page editor menu use it.
- **Sheet** (`OneUiSheet`): 28 dp top corners and a grabber; the Home sheet keeps its black look and scrim through it.
- **Full-screen editors** (`GlassDialog`: app picker, corner and gesture editors): the wave 5 gradient and waves are gone; they are a One UI page (back arrow, centred title, the content, a flat text-button footer under a hairline through `LocalDialogFooter`). The layout is no longer forced left-to-right.

## Workspaces page
Explanation card ("Workspaces are separate home setups, like Work and Personal. Each has its own home pages, dock, widgets and calm-centre apps. Switch between them here, from Radar or from the dock's menu."), then one card per workspace (name, "3 pages · 5 dock items", an "In use" badge on the active one, a menu: Make active, Rename, Duplicate, Delete with a confirm; the last workspace cannot be deleted so it has no Delete), a prominent "Add workspace" button (name, then "Start from: an empty home / a copy of <active>"), then "What the calm centre shows" and "Radar actions" for the active workspace. Tapping an inactive card switches to it. The page title area says "Active: <name>".
- **Copy** takes the arrangement (the live one for the active workspace, since the stored one is saved on leaving), dock, Radar actions and calm-centre apps; **not** the widget slots, which are hosted widget ids two workspaces cannot own.
- **Calm centre choices**: All apps; Only apps I choose (picker); Apps of a group (the group's packages copied once, said in the dialog). "Only this workspace's apps" from the brief is not offered: the model has no per-workspace app set to follow, and a snapshot of the dock's apps would be a new feature. A group choice only appears when a group exists.
- **App groups page**: list (apps and layout, "In the dock" when pinned), per-group menu (Open, Pin or Unpin, Save session, Remind me, Delete with a confirm), New group, the home shelf switch, Schedule an app, saved sessions (Restore, Delete).

## Removed
The dock panel's floating card, its chips, purple link buttons, the "Workspaces, groups and sessions" row and "Dock panel" entry in Desktop & dock, the "Dock on the phone" three-row radio list (the Dock page's switch plus "Use the default for this device" replace it), `Heading`/`Hint`/`Btn`/`Chips` of the old panel, the pill buttons in dialog footers.

## Not done, on purpose
The Home sheet (black quick settings), the folder rename, the alias and tag dialogs, the Radar pickers and the widget picker keep their own looks: they are not opened from the settings. `DropdownMenu` of other screens is untouched.

## Undo
Revert the wave 41 commit: the panel goes back to the floating card.

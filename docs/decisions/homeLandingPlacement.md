# Home items placed from a landing without pages; workspace switch in edit mode (wave 24, stream w24-homeTests)

Context: since wave 16 the default landing is Glance, and only Selected apps has pages (`HomeLandingRules.hasPages`). Widgets, stacks, folders, shortcuts, links, groups and the Files, Usage and Timeline tiles all live on those pages.

## 1. Placing an item from Glance, Recents or All apps switches to Selected apps

Found: the long-press quick sheet offers "Widgets, folders and icons" on every landing. On Glance, picking a widget, a folder, a link, a shortcut, a group or a panel tile placed it on the current Selected apps page while Glance stayed on screen. The item was saved, but there was nothing to see and no hint where it went. Dropping a link or an app onto the Glance home did the same. Test written first: `HOM_15_onGlanceAFilesTilePlacedFromTheQuickSheetSwitchesToSelectedAppsWhereItIsShown` (HomeLandingItemsW24Test), which failed with the landing still Glance.

Decision: placing an item from a landing without pages switches to Selected apps. This is the same rule "Edit home pages" has followed since wave 12 (`HomeLandingRules.editHomePagesLanding`). The pure rule is `HomePlacementLanding.after` in core-data. `HomeItems.onPlaced` runs after every add (widget, folder, group, link and panel tile, shortcut, dropped items), and a dropped app also calls it. HomeScreen sets the hook in one line. Second pass: the drawer's app menu "Add to home" (shown whenever the home style is paged, so also on Glance) and a second "add" of a panel tile that is already on the pages (which used to return without doing anything) now call it too; tests `HOM_02_onGlanceAddToHomeInTheDrawersAppMenu...` and `HOM_15_onGlanceAFilesTileThatIsAlreadyOnThePages...`, both failed before the change. Items opened from the sheet that place nothing (icon pack, hidden apps) and filing into an existing folder do not switch.

The other options were not taken. Hiding the entry on Glance would remove the only way from Glance to the widget picker. Staying on Glance with a notice would still leave the item invisible. Undo: drop the `onPlaced()` calls, or make `HomePlacementLanding.after` return null.

Owner check (device): whether this switch feels right, or whether a notice that leaves Glance on screen is preferred.

## 2. A workspace switch during edit mode ends edit mode

Open since wave 13: a workspace switch during edit mode replaced the layout through the workspace hooks (`applyHome`), and the edit session recorded that as an edit. One Undo would then write the old workspace's pages into the new workspace. The wave 13 note said no rebase hook reached the hooks without a larger change in `DockLayer.kt`.

Decision: the edit session ends when it sees the pins of another workspace. Leaving Selected apps already ends it the same way (wave 12). The pin list and the layout reach `HomeEditState` as two separate updates, in either order. The session ends on the first sign of the new workspace id, and an ended session records nothing, so both orders are covered and `DockLayer.kt` is not touched. The rule is `EditWorkspaceSwitch.endsEdit` (core-catalog), called from `HomeEditState.observePins`. Leaving the session settles the edit graveyard as Done would: what was removed in the left workspace stays removed, and its arrangement is captured with the removal.

Tests: `EditWorkspaceSwitchTest` (JVM) and `HOM_12_aWorkspaceSwitchDuringEditModeEndsEditModeInsteadOfBecomingAnUndoableEdit` (whole app; failed before the change).

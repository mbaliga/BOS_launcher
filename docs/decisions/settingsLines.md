# settingsLines (wave 54, CFG-21)

Owner: "just remove the boxes and use line separators instead."

## Decision
Settings rows no longer sit in rounded filled boxes. A group of rows is flat on the page background: the small grey section header above it names it, hairlines (`HyleColors.Hairline`, 1 dp) separate the rows, and 28 dp of empty space separates groups. This supersedes every "rounded card" statement of `settingsOneUI.md` (wave 40) and `settingsPages.md`/wave 46 about rows (the root list, category pages, panes, radio and check pages, the search results, the dock's workspaces and app groups pages, the Desktop displays pane, the Home items menu list).

## What it is
- `OneUiRows` (was `OneUiCard`): a full-width column with no clip and no fill, test tag `oneui.group`, still a semantic list (`CollectionInfo`). `OneUiGroup` (public face) is the same.
- Rows pad 24 dp at the start and end (`OneUi.RowPad`); the section header and caption use the same 24 dp, so headers, text and dividers share one left edge.
- `OneUiDivider` (tag `oneui.divider`) starts where the text starts: 24 dp, or 24 dp plus the 56 dp tile advance on a root row; it ends 24 dp before the end edge, mirrors in right-to-left, and is only put between rows, never after the last row of a group (Hidden apps rows too).
- Press, hover-less keyboard focus and "lit by a search result" highlights are full-width row fills as before; a keyboard-focused row also gets a 2 dp accent ring (`oneui.focusRing`).
- Kept: the coloured icon tile on root rows, the switch, slider, stepper, the 56 dp rows with 48 dp targets, the preview cards inside a choice row (cards inside rows stay cards), the rounded search field (a control), dialogs (`OneUiDialog` is a floating surface), menus, the primary button, the active badge.
- `OneUiModel.Block.Card` is `Block.Group` (the cut logic is unchanged: a new group starts at a section header, where controls change to links, and around a row of preview cards).

## Removed
`OneUi.CardShape`, `OneUi.Card` (replaced by `OneUi.Field` for the search field), `OneUi.CardGap` (now `GroupGap`, 28 dp), the pane wrapper's filled rounded box, the info card's box, `OneUiCard`. No test asserted a card shape or background.

## Not changed
The inline "Unhide" confirmation line of Hidden apps keeps its tinted rounded notice (it is a transient message, not a row container). Screens outside Settings with their own surfaces (Comms, Radar) are untouched.

## Undo
Revert the wave-54 commit.

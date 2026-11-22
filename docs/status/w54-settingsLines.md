# Wave 54 settingsLines status

Owner: "just remove the boxes and use line separators instead." Decision: `docs/decisions/settingsLines.md`.

| ID | Status | Note |
|---|---|---|
| CFG-21 No row box | done (Robolectric, no device) | `OneUiRows` has no clip and no fill; panes, the info text and the one-pane categories lose their rounded wrapper; `OneUiCard`, `OneUi.CardShape`, `OneUi.Card`, `OneUi.CardGap` removed |
| CFG-21 Hairlines | done | `OneUiDivider` between rows only, 1 dp `HyleColors.Hairline`, inset to the text start (LTR and RTL), ends 24 dp before the end edge; Hidden apps rows got hairlines |
| CFG-21 Headers and spacing | done | grey header above each group, 28 dp between groups, 24 dp row padding shared by header, text and divider |
| CFG-21 Highlight and focus | done | full-width press/selected/search fill kept; keyboard focus draws a 2 dp accent ring (tag `oneui.focusRing`) |
| CFG-21 Semantics | kept | a group is still a list with its row count |
| Shared pieces | done | `OneUiGroup`, `OneUiRowDivider`, `OneUiGap`, `OneUiInfoCard` keep their API, so the dock pages, Desktop displays pane and Home items menu follow without edits |
| Strings | none new | |

## Tests
- New `SettingsLinesW54Test` (feature-grid, Robolectric, 21): pixel probe of every group on root dark and light, category, radio, search results and a pane (corners and edges equal the page colour); divider counts equal rows minus one per group (root and category) and a group's `CollectionInfo`; divider start equals text start LTR, mirrored RTL, and on a root row equals the title after the icon tile; divider is 1 dp and the hairline colour; focus ring appears on the focused row and moves; search results are one flat list; 8 screenshots (root, category, pane, radio, dark and light) were read, the dividers were made to stop short of the end edge after the first reading.
- `OneUiModelTest`: `Block.Card` renamed `Block.Group` (assertions unchanged).
- No existing test asserted a card shape, size or background.

## Not matched / device-only
- Nothing is device-verified: the look on a real screen, the press ripple against the page, TalkBack reading, a hardware keyboard or controller moving the focus ring.
- The Hidden apps inline Unhide confirmation line keeps its tinted rounded box (a notice, not a row container).
- Panes that draw their own content (not through `OneUiGroup`) keep whatever internal styling they have; only the host's box around them is gone. Pane files owned by the choice-cards stream were not touched.
- Dividers inside a few custom panes are only where those panes call `OneUiRowDivider`.
- `SettingsOneUiUiTest.CFG_21_theRootListsEveryCategoryUpFrontWithItsSummaryAndNoTabsOrPuck`: asserted that the first seven root rows are on screen in the 411x891 window; the wider 28 dp group gaps (was 12 dp between cards) push the seventh below the fold, so it now checks the first six.

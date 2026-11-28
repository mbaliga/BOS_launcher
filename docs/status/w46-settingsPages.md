# Wave 46: settingsPages (CFG-21)

| ID | Status | Note |
| --- | --- | --- |
| CFG-21 | built | Rows with preview cards show the cards only (no chevron, no second page, no caption under them); a choice without cards opens a full radio page with the hint under the large title (never a dialog); `OneUiRadioDialog` and `OneUiCheckDialog` became full pages (`OneUiRadioPage`, `OneUiCheckPage`) and every caller was moved (dock mode, calm centre, group, Radar actions, dock app pickers, the desktop pane, the dock style chooser); the large title is centred vertically in its band on the root, category pages and choice pages, per pane in landscape. See `docs/decisions/settingsPages.md`. |

## Tests
- `OneUiPagesW46ModelTest` (JVM, 4): the presentation table for every kind of row, no presentation both inline and a page, centred-title maths.
- `SettingsPagesW46Test` (Robolectric, 13 plus 5 screenshots): rail rows have no click on the title row, only their cards are clickable, no repeated value caption; a card applies and no page opens; a plain choice opens a page with no dialog node, applies and returns; back returns unchanged; a check page (Done, back leaves); deep link to a row with cards still lands on its page; the title is centred within 2 dp on the root, a category, a choice page with its subtitle and in both landscape panes; the title still collapses into the small one on scroll.
- Updated: `OneUiPopupsW41Test` (radio and check tests now assert a page, not a dialog).
- Known failing before and after this change (verified on the unmodified branch): `GlanceHomeUiTest` x2 and `HomePagesW26ScreenshotTest` x2 (stale Recents indicator tags).

## Screenshots (dark, read)
`feature-grid/build/screenshots/w46-*.png`: root (the "Settings" title in the middle of the empty band, search below), Comms (Left page and Comms theme show titles, hints and cards only, no chevron, no caption), a choice page (Icon shape: title, one-line hint, four radio rows in one card), landscape two-pane and landscape choice page (each pane's title centred in its band).

## Not matched, device-only
Nothing here is device-verified. Not matched: One UI's own scroll and collapse feel, the exact motion of the title leaving its centred position (it moves up with the list and fades), the full-screen picker window's enter animation, TalkBack reading of the picker pages, and the pickers listed as not converted in the decision note (GlassDialog editors, the Launchpad rule editor dropdown).

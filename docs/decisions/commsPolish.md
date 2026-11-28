# Wave 30 commsPolish: header alignment and a clear read/unread distinction

Owner: "The comms text and three bars are misaligned. In comms, there is no visible distinction between read and unread."

**Supersedes** the sentence "Read rows are quieter: normal weight, headline in the muted ink, the second line dimmer" of [commsRead.md](commsRead.md), and the header glyph size (12.8 dp) of [commsLayout.md](commsLayout.md).

## Header
- Cause: the title was lifted 1.6 dp to match the mock-up's title centre (y about 92 mock px) while the glyph sits at 93.5, so the bars looked about 6 px low in the owner's crop.
- The menu button and the title are laid out together (`CommsHeaderPair`): the title is centred in the 48 dp row, the button is placed so that its middle (the glyph's middle bar) is on the middle of the title's capitals (first baseline minus half the cap height), whatever the font's line metrics. The 48 dp touch square stays, centred on the glyph.
- The glyph is as high as the capitals (0.711 em of 17 sp, so it follows the font scale) and 1.1 times as wide, bars 2 dp. Left edge 32 dp, title starts at 57.7 dp, as in the mock-up; right-to-left mirrors.
- The pair sits 0.9 dp above the row's centre: the one common centre that keeps both elements within the mock-up's 2 px tolerance (mock glyph centre y 94, mock title centre 90.5). The existing CML geometry tests pass unchanged (glyph 80-106, title 79-106 at mock y 80-108 and 77-104); the owner's alignment wins over the mock-up's 3.5 px offset between them.
- Measured (`CommsPolishHeaderTest`, 411 dp, 352 dpi): glyph and title ink centres are within 1 px at 100, 75 and 125 percent, light look, RTL and font scale 2.

## Read versus unread (every Comms list: live, history-only, UNREAD, READ, digest)
| | unread | read |
|---|---|---|
| headline | full ink, SemiBold | ink at 60 percent over the page, normal |
| second line | secondary ink | ink at 45 percent |
| time | accent ink (accent moved toward white/black until 4.5:1) | ink at 45 percent |
| icon | full | 55 percent opacity, saturation 0.6 (one shared ColorFilter) |
| dot | solid 8 dp accent dot in the left margin, centre 10.9 dp from the edge, on the icon's centre | none |
| TalkBack | state description "Unread" | "Read" |
- No stripes, no date bands; the layout does not move (the dot sits in the existing 32 dp margin). A collapsed group or the digest with an unread item inside shows the same dot on its header (`lp.group.unreadDot.<key>`).
- Flipping (open, tap, Mark as read) cross-fades colours and icon opacity in 150 ms (instant with reduced motion); the weight and the dot switch at once.
- Contrast (JVM test `CommsPolishContrastTest`, dark and light): read headline 4.5:1 or more (text); read second line and time 3:1 or more (secondary text, WCAG large/secondary); accent time and dot 4.5 and 3. All values are `CommsStyle` tokens (`CommsRowTone`).
- Tags: live rows `lp.row.unreadDot.<key>`; history view rows `lp.row.unreadDot.<entry id>`.
- Not done: the app module's `HubPanel` rows (other stream) were not restyled (done in wave 33, see [commsDebt.md](commsDebt.md)); device TalkBack and real-display contrast are not verified.

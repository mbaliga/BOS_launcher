# Decision: the page editor layout, a strip of small cards above the live card (wave 45, owner feedback)

**Status: built (JVM and Robolectric tested; nothing is device verified).** Affects HOM-12, HOM-01 in `docs/Spec.md`. Supersedes the portrait side-strip layout of `pageEditor.md` (wave 36) and the hamburger of `editorDebt.md` (wave 37); the swing, the Glance card, the Add card, drop-on-card, Move earlier / later and Delete with confirm and Undo are kept. Research: `docs/research/home-editing.md` (items 10 to 15, 2d, 2e, stream D).

## What the owner said
"how the home and pages should look like while being edited ... the editing experience is bonkers." The recording of the wave 36/37 editor showed the live page card cropped half off the right edge, a tiny vertical strip, a left column of four tabs and a Settings text block, a floating three-circle rail, a stray grey circle and four dim bottom pills. Answers given: in portrait a HORIZONTAL strip of shrunken page cards above the live card, tabs and their content BELOW the live card; the side strip only for landscape and tablets (600 dp wide and more); the live card NEVER cropped; the original mock-up idea stays (pages as a strip, tabs with content, tap a shrunken page to swing it into focus, still shrunken).

## The layout now (`PageEditorGeometry.layout`, pure)
Portrait (narrower than 600 dp, taller than wide), top to bottom:
1. A 56 dp slot for the edit chrome's top bar below the status bar inset (`Layout.topBar`). **Integration point:** `PageEditorHost(topBar = { ... })` draws into it; nothing is drawn there until `w45-editChrome` lands (its bar then goes in, or it keeps drawing over the slot from `HomeScreen`).
2. The strip: cards 64 x 112 dp (scaled down to 0.8 on windows shorter than 740 dp), 8 dp gaps, scrolls sideways, the focused card centred at scale 1.0 with a 2 dp accent outline, neighbours at 0.9 and 70 percent opacity. Glance first (house glyph, no menu), every page (number, tiny preview of its icons, three-dot overflow), then a dashed "New page" card.
3. The live card: the home's own layer scaled to at most 0.88 (or what the room allows), the aspect of the window, centred, always completely inside the window (`HOM_12_pe01b` runs 320x568 to 412x915, font scales 1, 1.3 and 2, with and without the bottom bar's room).
4. The tab row (Apps / Widgets / Folders / Settings as text tabs; the selected one bold with a 2 dp accent underline) and the tab content panel under it, a fixed-height panel that scrolls inside itself. The panel is 30 percent of the window height, but gives way first so the live card keeps at least 0.38 scale (about 21 percent on 411x891 while the bottom bar's 68 dp are still reserved; about 29 percent without them); its floor is 80 dp. The tab row grows with the font scale up to 1.5 times and scrolls sideways beyond.
5. Bottom: the room of the existing edit bar (`BOTTOM_BAR_DP = 68`). **Integration point:** set it to 0 when the chrome's bar replaces the four pills; the live card then grows.

Landscape and windows 600 dp wide or more (tablets): a vertical strip at the end side (the start side with the right-to-left language or page order), the live card beside it (at most 45 percent of the width), the tab row and the tab content in the remaining start side, full height. Paginated and Continuous use the same layout.

## What was removed
The floating three-circle rail (its Move earlier / Move later / Delete page are in the card's overflow menu, in the long-press drag and as TalkBack actions), the Delete page button in the Settings tab, the hamburger menu (Undo, Redo, Done are the edit bar's; Add page is the "New page" card and the bar), the empty left column, the cropping (the card was pushed 4 percent off the edge), the vertical stacked tabs and the "Page editor menu" button. The strip's focus card is no longer an empty outline: it shows the page like the others, because the live card is no longer in its place. The wave 36 stray grey circle was not found in the code as a drawn item (the ghost seam draws only while a drag runs); nothing was removed for it beyond the old strip.

## Reorder
Long press a page card (haptic LongPress), drag along the strip: a ghost of the card follows the finger, its place dims, an accent insertion line shows between the two cards it would land between (`PageEditorGeometry.dropIndex` rounds the travel to whole card pitches, `insertionGap` places the line), a haptic tick on every slot change, drop commits through `HomePageOps.moveTo` (page groups that overflowed move together). The drag reaches the cards visible in the strip; there is no edge auto-scroll yet, so for a far target use Move earlier / later. Keyboard: arrow keys move focus between cards (Compose's directional focus search), Enter shows the page (the card's click), Shift with the along-strip arrows moves the focused page one place (mirrored in a mirrored row). TalkBack: each page card is a button "Page N of M, selected" (": empty" when it is) with custom actions Move earlier, Move later (only where possible) and Delete page; the reorder is announced ("Page moved to position N of M", polite live region). The Glance card and the New page card have no actions.

## Page identity
The Settings tab says which page it is: a heading "Page 2" (pages have no names of their own, so the name is the number) and "Page 2 of 4". The wallpaper switch "Pages use the same wallpaper as Glance" sits under an "All pages" heading with the hint "Applies to every page and the scroll, not only this one": it is the one global setting it always was, now labelled as such.

## Swing
Kept: 350 ms FastOutSlowIn, the reduced-motion cross-fade of 160 ms. It now starts from the tapped card's CENTRE relative to the live card's centre (the strip is above, so the live page rises from the strip), at the strip card's size relative to the live card (`Layout.swingFrom`, about 0.4) instead of 0.92, and tilts about the axis of the larger offset only.

## Continuous
Unchanged behaviour (`editorDebt.md`): a tap selects the page, swings it in and scrolls the surface to its first column; scrolling the surface moves the selection.

## Not matched / honest limits
- On the phone the live card is small (about 0.35 to 0.4 of the window at 411x891, smaller on 320x568) because the strip, the tab row, the panel and the top bar all take their share. It is whole, centred and editable; it is not the 0.88 the single-page edit mode shows. The bottom bar's reserved room (`BOTTOM_BAR_DP`) is the biggest lever.
- The overflow target is 48 x 32 dp (the card is 64 dp wide and 112 dp tall): narrower in height than 48 dp. A 48 dp square would cover a quarter of the card.
- The strip card previews are tiny icon grids, not screenshots.
- No auto-scroll while dragging a card to the strip's edge.
- Page "selected" is both the card's selected state and part of its description, so a screen reader may say it twice.
- Device only: the swing feel, the drag and the haptics, directional focus with a real keyboard, TalkBack custom actions, the real insets and window size (the layout uses the configuration's screen size, as the rest of the home does).

## Where the code is
`PageEditorGeometry.kt` (layout, reorder maths, swing), `PageEditorHost.kt` (the host, the top bar slot, the tabs and content column), `PageEditorStrip.kt` (new: the strip, cards, overflow menu, drag, keys, actions), `PageEditorTabs.kt` (text tabs, content, page identity), `EditCardWiring.kt` (reorder, swing start scale), `L10nW45pageEditorLayout.kt`.

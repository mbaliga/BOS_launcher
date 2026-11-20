# settingsOneUI (wave 40, CFG-21)

The owner asked for a simpler, more traditional settings area with the full list laid out up front, emulating Samsung One UI. Ground Control's category strip is gone; the settings are a list and pages.

## What it is
- **Root**: one `LazyColumn`: a large "Settings" title in the upper third (the band is a third of the window, 172 to 260 dp), a rounded search field, then every category as a row of a rounded card. Four to five cards: look and home (Page N, Look, Home screen, App drawer, Desktop and dock), inputs (Corners, Gestures, Radar), Comms, system (System and sound, Hidden apps, Backup, Capabilities), then Lab and About. A category the grouping does not know gets a card before the last one, so a category added later is never lost (`OneUiModel.groups`).
- **Row**: a 40 dp rounded-square tile in a muted accent per category with the existing white glyph, the title (17 sp), a one-line grey summary, a chevron. Summaries come from the rows the category already holds (so they cannot disagree with the page) plus the page, gesture and hidden counts (`settingsSummaries`); a category with nothing better shows its first two values or "N settings".
- **Category page**: a slide from the end (a 90 ms fade under reduced motion), a 56 dp bar with a back arrow and a small centred title that fades in as the large title scrolls away (`OneUiModel.collapse`, pure), and the rows cut into cards. The model is a flat list, so the cut is made here: a new card starts where the kind of row changes (controls to links), around a row of preview cards, and at the section titles of `OneUiSections` (named by the English title of the first row of each section; a test fails when one of those rows is renamed). Section titles are headings, each card is a list.
- **Rows**: switch (own switch, rounded track and sliding thumb), choice (value in grey at the end, opens a radio list page that applies and returns), slider (the platform slider with a thin track, value at the end), stepper (minus and plus 48 dp targets, set-progress for TalkBack), action (destructive ones still ask first through the older sheet), pane (its content on a page with the same chrome). A choice that has preview cards keeps them inline, in its own card, where they were; tapping its title opens the radio page.
- **Wide landscape** (600 dp or more and wider than tall): the list is the start 40 percent (with a short 112 dp title band), the page of the selected category fills the rest, a row's page opens inside that pane, and Back closes. Portrait is drill-down.
- **Back**: Root, Category, Row page. Back (predictive, `PredictiveBackHandler`) and Esc step out one level; at the root they close. A deep link (a category index, a Radar result, "Edit home", the App drawer) opens its category directly and Back then goes to the root. `CATEGORY_ROOT` (-1) opens the root; the Home sheet's Ground Control button uses it.
- **Covered pages** (the root under a category, a category under its row page) are cleared from semantics and take no focus and no touch, so the screen reader and the keyboard only reach the page on top.
- **Preview window** (CFG-18): the host provides `LocalPreviewTop` (status bar inset + 56 + 8 dp); the window's default anchor uses it, and the title band is tall enough that the default window sits beside the title.
- **Backdrop**: the One UI page colours come from the launcher theme (soft off-black or near-white page, a lighter card). When the "Settings backdrop" preference supplies a wallpaper it is drawn behind and the pages are 94 percent opaque, so it only tints the page; the dimming slider therefore matters little. Pre-existing preference, kept so no row is dead.
- Settings are no longer forced left-to-right: right-to-left mirrors the arrows, chevrons, slide direction and switch.

## Removed
`CategoryTabs`, the category swipe, the cursor ring and `GroundControlNav` (tests included), the breadcrumb `DetailScreen`, the XMB row composables (`ToggleSettingRow` and friends); `SettingsMath` stays. `GKey` stays (Radar's key host uses it).

## Not done, on purpose
No new categories (One UI's separate Wallpaper and Dock entries are rows inside Home screen here), no per-row icons on pages, no popup dialog for choices.

## Undo
Revert the wave-40 commit: `SettingsSpace` goes back to the strip host.

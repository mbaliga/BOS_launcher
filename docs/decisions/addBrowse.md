# addBrowse (wave 51, HOM-07, HOM-12, HOM-04)

**Status: built (JVM and Robolectric tested; nothing is device verified).** Owner feedback on the page editor: "The apps and widgets selection is impossible to find unless the screen is empty. The widget selector is two layers down." The Widgets tab held one row "Choose a widget" that opened a modal sheet ("Add a widget"); the Apps tab grabbed focus on a filter field (keyboard up, icons hidden) and showed one thin strip of icons; in the single-page edit mode the tray's Apps chip opened a dialog and Widgets a dialog, with nothing visual in place.

## Decision
One browser, one layer, in both places, replacing the old entry path (the owner's rule: only the new design stays).
- **Widgets** (`WidgetBrowser.kt`): a sideways carousel of provider preview cards (the same preview loading the old picker used, `rememberWidgetPreview`), each with the widget name and its size ("5x2") under it, in two rows when the panel is tall enough (one larger row otherwise). One header row: a small search field that never takes focus on its own, and chips to filter by app (All, then each app). A tap on a card adds the widget to the page being edited through the picker's own bind and configure flow (`WidgetPickerLauncher.add`, so the peek or pop-out placement of `addWidget` is unchanged). Without a preview image the card draws the widget's footprint to its cell proportions. No "Choose a widget" row, no second sheet.
- **Apps** (`AppsContent`): the filter field no longer takes focus (the empty-home hint's `focusAdd` focuses the first app icon instead, for keyboard and controller users; a tap on the field is what raises the keyboard). The icons are 48 dp in a sideways grid with two rows when the room allows. With the keyboard up the page editor lifts its tab panel by the keyboard's height, so the results stay above it.
- **Home edit tray** (`HomeEditChrome.kt`): the Apps and Widgets chips toggle a panel directly above the tray (a rounded sheet, 40 percent of the window, 170 to 300 dp) with a title and a Done text button; the open chip is filled. The panel does not scrim or block: a touch anywhere outside the tray (the home's existing touch observer, no new input path) closes it while the page still gets that touch, so a drag of an item starts at once. Back and Esc close it before they cancel the edit. The chips are opaque with a hairline border so they read as buttons over a full page. Folders and + Page are as before.
- The widget dialog (`WidgetPickerDialog`) stays only for the places that are not page editing: the Ground Control item picker row, the corner editor and the left widget slot.

## Removed
The "Choose a widget" row (page editor Widgets tab, legacy `AddCarousel`), the "Widgets go on the page being edited." line, their strings in all languages, the tray's `AppPickerDialog` use and its `addAppOpen` state, `HomeItems.widgetRequest` (a widget now arrives as a chosen provider, `HomeItems.widgetChoice`).

## Not matched / honest limits
- No full-screen "all widgets" trailing tile (not required; the search and the app chips cover it).
- The tray carousel adds by tap only; dragging an app out of it onto the page is not wired (the page is edited directly; the page editor's Apps tab keeps its drag onto a page card).
- While the carousel is open it covers the lower part of the page (the page is not scrolled or shrunk); it closes with a touch outside, Done, Back or the chip.
- In the page editor the tab panel is short on phones (about a fifth of the window, the live card keeps its 0.45 minimum), so the widget cards show in one row and the apps in one or two rows depending on the window.
- Preview images come from the providers; Robolectric has none, so tests and screenshots show the footprint placeholders.
- Not verified on a device: the real provider previews, the bind consent and configure screens after a tap, the keyboard lifting the panel, TalkBack order, a controller.

## Where the code is
`feature-grid/WidgetBrowser.kt` (new), `WidgetPicker.kt` (`WidgetChoice` public, `rememberWidgetPreview`, `rememberWidgetChoices`, `widgetCellsOf`, `WidgetPickerLauncher.add`), `PageEditorTabs.kt`, `PageEditorHost.kt`, `HomeEditChrome.kt`, `AddCarousel.kt`, `app/HomeScreen.kt`, `HomeItemsHost.kt`, `EditCardWiring.kt`, `core-design/L10nW51addBrowse.kt`.

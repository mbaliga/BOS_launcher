# Wave 51: addBrowse (HOM-07, HOM-12, HOM-04)

Decision record: `docs/decisions/addBrowse.md`. Spec rows HOM-07 and HOM-12 in `docs/Spec.md` got the "Adding from the browser" and "Add browser" paragraphs. Nothing here is device verified.

| ID | Status | Note |
| --- | --- | --- |
| HOM-07 | built | The page editor's Widgets tab and the edit tray's Widgets chip show a preview carousel (`WidgetBrowser`): provider previews with name and size, a search field that does not take focus, filter chips by app, a tap adds through the picker's bind and configure flow (`WidgetPickerLauncher.add`). "Choose a widget" row and its strings removed. Tests `HOM_07_addBrowse10..12` (`PageEditorUiTest`), `HOM_12_addBrowse01` (`HomeEditChromeUiTest`), `HOM_12_theWidgetsChipOpensThePreviewCarouselDirectlyNotASheet` (`EditChromeW45AppTest`). |
| HOM-12 | built | Tray Apps and Widgets chips open the carousel in place above the tray (Done, same chip, Back, Esc or a touch outside closes; no scrim); Apps tab no longer takes focus or raises the keyboard, larger icons in rows, panel lifted over the keyboard. Tests `HOM_12_addBrowse01..05` (`HomeEditChromeUiTest`), `HOM_12_addBrowse13..14` (`PageEditorUiTest`), `HOM_12_theAppsChipOpensTheCarouselInPlaceAndATapPlacesTheAppOnTheCurrentPage` (app). |
| HOM-04 | built | The legacy add carousel (`AddCarousel`, only `EditCardHost`) shows the same widget browser in its Widgets tab. |

## Tests changed
- `PageEditorUiTest.HOM_12_pe21_...`: no longer clicks "Choose a widget" (row removed); asserts it and the "Add a widget" sheet are absent.
- `EditCardUiTest.HOM_04_tabsReachWidgetsFoldersAndShortcuts`: the Widgets tab shows the browser instead of the row.
- `HomeEditChromeUiTest.HOM_12_theTrayChips...` (renamed `...AreWideTargetsAndFoldersAndPageStillAct`): Apps and Widgets no longer call a callback (they open the panel); tested by the new `addBrowse` tests.
- `EditChromeW45AppTest.HOM_12_theAppsChip...`: the dialog ("Add an app") became the in-place carousel.
- New helper `WidgetFixtures.kt` (fake providers; Robolectric has none).

## Screenshots read (Robolectric)
`feature-grid/build/screenshots/addBrowse_tray_widgets.png`, `addBrowse_tray_apps.png`, `page_editor_addBrowse_widgets_tab.png`, `page_editor_addBrowse_apps_tab.png`. Read: the tray panel sits directly above the chips, two rows of cards or icons, the open chip filled; in the page editor the panel shows one row (short panel). Footprint placeholders stand for provider previews.

## Not done / device only
See "Not matched" in the decision: no "all widgets" tile, no drag out of the tray carousel, the carousel covers the lower page while open. Device only: real previews, bind and configure screens, the keyboard lift, TalkBack, controller.

# Wave 34: homeSheet (HOM-21, HOM-22, HOM-23)

| ID | Status | Note |
| --- | --- | --- |
| HOM-21 | built | Long-press Home sheet restyled like the Sound sheet (black, centred title, outlined pill chips, hairline text rows); `HomeQuickSheetBody` in `HomeQuickSheet.kt`. Robolectric screenshots `app/build/screenshots/home-sheet-*.png` (dark, light, page tools, RTL, font scale 2, landscape) were read; no device run. |
| HOM-22 | built | Glance tile, Background, Time/Weather/Battery tiles, Add page, Edit home, Files and Widgets-folders-icons removed from the sheet. Glance toggle, Add page and Edit home were already rows in Ground Control, Home screen; "Widgets, folders and icons" was reachable only here, so a row was added there. Files/timeline stay in Comms. |
| HOM-23 | built | Glance content and Hotspots chips kept. |

Tests changed: `HomeQuickSheetGlanceTest` rewritten (new: removed controls absent, wallpaper/ground rows, page tools); the flow tests HomeUnifyFlowTest, HomeUnifyReviewFlowTest, HomeUnifyReview2FlowTest, PagesExplicitW30FlowTest, HomeLandingItemsW24Test, GlanceSmokeTest now reach Edit home, Add page, the item picker and the Glance switch through Ground Control (`TourBase.quickGoTo`) because the sheet no longer has them; `TourBase.quickSheetShown` uses the new `quick.sheet` tag. New `HomeQuickSheetShotsTest`. New string table `L10nW34homeSheet.kt`.

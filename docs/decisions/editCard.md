# Decision: edit home as a card with a page carousel, empty default home, add carousel (wave 8, owner request)

**Status: built (Robolectric and JVM tested, not run on a device).** Affects HOM-01, HOM-04, HOM-05, HOM-12, WSP-05 in `docs/Spec.md`.

## What the owner asked

"When I long press the home to edit it, [the home shrinks and moves to the foreground], but on the right side [of the notifications picture, so the card goes left], that way I can scroll through pages and delete pages as I see fit. By default, let home be empty and apps hidden behind the add drawer/carousel." The mock-up shows the home as a rounded card (about 28 dp corners) pushed against one screen edge, a second card peeking in beside it, a quiet light surface with lots of air.

## What the spec said before

- HOM-12: "Explicit edit mode with undo for the last 20 changes". Edit mode was an outline around the full-size home plus a banner and a bar (Undo, Redo, Add page, Done). Pages were added and deleted only from the long-press quick sheet.
- HOM-01: no statement about a first-run layout. In code a fresh install defaulted to "Only ones I add" and a one-time seed put Settings, Phone and Messages on page one.
- HOM-05: the app drawer and the Radar were the only ways to find an app that was not on the home.

## What it says now

- Edit mode shrinks the live home (one `graphicsLayer` scale and translate, `EditCardGeometry`) into a card on the start side. The card stays directly editable: drag, drop-onto, resize handles and undo work because pointer input follows the layer transform. The home composition is not recomposed or captured, so its state is kept.
- The background holds the page carousel (`PageCarousel`, thumbnails drawn by `PagePreview` from the same page model, never screenshots) and the add carousel (`AddCarousel`).
- Pages: tap shows the page on the card, Delete page (items join the previous page, as the quick sheet always did; one undo step; an Undo notice), Add page, reorder by long-press drag or Move up / Move down. The last page cannot vanish: deleting it empties it and its apps go off the home (still in the drawer). Page edits go through the layout store, so the HOM-12 stack of 20 undoes them. The pure rules are `HomePageOps` (core-data); groups of visual pages that share an id (a density overflow) move as one.
- Fresh install: one empty page, no apps placed, empty dock. `FreshHome.isFresh` is true only when no page was ever saved (no pages, nothing removed), onboarding is not done and the old three-app seed never ran. A layout with pages, an imported or restored layout, and users from before this change (onboarding done, inferred from existing prefs) are untouched. The old Settings/Phone/Messages seed is kept only for those users and no longer runs on a fresh install.
- Add carousel: tap adds to the first free cell of the current page (next page with room, else a new page, `AddCarouselModel.place`). A long-press drag onto the card does the same; it does not choose a cell (the card is scaled, and the first-free-cell rule keeps keyboard and touch identical). Widgets, Folders and Shortcuts tabs open the existing pickers (widget picker, new folder, app shortcut, web link, file, app group).
- An empty home (Grid, paged) shows "Long press to add apps"; a tap on it, or a long press on empty home space, opens edit mode with the filter field of the add carousel focused. A long press on a non-empty home still opens the quick sheet (wallpaper, panels, Ground Control live there), whose Edit enters the card.

## Judgement calls (the owner may want to change these)

- The dock steps aside while the card is up (it would collide with the add carousel). Dock pins can still be edited from the dock outside edit mode; a move to the dock inside edit mode is therefore not offered while the card is shown. The layout undo still covers pin changes made before.
- Portrait puts the add carousel in a band under the card; landscape puts it at the foot of the right-hand column under the page carousel, whose rows then show the buttons beside the thumbnail.
- Scale is 0.68 in portrait and 0.60 in landscape (about 0.7, reduced so the column keeps at least a quarter of the width); 4% of the card's width is off screen.
- Only the Grid style, paged, has pages, so only it shows the hint and the page carousel is meaningful there; other styles keep the previous edit behaviour inside the card.
- The full-screen violet outline of the old edit mode is gone: the card itself says edit mode is on.
- NFR-14 audit: `EditCardGeometry.kt` is on the animate-as-state allow-list (one call, read in the draw phase). Reduced motion: the card jumps, the carousel fades in 160 ms.

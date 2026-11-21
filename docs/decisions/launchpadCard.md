# Launchpad as the background of a shrunken home card (wave 8)

## What changed

- STR-01: opening Launchpad no longer slides the home away (landscape, to 86 percent) or hides it (portrait). The live home is shrunk with a single graphicsLayer (scale, translate, rounded clip of 28 dp, soft shadow) into a card about two thirds size, pushed to the right (partly off screen) in both orientations; to the left in right-to-left layouts. A neighbouring card peeks in below it.
- STR-02/STR-08: the freed background column holds the notifications: a menu icon (the old group-by, show and clear-all menu), the Home button, the system state row (wrapped in two rows of two when narrow), then section titles. The selected title is large and dark, the others small and grey; the selected section's rows sit beneath it. There are no horizontally scrolling panes. The old per-group collapse is kept for the selected section (Left folds, Right unfolds; activating the selected title again toggles).
- STR-07: the screenshot matrix runs with the card in place (both orientations, light and dark, plus a mid-drag peek and a right-to-left case); the earlier content-only matrix is kept.
- STR-09: the waiting indicator, handle and rail sit on the notifications edge, which is the right edge in right-to-left.
- Tapping the card, dragging it back, Back, Esc, the handle and the shortcut return home. The card overlay is a focusable button so keyboard and switch users reach it; the home beneath is cleared from the accessibility tree while open.
- Reduced motion (NFR-14): the card snaps to its pose and only the backdrop cross-fades.
- Pure geometry: `LaunchpadCardGeometry` (card pose, bounds, neighbour, background column, CardEdge for Launchpad and, mirrored, for home edit mode).

## Why

Owner request (wave 8 brief, mock-up image 22): "the home will shrink and move to the foreground, with the background housing the notifications", no scrolling panes.

## What the old spec said

STR-01: "In landscape the home card stays partly visible; in portrait Launchpad takes the screen." Tests that encoded this (`RevealMathTest` landscape/portrait strip assertion) were rewritten to the new behaviour; the device checklist rows 1 to 11 of the Slide, peek and reveal section were reworded.

## Known limits

- The left-swipe from the first grid page (inner gesture) is off in right-to-left layouts; the mirrored edge drag, handle, rail, indicator and shortcut open it instead.
- The card is not yet shared with home edit mode (the page carousel on the opposite side): `CardEdge.forEdit` and `homeCardLayer` are ready for it.
- Section titles are the existing groups of the chosen grouping (Time, App, Sender, Conversation, Flat, App and conversation; Digest and Pinned always); a Now / Digest / Silent split is not added.

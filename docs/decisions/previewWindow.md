# previewWindow (wave 39, CFG-18)

The owner asked for the live preview in Settings to be movable, resizeable and minimisable. The fixed dock became a floating window.

## What it is
A bar (grip, "Preview", minimise button; 48 dp high) over the scaled live home (the same `HomeCapture` layer as before, nothing is rendered twice). Minimised it is a pill (112 x 40 dp, grip, title, restore glyph); the picture is not composed then, so nothing is drawn per frame.

## Decisions
- Geometry is pure (`PreviewWindowGeometry`, JVM tested): sizes in dp; the window never narrower than the 136 dp bar so the title and buttons fit; resize scales the long side of the picture so the aspect is kept, 72 dp (the old dock minimum) to 70 percent of the area's short side; default size is exactly the old dock size.
- Clamp: a window that fits stays fully inside; a window bigger than the area may overhang but keeps 48 dp of itself inside. Release snaps an axis to the 12 dp edge margin when within 32 dp (instant under reduced motion).
- Default anchor: top-end, 64 dp under the top, 12 dp from the end; top-start in a right-to-left locale, where the resize handle is bottom-left and dragging it left grows the window. Ground Control forces a left-to-right layout, so the window reads the configuration itself and provides its own layout direction.
- Minimise and restore keep the end-side edge, so the pill sits where the window's end was.
- Drags are measured in root coordinates (a node that moves or grows under the finger would distort local deltas) and start only after the touch slop, so taps and double taps still work. Position is applied in a graphicsLayer lambda; resize recomposes only the window.
- Pinch: only with two fingers; one finger on the picture is absorbed (no click inside, nothing reaches the settings below).
- Remembered per orientation (`port`, `land` text "x,y,long", NaN means default) plus a shared minimised flag, in the pref file `preview_window` (TolerantPrefs). It is device-local UI state: excluded from backup and device transfer (`StateSchema.BACKUP_EXCLUDED_PREF_FILES`, both XML rule files), never exported. Written when a gesture ends, never per frame, never during composition.
- Keys and TalkBack actions share one controller, so all inputs behave the same. Esc and Enter are handled on the minimise button and on the pill; the settings Esc handler lives on the sibling content, so it does not swallow them.

## Undo
Make `SettingsPreviewDock` draw the old fixed frame again (git history, wave 38) and drop `PreviewWindow.kt`, `PreviewWindowModel.kt`, the `preview_window` classification and the L10n table.

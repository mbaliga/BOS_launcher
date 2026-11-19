# Wave 34 homeSheet: the long-press Home sheet

Owner request: remove the crossed-out options from the Home sheet and align its look with the Sound and Vibration and Connectivity sheets.

## Removed from the sheet (features kept)
- "Calm home screen (Glance)" tile: still Ground Control, Home screen, first row.
- "Background" row: wallpaper rows are in Ground Control, Home screen (the Wallpaper row of the sheet opens that category).
- Time / Weather / Battery % tiles: Glance content chips stay in the sheet; the full toggles are in Ground Control, Home screen, Glance rows.
- "Add page": Ground Control, Home screen (Paginated), the edit-home bar's Add page button; "Delete page" stays in the sheet's This page block and in the per-page Ground Control category.
- "Edit home": Ground Control, Home screen; also long press on an empty fresh home.
- "Files, usage and timeline": Comms hub (Files view, Timeline); nothing else needed.
- "Widgets, folders and icons" (the item picker: widgets, folders, links, files, shortcuts, icon pack, hidden apps, groups, panels): this was reachable only from this sheet, so a new row "Widgets, folders and icons" is added to Ground Control, Home screen (`SettingsModel.addHomeItems`) right after Edit home. The owner is told.
- Dead code removed: `onAddPage`, `onEdit`, `onPanels`, `onHomeItems` parameters, the tile layout use, `panelsOpen` in HomeScreen.

## Look (tokens copied from SoundVibrationSheet, which is untouched)
Pure black surface, 36 dp top corners, no drag handle, scrim black 62 percent, centred 22 sp light white title, 28 dp side padding, section labels small caps grey (12 sp, 1.6 sp tracking, 22 dp above), chips are outlined pills (1 dp, 40 percent white) with the selected one filled white and black text (the Sound sheet is white on black and has no accent colour), at least 48 dp tall, wrapping in a FlowRow so font scale 2 never clips sideways, two plain 56 dp rows (glyph and text) split by 20 percent white hairlines. Both light and dark themes show the same black sheet, as the Sound sheet does. Drag down, scrim tap and Back dismiss through `ModalBottomSheet`. Chips use `selectable` (radio role) for TalkBack. The scaffold was NOT extracted into a shared composable: the Sound sheet is a custom overlay and the Connectivity stream edits the same file area, so tokens were copied (`HomeQuickSheetBody`).
- Not matched: the Sound sheet's 640 dp width cap and slide-in animation (the Material sheet's own animation is used), and the title is 22 sp like the real Sound sheet, not the 28-30 sp of the brief.

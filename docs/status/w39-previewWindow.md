# Wave 39: previewWindow (CFG-18)

Decision record: `docs/decisions/previewWindow.md`. Spec row CFG-18 and `docs/SPEC_STATUS.md` updated. Screenshots (written by `PreviewWindowUiTest` to `feature-grid/build/screenshots/preview_*.png`) were read: portrait dark default (window top-right under the header, bar with grip, title, minus, blue picture, diagonal handle bottom-right), moved and resized (window over the rows, larger picture), minimised (pill with grip, "Preview", square glyph), portrait light (white window), landscape dark moved and resized (clamped at the bottom edge, inside), right-to-left default (window top-left, minus on the left, grip on the right, handle bottom-left).

| ID | Status | Note |
| --- | --- | --- |
| CFG-18 | partial | Move, resize (handle and pinch), minimise/restore, remembered per orientation, keys, TalkBack actions, RTL default, reduced motion, landscape, large font: built and tested headless. Device-only: drag feel, pinch, snap feel, gesture conflicts with the settings list scroll, live pixels while a setting changes. |

## Tests
- `PreviewWindowGeometryTest` (JVM, 8): defaults and mirror, window sizing, resize bounds and aspect, RTL resize, clamp, oversized clamp keeps 48 dp, snap, codec round trip and garbage.
- `PreviewWindowUiTest` (Robolectric native graphics, 23): default frame, drag and clamp, snap with and without motion, double-tap reset, handle resize min and max, pinch, minimise and restore, draggable pill, persistence across a rebuilt window and the pref file contents per orientation, landscape, RTL (injected, see below), arrow and Shift+arrow keys, Esc and Enter, eight custom actions, 48 dp targets, touches never reach the underlay, one unfocusable unclickable picture node, 2x font, four screenshot tests.
- Old tests changed: `SettingsPreviewDockUiTest` (anchor now measured on the bar: window top 64 dp, picture 48 dp lower; the RTL test now asserts the mirrored default, the old one asserted the forced-left-to-right behaviour that this change replaces). `DockGeometryTest` and `SettingsPreviewDockRenderTest` unchanged.
- Robolectric's `ldrtl` qualifier does not reach `Configuration.layoutDirection` in this setup, so the tests inject `rtl = true` (`SettingsPreviewDock(capture, rtl = ...)`); the app passes nothing and reads the configuration. Not seen on a device.
- Audits touched: `preview_window` classified in `W13CrossAuditTest`, `StateSchema.BACKUP_EXCLUDED_PREF_FILES`, `backup_rules.xml`, `data_extraction_rules.xml`; L10n table `L10nW39previewWindow.kt` (es fr de pt hi zh ja ar).

## Not matched / known
- The window is at least 136 dp wide (bar), so a narrow portrait picture sits centred with margins; the old dock was exactly the picture size.
- In a right-to-left locale the default top-left spot overlaps the Home category puck; the user can move it.
- Light theme: Ground Control's own text is dim over its dark backdrop in the screenshot (existing, not changed here).

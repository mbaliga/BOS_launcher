# Wave 38 glanceFixed: fixed hotspots, music and dots; Glance without time, date, weather; no Suggested app

Nothing was run on a device. Verified by JVM and Robolectric tests and by reading rendered screenshots (`feature-grid/build/screenshots/fixed_chrome_glance_dark.png`, `fixed_chrome_page2_dark.png`; app: `app/build/screenshots/w38_glance_dark.png`, `w38_page_dark.png`). Design in `docs/decisions/glanceFixed.md`.

| ID | Status | Note |
|---|---|---|
| HOM-23 | built (device untested) | The four hotspots (drawn with the chosen corner style, live), the clock (analog default; digital at the music text size; 12/24 h) and the music line (with the pop-in controls) are `FixedHomeChrome` above the pager, on Glance and every page; same screen bounds on Glance, mid-swipe and on a page (`GFX_38_theFourHotspotsAndTheMusicLineStay...` in feature-grid, `GlanceFixedW38SmokeTest` in the real app). The Glance page is the wallpaper only; the clock and the music line are the only text-like elements; no date, weather or battery node anywhere (tests assert it on Glance and on a page). |
| HOM-23 (music) | built (device untested) | Music line on every page while media is active and `showMusic` (Settings, Home screen, "Show music", default on). `glanceContent` migrates into it (`GFX_38_anOldInstallKeepsItsMusicChoice...`). |
| HOM-22 / HOM-12 | built (device untested) | Home quick sheet: Pages, Order, Hotspots (also with Glance off), page tools, Wallpaper, Ground Control; no "Glance content" heading or chips. Page editor Glance tab: Show music and Hotspots. |
| INP-16, INP-17 | built (device untested) | Hotspot keyboard and pointer paths follow the one hotspot set on every page. Hotspot long press opens the corner editor. |
| HOM-24 | built (device untested) | Clock and status section removed (position, layout, size, day, date, weather, Fahrenheit, status strip, Health data); one clock setting Analog / Digital + 12 / 24 h under Home screen; stored keys retired, `clockFormat` kept. Weather client and INTERNET / location permissions removed (NFR-16). |
| CFG-03 | built | `glanceWeather`, `suggestion` retired (purge marker w38, dropped on import, deleted on save); `glanceContent` kept until `showMusic` exists. |
| HOM-01 | built | The Suggested app slot (thunderbolt) is removed: drawing, hit handling, `AppSuggester`, `showSuggestion`, settings toggle. |

## Existing tests changed or removed
- Removed: `GlanceWeatherContrastTest` (the weather layer is gone), the Glance clock / date / weather / font-200 / clock-size tests in `GlanceHomeUiTest`, the four clock-fit tests in `GlanceAuditW20UiTest`, `HOM_23_weatherConditionsMapToEffects`, daylight, thunder flash, ambience tests in `GlanceModelTest` and `GlanceAuditW20Test`, the content preset tests, `suggester favours...` in `HomeModelTest`.
- Rewritten: Glance UI tests use `GlanceHomeForTest` (Glance page plus the fixed chrome); "tap on the surface pops the controls" now taps the music line (the page surface no longer takes that tap); `GlanceSmokeTest`, `AppSmokeTest` (no `glance.clock` node), `HomeQuickSheetGlanceTest` (no content chips; hotspot chips stay with Glance off).
- `ScreenshotTest`, `GridHome` UI tests: the `suggestion` argument is gone.

## More existing tests changed or removed
- Removed: the status strip, clock position and bottom-clock band tests (`BottomStackTest` clock cases rewritten to dots only, `ScreenshotTest` clock-bottom and clock-big shots), `StateStoreTest` strip assertion, the Weather audits' weather-only allowance (rewritten to "no network at all").

## Device-only
Swipe feel; a swipe that starts on a hotspot's corner square against the pager; marquee and notch behaviour over real wallpapers; the pages keeping their own wallpaper contrast under the fixed music line.

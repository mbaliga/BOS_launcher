# Wave 41: glanceClock (big wallpaper clock on the Glance page)

| ID | Status | Note |
| --- | --- | --- |
| HOM-23 top line | built, not device-verified | The clock is removed from `FixedHomeChrome`; the music line is the only top line on every page. `FixedHomeChromeW38UiTest.GFX_41_theClockIsNotInTheFixedChrome...`, `GlanceFixedW38SmokeTest`. |
| HOM-23 big clock | built, not device-verified | `GlanceWallpaperClock` inside `GlanceSurface`: scrolls away with the page, absent on pages, centred upper middle. Six styles; each style pixel-checked inside the page and legible on dark and light (`GlanceClockW41UiTest`). Screenshots read: `feature-grid/build/screenshots/glance_clock_*.png`, `app/build/screenshots/w41_*.png`. |
| HOM-23 word clock | built | English words, JVM tests for 12:00, 0:00, 12:30, quarter past and to, five to and all 1440 minutes; other languages fall back to Big digital (not translated). |
| HOM-23 options | built | 12/24 hour, date, colour, size; settings rows only in Ground Control > Home screen. |
| HOM-23 migration | built | Analog to Analog classic, Digital to Big digital, 12/24 kept (`GlanceModelTest.GFX_41_...`). |
| CFG-21 summaries | unchanged | |

## Tests run
- core-data: `GlanceModelTest`, `StateSchema*`, `StateCodec*`, `StateBackup*` pass.
- feature-grid: `GlanceClockRulesW41Test`, `GlanceClockW41UiTest`, `FixedHomeChromeW38*` pass.
- app: `GlanceFixedW38SmokeTest`, `UpgradeSmokeTest` pass.
- core-quality: whole module passes. core-design: all pass except `CFG_19_...` (translation of "%s shortcut segments" from `SettingsOneUiSummaries.kt`, wave 40, not touched here).

## Old tests changed or removed
- `GlanceClockW38Test.kt` removed (the wave 38 clock and its size rule "same size as the music text" are replaced by the owner request); replaced by `GlanceClockW41Test.kt`.
- `FixedHomeChromeW38Test`: the clock-and-music-line test became `GFX_41_theClockIsNotInTheFixedChrome...`; focus-node counts no longer include the clock.
- `GlanceFixedW38SmokeTest`: clock test and the Analog or Digital settings test rewritten for wave 41; hotspot counts exclude the clock.
- `GlanceModelTest`: the wave 38 clock style test became the wave 41 default, round trip and migration tests.

## Device only
Minute tick after the screen was off, TalkBack reading, look over live wallpapers, tablet sizes.

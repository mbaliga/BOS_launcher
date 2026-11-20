# Glance wallpaper clock (wave 41, stream w41-glanceClock): decisions

Owner request (verbatim): "I think I want the big wallpaper clock on the glance calm home. When time shows along with the most music, have the clock show in the same line and the same size as the music text. Actually, let only music be there, ditch the clock from the top. Allow configuration of clock styles for the glance calm home."

## What changed

- **No clock in the fixed chrome.** `FixedHomeChrome` lost the clock row, its parameters and the 48 dp row height; the music line is now the only top line (on every page, as before). The wave 38 `GlanceClock` is replaced.
- **A big clock on the Glance page.** `GlanceWallpaperClock` (feature-grid `ClockBlock.kt`) is drawn by `GlanceSurface` above its tap layer, so it is page content: it scrolls away with the Glance and does not exist on any other page. Position is fixed (centred, top of the area at 14 percent of the page height, area 82 percent wide and 42 percent high, inside the safe area); not movable. It scales to fit, so landscape and font scale 2 do not overflow (numerals are measured with a text measurer and the size shrinks until they fit; Compose 2024.12 has no autosize).
- **Styles** (`ClockStyle`): Big digital (default; hours over minutes, ExtraLight, accent line), Big digital single line, Analog classic, Analog minimal, Word clock, Off. Word clock is English only (`ClockText.wordsEnglish`, rounded down to five minutes); any other app language shows Big digital (`ClockText.effectiveStyle`). Translating the words is not done.
- **Options:** 12 or 24 hour for the digital styles (`clockFormat` kept; System follows the device), "Show date under the clock" (`glanceClockDate`, default off), colour Automatic or Accent (`glanceClockAccent`; Automatic is `GlanceInk.clock(dark)` with a soft opposite-colour shadow), size Small, Medium, Large (`glanceClockSize`, default Large, factors 0.6, 0.8, 1.0). The rows are in `glanceSettings` (Ground Control > Home screen) only; with Off only the style row shows.
- **Time and accessibility.** Minute tick via `rememberNow` (ACTION_TIME_TICK), no frame loop, no seconds, nothing to animate (no reduced-motion branch needed). Digits and dial are laid out LTR in right-to-left. The node's content description is the time (and date) and changes once a minute; it is not a live region, so TalkBack does not announce on its own. A tap runs the Clock corner action.

## Persisted state

- `clockStyle` keeps its key. `ClockStyle.fromStored` maps an old "Analog" to AnalogClassic and "Digital" to BigDigital; the new name is written on the next save. A fresh install, an unknown or a missing value is Big digital (an install that never set the old style was on Analog by default and now gets Big digital; only a stored choice is carried over). `clockFormat` is untouched.
- New keys `glanceClockDate`, `glanceClockAccent`, `glanceClockSize` in `launcher_prefs` (same file, so StateSchema classification, backup and export need no change; unknown values fall back to the defaults). The retired keys `clockSize` and `clockDate` stay retired and are not reused.

## Not matched / device only

- Minute ticks over a screen-off period, TalkBack reading and the look over live wallpapers are device checks.
- The light-surface contrast is pixel-checked on a flat colour only; on a busy wallpaper the home scrim (NFR-15) is what keeps it legible.
- Dial size is capped at 260 dp (Large); on a tablet it stays that size, not larger.

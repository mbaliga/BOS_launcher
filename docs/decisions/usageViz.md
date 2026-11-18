# usageViz (wave 28): Usage merged into Timeline as an ASOC viz block

Owner: "Combine Usage with Timeline. Let usage be a small visualization above timeline. We recently developed a dataviz toolkit, I want you to use that for the visualization."

## The toolkit

The ASOC viz toolkit, version **0.3.0** (`ASOC.viz.version`; files `viz.js`, `viz-charts.js`, `index.html`, `orrery-kit/*`): a dependency-free SVG/JS charting library. Its rule is one ink on one ground; categories are textures, not hues (fixed order solid, hatch, dots, fade, cross, lines, checks, stipple; a ninth part folds into "Other", never a ninth texture); thin marks; no number on every point; tones such as `night` (ground #0c0c0d, ink #f2efe9, accent #f6ee2d). The launcher is Compose with no WebView, so the visual language is ported to Compose Canvas, not the library. The port lives in `feature-launchpad/.../ui/viz` (`Viz.kt` carries the KDoc table of what was ported from which toolkit function):

| here | toolkit |
|---|---|
| `VizTexture`, `VizTexture.ORDER`, `VizFormat.fold` | `ASOC.viz.order`, `fold` |
| `VizTextures` (tile painters hatch 7, dots 9, cross 7, lines 6, checks 8, stipple 4.5 px, cached `ImageShader` tiles; fade gradients) | `ASOC.viz.fills`, `canvasFills` |
| `VizTone` | `TONES.night`, `tone(o)` |
| `VizSteps` | `steps` |
| `VizStack` | `stack` |
| `VizLegend`, `VizSwatch` | `legend`, `swatch` |
| `VizStat` | `stat` |
| `VizTable` | `table()` |

## What the block is

One compact block (about 150 dp at scale 1, a little more when a five-key legend wraps) as the first item of the Timeline view's list, same horizontal margins as the list:

1. Header: the selected day's name ("Today" by default; "Yesterday", then the short weekday) as a muted caption on the baseline of the total as a stat figure (light weight, primary ink).
2. `steps`: the last seven days, columns from one baseline, day initials below, the figure above the selected column only (the toolkit prints a figure on every column; a week of seven is selected, not read, so the "no number on every point" rule wins). Today is the accent (the toolkit's yellow on dark, a darker olive-yellow derived for the light look), the selected column is full ink, the rest the same ink at half strength. Tap a column to select it.
3. `stack`: one pill for the selected day by app: the top 4 apps in solid, hatch, dots, fade; the rest folded into Other in stipple; labelled (share in percent) under a part only where the label fits.
4. `legend` row directly under it: texture swatch, the app's own coloured launcher icon (the only hue in the block; it is identity beside the swatch, never inside the chart), name, duration. Other has no icon.

## Decisions

- **Ink and ground** are the Comms colours: ink is the Comms primary text colour, ground the Comms background (`LocalLp`), so the block follows the dark and light looks; the Timeline panel no longer paints its own dark surface (Files still does and is unchanged). The accent only marks today.
- **Textures are stable per app.** Apps are ranked over the whole week (time, ties by package name); the rank pins the texture: ranks 1 to 4 keep solid, hatch, dots, fade on every day. A day's top-4 app that ranks lower over the week takes the first of the four textures its day left free (so a day never repeats a texture and never needs a ninth). Other is always stipple. The four app textures plus stipple are the whole set a day can show.
- **Data** is the existing `UsageStatsSource` (core-catalog), adapted to a small `UsageWeekSource` in the app; `UsageWeekRepo` reads the seven days off the main thread and caches them (a day read after it ended is final; today is re-read after 60 s). No new permission, no new API. Access is checked off the main thread and again on resume.
- **No access**: the existing "Allow usage access" action (through the same `GrantHooks` and settings intent) replaces the chart, no empty axes. **No data**: one quiet line, "No screen time recorded yet."
- **Accessibility** (the toolkit's `table()` and per-mark `aria-label`): the chart node carries a summary of the week, the selected day and its top apps, and a custom action "Show as table" / "Show as chart" that swaps the steps for a table of days and durations; columns are 48 dp wide tap targets (seven of them need 336 dp, so the chart may reach into the list margin on a 360 dp phone); every stack part is its own focus and accessibility node reading "App, duration, percent"; legend keys read "name, duration". Texture, not colour, carries identity.
- **Reduced motion**: nothing animates (the toolkit's only motion is an .15 s opacity transition on hover, which touch does not have), a source scan test keeps it so.
- **Left out of the port**: hover tooltips (touch selects instead), `bars`, `fan`, `line`, `spark`, `halftone`, `meter`, the Orrery charts.

## Tabs

ALL, UNREAD, FILES, TIMELINE (READ stays a row of the Comms menu). The quick sheet, the Usage home tile, Ground Control and deep links `liftoff-panel://usage` now open Timeline (`HubView.forPanel("usage")`; the list opens at the top, where the block is); a stored default view of "usage" is read as Timeline and rewritten; two-finger swipes step over the four tabs. The home Usage tile and its range setting stay (they are home items, not the removed view).

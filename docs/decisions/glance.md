# Glance home (wave 16, stream w16-glance): decisions

Owner request (authoritative, overrides the first-run default of wave 8 and the Minimal landing of wave 12): "This is what the home screen should look like in landscape and portrait. Time, centered, with day and date. Music that's playing at the top. On tap, the controls pop in, and pop out on inactivity. So the default for the launcher should be an empty or near-empty screen with only time or time+date or Date + weather ... and the four hotspots - user can make it two hotspots too, or three, or one, or none."

## What changed in the spec

| Row | Old text | New behaviour |
| --- | --- | --- |
| HOM-22 | Four landings, default Selected apps, the fourth is Minimal (wallpaper and four hotspots with dots). | The fourth landing is Glance (enum name `Minimal` kept for stored values, label "Glance"); the default landing is Glance. |
| HOM-01 | A fresh install starts on one empty page with a hint. | Selected apps is no longer the default landing; layouts are untouched. The hint shows only on Selected apps. |
| HOM-23 | none | New: the Glance home. |
| STR-06, INP-16, INP-17 | media strip, dwell reveal, corner keyboard | one added sentence each (Glance uses the media source; disabled hotspots have no reveal and are not focus nodes). |
| Minimal hotspot defaults (Radar, Launchpad, drawer, Ground Control) | Substituted for factory corners on Minimal | Removed: Glance uses the corner assignments as they are; the factory set is already the owner's reference (Camera top-left, Music top-right, All apps bottom-left, Search bottom-right). `MinimalHotspots.effective` stays in core-data, unused, because its tests encode a pure rule. |
| "Show corner dots" (`minimalHints`) | A faint dot per hotspot | Not drawn on Glance (the hotspot draws its action glyph). Wave 20: the pref and its stored key were removed; an old export that carries it drops the key on import and the one-time `RetiredPrefKeys` cleanup removes it from the private file. |

## Default and migration

- Plain pref `homeLanding` keeps being written (audit), plus a new marker `landingChosen`. Reading: with the marker the stored landing is the user's choice; without it, a stored Recents or All apps is kept (never defaults), a stored Minimal becomes Glance, a stored Selected or nothing becomes Glance. Wave 12 wrote the landing on every save, so a stored Selected cannot be told from a default: it counts as never chosen. This is the one place an existing install changes (owner: "the new default applies to everyone, including existing installs").
- The marker is written when the landing is not the default or was written before, so choosing Selected apps sticks, and choosing Glance again after that also sticks. Imports of an old file without the marker follow the same reading.
- Pages, folders, widgets, tags and hidden apps are never touched.

## Settings (plain prefs, exported)

`glanceContent` (CSV of Time, Date, Weather, NowPlaying, Battery; default Time, Date, NowPlaying; an empty string is a valid "nothing"), `glanceHotspots` (CSV of physical corners, default all four), `glanceControlsSec` (2 to 10, default 4), `glanceWeather` (Subtle default or Normal), `glanceIndicator` (default on). Presets for content replace only the time, date and weather part and leave Now playing and Battery as they were. Hotspot preset One is the bottom right (search); Two is both bottom corners.

## Behaviour decisions

- Layers, bottom to top: base, weather ambience, the two-slot SignalBackground and its scrim (passed in as `underlay`, so the glow lines and the dot grid sit above the weather), the tap layer (shares the home gesture layer), the centre, the music line, the notch, the hotspots, the indicator. Composition order is the TalkBack order.
- Controls: a pure value `ControlsPop` (no clock inside; every call takes the time) with the rules in the spec; the host runs one timer to `hideAt`. A tap with the notch shown is "a tap outside" and hides it. The notch is composed only while shown or animating out.
- Notch (wave 22, owner: the build 51 controls were a floating boxed pill with the title and filled glyphs): the controls are a black tab hanging from y = 0 of the window (`ControlsNotch`, `NotchShape`, numbers in `GlanceNotch`): flat top with concave flared shoulders (16 dp), straight sides, convex bottom corners (14 dp), 58 percent of the clock-area width within 220 and 320 dp, 32 dp tall below the display cutout. The shape is drawn from the physical top; the display cutout inset only pushes the three 48 dp targets down (and the title line, which always sits below the notch bottom with a small gap, so it never moves and is never covered). Glyphs are thin outline vectors (`OutlineMediaGlyphs`: Rewind, Play, Pause, Forward, stroke about 1.65 dp). Labels: Previous, Play or Pause, Next. The title is not inside the notch. Motion: translation from -height to 0 with a spring, retract 180 ms; reduced motion fades. Timer, tap-outside and the `MediaNow` calls are unchanged.
- Hotspots: `cornerPlacements` already maps physical corners to screen corners at the display rotation, so one stored set serves both orientations; enabled-ness uses the same mapping (`GlanceHotspots.enabledOnScreen`). Palette assignments run their first item (no room to fan out), as on Minimal.
- Weather: daylight is the hour of day (06 to 19), no location maths. Weather is requested (permission, refresh) when Weather is in the Glance content, as well as for the old clock option, still through the one `WeatherSource`.
- The dock rail is hidden on Glance unless Right side is Dock rail (the dock itself stays optional and off by default on phones).
- The clock size is `min(0.26 w, 0.30 h, 124 dp)` times the font scale (1 to 2), never above 0.28 w or 0.42 h, so at 200 percent it grows but cannot cover the corners or the music line.
- 12 or 24 hour uses the existing clock format setting (System by default); a 12-hour clock shows no AM/PM marker.
- Artwork tint: not done (owner: optional, no).

## Not done here

Device verification: see docs/device-checklist.md, "Glance home (wave 16)".

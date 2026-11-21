# Wave 35 signalEffects: four signal effects in two opposite pairs

Owner: "the signal effect can be a dot bloom, animated dots, comic book lines or a coloured vignette. Coloured vignette and comic book lines are for inverse functions: they flare up when connectivity is low, and become urgent when connectivity is lost. User can select to map this to battery or signal, and two things can be turned on at once, but it can only be opposite pairs: dot bloom OR animated dots AND coloured vignette OR comic book lines." Earlier: the comic-lines alert is "like a spider-sense". Nothing here was run on a device.

## Model (core-data)
- `CalmEffect` (Bloom, AnimatedDots) and `AlertEffect` (Vignette, ComicLines). `LauncherPrefs.indicatorCalm: CalmEffect?` (default AnimatedDots) and `indicatorAlert: AlertEffect?` (default none): two nullable slots, so two effects of one group, or an old unrestricted set, cannot be stored. Either group may be off.
- Removed (owner policy: only the proposed options stay): Snake outline, Edge meter, the bloom-from-edges variant, the eight-way measurement list (Storage, Memory, Notifications, Volume, Time of day, Fixed colour), the "flip the ramp" row and `indicatorInvert` (nothing uses it now).
- `IndicatorMetric` is Signal or Battery. Pref `indMetric` keeps "Battery"; any other stored value (including the old "Network") reads as Signal.
- Prefs: `indCalm` / `indAlert` (enum name or "Off"). Retired: `indEffects` and `indInvert`.

## Migration
`IndicatorMigration.fromLegacy` reads the old `indEffects` comma list once: the first calm effect found (DotsBreathe -> AnimatedDots; DotsBloomCentre or DotsBloomEdges -> Bloom) and the first alert effect (Vignette, ComicLines); Snake outline and Edge meter read as nothing. A never-stored key keeps the default (Animated dots only). `LauncherPrefsStore` runs it at start through `TolerantPrefs`-opened prefs, writes `indCalm`/`indAlert` and removes `indEffects`/`indInvert` in the same edit; a settings import goes through the same read, so old backups migrate too. `RetiredPrefKeys`: `indInvert` is purged and dropped on import; `indEffects` is purged only when `indCalm` exists (`AFTER_MIGRATION`), because a purge that ran first would lose the choice; the marker file is now `retired_pref_keys_w35`. The settings export is by reflection over `LauncherPrefs`, so the new fields are carried (StateCodecTest).

## Severity (`IndicatorSeverity`, pure)
- Healthy line: Battery 60 percent; Signal 75 percent of the bar scale, which is level 3 of 0..4 ("good", 4 of 5 bars) or better, the existing level mapping of `SignalLogic` / `NetworkSignal.cellStrength`. At or above it: level 0, nothing drawn.
- Below it the level rises linearly to 1 at nothing: `(line - value) / line`. (The brief said "scale with (1 - value)"; this is the same ordering re-based so the alert starts from nothing at the healthy line instead of popping in at 40 percent.)
- Urgent (lost): Signal = no connectivity (reading kind Off); Battery = at or below 5 percent and not charging. Battery charging is always healthy. Airplane mode (an Idle reading) is deliberate and is not an alarm. A Signal reading with zero bars that is still connected is severe (level 1) but not urgent.
- `IndicatorColors.alertArgb`: the palette's Middle (amber) easing to its Low (red) as severity rises; urgent is always the fixed `URGENT_RED`.

## Drawing (`IndicatorLayer`)
- Calm: Dot bloom = the old "from centre" ring wave over the dot lattice; Animated dots = the unchanged breathing dots. Colour is the ramp colour as before; Off paints nothing.
- Alert strength = intensity x (0.35 + 0.65 x level); urgent is full strength (a slider at zero is still "off"). Urgent pulses about every 1.2 s (brightness 0.7 to 1).
- Coloured vignette: radial band whose clear centre shrinks (80 percent to 30 percent of the corner radius) and whose alpha rises with severity.
- Comic-book lines: the existing deterministic `ComicLines` math, unchanged at fraction 1; a new optional `fraction` keeps every n-th line evenly around the ring: 17 percent of the 72 lines when barely low up to all when lost, with a width multiplier 0.8x to 1.8x, and 2.2x when urgent. Low is a static frame; only urgent re-steps (8 frames a second).
- Clock: one `LaunchedEffect` frame loop through the infinite-animation policy (the `infiniteFrameNanos` pattern), started only when the animated dots/bloom are on or an alert is urgent, never under reduced motion. Healthy alert = no clock, no cost. Reduced motion urgent = one static, full-strength, non-pulsing frame, never hidden.
- The wallpaper's own comic lines (`SignalBackground`, no connectivity) are untouched here; that path belongs to the w34 wallpaper stream. With Signal mapped and the comic-lines alert on, both can show when offline; reconcile at merge (the alert layer is the one that follows the Alert effect row).

## Where it draws
`HomeScreen.Background()` draws `IndicatorLayer` on Glance (underlay) and on every page/scroll surface. Radar is a full-screen overlay with its own background; True black composes no `SignalBackground`, so `SearchOverlay`/`SearchScreen` gained an `alertLayer` slot, drawn right after `RadarBackgroundLayer` (above Dimmed's scrim, below the field and results), and `HomeScreen` fills it with the alert-only `IndicatorLayer` (calm null) for both Radar background modes.

## Settings
Look > Indicator: "Calm effect" (Off / Dot bloom / Animated dots), "Alert effect" (Off / Coloured vignette / Comic-book lines) with the one-line hint "Alert effects only appear when the value is low, and become urgent when it is lost.", "Show" (Signal / Battery), Glow intensity, speed, colours, and "Glow: preview": a simulated-level slider over a small box using the same layer (local state only; reduced motion respected by the layer).

## Not matched / open
- No device run: severity thresholds, the pulse feel and the vignette strength are judged from Robolectric stills only.
- Old English/translated strings of removed effects stay in the L10n tables (harmless, unused).
- `docs/Spec.md` PAL-24 and HOM-16 rows still describe comic lines as "no connectivity only"; not edited here to avoid conflicts with the wallpaper stream.

Wave 37: the Spec.md PAL-24 and HOM-16 rows now describe this ([specSync.md](specSync.md)).

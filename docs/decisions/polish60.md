# polish60 (wave 60, CFG-80): a Side panel naming collision, and Glance text over an arbitrary wallpaper

Source: the "Not done" item of `settingsPolish.md` (wave 58) — *"'Grouping & rules' and 'Groups and rules' are two rows with
near-identical names in Side panel > Rules and gestures; renaming either is a product wording change left to the owner."* — and the
"Screenshots read" note of `glanceTop.md` (wave 59) — *"The dark theme's light ink over plain white reads weakly (the ink, not the
fade; left as it is, see the decision note)."* The owner asked for "the next wave", not a question, so both calls are made here.

## 1. The naming collision

Two Side panel rows read as near-duplicates:
- **"Grouping & rules"** — a *pane*, under Home > Side panel > Rules and gestures. Opens `LaunchpadRulesPane`: the default grouping
  mode (per app, sender, conversation) and the rules list that sets delivery per app.
- **"Groups and rules"** — an *action*, in the same section. Opens Comms' own live group/rule manager overlay
  (`LaunchpadGroupsOverlay`): the people picker, members lists and the Priority groups' Do Not Disturb breakthrough, reached through
  the Comms nav request (`openComms(null, true)`).

They edit genuinely different things (static Settings prefs vs. a live, data-driven manager you work inside Comms itself), but the
titles differ only by "Grouping" vs. "Groups", which is not enough for a row scanned in half a second, let alone read out by
TalkBack back to back.

**Decision: rename the action to "Manage groups and rules"; keep the pane's name as it is.**

Reasoning:
- The pane's name already says what it configures ("Grouping & rules": a grouping mode and a rules list — both nouns, both static
  settings). It does not need to change.
- The action is a *verb* in disguise ("open the manager"); naming it as what it does ("manage") makes it read as the different,
  more specific thing it is, rather than a shorter twin of the pane above it. "Manage groups and rules" also now reads consistently
  with how the row's own hint already described it ("The manager in Comms").
- The alternative (renaming the pane to "Message grouping and rules" and leaving the action as "Groups and rules") was considered
  and rejected: the pane's name was already distinct enough on its own ("Grouping"), and the action's name was the one actually
  carrying no information about what tapping it does.
- The manager overlay's own header (inside Comms, reached either from this row or from Launchpad's own header menu) keeps the
  plain "Groups and rules" title: once you are already in it, there is no collision to resolve, and it is also reachable from a
  second place (Launchpad's own menu) this wave does not touch.

Changed: the Settings row (`SettingsCategories.kt`, `SettingsTabLayout.kt`'s section row list), its new L10n key
(`L10nW60polish60.kt`, 8 languages), the hidden search keyword table (`SettingsRowMeta.KEYWORDS`: the old title "Groups and rules"
is now a synonym of the new one, so existing muscle memory still finds it; a synonym was also added for "Grouping & rules" itself,
naming what it holds), and the tests that asserted the old title (`CommsOptionsW29Test`: the row-count list, the `row(...)` lookup,
the search-synonym case — which now also proves the old phrase still resolves to the new row through the keyword table — and the
translated-strings walk).

Not changed: `LaunchpadGroupsW24.kt`'s own overlay header and its "Close groups and rules" description (a different context, see
above), `L10nW24launchpadRules.kt` (still the source of that header's string), and the code comments in `HubModel.kt` /
`LaunchpadGroupsReviewW24Test.kt` / `LaunchpadGroupsEndToEndW24Test.kt` that describe the nav request in prose (one comment in
`Launchpad.kt` was touched for accuracy, since it names the row directly).

## 2. Glance text over a wallpaper the app cannot sample

The Glance music line and the big wallpaper clock draw their own ink (`GlanceInk.secondary` / `GlanceInk.clock`: a light grey in
the dark theme, a dark grey in the light theme) directly over the user's wallpaper. For three of the five wallpaper kinds (solid,
gradient, signal) the app paints the background itself, so it already protects this text: `HomeScrim`/`HomeLegibility` samples the
real painted colours and sizes a scrim so the ink keeps WCAG AA. For the other two kinds — `WallpaperKind.Picture` (a photo or an
animated GIF/WebP from the gallery) and `WallpaperKind.System` (the device's own live wallpaper, drawn behind a transparent
window) — `WallpaperSurfaces.kt` already says why there is no scrim: *"a picture or the system wallpaper has no colour the app
knows"*. A photo can be any luminance anywhere on screen, so no fixed scrim size would be both unobtrusive on an already-dark photo
and sufficient on an already-bright one. This is exactly the gap the wave 59 screenshot found: the light ink reads weakly over a
plain white background, in the dark theme, with nothing painted behind it.

**Decision: a tight, opaque halo behind the text, not a background-colour sample.**

Two options were on the table (see the brief): (a) a shadow/outline behind the text, independent of the wallpaper's colour, or (b)
sampling the wallpaper's luminance behind the text and switching the ink or adding a local scrim. (b) is already the mechanism the
app uses for the three paintable kinds, through `HomeLegibility`/`BackgroundSamples` — but by design it cannot reach a picture or
the system wallpaper (nothing in the launcher process can read system wallpaper pixels or decode an arbitrary user photo for every
frame without cost and without flicker as the picture animates or the system wallpaper changes). So (a) is the only one that works
for *every* wallpaper kind, including the two this app cannot sample, and it is also simpler: it needs no live sampling, no
per-wallpaper-kind branch, and no interaction with the Crossfade/animation that already drives wallpaper switches.

The clock (`ClockBlock.kt`) already had a step in this direction: a single `Shadow` on its `TextStyle` (a soft, offset glow).
The music line (`NowPlayingLine` in `GlanceHome.kt`) had none at all — which is why the wave 59 screenshot caught only the music
line reading weakly, not the clock. Reusing and strengthening the existing technique (rather than inventing e.g. a duplicate
stroked-text layer) keeps one legibility mechanism for both:

- `GlanceInk.shadow(dark)` (feature-grid, `GlanceHome.kt`): a `Shadow` of the **opposite tone of the ink** (black behind the dark
  theme's light ink, white behind the light theme's dark ink), **centred** (`Offset.Zero`, not the old `Offset(0f, 2f)` — a
  marquee line moves, so an offset shadow reads differently at its two edges; a centred halo does not), at `alpha = 0.85f` and
  `blurRadius = 10f`. At that alpha, a pixel immediately next to a glyph is overwhelmingly the halo colour over whatever the
  wallpaper is: blended against literal white or black it still clears a contrast ratio of about 12:1 against the ink (WCAG's
  "large text" floor is 3:1), with wide margin for the blur's edges being less than fully opaque.
- `ClockBlock.kt`'s local `shadow` val is now `GlanceInk.shadow(dark)` (same halo, same constant, replacing its own weaker one).
- `NowPlayingLine` now takes a `dark: Boolean` (defaulting to `isDarkSurface()`, same as every other Glance text) and builds its
  `TextStyle` with `shadow = GlanceInk.shadow(dark)`, applied to both the measuring style (so wrapping/overflow are unaffected —
  a shadow changes no glyph metric) and the drawn `Text`. `FixedHomeChrome.kt`'s call site passes its own `dark` through.
- The wave 59 marquee edge fade (`fadeClippedEdges`, an alpha mask with `BlendMode.DstIn`) is untouched: it still fades the TEXT
  (now including its halo) to transparent at a clipped edge, so the underlying wallpaper still shows through there unchanged. The
  halo and the fade are independent layers (ink + its shadow, then an alpha mask over the whole composited result).

Tested: `GlanceLegibilityW60Test` (pure WCAG math on the engineered `alpha`/colour values, mirroring `core.quality.Contrast`'s
formula since feature-grid does not depend on core-quality — the same pattern `GlanceTopFadeW59PixelTest` already uses for its own
luminance helper) proves the halo clears 3:1 for both the clock's and the music line's ink, over literal white and black, in both
themes. `GlanceLegibilityW60PixelTest` (Robolectric, `PixelCopy`, the same harness as `GlanceTopFadeW59PixelTest`) renders the
music line and the clock over a full white and a full black background in the theme where that background is the hard case (white
in dark theme, black in light theme), screenshots them (`build/screenshots/w60_*.png`, read and judged: both read clearly, the
halo visible as a soft ring around every glyph), and asserts the halo actually changed pixels under the text (not just that the
math says it should).

## Not done
- No device run: the look over a real photo or the system's own live wallpaper (the two kinds this fix targets) is not seen on a
  phone. Nothing here is device-confirmed.
- The halo is one fixed strength for every wallpaper; it was not tuned per-photo (that would need exactly the live sampling this
  decision avoids for Picture/System kinds).
- `LaunchpadGroupsW24.kt`'s own overlay header keeps the plain "Groups and rules" title (see above); if the owner later wants the
  destination's header to match the Settings row that opens it, that is a further, separate rename.

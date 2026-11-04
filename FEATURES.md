# Sphere Launcher — Feature Guide

A comprehensive guide to the features implemented in this build (M0–M2) and their interactions.

---

## Core Experience

### The Sphere Equator (Home Surface)
The primary home screen is a **coverflow equator** — a row (or two rows in portrait) of app tiles rendered with 3D perspective and a magnification lens at the front-center.

- **Scrolling:** Drag horizontally to scrub through apps. Release snaps the nearest tile to the center.
- **Launching:** Tap the center tile (the magnified one) to launch it. Tap any other tile to bring it into focus first.
- **Positioning:** The surface always rests with exactly one tile at the focal point (dead-center, magnified).

### Tiles Mode
In the Settings panel, toggle **Tiles** to switch the visual rendering:
- **OFF (default):** Minimalist icons in rounded cards with focus labels below the surface.
- **ON:** Larger, glassier tiles with inset highlights, matching the prototype's Tiles aesthetic.

This is purely visual; all interactions remain the same.

### Per-Band Spin (Rubik's Cube Mode)
Toggle **Per-band spin** in Settings:
- **OFF (default):** Whole-sphere mode — dragging spins the entire equator as one unit (like spinning a globe).
- **ON:** Per-band mode — dragging spins only the row/band under your finger; other rows stay stationary (like a Rubik's cube).

Vertical drags always scroll between rows in both modes.

---

## All-Apps Globe

**Gesture:** Pinch inward (two fingers, move closer together) to zoom into the all-apps globe.

The globe is a **Fibonacci-sphere distribution** of every app in your inventory, rotatable in 3D.

- **Rotate:** Once zoomed in, drag to rotate the globe freely.
- **Magnification lens:** The same focus/lens mechanic applies — the app closest to the front is magnified.
- **Tap to launch:** Tap any app on the globe to launch it.
- **Zoom out:** Pinch outward (two fingers, move apart) or spread to return to the equator.

Optionally, from the globe, **spread** (two fingers, move apart) will open the **Settings panel** instead of closing the globe — useful for real-time tuning while viewing the all-apps layout.

---

## Settings Panel

Access by tapping the **gear icon** (top-right corner, landscape) or via the Spread gesture from the globe.

### Surface Section
Tunes the equator's visual and interaction feel:
- **Tiles:** Toggle visual mode (see above).
- **Per-band spin:** Toggle Rubik's cube mode (see above).
- **Perspective:** How much 3D depth is rendered (lower = flatter, higher = more exaggerated).
- **Spacing:** Horizontal distance between columns (affects the scrub feel).
- **Turn:** How much each tile rotates away from the viewer (card turn angle).
- **Depth:** Depth multiplier; how far tiles recede into the z-axis.
- **Min scale:** Smallest tile size (side tiles scale down; higher = less shrink).
- **Focus:** Magnification ratio of the centered tile (higher = more magnification).
- **Arc:** (Advanced) Spherical arc curvature of the row layout.
- **Tilt:** (Advanced) Vertical tilt of the row.

All changes apply **live** — the equator updates as you drag each slider.

### Rows Section
- **Auto:** In portrait, render 2 rows; in landscape, render 1 row.
- **One:** Always 1 row (landscape-style layout).
- **Two:** Always 2 rows (portrait-style layout).

Changing rows preserves the focused app — it remaps so the same app stays centered.

---

## Type-to-Search (Portrait)

In **portrait orientation**, a keyboard is always visible at the bottom (collapsible).

- **Type:** As you type, the app list filters in real-time using a smart ranking algorithm.
- **Ranking:** Exact matches > prefix matches > word-prefix matches > substring matches.
- **Launch:** Press Enter or tap the top result to launch the first match. The query clears after launch.
- **Collapse:** Tap the chevron (⌄) to collapse the keyboard and reclaim screen space.
- **Focus:** The equator always stays visible and interactive even while typing.

---

## Hub (Notifications)

**Gesture:** Pull down from the **top edge** to reveal the Hub — a glass panel that slides down from above.

The Hub displays a triaged notification feed ("Needs you"):
- **Messages:** Direct messages (from the demo feed; real NotificationListenerService integration is M3+).
- **Calendar:** Upcoming events and standups.
- **App Store:** Available app updates.

Each notification card has:
- **Action buttons:** Contextual responses (reply, join, snooze, update, dismiss).
- **Dismiss:** Swipe left or tap the "Dismiss" button to remove a notification.
- **Pull down more:** Drag further down to reveal more cards; release or tap "Show all" to view the full feed.

**Release & latch:** Release the Hub — it snaps closed if you released less than 40% down, or stays open if further.

---

## Chrome (Ambient Information)

The **chrome** layer provides glanceable information without demanding attention:

### Landscape
- **Clock:** Top-center, displays HH:MM.
- **Weather:** Top-left, current temperature and condition (demo: "24° Partly cloudy").
- **Wi-Fi:** Left edge, thin vertical bar showing signal strength (cyan = good, orange = weak).
- **Battery:** Right edge, vertical bar showing battery level (green = charging, white = normal, red = low).
- **Now Playing:** Bottom-left, scrolling now-playing track title.

### Portrait
- **Hero time:** Very large clock (76sp, light weight) at the top with the date and weather below.
- **Now-playing, Wi-Fi, battery:** Hidden in portrait to save space.

### Fading
When the **hub** is pulled down or the **globe** is zoomed in, the chrome fades out gracefully to avoid visual clutter. It re-appears as you collapse the hub or zoom out.

---

## Voice (M4+ Scaffold)

Voice input hooks are in place for M4+ integration but are **not yet functional** in this build.

The architecture supports:
- **On-device speech recognition** (Android SpeechRecognizer API with RECORD_AUDIO permission, M4+).
- **Voice navigation:** Speak an app name and the surface rotates to bring it to the center.
- **Voice commands:** "Open Settings", "Uninstall Instagram", etc. (requires command parser, M4+).

A mic button and voice indicator are scaffolded in the UI but do not function yet. The integration point is in `:app/ui/VoiceAssistant.kt`.

---

## App Management

**Long-press any app tile** to open the context menu:
- **App info:** Opens the system App-info screen (battery, storage, notification settings, etc.).
- **Hide:** Removes the app from all views (coverflow, globe, search). Hidden apps can be un-hidden from Settings.
- **Uninstall:** Fires the system uninstall flow.

Apps can be un-hidden in a future UI (currently managed via SharedPreferences internally).

---

## Hidden Apps

A **hidden apps set** is persisted locally (SharedPreferences). Hidden apps are filtered from:
- The coverflow equator.
- The all-apps globe.
- Search results.

They are recoverable (no permanent deletion) — a future "Manage Hidden Apps" UI will allow bulk un-hiding.

---

## Orientation & Layout Responsiveness

The launcher adapts to **landscape** (primary) and **portrait** (secondary) modes:

### Landscape (Primary)
- 1 row of apps (default Auto mode).
- Chrome at the top and edges.
- Settings panel slides in from the right.
- Hub pulls from the top.
- Keyboard hidden; search is a future global feature.
- All gestures available.

### Portrait (Secondary Keyboard-First)
- 2 rows of apps (default Auto mode) plus the always-visible keyboard.
- Hero clock at the top.
- Keyboard at the bottom (collapsible).
- Type-to-search filters live as you type.
- Settings panel slides from the right (keyboard remains visible).
- Narrower gesture detection for scrub (due to keyboard taking vertical space).

**Orientation changes preserve state:** The focused app stays focused, and the row split remaps so the same app remains in focus (column index remaps: 1↔2 rows).

---

## Known Limitations (M1–M2)

- **Voice is scaffolded but non-functional** (awaiting M4 integration).
- **App arrangements are limited to Bands** (Fibonacci sphere works but Usage, Recency, A–Z, Colour are scaffolded only).
- **Hub is a demo feed** (real NotificationListenerService integration is M3+).
- **Widget hosting is new and unverified on a device** (see *Widgets (hosting)* below).
- **Pages are new and unverified on a device** (see *Pages* below).
- **No local search** beyond type-to-search in portrait.
- **No haptics** (vibration vocabulary is defined but not wired; M4+).
- **No AGSL materials** (shader glow effects scaffolded for API 33+, not yet rendered).

---

## Widgets (hosting)

Sphere hosts other apps' standard App Widgets. Full design, touch model and the owner-verify list:
[`docs/widget-hosting.md`](docs/widget-hosting.md). **Not yet verified on a device.**

- **Add:** Settings → **Widgets → Add widget**, long-press an app → **Add widget here**, long-press
  empty surface, or **+ Add widget** in the hub. The picker lists every installed widget (work
  profile included, tagged in words), grouped by app, with preview, description and size in cells,
  and a local filter. Choose **Equator** or **Hub**.
- **Binding:** if Android asks, the system "allow Sphere to create widgets" prompt appears; widgets
  that need setup open their configure screen. Cancelling either leaves nothing behind.
- **On the equator:** widgets are tiles between apps. Tap an unfocused widget to focus it; the
  focused widget takes taps itself (its buttons and list rows); dragging still scrubs; long-press
  opens the widget menu.
- **In the hub:** pull the hub down; widgets sit in a full-width stack under "Needs you" at Small /
  Medium / Large height, with normal touch. The ••• button opens the widget menu.
- **Widget menu:** Reconfigure (when the widget supports it), Move to hub / equator, height in the
  hub, Remove widget.
- **Unavailable widgets** (mainly: restored before their app is installed, or in safe mode) show
  "Widget unavailable" with a Remove button. Widgets whose app is uninstalled or disabled are
  removed (Android deletes them).
- Placements are saved locally (SharedPreferences) and restored after restarts and reboots. After a
  backup restore to a new phone they show "Restoring widget" until Android hands over the
  restored widgets' new ids.
- **Known limitation:** an equator widget keeps its position by index, so installing, removing,
  hiding or unhiding an app that sorts before it gives it a different neighbour.

---

## Sphere's widgets for any launcher

Sphere provides five home-screen widgets you can place in any launcher (Pixel, One UI, Nova, or
Sphere itself), also when Sphere isn't your home app. Full detail and the owner-verify list:
[`docs/widgets.md`](docs/widgets.md). **Not yet verified on a device.**

- **Gate clock:** time; date; next alarm. Tap opens Sphere.
- **Battery:** level, state (glyph + word), level bar, time to full, "as of" time. Tap reads it again.
- **Now playing:** which app is playing and its state, with previous / play-pause / next at the large
  size. App name only by default; the option "Show track and artist" adds the title. An app hidden in
  Sphere is never named. Needs Sphere's notification access; says so when it's off.
- **Needs you:** how many notifications wait, per app (never their content; a work-profile app is
  its own row); apps hidden in Sphere are never named and get no row (they count in the total).
  Rows open their app; the card opens Sphere's hub. Option: counts only.
- **App pads:** up to six apps you choose (work profile too), one tap each.

Each resizes through small, medium and large layouts, stepping down a size at larger font scales
rather than clipping. They update while Sphere is running; when it isn't, they keep their last state,
and Battery, Now playing and Needs you print at every size the time that state was drawn.

---

## Pages

Home is page 0 of a scrollable set of pages. Full design, runtime rules and the owner-verify list:
[`docs/pages.md`](docs/pages.md). **Not yet verified on a device.**

- **Kinds of page:** a full-page **App Widget** (any app's, bound with the usual picker / prompt /
  configure flow, told the page's real size); an **HTML widget or page** run by the Orrery widget
  runtime (a D-AC widget package, `.zip` or folder, or a single `.html` page such as Orrery's own
  build, which appears as "Orrery"); a **website** (any http or https address).
- **Manage pages:** Settings → Pages → **Manage pages**, or long-press the page dots: add, rename,
  reorder (▲ / ▼), page info, reconfigure (App Widget pages that allow it), remove. Home is always
  first and can't be removed.
- **Move between pages** (Settings → Pages), three modes:
  1. **Two-finger swipe + dots** (default): swipe sideways with two fingers on any page, over any
     content. Pinch and spread on the coverflow still open the globe and settings.
  2. **Swipe past the coverflow's ends:** on home, from the last app, pull past it (a hint names the
     page) and release (a stroke from the middle of the list that overshoots the end doesn't count);
     on other pages, swipe inward from just inside the screen's edge.
  3. **Vertical pages (swipe up):** on home, swipe up (pull down still opens the hub); on other pages,
     two-finger vertical swipe.
  The **page dots** always work (a row at the bottom; tap a dot; the filled dot is the current page;
  TalkBack names the page after each turn). The home key goes back to page 0; back on a website goes
  back in its history first, then home.
- **HTML pages and widgets** run sandboxed, each at its own origin with its own storage: no permissions,
  no popups (a page's links leave for your browser or a chooser), no downloads, dialogs only from the
  page on screen. Widget packages never reach the network and never open a link or an app by
  themselves (only a declared route, right after you tap them). Removing a page clears what it stored. A single-file page starts with **no network**; its info has the switch **"Allow this page to
  reach the network"** and lists the hosts it reached or was refused; while it is on, a "Network on"
  tag sits beside the dots. With it off, Orrery's connectors and Google Fonts don't load (system fonts
  are used) and its saved state stays.
- **Websites** (http sites load too) show their host with an https / not-https mark (padlock shape and
  word), Back and Reload. Third-party cookies are off; permission requests are refused; downloads are refused.
  **Safe Browsing is off** (the owner's decision): there is no warning before a known-malicious site.
- **Memory:** only the current page and its neighbours keep a live WebView; others are rebuilt from
  their saved state or address when you come back. Off-screen pages are paused and give up the
  keyboard.
- **Backups** leave out website logins and HTML pages' saved state (Orrery's included): those stay on
  the phone.

---

## Settings Persistence

Currently, **only hidden apps are persisted** (SharedPreferences). Surface parameters (perspective, spacing, etc.) reset on app restart. For M2+, consider persisting these to a JSON preferences file or local database if the user wants to save custom configurations.

---

## Accessibility

Basic accessibility is in place:
- **Content descriptions:** All app tiles and interactive elements have labels for screen readers.
- **Semantic structure:** Buttons, toggles, and sliders are marked with semantic meaning.
- **Future improvements:** Voice navigation (M4+) will allow full hands-free operation.

---

## Debugging & Telemetry

No telemetry or crash reporting is wired up. For troubleshooting:
- Check Logcat for exceptions (most errors are caught with `runCatching`).
- Test with a varying number of apps (10, 100, 1000+) to check performance.
- Toggle Tiles, Per-band spin, and row modes to isolate rendering vs. gesture issues.

---

## Next Steps (M3+)

- **M3:** Real NotificationListenerService feed, contextual cards (weather, travel, location).
- **M4:** Voice input, command palette, on-device inference, haptics, AGSL materials, provenance glow.
- **M5:** Desktop / DeX support, external display layout, keyboard/trackpad, multi-window.

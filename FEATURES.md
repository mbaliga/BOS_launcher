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
- **No widgets** (M4+).
- **No local search** beyond type-to-search in portrait.
- **No haptics** (vibration vocabulary is defined but not wired; M4+).
- **No AGSL materials** (shader glow effects scaffolded for API 33+, not yet rendered).

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

# Sphere Launcher — Architecture & Code Organization

High-level design overview and module responsibilities.

---

## System Architecture

The launcher is built in **Kotlin + Jetpack Compose** with a modular architecture:

```
app (MainActivity, HomeScreen, UI chrome)
├── feature-sphere (coverflow, globe, gesture arbitration)
├── feature-search (type-to-search, app ranking)
├── core-data (app inventory, system integration)
├── core-design (Hyle tokens, type, motion, colors)
└── shaders (AGSL capability gating)
```

### Key Principle: Unidirectional Data Flow
- **State flows down:** HomeScreen hoists the surface state, passes it to LauncherSurface, which reads it frame-to-frame.
- **Events flow up:** Gestures and user actions bubble up as callbacks (onLaunch, onOpenSettings, etc.).
- **Persistence:** Only the hidden-apps set is persisted (SharedPreferences); surface parameters are ephemeral.

---

## Module Breakdown

### `:app` — Single-Activity Host & Chrome
**Responsibility:** The launcher container and chrome (clock, weather, battery, hub, settings).

**Key files:**
- `MainActivity.kt` — Single Activity, manifest HOME + DEFAULT roles, home-key reset via `onNewIntent`.
- `HomeScreen.kt` — Root Composable, orchestrates the surface, hoists state (params, tilesMode, rubikMode), hosts all overlays.
- `ui/Chrome.kt` — Clock hero (landscape), weather, battery edge bars, now-playing ticker, network indicator.
- `ui/HubPanel.kt` — Pull-down notification shade with dismissable cards.
- `ui/SettingsPanel.kt` — Live surface parameter tuning (perspective, spacing, turn, depth, focus, row mode, tiles, per-band spin).
- `ui/AppContextMenu.kt` — Long-press menu (app info, hide, uninstall).
- `ui/DotGridBackground.kt` — Hyle dot-grid substrate (Breath animation).
- `ui/VoiceAssistant.kt` — Voice input scaffold (M4+ integration point).

**State ownership:**
- `params: SphereParams` — Surface tuning (perspective, spacing, turn, depth, minScale, arc, tilt, focus).
- `rowMode: RowMode` — Row split (Auto, One, Two).
- `tilesMode: Boolean` — Tiles visual mode toggle.
- `rubikMode: Boolean` — Per-band spin toggle.
- `settingsOpen: Boolean` — Settings panel visibility.

### `:feature-sphere` — 3D Coverflow & Globe
**Responsibility:** Interactive 3D surface (coverflow equator + all-apps globe) and unified gesture handling.

**Key files:**
- `SphereGeometry.kt` — Magnification-lens math (exact port from the prototype), column spacing integral.
- `SphereState.kt` — Coverflow state machine: position (scrub), rows, transforms per tile, snap bounds.
- `TileTransform` data class — Per-tile 3D transform: translation, scale, rotation, opacity, z-index.
- `Coverflow.kt` — Render-only equator layer; applies transforms via `graphicsLayer`.
- `Globe.kt` — Fibonacci-sphere all-apps globe with rotation and magnification lens; hit-testing for taps.
- `GlobeState.kt` — Globe rotation state (yaw, pitch in radians).
- `LauncherSurface.kt` — Unified gesture handler arbitrating scrub, hub pull, globe rotation, pinch, long-press, and tap. Manages `zoom` (home ↔ globe) and `hub` (pull-down) animations.
- `rememberLauncherSurfaceState()` — Creates the hoisted state composable.

**Gestures (unified in LauncherSurface):**
- Horizontal drag → scrub equator (snap on release) OR rotate globe.
- Vertical drag → pull hub or scroll rows.
- Pinch in → zoom to globe.
- Spread / pinch out → zoom back to equator or open settings.
- Tap → focus/launch (equator) or launch (globe).
- Long-press → context menu.

### `:feature-search` — Portrait Keyboard & Type-to-Search
**Responsibility:** Portrait-only always-open keyboard and live app search.

**Key files:**
- `SearchKeyboard.kt` — Portrait keyboard layout, live filtering, result scrolling, Enter-to-launch.
- `AppSearch.kt` — Search ranking algorithm: exact → prefix → word-prefix → substring.

**Search ranking (prototype §2.B):**
- Exact match (e.g., "Slack" matches "Slack" exactly) → score 0.
- Prefix match (e.g., "sla" matches "Slack") → score 1.
- Word-prefix match (e.g., "sla" matches "Slack for Teams" at "Slack") → score 2.
- Substring match (e.g., "lac" matches "Slack") → score 3.
- Results sorted by score, then by label length, then by label (A–Z).
- Top 14 results shown; unique match auto-launches on Enter.

### `:core-data` — App Inventory & System Integration
**Responsibility:** Live app inventory from LauncherApps and user-facing app management.

**Key files:**
- `AppRepository.kt` — Abstract interface and LauncherAppsAppRepository implementation.
  - `apps: Flow<List<AppEntry>>` — Live inventory, re-emits on install/remove/update.
  - `launch(entry)` — Start the app's main activity.
  - `openAppDetails(entry)` — System App-info screen.
  - `requestUninstall(entry)` — System uninstall flow.
- `AppEntry.kt` — Immutable app metadata: package name, component name, label, user handle, icon loader.
- `HiddenAppsStore.kt` — Persisted hidden-apps set (SharedPreferences), exposed as a Flow.

**Design notes:**
- Uses Android's `LauncherApps` API (no root, no Shizuku).
- Work-profile aware (lists apps from all user profiles).
- Icons are loaded on-demand via Drawables, cached in Compose (prevents memory churn).

### `:core-design` — Hyle Design System
**Responsibility:** Tokens, motion, typography, colors, and shapes.

**Key files:**
- `Color.kt` — Hyle palette: Violet accent, background/surface/ink, hairlines, provenance (on-device warm radium / cloud cyan).
- `Motion.kt` — Eight named motion primitives (Breath, Stir, Reform, Reach, Erupt, Coalesce, Give, Settle) mapped to Compose AnimationSpecs.
- `Type.kt` — Hyle typography: display (clock hero), headline (widget numbers), body (labels), label (chrome).
- `Shape.kt` — Corner radii: large (app tiles, hub), medium, small (buttons, chips).
- `Theme.kt` — SphereTheme Composable wrapping Material3 with Hyle overrides.

**Provenance system (M4+ visualization):**
- `ProvenanceOnDevice` — Warm radium green for on-device computation.
- `ProvenanceCloud` — Cyan for cloud computation.
- Surfaces tint by where AI computation happened (assistant response, search indexing, etc.).

### `:shaders` — AGSL Capability Gating
**Responsibility:** GPU-accelerated materials for API 33+ with fallback for older devices.

**Intent (M4+):**
- Runtime shader checks (API 33+).
- Glow/bloom effects for focused tiles.
- Provenance-tinted surfaces.
- Fallback flat rendering for API 31–32.

**Current status:** Scaffolded, not yet rendered.

---

## Data Flow

### Startup
1. `MainActivity` launches single Composable.
2. `HomeScreen` root creates:
   - `LauncherAppsAppRepository` (subscribes to LauncherApps.Callback).
   - `HiddenAppsStore` (loads persisted hidden set).
   - `rememberLauncherSurfaceState()` (coverflow + globe state).
3. `repository.apps` Flow emits initial inventory.
4. HomeScreen filters apps (hidden set), passes to LauncherSurface.
5. Coverflow renders tiles from SphereState transforms.

### Interaction → Launch
1. User taps a tile.
2. LauncherSurface gesture handler detects tap, calls `onLaunch(app)` callback.
3. HomeScreen receives callback, calls `repository.launch(app)`.
4. AppRepository calls `launcherApps.startMainActivity()`.

### Inventory Change
1. User installs/removes/updates an app (system event).
2. LauncherApps.Callback fires.
3. AppRepository.apps Flow re-emits updated list.
4. HomeScreen re-composes with new apps.
5. SphereState re-computes transforms.
6. Coverflow re-renders.

### Settings Tuning
1. User opens Settings panel (spread gesture or gear icon).
2. User drags a slider (e.g., Perspective).
3. SettingsPanel calls `onParamsChange()` callback with new SphereParams.
4. HomeScreen updates `params` state.
5. SphereState reads new params, recomputes transforms for next frame.
6. Coverflow applies updated transforms immediately (live preview).

---

## Performance Considerations

### Frame Rate
- Gesture handling runs on the main (Compose) thread.
- `LauncherSurface` computes transforms each frame (O(n) where n = app count).
- Coverflow rendering via `graphicsLayer` is GPU-accelerated.
- **Expected performance:** 60 FPS with <200 apps on most devices (2020+).

### Memory
- Icons are cached by Compose's Image composable (keyed by app.key).
- Large icon bitmaps are released when apps scroll off-screen.
- AppRepository Flow emissions do not leak (callback unregistered on close).

### Optimization Opportunities
- Memoize SphereState transforms if > 500 apps (use `derivedStateOf`).
- Paginate icon loading (load visible tiles first, preload adjacent, lazy-load far rows).
- For 1000+ apps, consider a "search-first" UX (default to searching rather than scrolling).

---

## Testing & Debugging

### Unit Tests (Future)
- `SphereGeometry`: Test magnification curves and spacing integrals against prototype reference.
- `AppSearch`: Test ranking algorithm with mock app lists.
- `SphereState`: Test row remapping (1↔2 rows) preserves focused app.

### Integration Tests (Future)
- Install 10, 100, 500 apps via ADB; verify smooth rendering and search speed.
- Test orientation changes (portrait ↔ landscape) preserve state.
- Test hidden-apps persistence across app restarts.

### Manual Testing
1. **Inventory:** Install/remove apps mid-session; verify LauncherApps updates.
2. **Gestures:** Tap, drag, pinch, long-press each in landscape and portrait.
3. **Settings:** Adjust each slider; verify equator responds instantly.
4. **State:** Press home key; verify hub/globe collapse back to equator.
5. **Search (portrait):** Type queries; verify ranking and auto-launch.

---

## Future Architecture (M3–M5)

### M3 — Hub Expansion
- Integrate real NotificationListenerService.
- Contextual cards: weather, travel, calendar, location-aware.
- Requires new `:feature-hub` module with card providers.

### M4 — AI & Identity
- `:feature-assistant` — Command palette, voice input, intent routing.
- On-device inference (Urbana integration).
- Haptics framework (wired to motion primitives).
- AGSL materials (glow, provenance tint).
- Requires `:haptics` module and shader rendering in `:shaders`.

### M5 — Desktop
- External display layout (landscape optimized, multi-window stubs).
- Keyboard/trackpad support.
- Requires `:feature-desktop` module with new gesture/input models.

---

## Key Design Decisions

1. **Unidirectional data flow:** Keeps state mutations predictable; easy to trace bugs.
2. **Compose-only rendering:** No custom Canvas drawing; leverage GPU acceleration via `graphicsLayer`.
3. **Prototype fidelity:** Every interaction and visual matches the HTML prototype exactly to ensure the native build *feels* identical.
4. **No root, Play Integrity intact:** Uses public LauncherApps API; no hidden/privileged surface.
5. **Hyle identity:** Consistent motion, color, and haptics language; easier to extend and brand.
6. **Modular architecture:** Each feature (sphere, search, data, design) is its own module; can be developed/tested independently.

---

## Dependency Graph

```
app
├── feature-sphere (LauncherSurface, Coverflow, Globe)
├── feature-search (SearchKeyboard)
├── core-data (AppRepository, LauncherApps integration)
├── core-design (Colors, Motion, Type)
└── shaders (API 33+ capability)

feature-sphere
└── core-design

feature-search
└── core-data

core-data
└── (Android Framework: Context, LauncherApps, UserManager, SharedPreferences)

core-design
└── (Material3 Compose)
```

All modules depend on:
- Kotlin stdlib + coroutines.
- Androidx Compose (foundation, material3, activity).
- Androidx lifecycle (for state collection).

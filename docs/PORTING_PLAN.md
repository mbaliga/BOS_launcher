# Sphere Launcher — multi-platform porting plan

> Part of the constellation-wide porting program (`Personal-Tracker/PORTING_PROGRAM.md`, 2026-10-06).
> Status: **PLAN — nothing in this document has been built.** Every claim about a target platform is
> labelled with its evidence class (§0). Written against `main` @ `5db00d4` on 2026-10-06. This file is
> owned by the lead planning session; a platform track updates only its own §4 row and appends dated
> entries to this repo's own state file (`STATE.md`).
>
> **Naming.** The docs say "Sphere Launcher", the repo says "BOS"; the naming brief is owner-gated
> (`Personal-Tracker/NAMES.md`). This plan says "the launcher" and, for the desktop reframe, uses the
> descriptive label "sphere app launcher". Neither is a product name, package name or bundle id (program R11).
>
> **"Urbana inference" is a resolved naming conflation.** `ARCHITECTURE.md` (M4), `HANDOFF.md` and a KDoc in
> `core-data/.../VoiceInputManager.kt:27` mention "Urbana". The registry has ruled that Urbana is the Hyderabad
> urban-planning app with no inference role, and that the on-device/cloud model router is **asystemofmodels**
> (`NAMES.md`; `DECISIONS.md` D-N). This plan reads every such mention as asystemofmodels and does not edit those files.

## 0. Evidence labels (never dropped)

asom's set, unchanged: `LAB` · `CI (hosted VM) evidence` · `EMULATOR EVIDENCE` · `SIMULATOR` · `CI-APPROX — NOT DEVICE EVIDENCE` · `SIMULATED — NOT DEVICE EVIDENCE` · `VIRTUALIZED — NOT DEVICE EVIDENCE` · `SYNTHETIC` · `CI-ONLY / NOT RUN` · `NEEDS-DEVICE-VALIDATION` (NDV) · `NEEDS-OWNER-VALIDATION` (NOV). Program additions: `PLAN` · `NOT-APPLICABLE (<reason>)` · `CONTAINER-BUILD-ONLY` (this container: JVM x86_64 compile/tests, nothing else) · `BROWSER-HEADLESS`.
This plan uses `CI (hosted VM)` as the short form of `CI (hosted VM) evidence`.

Every §4 row is `PLAN` today. On-device behaviour of the Android app is owner-verified on the phone (`STATE.md`);
nothing here changes that. The planning container has no Android SDK, device or emulator, so even the repo's own
Android gate cannot run there (program §9).

## 1. What this repo is, in porting terms

**Product.** A landscape-first Android home launcher in Kotlin + Jetpack Compose (no AOSP fork): a coverflow
"equator" of app tiles with a continuous magnification lens, a pinch-in all-apps Fibonacci-sphere globe, portrait
type-to-search, a pull-down notification hub, live surface sliders, voice search and long-press app management
(`README.md`). **State** (`STATE.md`, the newest source): M0–M4 done, `v0.1.0-m1`, M5 (desktop / DeX large-screen
mode) pending; set-as-default, gestures, globe, search, hub and voice are owner-confirmed on the phone. **Targets
today:** Android phone (minSdk 31, targetSdk 35) and, planned as M5, an Android external-display layout. That is the
only "desktop" in the repo's vocabulary; it is Android large-screen work, not a desktop-OS port. The prototype
`docs/spherelauncher13.html` (493 lines, CSS 3D, no WebGL) is the visual source of truth (`HANDOFF.md`).

**Why every non-Android target is a reframe.** The product category, a replacement home screen, exists only on
Android: a HOME intent filter, `RoleManager.ROLE_HOME`, `LauncherApps`, `NotificationListenerService`, and a
wallpaper-backed window. No other target offers it to a third party, so every row in §4 is a reframe (program R12)
and the product-shape question (program OQ-6, here Q1) gates all of them.

**Repo state worth knowing before planning.**
- `DECISIONS.md` D-P/D-Q say crash-recovery PR #3 bumped AGP to 8.9.1 and added `build-apk-release.yml`. Neither is on
  `main`: `gradle/libs.versions.toml` pins AGP 8.7.3 and the only workflow is `.github/workflows/cleanup-artifacts.yml`.
  PR #3 is an open draft held for owner device verification (D-P); its branch is not fetched in this checkout, so its
  diff was not read.
- `FEATURES.md` still calls voice non-functional and the hub demo-only; `STATE.md` says M3 and M4 are done. This plan
  follows `STATE.md`.
- There is no LICENSE (D-I, owner-gated), no THIRD_PARTY file, no CI that builds the APK on `main`, and zero tests.
  One build-session artefact, `.kotlin/sessions/*.salive`, is tracked and `.kotlin/` is not in `.gitignore`.

**Stack.** Kotlin 2.1.0 (JVM 17) · Gradle 8.11.1 wrapper · AGP 8.7.3 · Compose BOM 2024.12.01 + Material3 ·
activity-compose 1.9.3 · lifecycle 2.8.7 · coroutines 1.9.0 · version catalog `gradle/libs.versions.toml`,
configuration cache on. Six Android modules: `:app` (application) and the libraries `:core-design`, `:core-data`,
`:feature-sphere`, `:feature-search`, `:shaders`. No native code, no KMP, no Room, no Hyle dependency (the palette is
re-implemented). The build needs the Android SDK (platform 35); **no pure-JVM build path exists** (`README.md` Building).

**Size, measured 2026-10-06.** 29 Kotlin files and 2,857 LOC
(`git ls-files | grep '\.kt$' | xargs wc -l`); 59 tracked files (`git ls-files | wc -l`); 0 test files
(`git ls-files | grep -iE '(test|androidTest)' | wc -l`); one 493-line HTML prototype (`wc -l`).

## 2. Portable core vs platform-bound layers

| Module / dir | Role | Portability | Approx LOC | Notes |
|---|---|---|---|---|
| `:core-design` | Hyle-like palette, eight motion primitives as `AnimationSpec`s, type, shapes, `SphereTheme` | kmp-common candidate | 225 | 0 `android.*` imports; only compose ui / animation / material3 / foundation. This is the tracked Hyle-consumer debt (README "Do not touch"). |
| `:feature-sphere` | `SphereGeometry` lens math, `SphereState`/`TileTransform`, `CoverflowLayer`, `Globe`, `LauncherSurface` (one arbitrated gesture handler) | kmp-common with two seams | 842 | 0 `android.*` imports. Seams: `androidx.core ... toBitmap` at `Coverflow.kt:104` and `Globe.kt:134`, and `AppEntry.loadIcon(): Drawable`. `java.lang.Math` at `SphereGeometry.kt:24`, `SphereState.kt:31,119`, `Globe.kt:57` must become `kotlin.math` in a common source set. |
| pure-math subset | `SphereGeometry` (54 LOC), `SphereState.transformFor`/`rowOf`/`columnOf`/`updateRows` (`SphereState.kt:87-150`), `fibonacciSphere` + `projectGlobe` (`Globe.kt:55-104`), `AppSearch.rank` (32 LOC) | pure Kotlin | ≈200 (estimate) | The fidelity-critical core, ported once already from the prototype. `Globe.kt` mixes this math with a composable that imports `toBitmap`, so extracting it is a file move, not a rewrite. |
| `:feature-search` | `AppSearch.rank` (exact > prefix > word-prefix > substring, top 14); `SearchKeyboard` (portrait always-keyboard) | kmp-common | 213 | `AppSearch` depends only on `AppEntry.label`. `SearchKeyboard` is common Compose except `toBitmap` at `SearchKeyboard.kt:123`. A physical keyboard replaces it on desktops. |
| `:core-data` | `AppRepository` over `LauncherApps` + `UserManager` profiles, `AppEntry`, `HiddenAppsStore` (SharedPreferences), notification listener + `NotificationProvider`, `VoiceInputManager`, `PerformanceMonitor` | android-bound | 526 | 29 `android.*` imports, the densest module. `AppEntry` is typed on `ComponentName`/`UserHandle`/`Drawable` (`AppEntry.kt:3-5,17-26`), so every consumer inherits Android types. The `AppRepository` interface (apps, launch, openAppDetails, requestUninstall) is already the right seam. |
| `:app` | Single-activity host, manifest (HOME + DEFAULT, listener service), role flow, `HomeScreen`, `ui/` (Chrome, HubPanel, SettingsPanel, AppContextMenu, DotGridBackground, VoiceAssistant, DefaultLauncherBanner) | android host plus common-able composables | 1,026 | 11 `android.*` and 9 `androidx.core/activity/lifecycle` imports. Host, role request and system reads stay Android; the `ui/` composables become common Compose once their system reads sit behind seams. |
| `:shaders` | `ShaderCapability`: `SDK_INT >= 33` gate and `withAgsl` | android-bound | 25 | No `RuntimeShader` is constructed anywhere (`grep -rn RuntimeShader`), and `withAgsl` has no caller outside `:shaders`, so there is no shader source to port today; the flat dot-grid fallback is what renders. |
| `docs/spherelauncher13.html` | Web prototype | web | 493 | Not shippable (fake inventory, no launching). The numeric source of truth for the lens (`magAtF`, `txAtF`, `render`, `renderGlobe`). |

**Platform-bound APIs that matter.**

| API | Where | Porting impact |
|---|---|---|
| HOME + DEFAULT filter, `singleTask`, `clearTaskOnLaunch`, `windowShowWallpaper` | `AndroidManifest.xml:24-46`, `res/values/themes.xml:9`, `MainActivity.kt` (`onNewIntent`) | Defines the product category; no equivalent on any target. Desktop reframe is a summoned window with its own background (`DotGridBackground` already exists). |
| `RoleManager.ROLE_HOME`, `Settings.ACTION_HOME_SETTINGS` | `DefaultLauncher.kt:3,33-41`, `ui/DefaultLauncherBanner.kt` | Android-only onboarding; dropped elsewhere (desktop: a "start at login / bind a shortcut" affordance instead). |
| `LauncherApps`, `UserManager.userProfiles`, uninstall intent | `core-data/.../AppRepository.kt` | The core seam: needs a per-OS inventory (Linux `.desktop`, macOS `.app`, Windows Start Menu / AUMID). iOS has no public enumeration API; Ubuntu Touch is unverified under confinement. App-info and uninstall have no uniform desktop equivalent. |
| `AppEntry` on Android types; `toBitmap().asImageBitmap()` | `AppEntry.kt:17-26`; `Coverflow.kt:104`; `Globe.kt:134`; `SearchKeyboard.kt:123` | The one leak of Android types into otherwise common feature modules. After a neutral model, `:feature-sphere` and `:feature-search` need no further change. |
| `NotificationListenerService` | `NotificationManager.kt:72-170`; consumed at `ui/HubPanel.kt:47-51` | No third-party notification listening on iOS or macOS; Windows needs an MSIX package plus WinRT interop; on Linux it would compete with the desktop's own notification daemon (not recommended); Ubuntu Touch unknown. `HubPanel` already has a demo fallback (`HubPanel.kt:50-51,151`). |
| `SpeechRecognizer` + `RECORD_AUDIO` | `VoiceInputManager.kt:9,24-57`; `ui/VoiceAssistant.kt:36` | No JVM-accessible OS speech API; options in Q8. |
| `SharedPreferences` (hidden-apps set) | `HiddenAppsStore.kt` | Trivial: a JSON file in the platform config directory (program F6 "app directories"). |
| `BatteryManager`; hard-coded weather and now-playing | `ui/Chrome.kt:3,103-106`; `Chrome.kt:97` ("Partly cloudy"), `Chrome.kt:129` ("Tycho — Awake") | Battery via F6's power probe or hide the bar. Weather and now-playing are placeholder strings, so there is nothing to port. |
| `LocalConfiguration.orientation`, `collectAsStateWithLifecycle`, `LocalLifecycleOwner`, `SimpleDateFormat`, `android.util.Log` | `HomeScreen.kt:53-54`; `HubPanel.kt:47`; `VoiceAssistant.kt:36`; `DefaultLauncherBanner.kt:34`; `Chrome.kt:29-31,65`; `PerformanceMonitor.kt:3` | Derive portrait/landscape from window size in common code. Lifecycle-runtime-compose multiplatform availability at the pinned version is an assumption to check. `java.text`/`java.util` are fine on JVM targets; they need `kotlinx-datetime` and a logger actual only if a non-JVM target (iOS) is pursued. |
| Touch-first gesture model (touch slop, pinch/spread, long-press, pull-down) | `feature-sphere/.../LauncherSurface.kt` | Common Compose API, so it compiles on desktop, but desktop needs wheel / trackpad / keyboard / right-click mappings. No such spec exists and it must not be improvised (Q11). |

## 3. Binding rules this port must not break

- **AGSL stays gated to API 33+**; do not ungate it (flat fallback below 33). Source: `README.md` "Do not touch" #1; `shaders/ShaderCapability.kt`. A desktop shader seam may sit beside it, never replace it.
- **Play-Integrity posture: no root, no Shizuku**, public `LauncherApps` API only. Source: `README.md` "Do not touch" #2; `ARCHITECTURE.md` Key Decision #4.
- **`:core-design` is a tracked Hyle-consumer debt; do not "fix" it ad hoc.** Source: `README.md` "Do not touch" #3; `Personal-Tracker/CONSTELLATION.md` §4 ("Hyle Compose/AGSL layer pending"). `DECISIONS.md` D-L puts this repo in the Hyle-consumer set, so the direction is to consume Hyle through the registry's path, not a per-port copy.
- **No telemetry, no crash-reporting SaaS, no phoning home** (`FEATURES.md` "Debugging & Telemetry"; program I-1). The only sanctioned reliability utility is `dev.aarso:crash-recovery` (D-O), which is Android-only today.
- **The prototype is the visual source of truth**: "when brief and prototype disagree, prototype wins" (`HANDOFF.md`); constants (12° step, 8-sample integral, tile and globe sizes) are ported verbatim and must match pixel-for-pixel (`SphereGeometry.kt` KDoc). Every target is held to the same numbers (§6 step C0).
- **Compose-only rendering, no custom Canvas, no AOSP fork; unidirectional data flow; only the hidden-apps set is persisted** (`ARCHITECTURE.md` Key Design Decisions 1-3 and "Persistence").
- **Landscape-first is the primary canvas**, portrait the secondary keyboard-first mode (`HANDOFF.md` Design Constraints). Desktop ports inherit landscape-first.
- **Environment honesty**: the build environment has no device; runtime, gesture, render and launcher behaviour is owner-verified on the phone only (`STATE.md`). Nothing in this plan claims a port works.
- **Crash-recovery PR #3 is held** as an open draft pending device verification on the RedMagic (PT:D-P). Do not merge, rebase over or bypass it, and no seam branch may conflict with it. What the registry records PR #3 as changing: `gradle/libs.versions.toml` (AGP 8.7.3 → 8.9.1, PT:D-Q), a new `.github/workflows/build-apk-release.yml`, and the crash-recovery submodule + `includeBuild` wiring (`Personal-Tracker/STATE.md`, crash-recovery rollout entry; `DECISIONS.md` D-P, D-Q). The PR diff itself was not read, so its full file list is unknown. Consequence for this plan: C2 and C3 (which edit the version catalog and `settings.gradle.kts`) branch from `main` only after PR #3 has merged, unless the owner rules otherwise in Q2; C0 and C1 touch neither file.
- **D-Q**: any `includeBuild` consumer of Hyle-Design-System must use Hyle's exact AGP (8.9.1 today) and a compatible Gradle wrapper.
- **D-I**: licence choice is owner-gated; this plan adds none. **NAMES.md**: product naming is owner-gated; no desktop product name, app id or package name is chosen here (program R11).
- **Colour is never the only signal** (program I-3, which binds Hyle-consumer repos; this repo is one per D-L). The palette defines `ProvenanceOnDevice` `#8CFF6A` (green) and `ProvenanceCloud` `#42E8FF` (`core-design/.../Color.kt`); they are not rendered yet (`ARCHITECTURE.md`: "M4+ visualization"). No port step may introduce a meaning carried by colour alone; any provenance surface needs a shape or label as well.
- **Program rules applied to every step**: R1 disjoint directories, R2 the existing gate stays green, R3 new CI workflow files only, R4 pure-core-first, R6 nothing released and no Actions artifacts, R7 real command output in `STATE.md`, R11 no identifier before a NAMES.md row, R12 reframes are labelled.

## 4. Target matrix (owner's order)

| Target | Feasibility | Approach | Blockers | Effort (eng-weeks, estimate) | Evidence today |
|---|---|---|---|---|---|
| Ubuntu Touch | reframe | Lomiri owns the home surface (its home is the app drawer), so the nearest shape is a Qt/QML "sphere" app picker Click that re-expresses the ≈200-LOC math and launches apps through whatever confinement allows. Second shape: bundle the HTML prototype as a webapp Click, a demo with no inventory. No JVM or Compose runtime exists in the normal Click path. | Q1; app enumeration and launch under AppArmor is **unverified**; no UT device on record (OQ-1); QML rewrite of ≈2.1k LOC of Compose UI and geometry (`:app` + `:feature-sphere` + `:feature-search`, measured); Qt 5.15 is end of road; no notification, speech or role analogue | 6 for the QML picker if confinement permits; ≈0.5 for the demo Click | PLAN |
| Linux desktop | moderate (a reframe, R12) | A hotkey- or command-summoned "sphere app launcher" (Ulauncher / Rofi class) on Compose Multiplatform desktop (JVM): KMP core + CMP UI split, Linux `.desktop` inventory, desktop input model, jpackage packaging | Q1; `AppEntry`/`AppRepository` are Android-typed (extraction first); no desktop interaction spec (Q11); hub and voice have no clean equivalent (Q7, Q8); Hyle debt (Q4); zero tests; repo gates (Q2) | 5 (includes ≈2 shared extraction that macOS and Windows reuse) | PLAN |
| iOS / iPadOS | reframe | Nearest shape is an in-app shelf: a user-curated set of declared URL schemes, Shortcuts / App Intents and links rendered with the same geometry. Not a launcher, not a home screen, no enumeration. Default recommendation: do not build. | Platform policy (no home replacement, no installed-app enumeration); Q6; App Review risk for an app whose sole purpose is launching others (program §4.3, 4.2 minimum functionality, general App Review knowledge); `java.*` use needs `kotlinx-datetime` for Kotlin/Native; Apple Program and delivery route (OQ-2) | 5 for the shelf, after the shared extraction | PLAN |
| macOS | moderate (a reframe, R12) | The same JVM build as Linux as a Spotlight-class "app sphere" utility: `.app` inventory, `.icns` decode, `open -b` launch, a summon path, `.dmg` via jpackage | Linux extraction first; `.icns` and `Info.plist` handling are bespoke; global summon needs a helper or menu-bar item; no third-party notification access; Developer ID and notarisation are owner actions (OQ-3); no Mac on record (OQ-5) | 3 (increment over Linux) | PLAN |
| Windows | moderate (a reframe, R12) | The same JVM build: Start Menu `.lnk` and `Get-StartApps` AUMID inventory, JNA for icons and hotkey, MSI via jpackage and WiX. Lowest priority: the program's Windows brief rates a launcher not-applicable or marginal, while §5 keeps this row. | Linux extraction first; JNA is a new dependency; notification and speech need MSIX + WinRT (out of scope); signing route (OQ-3); only the Dell (OQ-5) is a Windows device | 3 (increment over Linux) | PLAN |

Feasibility vocabulary is the template's. The five estimates sum to 22 engineer-weeks and overlap: macOS and Windows
ride the Linux build, so their real increment is per-OS inventory, packaging and signing. These are estimates, not a
schedule. Per program R12 the README of any shipped desktop build says "desktop app launcher inspired by Sphere
Launcher", never "port of the Android home screen".

## 5. Tier and sequencing

**Tier C, thin or reframed product.** The category exists only on Android. The desktop reframe is cheap and real:
the rendering is already common Compose (`graphicsLayer` 3D transforms plus one arbitrated `pointerInput` handler),
the fidelity-critical core is ≈200 LOC of pure Kotlin, and the constellation already has a working Compose
Multiplatform desktop template (`hnm_playground/ui/build.gradle.kts`: `jvm()`, optional `androidTarget()`, desktop
packaging). But each desktop still needs its own inventory, icon and launch layer, an input model the touch-only
prototype does not specify, and shipping without the hub and voice features. It should also not jump the repo's own
gates: zero tests, M5 pending, PR #3 awaiting device verification, no LICENSE.

| Platform | Program wave (§7) | What §7 says for this repo | Repo-local gate before the wave |
|---|---|---|---|
| Ubuntu Touch | P-UT b, as a reframe | "Reframes (wallpapers, IME, launcher) only on OQ-6". This repo's UT shape is QML-only, not JVM-cored, so it joins P-UT b by that sentence alone. | Q1 yes; Q5 answered; a UT device (OQ-1) or a CI-only waiver; spike U0 positive; F7 templates exist |
| Linux desktop | P-LX, in the wave's last group | "BOS 'sphere launcher' and 'Clackpad Desk' only on OQ-6" | Q1 yes; Q2 answered; F1 and F5 per OQ-17 (or Q4's carry-as-is ruling); steps C0–C3 green |
| iOS / iPadOS | none: P-iOS lists the launcher under "Not on iOS" | no slot | only if Q6 overrides; that would be a proposal for the lead to add a slot, not something this plan schedules |
| macOS | P-mac, reframe "app sphere", "only if asked" | reframe only on request | Linux binaries; OQ-3 for signing; OQ-5 means device gates stay NOV |
| Windows | no slot is named for this repo in §7 (program §4.5: "the launcher → n/a"); §5's row keeps the 3-week estimate | no slot | Q1 yes and an explicit owner request, after macOS; the lead decides whether to add or drop the slot |

**Proposals for the owner to rule (none is a decision; this repo has no decision register, so a ruled item would be
filed in `Personal-Tracker/DECISIONS.md`).**
- **BP-1.** Convert the pure modules to KMP in place (steps C1–C3) rather than keeping a separate source-mapping build.
  A separate build can map only files with no Android imports, and nothing but `SphereGeometry.kt` qualifies today;
  mapping the rest would need fake `android.*` shims on the desktop classpath, which is rejected. In-place conversion
  changes three module build files, `settings.gradle.kts` and the version catalog, so it must follow Q2 and Q3.
- **BP-2.** Do the hub/settings graduation that `STATE.md` already lists as a next step as the Compose Multiplatform
  split (step C3), so the move is done once.
- **BP-3.** Summon the desktop window with a `--toggle` command that signals the running instance, which the user binds
  in their desktop environment. This avoids depending on a Wayland global-hotkey API (unverified) on Linux. Native
  hotkeys on macOS and Windows are optional add-ons.
- **BP-4.** Make prototype-derived reference vectors the acceptance fence for every target, including the Ubuntu Touch
  QML re-expression.
- **BP-5.** Repo-local hygiene when the work starts: a `THIRD_PARTY.md` when the first non-Android dependency lands,
  `docs/DESKTOP_INTERACTION.md` as the extension of the prototype that Q11 asks for, and `.kotlin/` in `.gitignore`.

## 6. Work breakdown

Directory placement respects program R1–R4. **Existing Android source sets change only through steps C1–C3, as
ordinary PRs.** Everything else is new: `fixtures/`, `tools/`, `desktop/`, `packaging/<os>/`, `ubuntu-touch/`, and new
workflow files (never an edit to an existing one). Nothing here is released; every artefact is
`UNSIGNED — not for release`, package lanes run on `main` and tags only (compile-only on PRs), release binaries go to
draft GitHub Releases and never to Actions artifact storage (R6; this repo's own `cleanup-artifacts.yml` runs every six
hours because storage has been exhausted).

### Common steps (Linux first; macOS and Windows reuse them; Ubuntu Touch reuses C0 only)

| Step | What | Where | Done when | In this container? |
|---|---|---|---|---|
| C0 Fidelity fence | A small script evaluates the prototype's lens and globe functions and writes reference vectors (`magAtF`, `txAtF`, the `render` transform, `renderGlobe`) to JSON, checked in with the script beside it and never hand-edited. Add `AppSearch` ranking cases and row-remap cases. These are the exact tests `ARCHITECTURE.md` lists as "Unit Tests (Future)". | `tools/` (generator), `fixtures/` (JSON) | Vectors regenerate byte-identically | Not run; whether Node exists here is unchecked |
| C1 Neutral model seam | Android-side PR through the normal flow. A platform-neutral `AppEntry` (stable id, label, `icon: suspend () -> ImageBitmap?`); the `AppRepository` interface unchanged in shape; interfaces for the hub feed and voice input so common UI stops importing `SphereNotificationListener` and `VoiceInputManager` (`HubPanel.kt:47-51`, `VoiceAssistant.kt:36`); the three `toBitmap` sites move behind the Android actual; orientation derived from window size. `:app` keeps manifest, role flow, banner and services, but `HomeScreen` and the UI call sites adapt (diff size not measured). | `:core-data`, `:feature-sphere`, `:feature-search`, `:app` call sites | `./gradlew :app:assembleDebug` green; owner confirms nothing changed on the phone | No: needs the Android SDK. On `main` no hosted CI builds the APK (Q2) |
| C2 KMP conversion in place (BP-1) | Convert `:core-design`, `:feature-sphere`, `:feature-search` and the model and interfaces from `:core-data` to `kotlin("multiplatform")` with `androidTarget()` + `jvm()`, following the `hnm_playground/ui` pattern (Android opt-in by an explicit signal, never by sniffing `ANDROID_HOME`, so hosted runners stay SDK-free). `git mv src/main/kotlin src/commonMain/kotlin`; `java.lang.Math` becomes `kotlin.math`; the Android implementations (LauncherApps repository, listener, SpeechRecognizer, SharedPreferences store, `PerformanceMonitor`) go to `androidMain`; C0 vectors become `commonTest`. Pins per OQ-17 (today Kotlin 2.1.0, AGP 8.7.3, BOM 2024.12.01; PR #3 intends AGP 8.9.1). | those modules; root `settings.gradle.kts`, `gradle/libs.versions.toml`; new `.github/workflows/jvm-tests.yml` (SHA-pinned actions, ubuntu, no Android SDK, `./gradlew jvmTest`) | `:app:assembleDebug` green and `jvmTest` green on a hosted runner; owner phone check (NOV) | The JVM half is `CONTAINER-BUILD-ONLY` once written; the Android half is not |
| C3 Graduate UI to common (BP-2) | Move `HubPanel`, `SettingsPanel`, `Chrome` (clock; battery behind F6's power probe), `DotGridBackground`, `AppContextMenu` out of `:app/ui` into common modules named `:feature-hub` and `:feature-settings` per `STATE.md`. Replace `SimpleDateFormat` with a common clock helper. | new `:feature-hub`, `:feature-settings` | same two gates | as above |

R4 holds: the core's `jvmTest` is green before any desktop UI step starts. C2 and C3 additionally wait for PR #3 to merge (§3). If Q3 rules against in-place conversion, only
C0 can proceed without changing Android modules, and nothing else is plannable until Q3 is answered.

### Linux desktop (wave P-LX)

| Step | What | Where | Done when | Evidence after the step |
|---|---|---|---|---|
| L1 Desktop head | A Compose `application {}` window, borderless, shown and hidden on demand; `--toggle` signals the running instance over a Unix-domain socket in `$XDG_RUNTIME_DIR` (local only, never a TCP listener; no network, so I-1 is unaffected); X11/XWayland path first, Wayland behind a flag (program §4.2: Compose Desktop is XWayland-only today). Transparency and always-on-top behaviour are assumptions to verify. | `desktop/app` (`jvmMain`) | Window opens and toggles | `CI (hosted VM)` for compile; behaviour NDV |
| L2 Linux inventory | Parse `.desktop` entries per the XDG Desktop Entry spec (`NoDisplay`, `Hidden`, `OnlyShowIn`/`NotShowIn`, `Exec` field codes) from `/usr/share/applications`, `~/.local/share/applications`, and the Flatpak and Snap export directories; Freedesktop icon-theme lookup to `ImageBitmap` (PNG first; SVG through Skia is an assumption); live refresh with `java.nio` `WatchService`; launch through an argument-list `ProcessBuilder` or `gio launch`, never a shell string. One JVM artefact serves all three OSes, so the per-OS inventory is chosen at runtime from `os.name`, each OS in its own package so each track owns its files. | `desktop/platform/.../linux/` | Parser and ranking tests pass on checked-in sample `.desktop` files | `CI (hosted VM)`; real inventory NDV |
| L3 Input and chrome | Per the interaction spec (Q11): wheel or trackpad scrub, Ctrl+wheel or trackpad pinch to the globe, arrows and Enter, Esc, type-to-search from the physical keyboard (replacing `SearchKeyboard`), right-click menu (Hide; "reveal in file manager" for app info; Uninstall omitted unless the owner wants a package-manager hand-off). Battery via F6 or hidden. Weather and now-playing are not shown as real (a real weather feed would be a network call and needs an owner ruling under I-1). Hub per Q7 (curated or demo panel labelled as demo, or dropped); voice per Q8 (absent in v1 by default). | `desktop/app`, common modules | Spec items each have a test or a checklist line | CI for logic; feel NDV |
| L4 Packaging | CMP `nativeDistributions`: an app-image tarball and a `.deb` first, compiled `--release 17`, run on a jlinked Temurin 21 (program §4.2, F10); `packageDeb` needs `fakeroot`, present on `ubuntu-24.04` runners. **Flatpak is the program's default for GUI apps, but a sandboxed launcher may be unable to read host `.desktop` files or start host apps without sandbox holes (ASSUMPTION, unverified).** Spike S-BOS-1 decides before any Flatpak manifest is written. | `packaging/linux/`; `.github/workflows/desktop-linux.yml` | Tarball and `.deb` build on `main` or tag; draft Release only | `CI (hosted VM)`; install NDV |
| L5 Device checklist | `DEVICE_CHECKLIST_LINUX.md` items: Steam Deck Desktop Mode; the Dell if it runs Linux (OQ-5); Redmagic-Edge under Termux:X11 (must start without Wayland or Vulkan; software-GL frame rate is unknown); HiDPI on Plasma and GNOME. Steam Deck Gaming Mode has no desktop app list, so this plan does not schedule it (a judgment, not a sourced fact). | `docs/` | Owner ticks each item | NDV |

### macOS (wave P-mac, only if asked)

| Step | What | Where | Done when | Evidence after the step |
|---|---|---|---|---|
| M1 Inventory | Scan `/Applications`, `/System/Applications`, `~/Applications`; read `Info.plist` (`CFBundleName`, `CFBundleDisplayName`, `CFBundleIdentifier`, `CFBundleIconFile`); `.icns` to PNG through a pure-Kotlin decoder or a `sips` helper; launch with `open -b <bundleId>` through an argument list. The program's macOS brief suggests a small Swift helper over NSWorkspace / LaunchServices as an alternative; Swift is not available in this container. | `desktop/platform/.../macos/` | Plist and icon parser tests on sample bundles | `CI (hosted VM)` on `macos-latest`; real inventory NOV |
| M2 Summon | A menu-bar item or a Carbon `RegisterEventHotKey` helper (the brief avoids a CGEvent tap because it needs Accessibility consent); the `--toggle` path of BP-3 works unchanged. Agent-style app without a Dock icon is a design choice to confirm. | `desktop/app`, `apple/` only if a Swift helper is chosen | Summon works | NOV |
| M3 Packaging | jpackage `.dmg` on `macos-latest` through `desktop-macos.yml`; hardened runtime with the minimum entitlement set from asom's S-M3 verdict (F10), never jpackage's default sandbox plist; signing and notarisation exist only as disabled templates until OQ-3. | `packaging/macos/` | Unsigned `.dmg` builds on `main` or tag | `CI (hosted VM)`; Gatekeeper behaviour NOV |
| M4 Device | No Mac is on record (OQ-5), so every device gate stays NOV. | `docs/` | — | NOV |

### Windows (no wave named in §7; only on request, after macOS)

| Step | What | Where | Done when | Evidence after the step |
|---|---|---|---|---|
| W0 Path lint | Run the program's R3 Windows-checkout path lint before any `windows-*` lane. Measured today: no tracked path contains `: < > | ? * "` or a reserved device name (`git ls-files` filtered by regex returned nothing). Byte-exact fixtures such as `.lnk` samples need `-text` in a new `.gitattributes`. | `.github/workflows/` (new file) | Lint green | `CI (hosted VM)` |
| W1 Inventory | Start Menu `.lnk` files under `%ProgramData%` and `%AppData%` (`...\Microsoft\Windows\Start Menu\Programs`) plus packaged apps through `Get-StartApps` AUMIDs (child process, JSON output, argument list); icons via JNA `SHGetFileInfo` or a helper; launch through `explorer.exe shell:AppsFolder\<AUMID>` or ShellExecute. | `desktop/platform/.../windows/` | `.lnk` parser tests on sample files | `CI (hosted VM)` on `windows-2025`; real inventory NDV |
| W2 Summon | JNA `RegisterHotKey` plus a tray icon, or the BP-3 `--toggle` path. JNA (5.19.x in asom's Windows track) is a new dependency; record its licence in the new `THIRD_PARTY.md` (BP-5). | `desktop/app` | Summon works | NDV |
| W3 Packaging | jpackage MSI (WiX) on `windows-2025` through `desktop-windows.yml`; x64 first, arm64 optional; unsigned; winget manifest only as a disabled template; the MSI upgrade GUID and winget id wait for a NAMES.md row (R11). | `packaging/windows/` | Unsigned MSI builds on `main` or tag | `CI (hosted VM)`; install, SmartScreen and mixed-DPI behaviour NDV on the Dell while it is Windows (OQ-5) |

### Ubuntu Touch (wave P-UT b, reframe, only on Q1 and Q5)

| Step | What | Where | Done when | Evidence after the step |
|---|---|---|---|---|
| U0 Confinement spike | Can a confined Click app list other installed apps and launch them (for example through `url-dispatcher`)? Unknown, and not decidable from CI. Record the verdict in `STATE.md`. If the answer is no, the QML picker has nothing to show and U1 is dropped in favour of U2 or nothing. | `ubuntu-touch/spike/` | Verdict recorded on a real device | NDV; needs OQ-1 |
| U1 QML sphere picker | Re-express the ≈200-LOC math in QML/JS and hold it to the C0 vectors; Qt Quick transforms with perspective are expected to reproduce the CSS 3D look (unverified); `clickable.yaml`, `manifest.json.in`, `apparmor.in` with common policy groups only (F7 templates); `.github/workflows/ubuntu-touch.yml` (Clickable on `ubuntu-latest`, arm64 smoke on `ubuntu-24.04-arm`, a non-blocking 26.04 canary lane, because Qt 5.15 is end of road). No JVM bridge: the F7 jlinked-JVM path is not used. | `ubuntu-touch/` | Click builds in CI; QML tests pass against the vectors | `CI-APPROX — NOT DEVICE EVIDENCE`; behaviour NDV |
| U2 Demo Click (alternative) | Bundle `docs/spherelauncher13.html` as a webapp-container Click, labelled "prototype demo", no inventory and no launching. | `ubuntu-touch/demo/` | Click builds | `CI-APPROX — NOT DEVICE EVIDENCE`; behaviour NDV |
| U3 Waydroid note | If OQ-21 is answered yes, the unmodified APK could run under Waydroid as the home app of the Waydroid Android session only, not of Lomiri. Unverified, no work item. | none | — | NOT-APPLICABLE as a port; NDV if tried |

### iOS / iPadOS (no wave; only if Q6 overrides)

| Step | What | Where | Done when | Evidence after the step |
|---|---|---|---|---|
| I0 Default | Do not build. Record the decision. | `STATE.md` | Owner rules Q6 | — |
| I1 Shelf on CMP iOS | If asked: add `iosArm64` and `iosSimulatorArm64` to the common modules (`java.*` use replaced with `kotlinx-datetime` and a logger actual), a thin SwiftUI shell through an XcodeGen head (F10), a `macos-latest` job that builds the simulator app and runs the simulator tests, `CODE_SIGNING_ALLOWED=NO` on PRs. | `apple/` | Simulator build and tests green | `SIMULATOR`; device NDV on the iPad Pro M4 (only Apple device on record) |
| I2 Shelf content | User-curated entries launched through `LSApplicationQueriesSchemes`-declared schemes, Shortcuts or App Intents; no enumeration. README labels it "shelf", not a launcher (R12). | `apple/` | A curated entry launches | NDV |

## 7. Shared foundation this repo consumes or provides

**Consumes.**
- **F1 hyle-kmp.** This repo is in the Hyle-consumer set (D-L) and `:core-design` is the tracked debt; F1's `kotlin-common`
  tokens would replace the hand-written palette and F1's `Tokens.qml` would serve U1. Whether C2 waits for F1 or carries
  `:core-design` into `commonMain` unchanged (extending the debt) is Q4, and it needs a CONSTELLATION §4 decision first.
- **F2 crash-recovery as KMP.** This repo is a named consumer (PR #3 is the Android integration, held). Desktop gets F2's
  Swing/AWT recovery window only after PT:D-P's device verification closes and OQ-27 picks the toolkit.
- **F5 kmp-conventions.** Version catalog at the pin OQ-17 chooses, the `android.*`-import ban for common and jvm source
  sets (it would enforce C2 mechanically), conditional Android inclusion, jpackage conventions. BOS today is on
  Kotlin 2.1.0, near the program's staged option B; option A would move it to Kotlin 2.4.20 and AGP 9.4.1.
- **F6 platform-ports (proposed addition to F6's consumer list; the lead decides).** App directories (hidden-apps JSON) and the power probe (battery). This repo holds no keys, so F6's
  key-storage tiers and OQ-22 do not apply.
- **F9 CI matrix template.** `jvm-linux`, `jvm-macos`, `jvm-windows` and `ut-click` lanes, with package lanes on `main` and
  tags only, the R3 path lint, and no artifact uploads. This repo is public (program OQ-20), so the lanes cost nothing;
  artifact storage is the constraint, not minutes.
- **F10 packaging templates.** jpackage plus nFPM for Linux, WiX and winget for Windows, entitlements and `notarize.sh` for
  macOS, XcodeGen for iOS, the Clickable set for Ubuntu Touch.
- **F7 ubuntu-touch-shell (proposed addition to F7's consumer list, templates only; the lead decides).** Partially: the Click templates, `DEVICE_CHECKLIST_UT.md`, the policy checker. Not the jlinked
  JVM bridge, and not the S-UT1 verdict, because this repo's UT shape has no JVM core.
- **F11** evidence scheme and device checklists.

**Does not consume.** F3 cell-shell, F4 asom-client, F8 native engines, F12 web kit.

**asystemofmodels.** The M4 assistant in `ARCHITECTURE.md` means asystemofmodels (the Urbana note above). Today the repo has
no inference code: `VoiceInputManager` wraps Android `SpeechRecognizer`. Off Android, the route would be asom's desktop
`:server` on `127.0.0.1:11435` through F4's client, which the program marks unusable off-Android until asom rules
asom-design D25(b) and D14 part B (OQ-7d). Voice through asom is therefore not planned here (Q8).

**Provides.** Nothing another repo needs today. The per-OS inventory, icon, launch and summon code in `desktop/platform`
is BOS-local; no program F-item covers it. It is worth offering to Shared-Libraries-asoc only if a second consumer
appears. The prototype-to-vectors pattern (BP-4) is reusable by any repo that ported math from a web prototype.

## 8. Open questions for the owner

1. **Product shape (program OQ-6).** Is a summoned desktop "sphere app launcher" an acceptable reframing of Sphere Launcher,
   or is the product meaningful only as a phone home screen? *Blocks every §4 row.* If the answer is "only a home
   screen", all four non-Android rows drop to defer or skip and only M5 (DeX) proceeds.
2. **Gate sequencing.** Must M5, PR #3's device verification (D-P; F2 waits on OQ-27), the first unit tests and the LICENSE
   choice (D-I; program OQ-12 asks to re-check this repo) come before the seam work, or may C0–C3 start in parallel on their
   own branch? Also, which Android build gate do the seam PRs use, given none runs on `main`: PR #3's workflow first, or a
   new minimal debug-assemble workflow? *Blocks C1–C3 and every wave.*
3. **Seam layout and pins (BP-1, program OQ-17).** In-place KMP conversion touching root settings and the catalog, or a
   narrower route? Which pins: today Kotlin 2.1.0 / AGP 8.7.3, PR #3 intends AGP 8.9.1 (D-Q). *Blocks C2.*
4. **Hyle debt.** Carry `:core-design` into `commonMain` as it is, extending the tracked debt, or wait for or fund F1
   (README "Do not touch" forbids an ad-hoc fix; CONSTELLATION §4)? *Blocks C2 for `:core-design` and every desktop UI step.*
5. **Ubuntu Touch (program OQ-1, OQ-21).** Accept a QML rewrite subject to the confinement spike, ship the prototype as a
   demo Click only, or skip? Which device? Is Waydroid an acceptable answer for this app? *Blocks U0–U3.*
6. **iOS / iPadOS.** Skip outright, or build the curated shelf and accept App Review risk (program OQ-6, OQ-2)? *Blocks I1–I2.*
7. **Hub on desktop.** Drop it, ship a curated or demo panel labelled as demo, or attempt a Linux D-Bus notification monitor
   (which would compete with the desktop's own daemon)? *Blocks the hub items in L3 and the macOS and Windows equivalents.*
8. **Voice on desktop.** Drop in v1, bundle an engine (Vosk or whisper.cpp, neither of which is an F8 item and each would
   need its licence recorded), or route through asom's desktop server (needs program OQ-7d and a desktop pairing story that
   today exists only as Android AIDL)? *Blocks the voice items.*
9. **Names and identifiers (program OQ-25).** Product name for desktop builds ("Sphere Launcher", "BOS", other), and a
   NAMES.md row for the config-directory name, Flatpak id, bundle id, MSI upgrade GUID, AUMID and winget id. *Blocks all
   packaging steps and the config path in L2 and W1 (R11).*
10. **Signing, formats, hardware and CI (program OQ-3, OQ-4, OQ-5, OQ-20).** Developer ID and notarisation; a Windows
    signing route (Azure Artifact Signing is unavailable to an individual in India); Linux formats, including the launcher-
    in-Flatpak question from L4; whether a Mac exists and what the Dell runs. *Blocks the release templates and the macOS
    and Windows device gates.*
11. **Desktop interaction spec.** Who authors the mouse, trackpad and keyboard mapping and the summon behaviour, given the
    prototype is touch-only and the constellation rule is that the design is the UI? Should it extend `spherelauncher13.html`
    so the prototype stays the single source of truth? The same spec serves the repo's own M5 (DeX keyboard and trackpad).
    *Blocks L3, M2 and W2.*

## 9. Sources read

In this repo: `README.md`, `STATE.md`, `ARCHITECTURE.md`, `HANDOFF.md`, `FEATURES.md`, `settings.gradle.kts`,
`build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`,
`app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/themes.xml`,
`.github/workflows/cleanup-artifacts.yml`, `.gitignore`;
`app/src/main/kotlin/com/bos/sphere/{MainActivity,HomeScreen,DefaultLauncher}.kt` and `ui/{Chrome,HubPanel,VoiceAssistant,DefaultLauncherBanner}.kt`;
`core-data/.../{AppEntry,AppRepository,HiddenAppsStore,NotificationManager,VoiceInputManager,PerformanceMonitor}.kt`;
`core-design/.../Color.kt` (with the other four files in that module at profile level);
`feature-sphere/.../{SphereGeometry,SphereState,Coverflow,Globe,LauncherSurface}.kt`;
`feature-search/.../{AppSearch,SearchKeyboard}.kt`; `shaders/.../ShaderCapability.kt`; `docs/spherelauncher13.html`.

Outside this repo: `Personal-Tracker/PORTING_PROGRAM.md` (§0–§3, §4, this repo's §5 row, §6, §7, §8);
`Personal-Tracker/NAMES.md`, `DECISIONS.md` (D-I, D-L, D-N, D-O, D-P, D-Q), `CONSTELLATION.md`, `STATE.md`;
`Personal-Tracker/porting/platforms/{linux,macos,windows,ios,ubuntu-touch,framework-strategy}.md` (launcher passages);
`hnm_playground/ui/build.gradle.kts` (Compose Multiplatform desktop template); the reader profile
`profiles/read_BOS_launcher.json` (2026-10-06), whose line references were re-checked against the files and corrected
where they differed.

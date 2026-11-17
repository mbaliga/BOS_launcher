# Liftoff: Desktop-Class Launcher Product Spec (Alpha to Pre-Production)

Nov 11, 2026 · @Madhav Baliga

## 1. How to use this spec

This spec takes the launcher from early alpha to a pre-production build: feature-complete on every Must and Should requirement, stable on the reference devices, and ready for a closed beta. It is written to be handed to a coding agent one milestone at a time.

**Scope.** Non-root, stock Android, installed as a normal app and set as the default home app. Anything that needs root, Shizuku or ADB grants is isolated in section 14 and never blocks a core requirement.

**Conventions.**

- Every requirement has an ID (`PAL-03`), a priority (M = Must, S = Should, C = Could) and acceptance criteria that can be turned directly into tests.
- Pre-production means all M and S items pass. C items are built only if they do not delay a milestone.
- Each requirement states its platform dependency. Where a capability varies by Android version or OEM, the requirement defines the fallback, and the fallback is part of the acceptance criteria.
- A requirement is done when: acceptance criteria pass on all reference devices, it works with touch alone and with keyboard and pointer alone, it survives process death, and it has no hidden-API or reflection calls in the core build.

**Rules for the implementing agent.**

1. Do not mark a milestone complete with any M requirement unmet. Report unmet items by ID; do not substitute a simpler implementation silently.
2. Run the capability spikes in section 15 (work package 0) before building anything that depends on them, and record results in `docs/capabilities.md`.
3. If a platform limit makes a requirement impossible as written, stop and report it against the requirement ID with the evidence.
4. Prototype new surfaces as HTML first where the interaction is novel, then port to Kotlin once approved.

## Baseline: build 31 and the gap

Build 31 is a strong visual shell with almost none of the desktop-class layer: the looks, search surface, notification page and settings exist, while keyboard and pointer, launch control, external display, organisation tools and a durable state store do not. Source: the 13 Nov 2026 handoff (repo `mbaliga/BOS_launcher`, branch `claude/clever-mccarthy-804ej7`, draft PR #8, package `com.bos.sphere`).

Nothing in build 31 has been confirmed on a device by the coding agent; every "built" below means it compiles and passes unit and headless screenshot tests.

| Area | Built in 31 | Gap to this spec |
| --- | --- | --- |
| Home styles | Grid/Pages, List, PSP-XMB, DSi; paged or continuous; uniform frame (about 15% of the short side) | Folders, widgets, shortcuts, badges, profiles, edit mode with undo (HOM-04 to HOM-12); focus graph per style (INP-01) |
| Corner actions | Four corners; three styles (Holo, Sony quarter discs, Bubbles) | User-assignable actions, hot corners for pointer, keyboard bindings (INP-15 to INP-17) |
| All-apps puck | Corner wheel with A-Z, Category, Recent; Radar at centre; fixed ring of actions | Configurable actions, keyboard and scroll-wheel operation, small-screen spacing (INP-18, CFG-15) |
| App drawer | Grid, list and detailed views; A-Z sort and index | Tags, hidden apps, work and private profiles (HOM-05, HOM-06) |
| Radar | Filmstrip, Orbit and Flat layouts; category wheel; apps, contacts, actions, files, web; web hand-off through the chosen engine; query line always at the top; Orbit rings by how much each thing is used; long-press preview and pinch text size in typed results; the two-slot signal-driven background behind it (HOM-16, PAL-24) | Keyboard operation, per-result action list, settings index, calculator, ranking, streaming providers (PAL-03 to PAL-12); preview and pinch in all layouts (PAL-20) |
| Launchpad | Notifications page beneath home, home floats as a rounded card; grouping, rules, light and dark; slide left = one action, slide right = palette | Device confirmation, screenshot tests, grant flow, keyboard and pointer, system state row (STR section) |
| Ground Control | XMB category bar and list; inline toggles, sliders, segments; preview cards; tactile-kit controls | Finish tactile style, live preview dock, search, capability report, backup pages (CFG section) |
| Backgrounds | Removed by owner decision in wave 15 (the old gallery of solids, gradients, textures, live scenes, the Horizon picture, an own photo and the system wallpaper); replaced by up to two signal-driven items chosen from nine (HOM-16) | None |
| Extras | Home long-press quick sheet, media strip, onboarding, 8 UI languages (new strings English only) | Media strip grant prompt (STR-05); translations |
| State | `LauncherPrefs` data class in SharedPreferences | Versioned store, export and import, snapshots, safe mode (CFG-01 to CFG-04, CFG-09) |
| Input | Touch only | Entire INP section |
| Dock, launch control, external display, workspaces | Not started | Entire DCK, WSP and EXT sections |
| Tests | Unit tests and Robolectric screenshots | Tests named by requirement ID, device checklist, benchmarks (section 15) |

The ordered work to close this gap is in the Build instructions tab.

## 2. Product definition

Liftoff is a home app that gives Android the four things a desktop has and a phone launcher lacks: keyboard and pointer as first-class inputs, visible running state, control over where and how apps open, and one search that reaches everything. It keeps a touch-first surface so the same setup works in the hand and at a desk.

**Product names.** The spec's functional terms map to the space-themed product names below. Requirement IDs keep their prefixes.

| Spec term | Product name | Sections |
| --- | --- | --- |
| The launcher | Liftoff | all |
| Command palette and search | Radar | 6 |
| Notification centre and system strip | Launchpad | 11 |
| Settings | Ground Control | 12 |

**Shell model.** These are fixed design decisions the rest of the spec builds on, not open questions.

- **Landscape is the primary orientation.** Every surface is designed in landscape first and adapted to portrait. The uniform home frame keeps apps in the same region in both.
- **Four corners.** Each screen corner holds a shortcut or action, in the user's chosen corner style. Corners are the main quick-access model on a handheld.
- **The puck.** A corner wheel opens all apps by A-Z, category or recency, with Radar at its centre and a ring of one-tap actions around it.
- **Home styles.** Grid, List, XMB and DSi are interchangeable presentations of the same items.
- **Launchpad sits beneath home.** Home floats above it as a card and slides aside to reveal notifications from the left edge.
- **Ground Control** uses a cross-media-bar layout: categories across, settings down, changes previewed live.

**Principles**

1. **Input parity.** Every action is reachable by touch alone and by keyboard and pointer alone. No hover-only or keyboard-only features.
2. **Launch-time control, not window management.** The launcher decides where and how an app opens. It does not try to manage windows after launch, because non-root Android does not allow it.
3. **Honest state.** If the platform only exposes recent apps, the UI says recent, not running.
4. **Local and sovereign.** No account, no network dependency, no telemetry. All indexes live on the device. Configuration is a portable file.
5. **Degrade by capability, not by device list.** Features switch on from runtime capability checks, never from model allowlists.
6. **Calm.** Fewer interruptions than the stock experience, never more.
7. **One foundation, selectable surfaces.** Grid, List, XMB and DSi are home styles on top of a shared core. Nothing in the core depends on them.

**Non-goals for pre-production**

- Replacing system Recents, gesture navigation, or window chrome.
- A custom window manager, tiling engine or window snapping after launch.
- Any core feature that requires root, Shizuku, ADB or an accessibility service.
- Feeds, news, ads, accounts, cloud sync.
- Built-in file manager or browser. The launcher deep-links to real ones.

## 3. Platform constraints (non-root)

A third-party home app owns its own surfaces and the moment of launch; the system launcher and System UI own everything after that. Every requirement in this spec is designed inside that boundary.

Status key: **Yes** = public API, works as a normal app. **Grant** = works after a user-granted special access. **Partial** = works with limits or varies by version/OEM. **No** = not possible without root or system signature.

| Capability | Status | Mechanism | Design consequence |
| --- | --- | --- | --- |
| Be the home app | Yes | `ROLE_HOME` via RoleManager | Onboarding requests the role; app must work before it is granted |
| List and launch apps, work profile, private space | Yes | `LauncherApps`, `UserManager` | Private space needs Android 15+ and the home role |
| App shortcuts and pinning | Yes (home role) | `LauncherApps.getShortcuts`, pin requests | Only while default home |
| Host widgets | Yes | `AppWidgetHost`; per-widget bind consent | Bind prompt on first add |
| Notification content, badges, media sessions | Grant | `NotificationListenerService` | Calm centre and dots are off until granted |
| Recent and foreground app history | Grant | `UsageStatsManager` (usage access) | Dock, the Recent apps home landing (HOM-22) and the Recents panel (STR-14) show recent apps, the landing and the panel also from Liftoff's own launches without the grant; cannot enumerate live tasks or windows, and cannot tell whether an app process is still alive; the panel says so |
| Launch on a chosen display | Yes | `ActivityOptions.setLaunchDisplayId` (API 26+) | Basis of external display support |
| Launch with window bounds | Partial | `ActivityOptions.setLaunchBounds`; ignored where freeform is unsupported | Works in desktop windowing; no-op on most phones' internal display |
| Force a windowing mode at launch | No (core) | `setLaunchWindowingMode` is a hidden API | Excluded from core; elevated tier only |
| Launch into split | Partial | `FLAG_ACTIVITY_LAUNCH_ADJACENT` + new task | Behaviour varies by version; needs spike SPK-02 |
| Move, resize, close, snap other apps' windows | No | Owned by WM Shell | Out of scope. Launch-time placement only |
| Recents screen and gesture navigation | No | Owned by the system launcher (QuickStep) | Do not replace. Some OEMs degrade or disable gestures under third-party launchers |
| Home surface on an external display | Partial | System starts its own desktop session on connected displays | Launcher opens its own activity there as a desktop surface; may not be "home" on that display. Spike SPK-01 |
| Global keyboard shortcuts while another app is focused | No (core) | Only an accessibility service can filter keys globally | Shortcuts work when a launcher surface is focused; system Meta shortcuts stay with the OS |
| Toggle Wi-Fi, Bluetooth, mobile data | No | Blocked for apps since Android 10 | System strip shows state and opens system panels |
| Open notification shade programmatically | Partial | No stable public API | Elevated tier or omitted; own curtain renders listener data |
| Background clipboard history | No | Clipboard readable only while focused | Clipboard items captured only while a launcher surface is focused |
| Whole-storage file search | Partial | MediaStore + folders granted through the system picker | No all-files access; index covers media and user-granted folders |
| Start activities from the background | Partial | Background activity launch restrictions | Scheduled launches arrive as a notification action, not an auto-launch |
| Uninstall, app info, default-app settings | Yes | System intents | Always hands off to system UI |

**Platform facts this rests on (checked 11 Nov 2026)**

- Android 17 supports per-display desktop windowing, with a desktop-first mode (freeform by default, taskbar with pinned and open apps) and a touch-first mode (fullscreen by default). Connected external displays default to desktop-first; attaching a keyboard plus mouse or touchpad also switches a display to desktop-first. [AOSP: Desktop windowing](https://source.android.com/docs/core/display/desktop-windowing)
- Connecting a phone to a display leaves the phone's state unchanged and starts a blank desktop session on the display. Apps may target a display at launch with `launchDisplayId`. If the display disconnects, activities move to the primary display with a configuration change. [Android Developers: Support connected displays](https://developer.android.com/develop/adaptive-apps/guides/support-connected-displays)
- Recents and gesture navigation are handled by the preinstalled launcher even when another home app is default, and results vary by OEM. [Lawnchair docs](https://docs.lawnchair.app/troubleshooting/gesture-navigation-issues), [Niagara help](https://help.niagaralauncher.app/article/114-recent-apps-screen-issues)
- The windowing-mode launch option is not public; third-party taskbars reach it by reflection. [utzcoz: How Taskbar starts apps in freeform](https://utzcoz.github.io/2021/09/12/How-Taskbar-start-app-in-freeform-windowing-mode.html)

Rows marked Partial are not yet verified on your devices. Section 15 lists the spike that settles each one before dependent work starts.

## 4. Devices, form factors and input modes

The launcher runs in three capability tiers detected at runtime, and one layout model reflows across all of them.

| Tier | Detected by | What switches on |
| --- | --- | --- |
| T1 Handheld | Compact window, touch only | Touch home surface, palette, drawer, dock, widgets |
| T2 Large or keyboard | Medium/expanded window, or a hardware keyboard or pointer attached | Persistent dock, hover and right-click, shortcut hints, multi-column layouts, split launch |
| T3 Desktop | Display in desktop windowing, or an external display that can host tasks | Desktop surface on that display, launch-with-bounds, layout presets, per-display dock |

**Orientation.** Landscape is the reference layout in every tier. Portrait is a first-class adaptation, not a fallback: the uniform home frame, corners, puck, Radar, Launchpad and Ground Control each define both. Acceptance for any visual requirement means both orientations pass, with landscape checked first. Rotation never loses position, focus or an open overlay.

**API levels.** minSdk 29 (Android 10), targetSdk and compileSdk at the current stable level (Android 17). T3 features require Android 16 or later and are hidden below that.

**Reference devices for acceptance** (replace with what you actually own):

- A Pixel phone on Android 17, with and without an external display.
- A tablet or foldable on Android 16 or 17 with desktop windowing.
- One Samsung device (One UI), for OEM behaviour and DeX coexistence.
- One Xiaomi or OnePlus phone, for aggressive background limits and gesture restrictions.
- One Android 10 to 12 phone as the T1 floor.

**Input requirements across all tiers**

- Touch targets at least 48 dp; pointer targets may be denser only when a pointer is the active input.
- Active input mode is tracked live (last input event wins) and drives hover affordances, focus rings and density.
- All layouts use window size classes and activity context metrics. No model checks, no assumptions that the display is fixed.

## 5. Architecture

The existing Kotlin and Compose modules stay; five small core modules are added beside them, and `feature-grid` is split only as work packages touch it. No big-bang restructure. Home styles depend on the core; the core never depends on a style.

| Module | Status | Holds today | Target role |
| --- | --- | --- | --- |
| `app` | Exists | HomeScreen host, Launchpad, quick sheet, media strip, settings categories | Shell: activities, services (notification listener), wiring. No business logic |
| `feature-grid` | Exists | Home styles, corners, puck, Radar UI, Ground Control UI, tactile kit, backgrounds | Home styles, corners and puck. Radar UI, Ground Control UI and the tactile kit move out into `feature-radar`, `feature-groundcontrol` and `ui-tactile` when their work package starts |
| `core-data` | Exists | Launchpad rules | The versioned state store: layout, settings, workspaces, sessions, rules; export, import, snapshots, migrations |
| `search-engine` | Exists | Search sources and web engine | Index, provider interface, ranking |
| `core-capabilities` | New |  | Runtime capability registry: API level, grants, displays, input devices. The only source for feature gating |
| `core-launch` | New |  | The only place that starts other apps: `LaunchRequest` in, applied result out |
| `core-input` | New |  | Input-mode tracking, focus graph helpers, shortcut registry, context-menu model |
| `core-signals` | New |  | Notification listener data, media sessions, usage stats, battery and connectivity state |
| `core-catalog` | New or extracted |  | Apps, shortcuts, profiles and widget providers from `LauncherApps`; icon cache |
| `feature-dock` | New |  | Dock rail and app groups |
| `feature-desktop` | New |  | Desktop surface for external displays and desktop windowing |
| `elevated` | New, separate flavour, last |  | Section 14 add-ons |

If app listing, icon loading or launching already live somewhere in `app` or `feature-grid`, they are moved into the matching core module rather than rewritten.

**Structural rules**

- **Surface contract.** A home surface implements one interface: render a set of items for a window size and input mode, report focus targets, accept item actions. Grid, List, XMB and DSi are four implementations, already built; they are adapted to the contract, not rewritten. Switching surface never changes data.
- **Capability gating.** Features query `capabilities` and render a clear off state with the reason and the fix (grant, Android version, device). No feature checks `Build.MODEL`.
- **Launch requests.** Every launch is a `LaunchRequest(target, display?, bounds?, placement?)`. `core-launch` resolves it to the best supported option and returns what was actually applied, so the UI can tell the user when placement was not honoured.
- **State schema.** One versioned JSON-serialisable model for layout, workspaces, sessions, shortcuts and settings. Migrations are tested from every prior schema version.
- **Process death.** All surfaces restore from the store; nothing relies on in-memory singletons surviving.
- **Build flavours.** `core` (Play-safe, no accessibility service, no hidden APIs) and `elevated` (adds section 14). Core must pass every M and S requirement alone.

## 6. Command palette and search (PAL)

The palette is the primary way to do anything: one field, local results as you type, and an action on every result.

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| PAL-01 | M | Open the palette from any launcher surface by typing, by a swipe gesture, by a visible search affordance, and by a keyboard shortcut | Typing a printable key on home opens the palette with that character entered. All four entry points work in T1 to T3 |
| PAL-02 | M | Search installed apps across all profiles | Results update within 50 ms per keystroke with 500 apps installed. Matches on label, package and user-defined alias; tolerates one typo and initials ("gm" finds Google Maps) |
| PAL-03 | M | Search app shortcuts and launcher actions | Static and dynamic shortcuts appear under their app. Launcher actions (open settings, switch workspace, edit home) are searchable |
| PAL-04 | M | Search system settings | A bundled index of settings screens resolves to working intents on the reference devices; entries with no handler are hidden |
| PAL-05 | M | Inline calculator and unit conversion | Arithmetic and common unit conversions answer inline; Enter copies the result |
| PAL-06 | M | Web fallback | The last result always hands the query to the default browser or a user-chosen search engine; a store lookup row ("Search Play Store for ...") sits just before it. The query is sent as typed: no `!bang` syntax and no bang chips (wave 18, owner request; a leading letter and a space is ordinary text). No query leaves the device before the user selects it |
| PAL-07 | M | Keyboard operation | Up/Down moves selection, Enter runs the primary action, Tab or Right opens the action list for a result, Esc closes. Focus never leaves the palette unexpectedly Wave 18: in the Orbit the order the keys walk is the ring order, innermost ring first and outward, because results are laid out best first ring by ring (PAL-18) |
| PAL-08 | M | Result actions | Every app result offers: open, open on another display (T3), open adjacent (where supported), app info, add to dock, add to workspace, uninstall. The same list appears on long-press and right-click |
| PAL-09 | M | Ranking | Ranking blends match quality with local frequency and recency. Selections are learned on device only and can be reset Wave 18: with an empty query the frequency is also what lays out the resting Radar (PAL-18): a decayed use count per app, person and result, half the weight every 14 days, from the launch history and what Radar learned (the larger of the two, so a launch made from Radar is not counted twice). Wave 20: "Forget what Radar learned" also stamps the moment, and the resting Radar ignores the home's launch history from before it (the history itself stays the home's, for recents) |
| PAL-10 | S | Contacts | With contacts permission, contacts are searchable with call, message and open actions. Without it, the category shows a one-tap enable row and nothing else |
| PAL-11 | S | Files | Media and user-granted folders are indexed incrementally. Results open with the default handler. The index survives reboot and reconciles changes in the background |
| PAL-12 | S | Background search | Slow providers stream results into the open palette without blocking typing or reordering results under the selection. Closing the palette cancels pending work; reopening with the same query restores results Wave 18: a result keeps its Orbit ring while its rank is unchanged; a ring changes only when the rank crosses a ring boundary (5, 14, 27 results in) |
| PAL-13 | S | ~~Scoped search by typed prefix~~ | **Removed by owner decision, wave 18** (build 49: "the search suggests typing an alphabet, then a space and the query, that doesnt work for web, or anything else for that matter and it looks ugly so remove it"). There is no typed prefix: a query such as `w bows of denmark` is plain text and simply searches apps, contacts, settings, files and the web hand-off. The scope is the category wheel and nothing else (PAL-19). The empty-state prefix chips, the Ground Control "Search prefixes" editor, the `a`/`f`/`s`/`c`/`w`/`>` prefix parsing and the `!bang` chips and syntax are gone; see `docs/decisions/radarSearchCleanup.md` (restore with `git revert`). Was: prefixes limit scope (for example ` a  ` apps, ` f  ` files, ` s  ` settings, ` c  ` contacts), listed in the empty state and editable |
| PAL-14 | S | Provider interface | Providers implement one internal interface (query, cancel, result with actions). Adding a provider needs no palette UI change |
| PAL-15 | C | Store lookup | A result row offers to search the installed app store for the query |
| PAL-16 | C | On-device semantic search | Natural-language queries over file names and app descriptions using an on-device model; off by default |
| PAL-17 | C | Third-party provider API | Other apps can register providers through a documented, permission-guarded interface |

**Notes**

- Index location: app-private storage only. PAL-11 uses MediaStore and folders the user grants through the system folder picker; all-files access is not requested.
- The empty state shows recent apps, recent files (if granted) and pinned actions, so the palette is useful before typing.

### Radar as built

Radar already has the surface; PAL-01 to PAL-14 above are added behind it. These requirements cover what is specific to the existing design.

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| PAL-18 | M | Three layouts | Flat, Orbit and Filmstrip stay selectable. All three meet PAL-07. When the active input is keyboard or pointer, results default to Flat unless the user has pinned a layout. All three draw over the same black base and the same two-slot background as the home (HOM-16): the wave 14 Radar-only background setting and the bundled backdrop picture are gone **Wave 18, owner request on build 49 ("the first circle should have apps most frequently searched for, and further rings have lesser frequency opened items")**: the **Orbit rings are tiers of use**. Results are laid out best first, ring by ring: with an empty query the innermost ring holds what you open most (apps first, then people, ranked by decayed use, see PAL-09), the next ring the next most, and so on outward; ring capacities grow outward (5, 9, 13, 17, and never more than a ring has room for on the screen); with a query the order is match quality blended with use (PAL-09) and the best match is on the innermost ring. Ties are stable (newest first). Each ring is a faint guide circle, brighter toward the middle, and TalkBack hears it as a tier ("Most used", "Often used", "Sometimes", "Rarely"; each tile also states its tier). With nothing learned yet the Orbit, Filmstrip and Flat show the quiet line "Rings fill up as you use Radar". Flat and Filmstrip use the same use-blended order for their empty-query list (Flat shows a "Most used" list at rest; Flat used to show nothing at rest). **Wave 20: the Orbit stays on screen while typing**: the rings, the wheel and the query line stay, and the results re-lay themselves on the rings as you type, best match innermost (ring capacities 5, 9, 13, 17 as the screen allows; a result changes ring only when its rank crosses a ring boundary, and keeps its place inside the ring between keystrokes); TalkBack hears the tiers as "Best match", "Close", "Related", "Other"; the web hand-off rows ("Search <engine> for ...", the Play Store row) and a Filenames refinement sit on a compact one-line strip at the bottom of the free area, below the rings, so they stay one tap away and the rings are centred above them; Enter opens the first (innermost) result; with a hardware keyboard or pointer driving, results are still Flat unless a layout is pinned (see docs/decisions/orbitTyping.md); the Filmstrip and Flat show the typed list as before. **The query line is always at the top** of the screen, below the status bar and cutout, in all three layouts and both orientations; the results are below it and the keyboard is at the bottom, so results never hide under it; the rings are centred on the free area below the query line. (Was: the query line sat at the bottom above the keyboard while typing and was an invisible field while browsing) |
| PAL-19 | M | Category wheel is the scope control | The wheel entries (Everything, The Web, Actions, Apps, Contacts, Files) are the only scope control; the scope menu next to the query line (Flat and typed results) offers the same entries. Left/Right moves the wheel from the keyboard. Wave 18: the coupling of the wheel to typed prefix letters is gone with PAL-13; picking a scope never rewrites the query text. Was: wheel entries and PAL-13 prefixes were one setting |
| PAL-20 | S | Preview and text size everywhere | Long-press or right-click preview, and pinch or Ctrl+scroll text size, work in all three layouts. Orbit and Filmstrip captions scale with the text size Wave 18: the preview and text size work on the rings in their new places; the tier of a tile does not change what the preview shows |
| PAL-21 | M | Web search stays private | Web queries are handed to the browser as a URL only when the user selects the web row. Radar itself makes no network request and fetches no suggestions |
| PAL-22 | S | Sibling-app providers | Radar consumes the provider contract in `docs/SEARCH_PROVIDER.md`. Fylz and Fotoz results appear when those apps are installed and expose it; otherwise no empty category is shown |
| PAL-23 | S | Orbit feel | Spin creep and fling constants are adjustable in the Lab page (CFG-17) so they can be tuned on a device; final values are committed as defaults |
| PAL-24 | S | Signal views: dot grid, glow lines, comic lines, radar sweep | The signal-driven visuals of wave 14, now four of the nine items of the background model (HOM-16). **Breathing dots** (owner's SVG, `docs/design/radar-dot-grid.md`: 24 dp pitch, radius 2 dp swelling to 3 dp, near-white at 30 percent x #D9D9D9 through an elliptical falloff, brightest in the middle, invisible at the edges; landscape follows the 956 x 440 artboard, portrait the 440 x 956 one; each dot breathes in a slow radial wave plus a per-dot phase offset, 5.5 s period; 8 alpha buckets with round-cap points, nothing allocated per frame); with the level the breath gets deeper (15 to 70 percent) and the lit radius wider (40 to 100 percent of the grid). **Radar sweep** (rings, crosshair, centre dot, a conic wedge turning once per 6 s and a ring ping): with the level the wedge brightens (30 to 100 percent) and pings come more often (none with no signal, then every 9 s, 6.5 s, 5 s, 3.2 s). **Glow lines** (owner's SVG, `docs/design/radar-glow-lines.svg`): eight bracket shapes grey at rest, blue pulses travel along their centre lines with a glow of three stacked strokes (no blur, no RenderEffect), a calm ellipse keeps the middle quiet; level 0 no pulse, 1 two slow faint pulses, 2 four lines, 3 six lines, 4 all eight with two bright pulses each, never faster than one pulse per 3 s at a point (nothing above 3 Hz). **Comic lines** (manga speed lines from the screen edge inward, irregular, 8 redraws a second at low alpha, outer third only) appear only with no connectivity (level 0), over whatever items are chosen, fade out in about 400 ms when signal returns; they are the reverse calm-computing indicator and are always on (the wave 14 switch was removed with the Radar-only settings). Signal comes from the default-network callback and `NetworkCapabilities` (transport, validated, strength), debounced 600 ms, collected only while a surface that draws the background is on screen and started, off the main thread, through an injectable `SignalSource`; no telephony listener, no new permission (ACCESS_NETWORK_STATE only), no traffic of our own (NFR-16, NFR-17). The level eases over about 1 s. One animation clock (one infinite transition read in draw lambdas) runs only while the activity is resumed and the window focused, and never under reduced motion (NFR-14: one static composition at the current level). Text keeps AA over every frame (NFR-15). TalkBack hears the level once, politely ("Signal: good, Wi-Fi"); the Lab page (CFG-17) forces No signal, Weak, Fair, Good, Excellent or Live. The Radar-only pane (Sweep, Signal view, Comic lines switches) and the Radar background style setting were removed in wave 15 (docs/decisions/backgroundsReset.md) |

PAL-22 replaces PAL-17 as the first real use of an external provider; a public third-party API stays Could.

## 7. Keyboard, pointer and touch (INP)

Every launcher surface is fully operable with touch alone and with keyboard and pointer alone, and switches affordances as the active input changes.

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| INP-01 | M | Focus graph | Every interactive element is reachable with arrow keys and Tab in a predictable order on every surface, including XMB, DSi, the puck and Radar's Orbit. A visible focus ring appears only in keyboard mode |
| INP-02 | M | Pointer support | Hover states, pointer icons, scroll-wheel and trackpad scrolling, and secondary click on every item. Secondary click opens the same menu as long-press |
| INP-03 | M | Single context-menu model | One menu definition per item type feeds long-press, right-click and the Menu key. A test asserts the three produce identical entries |
| INP-04 | M | Shortcut registry | All shortcuts are declared in one registry with a default binding. A shortcut sheet (opened with a key and from settings) lists them; the launcher also publishes them to the system shortcut helper |
| INP-05 | M | Default shortcut set | Open palette, open drawer, focus dock item 1 to 9, switch workspace, open settings, edit mode, move item (arrows in edit mode), dismiss. All work on every surface |
| INP-06 | M | Input-mode tracking | The last input event sets the mode (touch, keyboard, pointer). Switching mode changes affordances within one frame and never moves focus or loses state |
| INP-07 | M | Touch parity | No feature is reachable only by hover, right-click or a key. An automated audit enumerates actions and fails if one lacks a touch path |
| INP-08 | M | Gestures | Swipe, long-press, pinch and double-tap on home are each assignable to a launcher action. Defaults do not conflict with system back or home gestures. "Open recents" (STR-14) is one of the assignable actions, with Ctrl+R as its shortcut; the swipe in from the right edge is the default way in and is not a system gesture exclusion |
| INP-09 | M | Edit by keyboard and by drag | Items can be moved, resized (widgets) and filed into folders by drag and by keyboard alone |
| INP-10 | S | Remappable shortcuts | Users rebind any registry entry; conflicts are detected; bindings export with the config |
| INP-11 | S | Drag and drop | Drag an app or shortcut from drawer or palette to home, dock or folder. Drop text, links and files from other apps onto the home surface to create shortcuts, where the platform delivers the drop |
| INP-12 | S | Force keyboard open | Option to raise the on-screen keyboard automatically when the palette or drawer opens, set per orientation Wave 18: Radar's query line is at the top in every layout and both orientations, so the keyboard sits below the results; there is no bottom-position setting (the option never existed as a setting, the line was simply drawn at the bottom while typing), and focus lands in the field on open. Wave 20 gap, owner decision open: Radar raises the on-screen keyboard on every open (it is a type-to-search screen); the per-orientation option governs the drawer's filter field only |
| INP-13 | S | Voice entry | A push-to-talk control feeds on-device speech recognition into the palette. If no on-device recogniser exists, the control is hidden. Recognition must never start without an explicit press |
| INP-14 | C | Global shortcuts outside the launcher | Elevated tier only. See section 14 |

**Constraint to respect.** Meta-key system shortcuts belong to the OS and cannot be overridden. Launcher shortcuts apply only while a launcher surface has focus; the shortcut sheet says so.

### Corners and puck

The corners and the puck are Liftoff's signature touch controls; these requirements give each a pointer and keyboard form.

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| INP-15 | M | Corner assignments | Each of the four corners can be set to an app, shortcut, launcher action, app group or nothing, with an optional separate set per orientation. Holo, Sony quarter-disc and Bubbles styles all honour the same assignments |
| INP-16 | M | Hot corners | With a pointer, resting in a corner reveals its action after a short dwell (default 150 ms; adjustable or off); click activates. Never fires during a drag On the Glance home (HOM-23) a disabled hotspot has no dwell reveal and does nothing. |
| INP-17 | M | Corners by keyboard | Each corner has a default shortcut and is a node in the focus graph, reachable with arrows from the nearest edge item On the Glance home (HOM-23) each enabled hotspot is a focus node; a disabled one is not. |
| INP-18 | M | Puck by keyboard and wheel | A shortcut opens the puck. Left/Right or the scroll wheel rotates it, typing a letter jumps to it, Tab cycles A-Z, Category and Recent, Enter launches, Esc closes |
| INP-19 | M | Puck sizing | Ring and hub spacing derive from window size and stay usable from the compact size class up; no overlap or clipped actions at the smallest supported window |
| INP-20 | S | Controller input | A game controller's D-pad, confirm and back buttons drive every surface as arrows, Enter and Esc |

## 8. Home surfaces, drawer and widgets (HOM)

Home is a host that renders the user's items through a selectable home style (Grid, List, XMB or DSi) and always offers the same organisation tools underneath.

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| HOM-01 | M | Selectable resting surface | User picks Grid, List, XMB or DSi, paged or continuous. Switching keeps all items, folders and widgets. Each style meets INP-01 and the frame budget in section 13. A fresh install starts with one empty page and an empty dock: no apps are placed, a quiet hint ("Long press to add apps") opens edit mode with the add carousel focused, and apps stay reachable through the add carousel, the app drawer, Radar and search. Existing users keep their saved layout untouched (the empty default is decided by the absence of a saved layout, never by overwriting one). This is the Selected apps landing of HOM-22 (wave 16: no longer the default landing, which is the Glance home of HOM-23; existing layouts are untouched); the home can also land on Recent apps, All apps or Glance, and switching landing never changes the saved layout. The empty-home hint also offers "Or show recent apps" (HOM-22) |
| HOM-02 | M | Item types | App, shortcut, folder, widget and file/link shortcut can all live on home and in the dock |
| HOM-03 | M | Icon rendering | Adaptive icons render at correct shape and size with no dark tile behind them. Themed (monochrome) icons and icon packs are supported; per-item icon override |
| HOM-04 | M | Folders | Create by dropping one item on another or from the menu; name, reorder, open by tap, click or Enter; searchable by name. The add carousel in edit mode has a Folders tab (new folder) beside Apps, Widgets and Shortcuts |
| HOM-05 | M | App drawer | Full list with alphabetical index, type-to-filter, work and personal tabs, user tags and hidden apps. In edit mode the same inventory (hidden apps stay hidden) is the add carousel's Apps tab: alphabetical, type-to-filter, Personal and Work chips; a tap adds the app to the first free cell of the current page (next page with room, else a new page), and a long-press drag onto the card does the same. The same list, index, filter and tabs are the All apps landing of HOM-22, with the same hidden-apps rule |
| HOM-06 | M | Profiles | Work profile apps are badged and can be paused. Private space appears only when unlocked (Android 15+) |
| HOM-07 | M | Widgets | Add from a searchable picker, resize by handles and by keyboard, reconfigure, remove. Widgets restore after reboot and after provider update |
| HOM-08 | M | Widgets mixed with tiles | Widgets occupy the same layout model as app tiles on the grid surface. On List, XMB and DSi, widgets live in a defined widget band or panel, not overlapping tiles |
| HOM-09 | M | Notification badges | Dots with optional count on app items when notification access is granted; long-press or right-click previews notifications |
| HOM-10 | M | Package changes | Install, update, uninstall, disable, archive and suspend are reflected within 1 second without restart; removed items leave no gaps or crashes |
| HOM-11 | M | Adaptive layout | Grid dimensions derive from window size class and user density setting. Rotating, folding, resizing or moving displays reflows without losing positions; each size class keeps its own arrangement |
| HOM-12 | M | Edit mode | Explicit edit mode with undo for the last 20 changes. Edit mode shrinks the live home into a rounded card (about 28 dp corners, scale about 0.7) pushed to the start side (left; right in RTL) with its outer edge partly off screen, still directly editable; the freed background holds a vertically scrolling page carousel of live thumbnails (current page highlighted, tap to show, Delete page with an undo notice, Add page, reorder by long-press drag or Move up and Move down; the last page cannot vanish: deleting it empties it) and the add carousel (Apps, Widgets, Folders, Shortcuts). Arrows, Tab and Enter reach everything, Esc or Done returns; reduced motion replaces the shrink with a cross-fade; the card is one graphicsLayer scale and translate on the live home, which keeps its state. The dock steps aside while the card is up |
| HOM-13 | S | Free-form placement in T3 | On desktop surfaces, items may be placed off-grid with snap guides; widgets may overlap empty space but not each other |
| HOM-14 | S | Widget stacks | Several widgets share one slot, switched by swipe, scroll wheel or keys |
| HOM-15 | S | Files and recent documents | A home panel or widget lists recent files from granted sources and pinned folders |
| HOM-16 | S | Background: two signal-driven slots | **Owner decision, wave 15: the previous gallery was removed** (every built-in kind: solids, gradients, textures, live scenes, the procedural Horizon picture; the custom picture; the system-wallpaper option with its scroll-offset forwarding and wallpaper-derived icon accent; the Lab gallery UI). The background of the app (home, Radar and every surface that draws one) is up to **two distinct items chosen from nine, at most one of them a living wallpaper**; None is a valid state of each slot (pure black). **Living wallpapers (5), drawn procedurally in one Canvas** from layered radial, linear and sweep gradients (no bitmap, no RenderEffect blur; additive cores; a precomputed film-grain tile against banding; slow breathing of a few percent): **Beam** (violet beam from the right edge in landscape, from the bottom edge in portrait; reach, width and the white-lilac tip grow with the level, a beaded sparkle band along the core), **Spire** (blue cusp rising from the bottom; it rises higher and its white core brightens), **Halo** (rose ring, tilted; the bright arc sweeps from a short arc to a nearly closed ring, the haze brightens), **Laser** (red vertical beam on a floor line with a thin arc over it; beam length, arc run and floor glow grow), **Shaft** (violet diagonal shaft from the top edge to the lower right; it widens and brightens). **Effects (4)**: **Glow lines**, **Breathing dots**, **Radar sweep** (PAL-24) and **Bottom glow** (a bloom rising from a bright line near the bottom edge, white-hot at the line and blue above; its height and brightness grow). Every item is a SignalView: it glows differently with the level (0 none, 1 to 4) as set by one table of parameters per item (`BackgroundCurves`), eased over about 1 s; with no signal it falls to a dim resting hairline or ember and the comic lines (PAL-24) appear at the periphery. Default pair for new installs and for every migrated user: **Spire + Breathing dots**. Any stored choice of the old gallery maps to the default pair; old exports and imports are accepted (the retired keys are dropped; export schema 3, settings document 2). Ground Control > Background: Slot 1 (any of the nine or None), Slot 2 (any other item or None; options the one-wallpaper rule or an item already in the other slot rules out are disabled and say why), a live thumbnail on every option at the preview level, a "Preview signal" control that changes only the pane (the Lab override is separate), Intensity (Subtle, Normal). One composable, `SignalBackground`, is behind every surface; the Settings backdrop "Background, dimmed" draws it still. Reduced motion: static at the current level. Nothing animates while the surface is off screen or the screen is off. Light theme: a quiet wash (no additive light). Text keeps AA over every item at every level (NFR-15, sampled from the real painters at levels 4 and 2). Plain prefs, exported; 8 languages |
| HOM-17 | C | Usage widget | Time-split widget from usage stats |
| HOM-18 | C | Timeline view | Chronological view of app opens with scheduled launches (see WSP-11) |

### Home frame as built

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| HOM-19 | M | Uniform frame | The margin band (about 15% of the short side) keeps the app area in the same region in both orientations for every home style. Corners, clock, page dots and dock rail sit inside the band and never overlap the app area |
| HOM-20 | M | Clock band fit | With the clock at the bottom, the weather line and page dots fit within the band at every supported window size, by scaling or dropping the weather line first. Closes the cramped layout noted in the handoff |
| HOM-21 | M | Long-press quick sheet | The existing quick sheet is also opened by right-click on empty home and by the Menu key, and lists the same entries (INP-03) |
| HOM-22 | M | Home landing | A setting with four landings, one at a time, switchable at any time without losing anything (only the setting changes; pages, folders, widgets, tags and hidden apps are untouched). **Selected apps**: the home of HOM-01 (the user's pages, folders, widgets, style Grid, List, XMB or DSi, paged or continuous). **Recent apps**: the apps opened most recently, newest first, one per app, bounded (default 24, setting 8 to 48), in the chosen home style (grid by default; no pages). Sources: Liftoff's own launch history (always, private) plus UsageStatsManager foreground events when usage access is granted (never required; the empty state says how to include apps opened elsewhere). Public API only: no getRecentTasks, no reflection. Hidden apps excluded, work-profile apps kept and badged (filterable), Liftoff itself never listed; a Liftoff-launched app still in a session goes first only if it is already listed. Long press, right-click or the Menu key offers the normal app menu plus "Remove from recents" and "Clear recents" (a removed app returns when launched again). Quiet empty state with a one-line explanation. Updates live from a Flow computed off the main thread. **All apps**: the full alphabetical list in the chosen home style with alphabet index, type-to-filter and Work/Personal tabs (the drawer's content as home), hidden apps respected. **Glance** (wave 16, owner redesign, replaces Minimal; stored name still "Minimal"): the quiet default home of HOM-23, a near-empty surface with the clock and date, the music playing at the top, an optional weather ambience, up to four hotspots and a surface indicator, over the two-slot background (HOM-16). No strip, dock (unless Right side is the dock rail), page dots or tiles. Edge swipes, gestures, shortcuts and a long press (the quick sheet with Glance content, Hotspots, Background, Landing and Edit home pages) keep working. Switching: Ground Control > Home > Landing (four options, one line and a glyph each), the quick sheet (four-way segmented control), the command "Cycle home landing" (default Ctrl+Shift+L) and the assignable corner and gesture action "Cycle home landing". Edit mode (long press into the left card) exists only on Selected apps; on the others a long press opens the quick sheet, which has "Edit home pages" (switches to Selected apps, then enters edit mode). The landing is global (workspaces keep only an opaque home arrangement), a plain pref in the versioned state, exported. Wave 15: the Recent landing and the Recents panel of STR-14 share one source (`HomeRecents`), and the usage events now include background events (ACTIVITY_PAUSED, MOVE_TO_BACKGROUND), so an app is placed by when it was last launched from Liftoff or last seen going to the background. The removals of the Recent landing are private usage history, never exported or backed up (same class as the timeline log). The default landing is Glance (wave 16, owner request: "the default for the launcher should be an empty or near-empty screen") for everyone who never chose a landing, existing installs included: a stored Recent apps or All apps was always an explicit choice and is kept, a stored Minimal becomes Glance, and a stored Selected apps cannot be told from the old default (every save wrote it) so it counts as not chosen; from then on an explicit choice is kept through a "landingChosen" marker (a pick of Selected apps sticks). Cycle order: Selected apps, Recent apps, All apps, Glance. The empty-home hint of HOM-01 shows only on Selected apps. No network. Cross-fade between landings, instant under reduced motion. RTL, both orientations, light and dark, 8 languages, INP-01 to INP-03 |
| HOM-23 | M | Glance home | The new default landing (wave 16, owner request, decisions in docs/decisions/glance.md). One composition that rotates with the display, pure black (a quiet light surface with dark type on the light theme) over the two-slot background of HOM-16 (default Spire + Breathing dots; the glow lines flank the clock). **Centre**: the clock (large, thin, tabular digits, 12 or 24 hour by the clock format setting or the system, no seconds; not mirrored in right-to-left) with the date line (weekday, day, month in the user's language; it wraps) under it. A "Glance content" setting with presets Time; Time + Date (default); Date + Weather; Time + Date + Weather, and a free toggle set (time, date, weather, now playing, battery); the temperature sits next to the date when Weather is on. A tap on the clock runs the Clock corner action (default: the clock app). **Now playing** at the top centre: music note, title and artist from the media session source (STR-06), one line, a marquee only when it does not fit (and never under reduced motion), hidden when nothing plays (its height is reserved so the layout stays put). A tap anywhere on empty Glance surface (not a hotspot, not the clock) pops the CONTROLS in: a black notch-like pill drops from the top edge (spring scale and translate, about 250 ms) holding Previous, Play/Pause and Next (48 dp targets, TalkBack labels, keyboard focus nodes; the music line itself is a focus node whose Enter pops them in) above the title; they pop out after N seconds of inactivity (default 4, setting 2 to 10; any interaction with the controls restarts the timer, it is paused while a finger is down), on a tap outside, or when playback ends or the screen stops; reduced motion fades; with nothing playing a tap does nothing visible. **Weather ambience**: with Weather in the content and weather available (the existing WeatherSource, the only network user, NFR-16/17 unchanged), one Canvas behind the clock and below the glow lines and dot grid: clear day a soft warm glow, clear night a few slowly twinkling stars, clouds drifting soft shapes, rain sparse thin diagonal streaks, snow slow flakes, fog faint drifting haze, thunder storm clouds and a very soft flash at most once per 23 s (floor 20 s, a single smooth pulse, far under 3 Hz); Subtle (default) or Normal; shapes precomputed, nothing allocated per frame, one shared clock read in draw, a still frame under reduced motion, no animation while the screen is off or the surface is not visible; absent when weather is off or unavailable. **Hotspots 0 to 4**: each corner is on or off (presets Four, Two (bottom), One (bottom right), None; a free toggle per corner), stored by physical corner of the natural portrait orientation so the whole composition rotates with the device; assignments are the existing corner assignments (INP-15), the factory set is the owner's reference (camera top left, music top right, apps bottom left, search bottom right); the corner draws the glyph of its assigned action; a disabled hotspot draws nothing and its area does nothing (the dwell reveal included); with none the edge swipes, long press and keyboard shortcuts still work; each enabled hotspot is a focus node (INP-17) and a labelled button for TalkBack; the dwell reveal (INP-16) is kept. **Surface indicator** at the bottom centre: a list glyph for Launchpad (when it is on), the home glyph (current, brighter, not interactive) and one dot per right-side destination (RightSideDestinations: the Recents panel or the dock rail), mirrored in right-to-left, fading to low alpha after 4 s, can be turned off. **Launchpad and recents from Glance** (wave 17, owner bug report on build 48: "The glance needs to have launchpad on the left. Why is that missing?", decisions in docs/decisions/glanceEdges.md): Launchpad is reachable by the edge swipe from the physical left edge (the right in right-to-left; the swipe zone is 48 dp wide on Glance so it can start where the handle is), the quiet edge handle, the indicator's list glyph (a 48 dp button, "Open Launchpad"), the keyboard shortcut and the corner and gesture action "Open Launchpad"; recents (Right side: Recents panel) the same way on the other edge (edge swipe, handle, the right dot as a "Open recents" button, Ctrl+R, the action). The handle is a slim bar at low alpha that fades to nearly nothing after 4 s of inactivity and returns on any touch; its tap target is 48 dp wide by 160 dp tall at the edge, a labelled, focusable button. The waiting-notifications indicator is drawn as a quiet dot above the left handle (Priority accent of STR-13), a 48 dp target. No Launchpad rail on Glance unless the rail setting is on. With Launchpad Off no left affordance is drawn and nothing is dead; with Right side Off or Dock rail there is no recents handle. Which chrome each landing draws is the explicit table `HomeChromeRules` (left handle, waiting indicator, rail, right handle, media strip, dock, work banner), tested for every landing. Light and dark, both orientations, font scale to 200 percent (the clock grows within the window, the date wraps, nothing overlaps a hotspot), reading order clock, date, weather, now playing, controls, hotspots. The Selected, Recent apps and All apps landings, edit mode (Selected only), Launchpad groups and the quick sheet are unchanged. 8 languages, settings are plain exported prefs |

Items the handoff does not mention, and which the audit must therefore report as present or missing before work starts: folders, widget hosting, app shortcuts, notification badges, work profile, icon packs and themed icons.

## 9. Dock, launching and workspaces (DCK, WSP)

The dock shows what is pinned and what was recently used, and every launch can carry a destination: which display, what bounds, alongside what.

### Dock

Build 31 has no dock: on a phone, the corners and the puck's Recent ring already cover quick access. The dock is therefore a new, optional edge rail inside the home frame. It is off by default in T1 and on by default in T2 and T3, where a keyboard, pointer or large window makes a persistent strip of pinned and recent apps worth the space. In landscape it may sit on the bottom or either side.

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| DCK-01 | M | Pinned items | User-ordered pinned apps, shortcuts and folders; reorder by drag and by keyboard |
| DCK-02 | M | Recent section | With usage access, a separate section shows the most recently used apps not already pinned, newest first, updated when the launcher resumes. Labelled "Recent", never "Running" |
| DCK-03 | M | Recency marker | Pinned apps used in the current session carry a marker. The marker's meaning (recently used) is stated in the dock's help text |
| DCK-04 | M | Dock modes | User chooses collapsed band or pinned-plus-recent. In T2 and T3 the dock is persistent; in T1 it is off unless enabled |
| DCK-05 | M | Keyboard access | Shortcut focuses the dock; number shortcuts launch items 1 to 9 |
| DCK-06 | M | No usage access | Without the grant, the recent section is replaced by a single enable row; pinned items work fully |
| DCK-07 | S | Per-display docks | Each display with a launcher surface has its own dock instance sharing the same pins |
| DCK-08 | S | Media item | When a media session is active, the dock shows a compact transport control |

The launcher has no way to keep its dock visible on top of other apps without an overlay permission, and the system provides its own taskbar in desktop windowing. The dock therefore lives on launcher surfaces only. An always-on-top dock is not in scope.

### Launching

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| WSP-01 | M | Launch request model | All launches go through `LaunchRequest`. The result reports what was applied; if a requested placement was dropped, the user sees a brief, non-blocking notice once per session per reason |
| WSP-02 | M | Open on display | Where more than one display can host tasks, every app action list includes "Open on \<display name>". Verified by the task appearing on that display |
| WSP-03 | M | Open adjacent | Where SPK-02 confirms support, "Open beside" launches the app into split with the current task. Where unsupported, the action is not shown |
| WSP-04 | M | Open with bounds | On displays in desktop windowing, launches may carry bounds (left half, right half, centred, full). Where bounds are ignored, the app opens normally and WSP-01's notice applies |
| WSP-05 | M | App groups | A saved group of 2 to 4 apps with a layout preset launches with one action. Appears as a home item, dock item and palette result. The add carousel's Shortcuts tab offers App group |
| WSP-06 | M | Launch animation | App opens animate from the source item using the public activity options. No custom return-to-home animation is attempted beyond what the system provides |

App groups launch their members in sequence with a short delay and re-check the result of each. On T1 with no split support, a group opens its first app and offers the rest as a quick switcher.

### Workspaces and sessions

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| WSP-07 | S | Workspaces | Named contexts, each with its own home arrangement, dock pins, widgets and palette pinned actions. Switching takes under 150 ms and is reachable by shortcut, gesture and palette |
| WSP-08 | S | Workspace-scoped notification filter | Each workspace may define which apps surface in the calm centre while it is active (display filter only; it does not change system notification settings) |
| WSP-09 | S | Sessions | Save the current app group plus display targets as a named session; restore reopens them. Restores app placement only, never in-app state, and says so |
| WSP-10 | S | Display-aware restore | Restoring a session when a target display is missing falls back to the current display and reports it |
| WSP-11 | C | Scheduled launches | A timed trigger posts a notification with an Open action for an app or group. Auto-launch without a tap is elevated tier only |
| WSP-12 | C | Automation hooks | Workspaces and sessions are callable by explicit intents so Tasker-style tools can trigger them |

## 10. External display (EXT)

On a connected display the launcher provides a desktop surface as its own activity, coexisting with whatever desktop session the system starts there. It does not assume it is the home app on that display.

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| EXT-01 | M | Display detection | Displays are tracked live through `DisplayManager`. Connect and disconnect are handled within 1 second with no crash and no lost state |
| EXT-02 | M | Desktop surface | When a display that can host tasks connects, the launcher offers (once, then per user setting: always, ask, never) to open its desktop surface there. The surface is a resizable activity launched with that display's ID |
| EXT-03 | M | Independent state | The phone keeps its own home surface and state. The desktop surface has its own arrangement, dock instance and focus |
| EXT-04 | M | Density and metrics | The desktop surface lays out from its own window metrics and density. Verified on displays at 1080p and 4K with no clipped or oversized elements |
| EXT-05 | M | Windowed operation | The desktop surface works maximised, as a freeform window and in split, down to the compact size class |
| EXT-06 | M | Disconnect | On disconnect the surface closes cleanly or migrates to the primary display without duplicating the home surface. Arrangement for that display is kept and restored on reconnect |
| EXT-07 | M | Launch targeting | From either display, apps can be opened on the other (WSP-02). Apps that refuse a secondary display fall back with the WSP-01 notice |
| EXT-08 | M | Coexistence | The surface never covers or interferes with the system taskbar. If the system's own home or taskbar is present on that display, the launcher's dock is inset accordingly |
| EXT-09 | S | Per-display profiles | Arrangement, density and dock mode are remembered per display (keyed on a stable display identifier where available, otherwise on resolution and name) |
| EXT-10 | S | Phone as input | While the desktop surface is focused, the phone can show a trackpad and keyboard panel that drives the launcher's own surface only |
| EXT-11 | C | Mirrored displays | When the external display mirrors the phone, T2 rules apply to the mirrored surface and no second surface opens |

**SPK-01 decides the shape of this section.** If a device does make the default home app the home of the external display, EXT-02 becomes automatic and the offer prompt is skipped. If it does not, the surface is an ordinary window the user can close, and the launcher must offer a quick way to reopen it (dock item, palette action, notification while connected).

EXT-10 cannot move the system pointer or type into other apps without an accessibility service. In the core build it controls only the launcher's desktop surface.

## 11. Launchpad: notifications and system state (STR)

Launchpad is a notifications page that sits beneath home: home floats above it as a rounded card and slides aside from the left edge to reveal it. It renders data from the notification listener and does not replace the system shade.

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| STR-01 | M | Reveal and peek | Dragging home from its left edge slides the card aside to show Launchpad; a partial drag peeks and springs back. Also opened by a visible handle, a click, and a shortcut, on every landing including Glance (wave 17: on Glance the handle is the quiet low-alpha form, the Launchpad glyph of the surface indicator is a button, and the waiting dot is quiet; see HOM-23). The whole home is one surface that slides sideways as a card (to the right; to the left in right-to-left), in both orientations: it shrinks a little (about 0.93 scale, top and bottom inset), rounds to 28 dp corners with a hairline lighter edge on its leading side, and dims (a black scrim rising to about half at rest) as it recedes. At rest it nearly leaves: only a thin sliver of its inner edge with the rounded corners (about 7 percent of the width) stays at the screen edge. The notifications panel is full screen beneath, static (it does not move), its content inset from the sliver side and fading in over the last part of the motion. There is no neighbouring page card. The card follows the finger 1:1 during an edge drag; release past a threshold or with a fling settles open, otherwise it springs back; taps, the handle and the shortcut settle in 0.45 s with an ease-out. Nothing scrolls sideways; tapping the sliver, dragging it back, Back, Esc, the handle or the shortcut returns home. Reduced motion cross-fades instead of travelling. The same geometry, card layer, scrim and 0.45 s settle also serve the Recents panel on the right-hand side (STR-14), parameterized by the edge the card goes to (wave 15, owner request: the mirror image; Launchpad itself is unchanged) (wave 9, owner redesign from a screen recording; was: a two-thirds card pushed right with the notifications in the freed column and a neighbouring card peeking in below; before that, portrait Launchpad takes the screen) |
| STR-02 | M | Content | Notifications grouped by app and conversation, newest first, in the background column beside the home card as ONE vertically scrolling list in which every section and every notification is visible at once (no accordion, nothing folded by default). Each notification is a roomy row: at least 72 dp tall, 16 dp horizontal and 12 dp vertical padding, a 40 dp app icon, the title in a clearly heavier and larger type than the text, up to two lines of text, the time at the end, 8 dp between rows, and a hairline and extra space between conversation groups. A conversation with several messages shows its latest message and a small "+N more" that expands in place when the user taps it (a choice, never a default). Section titles stay quiet; a compact title strip at the top jumps to a section (tap a title to scroll to it). Tapping a row opens the notification: its own PendingIntent is sent with the sender's background-start opt-in (Android 14 and later), and when that does not work the app's launcher activity is started; Launchpad closes only after a successful hand-off, and otherwise stays open with a short message. The notification is cancelled when it asked to be auto-cancelled; mark-read stays. The notification's own actions and inline reply are passed through the same way (a failed send is shown, not swallowed). Dismissing here dismisses in the system. With groups (STR-13) the section titles are the user's groups (priority groups first, then the built-in section for everything not in a group, quiet groups folded last) and the by-app / by-conversation grouping stays inside a section; with no groups defined it is as before |
| STR-03 | M | Row gestures with equivalents | Slide left runs the one primary action (default dismiss; set per rule). Slide right opens the action palette (snooze, open, mute app, make a rule). Both are also buttons shown on hover and focus, entries in the context menu, and keys (Delete, Menu). The one menu (palette, long press, secondary click, Menu key) also offers the group actions of STR-13: "Move to group...", with "Always put <person> in <group>" and "Always put <app> in <group>", "This is the same person as...", and "Make a rule from this" |
| STR-04 | M | Rules | Existing Launchpad rules persist in the state store and export with the config. Per app: show immediately, batch into a digest at chosen times, or silent in Launchpad only. Rules affect Liftoff's surfaces only, and Launchpad says so. A Priority group (STR-13) sits above these per-app rules: its notifications show at once, bypassing the app's digest and silent-in-Launchpad settings; a Normal or Quiet group's delivery override is only the default for an app with no rule of its own |
| STR-05 | M | Grant flow | Without notification access, Launchpad and the media strip each show a one-screen explanation and an enable button on first use; nothing appears broken or empty without explanation |
| STR-06 | M | Media | The media strip shows artwork, title and transport controls for any active media session, and feeds the dock media item (DCK-08) The Glance home (HOM-23) reads the same media source for its top music line and pop-in controls. |
| STR-07 | M | Test coverage | Launchpad has screenshot tests in both orientations for: no grant, empty, grouped, digest, light and dark, with the home card sliver in place, plus a mid-drag peek and a right-to-left case; plus device checklist entries for the slide, peek and row gestures |
| STR-08 | M | Keyboard and pointer | Launchpad meets INP-01 to INP-03; arrows move between section titles and rows and reach the home card (a focusable button); Esc returns to home with focus where it was |
| STR-09 | S | Waiting indicator | A slim indicator on the notifications edge of the home (the left edge; the right in right-to-left) shows the waiting count and expands briefly for a new item, subject to the app's rule. Never steals focus. Unread items of a Priority group (STR-13) count even when their app is on a digest or silent in Launchpad, and tint the indicator with the group's colour plus a star and a spoken "N priority", so colour is never the only cue; a Quiet group's items never count. On the Glance home (wave 17) the indicator is a quiet dot above the left handle, a 48 dp target with the same spoken text and the same Priority accent |
| STR-10 | S | System state row | Time, battery, connectivity and sound state at the top of Launchpad as four icon-only buttons in one row (clock, battery reflecting level and charging, connectivity reflecting the link, volume, vibrate or silent reflecting the ringer mode), drawn as vector shapes with no text. Each has a content description carrying the state ("Battery: 82%, charging"), is focusable with a visible focus ring, and tapping it opens the matching system panel. Tiles display state and do not claim to toggle what Android blocks (wave 9, owner request; was: text tiles with a value and a caption, wrapped in rows of two when narrow) |
| STR-11 | C | Digest widget | Batched notifications are also available as a home widget |
| STR-12 | C | Open system shade | A control opens the real system shade. Elevated tier only unless SPK-04 finds a stable public route |
| STR-13 | M | Groups | The user can define named groups (name, icon or colour, order, priority Priority / Normal / Quiet, optional delivery override) that become the sections of the Launchpad column. Assignment is manual (from a row's action palette and context menu: "Move to group...", "Always put <person> in <group>", "Always put <app> in <group>", "Make a rule from this") or by ordered rules (all conditions AND, first match wins; a manual assignment always beats rules; deterministic and previewable). A Person merges the same human across apps (sender names normalised for case, diacritics, emoji and "(WhatsApp)" style suffixes; "This is the same person as..." links; with the optional contacts grant, contact names, alternate names, phones and emails extend matching), so one person's chats from every messaging app land in one group. Rule conditions: app, person, sender name contains / starts with / equals, conversation title, text keyword, category (message, call, reminder), is a conversation, sender's last name equals mine, in my contacts, starred contact, time-of-day window. "My last name" is a setting (prefilled from the owner profile only with the contacts grant, otherwise typed; kept privately); last-name logic handles "Last, First", particles, several surnames and single names (no match, no crash). Priority groups sort first, bypass per-app digest and silent-in-Launchpad rules (Liftoff surfaces only; system Do Not Disturb is never touched) and may raise the waiting indicator with an accent; Quiet groups are folded. A Groups manager creates, renames, reorders (Move up / Move down for keyboard), deletes (snapshot first, CFG-04) and sets priority. The rule editor has a form builder, a live "matches N current notifications" preview with the first names, a sample-sender test field and a plain-language summary. Groups, links, assignments, rules and the last name persist in the versioned state store and the export, are excluded from cloud backup and device transfer, and a state with none behaves exactly as before. No network; matching runs off the main thread and handles 500 active notifications within a tested budget |
| STR-14 | S | Recents panel | The right-hand mirror of Launchpad (wave 15, owner request: "maybe RHS is where we put the recent apps"). A swipe in from the right edge (the left edge in right-to-left), a slim handle, the shortcut Ctrl+R and the assignable corner and gesture action "Open recents" shrink the live home into the same rounded card as Launchpad (0.93 scale, 28 dp corners, scrim, 0.45 s settle, spring-back, follows the finger 1:1) pushed to the LEFT with a sliver (the right in right-to-left); one geometry class is parameterized by the edge, not forked. The freed background is full screen and static and holds the recent apps: a roomy list (48 dp icon, label, "Recent" and how long ago, rows at least 64 dp tall), newest first, a "Clear all" button, a long press, right-click or the Menu key opens the normal app menu plus "Remove from recents", a tap opens the app, Esc, Back, a tap or drag on the sliver return home. One line states what the list can and cannot know. Data and honesty: Android has no public API for a launcher to read the system recent-tasks list (getRecentTasks and task snapshots are for system apps) and none to know whether an app process is alive. The source is (a) Liftoff's own launch history and (b) with usage access, UsageStatsManager events ACTIVITY_RESUMED, ACTIVITY_PAUSED and their deprecated MOVE_TO_FOREGROUND and MOVE_TO_BACKGROUND, so apps opened from a notification, a share sheet or the system also appear, ordered by when they were last seen going to the background (or launched); no hidden API, no reflection, no accessibility service. The Recent apps landing (HOM-22) and this panel are computed by ONE source (`HomeRecents`), with the same hidden-app, work-profile, removal and clear rules; the removals file stays private (never exported, never backed up). Right side setting (Ground Control > Home > Right side, a plain exported pref): Recents panel (default), Dock rail (the right-edge swipe reveals the existing dock rail instead; it never turns the dock on, and the dock stays optional everywhere) or Off (no handle, no swipe; the action says so in a toast). Launchpad and the Recents panel are never open together. A small provider (`RightSideDestinations`) tells the Glance surface indicator (HOM-23) which destinations the right side offers. On Glance (wave 17) the right handle is the quiet form (barely-there bar, 48 dp by 160 dp target, fades when idle) and the indicator dot is a button that opens the panel (or the dock rail). Reduced motion cross-fades; light and dark; both orientations; right-to-left mirrors the card, the sliver, the swipe edge and the handle; the content scales with font size up to 200 percent; no network |

System heads-up pop-ups are drawn by System UI and cannot be suppressed by a launcher. Reducing those is a matter of the user's notification channel settings; the centre links to them per app.

## 12. Ground Control, configuration and onboarding (CFG)

The whole setup is one portable file, settings are a real screen rather than a pop-up, and onboarding asks for each grant only at the moment its feature is first used.

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| CFG-01 | M | Export | One action writes layout, workspaces, sessions, shortcuts, palette aliases and settings to a single versioned file at a user-chosen location. No personal content (notifications, usage history, contacts) is included |
| CFG-02 | M | Import | Importing a file restores the setup on a clean install. Missing apps become placeholders that resolve when the app is installed. Widgets re-bind with one consent step each |
| CFG-03 | M | Schema versioning | Files from every earlier schema version import through tested migrations. Unknown fields are preserved on round trip |
| CFG-04 | M | Automatic local backup | Rolling snapshots (last 5) are kept in app storage and before every import or destructive edit (including deleting a Launchpad group, a group rule, a person link or an assignment); one-tap restore |
| CFG-05 | M | Settings screen | Ground Control keeps its cross-media-bar layout: categories across, settings down, inline controls. Fully operable by keyboard. Every setting is also a Radar result |
| CFG-06 | M | Onboarding | First run: set as home, choose home style, optional import. Nothing else is requested up front |
| CFG-07 | M | Just-in-time grants | Notification access, usage access, contacts and folder access are requested when the user first touches the feature, each with a one-screen explanation of what is read and that it stays on device |
| CFG-08 | M | Capability report | A settings page lists every capability from section 3 with its current state on this device and the reason for any that are off |
| CFG-09 | M | Crash recovery and safe mode | Two layers. Layer 1 (wave 18, the shared `crash-recovery` utility from Shared-Libraries-asoc): after any crash, ANR or native crash, the next start shows the recovery screen instead of the home (splash, plain-language summary, "View the full report" with Copy, Save file, Email / share sheet and WhatsApp on the user's tap, Continue, Discard, and a Reset-data section only after a repeated crash). It is installed as the first statement of `Application.onCreate` and shown from the first statement of `MainActivity.onCreate`. Layer 2 (wave 19, owner decision: safe mode is for CRASHES, never for quick restarts): if the launcher crashes again after Continue (the library's crash streak is 2 or more) or crashes twice within 30 seconds of start (the loop guard: a "starting" marker still set plus a crash-shaped exit record of the previous process, that is REASON_CRASH, REASON_CRASH_NATIVE, REASON_ANR or REASON_INITIALIZATION_FAILURE; below Android 11 a pending JVM crash report), it boots into safe mode. Two quick starts with no crash (installing over the running launcher, Home then relaunch, an app being replaced, a user-requested stop, a low-memory kill) never do. Safe mode is: a minimal grid surface with widgets disabled, restore from the last snapshot, Carry on, and a "View crash report" button that opens the recovery screen for the newest report. Wave 20: the library's streak only counts while its newest crash is under 30 minutes old, so a safe mode swiped away does not trap later starts. The sequence and what resets each: `docs/decisions/crashRecovery.md` |
| CFG-10 | S | Theming | Light, dark and wallpaper-derived palettes; density and type scale; all tokens come from the shared design system |
| CFG-11 | S | Preferences file compatibility | The settings portion of the export is a separable document so it can later follow a shared cross-app preferences format |
| CFG-12 | C | Import from other launchers | Read layout from a launcher backup format where it is documented |

CFG-09 matters more for a home app than for any other app: a crash loop in the default launcher can leave the device hard to use.

### Ground Control as built

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| CFG-13 | M | Cross-media-bar navigation | Left/Right changes category, Up/Down moves between settings, Enter edits, Esc backs out; the same model by touch, pointer, keyboard and controller, in both orientations |
| CFG-14 | M | Search | The search control filters settings by name and description, using the same index Radar uses for settings |
| CFG-15 | M | Configurable puck actions | The user chooses which actions sit on the puck ring and their order, replacing the fixed set; the count limit follows INP-19 |
| CFG-16 | M | Tactile style complete | Steppers, section headers and the remaining older pages use the tactile kit. One screenshot test per control type, light and dark |
| CFG-17 | M | Lab page (debug builds) | A Ground Control category that runs the capability spikes on the device, shows live input mode and display list, exposes feel tunables (PAL-23), forces the signal state every background item reads (PAL-24, HOM-16), and copies results as text |
| CFG-18 | S | Live preview dock | An always-visible live preview of the real home while settings change. Preview rails render in headless tests, closing the blank-rail defect in the handoff |
| CFG-19 | S | Translations | All strings exist in the 8 UI languages; CI fails on a missing key |
| CFG-20 | M | Package identity | The application ID is decided before any public or store build (see section 16); changing it later breaks updates for existing installs |

## 13. Non-functional requirements (NFR)

A home app is judged on being instant and never failing; these budgets are release gates, measured on the mid-range reference device. The numbers are proposed targets to confirm after the first benchmark run.

### Performance

| ID | Metric | Budget |
| --- | --- | --- |
| NFR-01 | Home press to interactive home, warm | 150 ms p95 |
| NFR-02 | Cold start to interactive home | 700 ms p95 |
| NFR-03 | Palette open to first results | 100 ms p95 |
| NFR-04 | Palette keystroke to updated app results | 50 ms p95 |
| NFR-05 | Frame time on home, drawer and surface transitions | No more than 1% janky frames at the display's refresh rate |
| NFR-06 | Memory at rest on home | Under 180 MB PSS with 10 widgets |
| NFR-07 | Background work | No wake locks; indexing runs in constrained jobs; under 0.5% battery per day at rest |
| NFR-08 | Install size | Under 25 MB download for the core flavour |

Budgets are enforced by macrobenchmark tests in CI, with baseline profiles shipped.

### Reliability

- **NFR-09.** Crash-free sessions at or above 99.8% across the beta cohort; zero known crash on start. Wave 18: crash capture is the shared `crash-recovery` utility (JVM crashes through its handler, native crashes and ANRs through `ApplicationExitInfo`); the launcher keeps only the local session ledger behind the crash-free percentage, shown in Ground Control > Backup > Crash reports. Upgrade-path smoke tests start the launcher over the state every earlier build wrote (`docs/decisions/startupCrashes.md`).
- **NFR-10.** No ANR in the launcher process during a 2-hour scripted soak.
- **NFR-11.** Full state restore after process death, reboot, package update, profile toggle and display hot-plug.
- **NFR-12.** No main-thread disk or binder calls on the home render path (StrictMode clean in debug).

### Accessibility

- **NFR-13.** TalkBack can reach and operate every action; XMB, DSi, the puck and Orbit expose a linear accessible order.
- **NFR-14.** Works at 200% font scale and with reduced motion (surface morphs become cross-fades).
- **NFR-15.** Contrast meets WCAG AA over any background item (HOM-16) at any signal level, using a scrim where needed; the scrim is sized from the real painters drawn into a small bitmap.

### Privacy and permissions

- **NFR-16.** The core flavour declares no `INTERNET` permission. Web results hand off to the browser.
- **NFR-17.** No analytics, no crash upload. Crash reports are saved locally and shared only by explicit user action. Wave 18: the report and its Copy, Save file, Email and WhatsApp / share-sheet actions are the shared recovery screen's, each on the user's own tap; the module has no dependency and the manifest gains no permission. The Ground Control "Crash reports" pane shows only whether a report is waiting, the local crash-free percentage and (debug and alpha Lab builds) a preview of the recovery screen with sample content.
- **NFR-18.** Indexes, usage history and notification content are stored in app-private storage and excluded from export and from system cloud backup.

| Permission or access | Needed for | When asked |
| --- | --- | --- |
| Home role | Being the launcher, shortcuts, private space | Onboarding |
| `QUERY_ALL_PACKAGES` | App catalogue (permitted for launchers; declare in store listing) | Install time |
| Widget binding | Hosting widgets | First widget added |
| Notification access | Badges, calm centre, media | First use of any of them |
| Usage access | Recent section, usage widget, timeline | First use |
| Contacts | Contact search | First contact search |
| Media read and folder grants | File search, recent files | First file search |
| Microphone | Voice entry | First press of the voice control |

Not requested in the core flavour: accessibility service, overlay, all-files access, exact alarms beyond what scheduled launches need, device admin.

### Compatibility

- **NFR-19.** Edge-to-edge and predictive back are implemented correctly on all surfaces.
- **NFR-20.** With three-button navigation and with gesture navigation, home, back and recents behave as the OS intends; the launcher never intercepts system gestures.
- **NFR-21.** A per-OEM notes file documents battery-optimisation steps and known gesture limitations, surfaced in the capability report.

## 14. Optional elevated tier (ELV)

Everything here is an opt-in add-on in the `elevated` flavour. None of it is needed for pre-production, and the core must never call into it without a capability check. Build it after M5, if at all.

| ID | Route | What it could unlock | Cost |
| --- | --- | --- | --- |
| ELV-01 | Accessibility service | Global shortcuts while other apps are focused; open system shade and Recents; toggle split; see the list of open windows for a truer "running" indicator | Store policy scrutiny and a prominent disclosure; users distrust the grant; must be a separate, clearly explained switch |
| ELV-02 | Shizuku (ADB-level, no root) | Launch into a forced windowing mode; move tasks between displays; auto-launch scheduled sessions; read live task list | Needs user setup that resets on reboot on many devices; behaviour differs per Android version |
| ELV-03 | Root | Act as the system Recents provider (for example through QuickSwitch-style modules) for native gesture animations | Small audience, high support load, breaks on OS updates |

**Rules**

- Each elevated feature maps to a core requirement it enhances and falls back to the core behaviour when the route is unavailable.
- The Play build ships the core flavour. The elevated flavour is distributed separately (direct download or an alternative store) unless ELV-01 alone is cleared for Play.
- No elevated route may be used to read content from other apps' windows.

## 15. Spikes, gate and test plan

The ordered work packages that take build 31 to the gate are in the Build instructions tab. This section holds what they are measured against: the platform spikes, the pre-production gate and the test plan.

### Capability spikes

The coding agent cannot run a device, so each spike is built as a button in the Lab page (CFG-17) that performs the check on the phone and shows a result to copy back. Results go in `docs/capabilities.md`. Two more checks ride along with these: whether pointer hover events reach the home activity at the screen corners (INP-16), and whether controller input reaches it (INP-20).

| Spike | Question | Decides |
| --- | --- | --- |
| SPK-01 | When a display connects, does the default home app get a home surface there, on each reference device? | Shape of EXT-02 |
| SPK-02 | What does an adjacent launch from the home app do on each Android version: enter split, replace, or nothing? | WSP-03, WSP-05 |
| SPK-03 | Where are launch bounds honoured (internal display, tablet desktop mode, external display)? | WSP-04, HOM-13 |
| SPK-04 | Is there a stable public way to open the system notification shade? | STR-12 |
| SPK-05 | Do drops from other apps reach a home activity on each version? | INP-11 |
| SPK-06 | Which reference OEMs restrict gestures or kill the launcher in the background? | NFR-20, NFR-21 |
| SPK-07 | Is on-device speech recognition present on each device? | INP-13 |

**Pre-production gate**

- [ ] Every M and S requirement passes on all reference devices
- [ ] All NFR budgets met in CI on the mid-range device
- [ ] 14 days as the daily-driver launcher on two devices with no crash on start and no forced fallback to the stock launcher
- [ ] Build 31 settings migrate to the new store without loss
- [ ] Core flavour contains no hidden-API calls, no accessibility service, no network permission
- [ ] Capability report is accurate on every reference device

### Test plan

| Layer | Covers | Tooling |
| --- | --- | --- |
| Unit | Ranking, launch-request resolution, schema migrations, shortcut conflicts | JVM tests |
| Contract | Each home surface against the surface contract; each palette provider against the provider interface | Shared test suites run per implementation |
| UI | Acceptance criteria by requirement ID, in touch mode and in keyboard mode | Compose UI tests named by ID (`PAL_07_keyboardOperation`) |
| Device | Display hot-plug, profile toggle, package changes, process death, rotation and fold | Instrumented tests plus a scripted manual checklist for external displays |
| Performance | NFR-01 to NFR-06 | Macrobenchmark with baseline profiles |
| Soak | NFR-09, NFR-10 | 2-hour scripted loop nightly |

Every acceptance criterion has a test whose name starts with its requirement ID, so coverage against this spec can be listed mechanically.

## 16. Open decisions and risks

**Decisions needed from you**

- [ ] Application ID: keep `com.bos.sphere` or change it to match Liftoff. Decide before any public or store build.
- [ ] Globe and ring: retired, or planned later as extra home styles? This spec assumes retired.
- [ ] Final names. Launchpad may read as an app launcher; Liftoff needs a store and trademark collision check.
- [ ] Dock rail off by default on phones, on for keyboard, pointer and large windows: confirm.
- [ ] Confirm minSdk 29. Raising it to 31 or 33 removes compatibility work but drops older devices.
- [ ] Replace the reference device list in section 4 with the devices you own; acceptance depends on it.
- [ ] Decide whether the elevated flavour exists at all for the first public release.
- [ ] Confirm the performance budgets after the first benchmark run.

**Risks**

| Risk | Likelihood | Effect | Mitigation |
| --- | --- | --- | --- |
| Work is reported complete that has never run on a device | High | Defects found late; status cannot be trusted | Three-state status (built, screenshot-tested, device-confirmed); only you set the last; device checklist per work package |
| The launcher is not treated as home on external displays | High | Desktop surface is a closable window, not a true desktop | EXT section already assumes this; SPK-01 confirms per device |
| Adjacent and bounds launches behave differently per version and OEM | High | App groups feel inconsistent | Launch result reporting (WSP-01), per-capability gating, honest notices |
| Signature layouts (XMB, DSi, Orbit, puck) are hard to make keyboard-navigable | Medium | Input parity slips or the layouts get flattened | Focus graph designed per layout in work package 2 before any other input work; Flat is the keyboard default in Radar |
| Moving settings out of SharedPreferences loses a user's setup | Medium | Broken upgrade from build 31 | One-way migration with a snapshot first and a round-trip test from a captured build 31 prefs file |
| `feature-grid` keeps absorbing new work | Medium | Slower builds and tangled dependencies | New capabilities go in the new core modules; splits happen when a work package touches the area |
| OEMs degrade gestures or kill third-party launchers | Medium | Poor first impression on Xiaomi, OnePlus, Samsung | SPK-06, OEM notes, capability report |
| Desktop windowing APIs keep changing across Android 17 quarterly releases | Medium | T3 features regress | Capability registry, T3 behind runtime checks, re-run spikes on each platform release |
| Scope: over 110 requirements for one builder | High | Pre-production slips | Work package 8 is the cut line; any S item may move to post-beta without blocking the gate if you choose |

**Not yet researched**

- Google's developer guidance for the new Android laptop form factor includes a page on keyboard apps and launcher compatibility. It was not reviewed for this draft and may change T3 assumptions.
- Current Play policy wording for accessibility services and for `QUERY_ALL_PACKAGES` should be re-read before M6.

## Sources

- [Desktop windowing, Android Open Source Project](https://source.android.com/docs/core/display/desktop-windowing) (updated 9 Oct 2026)
- [Support connected displays, Android Developers](https://developer.android.com/develop/adaptive-apps/guides/support-connected-displays) (updated 22 Oct 2026)
- [ActivityOptions reference, Android Developers](https://developer.android.com/reference/kotlin/android/app/ActivityOptions.html) (read in part)
- [Gesture navigation issues, Lawnchair docs](https://docs.lawnchair.app/troubleshooting/gesture-navigation-issues)
- [Recent apps screen and gesture navigation issues, Niagara Launcher help](https://help.niagaralauncher.app/article/114-recent-apps-screen-issues)
- [How Taskbar starts apps in freeform windowing mode, utzcoz](https://utzcoz.github.io/2021/09/12/How-Taskbar-start-app-in-freeform-windowing-mode.html)

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
| Radar | Filmstrip, Orbit and Flat layouts; category wheel; apps, contacts, actions, files, web; DuckDuckGo with bangs; long-press preview and pinch text size in typed results | Keyboard operation, per-result action list, settings index, calculator, ranking, streaming providers (PAL-03 to PAL-12); preview and pinch in all layouts (PAL-20) |
| Launchpad | Notifications page beneath home, home floats as a rounded card; grouping, rules, light and dark; slide left = one action, slide right = palette | Device confirmation, screenshot tests, grant flow, keyboard and pointer, system state row (STR section) |
| Ground Control | XMB category bar and list; inline toggles, sliders, segments; preview cards; tactile-kit controls | Finish tactile style, live preview dock, search, capability report, backup pages (CFG section) |
| Backgrounds | 24 solids, 20 gradients, 15 textures, 13 live, Horizon picture, own photo | Option to draw over the system wallpaper (HOM-16) |
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
| Recent and foreground app history | Grant | `UsageStatsManager` (usage access) | Dock shows recent apps; cannot enumerate live tasks or windows |
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
| PAL-06 | M | Web fallback | The last result always hands the query to the default browser or a user-chosen search engine. No query leaves the device before the user selects it |
| PAL-07 | M | Keyboard operation | Up/Down moves selection, Enter runs the primary action, Tab or Right opens the action list for a result, Esc closes. Focus never leaves the palette unexpectedly |
| PAL-08 | M | Result actions | Every app result offers: open, open on another display (T3), open adjacent (where supported), app info, add to dock, add to workspace, uninstall. The same list appears on long-press and right-click |
| PAL-09 | M | Ranking | Ranking blends match quality with local frequency and recency. Selections are learned on device only and can be reset |
| PAL-10 | S | Contacts | With contacts permission, contacts are searchable with call, message and open actions. Without it, the category shows a one-tap enable row and nothing else |
| PAL-11 | S | Files | Media and user-granted folders are indexed incrementally. Results open with the default handler. The index survives reboot and reconciles changes in the background |
| PAL-12 | S | Background search | Slow providers stream results into the open palette without blocking typing or reordering results under the selection. Closing the palette cancels pending work; reopening with the same query restores results |
| PAL-13 | S | Scoped search | Prefixes limit scope (for example ` a  ` apps, ` f  ` files, ` s  ` settings, ` c  ` contacts). Prefixes are listed in the empty state and editable |
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
| PAL-18 | M | Three layouts | Flat, Orbit and Filmstrip stay selectable. All three meet PAL-07. When the active input is keyboard or pointer, results default to Flat unless the user has pinned a layout |
| PAL-19 | M | Category wheel is the scope control | Wheel entries (Everything, The Web, Actions, Apps, Contacts, Files) and PAL-13 prefixes are one setting: changing either updates the other. Left/Right moves the wheel from the keyboard |
| PAL-20 | S | Preview and text size everywhere | Long-press or right-click preview, and pinch or Ctrl+scroll text size, work in all three layouts. Orbit and Filmstrip captions scale with the text size |
| PAL-21 | M | Web search stays private | DuckDuckGo queries and bangs are handed to the browser as a URL only when the user selects the web row. Radar itself makes no network request and fetches no suggestions |
| PAL-22 | S | Sibling-app providers | Radar consumes the provider contract in `docs/SEARCH_PROVIDER.md`. Fylz and Fotoz results appear when those apps are installed and expose it; otherwise no empty category is shown |
| PAL-23 | S | Orbit feel | Spin creep and fling constants are adjustable in the Lab page (CFG-17) so they can be tuned on a device; final values are committed as defaults |

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
| INP-08 | M | Gestures | Swipe, long-press, pinch and double-tap on home are each assignable to a launcher action. Defaults do not conflict with system back or home gestures |
| INP-09 | M | Edit by keyboard and by drag | Items can be moved, resized (widgets) and filed into folders by drag and by keyboard alone |
| INP-10 | S | Remappable shortcuts | Users rebind any registry entry; conflicts are detected; bindings export with the config |
| INP-11 | S | Drag and drop | Drag an app or shortcut from drawer or palette to home, dock or folder. Drop text, links and files from other apps onto the home surface to create shortcuts, where the platform delivers the drop |
| INP-12 | S | Force keyboard open | Option to raise the on-screen keyboard automatically when the palette or drawer opens, set per orientation |
| INP-13 | S | Voice entry | A push-to-talk control feeds on-device speech recognition into the palette. If no on-device recogniser exists, the control is hidden. Recognition must never start without an explicit press |
| INP-14 | C | Global shortcuts outside the launcher | Elevated tier only. See section 14 |

**Constraint to respect.** Meta-key system shortcuts belong to the OS and cannot be overridden. Launcher shortcuts apply only while a launcher surface has focus; the shortcut sheet says so.

### Corners and puck

The corners and the puck are Liftoff's signature touch controls; these requirements give each a pointer and keyboard form.

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| INP-15 | M | Corner assignments | Each of the four corners can be set to an app, shortcut, launcher action, app group or nothing, with an optional separate set per orientation. Holo, Sony quarter-disc and Bubbles styles all honour the same assignments |
| INP-16 | M | Hot corners | With a pointer, resting in a corner reveals its action after a short dwell (default 150 ms; adjustable or off); click activates. Never fires during a drag |
| INP-17 | M | Corners by keyboard | Each corner has a default shortcut and is a node in the focus graph, reachable with arrows from the nearest edge item |
| INP-18 | M | Puck by keyboard and wheel | A shortcut opens the puck. Left/Right or the scroll wheel rotates it, typing a letter jumps to it, Tab cycles A-Z, Category and Recent, Enter launches, Esc closes |
| INP-19 | M | Puck sizing | Ring and hub spacing derive from window size and stay usable from the compact size class up; no overlap or clipped actions at the smallest supported window |
| INP-20 | S | Controller input | A game controller's D-pad, confirm and back buttons drive every surface as arrows, Enter and Esc |

## 8. Home surfaces, drawer and widgets (HOM)

Home is a host that renders the user's items through a selectable home style (Grid, List, XMB or DSi) and always offers the same organisation tools underneath.

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| HOM-01 | M | Selectable resting surface | User picks Grid, List, XMB or DSi, paged or continuous. Switching keeps all items, folders and widgets. Each style meets INP-01 and the frame budget in section 13 |
| HOM-02 | M | Item types | App, shortcut, folder, widget and file/link shortcut can all live on home and in the dock |
| HOM-03 | M | Icon rendering | Adaptive icons render at correct shape and size with no dark tile behind them. Themed (monochrome) icons and icon packs are supported; per-item icon override |
| HOM-04 | M | Folders | Create by dropping one item on another or from the menu; name, reorder, open by tap, click or Enter; searchable by name |
| HOM-05 | M | App drawer | Full list with alphabetical index, type-to-filter, work and personal tabs, user tags and hidden apps |
| HOM-06 | M | Profiles | Work profile apps are badged and can be paused. Private space appears only when unlocked (Android 15+) |
| HOM-07 | M | Widgets | Add from a searchable picker, resize by handles and by keyboard, reconfigure, remove. Widgets restore after reboot and after provider update |
| HOM-08 | M | Widgets mixed with tiles | Widgets occupy the same layout model as app tiles on the grid surface. On List, XMB and DSi, widgets live in a defined widget band or panel, not overlapping tiles |
| HOM-09 | M | Notification badges | Dots with optional count on app items when notification access is granted; long-press or right-click previews notifications |
| HOM-10 | M | Package changes | Install, update, uninstall, disable, archive and suspend are reflected within 1 second without restart; removed items leave no gaps or crashes |
| HOM-11 | M | Adaptive layout | Grid dimensions derive from window size class and user density setting. Rotating, folding, resizing or moving displays reflows without losing positions; each size class keeps its own arrangement |
| HOM-12 | M | Edit mode | Explicit edit mode with undo for the last 20 changes |
| HOM-13 | S | Free-form placement in T3 | On desktop surfaces, items may be placed off-grid with snap guides; widgets may overlap empty space but not each other |
| HOM-14 | S | Widget stacks | Several widgets share one slot, switched by swipe, scroll wheel or keys |
| HOM-15 | S | Files and recent documents | A home panel or widget lists recent files from granted sources and pinned folders |
| HOM-16 | S | Wallpaper integration | The built-in backgrounds stay. An added option draws home over the system wallpaper, forwards scroll offsets and derives accent colours from it |
| HOM-17 | C | Usage widget | Time-split widget from usage stats |
| HOM-18 | C | Timeline view | Chronological view of app opens with scheduled launches (see WSP-11) |

### Home frame as built

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| HOM-19 | M | Uniform frame | The margin band (about 15% of the short side) keeps the app area in the same region in both orientations for every home style. Corners, clock, page dots and dock rail sit inside the band and never overlap the app area |
| HOM-20 | M | Clock band fit | With the clock at the bottom, the weather line and page dots fit within the band at every supported window size, by scaling or dropping the weather line first. Closes the cramped layout noted in the handoff |
| HOM-21 | M | Long-press quick sheet | The existing quick sheet is also opened by right-click on empty home and by the Menu key, and lists the same entries (INP-03) |

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
| WSP-05 | M | App groups | A saved group of 2 to 4 apps with a layout preset launches with one action. Appears as a home item, dock item and palette result |
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
| STR-01 | M | Reveal and peek | Dragging home from its left edge slides the card aside to show Launchpad; a partial drag peeks and springs back. Also opened by a visible handle, a click, and a shortcut. In landscape the home card stays partly visible; in portrait Launchpad takes the screen |
| STR-02 | M | Content | Notifications grouped by app and conversation, newest first, with the notification's own actions and inline reply passed through. Dismissing here dismisses in the system |
| STR-03 | M | Row gestures with equivalents | Slide left runs the one primary action (default dismiss; set per rule). Slide right opens the action palette (snooze, open, mute app, make a rule). Both are also buttons shown on hover and focus, entries in the context menu, and keys (Delete, Menu) |
| STR-04 | M | Rules | Existing Launchpad rules persist in the state store and export with the config. Per app: show immediately, batch into a digest at chosen times, or silent in Launchpad only. Rules affect Liftoff's surfaces only, and Launchpad says so |
| STR-05 | M | Grant flow | Without notification access, Launchpad and the media strip each show a one-screen explanation and an enable button on first use; nothing appears broken or empty without explanation |
| STR-06 | M | Media | The media strip shows artwork, title and transport controls for any active media session, and feeds the dock media item (DCK-08) |
| STR-07 | M | Test coverage | Launchpad has screenshot tests in both orientations for: no grant, empty, grouped, digest, light and dark; plus device checklist entries for the slide, peek and row gestures |
| STR-08 | M | Keyboard and pointer | Launchpad meets INP-01 to INP-03; arrows move between groups and rows; Esc returns to home with focus where it was |
| STR-09 | S | Waiting indicator | A slim indicator on the left edge of the home card shows the waiting count and expands briefly for a new item, subject to the app's rule. Never steals focus |
| STR-10 | S | System state row | Time, battery, connectivity and sound state at the top of Launchpad; tapping one opens the matching system panel. Tiles display state and do not claim to toggle what Android blocks |
| STR-11 | C | Digest widget | Batched notifications are also available as a home widget |
| STR-12 | C | Open system shade | A control opens the real system shade. Elevated tier only unless SPK-04 finds a stable public route |

System heads-up pop-ups are drawn by System UI and cannot be suppressed by a launcher. Reducing those is a matter of the user's notification channel settings; the centre links to them per app.

## 12. Ground Control, configuration and onboarding (CFG)

The whole setup is one portable file, settings are a real screen rather than a pop-up, and onboarding asks for each grant only at the moment its feature is first used.

| ID | P | Requirement | Acceptance criteria |
| --- | --- | --- | --- |
| CFG-01 | M | Export | One action writes layout, workspaces, sessions, shortcuts, palette aliases and settings to a single versioned file at a user-chosen location. No personal content (notifications, usage history, contacts) is included |
| CFG-02 | M | Import | Importing a file restores the setup on a clean install. Missing apps become placeholders that resolve when the app is installed. Widgets re-bind with one consent step each |
| CFG-03 | M | Schema versioning | Files from every earlier schema version import through tested migrations. Unknown fields are preserved on round trip |
| CFG-04 | M | Automatic local backup | Rolling snapshots (last 5) are kept in app storage and before every import or destructive edit; one-tap restore |
| CFG-05 | M | Settings screen | Ground Control keeps its cross-media-bar layout: categories across, settings down, inline controls. Fully operable by keyboard. Every setting is also a Radar result |
| CFG-06 | M | Onboarding | First run: set as home, choose home style, optional import. Nothing else is requested up front |
| CFG-07 | M | Just-in-time grants | Notification access, usage access, contacts and folder access are requested when the user first touches the feature, each with a one-screen explanation of what is read and that it stays on device |
| CFG-08 | M | Capability report | A settings page lists every capability from section 3 with its current state on this device and the reason for any that are off |
| CFG-09 | M | Safe mode | If the launcher crashes twice within 30 seconds of start, it boots into a minimal grid surface with widgets disabled and offers restore from the last snapshot |
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
| CFG-17 | M | Lab page (debug builds) | A Ground Control category that runs the capability spikes on the device, shows live input mode and display list, exposes feel tunables (PAL-23), and copies results as text |
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

- **NFR-09.** Crash-free sessions at or above 99.8% across the beta cohort; zero known crash on start.
- **NFR-10.** No ANR in the launcher process during a 2-hour scripted soak.
- **NFR-11.** Full state restore after process death, reboot, package update, profile toggle and display hot-plug.
- **NFR-12.** No main-thread disk or binder calls on the home render path (StrictMode clean in debug).

### Accessibility

- **NFR-13.** TalkBack can reach and operate every action; XMB, DSi, the puck and Orbit expose a linear accessible order.
- **NFR-14.** Works at 200% font scale and with reduced motion (surface morphs become cross-fades).
- **NFR-15.** Contrast meets WCAG AA over any wallpaper, using a scrim where needed.

### Privacy and permissions

- **NFR-16.** The core flavour declares no `INTERNET` permission. Web results hand off to the browser.
- **NFR-17.** No analytics, no crash upload. Crash reports are saved locally and shared only by explicit user action.
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

# Liftoff Launcher: product half of the settings handoff

Purpose: give the product owner everything needed to decide the settings information architecture (IA), then hand the
decisions back so implementation can be done once and done exactly. Sections: 1 Product on one page, 2 Personas,
3 User journeys, 4 IA problems and open decisions, 5 Acceptance checklist.

Sources read: `docs/Spec.md` (CFG-05/13/14/18/21, HOM-01..23, STR-01..14, WSP, DCK, PAL), `docs/decisions/*`
(settingsOneUI, settingsPages, settingsPopups, popupsHidden, glanceFixed, glanceClock, homeSheet, dockThemes,
workProfile, editIntegrate, commsRename and others), `docs/research/home-editing.md`, `docs/status/*`, `README.md`,
and the settings code (`app/src/main/kotlin/com/bos/sphere/ui/SettingsCategories.kt`, `BackupPane.kt`,
`DesktopSettingsPane.kt`, `PuckActionsPane.kt`, `DockThemesSettings.kt`, and `feature-grid/.../SettingsOneUi*.kt`).
Owner quotes are verbatim from the session transcript; where a quote is the owner's words as preserved in a decision
note or a session summary rather than the live transcript, it is marked (note).

Honesty rule used throughout: "built" means compiled and tested on the JVM or Robolectric; nothing in this repo is
device-verified unless a doc says so. Anything the owner asked for in the last hour of the session (clock move/edit,
visual settings, the renames, the font, workspaces up front, actions as bubbles) is NOT in the repository's settings
today; where an agent was started it is marked "in flight, not merged as far as this read can tell".

---------------------------------------------------------------------------------------------------------------------

## 1. Product on one page

Liftoff is an Android home app (Kotlin, Compose, no AOSP fork, no root) for people who want a calm phone, a desktop-class
input model (keyboard, pointer, controller) and one search that reaches everything. Landscape is the reference
orientation, portrait is a first-class adaptation (Spec section 4). Local and sovereign: no account, no telemetry, an
explicit privacy switch for the single network use (web suggestions).

### 1.1 Surfaces

| Surface | What it is today | How you get there | Settings that shape it |
|---|---|---|---|
| Glance (the calm home) | First stop of the home pager. Near-empty: big wallpaper clock (5 styles + Off), music line at top with pop-in controls notch, four hotspots, surface indicator at the bottom, over a two-slot signal wallpaper. On by default. | Home key; swipe from the pages | Settings > Home screen: Calm home screen (Glance), Clock style/format/date/colour/size, Show music, Hotspots, Controls hide after, Surface indicator, Glance wallpaper |
| Home pages | After Glance. Pages setting: Off, Paginated (swiped pages), Continuous (one sideways scroll filled column by column, Windows-8-start style). Each page is a Grid, Circle or Coverflow with its own density. Explicit: the user adds one empty page at a time. | Swipe from Glance; Settings > Home screen > Pages | Home screen (Pages, Order, Style, density, spacing, margins, labels, badges), per-page "Page N" category |
| Edit mode | The page itself shrinks to 0.88 with a scrim and real grid dots; one selected item, handles for widgets, action bar + item sheet, one Done, hint shown 3 times; page editor has a page strip and Apps / Widgets / Shortcuts / Folders tabs. | Long press an item or empty page; Settings > Home screen > Edit home / Widgets, folders and icons | Edit chrome store (`edit_chrome`); no user-facing settings |
| Radar (search) | Full-screen search: Filmstrip (default), Orbit (circles of apps, rings by use), Flat list. Apps, settings, files, contacts, actions (as bubbles), calculator, web (DuckDuckGo default), Play Store rows. Query field at the top, mic bottom centre. | Typing on home, corner/gesture action, keyboard shortcut | Settings > Radar |
| App drawer disc | The only drawer. A looped alphabet/category/recent wheel around a corner, black hub = Back, white segmented rotatable ring (6 to 8 segments) of user-chosen shortcuts; Work label and Pause pill when a work profile exists. | Corner/gesture "all apps" | Settings > App drawer |
| Side panels | Left: Comms (notifications hub: All / Unread / Read, Files, Usage + Timeline, groups and rules; the home card shrinks and slides) or one full-screen widget, or Off. Right: Recents panel, dock rail or nothing. The side the panels sit on follows Order. | Swipe from the edge, indicator glyphs, Ctrl+R | Settings > Home screen (Left page, Right side, Order), Settings > Comms |
| Dock / Desktop | Optional dock (Standard / Transparent / 3D), workspaces, app groups and sessions, external-display desktop surface. Off unless enabled (T1). | Dock long-press menu; Settings > Desktop & dock | Desktop & dock; Home screen > Dock style |
| Corners / hotspots / gestures | Four physical-corner slots (Holo, Sony quarter discs, Bubbles), shown as hotspots on Glance and every page via one fixed chrome layer; gesture bindings; optional motion gestures. | Always on screen | Corners, Gestures (+ Motion), Home screen > Hotspots |
| Home quick sheet | Black popup on long press of empty space: Pages chips, Order chips, Hotspots chips, page tools, Wallpaper row, Ground Control button. | Long press empty home | Not a settings page; links to them |
| Settings | One UI style: large collapsing title, search field, rounded cards of categories, a page per category, radio pages for choices, floating Preview window. Two-pane on wide landscape. | Home sheet > Ground Control; Radar settings results | CFG-21 |

### 1.2 Design principles the owner has stated (quote, then the rule)

1. Visual first. "I want the settings options to be visual as far as possible. The apps and widgets selection is impossible to find unless the screen is empty. The widget selector is two layers down." Rule: a choice among looks is a rail of preview cards, not a text row; the most common job (add an app or widget) is reachable in one tap from where the user already is.
2. Pages, not popups, when the options are not already outside. "Use full page menus where there are popups, not when the Options are outside already! Why is the left page options outside as well as inside?" Rule (settingsPages): one presentation per option set, never two. Rail = cards inline, no chevron, no second page. Plain choice = value in grey + chevron + full radio page. Dialogs only for confirmations and short text entry. Menus stay menus.
3. Follow Samsung One UI. "I want to make the settings area simpler, more traditional where the full list is laid out up-front. Try to emulate the samsung One UI." and "The popups look ugly af in setting. Please follow oneUI better."
4. Primary action on top as a coloured box, secondary below as plain text. "And never show the options side by side, top and bottom. Primary on top, coloured box, secondary below as just text". Note: this contradicts the One UI dialog spec in CFG-21 (a row of flat accent text buttons cut by hairlines, no filled buttons). Needs an owner ruling (problem 21).
5. Smoky popovers, not boxes. "I dont want these boxes, I want a gradient background, radial gradient from black to transparent, like a smoky effect" (said of the notification preview card and its options menu; built in build 71 for those two popups only).
6. Keep only the newest specified option. (note, recorded in session summaries) "We have till now added a lot of things and then whittled them down, I want to take a slightly different approach- for the things I am now specifying, only keep the options I am proposing, remove the others we had before." Rule: a new owner spec replaces, it does not add beside the old. Consequences already applied: no flat drawer ("The app drawer will be the disc type only. There won't be a drawer. Remove that option."), no landing selector ("Home landing is glance only. User can at home turn off the calm (empty screen)"), no clock position/layout, no weather on Glance, one wallpaper picker.
7. Honest verification. Standing instruction recorded across the session (note): never claim device verification; three states built / screenshot-tested / device-confirmed, only the owner sets the last (Spec section 16). Settings docs must say "device check" for what Robolectric cannot prove.
8. Optional dock. The dock stays optional and is never turned on as a side effect (docs/decisions/dockOptional.md).
9. Calm. Spec principle 6: fewer interruptions than stock. Glance is empty on purpose.
10. Input parity. Every setting reachable by touch and by keyboard/pointer/controller (CFG-13).
11. Structure the owner wants in titles (latest, unimplemented). "The title of the settings sections need to be vertically AND horizontally centred. It needs to be in caps. The font should be space grotesk." and "There is no font selection for liftoff rn, but there should be."
12. Names must describe what is inside. "Why Page 1? Shouldnt it be pages or something of the sort? Why comms? it needs a better name- comms is the name of the section when comms are configured to it."

### 1.3 Vocabulary: preferred and deprecated

| Thing | Preferred (UI, docs) | Deprecated or collides | Notes |
|---|---|---|---|
| The product | Liftoff (Launcher) | Sphere Launcher, BOS launcher, Hyle | Package `com.bos.sphere` stays (CFG-20). Spec says final name needs trademark check. |
| The settings app | "Settings" (the root title in the UI) | "Ground Control", "cross-media-bar", "XMB settings", "tabs" | Spec/README/code comments still say Ground Control. OPEN: pick one (problem 20). |
| Calm home | Glance (calm home screen) | Minimal, landing, home landing, "Recent apps landing", "All apps landing", deck | `Calm home screen (Glance)` is the toggle row title. |
| After Glance | Pages (Off / Paginated / Continuous) | "scroll" (use Continuous), "Pages home", "Selected apps" | Three unrelated things are all labelled "Pages" today (problem 1). |
| Notifications hub | Comms; Comms menu; Comms size | Launchpad, Notification menu, hub, pull-down hub | Owner now wants a better name (problem 7). Code/pref keys keep `launchpad`. A hidden "launchpad" search alias exists. |
| Right panel | Recents panel | Launchpad's mirror, RHS | Setting is called "Right side" (Recents panel / Dock rail / Off). |
| App drawer | App drawer disc, disc; segments; Drawer shortcuts | drawer, flat drawer, switcher Compact/Expanded, arc drawer, puck, wheel, globe | UI still says "Wheel groups", "Ring segments", "Shortcut ring" in settings text (mixed). The Spec uses "puck". |
| Search | Radar; Filmstrip / Orbit / Flat | command palette, palette, typed prefix, !bang, bottom search field | Radar settings category was titled "Search" in code comments only. |
| Corner shortcuts | Corners (slots) and Hotspots (the on-screen presence on Glance and pages) | hot corners (that is the pointer-dwell feature), complications, palettes | Collision: "Hot corners with a mouse" vs "Hotspots". |
| Wallpaper | Wallpaper (Solid, Gradient, Signal, System live, Picture); Glance wallpaper, Pages wallpaper | Background, background gallery, landing background | `Radar background` (Dimmed wallpaper / True black) and `Settings backdrop` are different things named "background/backdrop". |
| Signal effects | Indicator: Calm style (Off / Regular / Edge dots / Corner bloom / Bottom glow / Edge beam) and Also show regular battery and signal | network dots, spider-sense (owner's colloquial), glow lines | Collides with "Surface indicator" (Glance bottom row) and the page indicator. |
| Quick popups | Sound and Vibration (volume knob, not wheel or brightness), Connectivity (was "Internet") | Brightness sheet, Internet sheet | Owner: "What you are currently calling Brigthness is actually volume" and "Volume knob, rather than wheel". |
| Setups | Workspace (named home setup), App group (2 to 4 apps launched together), Session (group + display targets) | profile (reserved for work profile and display profile) | Owner: "Workspaces are not intuitive to configure." |
| Work profile | Work apps, Work label, Pause work apps | Private space only when unlocked | Lives on the disc, not in settings. |
| Notification history | Comms: All / Unread / Read; Files; Usage + Timeline | tabs Files/Usage/Timeline as separate tabs; Read history | "Dismiss" in Comms means mark as read (owner). |
| Theme file | Theme pack (`.liftofftheme`), Themes (import, export, reset) | theme store (hook only, no address) | Owner wants a link to the asystemofcells site (hook exists, address null). |
| Edit | Edit home; page editor; Done | edit card, carousel, Home items dialog | One Done. |

---------------------------------------------------------------------------------------------------------------------

## 2. Personas

Derived from Spec section 4 (tiers T1 handheld, T2 keyboard/large, T3 desktop), the spec's principles and the owner's
statements. They are the test of every IA decision in section 4. Names are descriptive roles, not real people.

### P1. The Tinkerer (the owner archetype)
- Goals: make the phone exactly theirs: Glance with a chosen clock, the disc with their four shortcuts, Radar shaped how they like, theme packs, workspaces for Work and Personal. Wants to try, compare and revert.
- Devices and inputs: one flagship phone in landscape-first use, sometimes docked to a monitor; touch, occasionally a Bluetooth keyboard; screen recordings and mock-ups are how they give feedback.
- Touches in settings: nearly everything; weekly. Look (theme, icon shape, dock style, indicator effects, wallpaper kinds incl. GIF/video), Home screen, App drawer, Radar, Corners, Comms, Backup (export before experiments).
- Frustrations: settings that are "two layers down"; options in two places ("Why is the left page options outside as well as inside?"); old options left beside new ones; names that do not match the thing; no preview of the effect; features reported done that are not on the device.
- Success: any setting found in two taps or by search; every look choice is a picture; changing it shows on the live preview; a full export restores the whole look on a new phone.

### P2. The Minimalist (Glance only)
- Goals: a calm phone. Only the clock, the music line, four (or fewer) hotspots, a quiet wallpaper. Everything else by search.
- Devices and inputs: any phone, one hand, touch only; types to search.
- Touches in settings: a handful of times: Glance on, Pages Off, Clock style/size/colour, Hotspots (Four/Two/One/None), wallpaper, Radar layout, Hidden apps.
- Frustrations: clutter on Glance (weather, date, strips were removed at the owner's request); having to understand Pages, Order, Recents panel to be left alone; an indicator or dock turning on by itself.
- Success: first run lands on Glance with defaults that already look right; the whole config they care about fits on one short page; nothing appears unless asked for.

### P3. The Keyboard and Controller User (desktop, T2/T3)
- Goals: use the launcher like a desktop: shortcuts, dock numbers 1..9, hot corners with a mouse, Ctrl+R recents, Ctrl +/- Comms size, controller navigation; external display desktop with per-display arrangement.
- Devices and inputs: tablet or foldable, phone on a monitor/DeX, hardware keyboard, pointer, gamepad.
- Touches in settings: Desktop & dock (dock mode, position, apps, display connect/unplug behaviour), Keyboard shortcuts row, Always-on keyboard, "Keyboard opens with Radar and drawer", Hot corners with a mouse (+ delay), Radar layout kept with a keyboard, Workspaces and App groups.
- Frustrations: settings reachable only by touch; shortcut conflicts; a keyboard row in Home screen next to icon spacing; no obvious home for input settings.
- Success: Tab, arrows, Enter, Esc, Back walk the whole settings tree; shortcut rebinding is in one "Input" place; window resize reflows without losing state.

### P4. The Accessibility User
- Goals: legible, predictable, screen-reader and switch friendly; large text; reduced motion; high contrast; no hidden gestures required.
- Devices and inputs: TalkBack, font scale up to 2.0, reduced motion/animations off, sometimes external keyboard; RTL languages (ar) or hi/ja/zh.
- Touches in settings: Language, Text size and density (Look > Wallpaper palette, density and text size), Font (new), Comms size (75 to 125 percent), Icon size, Label size, Interface sounds and volume, Order (Right to left), Reduced-motion related toggles ("Keep moving" in Radar).
- Frustrations: titles clipped at font scale 2, controls under 48 dp, rows whose state is not read out, a floating preview window that eats touch, gestures as the only route.
- Success: every row has name, value and state in its description; 48 dp targets; wrap not clip; mirrored in RTL; every gesture has a visible alternative; a screen reader user can complete every journey in section 3.

### P5. The Privacy-conscious User
- Goals: nothing leaves the device unless they chose it; know exactly what each access grants.
- Devices and inputs: any; reads permission screens.
- Touches in settings: "Web suggestions" (the only network use; sends typed text to the chosen engine and Play Store), Web results, Web engine, Notification access, Usage access (Recent apps "Include apps opened from elsewhere"), Contacts and Files search, Search folders, Search history and aliases ("Forget"), Delete history, Clear all notifications, Hidden apps, Backup (no personal content in an export: notifications and usage history are excluded, CFG-01).
- Frustrations: access rows scattered (System, Comms "Access Liftoff asks for", Recents toggle, Radar toggles); not knowing what a switch sends.
- Success: one "Privacy and access" view listing every grant with state and the off switch; each network-touching row says what is sent and to whom; web suggestions default is the owner's call (currently ON).

### P6. The Work-Profile User
- Goals: personal and work apps both launchable, work apps pausable after hours, work notifications handled by their own rules, a separate home setup per context.
- Devices and inputs: managed or dual-profile phone; touch; quick mode switching.
- Touches in settings: Workspaces (Work / Personal), App groups, Comms groups and rules (Priority breaks through Do Not Disturb inside Liftoff), Notification badges ("work apps use their own profile's notifications"), Hidden apps, Open with.
- Frustrations: pause lives only on the disc (Work label + pill), not in settings; workspaces are buried; calm-centre app filter is per workspace but app groups are shared (confusing).
- Success: Work/Personal switch in one gesture or from settings front page; pause/resume discoverable from Settings; badges and Comms rules distinguish profiles.

### P7. The Helper / Shared-Device User (low-tech family member, or a person setting up for one)
- Goals: a very simple phone for someone else (a parent, a child): few big icons, no accidental long-press edits, easy recovery.
- Devices and inputs: shared or low-tech user's phone; touch; large text; set up once by the helper, then used for months.
- Touches in settings: Style, density (Default density, Icon size, Label size), Pages (one page), Hidden apps, Hotspots (None or One), Left page Off, Right side Off, Always-on keyboard, Language, Backup (export on the helper's phone, import on theirs), Safe mode / crash recovery.
- Frustrations: no "lock layout" or simple-mode preset; edits are one long press away; recovery screens full of jargon.
- Success: one "Simple" preset or a clear short list; a backup file from the helper's phone sets everything; if it breaks, the recovery screen speaks plain language (it does: CFG-09) and the helper can restore the last snapshot.

---------------------------------------------------------------------------------------------------------------------

## 3. User journeys

Paths are the current UI path: "Settings > Category > Row". Settings root is reached from: long press empty home >
Home sheet > Ground Control button (opens the root), a Radar settings result (opens the exact row), or a deep link
(Edit home opens Home screen; the drawer's empty section opens App drawer). Taps are counted from the Home sheet.
"Friction" is today's state from the code and the owner's complaints; "Ideal" is the target the IA should deliver.

### J1. First run and permissions
- Trigger: fresh install, first Home press.
- Steps today: install > press Home > Android asks which home app > Liftoff via `RoleManager.ROLE_HOME` (fallback: home settings) > CFG-06 onboarding (set as home, choose home style, optional import) > lands on Glance, Pages Off, Dock off. Fresh state per HOM-01: no apps placed; a quiet hint "Long press to add apps" opens edit mode. Permissions come just in time (CFG-07), each with a one-screen explanation: notification access (opening Comms), usage access (Recent apps "Include apps opened from elsewhere", Dock Recent), contacts and folder access (Radar), microphone (voice entry), work-profile quiet mode (needs default home).
- Settings/config touched: default-home role; `homeStyle`, import; later: notification listener, usage access.
- Friction: "Set as default home" and "Notification access" and "Do Not Disturb access" are rows in System; "Access Liftoff asks for" is another page under Comms; the Recent apps usage toggle is in Home screen; no single place that shows all grants. Spec says onboarding asks the home style, but Glance now has no style choice (landing removed); verify what the first-run screen actually asks (device check).
- Ideal: first run = 3 screens max (set default, optional import, done); a "Permissions and access" page in Settings lists every grant with state and a button; every permission row elsewhere deep-links to it.

### J2. Make it look like mine (theme, font, icon shape, wallpaper)
- Trigger: "this is not my phone yet".
- Steps today: Settings > Look > Light / dark (rail of previews) > Accent (colour picker page) > Themes (Import / Export / Reset a `.liftofftheme` pack) > Wallpaper palette, density and text size (palette source: From my wallpaper; Density; Text size) > Icon shape (rail) > Icon colour > Icon plate > Settings backdrop (+ Backdrop dimming). Then separately Settings > Home screen > Glance wallpaper / Pages wallpaper (Solid, Gradient, Signal, System live, Picture; GIF/WebP/video with Still / Play once / Loop, power warning dialog) and Dock style (Standard / Transparent / 3D). Then Settings > Look > Indicator (Calm effect, Alert effect, Show, Glow intensity/speed/preview/colours). Per-app icon colour: long press an app > Icon colour.
- Settings/config touched: `themeMode`, `accentColor`, `iconShape`, `iconTint`, `iconPlate`, `settingsBackdrop`, `settingsDim`, `ThemingStore` (palette source, density, type scale), wallpaper prefs per surface, `DockSettings.style` (workspace file), theme packs.
- Friction: wallpaper is not under Look; dock style is under Home screen while the Dock is under Desktop & dock; "Look" holds the signal Indicator which is a wallpaper effect; no font choice at all (`Type.kt` uses `FontFamily.Default`); theme pack covers what? (undocumented to the user); text size is hidden in a pane titled "Wallpaper palette, density and text size" (three unrelated things in one title).
- Ideal: one "Look" group in this order: Theme (mode, accent, palette, themes), Font, Icons, Wallpaper (all surfaces), Dock style, Signal effects, Settings screen. A live preview always visible. A theme pack exports everything in Look (state which fields). Font picker with previews, including Space Grotesk.

### J3. Set up the home (pages, hotspots, corners, gestures)
- Trigger: "I want pages after the calm screen and my four corners".
- Steps today: Settings > Home screen > Pages (rail: Off / Paginated / Continuous) > (Paginated) Add page > Order (Left to right / Right to left) > Hotspots (Four / Two (bottom) / One / None) and per-corner switches ("Top left hotspot" etc.) > Settings > Corners: Corner style (Holo / Sony quarter discs / Bubbles), Corner icon size, Icon type, Follow device rotation, Separate corners for landscape, Hot corners with a mouse (+ Hot corner delay), then Top left, Top right, Bottom left, Bottom right (each opens an editor page: action, app, palette, widget, complication) > Settings > Gestures: Pinch changes grid density, per-gesture bindings (pinch in/out, swipes across the paging axis, double tap), "On this home" explanation line > Settings > Gestures > Motion: Motion gestures (shake, flick twice...), When home isn't showing, Motion sensitivity.
- Settings/config touched: `homePages`, `homeOrder`, `glanceHotspots`, `cornerStyle`, `cornerIconSize`, `cornerIconMode`, `cornersFollowDevice`, `cornersPerOrientation`, `hotCornersOn`, `hotCornerDwellMs`, corner slots, gesture map, `motionEnabled`, `motionSensitivity`.
- Friction: hotspot presence (Home screen, under the "Glance" section) and what each hotspot runs (Corners) are on different pages; the hotspot hint says "What each runs is set under Corners". "Hot corners with a mouse" sounds like hotspots. Motion is a section of Gestures under a title row also called "Motion gestures". The owner stated the hotspots are fixed on every page and follow the corner style (glanceFixed): settings do not say so.
- Ideal: one "Corners and gestures" page (Controls group): corner slots front and centre as a picture of the four corners (tap a corner to set it), presence (Four/Two/One/None) beside it, style rail on top, gestures below, motion last, pointer hot corners under "Pointer".

### J4. Edit the home (add app / widget / folder, resize, page editor)
- Trigger: "put WhatsApp on page 1 and a calendar widget".
- Steps today: long press an item or an empty page (or Settings > Home screen > Edit home) > the page shrinks to 0.88, scrim, grid dots, hint (first 3 times) > Add via the tray or the page editor: horizontal page strip (portrait) with Apps / Widgets / Shortcuts / Folders tabs, apps tap-to-place, widgets via a searchable picker > select an item: outline, handles (corners and edges within the provider's resize limits), action bar and sheet (Resize presets stop at a neighbour with a buzz and "Can't resize: another item is in the way") > drag: icons push aside, widgets block, icon on icon makes a folder > Done (one Done; Cancel restores widget sizes, folder contents and names, clock tile settings). Per page: long press empty > Settings "Page N" category (Layout rail Grid/Circle/Coverflow, density follows home default, columns/rows, rings, Clock tile on this page, Add a page after this, Delete this page). Settings route to the picker: Settings > Home screen > Widgets, folders and icons (4th row), whose hint is identical to Edit home's.
- Settings/config touched: page layouts, `HomePage.keys`, `EditChromeStore`, widgets binding, `pageMode` (App drawer > Pages: One page / Selected).
- Friction: owner: "The apps and widgets selection is impossible to find unless the screen is empty. The widget selector is two layers down." and "the handles and editing experience is bonkers" (research done, waves 45 to 48 built, none device-verified). Two near-duplicate rows (Edit home, Widgets, folders and icons) with the same hint; the per-page category is named "Page 1" and exists only on the paged home; the clock tile has its own settings separate from the Glance clock.
- Ideal: the add-apps-and-widgets picker is one tap from the Home sheet and from the top of Settings > Home, shown as visual tiles; per-page settings appear inside the page editor (tap a page, its settings under it), not as a mystery "Page N" category.

### J5. Search (Radar, web, apps, actions)
- Trigger: start typing on home, or a corner/gesture/shortcut.
- Steps today: type > Radar opens (layout Filmstrip default; Orbit rings by use frequency; Flat for keyboard users; "Keep this layout with a keyboard" else it falls to Flat) > results: apps (with nested shortcuts), settings, files, contacts, actions (like "flashlight", shown up front as action bubbles per the owner's latest ask: in flight, not merged as far as this read can tell), calculator/unit answers, web row last, Play Store rows > Enter on the Web scope opens the engine (DuckDuckGo default; owner: "Default cant be Google. Default has to be duckduckgo") > mic = voice entry. Settings: Settings > Radar: Radar layout (rail), Keep this layout with a keyboard, Radar background (Dimmed wallpaper / True black), Keep moving, Web engine, Web results, Web suggestions, Actions, Files, Deep results, Contacts, Search folders (pane), Search providers from other apps (pane), Search history and aliases (pane). Home screen holds the keyboard rows: Always-on keyboard, "Keyboard opens with Radar and drawer" portrait and landscape, Keyboard shortcuts.
- Settings/config touched: `searchLayout`, `searchLayoutPinned`, `radarBackground`, `searchMotion`, `webEngine`, `searchWeb`, `webSuggestions`, `searchActions`, `searchFiles`, `searchDeep`, `searchContacts`, folder grants, provider list, history/aliases.
- Friction: the keyboard behaviour of Radar is in Home screen, not Radar; "Web results" and "Web suggestions" and "Web engine" are separate rows with overlapping hints; Radar's own visual choice (Radar background) is a text choice with no cards; Enter-opens-Play-Store was a reported bug, fixed (wave 43).
- Ideal: one Radar page, sectioned Layout (rail), Appearance, Input (keyboard rows move here), Sources (switches with a one-line "what is searched / what is sent"), Web (engine + suggestions), Privacy (history, aliases). A tester can read the page top to bottom and know what Radar will do.

### J6. Comms (notifications, mark read, timeline, files, widgets in the side panel)
- Trigger: a notification arrives; or the user swipes the home card aside.
- Steps today: swipe from the left edge, tap the Comms glyph in the surface indicator or Ctrl+... > Comms list (All / Unread / Read; pinch filters Unread - All - Read; slide left = primary action, default dismiss which only marks read; slide right = action palette; Files view (list or grid of real previews), Usage and Timeline combined view) > tap a notification: opens the app AND the specific notification (deep link) and Back returns to it. Settings > Comms: Left page (rail: Off / Comms / A widget) > Choose widget > Comms theme (Dark / Light) > Comms size (stepper 75 to 125 percent in 5s) > Default view > Clear from the shade when dismissed in Comms > Read history > Groups and rules > Unread strip > Comms gestures (pane) > Grouping & rules (pane) > Access Liftoff asks for (pane) > Mark all as read / Clear all notifications / Delete history (each confirms). Groups and rules manager is also reachable from inside Comms.
- Settings/config touched: `leftPage`, `leftWidgetId`, `launchpadTheme`, `CommsScaleSettings`, `CommsOptions` (default view, clear from shade), `launchpadRail`, group/rule store, `NotificationHistory` (5000 entries).
- Friction: "Left page" appears in Home screen's neighbourhood conceptually but lives in Comms; the owner's naming complaint ("Why comms? it needs a better name- comms is the name of the section when comms are configured to it. What if its a widget instead?"); the ask for a fifth section for widgets ("I would also like to be able to add widgets to it (as a fifth section) where I can add widgets instead of on the right side, if I so choose"); the Comms category mixes panel-level settings (which panel, which widget) with notification-level settings (rules, history). Notification Access appears in System and Comms.
- Ideal: a Side panels group: Left panel (what is in it: Notifications / Widget / Off, with widgets as a section choice) and Right panel (Recents / Dock rail / Off), each with a picture; notification settings under the Notifications panel only. Name decided by the owner (problem 7).

### J7. Drawer disc use and customisation
- Trigger: "open all apps".
- Steps today: corner or gesture "apps" > the disc opens around the corner: curved labels A-Z, Category, Recent (and Work when a work profile exists) that loop (Z then # then A); drag to turn; tap a letter to fan its apps (plain launcher icons, not squished); black hub = Back; white ring segmented 6 to 8, rotatable, each segment a shortcut/app/empty. Settings > App drawer: Ring segments (6, 7, 8) > Drawer shortcuts (pane; each slot: Empty / App... / an action; Reset to default; defaults flashlight, alarm clock, notifications, Do not disturb) > Show labels under glyphs > Wheel groups (A-Z, category, recent) > Pages (One page / Selected, paged home only) > Open with (default app per kind: Music, Messages, Browser, Email, Maps, Calendar, Gallery, Contacts, Calculator, Camera). Hidden apps is a separate root row.
- Settings/config touched: `PuckActionsStore` (segments, slots, labels; not in launcher_prefs), `wheelGrouping`, `pageMode`, `wheelWorkOnly`, per-kind default apps, `HiddenAppsStore`.
- Friction: "Pages" under App drawer means "keep all apps on the pages or only chosen ones" and shares its name with Home > Pages and Page N; "Open with" belongs to launching, not to the drawer; Hidden apps is far from the drawer; a drawer settings page named "App drawer" while the product no longer has a drawer.
- Ideal: Apps group: Disc (segments, shortcuts as a picture of the ring you tap to edit), Grouping, Hidden apps, Open with, and "Which apps stay on pages" renamed.

### J8. Clock on Glance (move, adjust, style)
- Trigger: "the clock should be where I want it, and look how I want".
- Steps today: Settings > Home screen > (Glance section) Clock style (Big digital, Big digital one line, Analog classic, Analog minimal, Word clock (English only), Off) > Clock format (12-hour / 24-hour, digital styles only) > Show date under the clock > Clock colour (Automatic / Accent) > Clock size (Small / Medium / Large). Position is fixed (14 percent from the top, centred): the owner wrote "Clock position, clock layout cannot be altered- remove that from the settings" earlier, then later: "I want to be able to move and adjust the clock while on the calm glance screen. Long press on the clock should first afford the options to edit it, long press on an empty space affords the launcher config options." Status: an agent was started (clock edit mode on long press: drag, snap to centre lines, size/style/format/date/colour, Reset position, Done, Cancel); not confirmed merged. Tap the clock = run the Clock corner action.
- Settings/config touched: `clockStyle`, `clockFormat`, `glanceClockDate`, `glanceClockAccent`, `glanceClockSize`, (new) clock position.
- Friction: the clock rows sit among wallpaper, music and hotspot rows under "Glance" in the 55-row Home screen page; "Controls hide after" and "Show music" apply to every page, not just Glance; two clocks (Glance wallpaper clock and the per-page Clock tile with its own prefs); Digital clock format row shows 24-hour as selected while the stored value is System on a 12-hour device (known, in glanceFixed.md).
- Ideal: Glance has its own page (Home > Glance) that shows the clock as a live preview; the same options appear in the long-press clock editor; both write the same prefs; Settings has a Reset position.

### J9. Work profile pause
- Trigger: "stop work notifications and apps after hours".
- Steps today: open the disc > tap Work (only visible while a work profile exists) > list shows work apps; the pill "Pause work apps" / "Resume work apps" (amber while paused) at the screen edge calls the system quiet mode > paused work apps are dimmed 45 percent, two pause bars, "<name>, paused", not launchable. The home's own pause chip steps aside while the disc is open. Settings has no pause control. Requires Liftoff to be the default home. Comms rules and badges treat work separately.
- Settings/config touched: `wheelWorkOnly`; the pause itself is system state (not stored).
- Friction: discoverable only on the disc; no settings row; no schedule; private space has no entry; device-only check never run.
- Ideal: Workspaces page shows Work as a workspace with a Pause toggle (state mirrored, same mechanism); schedule is a decision for the owner (problem 17).

### J10. Workspaces and app groups
- Trigger: "I want a Work setup and a Weekend setup".
- Steps today: Settings > Desktop & dock > Workspaces (explanation card "Workspaces are separate home setups, like Work and Personal", list with active badge and three-dot menu, add with a copy, calm-centre radio for which apps' notifications show) > Settings > Desktop & dock > App groups (new group, layout preset, sessions, scheduled launches) > Settings > Desktop & dock > Dock (show, mode, position, apps) > Dock and external display (offer, disconnect, per-display profiles). Switch: Workspaces page, Radar ("Switch to workspace"), dock menu. A workspace = home pages + dock + widget slots (left page and corners) + Radar actions pinned + which apps the calm centre shows. App groups, sessions and scheduled launches are shared by all workspaces.
- Settings/config touched: `WorkspaceManager` files (dock, pages, widget slots), app groups store, `DockSettings`, display profiles.
- Friction: owner twice: "Workspaces are not intuitive to configure." and "Workspaces configuration is still confounding. It needs to be a little upfront as a section on the settings screen." Path is root > Desktop & dock > Workspaces (inside a category named for hardware). What is and is not per workspace is invisible until you read the card. "Calm centre shows all apps" is cryptic. Dock style is elsewhere.
- Ideal: Workspaces is a top-level group (or the first card on the root) showing the active workspace, a picture of what it contains, Switch / Add / Duplicate; groups and sessions are a sibling page; dock is configured per workspace and says so.

### J11. Backup, restore, export and theme packs
- Trigger: before experimenting, new phone, share a look.
- Steps today: Settings > Backup > Export, import and restore (one versioned file at a chosen location: layout, workspaces, sessions, shortcuts, palette aliases and settings; no notifications or usage history; or restore one of the last 5 automatic snapshots; snapshots are also taken before every import or destructive edit) > Crash reports (row on the Backup page). Themes: Settings > Look > Themes (Import, Export, Reset `.liftofftheme`). Per-surface reset: Drawer shortcuts "Reset to default"; Comms Delete history.
- Settings/config touched: `StateSchema` classification of every pref file; `RetiredPrefKeys` cleanup; export codec (versioned, unknown fields preserved).
- Friction: no statement of what a backup contains per setting; settings spread across `launcher_prefs`, the puck store, the workspace file, `edit_chrome`, Comms options, `ThemingStore`, a device-local preview-window file (intentionally not exported); theme pack scope is unclear; Backup also hosts Crash reports.
- Ideal: Backup page shows "included: X, excluded: Y (why)"; Look > Themes says exactly which settings a theme pack carries; every new setting is classified at creation (see checklist).

### J12. Recover when something breaks (device checks, crash recovery)
- Trigger: the launcher crashes, a screen is blank, or the owner wants evidence.
- Steps today: any crash/ANR/native crash > next start shows the recovery screen (plain-language summary, View the full report with Copy / Save file / Email-share sheet / WhatsApp on tap, Continue, Discard, Reset data only after a repeated crash) > if it crashes again (streak 2 or within 30 s with a crash-shaped exit record) > Safe mode (minimal grid, widgets disabled, restore from the last snapshot, Carry on, View crash report). Quick restarts never trigger it. In Settings: Capabilities (report of every capability and state, Copy report; OEM notes), Lab (debug gate: spikes, Device checks page with frame timing, cold start, checklist, copyable report), Backup > Crash reports, System > Android settings.
- Settings/config touched: `CrashLoopGuard`, snapshots, `LabGate`.
- Friction: Lab/Capabilities/About sit in the root list at the same visual weight as Look; the recovery screen is English-only (decision in auditSweep.md); no in-app way to say "restore previous pages" except a row that appears for 30 days after a reset (Home screen > Restore previous pages).
- Ideal: one "Help and recovery" area at the bottom of System: Capabilities, Crash reports, Restore snapshot, Safe mode explanation, Device checks (when enabled), About.

---------------------------------------------------------------------------------------------------------------------

## 4. IA problems and open decisions

Current structure, from `reorganise()` in `SettingsCategories.kt` and `OneUiModel.GROUPS` (root cards, in order):
Card 1: Page N (paged home only), Look, Home screen, App drawer, Desktop & dock. Card 2: Corners, Gestures, Radar.
Card 3: Comms. Card 4: System & sound, Hidden apps, Backup, Capabilities. Card 5: Lab (gated), About. Fourteen rows at root.
Row counts: Home screen about 55 (37 direct + wallpaper 3 + Glance 13 + dock style, keyboard shortcuts), Comms 15, Radar 14,
Look 8 + Indicator 7 = 15, Corners 8, App drawer 6, Gestures 3 + bindings + Motion 4, System 5, Sound 2.
Deep links use numeric category constants (`CATEGORY_THIS_PAGE = 0`, `CATEGORY_HOME = 1`, `CATEGORY_APP_DRAWER = 900`,
`CATEGORY_INDEX_BASE = 1000`).

Each problem: P = problem, E = evidence, O = options, R = recommendation, B = what it blocks.

### 1. Three different things are called "Pages", plus "Page N"
- P: Home screen > Pages (Off / Paginated / Continuous), App drawer > Pages (One page / Selected apps), the root row "Page 1" (per-page category), and the Spec's "Pages setting". The owner: "Why Page 1? Shouldnt it be pages or something of the sort?"
- E: `SettingsCategories.kt` lines ~246, ~267, ~404 (`x("Pages")` twice); `OneUiModel.PAGE = "Page"`; category exists only if `p.isPaged`.
- O: (a) rename root row "Pages" and make it a list of pages (tap a page, its settings) plus the mode; (b) keep per-page settings only inside the page editor; (c) keep "Page N" but add the count and rename the others ("Pages mode", "Apps on pages").
- R: (a)+(b): a root group "Home" with a "Pages" page: mode chips on top, then a visual strip of pages; each page opens its settings. Rename App drawer > Pages to "Apps kept on pages" (One page / Chosen apps).
- B: root row naming, deep link constants, J3 and J4.

### 2. Home screen is a 55-row junk drawer
- P: one page holds Glance, wallpaper, recents, style, density, spacing, margins, labels, badges, page turning, keyboard, status bar, restore, Edit home, dock style, Left page/Right side/Order. Ten section headers exist only through `OneUiSections.TABLE`.
- E: `OneUiSections.TABLE["Home screen"]` (10 headers); `buildSettingsCategories` Home block lines 250 to 393.
- O: (a) split into Glance, Pages and layout, Icons and grid, Keyboard; (b) keep one page, add a sticky section index; (c) move by topic into other groups (wallpaper to Look, keyboard to Controls, side panels to Side panels).
- R: (a)+(c) as in the proposed IA below. Target: no page above ~15 rows.
- B: everything else in this list; the live-summary rules.

### 3. Wallpaper is not in Look; Look holds a wallpaper effect
- P: Glance wallpaper and Pages wallpaper are Home screen rows; Look has Indicator (a signal effect over wallpaper) and Settings backdrop; text size hides in "Wallpaper palette, density and text size".
- E: `wallpaperSettings()` called from the Home block; Theme block rows; decision pagesWallpaper.md moved the picker "from Look > Theme to Home screen".
- O: (a) Wallpaper page in Look covering all surfaces (Glance, Pages, Radar background, Settings backdrop); (b) keep per-surface rows where the surface is configured plus a mirror in Look; (c) leave.
- R: (a). Surfaces are rows inside that page; Glance and Pages pages show a one-line summary linking back.
- B: J2; the live preview; theme pack scope.

### 4. Clock and Glance settings are buried and the clock has two homes
- P: five clock rows live in the Home screen page between Order/Right side and Hotspots; the page Clock tile has separate settings; the owner wants to move/adjust the clock on Glance.
- E: `glanceSettings()`; `HomePage.keys` CLOCK; glanceClock.md ("not movable"); owner 6092.
- O: (a) a dedicated Glance page (clock with preview, music, hotspots, indicator, wallpaper); (b) clock options only in the long-press editor plus a mirror row; (c) merge the Clock tile into the same settings.
- R: (a) and (c) share one model; the editor and the page edit the same prefs.
- B: J8; the position preference (new); the owner's clock-edit work in flight.

### 5. Hotspots, corners, gestures and "hot corners" are four names for two ideas
- P: presence (Four/Two/One/None + per-corner switches) is in Home screen; what each runs is in Corners; pointer dwell "Hot corners with a mouse" is also in Corners; Motion is inside Gestures.
- E: hint text "What each runs is set under Corners"; `Corner` category rows; glanceFixed.md.
- O: (a) one Controls group: Corners (picture of four corners: presence + action + style), Gestures, Pointer and keyboard, Motion; (b) merge presence into Corners only; (c) leave and rename "Hot corners" to "Pointer corners".
- R: (a). Rename "Hot corners with a mouse" to "Pointer corners". Corner style rail leads the page.
- B: J3, persona P3 acceptance.

### 6. Workspaces are buried under Desktop & dock
- P: owner twice asked for it up front; a Workspaces page is three levels from the user's mental start (root, Desktop & dock, Workspaces) and the category is named for hardware.
- E: `desktopCategory()`; settingsPopups.md; owner 6141 "Workspaces configuration is still confounding. It needs to be a little upfront as a section on the settings screen."
- O: (a) top-level group "Workspaces" (workspaces, app groups and sessions, switching shortcuts); Dock moves beside it; (b) pinned card at the top of the root showing the active workspace with Switch and Manage; (c) leave, add a root summary row.
- R: (a) and (b): the group page begins with the active-workspace card. Dock configuration moves into the workspace page (per workspace, labelled).
- B: J10, persona P6.

### 7. Left page, Right side, Order and Comms: the side-panel model has no home and a name problem
- P: three rows in Home screen (Left page, Right side, Order) plus a Comms category whose name the owner rejects; "Right side" can be a dock rail (not a panel); the owner wants widgets as a fifth section of the left panel.
- E: `LeftPage` enum (Off / Comms / A widget), `RightSide` (Recents panel / Dock rail / Off), owner 6141; HomeOrder doc.
- O (naming): (a) "Panels" with Left panel = Notifications|Widget, Right panel = Recents; (b) "Inbox" for the notifications panel; (c) keep Comms for the product surface but call the settings group "Side panels".
- O (structure): (i) Left panel allows a fifth section (Widgets) in the same panel as Comms tabs; (ii) keep two exclusive modes (today).
- R: group "Side panels", rows Left panel and Right panel, each as a rail; notification settings stay under the notifications panel. Decide the fifth-section behaviour (i or ii) first: it changes the data model (`LeftPage`).
- B: renaming across 8 languages, `commsRename` source-scan test, deep links, J6.

### 8. No font setting and the title style is unspecified in code
- P: the owner wants font selection, Space Grotesk, caps, centred titles. Today `Type.kt` uses `FontFamily.Default`; the large title is left aligned and centred vertically in its band (centredTop) only.
- E: `core-design/.../Type.kt`; settingsPages.md ("left aligned as before"); owner 6141.
- O: (a) a Font picker in Look with previews (System default, Space Grotesk, others) applying app-wide; (b) a font only for titles; (c) font by surface.
- R: (a). Ship Space Grotesk as bundled font; titles: caps, centred horizontally and vertically in the band; decide scope (settings only vs launcher-wide) and licence/asset size.
- B: every title in the app, translation checks (caps in hi/ar/ja/zh is not meaningful; define the rule), RTL, font scale 2.

### 9. "Indicator" names three different things
- P: Look > Indicator (signal effects), Home screen > Surface indicator (Comms/home/panel glyph row on Glance), the page indicator dots/pills, and the pref group "Indicator metric".
- E: `SettingCategory("Indicator")`, `glanceSettings()` "Surface indicator", glanceFixed.md.
- O: rename effects "Signal effects"; "Surface indicator" to "Page dots and shortcuts row"; or merge.
- R: "Signal effects" (calm/alert) in Look; "Surface bar" for the Glance row.
- B: summaries, search results, translations.

### 10. Add apps and widgets is not discoverable and not visual
- P: Edit home and Widgets, folders and icons are rows 3 and 4 of a 55-row page, same hint; the owner cannot find them.
- E: owner 6106; homeSheet.md moved the picker out of the Home sheet to Settings (fourth row).
- O: (a) first row of Home > Pages as two big tiles (Add apps, Add widgets) with icons; (b) a persistent "+" in the Home sheet; (c) both.
- R: (c). Tiles open the page editor tabs directly.
- B: J4, the owner's "visual" principle.

### 11. Visual coverage is uneven: many choices are still text rows
- P: owner wants visual "as far as possible". Rails exist for: page Layout, home Style, Pages, Default density, Page turning (x2), Left page, Comms theme, Light/dark, Icon shape, Settings backdrop, Radar layout. Text-only: Clock style, Clock size, Clock colour, Corner style (verify), Order, Right side, Icon colour, Wheel groups, Calm/Alert effect, Show (metric), Radar background, Hotspots, Comms default view, Web engine, Dock style (pane with previews).
- E: `SettingItem.Choice(... preview = ...)` presence in `SettingsCategories.kt`.
- O: (a) every Choice of 2 to 6 looks gets a preview; (b) previews only for spatial/appearance choices (clock, corner style, order, right side, radar background, hotspots, effects), text for behavioural ones; (c) status quo.
- R: (b), and write the rule into the checklist (section 5).
- B: preview widgets (`feature-grid/.../SettingsPreviews.kt`), tests.

### 12. Access and permissions are scattered
- P: Notification access and Do Not Disturb access (System), Access Liftoff asks for (Comms), usage access (Home screen Recent apps), contacts/files (Radar), mic (voice).
- E: System block; `AccessPane`; `recentsUseUsageAccess`.
- O: (a) Access and privacy page in System listing every grant; (b) keep inline but mirror; (c) per-feature only.
- R: (a) plus inline deep links.
- B: P5, J1.

### 13. Hidden apps, Open with and Pages-on-drawer are drawer concerns placed elsewhere
- P: Hidden apps is a root card row; Open with is a drawer pane; what stays on pages is a drawer row.
- E: `reorganise()` places Hidden apps between App drawer and Radar.
- O: Apps group = Disc, Hidden apps, Open with, Apps kept on pages; or leave Hidden apps at root.
- R: first. Keep a search alias.
- B: J7.

### 14. Dock style is separated from the Dock; the Dock is hardware-named
- P: Home screen > Dock style vs Desktop & dock > Dock; "Right side = Dock rail" is a third place.
- E: `dockStyleSettingItem()`, `desktopCategory()`.
- O: Dock page holds style; "Right side" keeps only Recents/Off and Dock rail moves to the Dock page; rename "Desktop & dock" to "Dock and displays".
- R: as listed.
- B: J10.

### 15. Density has three overlapping controls
- P: Grid follows window size + Grid density (adaptive), Default density (presets + steppers), per-page density; plus spacing across/down/same, margins, Label size, Icon size, App labels.
- E: Home block lines ~319 to ~366; Page N rows.
- O: (a) one "Layout" page: Icon size, Spacing, Margins, Density mode (Fixed grid / Adaptive) with a live picture; (b) drop one of fixed/adaptive; (c) status quo.
- R: (a). Per-page override stays in the page editor.
- B: J4, tests of every slider range.

### 16. Orientation-split rows use inconsistent naming
- P: "Page turning · portrait/landscape", "Columns · portrait", "Keyboard opens with Radar and drawer · portrait/landscape" vs "Separate corners for landscape".
- O: one row with a Portrait|Landscape segmented switch; or consistent suffixes; or only the current orientation shown.
- R: segmented Portrait|Landscape control inside each page.
- B: summaries, search.

### 17. Work profile pause has no settings entry (and no schedule)
- P: only on the disc; the owner noticed it was lost once (workProfile.md).
- O: row under Workspaces/Apps; add schedule; or leave disc-only.
- R: row in Apps group that mirrors state; schedule is the owner's call (non-goal creep; requires default home).
- B: J9.

### 18. Settings search covers rows, not inside panes; deep links are numeric
- P: `SettingsIndex` builds entries from categories' items; content rendered inside `SettingItem.Pane` (Themes, Drawer shortcuts, Workspaces, Search providers, folder lists) is not individually indexed. Deep link constants are indexes that move when "Page N" exists.
- E: `SettingsIndex.fromCategories`; `settingsCategoryIndex`.
- O: (a) register pane contents as index entries with ids; (b) keywords per pane; (c) status quo.
- R: (a) plus stable string ids for categories instead of indexes.
- B: CFG-14, checklist "every setting searchable".

### 19. Section titles depend on English row titles
- P: `OneUiSections.TABLE` keys section headers off the English title of the first row; a rename silently drops a header (a test catches existence only).
- R: give each row a stable id and each section an explicit list; do this before renaming anything.
- B: every rename in this list.

### 20. "Ground Control" versus "Settings"
- P: UI says Settings; Spec, README, comments and old decision notes say Ground Control; the owner uses "settings".
- O: Settings everywhere; keep Ground Control as a nickname for the home-sheet button; or Ground Control as the brand.
- R: "Settings"; remove Ground Control from the UI and Spec text.
- B: documentation, hidden aliases, tests that match text.

### 21. Dialog button layout: two owner rules conflict
- P: CFG-21 specifies a row of flat accent text buttons; the owner later: "never show the options side by side, top and bottom. Primary on top, coloured box, secondary below as just text".
- O: (a) adopt the owner's stacked layout in all dialogs (Settings confirm, backup, wallpaper power warning); (b) only in popovers (notification preview/menu); (c) keep One UI row.
- R: (a) after the owner confirms the scope; destructive primary stays red.
- B: `OneUiDialog` kit and tests.

### 22. Summaries and live state
- P: root summaries come from `settingsSummaries` plus fallback ("first two values or N settings"); with the split IA they must be rewritten.
- R: define summary content per group in this document (acceptance section 5).
- B: root row design.

### Proposed top-level IA (recommended option, 8 groups)

1. Home: Glance (clock with preview, music, hotspots presence, Surface bar, controls timeout), Pages (mode, Order, page list with per-page settings, Add apps and widgets tiles), Layout (icon size, spacing, margins, density, labels, badges), Edit home shortcut.
2. Look: Theme (mode, accent, palette, themes), Font (new), Icons (shape, colour, plate), Wallpaper (Glance, Pages, Radar background, Settings backdrop), Dock style, Signal effects, Text size and density.
3. Workspaces: active workspace card, list, App groups and sessions, Dock (per workspace), External displays.
4. Apps: App drawer disc (segments, shortcuts, labels, grouping), Hidden apps, Open with, Apps kept on pages, Work profile pause.
5. Radar: Layout, Appearance, Input (keyboard rows move here), Sources, Web, Privacy (history, aliases, folders, providers).
6. Controls: Corners (four-corner picture, style), Gestures, Pointer corners, Keyboard shortcuts, Motion.
7. Side panels: Left panel (Notifications / Widgets / Off), Right panel (Recents / Dock rail / Off), Notifications (view, theme, size, groups and rules, history, clear, mark read), Order.
8. System: Language, Sound, Access and privacy, Set as default, Backup and restore (+ theme packs), Help and recovery (Capabilities, Crash reports, Device checks, Lab, About).
Max depth: root > group > page (two taps); panes inside pages count as a third level only for rare tasks.

Alternatives considered: A) keep 14 rows and only fix names (cheap, leaves problems 2, 6, 11 open); B) the 8-group proposal above;
C) a flat searchable list with no groups (breaks One UI familiarity the owner asked for).

---------------------------------------------------------------------------------------------------------------------

## 5. Acceptance checklist ("settings done perfectly")

Each item is pass or fail. T = JVM or Robolectric test possible; D = device-only check.

Consistency and naming
- [ ] T Every user-visible name matches the vocabulary table (1.3); deprecated names appear nowhere in UI, hints, summaries, search aliases are hidden only. A source-scan test per deprecated term (pattern: `commsRename`).
- [ ] T All names exist in 8 languages (es, fr, de, pt, hi, zh, ja, ar); `TranslationCoverageTest` green; no English fallbacks in the new IA.
- [ ] T Every row has a stable id, not an index and not its English title; section headers reference ids (replaces `OneUiSections.TABLE` string keys).
- [ ] T No two rows in the whole tree have the same title with different meaning ("Pages", "Indicator", "Motion", "Hot corners").
- [ ] T One presentation per option set (rail xor page xor segmented xor switch) as `OneUiModel.presentation` already enforces.
- [ ] Dialog only for confirm or short text; pickers are pages; button layout follows the owner's ruling on problem 21.

Depth and reachability
- [ ] T Common tasks need at most 2 taps from the Home sheet to the change: change theme/accent, change clock style, add app or widget, switch workspace, change Radar layout, hide an app, set a hotspot, change the font.
- [ ] T Root has at most 8 group rows; no page has more than ~15 rows without section headers; no page has more than 40.
- [ ] T Every setting is a result in settings search by name, hint and group; inside-pane items too; a result opens the row scrolled to and lit (CFG-05/14).
- [ ] T Deep links: Edit home, App drawer, Radar results, Home sheet's Settings button open the right page and Back returns to the root.
- [ ] T Rows that do not apply to the current state are hidden, not shown dead (e.g. Clock format with analog styles; Page N rows on a continuous home), and the page says why.

Live summaries and state
- [ ] T Root and row summaries are computed from the same state as the page (they cannot disagree); a test per group sets a state and reads the summary.
- [ ] T The Digital clock format row shows the real device setting when the stored value is System.
- [ ] T Preview window shows the change live and is movable, resizable, minimisable (CFG-18); no touch passes through.

Visual choices
- [ ] T Every Choice of appearance has preview cards per the rule agreed for problem 11; cards are real renderings of the surface, not icons.
- [ ] T Preview cards render in both themes and both orientations (Robolectric screenshot per card set).
- [ ] Title style: caps, Space Grotesk (if adopted), centred horizontally and vertically in its band; collapsing small title centred; verified at font scale 2 and in RTL and with CJK/Devanagari/Arabic (caps rule defined).
- [ ] T Font picker applies to the whole app and survives backup/import.

Accessibility
- [ ] T Every row: name, value and state in the semantics description; switch/slider/stepper roles; 48 dp targets; 56 dp rows; 16 dp margins.
- [ ] T Covered pages are cleared from semantics; focus order matches visual order; Back and Esc step out one page.
- [ ] D TalkBack walk of J1 to J12; Switch Access; keyboard and controller walk (Tab, arrows, Enter, Esc).
- [ ] T/D Reduced motion: transitions become fades, no infinite animation in settings.
- [ ] T/D Font scale 2.0 wraps, never clips; display size large.

RTL and languages
- [ ] T Mirrored layout in Arabic; Order (Right to left) is independent of the language; icons that imply direction mirror; numerals (clock) are not mirrored.
- [ ] D Hindi, Japanese, Chinese strings fit; caps rule for scripts without case.

Backup and persistence
- [ ] T Every setting is classified in `StateSchema` (exported, device-local, or intentionally excluded with a reason) and round-trips: export > fresh install > import > same summaries.
- [ ] T No orphan prefs: every pref key is either read by a row, retired in `RetiredPrefKeys`, or documented as internal; a test fails on an unread key.
- [ ] T Retired keys from the rename/move are migrated, not lost (like `glanceContent` into `showMusic`).
- [ ] T Theme pack covers exactly the Look group, listed in the UI.
- [ ] T Backup page lists what is excluded (notifications, usage history, preview-window position) and why.

Tests per setting
- [ ] T For every row: a unit test of its model (default, set, persist, summary), a UI test that toggling writes the pref, and a search test. Generated from the row registry so a new row without tests fails.
- [ ] T Screenshot test per control type, light and dark (CFG-16).
- [ ] T Audit: no `getSharedPreferences(` outside `TolerantPrefs.of`; no snapshot writes outside composition.

Honesty and device-only list (cannot be proved by Robolectric; must be listed in `docs/device-checklist.md` and reported as unverified until the owner confirms)
- [ ] D Real keyboard raising and IME resize in text-entry pages; dialog enter animation and blur.
- [ ] D TalkBack focus trapping in pages and the floating preview window.
- [ ] D Controller keys in pages; mouse hover states.
- [ ] D One UI font rendering and Space Grotesk look at real densities.
- [ ] D Work-profile pause and resume (quiet mode needs default home), and the pill position.
- [ ] D Live and video wallpaper power warning behaviour and battery effect.
- [ ] D Real notification deep link (opens the exact notification) and Back return.
- [ ] D Hotspot swipe arbitration against the pager; edit-handle feel; clock drag with haptics.
- [ ] D Whole-app frame rate and cold start with the new settings tree (Device checks page).
- [ ] D The first-run permission flow on a clean device (J1).

Definition of done for the IA work: the 22 problems each have a recorded owner decision; the 8-group tree is in
`docs/Spec.md` CFG-21; every checklist item above is pass or listed as device-only with its reason; no status in
`docs/SPEC_STATUS.md` says "built" for anything not covered by a named test.

---------------------------------------------------------------------------------------------------------------------

## Appendix A. Owner statements about settings, grouped by theme (verbatim)

Settings structure and style
- "I want to make the settings area simpler, more traditional where the full list is laid out up-front. Try to emulate the samsung One UI." (2026-11-20)
- "The popups look ugly af in setting. Please follow oneUI better." (2026-11-20)
- "Use full page menus where there are popups, not when the Options are outside already! Why is the left page options outside as well as inside? this is there for many options. Move the settings title to the centre of the empty space." (decision note settingsPages.md)
- "I want the settings options to be visual as far as possible. The apps and widgets selection is impossible to find unless the screen is empty. The widget selector is two layers down." (2026-11-22)
- "Why Page 1? Shouldnt it be pages or something of the sort? Why comms? it needs a better name- comms is the name of the section when comms are configured to it. What if its a widget instead? I would also like to be able to add widgets to it (as a fifth section) where I can add widgets instead of on the right side, if I so choose. Workspaces configuration is still confounding. It needs to be a little upfront as a section on the settings screen. The title of the settings sections need to be vertically AND horizontally centred. It needs to be in caps. The font should be space grotesk. There is no font selection for liftoff rn, but there should be." (2026-11-22)
- "Give me a handoff doc for all the settings, configurations user personas and user journeys here. I think I need to resolve the settings IA and logic and give it to you to expect everything done perfectly." (2026-11-22)
- "I asked for the floating preview window in settings to be movable, resizeable and minimize-able. Is that done yet?" and in the later list "The preview window in the settings needs to me movable, resizeable, and minimise-able."
- "Move the comms options into liftoff settings" (2026-11-18)

Popups and buttons
- "I dont want these boxes, I want a gradient background, radial gradient from black to transparent, like a smoky effect" and "And never show the options side by side, top and bottom. Primary on top, coloured box, secondary below as just text" (2026-11-21)
- "Remove the options crossed out here amd align the look of this home popover with the volume and connectivity popovers" (Home sheet, 2026-11-19)
- "This is what the Connectivity Pop-up should look like (Currently called Internet)"; "What you are currently calling Brigthness is actually volume, and it isn't a light with rays, rather a wheel with volume markers."; "Volume knob, rather than wheel"

Glance, clock, hotspots
- "Home landing is glance only. User can at home turn off the calm (empty screen)"
- "the four hotspots are supposed to remain constant and fixed as I scroll from the calm home to the page(s) on the right" and "When I select the corner style (holo or sony-style discs of bubbles), it has to change here on the calm glance screen as well."
- "Clock position, clock layout cannot be altered- remove that from the settings. Add a setting for analog vs digital, and digital if enabled can have 12hr/24hr formats." and "Remove the clock and status section- make this clock setting appear in the same space as the calm glance screen."
- "I think I want the big wallpaper clock on the glance calm home. ... let only music be there, ditch the clock from the top. Allow configuration of clock styles for the glance calm home."
- "I want to be able to move and adjust the clock while on the calm glance screen. Long press on the clock should first afford the options to edit it, long press on an empty space affords the launcher config options."

Pages, order, editing
- "Home view will only be glance, no option to change. User can add a page after that and choose if successive pages are a scroll or paginated. Glance can be turned on or off. If its off, the first page or scroll appears." and "User can choose between left to right or right to left ordering. Right to left means the notifications are on the right edge and the columns get filled right to left." and "The pages or scroll can have the same or different wallpaper as the glance screen. Wallpaper can be solid colour, gradient or live wallpaper."
- "Dont show allu the pages at once with all the icons, add one page at a time, empty, and let the user add their icons and eidgets to it by themselves- you are showing all the pages with all the icons and the pages dont even get deleted- not okay"
- "I want the page editor to look something like this, with the pages shown on the side (in case of pagination) and apps, widgets and other settings appearing on the left (as tabs with content below), or show on the top in case of continuous. When the user taps on the shrunken page, the page should swing into view, still shrunken"
- "Run a research agent for how the home amd pages should look like while being edited as well as the widgets, the handles and editing experience is bonkers."

Drawer disc
- "The app drawer will be the disc type only. There won't be a drawer. Remove that option." and "The black circle that holds the back button of the radial app drawer should be bigger... The white donut that surrounds it should be segmented across it's whole length, and be rotatable as well. Make the shortcuts on it editable from an app drawer section in settings. it can have 6-8 segments."
- "The categories or alphabet sections must ne looped... So if I go to A, I should see # preceding it"; "The user should be able to configure the shortcuts to be shown in the four sections." (later 6 to 8)

Radar and search
- "The wallpapers must dim or use a true black for the radar background, otherwise radar becomes a poor experience."
- "Default cant be Google. Default has to be duckduckgo"
- "If I search the web, I should start seeing eing search results here. Even when I go to search the web, pressing send/enter/go opens play store. When searching apps, show play store results as well."
- "The sub options for the actions suggestions show as a '+4' format, I'd rather have the actions suggestions up front as action bubbles"

Comms
- "Rename Launchpad to Comms." (2026-11-18)
- "Currently pinch in out changes the scale of the comms interface. Change that to filter to read/unread. Once a notification is read, it disappears from the comms menu. It shouldnt." and "User should be able to configure the scale of the content here from 75% to 125% in detents of 5%."
- "Dismiss in a persistent notification store is just mark as read so it is dismissed from a place of importance."
- "Combine Usage with Timeline." and "In the timeline, I want a couple of more things, I want notifications and alerts marked here too, as well as app opens and schedule e opens, alarms, everything that gets recorded."

Wallpaper, effects, themes, dock
- "We have two options here- background and wallpaper. Make sure wallpaper picker supports live wallpaper selection as well as selecting any image from the gallery. If the user selects an animated gif ... loop can be continuous or bounce." and "Omit the bounce effect for video"
- "So the signal effect can be a dot bloom, animated dots, comic book lines or a coloured vignette. ... it can only be opposite pairs- dot bloom OR animated dots AND coloured vignette OR comic book lines."
- "I want to have dock styles- 1. Transparent 2. 3d. Custom theme uploads? a link to the asystemofcells website where people can get new themes and publish their own, so that they can browse themes in a theme store?"
- "Tapping on the dock open dock settings, long press should do that. If there are no recents or pinned, then hide the dock instead of showing just home."

Hidden apps
- "I think I hid the whatsapp icon ... now whatsapp won't show up in search as well. I need a way for hidden apps to be unhidden again."

Workspaces
- "Workspaces are not intuitive to configure." (2026-11-20) and "Workspaces configuration is still confounding. It needs to be a little upfront as a section on the settings screen." (2026-11-22)

## Appendix B. Decisions the owner must make to unblock implementation (summary)

1. Adopt the 8-group IA or choose another (problem 4 section). 2. Name of the notifications panel and the fifth-section behaviour of the left panel (7). 3. "Settings" versus "Ground Control" (20). 4. Font scope and caps rule for non-cased scripts (8). 5. Dialog button layout (21). 6. Where Workspaces sits and whether Dock is per workspace (6, 14). 7. Visual-preview rule: which choices get cards (11). 8. Theme pack scope and the store link address (J2, J11). 9. Whether web suggestions default stays ON (P5). 10. Work pause schedule: in or out of scope (17). 11. Merge or keep the Glance clock and the page Clock tile (4). 12. Whether a Simple preset for helpers is in scope (P7).

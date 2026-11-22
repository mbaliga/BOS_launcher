# Liftoff Launcher - Settings inventory, as built
Branch claude/clever-mccarthy-804ej7. Derived from code (SettingsCategories.kt, SettingsOneUi*.kt, SettingsOneUiSections.kt, SettingsOneUiSummaries.kt, SettingsIndex.kt, DeviceChecksPane.kt, LauncherStores.kt, HomeModel.kt and the pane files). "Settings" is the user-facing name; "Ground Control" is the legacy name still in code/comments/one sheet row.

## Conventions used below
- Depth = taps from Settings home. 0 root, 1 category page, 2 a row's own page (radio page / pane) or a dialog/sheet opened from a category row, 3+ pickers/dialogs opened inside a pane.
- Row kinds (OneUiModel.presentation): Switch, Slider, Stepper (inline); Rail (Choice WITH preview cards: inline, no page); Page (Choice WITHOUT preview: grey value + chevron, opens a radio page, selecting a value applies and goes back); Pane (chevron, opens own page); Link/LinkChevron (Action; destructive ones ask via confirm dialog first).
- Persistence: `lp` = SharedPreferences file launcher_prefs (read/written as one LauncherPrefs by LauncherPrefsStore); other files named in full. Exported by Backup (state export: launcher_prefs, shortcuts, home_layout, workspaces, launchpad_rules, ALLOWED pref files) EXCEPT files in StateSchema.BACKUP_EXCLUDED_PREF_FILES (palette, desktop_surface, liftoff_misc, device_checks, device_bound, groups, launchpad_hub, preview_window, edit_chrome) and `theming` (in neither list; no export key found).
- Search column: `S` = row indexed with title + hint + current value + category title (rank: title prefix 100, title word 80, title contains 60, hint 30, value-equals 20, category 10). `P` = Pane/Action without hint: title + value + category only. `-` = not indexed (lives inside a pane or sheet; only the pane title is searchable). Hidden aliases: "launchpad" -> "comms", "sessions" -> "groups". A hit opens the category page and highlights the row for 2.2 s; it does NOT open the row's sub-page. Radar also queries the same index (CategorySettingsSource) and opens Settings on the row.
- Rows marked (cond) exist only while their condition holds, so they are also absent from search then.

# A) IA as built

## A1 Settings home (depth 0)
- Container: OneUiSettingsHost inside SettingsSpace; fades/scales in; Esc and predictive back close; two-pane (list 40% / page 60%) when width >= 600 dp AND landscape, else single column with pages sliding in from the end (fade only under reduced motion). Forced LTR.
- Top bar (56 dp, pinned): back arrow at start (label "Close settings", closes Settings); small centred title "Settings" fades in as the 36 sp large title collapses; nothing at the end (no overflow menu, no actions).
- Large collapsing title "Settings" (band height = window/3, 172-260 dp; 112 dp compact in two-pane).
- Floating "Preview" window (this is the "Preview chip"): live scaled capture of the home, over the pages. Bar with grip + title "Preview" + Minimise button; minimised it is a pill labelled "Preview" with a restore glyph. Drag by bar, corner resize handle, two-finger pinch, keyboard/TalkBack actions (Move up/down/left/right, Larger, Smaller, Minimise/Restore, Reset position). Position/size/minimised remembered per orientation in pref file `preview_window` (keys `port`, `land` = "x,y,long"; `minimised`); excluded from backup. Default anchor top-end under the bar. Not a setting row; no label in the list.
- Search field "Search settings" (rounded, clear X). Typing replaces the groups with one card of results (title, "Category > value", chevron); empty -> "No settings match "q"". Back first clears the query.
- Groups (cards) in order (OneUiModel.GROUPS; unknown categories would get a card before the last). Row = 40 dp coloured icon tile (white glyph) + title + 1-line grey summary + chevron. All rows open a category page (depth 1).

| Card | Row (title) | Tile colour | Glyph | Live summary (SettingsOneUiSummaries) | Opens |
|---|---|---|---|---|---|
| 1 | Page N (paged home only; N = page under finger) | #4A86D9 blue | Widget | value of "Layout" row (Grid / Circle / Coverflow) | category page |
| 1 | Look | #9B6BD6 purple | Palette | "<Light / dark> · <Icon shape> icons" e.g. "Dark · Rounded icons" | page |
| 1 | Home screen | #4A86D9 blue | Apps | Pages Off: "Glance only"; else "<Pages value> · N page(s)" (N = real page count) | page |
| 1 | App drawer | #2FA3A0 teal | Widget | "<Ring segments> shortcut segments" (e.g. "8 shortcut segments") | page |
| 1 | Desktop & dock | #5C74C9 indigo | Apps | none given -> fallback "4 settings" (all rows are panes) | page |
| 2 | Corners | #D9923A amber | Plus | "<Corner style> · <Icon type>" e.g. "Holo · Glyph" | page |
| 2 | Gestures | #58A55C green | Pinch | "N gestures assigned" / "No gestures assigned" (count of bound gestures, touch + motion) | page |
| 2 | Radar | #2F8FBF blue-cyan | Radar | "<Radar layout> · <Web engine>" e.g. "Filmstrip · DuckDuckGo" | page |
| 3 | Comms | #DE6A5E coral | Bell | "<Default view> · <Comms size>" e.g. "All · 100%" | page |
| 4 | System & sound | #76859A slate | Gear | "<Language> · Sounds on/off" | page |
| 4 | Hidden apps (title "Hidden apps · N" internally, shown as "Hidden apps") | #8C6CB4 violet | Image | "Nothing hidden" / "N hidden" | page (the page IS the pane: no 2nd level) |
| 4 | Backup | #3F9E7A green | Info | fallback "2 settings" | page |
| 4 | Capabilities | #C48A2E ochre | Info | fallback "N settings" (Copy report + 28 read-only rows) | page |
| 5 | Lab (only if LabGate: debug build or LAB_VISIBLE alpha default) | #B45F8C pink | Sliders | fallback "N settings" | page |
| 5 | About | #6D7C8C slate | Info | first row value = Version | page |
Fallback summary = first two non-blank values of Choice/Toggle/Slider rows joined " · ", else "%d settings". Entry points other than the list: Home quick sheet "Ground Control" row (root), "Home settings" corner/gesture action, app menu "Home settings" on non-paged homes, long-press empty space on List/PSP/DSi, CATEGORY_THIS_PAGE (long-press a page -> "Page N"), CATEGORY_APP_DRAWER (empty disc sections), Radar settings results (category + row), Comms "Groups and rules" returns the other way.

## A2 Category pages (depth 1) and what each row opens
Each page: top bar with Back; large title; rows cut into rounded cards (new card where row kind changes control<->link, and around rail rows); small grey section headers from OneUiSections.TABLE (a header only appears if its first row exists, so headers vanish with the row's condition).

```
Page N  (paged home only)                      [no section headers]
  Layout (Rail 3)  | Density follows the home default (switch) | Columns/Rows portrait/landscape (4 steppers, cond custom)
  Rings (stepper, cond Circle) | Clock tile on this page (switch) | Add a page after this (link) | Delete this page (link, cond)
Look
  [Theme]   Light / dark (Rail 3) | Accent (Pane, d2: colour picker) | Themes (Pane, d2: import/export/reset/undo; d3 preview+Apply dialog)
            Wallpaper palette, density and text size (Pane, d2)
  [Icons]   Icon shape (Rail 5) | Icon colour (Page 3, d2) | Icon plate (switch)
  [Settings screen] Settings backdrop (Rail 2) | Backdrop dimming (slider, cond Wallpaper)
  [Effects] Calm effect (Page 3, d2) | Alert effect (Page 3, d2) | Show (Page 2, d2)
  [Glow]    Glow: intensity | Glow: speed | Glow: preview (Pane d2) | Glow: colours (Pane d2: 4 swatches + colour picker)
Home screen
  [Pages]   Calm home screen (Glance) (switch) | Add page (link, cond Pages=Paginated) | Edit home (link, closes Settings) | Widgets, folders and icons (link, closes Settings)
            | Restore previous pages (link, cond backup kept 30 d)
  [Layout]  Order (Page 2, d2) | Right side (Page 3, d2) | Dock style (Pane d2: 3 style previews)
  [Wallpaper] Glance wallpaper (Pane d2; type chips; live slot pickers; picture; power-warning dialog d3) | Pages use the same wallpaper as Glance (switch)
            | Pages wallpaper (Pane d2, cond switch off)
  [Recent apps] Recent apps shown (stepper 8-48) | Include apps opened from elsewhere (switch)
  [Glance]  Clock style (Page 6, d2) | Clock format (Page 2, cond digital) | Show date under the clock | Clock colour (Page 2) | Clock size (Page 3)  [last 3 cond style != Off]
            | Show music | Hotspots (Page 4, d2) | Top left/Top right/Bottom right/Bottom left hotspot (4 switches) | Controls hide after (seconds) (stepper 2-10) | Surface indicator
  [Home style] Style (Rail 4) | Pages (Rail 3) | Scroll direction (Page 4, cond DSi) | Grid follows window size | Home panels (Pane d2) | Grid density (slider, cond adaptive)
  [Icons and grid] (header only when paged) Default density (Rail 4 +Custom; preview holds 4 steppers) | Icon size (not PSP) | Same spacing both ways | Spacing / Spacing across | Spacing down (cond) | Height in page | Side margin | Top margin | Bottom margin | Label size | App labels | Notification badges | Count in badges (cond)
            (non-paged: Spacing (rows only) + Side margin only)
  [Page turning] (paged) Page turning portrait (Rail 2) | Page turning landscape (Rail 2)
  [Keyboard] Always-on keyboard | Keyboard opens with Radar and drawer · portrait | ... · landscape | Keyboard shortcuts (link -> d2 sheet: per-command Change/Unbind/Reset/Reset all, swap-on-conflict)
  [Behaviour] Hide status bar | Restore open screens
App drawer
  [Shortcut ring] Ring segments (Page 3, d2) | Drawer shortcuts (Pane d2: 6-8 rows -> d3 radio page of ~28 actions/Empty/App... -> d4 app picker; Reset to default) | Show labels under glyphs
                  | Wheel groups (Page 3, d2) | Pages (Page 2, d2, cond paged)   <- these two sit under the "Shortcut ring" header (second header at "Show labels...")
  [Shortcuts] Open with (Pane d2: 10 rows Music..Camera -> d3 app picker dialog)
Corners
  [Appearance] Corner style (Rail 3) | Corner icon size (slider 20-72 dp) | Icon type (Page 2, d2)
  [Behaviour]  Follow device rotation | Separate corners for landscape | Hot corners with a mouse | Hot corner delay (slider, cond on)
  [Shortcuts]  Top left | Top right | Bottom right | Bottom left (4 links, summary = slot) -> d2 Corner editor DIALOG (kind chips Action/App/Shortcut/Palette/Complication/Cluster/Widget/Nothing; palette add-item picker d3; widget picker hands off)
Gestures   (Motion is merged into this page)
  [Home gestures] Pinch changes grid density (switch, cond surface has density) | Pinch in | Pinch out | Swipe up | Swipe down | Swipe left | Swipe right | Double tap | Long press  (8 links -> d2 Gesture dialog: Actions/Apps tabs + "Do nothing"; summary appends "not on <style>: reason")
            | On this home (info row, no-op)
  [Motion]  Motion gestures | When home isn't showing | Motion sensitivity | Shake | Flick twice | Flip twice | Place face down (4 links -> d2 dialog)
Radar
  [Layout]  Radar layout (Rail 3) | Keep this layout with a keyboard | Radar background (Page 2, d2) | Keep moving
  [Search]  Web engine (Page 6, d2)
  [Show in results] Web results | Web suggestions | Actions | Files | Deep results | Contacts   (header text is at "Web results"; "Web engine" sits above it under [Search])
  [Sources] Search folders (Pane d2: add folder via system picker, release, reindex) | Search providers from other apps (Pane d2: per-app approve) | Search history and aliases (Pane d2: forget, remove aliases)
Comms
  [Appearance] Left page (Rail 3) | Choose widget (link, cond Widget) | Comms theme (Rail 3) | Comms size (stepper 75-125 step 5)
  [Reading]    Default view (Page 5, d2) | Clear from the shade when dismissed in Comms | Read history (link, closes Settings -> Comms Read) | Groups and rules (link -> Comms manager) | Unread strip
  [Rules and access] Comms gestures (Pane d2: 4 gestures x action chips + Reset) | Grouping & rules (Pane d2: default grouping, rules, per-app delivery) | Access Liftoff asks for (Pane d2: 5 grants)
  [Clean up]   Mark all as read | Clear all notifications | Delete history   (each: confirm dialog d2)
System & sound
  [General]  Language (Page 10, d2)
  [Android]  Set as default home | Notification access | Do Not Disturb access | Android settings  (4 links out to system)
  [Sound]    Interface sounds | Volume
Hidden apps   (category page == pane: search, per-app Unhide with confirm, Unhide all, "Hidden by Comms rules" list with Remove)
Backup        Export, import and restore (Pane d2; actions + dialogs d3) | Crash reports (Pane d2)
Desktop & dock  Dock (Pane d2, bare) | Workspaces (Pane d2, bare; subtitle = active workspace) | App groups (Pane d2, bare) | Dock and external display (Pane d2, bare)
Capabilities  Copy report (link) + 28 read-only status rows (no-op)
Lab (gated)   Copy all results (link) | Device checks (Pane d2) | 27 spike/diagnostic rows (some run on tap)
About         8 read-only rows (no-op)
```
Max nesting actually reachable: Settings > Desktop & dock (1) > Workspaces (2) > What the calm centre shows (3, radio page) > Only apps I choose -> app picker (4); also Settings > App drawer (1) > Drawer shortcuts (2) > Drawer shortcut N (3) > App... picker (4); Settings > Home screen (1) > Glance wallpaper (2) > Live slot pickers / Choose live wallpaper (3).

## A3 Pane contents (depth 2+) - what each pane holds
- Accent: colour picker (saturation/brightness + hue).
- Themes: info text; Import theme (.liftofftheme, preview dialog "This theme will change..." Apply/Cancel); Export current look; Reset to default look; Undo last theme change (cond). Theme pack fields: Mode, Accent, Icon shape, Icon colour, Dock style, Wallpaper (solid/gradient), Name, Author, Version.
- Wallpaper palette, density and text size (ThemingPane): Palette chips (Chosen here / From my wallpaper), Density slider 85-125% (steps 5), Text size slider 85-140% (steps 5).
- Glow: preview (IndicatorPreview, level scrub), Glow: colours (Low / Middle / High / Idle swatches + picker).
- Dock style: chooser Standard / Transparent / 3D (note: also reachable by long-press on the dock).
- Home panels: Usage panel chips (Today / Last 7 days); Files panel chips (10 / 20 / 50 files); Folders the launcher may read (list, Remove); hint text.
- Glance/Pages wallpaper pane: preview strip; Wallpaper type chips (Solid colour, Gradient, Signal wallpaper, System live wallpaper, Picture); Solid -> colour picker; Gradient -> From/To stop chips + picker + Angle chips (0,45,...,315); Signal wallpaper -> Preview signal chips (Live, No signal, Weak, Fair, Good, Excellent; pane-local, not stored), Slot 1 and Slot 2 pickers (None, Beam, Spire, Halo, Laser, Shaft living; Glow lines, Breathing dots, Bottom glow, Radar sweep effects; at most one living + one effect), Intensity chips (Subtle/Normal, shared by both surfaces); System live wallpaper -> "Choose live wallpaper" (opens system chooser) + Dim chips (0/25/50); Picture -> choose picture/GIF/video (200 MB video / 30 MB animated cap), Picture fit (Fill/Fit), Dim (0/25/50), Animation Playback (Still/Once/Continuous/Bounce), Loop style, Remove picture, "Animated wallpapers use more power" confirm dialog (stored powerOk). While Pages=same-as-Glance the Pages pane is only an info line.
- Drawer shortcuts: slots 1..N (N = Ring segments) each -> radio page (Empty, App..., Radar first, then every CornerActionId except All apps); Reset to default.
- Open with: Music, Messages, Browser, Email, Maps, Calendar, Gallery, Contacts, Calculator, Camera -> app picker (summary "Ask each time" or chosen app).
- Search folders / providers / history: see Radar tables.
- Comms gestures: 4 gestures (Slide an item right, Slide an item left, Long-press an item, Double-tap the header) each with action chips (see CM table); PinchIn/PinchOut gestures exist in the store but are filtered out of the UI.
- Grouping & rules (LaunchpadRulesPane): Default grouping chips (Time, App, Sender, Conversation, Flat, App and conversation) + per-app override; Rules list (Add rule: app, sender, contains text, action Hide / Mute (no badges) / Pin / Important / Auto-clear (minutes) / Group by app|sender|conversation / Flat list); Delivery per app (Show immediately / Batch into a digest [times] / Silent, Comms only; "Slide left runs" action chips).
- Access Liftoff asks for: 5 rows "Show notifications here", "Show recent apps" (usage access), "Find people" (contacts), "Search your files" (folders), "Speak to search" (microphone); each shows On/Off + Manage/enable, explains first, then goes to the system.
- Export, import and restore: Export setup... (liftoff-setup.json), Export settings only... (liftoff-settings.json), Import setup... (snapshots first), Import layout from another launcher... (Launcher3 DB or zip, apps only, confirm "Replace home layout"), "Reload now" (cond), "Widgets to add again" list (per widget row + Dismiss), Snapshots: Take a snapshot now, Restore <date> x5 (last 5 kept; taken before every import, before deleting a page, after each stable start).
- Crash reports: crash-free ledger line, pending report line, Share, "Preview recovery screen" (only where LabGate is visible).
- Dock: Show the dock (switch; absent override -> tier default T1 off, T2/T3 on), Dock mode (radio page: Collapsed band / Pinned and recent), Dock position (segmented Bottom/Left/Right; landscape only), Add apps to the dock (picker), Desktop item in the dock (switch), "Use the default for this device" (cond).
- Workspaces: list (Make active, Rename, Duplicate, Delete), Add workspace (dialog; start from empty or copy), What the calm centre shows (radio: All apps / Only apps I choose -> app picker / Apps of a group -> group radio), Radar actions (check page, pinned palette actions), widgets note.
- App groups: Your groups (Open, Pin to dock, Save session, Remind me, Delete), New group (full page: name, layout Tiled/Full size/Centred, pick 2-4 apps), Show groups on the home (switch), Schedule an app (picker + schedule dialog), Sessions (Restore, Delete).
- Dock and external display: When a display that can host apps connects (radio: Ask me when a display connects / Open the desktop on its own / Never open the desktop), When that display is unplugged (radio: Close the desktop / Move the desktop to the phone), Displays Liftoff remembers (per display: Dock position Bottom/Left/Right/Hidden, Density Automatic/120/160/213/240/320 dpi).
- Device checks (Lab): Cold start readout; Frame timing probes for 5 surfaces (Glance, Drawer scroll, Radar: open and type, Comms: open and close, Page swipe) each Run -> 3 s lead-in + 10 s; Checklist of 11 device checks each Pass/Fail/Skip + note (<=400 chars); Copy report. Stored in pref file `device_checks` (excluded from backup).

# B) Settings inventory (tables). IDs are stable row ids used for the counts in C.
Column keys: Ctl = control; Dflt = default; Key = file:key; Dep = depends on / hidden when.

## B1 Page N (depth 1; paged home only; per-page, stored in home_layout, not in launcher_prefs)
| # | Setting | Where | Ctl | Values/range | Dflt | Key | Affects | Dep | Search |
|---|---|---|---|---|---|---|---|---|---|
| PG01 | Layout | Page N | Rail | Grid / Circle / Coverflow | Grid | home_layout: page.layout.shape | that page's arrangement | paged home | S |
| PG02 | Density follows the home default | Page N | Switch | on/off (off copies home grids as custom) | on | home_layout: page.layout.portrait/landscape (null = follow) | page grid | Layout=Grid | S |
| PG03 | Columns · portrait | Page N | Stepper | 2-10 | home 3 | same | page grid | Grid + custom density | S |
| PG04 | Rows · portrait | Page N | Stepper | 1-10 | 6 | same | page grid | same | S |
| PG05 | Columns · landscape | Page N | Stepper | 2-10 | 5 | same | page grid | same | S |
| PG06 | Rows · landscape | Page N | Stepper | 1-10 | 2 | same | page grid; overflow moves to next page | same | S |
| PG07 | Rings | Page N | Stepper | 1-3 (6/12/18 slots + centre) | 2 | home_layout: page.layout.rings | circle page | Layout=Circle | S |
| PG08 | Clock tile on this page | Page N | Switch | on/off (moves tile) | off | home_layout: key tile:clock in page keys | clock tile | paged | S |
Actions (not settings): Add a page after this; Delete this page (cond >1 page or non-empty).

## B2 Look (depth 1)
| # | Setting | Where | Ctl | Values/range | Dflt | Key | Affects | Dep | Search |
|---|---|---|---|---|---|---|---|---|---|
| LK01 | Light / dark | Look>Theme | Rail | Dark / AMOLED / Light | Dark | lp:themeMode | whole launcher UI | overridden when Palette = From my wallpaper | S |
| LK02 | Accent | Look>Theme | Pane (picker) | ARGB colour | #8E7BFF | lp:accent | accent everywhere, switch/slider colour in themes | overridden by wallpaper palette | P |
| LK03 | Palette (Chosen here / From my wallpaper) | Look>Theme>Wallpaper palette... | Chips in pane | Fixed / Wallpaper | Chosen here | theming:settings (JSON "source") | derives mode+accent from system wallpaper | - | - (pane title only) |
| LK04 | Density (UI scale) | same pane | Slider | 85-125% step 5 | 100% | theming:settings.density | size of everything (ThemeScaleMath) | - | - |
| LK05 | Text size | same pane | Slider | 85-140% step 5 | 100% | theming:settings.type | text only | - | - |
| LK06 | Themes (import / export / reset / undo) | Look>Theme | Pane (4 actions) | .liftofftheme file | - | writes lp themeMode/accent/iconShape/iconTint/wallpaper + dock style | look in one file | - | P |
| LK07 | Icon shape | Look>Icons | Rail | Original / Rounded / Squircle / Circle / Square | Rounded | lp:iconShape | all app icons | - | S |
| LK08 | Icon colour | Look>Icons | Page | None / Accent / Monochrome | None | lp:iconTint (per-app override: icon_colors:<appKey>) | app icons | per-app colour set from app menu | S |
| LK09 | Icon plate | Look>Icons | Switch | on/off | on | lp:iconPlate | tile behind transparent icons | - | S |
| LK10 | Settings backdrop | Look>Settings screen | Rail | Background, dimmed / Pitch black | Background, dimmed | lp:settingsBackdrop | behind Settings pages | - | S |
| LK11 | Backdrop dimming | Look>Settings screen | Slider | 20-95% | 70% | lp:settingsDim | Settings backdrop | backdrop = wallpaper (cond) | S |
| LK12 | Calm effect | Look>Effects | Page | Off / Dot bloom / Animated dots | Animated dots | lp:indCalm | signal/battery background effect | - | S |
| LK13 | Alert effect | Look>Effects | Page | Off / Coloured vignette / Comic-book lines | Off | lp:indAlert | alert effect when low | - | S |
| LK14 | Show | Look>Effects | Page | Signal / Battery | Signal | lp:indMetric | what effects map to | - | S |
| LK15 | Glow: intensity | Look>Glow | Slider | 0-100% | 60% | lp:indIntensity | effects | - | S |
| LK16 | Glow: speed | Look>Glow | Slider | 0-100% | 50% | lp:indSpeed | effects | - | S |
| LK17 | Glow: colours - Low | Look>Glow (pane) | Swatch+picker | ARGB | #FF4D4D | lp:indLow | effects | - | P (pane title) |
| LK18 | Glow: colours - Middle | same | same | ARGB | #FFB347 | lp:indMid | same | - | - |
| LK19 | Glow: colours - High | same | same | ARGB | #6BFF8F | lp:indHigh | same | - | - |
| LK20 | Glow: colours - Idle | same | same | ARGB | #8A8F99 | lp:indIdle | same | - | - |
Also Look>Glow: "Glow: preview" pane (no setting, scrub only).

## B3 Home screen (depth 1) - Pages / Layout / Wallpaper / Recents
| # | Setting | Where | Ctl | Values/range | Dflt | Key | Affects | Dep | Search |
|---|---|---|---|---|---|---|---|---|---|
| HS01 | Calm home screen (Glance) | Home>Pages | Switch | on/off | on | lp:glanceEnabled | Glance as first surface | - | S |
| HS02 | Order | Home>Layout | Page | Left to right / Right to left | LTR | lp:homeOrder | Comms edge, recents side, column/page fill, hotspot roles | - | S |
| HS03 | Right side | Home>Layout | Page | Recents panel / Dock rail / Off | Recents panel | lp:rightSide | swipe from right edge | - | S |
| HS04 | Dock style | Home>Layout | Pane | Standard / Transparent / 3D | Standard | liftoff_workspaces.json: dock.style | dock look | also long-press dock; also in Themes | P |
| HS05 | Pages use the same wallpaper as Glance | Home>Wallpaper | Switch | on/off | on | lp:wpPagesSame | Pages wallpaper source | - | S |
| HS06 | Recent apps shown | Home>Recent apps | Stepper | 8-48 | 24 | lp:recentsLimit | Recents panel length | - | S |
| HS07 | Include apps opened from elsewhere | Home>Recent apps | Switch | on/off | on | lp:recentsUsage | adds usage-access apps to Recents | needs Usage access (Access pane) | S |
| HS08 | Clock style | Home>Glance | Page | Big digital / Big digital, one line / Analog classic / Analog minimal / Word clock / Off | Big digital | lp:clockStyle | Glance clock | - | S |
| HS09 | Clock format | Home>Glance | Page | 12-hour / 24-hour | System (UI shows 24-hour while stored value is System = follow device) | lp:clockFormat | Glance digital clock | style digital | S |
| HS10 | Show date under the clock | Home>Glance | Switch | on/off | off | lp:glanceClockDate | Glance | style != Off | S |
| HS11 | Clock colour | Home>Glance | Page | Automatic / Accent | Automatic | lp:glanceClockAccent | Glance | style != Off | S |
| HS12 | Clock size | Home>Glance | Page | Small .6 / Medium .8 / Large 1 | Large | lp:glanceClockSize | Glance | style != Off | S |
| HS13 | Show music | Home>Glance | Switch | on/off | on | lp:showMusic | now-playing line + pop-in controls on every page | - | S |
| HS14 | Hotspots | Home>Glance | Page | Four / Two (bottom) / One / None (Custom shown only via toggles) | Four | lp:glanceHotspots (encoded set) | corner hotspots | also quick sheet | S |
| HS15 | Top left hotspot | Home>Glance | Switch | on/off | on | lp:glanceHotspots | what each runs = Corners | - | S |
| HS16 | Top right hotspot | same | Switch | | on | same | | | S |
| HS17 | Bottom right hotspot | same | Switch | | on | same | | | S |
| HS18 | Bottom left hotspot | same | Switch | | on | same | | | S |
| HS19 | Controls hide after (seconds) | Home>Glance | Stepper | 2-10 | 4 | lp:glanceControlsSec | music controls pop-out | - | S |
| HS20 | Surface indicator | Home>Glance | Switch | on/off | on | lp:glanceIndicator | bottom row Comms/home/right panel | page dots take over when hidden | S |
| HS21 | Style | Home>Home style | Rail | Pages / List / PSP / DSi | Pages (Grid) | lp:style | home surface type | - | S |
| HS22 | Pages | Home>Home style | Rail | Off / Paginated / Continuous | Off | lp:homePages | pages after Glance | also quick sheet; name clash with App drawer "Pages" | S |
| HS23 | Scroll direction | Home>Home style | Page | Along the long side / short side / Top to bottom / Left to right | Long | lp:scrollAxis | DSi/continuous scroll | Style=DSi | S |
| HS24 | Grid follows window size | Home>Home style | Switch | on/off | off | lp:adaptiveGrid | adaptive columns/rows | - | S |
| HS25 | Home panels | Home>Home style | Pane | see HS26-27 | - | - | Files/usage panels | - | P |
| HS26 | Usage panel range | Home panels pane | Chips | Today / Last 7 days | Today | home_state:panel.usage | Timeline usage block | - | - |
| HS27 | Files panel: files per folder | Home panels pane | Chips | 10 / 20 / 50 | 20 | home_state:panel.files.count (clamped 5-50) | Files panel | - | - |
| HS28 | Grid density | Home>Home style | Slider | 50-200% | 100% | lp:gridDensity | adaptive grid | adaptive on | S |
| HS29 | Default density | Home>Icons and grid | Rail | Roomy 3x5/4x2, Default 3x6/5x2, Dense 4x7/6x3, Packed 5x8/8x3, Custom | Default | lp:pCols,pRows,lCols,lRows | portrait/landscape grids | paged | S |
| HS30 | Default density: Columns portrait | same (in rail preview) | Stepper | 2-10 | 3 | lp:pCols | | paged | - |
| HS31 | Default density: Rows portrait | same | Stepper | 1-10 | 6 | lp:pRows | | paged | - |
| HS32 | Default density: Columns landscape | same | Stepper | 2-10 | 5 | lp:lCols | | paged | - |
| HS33 | Default density: Rows landscape | same | Stepper | 1-10 | 2 | lp:lRows | | paged | - |
| HS34 | Icon size | Home>Icons and grid | Slider | 50-95% | 82% | lp:iconScale | icons on grid/DSi/List | not PSP | S |
| HS35 | Same spacing both ways | same | Switch | on/off | on | lp:spacingV (-1 = same) | | paged | S |
| HS36 | Spacing / Spacing across | same | Slider | 0-100% | 0% | lp:iconSpacing | | paged (non-paged: row gap only) | S |
| HS37 | Spacing down | same | Slider | 0-100% | (= across) | lp:spacingV | | paged + not "same" | S |
| HS38 | Height in page | same | Slider | 0-100% | 40% | lp:gridBias | | paged | S |
| HS39 | Side margin | same | Slider | 0-25% | 0 | lp:gridInsetX | | all styles | S |
| HS40 | Top margin | same | Slider | 0-40% | 0 | lp:gridInsetTop | | paged | S |
| HS41 | Bottom margin | same | Slider | 0-40% | 0 | lp:gridInsetBottom | | paged | S |
| HS42 | Label size | same | Slider | 70-150% | 100% | lp:labelScale | | paged | S |
| HS43 | App labels | same | Switch | on/off | off | lp:labels | labels on home | PSP: only focused name | S |
| HS44 | Notification badges | same | Switch | on/off | on | lp:badgesOn | dots on apps | needs notification access | S |
| HS45 | Count in badges | same | Switch | on/off | on | lp:badgeCount | | badges on | S |
| HS46 | Page turning · portrait | Home>Page turning | Rail | Sideways / Up / down | Sideways | lp:pPaging | | paged | S |
| HS47 | Page turning · landscape | same | Rail | same | Sideways | lp:lPaging | | paged | S |
| HS48 | Always-on keyboard | Home>Keyboard | Switch | on/off | off | lp:alwaysKeyboard | type-to-find mode | - | S |
| HS49 | Keyboard opens with Radar and drawer · portrait | same | Switch | on/off | off | lp:kbOpenP | | - | S |
| HS50 | Keyboard opens with Radar and drawer · landscape | same | Switch | on/off | off | lp:kbOpenL | | - | S (no hint) |
| HS51 | Keyboard shortcuts (per-command chords) | Home>Keyboard | Link -> sheet | Change / Unbind / Reset / Reset all | defaults | extras.json: "keyboard" | hardware-keyboard bindings | - | S (hint) |
| HS52 | Hide status bar | Home>Behaviour | Switch | on/off | on | lp:hideStatusBar | | - | S |
| HS53 | Restore open screens | Home>Behaviour | Switch | on/off | off | lp:restoreSurfaces (+ liftoff_misc:openSurface) | reopen drawer/Radar after restart | not in safe mode | S |
Actions on this page (not settings): Add page, Edit home, Widgets, folders and icons, Restore previous pages.

### B3b Wallpaper panes (Glance wallpaper pane HS54-HS66; Pages wallpaper pane repeats with prefix wpPages, HS67-HS79; intensity shared HS80). Pane rows are searchable only by pane title ("Glance wallpaper"/"Pages wallpaper" + summary).
| # | Setting | Ctl | Values | Dflt | Key (Glance; Pages uses wpPages*) |
|---|---|---|---|---|---|
| HS54 | Wallpaper type | Chips | Solid colour / Gradient / Signal wallpaper / System live wallpaper / Picture | Signal wallpaper | lp:wpGlanceKind |
| HS55 | Solid colour | Picker | ARGB | default solid | lp:wpGlanceSolid |
| HS56 | Gradient from | Picker | ARGB | | lp:wpGlanceFrom |
| HS57 | Gradient to | Picker | ARGB | | lp:wpGlanceTo |
| HS58 | Gradient angle | Chips | 0,45,90,...,315 | 90 | lp:wpGlanceAngle |
| HS59 | Live: Slot 1 | Picker | None, Beam, Spire, Halo, Laser, Shaft, Glow lines, Breathing dots, Bottom glow, Radar sweep (normalised: <=1 living + effects) | default pair | lp:bgSlot1 (Glance) / wpPagesSlot1 |
| HS60 | Live: Slot 2 | Picker | same | | lp:bgSlot2 / wpPagesSlot2 |
| HS61 | Picture file | Chooser | gallery still / GIF / WebP / video | none | lp:wpGlancePic (+PicAnim, PicVid, PicMs; file in device_bound) |
| HS62 | Picture fit | Chips | Fill / Fit | Fill | lp:wpGlancePicFit |
| HS63 | Picture dim | Chips | 0 / 25 / 50 % | 0 | lp:wpGlancePicDim |
| HS64 | Animation playback | Chips | Still / Play once / Continuous / Bounce | Still | lp:wpGlancePicGif |
| HS65 | Loop style (bounce) | Chips | bounce style | | lp:wpGlancePicBounce |
| HS66 | System wallpaper dim | Chips | 0 / 25 / 50 % | 0 | lp:wpGlanceSysDim |
| HS67-HS79 | (same 13 rows for Pages) | | | | lp:wpPages* (cond pagesSameAsGlance off) |
| HS80 | Intensity (live wallpaper) | Chips | Subtle / Normal | Normal | lp:radarIntensity (shared; name is legacy "Radar intensity") |
Also stored: lp:wpGlancePicOk/powerOk (power-warning confirmed). Not stored: "Preview signal".

## B4 App drawer (depth 1) - the white shortcut ring around the Back hub, wheel, Open with
| # | Setting | Where | Ctl | Values | Dflt | Key | Affects | Dep | Search |
|---|---|---|---|---|---|---|---|---|---|
| AD01 | Ring segments | AD>Shortcut ring | Page | 6 / 7 / 8 | 8 | puck_actions:segments | disc ring | - | S |
| AD02 | Drawer shortcut slot 1..8 (8 slots, first N used) | AD>Shortcut ring>Drawer shortcuts pane | Radio page + app picker | Empty / App... / Radar / any CornerActionId except All apps | Flashlight, Clock, Notifications, Do not disturb, Radar, Camera, Calculator, System settings | puck_actions:slots (newline list "a:Name"/"p:appKey"/"-") | disc segment actions | slots > N hidden but kept | P (pane) |
| AD03 | Show labels under glyphs | AD>Shortcut ring | Switch | on/off | off | puck_actions:labels | disc | - | S |
| AD04 | Wheel groups | AD>Shortcut ring | Page | A-Z / Category / Recent | A-Z | lp:wheelGrouping | wheel spokes | - | S |
| AD05 | Pages | AD>Shortcut ring | Page | Multiple pages / One page | Multiple pages | lp:pageMode | how many home pages show | paged; same title as HS22 | S |
| AD06 | Open with: Music, Messages, Browser, Email, Maps, Calendar, Gallery, Contacts, Calculator, Camera (10 rows) | AD>Shortcuts>Open with pane | App picker | any installed app / Ask each time | Ask each time | action_apps (per action id) | corner/gesture/disc actions | - | P (pane) |
Not in Settings: Work-profile-only disc toggle (lp:wheelWorkOnly, on the disc; no row).

## B5 Corners (depth 1)
| # | Setting | Where | Ctl | Values | Dflt | Key | Affects | Dep | Search |
|---|---|---|---|---|---|---|---|---|---|
| CR01 | Corner style | Corners>Appearance | Rail | Holo / Sony quarter discs / Bubbles | Holo | lp:cornerStyle | corner chrome | - | S |
| CR02 | Corner icon size | same | Slider | 20-72 dp step 1 | 32 | lp:cornerIconSize | | - | S |
| CR03 | Icon type | same | Page | Glyph / App icon | Glyph | lp:cornerIconMode | | - | S |
| CR04 | Follow device rotation | Corners>Behaviour | Switch | on/off | on | lp:cornersFollow | | - | S |
| CR05 | Separate corners for landscape | same | Switch | on/off | off | lp:cornersPerOri | landscape set of slots | - | S |
| CR06 | Hot corners with a mouse | same | Switch | on/off | on | lp:hotCornersOn | pointer dwell reveal | - | S |
| CR07 | Hot corner delay | same | Slider | 50-600 ms step 10 (store clamps 0-1000) | 150 | lp:hotCornerDwell | | CR06 on | S |
| CR08 | Top left slot | Corners>Shortcuts>dialog | Dialog | Action / App / Shortcut / Palette (<=40 items) / Complication / Cluster (<=4) / Widget / Nothing | Camera | shortcuts:corner.TopLeft (+lcorner.* landscape; device_bound file for widgets) | corner + Glance hotspot target | - | S |
| CR09 | Top right slot | same | | | Music | shortcuts:corner.TopRight | | | S |
| CR10 | Bottom right slot | same | | | Radar | shortcuts:corner.BottomRight | | | S |
| CR11 | Bottom left slot | same | | | All apps | shortcuts:corner.BottomLeft | | | S |
(Complications: Battery, Date, Time, Next alarm, Ring mode, Flashlight, Storage, Notifications.)

## B6 Gestures + Motion (depth 1; Motion merged here)
Assignable action list (CornerActionId): Music, Camera, Radar, All apps, Notifications, Home settings, Phone, Messages, Browser, Email, Maps, Calendar, Clock, Gallery, Contacts, Calculator, Assistant, Flashlight, Denser grid, Sparser grid, Cycle ring/vibrate/silent, Ring mode, Vibrate mode, Silent mode, Do not disturb, Settings, Comms, Open recents; or any app or app shortcut; or Nothing.
| # | Setting | Where | Ctl | Values | Dflt | Key | Affects | Dep | Search |
|---|---|---|---|---|---|---|---|---|---|
| GS01 | Pinch changes grid density | Gestures>Home gestures | Switch (derived) | on = PinchIn Denser grid + PinchOut Sparser grid; off = PinchIn Radar, PinchOut Nothing | off | no own key; writes shortcuts:gesture.PinchIn/PinchOut | | surface has density | S |
| GS02 | Pinch in | Gestures | Dialog | action/app/nothing | Radar | shortcuts:gesture.PinchIn | home | | S |
| GS03 | Pinch out | same | | | Nothing | gesture.PinchOut | | | S |
| GS04 | Swipe up | same | | | All apps | gesture.SwipeUp | | home must not scroll that way | S |
| GS05 | Swipe down | same | | | Notifications | gesture.SwipeDown | | same | S |
| GS06 | Swipe left | same | | | Radar | gesture.SwipeLeft | | same | S |
| GS07 | Swipe right | same | | | Nothing | gesture.SwipeRight | | not when left page opens | S |
| GS08 | Double tap | same | | | Nothing | gesture.DoubleTap | | empty space | S |
| GS09 | Long press | same | | unbound = quick sheet | quick sheet | gesture.LongPress | | empty space | S |
| GS10 | Motion gestures | Gestures>Motion | Switch | on/off | on | lp:motion | sensors while home shows | sensor present | S |
| GS11 | When home isn't showing | same | Switch | on/off | off | lp:motionBg | foreground service listening | - | S |
| GS12 | Motion sensitivity | same | Slider | 0-100% | 50% | lp:motionSens | | - | S |
| GS13 | Shake | same | Dialog | | Vibrate mode | shortcuts:gesture.Shake | | | S |
| GS14 | Flick twice | same | | | Silent mode | gesture.DoubleFlick | | | S |
| GS15 | Flip twice | same | | | Flashlight | gesture.DoubleFlip | | | S |
| GS16 | Place face down | same | | | Nothing | gesture.FaceDown | | | S |
Info row (no-op): "On this home" (explains which swipes are free).

## B7 Radar (depth 1)
| # | Setting | Where | Ctl | Values | Dflt | Key | Affects | Dep | Search |
|---|---|---|---|---|---|---|---|---|---|
| RD01 | Radar layout | Radar>Layout | Rail | Filmstrip / Orbit / Flat | Filmstrip | lp:searchLayout (+sets searchLayoutPinned on pick) | | - | S |
| RD02 | Keep this layout with a keyboard | same | Switch | on/off | off | lp:searchLayoutPinned | else flat list with keyboard/mouse | - | S |
| RD03 | Radar background | same | Page | Dimmed wallpaper / True black | Dimmed | lp:radarBackground | | - | S |
| RD04 | Keep moving | same | Switch | on/off | on | lp:searchMotion | Filmstrip/Orbit drift | - | S |
| RD05 | Web engine | Radar>Search | Page | Google / DuckDuckGo / Bing / Brave / Ecosia / Startpage | DuckDuckGo | lp:webEngine | Enter opens engine | - | S |
| RD06 | Web results | Radar>Show in results | Switch | on/off | on | lp:searchWeb | web + Play Store rows | - | S |
| RD07 | Web suggestions | same | Switch | on/off (privacy: only network use) | on | lp:webSuggestions | typed text sent to engine/Play Store | Web results on; engine has endpoint (not Ecosia/Startpage) | S |
| RD08 | Actions | same | Switch | on/off | on | lp:searchActions | | - | S |
| RD09 | Files | same | Switch | on/off | on | lp:searchFiles | | - | S |
| RD10 | Deep results | same | Switch | on/off | on | lp:searchDeep | Fylz/Fotoz | apps installed | S |
| RD11 | Contacts | same | Switch | on/off | on | lp:searchContacts | | contacts permission | S |
| RD12 | Search folders | Radar>Sources | Pane | add (system picker) / release / Update index | none | system persisted URI grants (FileGrants) + file index | file search | no all-files access | P |
| RD13 | Search providers from other apps | same | Pane | per provider On/Off | off | palette:approvedSources | third-party providers | READ_SEARCH apps | P |
| RD14 | Search history and aliases | same | Pane | Forget what Radar learned; remove aliases | - | radar learning store; palette aliases | ranking | - | P |
Not in Settings: Radar result text size (radar_ui:resultScale, default 1.15, pinch/Ctrl+/-); webNoteSeen (radar_ui). Radar signal override (lp:radarSignalForce) lives in Lab rows.

## B8 Comms (depth 1) - notifications hub / side panel
| # | Setting | Where | Ctl | Values | Dflt | Key | Affects | Dep | Search |
|---|---|---|---|---|---|---|---|---|---|
| CM01 | Left page | Comms>Appearance | Rail | Off / Comms / A widget | Comms | lp:leftPage | swipe right from first page | - | S |
| CM02 | Choose widget | same | Link | widget picker | none | device_bound:leftWidgetId | | Left page = widget | S |
| CM03 | Comms theme | same | Rail | Dark / Light / Follow the phone | Dark | lp:lpTheme | Comms look | - | S |
| CM04 | Comms size | same | Stepper | 75-125% step 5 | 100% | comms_scale:pct | Comms text/rows (also Ctrl+/-) | - | S |
| CM05 | Default view | Comms>Reading | Page | All / Unread / Read / Files / Timeline | All | launchpad_hub:defaultView | first tab | also set from Comms | S |
| CM06 | Clear from the shade when dismissed in Comms | same | Switch | on/off | on | launchpad_hub:clearFromShade | | - | S |
| CM07 | Unread strip | same | Switch | on/off | on | lp:launchpadRail | strip at left edge | - | S |
| CM08 | Comms gestures: Slide an item right | Comms>Rules and access>pane | Chips | Nothing/Dismiss/Mark as read/Open the app/More options/Mute this app/Hide this app/Pin this sender/Clear all/Show unread...Next grouping/Collapse | Dismiss | launchpad_rules:gestures | | - | P |
| CM09 | ...Slide an item left | same | Chips | same | Dismiss | same | | | - |
| CM10 | ...Long-press an item | same | Chips | same | More options... | same | | | - |
| CM11 | ...Double-tap the header | same | Chips | non-item actions | Next grouping | same | | | - |
| CM12 | Default grouping | Comms>Grouping & rules>pane | Chips | Time/App/Sender/Conversation/Flat/App and conversation | App and conversation | launchpad_rules:prefs.defaultMode | list grouping | - | P |
| CM13 | Per-app grouping override | same | Chips | as CM12 | none | launchpad_rules:prefs.perApp | | - | - |
| CM14 | Rules (add/remove) | same | Form | app, sender, text, action | none | launchpad_rules:rules | filter/pin/hide | - | - |
| CM15 | Delivery per app | same | Chips | Show immediately / Digest / Silent in Comms; digest times default 08:00, 18:00 | Immediate | launchpad_rules:policies | | - | - |
| CM16 | Slide left runs (per app) | same | Chips | Default or an action | Default | launchpad_rules:policies.primaryAction | | | - |
| CM17 | Access: Show notifications here | Comms>Access pane | Link | system listener settings | off | system | Comms feed | - | P |
| CM18 | Access: Show recent apps | same | Link | usage access | off | system | | | - |
| CM19 | Access: Find people | same | Link | READ_CONTACTS | off | system | | | - |
| CM20 | Access: Search your files | same | Link | folder picker | off | FileGrants | | | - |
| CM21 | Access: Speak to search | same | Link | RECORD_AUDIO | off | system | | | - |
Actions (confirm dialogs, not settings): Read history, Groups and rules, Mark all as read, Clear all notifications, Delete history.
Comms-owned settings outside Settings (reachable from Comms itself): Groups manager (group list, priority, delivery, colour/icon, group rules, linked people, "My last name", "Priority groups break through Do Not Disturb" = groups store), Group by / Show filter (launchpad_hub:grouping, default Flat), Sound and Vibration sheet (Music/Notifications/Alarm volume = system streams; Launcher vibration Off/Light/Medium/Strong = launcher_haptics:strength default Medium), Connectivity sheet (opens system panels), Files panel view List/Grid (home_state:panel.files.view), Comms tab/read filter (session state).

## B9 System & sound (depth 1)
| # | Setting | Where | Ctl | Values | Dflt | Key | Affects | Dep | Search |
|---|---|---|---|---|---|---|---|---|---|
| SY01 | Language | System>General | Page | System, English, Espanol, Francais, Deutsch, Portugues, Hindi, Chinese, Japanese, Arabic | System | lp:language | launcher strings (Settings forced LTR) | - | S |
| SY02 | Set as default home | System>Android | Link | role/Home settings | - | system | | not default | S |
| SY03 | Notification access | same | Link | via grant sheet | - | system | | | S |
| SY04 | Do Not Disturb access | same | Link | system | - | system | silent-mode/DND actions | | S |
| SY05 | Android settings | same | Link | opens system Settings | - | | | | S |
| SY06 | Interface sounds | System>Sound | Switch | on/off | on | lp:uiSounds | console tones in Settings | - | S |
| SY07 | Volume | same | Slider | 0-100% | 40% | lp:uiSoundVolume | tones | - | S |
(SY02-SY05 are navigation links, not stored settings.) No reduced-motion/accessibility settings exist: motion follows the system "remove animations" (rememberMotionPrefs); Device checks lists Animations removed / Font 200% / Display size / TalkBack as manual checks.

## B10 Hidden apps, Backup, Desktop & dock, Capabilities, Lab, About
| # | Setting | Where | Ctl | Values | Dflt | Key | Affects | Dep | Search |
|---|---|---|---|---|---|---|---|---|---|
| HA01 | Hidden apps (Unhide / Unhide all / Remove Comms hide rule) | Hidden apps (page=pane) | Pane inline | per-app list | none | hidden_apps:keys; launchpad_rules:rules | home, drawer, Radar, Comms | set from app menu "Hide" | P (title "Hidden apps"; category summary) |
| BK01 | Export setup / Export settings only / Import setup / Import layout / Snapshots (take, restore x5) / Reload / widget re-add | Backup>Export, import and restore | Pane (actions) | files liftoff-setup.json, liftoff-settings.json | - | snapshots store (5 kept) | everything | - | P |
| BK02 | Crash reports | Backup>Crash reports | Pane | share one; Preview recovery screen (Lab-gated) | - | crash store (device local) | | | P |
| DD01 | Show the dock | Desktop & dock>Dock | Switch | on/off/default | tier default | liftoff_workspaces.json: dock.enabled (null=default) | dock | - | - (pane P) |
| DD02 | Dock mode | same | Radio page | Collapsed band / Pinned and recent | Pinned and recent | dock.mode | | - | - |
| DD03 | Dock position | same | Segmented | Bottom / Left / Right | Bottom | dock.edge | landscape only | - | - |
| DD04 | Add apps to the dock | same | Picker | apps | none | workspace pins (per workspace) | | | - |
| DD05 | Desktop item in the dock | same | Switch | on/off | off | pin shortcut desktop | | | - |
| DD06 | Workspaces: create/rename/duplicate/delete/activate | Desktop & dock>Workspaces | list+dialogs | name unique | "Home" workspace | liftoff_workspaces.json (+ liftoff_workspace_widgets.json no-backup) | whole home (layout, dock pins, widgets) per workspace | - | - |
| DD07 | What the calm centre shows (per workspace) | same | Radio page | All apps / Only apps I choose / Apps of a group | All apps | w.notifyApps | calm-centre notifications | - | - |
| DD08 | Radar actions (per workspace) | same | Check page | pinned palette actions | none | w.actions | Radar before typing | - | - |
| DD09 | App groups (create/open/pin/session/remind/delete) | Desktop & dock>App groups | list+full page | 2-4 apps; layout Tiled/Full size/Centred | none | workspaces.json groups/sessions | | - | - |
| DD10 | Show groups on the home | same | Switch | on/off | on | dock.groupShelf | group chips on home | - | - |
| DD11 | When a display that can host apps connects | Desktop & dock>Dock and external display | Radio page | Ask me / Open on its own / Never | Ask me | desktop_surface:offer_setting | | - | - |
| DD12 | When that display is unplugged | same | Radio page | Close the desktop / Move to the phone | Close | desktop_surface:migrate_on_disconnect | | - | - |
| DD13 | Per display: Dock position | same | Pills | Bottom / Left / Right / Hidden | Bottom | desktop_surface:profile:<id> | | display seen before | - |
| DD14 | Per display: Density | same | Pills | Automatic / 120 / 160 / 213 / 240 / 320 dpi | Automatic | desktop_surface:profile:<id> | | | - |
| CP01 | Capabilities (Copy report + 28 read-only rows) | Capabilities | Info | state On/Off/Unknown + fix hint | - | none | diagnostics | always listed | S (row titles) |
| LB01 | Lab (spike rows, Orbit feel x4 + reset/export, Radar signal x6, Crash on purpose, Input devices) | Lab | Actions | Orbit creep/decay/gain/max step values | Live signal | orbit_feel:*; lp:radarSignalForce | tuning/diagnostics | LabGate | S |
| LB02 | Device checks (probes, 11 checklist items, notes, Copy report) | Lab>Device checks | Pane | Pass/Fail/Skip + note | Not checked | device_checks:probe.*, check.*.status, check.*.note, coldStartMs | evidence only | LabGate | P |
| AB01 | About (Version, Build number, Commit, Commit date, Build type, Package, Android, Device) | About | Info | read-only | - | none | | | S |

## B11 Settings that live OUTSIDE Settings (long-press / in-surface)
| # | Setting | Where | Ctl | Values | Dflt | Key | Dup of |
|---|---|---|---|---|---|---|---|
| QS01 | Pages | Home quick sheet (long-press empty home) | Chips | Off / Paginated / Continuous | | lp:homePages | HS22 |
| QS02 | Order | quick sheet | Chips | LTR / RTL | | lp:homeOrder | HS02 |
| QS03 | Hotspots | quick sheet | Chips | Four / Two (bottom) / One / None | | lp:glanceHotspots | HS14 |
| QS04 | This page: Align top / Align bottom / Make home page | quick sheet (paged) | Chips | | gridBias 0.4 / homePage 0 | lp:gridBias, lp:homePage | HS38 (partly) |
| QS05 | Transition | quick sheet (paged) | Chips | Slide / Fade / Zoom / Cube / Flip / Stack | Slide | lp:pageTransition | no Settings row |
| QS06 | Wallpaper (row) / Ground Control (row) / Delete page | quick sheet | Rows | | | | HS05, root |
| QS07 | Dock style | long-press dock | chooser | | | dock.style | HS04 |
| QS08 | Icon colour (per app) | app long-press menu | picker | ARGB | | icon_colors:<appKey> | LK08 |
| QS09 | Hide app | app menu | action | | | hidden_apps:keys | HA01 |
| QS10 | Clock tile options (analog, 12/24, day line) | item sheet of the clock tile | toggles | | analog off, System, date on | lp:clockTileAnalog / clockTileFormat / clockTileDate | HS08-HS12 (different clock) |
| QS11 | Disc Work-only toggle | all-apps disc | toggle | | off | lp:wheelWorkOnly | none |
| QS12 | Grid columns by pinch (DSi/continuous) | gesture | | 2-8 | 4 | lp:continuousColumns | no row |
| QS13 | Launcher vibration, volumes | Comms Sound and Vibration sheet | sliders | | Medium | launcher_haptics:strength | none |
| QS14 | Comms Group by / default tab "set default" | Comms menu | | | | launchpad_hub:grouping / defaultView | CM05 |
| QS15 | Radar result size | Radar pinch / Ctrl+- | | | 1.15 | radar_ui:resultScale | none |
| QS16 | Files panel view | Comms Files tab | List / Grid | | List | home_state:panel.files.view | none |
| QS17 | First-run onboarding: home style | Onboarding | chooser | | | lp:style, onboardingDone | HS21 |

# C) Counts
## C1 Counts
Method: one row = one independently changeable control with its own stored value; a "managed collection" pane (folders, providers, hidden apps, workspaces, groups, rules) counts once; links, confirm-actions and read-only rows are counted apart.
| Measure | Count | Detail |
|---|---|---|
| Settings reachable in Settings (B1-B10) | 204 | Page N 8; Look 20; Home screen 79 (incl. wallpaper pane 13 rows x2 surfaces + shared intensity = 27, home-panels 2, density steppers 4; excludes the Home panels pane row itself); App drawer 22 (4 rows + 8 ring slots + 10 Open-with apps); Corners 11; Gestures+Motion 16; Radar 14; Comms 16 (CM01-CM16); System & sound 3; Hidden apps 1; Desktop & dock 14 |
| Search-indexed rows (live) | ~230, varies with state | every row of the 15 categories (titles, hints, values, category); NOT anything inside the 24 panes |
| Settings living only outside Settings (B11) | 17 rows (9 with no Settings equivalent) | quick sheet (Transition, This page tools), clock-tile options, disc Work-only, pinch columns, Launcher vibration, Radar result size, Files view, onboarding |
| Action/link rows inside Settings | 42 | 37 on category pages: Page N 2; Home 4; Comms 5 (Read history, Groups and rules, Mark all, Clear all, Delete history); System 4; Capabilities Copy report 1; Lab 21 (Copy all results + 20 runnable); plus 5 grant links in the Access pane. Not counted: dialog/pane buttons (Backup, Device checks, Themes) |
| Read-only status rows | 44 | Capabilities 28, About 8, Lab diagnostics 7, Gestures "On this home" 1 |
| Category pages | 15 (13 always; Page N only on paged home; Lab only when LabGate) | root list not counted |
| Root list | 1 | 5 cards, 15 rows |
| Row-level pages | 20 radio pages (Choice without preview) + 24 pane pages (+ Hidden apps pane shown inline on its category page) | |
| Depth-3/4 pages | 8 pages (Drawer shortcut N radio, Dock mode radio, Calm-centre radio, Apps-of-which-group radio, Radar-actions check page, New app group full page, Display-connect radio, Display-unplug radio) + AppPicker/dialog uses (6) | plus dialogs/sheets: Corner editor, Gesture binding, Keyboard shortcuts sheet, 3 Comms confirm dialogs, Theme apply dialog, power-warning dialog, workspace add/rename/delete, group/session/schedule dialogs, import dialogs |
| Total distinct screens in Settings | 68 (1 root + 15 + 20 + 24 + 8) | + ~25 dialogs/sheets/pickers |
| Max depth | 4 taps from Settings home | e.g. Desktop & dock > Workspaces > What the calm centre shows > Only apps I choose (app picker); App drawer > Drawer shortcuts > Drawer shortcut 3 > App... (picker). Shortest settings: 1 tap (Rail/switch/slider/stepper on category page) |
| Taps to a typical setting | switch/slider/rail: 2 (category, control); Page-choice: 3 (category, row, value; value tap returns) | Search: 2 (type, tap result, row highlighted, 3rd tap to change a Page-choice) |

## C2 Duplicates (same setting or same stored value reachable in more than one place)
1. Pages (lp:homePages): HS22 and quick sheet QS01. 2. Order (lp:homeOrder): HS02 and QS02. 3. Hotspots (lp:glanceHotspots): HS14 preset, HS15-18 four switches, quick sheet QS03 (three controls, "Custom" preset only expressible by the switches).
4. Dock style: HS04 pane, long-press the dock, Themes pack (LK06). 5. Mode/accent/icon shape/icon colour/dock style/wallpaper: individual rows AND the Themes import/export/reset pane write the same fields.
6. Comms default view: CM05 and "set default" inside Comms. 7. Comms size: CM04 and Ctrl+Plus/Minus (same key). 8. Keyboard shortcuts: HS51 row and F1 sheet.
9. Folder grants: Radar > Search folders (FileGrants), Home panels > "Folders the launcher may read" (RecentFilesSource), Comms > Access > "Search your files": three UIs over the same system persisted-URI grants.
10. Notification access: System & sound > Notification access and Comms > Access > Show notifications here. 11. Hidden apps: Hidden apps page, app menu Hide, and Comms Hide rules ("Hidden by Comms rules" list also in Grouping & rules).
12. Pinch density: GS01 switch and GS02/GS03 edit the same two gesture keys. 13. Wallpaper: HS Glance wallpaper vs quick sheet "Wallpaper" row. 14. Per-page grid (PG02-06) vs default density (HS29-33) vs adaptive (HS24, HS28). 15. Icon colour: global LK08 vs per-app (app menu).
16. Edit home and Widgets, folders and icons: two rows, identical summary text, both close Settings. 17. Add page (Home) vs Add a page after this (Page N).

## C3 Stored but with no row in Settings (orphans / surface-only)
lp:continuousColumns (pinch only), lp:appsOnHome (migration only, never user-set), lp:homePage and lp:pageTransition (quick sheet only), lp:wheelWorkOnly (disc toggle), lp:onboardingDone, lp:clockTileAnalog/clockTileFormat/clockTileDate (item sheet), lp:radarSignalForce (Lab rows), launchpad_hub:grouping (Comms menu), launchpad_rules:gestures PinchIn/PinchOut (filtered out of the Comms gestures pane; still stored, defaults Show unread / Show everything, pinch now sets Comms size), launcher_haptics:strength (Comms Sound and Vibration sheet only), radar_ui:resultScale and :webNoteSeen, home_state:panel.files.view, orbit_feel:* (Lab), puck_actions slots 7-8 when Ring segments < 8 (kept hidden). Dead code: enum HealthSourcePref (no pref, no reader); SettingItem.Choice.stepped flag and its doc comment ("arrows... >6 options open a menu") are stale (Choice is rendered as Rail or radio page only); OneUiSegmented is not used by any Settings category row (only dock/desktop pane rows).

## C4 UI settings with no (or conditional) effect found
- Nothing found with a completely unread value (every lp key above has a reader). Conditional/doubtful: HS22 Pages is listed on List / PSP / DSi styles where isPaged is false (effect on those styles not verified); paged-only rows (Default density, Page turning, margins, label size) appear while Pages = Off (Glance only, no pages) because isPaged is true then; SY07 Volume stays enabled with Interface sounds off; RD07 does nothing with Ecosia/Startpage or Web results off; RD10 does nothing without Fylz/Fotoz; GS04-GS07 can be bound but "won't fire on the current home" (annotated in summary); GS10 stays editable with no motion sensor; LK01/LK02 are overridden while Palette = From my wallpaper (no note on those rows); HS09 shows "24-hour" while the stored value is System (follow device).
- Info rows that look tappable: Capabilities rows (no-op Action), About rows, "On this home".

## C5 Dead / retired preference keys (RetiredPrefKeys + store clean-ups)
launcher_prefs (purged once per install, marker retired_pref_keys_w38; also dropped on import): bgKind, bgPreset, bgA, bgB, wallpaper, searchBackdrop, searchBdStyle, radarSweep, radarSignalView, radarComic (old background gallery; bgPicture is owned by DeviceBound), minimalHints, networkDots, switcherStyle, drawerShortcutsOn, drawerShortcuts, drawerShortcutsShown, indInvert, glanceWeather, suggestion, drawerStyle, drawerView, drawerSort, drawerRail, drawerRailVisible, drawerViewSwitch, clockPosition, clockLayout, clockSize, clockDate, clockDay, clockWeather, weatherF, strip, healthSource (34). palette: scopeMapping, prefixes (2). crash_loop: rapid (1). home_state: panel.timeline.depth, panel.timeline.scheduled (2). After migration only: indEffects (-> indCalm/indAlert), glanceContent (-> showMusic) (2). Removed by LauncherPrefsStore on write: homeLanding, landingChosen, homeScroll, pagesModeChosen (4; homeScroll/pagesModeChosen are read once to derive Pages). puck_actions:actions (migrated to slots). Total distinct retired keys: 45 (41 in RetiredPrefKeys + 4 in HomeModelMigration).

## C6 Naming inconsistencies (same thing, different names - or one name, different things)
1. Product/screen: root title "Settings"; legacy "Ground Control" still shown (quick sheet row, a toast "Ground Control, Home screen, Right side"); corner/gesture action "Home settings" opens it; action "Settings" and row "Android settings" open Android's.
2. Comms vs Launchpad/hub: UI "Comms"; stored lp:lpTheme, lp:launchpadRail (UI "Unread strip"), LeftPage "Launchpad" (UI "Comms"), files launchpad_rules / launchpad_hub; Radar index alias launchpad->comms; "Silent, Comms only" vs "Silent in Comms"; "Mute (no badges)" vs "Silent" vs "Mute this app".
3. "Pages" means three things: Home>Pages (Off/Paginated/Continuous), App drawer>Pages (Multiple/One page; actually limits home pages), and the section header "Pages". Category "Home screen" is built/keyed as "Home" and the quick sheet is titled "Home".
4. Dock: "Dock position" = edge (Bottom/Left/Right) on the Dock page and = dock mode (Bottom/Left/Right/Hidden) per external display; "Dock mode" = Collapsed/Pinned (feature-dock DockMode) vs desktop DockMode enum; "Dock" page vs "Dock and external display".
5. Density x4: Look "Density" (UI scale), Home "Default density" / "Grid density" / "Density follows the home default", display "Density" (dpi).
6. Wallpaper/background: "Wallpaper palette, density and text size" is theming, not a wallpaper; Glance wallpaper types "Signal wallpaper" / "System live wallpaper"; code "background slots" (bgSlot1/2), "Radar background", "Settings backdrop", radarIntensity = "Intensity" of the Glance wallpaper.
7. Groups: "Groups and rules" (link) vs "Grouping & rules" (pane) vs "App groups" vs "Wheel groups" vs "Default grouping"; "and" vs "&". Alias "sessions" -> "groups".
8. Hotspots / hot corners / corners: Glance "Hotspots" + "%s hotspot", Corners "Hot corners with a mouse", "Corner style", corner slots.
9. Sound: "Interface sounds" + "Volume" (UI tones) vs Comms "Sound and Vibration" (system streams, "Launcher vibration").
10. Radar vs Search: category "Radar", rows "Search folders", "Search providers from other apps", "Search history and aliases", "Web engine", Access "Search your files", Files switch.
11. Look vs Theme vs Indicator: built as Theme + Indicator, shown as Look; effect rows titled "Calm effect", "Alert effect", "Show", "Glow: intensity", "Glow: speed", "Glow: preview", "Glow: colours" (colon style used nowhere else; "Show" is vague).
12. Separator/style drift: "Page turning · portrait" (middle dot) vs "Columns · portrait" vs "Hotspots"; "Keyboard opens with Radar and drawer · landscape" has no hint while portrait does; sentence vs title case ("Add page", "Add a page after this", "Add workspace", "New group", "New app group"); "Unhide" vs "Show".
13. Black: "Pitch black" (Settings backdrop) vs "True black" (Radar) vs "AMOLED" (theme).
14. Clock: Glance clock (Clock style/format/colour/size/date) vs clock tile options (analog, 12/24, date) vs "Clock tile on this page" vs Page N. 15. "Order" (generic title for page/column order) and "Right side" (really Recents/dock rail/off).
16. Product vs code: Liftoff vs com.bos.sphere / "Sphere" / BOS_launcher; wave/ticket codes (CFG-, HOM-) appear in Lab row titles (SPK-01...) shown to users.

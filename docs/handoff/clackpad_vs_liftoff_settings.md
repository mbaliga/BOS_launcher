# Clackpad settings vs Liftoff settings: what to emulate

Scope: read-only study. Clackpad = /home/user/clackpad (Android IME). Liftoff = /home/user/BOS_launcher, branch claude/clever-mccarthy-804ej7.
Clackpad paths below are relative to `clackpad/app/src/main/kotlin/xyz/mdhv/clackpad/` unless they start with docs/, web-config/ or clackpad/. "SA" = SettingsActivity.kt.
Nothing here was run on a device. Counts come from Clackpad's own docs and code.
Staleness warning: docs/ARCHITECTURE_NOTES.md section 7 (lines 517-615) is partly stale. It says "Settings search does not exist" (7.5) and "navigation is a flat 10-item home list"; the code now has a synonym search (SA:931) and 12 sections (SA:149). The code and docs/SETTINGS_HELP_AUDIT.md are the truth. Section 7.1 (two JSON builders drifting) is still a live lesson.

---------------------------------------------------------------------------------------------------
## 1. Clackpad settings IA as built

### 1.1 Shape
- Two levels only: Home (depth 0) then a section page (depth 1). No row opens a third settings page, except the "What Clackpad learned" and "Never correct" screens (SA:2999, SA:3303) and four separate Activities. Everything else is inline controls on the section page.
- Home, top to bottom (SA:655-705): logo, title "CLACKPAD SETTINGS" in monospace (24 sp, letterSpacing 0.08), a 2x3 grid of boxed tiles (SA:583-653), optional health card (only when the keyboard degraded in the last 48 h), search field (icon + plain text + hairline underline, SA:706), then either the Setup checklist card (until the IME is enabled and selected) or the Dictionary card, then 12 icon rows (icon 26 dp + 18 sp title, no chevron, no summary line), then three launcher rows (Dictionary, Notes, Analytics).
- Tile grid: Customization (selected), Dictionary & Thesaurus, Analytics & Trends, Notes, Extensions, Numpad. Four of the six open separate Activities.
- A live keyboard preview dock sits at the bottom of every screen (SA:10126): header "LIVE PREVIEW" with a collapse chevron, a test text field, then the real keyboard (native or WebView, per `native_keyboard`). Optional "Auto-hide preview while scrolling". Changes are coalesced into one rebuild after 60 ms and skipped when the JSON is byte-identical (SA:121-136).
- Size: 173 interactive options (docs/SETTINGS_HELP_AUDIT.md:9 table), 12 sections, 7 help (i) icons (4.05 %).

### 1.2 Section tree (SA:149-162, builders at the line numbers shown)
Control key: Car = carSelect icon/segment bar with the picked name beneath (SA:6863); Tiles = drawn thumbnail tiles; Sl = slider; Sw = boolCar On/Off pill (SA:8455); Btn = actionButton/linkAction; Ed = custom editor.

| Section | Groups inside (header text) and what they hold | Controls | Notable defaults |
|---|---|---|---|
| Get Set up (buildSetup SA:1098) | Setup checklist (enable IME, select IME, status line, Replay tour); "Make it yours" > Quick theme | checklist rows, Btn, Tiles (5 preset themes: Ortho, Classic, Playful, Fast, Vintage; SA:8231) | tour shown once; theme tiles show real keyboard crops |
| Keys & Layout (SA:1547, ~40 options) | (no header) Key Height, Fret Bar Height / Row Spacing; Accessibility (Reduce motion, Auto-reduce in Battery Saver (i)); Keyboard Layout; Grid Geometry; Custom Grid (i) editor; Uppercase keycaps; Action/Enter key; Extra Rows & Rails; Alt Symbol Gesture (+ hold ms, double-tap ms, calibrator buttons); Landscape Layout; One-handed; Floating keyboard; Modifier Labels; Ctrl Key Combos; Ctrl+Shift Shortcuts | Sl, Car, Sw, Ed (grid editor with drawn keycaps), calibrators | Key Height 1.2, layout qwerty, geometry staggered, alt gesture hold 400 ms |
| Appearance (SA:2654, ~41) | Theme (import .cptheme); Keycap Shape (cards + Keycap size); Keycap Legend; Space Bar Animation; Keys & Case Colour; Horizontal Swipe; Glide Trail (colour, length); Fret Colour; Quick-pull Reveal; Glyph Colour (+brightness, effect); Colour-Blind Preview; Board Lighting; Trackpad Row (i) (drag editor); Alt Symbol Glyphs; Glow/Accent Colour; App Profiles; Suggestion row (size, magnification); CAPS-lock Light (mode, 3 battery colours + thresholds); Bottom Chin (+Pro); Chin Decoration | Tiles of real renders, Car, Sl with unit readouts, colour swatches, Ed | colorway black, fret silver, accent #8E7BFF |
| Trackpad (SA:3496, ~10) | Cursor Control; Precision Bloom (+size); Corner Radius (slider with "Auto" chip); Trackpad Row Spacing; Click Buttons; Cursor Feel (sensitivity, acceleration, select gestures); Joystick Style; Trackpad shape; Colour & Bezel coverflow | Car, Sl, coverflow, Btn | cursor pad, sens 1.0, accel 0.5 |
| Gestures (SA:4350, ~10) | Multi-finger master switch; One-handed swipe; Two-finger swipes; Pinch; Three-finger swipes (each = toggle wheel + action pill, SA:4274) | Sw, pill pickers | multi_gestures on |
| Typing (SA:4367, ~29) | 10 on/off behaviours up front (autocap, double-space period, autocorrect, casing, auto-pair, lists, auto-indent, smart paste, inline calc); Correction strength; Spoken Feedback; Extra Dictionaries; Your Vernacular (i); Personal Dictionary; Never Correct; Typing Intelligence; Second Language (flag-like language tiles); Voice, Stickers & Sign; Tools Panel Nav (tile picker); Snippets | Sw (dominant), Car, language Tiles | almost every Sw defaults ON; ac_strength balanced |
| Feedback (SA:4504, ~18) | Key popup, Vibrate on keypress, Haptics intensity (0-10); Haptic effect (tile row, tap plays it); Per-Action Haptics (6 sliders); Custom Haptic Workbench; Key Click Sound tiles | Sw, Sl, Tiles (live-play on tap) | intensity 7 |
| GIFs and Stickers (SA:4770, 2) | My GIFs, My Stickers, Search GIFs Online (off) | Btn, media grid, Sw | online off |
| AI (SA:5051, ~9) | Predictions, Smart complete; Inline Translation; Translate As You Type; Nooz Models (model coverflow with fit verdicts); Bring Your Own AI (i); Downloadable on-device model | Sw, Car, coverflow tiles, Btn | all remote/AI opt-in OFF |
| Privacy (SA:5973, 2 + lists) | Clipboard history expiry, Text recovery (i), Curated presets, Per-app rules (i), Analytics, Network activity log | Car, Btn, lists | clip 24 h, recovery off |
| Advanced (SA:6535, ~10) | Pro showcase card; Keep keyboard open; Preview collapse/auto-hide/show; Wordbook Review limits; Dictionary landing effect; Backup & Restore; Version History (2 slots); Restore Defaults; Play (games); Keyboard Engine; Troubleshooting; Clackpad Pro | Sw, Car, Btn | preview_enabled on |
| About (SA:6797) | Interface Language, About, The house, Licences | Car, links | system language |

### 1.3 Control vocabulary actually used
- carSelect (SA:6863): ONE physical bar split by metallic dividers; chosen segment lit, its full name printed beneath. Options carry a glyph, a swatch or a 2-letter `seg` code. Used for nearly every single-choice setting, even 2-option ones.
- Tiles with real renders: Quick theme tiles are 132 dp squares showing "real keyboard crops" (SA:8231-8300 and the comment at SA:8454 area), selected = 2.5 dp accent ring; sizeChooser draws a keycap whose height scales with the option ("so the choice is visual", SA:1410).
- Coverflow strips: cursor colour/bezel, model catalogue, magnified centre.
- Sliders: `sliderRow` (SA:8754): UPPERCASE label, right-aligned live readout ("60%", "400ms") when a unit is given, a default-value tick drawn behind the thumb, 26 dp pill thumb. Corner radius slider adds an "Auto" chip that deletes the pref (SA:8580-8640).
- Switch: boolCar On/Off pill, label left, optional (i) after the label.
- Steppers: only inside the Custom Grid editor (rows/cols +/-, Narrower/Wider).
- Calibrators: "double-tap here to set your speed" and a hold recorder write the millisecond value so the user never types it (SA:8656, SA:8702).
- Drag editors: Trackpad Row tray, Custom Grid, nav-tab picker.
- (i): 48 dp target, opens an AlertDialog of at most 2 short sentences; only 7 exist.
- No chevron rows, no radio pages. Even long lists (languages) are tiles or chips on the section page.

### 1.4 Presets, profiles, backup, search, gating
- Presets: 5 Quick themes (apply 6+ prefs at once; `matches()` computes the selected ring) on the Setup page, plus the Theme pack import (.cptheme) and 3 settings-less preset families (colourway, fret, glyph colour).
- Profiles: only in the web Studio (web-config/studio.html tab "Profiles": New, Duplicate, Rename, Delete, Use; profile store in browser localStorage). On-device there are NO named profiles, only per-app accent "App Profiles" (package substring to colour).
- Studio: left = the real keyboard.html in an iframe; right = 9 tabs (Colors, Keys & Legends, Lighting, Chin & Stickers, Tool Layers, Ctrl Maps, Snippets, App Profiles, Profiles); 134 mapped fields; export = `{clackpad_config:1, prefs:{key:{t,v}}}` (web-config/STUDIO.md:41-71). `index.html` is the flat form builder over the same store and format. Caveat in STUDIO.md: the iframe previews the legacy WebView engine, not the native one, and the page says so in a banner.
- Backup (SA:9669-9990): whole-prefs dump, asks "include learned vernacular?" before every export, never exports `pro_unlocked` or secrets (NON_PORTABLE_PREFS, SA:9615), import trusts the type each key already has on the device (type-poison guard), 2 rolling version-history slots with relative time ("3 hours ago"), "Restore Defaults" snapshots first so it is undoable.
- Search (SA:931-947, index SA:827-925): ~85 entries, each = section + title + header anchor + synonym bag ("vibration" finds Haptics). All query words must prefix-match; title hit outranks synonym hit. A hit opens the section, scrolls to the group header and flashes it 1.6 s. Index holds no help prose.
- Gating: one boolean `pro_unlocked`; `proGate()` (SA:958) toasts "X is part of Clackpad Pro" and jumps to the Pro card; locked controls become a dimmed row with a PRO pill (SA:977). Docs admit enforcement is weak (ARCH_NOTES 7.4 line 577).

### 1.5 Taps to anything
- Any control: 2 taps (section, control). Search: type + 1 tap = lands on the group, flashed. Quick theme: 2 taps, preview already on screen. No control sits deeper than 2 taps except editors (3).

---------------------------------------------------------------------------------------------------
## 2. Design principles inferred from code and docs (with citations)

P1. Everything inline on a section page; no sub-pages for choices.
  - SA:149-162 (12 flat sections) and the absence of any radio page. Choices render as bars/tiles on the page (SA:6857 "Car-button exclusive select (per the mockups)").
P2. See the effect while you set it: the preview is pinned, always real, and cheap.
  - SA:121-124 "Live-preview refresh is expensive ... Coalesce bursts of config changes (slider drags, coverflow scrolls, per-character text edits) into ONE rebuild, and skip the rebuild entirely when the resulting config is byte-for-byte unchanged."
  - clackpad/FEATURES.md:577-579 "live keyboard preview — every change renders immediately on a real, non-typing instance of the keyboard driven by the same config JSON the IME uses."
P3. Label names the option; help is rationed to about 5 %.
  - clackpad/app/src/test/.../SettingsHelpGuardTest.kt:24 "Settings carries NO explanatory prose. Labels name the option; the only help is a small (i) on the handful of options where a wrong guess has consequences, and its text lives in a dialog, never on the page."
  - docs/SETTINGS_HELP_AUDIT.md:6 table: help texts 176 -> 7; "Kept help as a share of options 4.05%", limit 5 %. Enforced by a failing test (guard lists the exact allowed (i) set).
  - Fix by renaming, not by explaining: audit "Considered and dropped": "Clipboard ... renamed 'Clipboard history'; its 'Off' tile kept everything, so it is now 'Keep until cleared'".
P4. Show, do not describe: option tiles are renders of the real thing.
  - SA:1410 "A keycap whose height scales with the option, so the choice is visual."
  - clackpad/FEATURES.md:517 "Settings previews are generated by screenshotting the real keycap CSS".
P5. Do not make users type numbers they cannot guess: record them.
  - SA:8656 "(the calibrator) ... so they never have to guess a millisecond value" (double-tap window, long-press hold).
P6. Sliders show their live value and where the default sits.
  - SA:8767 "Right-aligned live value readout ('60%', '400ms')", the default tick at SA:8787, and the "Auto" chip that removes the pref (SA:8580). Test: SettingsSliderReadoutAndHoldCalibratorTest.kt header.
P7. Search is a synonym index, not a text scan of help.
  - SA:673 "Mac-style settings search: matches names, hints AND similar meanings ('vibration' finds Haptics, 'theme' finds colours). Results ... jump straight to the matching group." docs/SETTINGS_HELP_AUDIT.md section 4: "SEARCH_INDEX holds titles, section anchors and a bag of synonyms only, never help prose".
P8. Safe by default, undoable resets.
  - SA:9990 Restore Defaults "Your current settings are kept as a version-history checkpoint first". Export asks what to include (SA:9693 area). Import refuses wrong types (SA:9843+, FIX-CRASHES F4b comment).
P9. Free presets stay free; only "make it yours" is gated, and a locked row stays visible.
  - SA:951-953 "ONE choke point for every promised perk ... Free presets always stay free - only the 'make it yours' layer locks."; locked row = dimmed + PRO pill (SA:973-977).
P10. 44-48 dp touch floors are tested, not hoped for.
  - docs/ARCHITECTURE_NOTES.md:3473 section 19 "Language tile rows: the 44dp touch floor"; (i) test requires 48 dp.
P11. The preview and the real engine must not drift; parity is a known failure mode.
  - docs/ARCHITECTURE_NOTES.md:519 "Two independently-maintained JSON builders - already drifting" and "No schema or test asserts field parity". web-config/STUDIO.md:41 admits the Studio iframe no longer shows the native engine, and says so in a banner.
P12. One portable file format shared by app, Studio and flat form.
  - web-config/STUDIO.md:66 "Both pages share the same profile store and the same export/import JSON shape".

---------------------------------------------------------------------------------------------------
## 3. Side by side

Liftoff column from docs/handoff/settings_inventory.md (A1, A2, C1), docs/handoff/product_half.md section 1.2 and 4, docs/status w52-w56 notes, and code in feature-grid/.../SettingsOneUi*.kt.

| Aspect | Clackpad pattern | Liftoff today | Gap / note |
|---|---|---|---|
| Depth | 2 levels, inline choices (P1) | 15 categories; 68 distinct screens; 20 radio pages + 24 panes; max depth 4 (inventory C1) | Liftoff deeper; Rails already make 2-9 option picks inline (CFG-23), so the remaining depth is panes and Page choices (grey value + chevron) |
| Home size | 12 rows + 6 tiles | 15 rows in 5 groups, each with live 1-line summary | Liftoff better (summaries). Clackpad has none |
| Choice control | Car bar + real-render tiles | OneUiVisualChoice drawn cards for every 2-9 option pick (w54-settingsVisual.md) | Liftoff ahead on visual; Clackpad's strength is that tiles are real renders (screenshots of the real surface), Liftoff's are hand drawings (SettingsPreviews.kt) |
| Slider | Uppercase label, live unit readout, default tick, Auto chip | `OneUiSliderRow` (SettingsOneUiParts.kt:233): title, grey valueText right, tick haptics by band, 48 dp | Readout parity OK. No default tick, no reset-to-default or Auto state |
| Switch | On/Off pill | One UI switch rows | Equal |
| Stepper | only in grid editor | Stepper rows (rows/cols, rings, recents) | Liftoff richer |
| Numeric timing | calibrators record the value | Hot corner delay (slider), Controls hide after (stepper), Motion sensitivity (slider); no "try it" | Gap: no calibrator |
| Live preview | Pinned dock, always the real keyboard, collapse + auto-hide on scroll, 60 ms coalescing | Floating Preview window: live scaled capture of home, drag/resize/pinch/minimise, position per orientation (PreviewWindow.kt, SettingsPreviewDock.kt) | Liftoff ahead on window UX. Gap: it previews the home only; Radar, drawer disc, Comms, corners are not in it (see Q1). No auto-minimise while dragging a slider |
| Presets | 5 Quick themes at top of setup, selected ring computed by `matches()` | Theme packs (import/export/reset/undo pane at depth 2, Look page); onboarding style chooser; no built-in preset rail | Gap: no one-tap "Looks" |
| Profiles | Studio Profiles tab (New, Duplicate, Rename, Delete, Use); none on device | Workspaces (desktop), Themes, 2 export kinds, snapshots x5 | Gap: no named, switchable settings profile (see item 8) |
| WYSIWYG Studio | Browser page with real keyboard iframe + 9 tabs, same export format | None. Preview window is on-device only | Large optional gap; not recommended (Q3) |
| Search | Synonym bag per entry, flash header, no help prose | Title/hint/value/category scoring; highlights row 2.2 s; does not index pane contents; no synonym field; hidden aliases only launchpad and sessions (inventory A1) | Gap: synonyms and pane contents (also IA problem 18) |
| Help text | 7 (i) of 173 (4 %), guard test | Row summaries/hints under many rows (OneUiRowFrame summary = item.hint); info rows; hint text on panes; no budget or test | Gap: no help policy test. Not same trade-off: Liftoff summaries are also state ("Dark - Rounded icons") |
| Restore | Version history 2 slots + Restore Defaults (snapshots first) | Snapshots x5 (before every import, before deleting a page, after each stable start) in Backup pane; "Reset to default look"; no global or per-page reset | Gap: no Reset-this-page / Reset-all with automatic snapshot |
| Export | Asks what rides along; never exports secrets/entitlement; type guard on import | Export setup / settings only; Import snapshots first; some prefs files excluded (StateSchema.BACKUP_EXCLUDED_PREF_FILES) | Verify import type-guard and non-portable list (item 7) |
| Gating | Pro: visible locked row + PRO pill | LabGate hides Lab; (cond) rows vanish when their condition fails, so they also vanish from search | Gap: conditional rows disappear instead of showing disabled-with-reason |
| Naming | Short nouns; hint-free | Inventory C6 lists 16 naming inconsistencies; "Ground Control" leftovers | Clackpad is cleaner per row but less structured |
| Titles | Monospace 21-24 sp, left, back chevron | Centred caps Space Grotesk (owner spec) | Keep Liftoff |
| Rows | Plain icon rows; tiles boxed with 16 dp radius | Flat rows with 1 dp hairlines inset to text start, no boxes (w54-settingsLines.md) | Keep Liftoff (see conflicts) |
| Taps | 2 | 2 (switch/slider/rail), 3 (Page choice), 4 (deep panes) | Liftoff worse only on Page choices and panes |

---------------------------------------------------------------------------------------------------
## 4. Recommended changes (priority order, 11 items)

Paths: LF-app = app/src/main/kotlin/com/bos/sphere/ui/; LF-grid = feature-grid/src/main/kotlin/com/bos/sphere/feature/grid/.

1. **"Looks" preset rail at the top of Look (and as the first card row of a fresh Settings)**.
   What: 5-6 built-in whole-look presets (mode, accent, icon shape, icon colour, dock style, font, wallpaper, calm effect) drawn as OneUiVisualChoice cards; selected state computed by comparing the current prefs to the preset (Clackpad `QuickTheme.matches()`, SA:8161-8230). Tile art = a real capture of the home in that look (generate via the existing ScreenshotTest harness, Clackpad did the same from real CSS) rather than a hand drawing.
   Where: LF-app SettingsCategories.kt (Look category), ThemesUi.kt (theme pack fields already hold exactly these properties), core-data theme pack model, LF-grid OneUiVisualChoice.kt, SettingsPreviews.kt.
   Effort: M (reuse theme pack apply + "Undo last theme change"; presets = bundled .liftofftheme files).
   Why: 1 tap to a whole look (visual, fewer taps, presets). Conflicts: none; it is a Rail, not a page.

2. **Search: synonym bags and pane contents**.
   What: add `keywords` to each SettingItem (not shown; not in the hint), score them below hint; register Pane contents (Themes, Drawer shortcuts, Workspaces, Search providers, folders) as index entries with stable string ids; make a hit open the sub-page and flash the row. Copy Clackpad's "all words prefix-match" rule, which Liftoff partly has.
   Where: LF-grid SettingsIndex.kt, SettingsOneUiModel.kt (SettingItem), LF-app SettingsCategories.kt; ties to product_half IA problems 18 and 19.
   Effort: M.
   Why: fewer taps (type "vibration", "font", "dark"), reaches the 24 panes the index cannot see today. Conflicts: none.

3. **Default tick, "Default" reset and consistent units on sliders**.
   What: `OneUiSliderRow` draws a small tick at the default and shows a "Default" text action (or double-tap the readout) when the value differs; keep the grey live readout; add units to every valueText (%, dp, s). Add the Clackpad "Auto" state where a pref is derived (Glow, Icon size, density: absent = follow).
   Where: LF-grid SettingsOneUiParts.kt:233 (OneUiSliderRow), SettingItem.Slider (needs `default`), call sites in SettingsCategories.kt (iconScale, iconSpacing, gridDensity, Comms size).
   Effort: S.
   Why: live readout and default visibility; zero extra taps to undo a drag. Conflicts: none (flat, text action, no box).

4. **Reset this page / Reset all, each with an automatic snapshot**.
   What: Backup already snapshots before imports. Add a text row "Reset this page to defaults" at the bottom of each category page and "Reset all settings" in Backup, both taking a snapshot first and offering Undo in the confirm result (Clackpad: SA:9990 and Version History).
   Where: LF-app BackupPane.kt, SettingsCategories.kt (per-category default map), LauncherPrefsStore.
   Effort: M (need per-category default key lists; RetiredPrefKeys already shows the key inventory).
   Why: presets/profiles safety net; lets the owner experiment. Conflict check: confirm dialog is allowed (owner: "dialogs only for confirmations"). Stack buttons as primary-on-top (coloured box "Reset"), secondary below as plain text, to honour the recorded rule (see product_half 1.2 item 4; do not copy Clackpad's side-by-side AlertDialog).

5. **Help diet guard test plus move row hints to state**.
   What: add a `SettingsHelpGuardTest` equivalent: every row's summary is either a state value (like "Dark - Rounded icons") or at most a few words; allow-list of rows with prose; cap prose rows at about 5 % of rows. Where a row needs consequence help (Dock default per tier, Restore pages, Work profile), put it on the destination page header, not in an (i) popup.
   Where: new test in feature-grid/src/test; LF-grid SettingsOneUiParts.kt (OneUiRowFrame summary), SettingsOneUiModel.kt (SettingItem.hint).
   Effort: M (writing the allow-list is the work).
   Why: calmer, visual-first pages. Conflict: Clackpad's (i) opens an AlertDialog. Liftoff rule = full pages not popups, dialogs for confirms and short text only. So do NOT port the (i) dialog; use a page header line or the destination page.

6. **Preview parity test and slider-friendly preview behaviour**.
   What: (a) a test that every setting key which changes the home is represented in the Preview capture (Clackpad's drift lesson, ARCH_NOTES 519, "no test asserts field parity"); (b) coalesce preview refresh (Clackpad 60 ms + skip when unchanged) while dragging; (c) auto-minimise or dim the Preview while a slider is being dragged if it covers the row; remember per page. Extend the preview to Radar, disc and Comms where those pages are open (currently home only).
   Where: LF-grid PreviewWindow.kt, PreviewWindowModel.kt, SettingsPreviewDock.kt, IndicatorPreview.kt.
   Effort: M to L (new surfaces in the capture are L).
   Why: WYSIWYG for settings whose effect is off the home (Radar layout, drawer ring, Comms size).

7. **Import/export hardening and "what rides along"**.
   What: check Import against Clackpad's rules: wrong-typed value refused (SA:9870 type guard), a non-portable key list enforced on BOTH export and import (SA:9615), size-bounded read, and export asks what to include (Comms history, hidden apps list, per-app rules are private). Surface the choice as radio cards in the Backup pane, not a dialog.
   Where: LF-app BackupPane.kt, StateSchema (BACKUP_EXCLUDED_PREF_FILES), LauncherPrefsStore.
   Effort: S (audit) to M (UI).
   Why: profile portability without leaking private state. Conflicts: none if done as a page.

8. **Named settings profiles (optional, after item 1)**.
   What: save the current Look + Home + Gestures as a named profile, switch from Backup page and the Home quick sheet; Clackpad Studio has New / Duplicate / Rename / Delete / Use. Profile file = the existing "Export settings only" JSON, so profiles, backups and presets share one format (P12).
   Where: LF-app BackupPane.kt, HomeQuickSheet.kt, LauncherPrefsStore, core-data.
   Effort: L. Overlaps Workspaces (desktop) and theme packs, see Q2.
   Why: presets/profiles; one-tap switch (Work / Calm / Desk).

9. **Calibrators for time-based settings**.
   What: "Try it" area on the page for Hot corner delay, Controls hide after, Long press, Double tap (gesture window), Motion sensitivity: tap/hold/shake in the box and the measured value is written, shown as the slider value. Clackpad: SA:8656 (dblTapCalibrator), SA:8702 (holdCalibrator).
   Where: LF-grid SettingsOneUiParts.kt (new row kind), SettingsCategories.kt rows for corners/gestures/motion.
   Effort: M.
   Why: live feedback in place of blind numbers.

10. **Disabled-with-reason instead of vanishing conditional rows**.
   What: keep (cond) rows visible but dimmed with a one-line reason ("Needs Circle layout", "Pages is Off"), still searchable; Clackpad shows locked rows as dimmed stand-ins (SA:973). Liftoff already writes "not on <style>: reason" for gestures; extend that to Page N, Clock, Dock, Hot corner delay.
   Where: LF-grid SettingsOneUiModel.kt (item `enabled` + `reason`), SettingsOneUiSections.kt (headers no longer vanish with a row), SettingsIndex.kt.
   Effort: M.
   Why: discoverability (the owner already complained that apps/widgets were "impossible to find"), fewer taps hunting.

11. **"Finish setup" card at the top of Settings, only while something is unfinished**.
   What: single row (not a box) above the groups: default home, notification access, usage access, DND access; disappears when all done. Clackpad: Setup checklist replaces the Dictionary card until complete (SA:683-689) and health card shows only when real.
   Where: LF-app SettingsCategories.kt (System & sound, Comms > Access pane), LF-grid SettingsOneUi.kt root; links the scattered Access rows (product_half problem 12).
   Effort: S.
   Why: fewer taps for first run, one place for permissions.

### Conflicts with the owner's recorded preferences (product_half.md section 1.2)
- Visual cards, not text: all items honour this; item 1 adds more cards. Clackpad's `carSelect` icon bar and `seg` 2-letter codes are text-ish and weaker than Liftoff's drawn cards, do NOT port them.
- Flat hairline rows, no boxes (w54-settingsLines.md): Clackpad home uses boxed 16 dp tiles and rounded cards. Do NOT port the tile grid or boxed cards; items 4, 10, 11 are plain rows.
- Pages not popups: Clackpad's (i) AlertDialog, export-options AlertDialog and Restore Defaults dialog are popups. Port the behaviour, not the dialogs (items 4, 5, 7).
- Primary action stacked on top, secondary as text below: Clackpad dialogs put buttons side by side (Include / Leave it out / Cancel, SA:9693 area). Do not copy; stack per owner rule (item 4). Note the existing owner-vs-One-UI conflict (product_half problem 21) is still open.
- Pinned preview vs floating Preview: Clackpad's dock-at-bottom conflicts with Liftoff's floating window the owner chose; keep floating.
- "Keep only the newest specified option": items 1 and 8 add concepts; ask first (Q2).
- Pro gating itself is not applicable to Liftoff (no purchase); only the "visible locked row" idea (item 10) transfers.

---------------------------------------------------------------------------------------------------
## 5. Open questions for the owner (max 3)

Q1. Should the floating Preview also show Radar, the drawer disc, Comms and corners (they affect many settings but are not on the home), or stay home-only? This decides whether item 6 is M or L and whether Radar/Comms pages get a live preview.
Q2. Do you want named, switchable setting profiles (item 8: Work / Calm / Desk), or are built-in Looks (item 1) plus theme packs and Workspaces enough? Profiles overlap Workspaces and would add a concept you may not want ("keep only the newest option").
Q3. Clackpad has a browser WYSIWYG Studio (web-config/studio.html) that authors a profile file off-device and imports it. Do you want an equivalent for Liftoff (a web page that edits the same JSON and previews the home), or is the on-device Preview window all you need?

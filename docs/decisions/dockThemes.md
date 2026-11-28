# Dock styles, theme packs and the theme store hook (wave 39)

Owner request: "I want to have dock styles - 1. Transparent 2. 3D. Custom theme uploads? A link to the asystemofcells website where people can get
new themes and publish their own, so that they can browse themes in a theme store?"

## 1. Dock styles

Exactly three, one persisted setting (`DockSettings.style`, stored as `dock.style` in the workspace file, so backup and export carry it with the
rest of the dock section; an older file without it reads as Standard, an unknown value from a newer build reads as Standard):

| Style | What is drawn |
| --- | --- |
| Standard (default) | The existing dark rounded pill, byte for byte the same drawing as before |
| Transparent | No panel and no border. A soft dark halo (radial gradient, `DockStyleTokens.HALO_*`) behind each icon; the home glyph, the plus ring and the divider get a dark disc or strip behind a white glyph. The active dot gets a dark ring |
| 3D | A rounded slab: vertical face gradient, inner shadow band under the top edge and a darker lip at the bottom, a bright highlight stroke fading down the sides, a bevel stroke (light on top, dark underneath), and a contact shadow of six stacked translucent rounded rectangles that falls down for the bottom dock and inward for a side dock. Icons are raised 4 dp, each with a small squashed oval shadow and a faint mirrored reflection (an explicit layer with a gradient mask, no bitmap) on the shelf. Light and dark variants follow the UI theme |

- Everything is Canvas and graphicsLayer; no infinite animation, no timer, so the motion policy and battery saver have nothing to gate.
- Touch targets, press feedback (the existing ripple), the long-press menus, the active dot, TalkBack actions and reorder are the existing code;
  the styles only change what is drawn. The reflection is not a semantics node (cleared), so a screen reader hears each icon once.
- The dock node's state description is "Dock style: X". The dock stays optional: nothing here turns it on or off, and with it off (or empty) no
  style draws anything.
- Where to change it (W55, docs/decisions/dockSheet.md: the two places below were replaced): the dock's sheet (Dock style cards) and Settings > Desktop & dock > Dock (cards under a live preview); the long-press menu entry and the Home screen row are gone. Was: the dock's long-press menu ("Dock style", a dialog with the three choices) and Ground Control, Home screen, "Dock style"
  (three radio rows, each with a small preview drawn by the dock's own code, so what the preview shows is what the dock gets).
- Landscape side docks, RTL and large font scale: the rail's layout is unchanged; tested in the three styles.
- Icon sizes and icon shapes: the dock draws the icon composable it is handed at 40 dp, as before; the styles do not touch them.
- The halo is tuned by constants (`HALO_ALPHA`, `HALO_MID_AT`, `HALO_RADIUS`); a white glyph reaches 3:1 over every sample wallpaper including pure
  white (tested in `DockStyleTokensW39Test`). The halo is deliberately visible on a light wallpaper; if it reads too heavy on a device, lowering
  `HALO_ALPHA` trades legibility for softness.

### Not built

- The optional gyroscope parallax/tilt of the 3D shelf: skipped. It would need a sensor listener with its own reduced-motion, battery-saver and
  lifecycle handling for a purely decorative gain; the owner marked it optional and default off.

## 2. Theme packs

A small versioned JSON file, `.liftofftheme` (MIME `application/json`, `.json` also accepted), format `liftoff-theme`, `formatVersion` 1:

```
{ "format": "liftoff-theme", "formatVersion": 1, "name": "Dusk", "author": "optional", "version": "optional",
  "settings": { "themeMode": "dark|amoled|light", "accent": "#RRGGBB", "iconShape": "original|rounded|squircle|circle|square",
                "iconTint": "none|accent|mono", "dockStyle": "standard|transparent|3d",
                "wallpaper": { "kind": "solid", "color": "#RRGGBB" } | { "kind": "gradient", "from": "#RRGGBB", "to": "#RRGGBB", "angle": 0..360 } } }
```

- **Untrusted data.** Parsed with `org.json`, never executed; only the keys above are read, any other key (anywhere) is ignored and never survives a
  re-export; every value is checked against a closed set or a strict shape. Hard limits: 64 KB (bytes, not characters), strings at most 80
  characters with no control or format characters (a right-to-left override cannot make a name read backwards), colours `#RRGGBB` or `#AARRGGBB`
  (alpha ignored, always opaque), gradient angle 0..360. No picture, video, file, system or signal wallpaper, link or number outside its range can be
  carried (`kind` other than solid or gradient is a refusal). A pack with no recognised setting is refused ("does not change anything"). A newer
  `formatVersion` is refused with its own message. Every problem found is listed (up to eight), not only the first.
- **Apply.** Only the carried settings change: UI mode, accent, icon shape, icon colour, dock style, and the Glance wallpaper (the pages follow it while
  "Pages use the same wallpaper as Glance" is on; a separate Pages wallpaper is never touched). Nothing else (apps, layout, pictures) is read or written.
- **Preview before anything happens.** The pack's name, author and version, then a row per carried setting with before and after chips (mode, accent
  swatch, icon shape, icon colour, dock style, wallpaper swatch), "stays the same" where it would not change, and Apply / Cancel.
- **Undo.** Apply and Reset keep the previous look (every field a pack can change, including a wallpaper kind a pack cannot carry, such as a picture
  or the signal wallpaper) for one in-memory undo (`ThemeUndo`). It is deliberately not stored: a restart forgets it, and a theme pack itself is
  never stored anywhere (no preference, no file).
- **Export.** "Export current look" writes the current look as a pack through `ActivityResultContracts.CreateDocument` (no FileProvider, no share
  intent, no new permission). A wallpaper that is not a solid colour or gradient is left out and the confirmation says so.
- **Import.** `ActivityResultContracts.OpenDocument`, read on `Dispatchers.IO`, at most 64 KB + 1 byte, then the same parser. The pickers sit behind the
  `ThemeFiles` seam so the flow is tested without a picker.
- **Rows.** Ground Control, Look, "Themes" (Import theme, Export current look, Reset to default look, and "Undo last theme change" while a snapshot
  exists). "Reset to default look" is dark mode, the default accent, rounded icons, no icon tint, the standard dock and the default (signal) Glance wallpaper.
- **Opening a file from outside.** `ThemeImportActivity` handles `ACTION_VIEW` for `content:` URIs of MIME `application/x-liftofftheme` or a path
  ending `.liftofftheme`; it is not browsable from web pages, takes no new permission, reads only the Uri it was handed and lands on the same
  preview (`ThemeImportFlow`). It does not register for the generic `application/json` MIME: that would offer Liftoff for every JSON file on the phone.
  A file manager that hands over a `.liftofftheme` content Uri with a generic type and a path without the extension will not offer it; Import
  theme in Ground Control still works for it.
- **State classification.** The only persisted state this adds is the dock style (inside the existing `dock` section: exported, backed up, restored,
  additive within `WorkspaceJson` schema 1, no version bump). The pack and its undo are not stored; `CFG_01_state_noThemePackIsStoredAnywhere` keeps
  the pack sources free of any storage call.

## 3. The theme store (owner's idea, future work)

The owner's idea: a link to the asystemofcells website where people can get new themes and publish their own, so they can browse a theme store.
The owner answered the open question: **leave the link out for now.** So there is no URL, no browse-store row and no network code. The code is shaped
so a later change is one constant: `ThemeStore.ADDRESS` (core-data, `DockStyle.kt`) is null, `ThemeStore.isConfigured()` is false, and the hook is
documented there. When the address is known: set it, add the browse row to the Themes pane when `isConfigured()`, and open it as a plain external link
(not inside the launcher, and nothing from the site is fetched or applied automatically: a downloaded pack still goes through Import, validation and
the preview). Publishing themes is the site's business, not the launcher's. Address: unknown. Tested: `ThemeStore` is null and the Themes pane has no
store, browse or link text.

## Tests

`ThemePackW39Test` (parse, limits, unknown keys, colours, wallpaper kinds, closed sets, errors, round trip, apply, preview changes, apply and undo,
reset, the store hook), `DockStyleStateW39Test` (backup carries the style; no pack storage), `DockStyleTokensW39Test` (halo contrast, slab tokens,
shadow direction, JSON), `DockStyleUiW39Test` (nodes, 48 dp targets, active dot, press, long press, the dock menu entry and dialog, the radio chooser,
pixels that make each style, light and dark slab, RTL, large font, 12 dock screenshots and the chooser), `ThemesW39Test` (rows, fake picker import flow,
preview, every error state, export, reset, undo, the view-a-file flow, the Dock style pane, the manifest entry, preview and rows screenshots).

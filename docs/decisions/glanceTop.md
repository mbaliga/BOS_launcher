# glanceTop (wave 59, CFG-75 to CFG-79): the music line's fade and width, and the Hotspots editor

Owner feedback, with two screenshots of the Glance home over a light, busy live wallpaper (a microscope scene): the music line at the top shows solid black blocks at both ends of the scrolling title, and it does not run full width although there are no hotspot icons at the top. Owner (verbatim): "See this issue, please fix it. And I think we should have an option to have full width when the top icons are removed. I think instead of four or two or whatever we should just have a live preview, a wysiwyg editor to turn on or off the hotspots and configure them."

Nothing here was run on a device. Status: `docs/status/w59-glanceTop.md`.

> This note supersedes: in `glanceFixed.md` (wave 38), "filtered to the corners the Hotspots chips enable" and "the Hotspots chips (Four, Two, One, None)" (one editor now turns each corner on or off) and the music line's "marquee with faded edges" painted over the surface colour (the fade is an alpha mask now); in `glance.md` (wave 16), the Hotspots chips; in `homeSheet.md` (wave 34) and `homeMenuVisual.md` (wave 51), the Hotspots cards of the Home sheet (a Hotspots tile opens the editor); in `settingsTabs.md` (wave 57), "hotspots and the four hotspot switches" in Home > Glance and "the four corners" rows in Home > Corners (the editor is the first block of Corners); in `previewAll.md` (wave 57), the "<corner> hotspot" rows of the Glance topic (gone with the switches) and the Corners topic ringing all four corners (it rings the corners that are on); in `homePages.md` (wave 26), "the user's two, three, one, none" hotspots as chips. The policy "keep only the owner's newest option" applies: the presets have no second way in.

## 1. The black blocks (CFG-75)

Cause: `NowPlayingLine` faded its clipped ends by drawing a gradient from a SOLID colour (`edge` = black on a dark surface, a pale grey on a light one, passed from `FixedHomeChrome`) to transparent over the text. On the Glance the line sits on the wallpaper, not on a black surface, so over a light wallpaper the gradient was a black block at each end.

Fix: the text itself fades to transparent. `Modifier.fadeClippedEdges` puts the text in an offscreen layer (`graphicsLayer(compositingStrategy = Offscreen)`) and draws a horizontal gradient from opaque to transparent with `BlendMode.DstIn`, which only changes the text's alpha. No colour is painted, so a light, dark, live or picture wallpaper shows through unchanged. The `edge` parameter and both call sites' colours are gone. Only a clipped edge fades (`MarqueeFade.edges`): the end while the title overflows, the start once the title starts to move (after the marquee's 1.2 s wait), never a line that fits (it is centred whole) and never the reduced-motion line (an ellipsis). Right to left swaps which side is the start.

Other text fades checked: the scroll home's edge fades (`ContinuousHome.edgeFades`) and the Files chip bar already used the same `DstIn` mask; the Comms bars' fade fills are fills, not text over a wallpaper; the settings title has no fade. No other colour-edge fade over arbitrary backgrounds was found, so nothing else changed.

Tests: pixel tests draw the line over a white and over a patterned pale background, in the dark and the light theme, and compare the outermost columns of both clipped ends with the same picture without the line (`GlanceTopFadeW59PixelTest`: no darker than the background, mean difference under 6 percent; window copies with `PixelCopy`, because a software `View.draw` does not honour the offscreen layer in Robolectric). With the old gradient they fail (checked by putting it back: luminance 0.76 to 0.96 below the background).

## 2. Full width (CFG-75, CFG-76)

Cause: the line ran edge to edge only when neither top corner was ENABLED (`titleWidthDp(topLeft, topRight)` with `glanceOn`). A corner that is on but holds nothing draws nothing and still reserved 280 dp's worth of room.

Rule now: a top corner reserves room only when it DRAWS something: on AND holding an assignment (`GlanceHotspots.draws`: anything but an empty slot: an action, an app, a shortcut, a palette, a complication, a cluster or a widget peek) at the screen corner under the current rotation (`GlanceHotspots.drawnOnScreen`, the same physical mapping as `enabledOnScreen`). The home passes that set to `FixedHomeChrome`.

The owner's option: **Music line width** (Settings > Home > Glance, under Show music; dimmed with "Show music is off" while the line is off): two drawn cards, **Fit around corners** (default, the rule above) and **Full width** (always edge to edge with the 12 dp margins). With Full width the top corner icons sit over the line's ends: at a drawn top corner the whole line (note glyph and text) is transparent for its outer 46 dp and fades in over the next 28 dp (`UNDER_CORNER_CLEAR_DP`, `UNDER_CORNER_FADE_DP`, the same alpha mask), so the icon reads clearly; the corners' touch squares are drawn above the line, so a tap on a corner runs the corner (tested). Stored as `musicLineWidth` (enum name) in `launcher_prefs`, exported and imported with the settings file and the backup by the codec's reflection, reset with the Home tab, unknown values read as the default. Studio: a field in the regenerated schema, and its drawing of the music line follows both the drawn rule and the width, with the same alpha-mask fade (an SVG mask). The app's floating Preview shows it: its Glance topic draws the real fixed chrome.

## 3. The Hotspots editor (CFG-77 to CFG-79)

Removed (owner policy: only the newest option): the Hotspots preset choice (Four, Two (bottom), One, None; `HotspotPreset`, `GlanceHotspots.preset/presetOf`, the `SettingsArtHome.hotspots` art set) and the four "<corner> hotspot" switches in Settings > Home > Glance, the four corner rows of Settings > Home > Corners, the Home sheet's Hotspots cards, the page editor's Settings tab hotspot switches, and the Studio field's preset buttons (its per-corner checkboxes stay: they are the plain on/off of each corner).

Migration: none needed and none written. A preset was never stored: it was a reading of the stored set of corners (`glanceHotspots`, physical corners, default all four). The set is read and written as it is, so every install keeps exactly the corners it had on (`GlanceTopW59Test.CFG_77_theRemovedPresetsLeaveTheStoredHotspotSetAsItIs`). The Calm profile start still asks for the two bottom corners, now as a plain set.

The editor (`HotspotsEditor`, feature-grid; `HotspotsEditorFor` wires it to the preferences):
- **A live miniature of the home** at the phone's aspect: the real fixed wallpaper from the home capture (when the orientation in view is the device's), then the real corner layer (`CornerLayer`, the chosen corner style, icon size, icon type and glyphs) composed at the home's own size and scaled down (`PreviewSandbox`), one layer per corner so an off corner is dimmed. Nothing in the miniature can be touched or focused, and its corner layer registers no focus nodes (`launcherItem` is a no-op inside a preview sandbox, so it never takes or drops the real home's corner ids).
- **Tap a corner**: on or off. Off is the dimmed item with a slashed outline. **Long press**, or **Edit** in the row below: the existing corner editor page for that corner (action, app, shortcut, palette, cluster, widget, nothing). The selected corner **pulses** (a ring on its glyph; still and thicker under reduced motion: the infinite transition reads the motion policy).
- **The row below**: the selected corner's name ("Top left"), what it holds, an On/Off switch and Edit; then one line of help.
- **Accessibility**: each corner is a switch named "<corner> hotspot, <item>" with On or Off as its state, a long-press action and a custom action "Edit". **Keys**: arrows move between the corners (physical directions, the miniature is laid out left to right in every language), Enter toggles, E edits.
- **Right to left** keeps the physical corners (hotspots stay physical: `docs/decisions/defects58.md`); the names are the translated corner names.
- **Orientation**: in landscape the miniature is landscape and each screen corner acts on the physical corner under it (with "Follow device rotation"). With "Separate corners for landscape" a Portrait / Landscape control picks the orientation being edited: the miniature turns, shows that orientation's assignments, and Edit writes that set (the home's corner editor gained an explicit target set, `editCornerLandscape`). The on/off set stays ONE set of physical corners for both orientations (as before; a second set would be new state the owner did not ask for).

Where it is:
- **Settings > Home > Corners**, first block (a choice row whose control is the editor, value "N of 4 on"; searchable by the corner names); Corner style, icon size and type follow as cards and rows as before.
- The Home tab now has 80 rows in its fullest state (was 87 in `settingsTabs.md`: the Hotspots choice, the four hotspot switches and the four corner rows out; Music line width and the editor in).
- **The Home sheet**: a Hotspots tile (where the cards were) opens the same editor as a full page (`HotspotsEditorPage`, the One UI page frame); its corner editor opens above it.
- **The floating Preview**: the Corners topic rings the corners that are on and pulses the corner selected in the editor (`PreviewFocusState.onSubject`); the "Hotspots" row maps to the Corners topic.

## Not done / device only
- The look of the fade over a real live wallpaper, the miniature with a real wallpaper capture, TalkBack and a hardware keyboard on a phone, and touch arbitration between a corner and the full-width line on a real screen: device checks.
- The page editor's Settings tab has no link to the editor (the Home sheet tile and Settings are the two ways in).
- In the dark theme the music line's light ink over a very light wallpaper reads weakly (the ink, not the fade; unchanged here, see the screenshots).

## Undo
Revert the wave-59 commit: the presets, switches and the colour fade come back; `musicLineWidth` stays stored and is ignored.

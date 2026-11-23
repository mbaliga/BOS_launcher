# Dock magnification and the 3D shelf (wave 56)

Owner request: the dock should optionally magnify like the Mac dock, and the 3D style should look like the classic 3D dock (reference picture: a perspective glass shelf, glossy icons standing on it with reflections, a dot under running apps, a dark rounded name bubble with a pointer).

## Setting

`DockSettings` gains `magnify` (Boolean, off) and `magnifyStrength` (Subtle 1.3x, Medium 1.6x default, Large 2.0x). Stored in the dock object of the workspace file (`dock.magnify`, `dock.magnifyStrength`), so export and import carry them with the dock section; an older file has neither and reads as off; an unknown strength reads as Medium. No preference key, so nothing for the retired-key audit. `DockOptionsBody` (sheet and Settings > Dock) shows a switch row and, while it is on, three drawn radio cards whose art uses the same kernel as the dock. The all-settings test lists seven fields and the controls.

## Behaviour (`DockMagnify.kt`)

- Kernel: raised cosine over R = 2.5 pitches (60 dp). Scale = 1 + (f-1)w(d). Every member is pushed by the integral of the extra width between it and the pointer, so the stretch is continuous, never closer than at rest, and the icon under the pointer stays under it. Icons grow from their outer edge (the shelf for the bottom dock, the screen edge for a side dock, so they grow towards the centre) and lift up to 6 dp. Members that cannot scale (hosted widgets) are only pushed.
- Room: a dock that fits at rest asks for slack at both ends and headroom (padding inside the scroll viewport) only while touched, capped by what the parent allows. If the spread still does not fit, the row is drawn closer around the pointer (down to 0.72 of the pitch) and then grows less; it is never shifted off the pointer. A dock that already scrolls just spreads and the viewport clips it.
- Input: one pointer-state, read in graphics layers only (no recomposition per move; tested). A mouse or stylus magnifies on hover. A finger magnifies after the touch slop or 120 ms, so a plain tap never sees the effect and launches exactly the pressed icon. Focus (arrows, D-pad) moves the effect to the focused icon. Long press, the item menu, reorder drag and the dock sheet are the existing handlers; the tracker observes in the initial pass and consumes nothing. While the long-press menu is open the bubble hides.
- Motion: a spring (0.72 damping) for the amount; reduced motion or animations off applies it instantly with no overshoot. Edit dock: no magnification.
- Bubble: dark rounded label with a pointer above the icon (beside it for side docks), clamped to the window, hidden from TalkBack (the icon already says its name). Shown with magnification in every style, and in the 3D style on press or hover even with magnification off.
- Hit testing: the slot layers are the outermost modifier, so Compose hits the magnified geometry; the rail's long-press mapping uses the kernel's `indexAt`.

## 3D look

The old rounded slab, its contact shadow and raised icons are deleted. The shelf is drawn in a canonical frame (x along the dock, y back to front) and turned for side docks: translucent floor trapezoid (back edge pulled in by 0.42 of the depth), darker lip, bright back-edge line, slanted lit ends, inner shadow, a glow, and a shadow below. Slots are 10 dp deeper on the shelf side (the rail's padding there is dropped, so the rail is no taller) to hold the reflection: the icon mirrored across its foot, faded, clipped to the floor, and anchored to the floor while the icon grows. A running (recently used) app gets a small dot on the lip. The slot's ripple is off in 3D (a ripple over the whole slot reads as a dark box on glass). Glass tint follows the light and dark theme and stays translucent over the wallpaper. The style card and the Settings live preview use the same drawing (glossy stand-in tiles).

## Limits

Real app icons are not given a gloss. With the host's side margins, a dock of more than about three icons scrolls on a phone; DockLayer reduces the margins from 76 to 20 dp while magnification is on. Touch feel, 60 fps and a real mouse hover are device-only.

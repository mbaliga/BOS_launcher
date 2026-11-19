# Wave 34 wallpaper (owner: picture, GIF, video, system live wallpaper, spider-sense alert)

| ID | Status | Note |
|----|--------|------|
| HOM-16 | done (Robolectric and JVM only) | One Wallpaper picker, five kinds: Solid colour, Gradient, Signal wallpaper (the old live one, id `live` unchanged), System live wallpaper, Picture. Picture from the Photo Picker (seam `MediaPickerFactory`) copied to `files/wallpapers`, Fill/Fit, dim 0/25/50. Tests: `WallpaperPictureTest` (core-data, pure), `WallpaperPictureUiTest`, `WallpaperPictureRenderTest`, `PictureImportTest`, `WallpaperPaneUiTest` (updated) |
| HOM-16 gif | done (clock and UI tested; real decoding device-only) | Still / Animated, Play once / Loop, Continuous / Bounce; `GifTimeline` clock with real delays; frame cache bounded at 48 MB (GIF via `Movie`), streaming `AnimatedImageDrawable` for Once/Continuous beyond that, "Too large to bounce, looping instead"; animated WebP loops instead of Bounce |
| HOM-16 video | done (UI and policy tested with a fake engine; playback device-only) | Video via the same picker, `MediaPlayer` on a handler thread into a muted `TextureView` over the poster; Still / Play once / Loop, **no Bounce** (owner) |
| HOM-16 warning | done | Dialog "Animated wallpapers use more power" before any animated mode or video is switched on (Use anyway / Choose a still instead / Cancel), asked once per file (`powerOk`), standing line in the pane, "uses more power" in the row summary |
| HOM-16 system | done (device confirms the look) | Transparent surface (`SystemWallpaperLayer`), `GlanceSurface` no longer paints an opaque base under it, chooser button with fallbacks, active live wallpaper name, dim chips |
| PAL-24 | extended | Spider-sense alert (`SpiderSenseAlert`) over every kind and on Radar Dimmed and True black; before, only a signal wallpaper with an item drew it. Tests: `PAL_24_` in `WallpaperPictureRenderTest` (differing pixels with and without signal for each kind; screenshots `wallpaper34-render-alert-*.png`, `radar-*`) |
| NFR-14 | ok | Animated pictures: reduced motion, battery saver, app not resumed/focused all fall back to or hold the still; the frame loop uses `infiniteFrameNanos` (infinite-animation policy), no `rememberInfiniteTransition`; the alert shares the radar clock and the reduced-motion still frame |
| NFR-18 / CFG-03 | ok | The picture file is local-only: `wallpapers` excluded from system backup (both xml files), its prefs are device-bound (`StateSchema.WALLPAPER_PICTURE_KEYS`, never exported or imported); fit, dim, mode and kind travel; no schema bump (additive keys in launcher_prefs) |

## Old tests changed
- `WallpaperModelTest`: the "three kinds only" assertion is now the five ids. `WallpaperPaneUiTest`: the type chips are the five labels and "Live wallpaper" became "Signal wallpaper". `StateCodecTest.CFG_01_everySettingIsCarried`: variants for the new `PictureSpec` field (fit, dim, mode travel; file and description are device-bound on purpose) and a +25 Int step so the dim fields are exercised.

- `BackgroundPaneUiTest`: the test host is 1500 dp tall instead of 1200 (the kind chips now take two rows, which pushed slot 2 out of the viewport; behaviour unchanged).

## Verified (Robolectric / JVM)
Clock per mode, policy fallbacks, size rules, GIF reader on synthetic bytes, pane flows with fake picker, importer and system access, dialog flow, standing warning, fallbacks and notes, Fit/Fill/dim/missing-file drawing, frame player in Once, Continuous, Bounce (one cycle) and paused, the system layer transparent, alert pixels over each kind and on Radar, no video player created on Radar. Screenshots were read.

## Device-only / not matched
Real GIF/WebP/MP4 decoding and playback, `Movie` frame fidelity, the Photo Picker, `WallpaperManager.getWallpaperInfo()`, the system wallpaper seen through the window (including a real live wallpaper), battery/heat behaviour, frame pacing. `ImageDecoder` is not available under Robolectric, so the still importer is tested through a test decoder. The Bounce test clock samples one cycle only (the Compose test clock steps 16 ms frames and drifts over later cycles); the exact clock is tested in pure JVM.
- `WallpaperRowsW26Test`: the Glance row summary says "Signal wallpaper" (the kind was renamed from "Live wallpaper"). `PackageRemovedW26SmokeTest` failed once in the full app run: the known flaky.

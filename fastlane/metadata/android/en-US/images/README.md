# images/

Sizes and check commands: `Personal-Tracker/store/ASSET_SPECS.md`.

## Needed, none present yet

- `icon.png` 512x512 no alpha · `featureGraphic.png` 1024x500 no alpha
- `phoneScreenshots/` — 2 to 8, **landscape 16:9** (1920x1080), no alpha.

## Shoot in landscape

This is a landscape-first launcher. Portrait screenshots would misrepresent it and
throw away its best frame. Play accepts 16:9 landscape.

1. The coverflow equator with the magnification lens at front centre.
2. The all-apps globe mid-rotation.
3. Type-to-search with live results.
4. The pull-down hub.

The magnification lens is the whole idea, and it only reads at a wide aspect. Give
it the first slot.

```sh
adb exec-out screencap -p > shot.png
magick shot.png -background black -alpha remove -alpha off phoneScreenshots/01.png
```

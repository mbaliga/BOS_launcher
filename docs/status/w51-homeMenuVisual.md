# Wave 51: homeMenuVisual (HOM-21)

| ID | Status | Note |
| --- | --- | --- |
| HOM-21 | built | The Home quick sheet shows every choice as a drawn card (Pages, Order, Hotspots, This page, Transition) with the selected one ringed and checked, and three entry tiles (Add apps, Add widgets, Edit home) on top wired to the edit tray's entry points. Pills deleted. Wallpaper and Ground Control rows kept. |
| HOM-22 | built | Pages and Order cards set the same prefs as the old pills (tags `quick.pages.*` kept, new `quick.order.*`). |
| HOM-23 | built | Hotspots cards set the same presets (`quick.hotspots.*`). |

## Tests
- New `HomeQuickSheetVisualTest`: entry tiles call their callbacks; every card of Pages, Order, Hotspots, Align, Make this home and each Transition changes the setting and exposes the selected state; label and radio role in semantics; 48 dp minimum.
- Changed `HomeQuickSheetGlanceTest`: "Edit home" and `quick.edit` are no longer asserted absent (it is a tile now); Order tests click the new tags; the body takes the three tile callbacks. Old pill-specific behaviour has no remaining test.
- `HomeQuickSheetShotsTest` unchanged; its screenshots (`app/build/screenshots/home-sheet-*.png`) were read: dark, RTL and page tools look right (check badge covers a corner of the Order art, accepted).
- Strings: `L10nW51homeMenuVisual.kt` (one new string, "Add widgets", 8 languages).

## Not verified
- Nothing was run on a device. The Add apps and Add widgets tiles are wired in `HomeScreen` and covered only as callbacks; the real flow (Pages Off turns Pages on, then the picker opens) was not exercised end to end. The looped animation bonus was not built.

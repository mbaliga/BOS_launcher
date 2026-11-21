# Wave 35: signalEffects status

Nothing here is device-confirmed. JVM and Robolectric only. Screenshots read: `feature-grid/build/screenshots/signal35-*.png`.

| ID | Status | Note |
| --- | --- | --- |
| PAL-24 | done in JVM and Robolectric | Four effects in two opposite groups, one nullable choice each (invalid pairs unrepresentable); Show = Signal or Battery; alert effects invisible when healthy, growing as the value falls, urgent (red, full, 1.2 s pulse, dense comic ring) when lost; reduced motion urgent is one static full-strength frame; alert also over Radar Dimmed and True black. Spec.md row text not updated (see decisions/signalEffects.md) |
| HOM-16 | done | the alert sits in the home Background() on Glance and pages (as the calm effect did) and in Radar through the new `alertLayer` slot |
| CFG-01 | done | `indCalm`/`indAlert` exported by the reflection test; `indEffects` migrated once; `indEffects`/`indInvert` retired (purge marker `retired_pref_keys_w35`); `indInvert` dropped on import |
| CFG-03 | done | retired keys: `indInvert` always, `indEffects` only after its migration |

## What the screenshots show (read)
- Comic lines, Signal 40 percent: a few dozen thin, dim amber-brown rays at the screen edge, sparse and faint; lost (offline): the full ring of many bold bright red rays of varied length, much denser and brighter.
- Coloured vignette, Signal 40 percent: a soft amber-brown glow along the edges and corners, restrained; lost: a strong red wash filling the outer third and more, black only in a central oval.
- Radar with the alert, True black and Dimmed, offline: the red vignette sits behind the search field, the wheel labels and the mic button, which remain legible; the Dimmed shot also shows the wallpaper's own blue wedge.
- Calm dots (Bloom and Animated dots) at t=0 are a faint green lattice, easy to miss in a still; not judged beyond "drawn".
- Settings preview: lost level (0 percent) shows the red vignette in the preview box above the labelled slider and the "Preview only" line.

## Tests added
- core-data `IndicatorEffectsW35Test` (pure JVM): defaults, migration from every old set shape, store migration and key removal, fresh install, round trip with both off, retired keys, severity (healthy, falling, lost, battery charging, airplane), alert colour, preview reading.
- feature-grid `IndicatorAlertW35UiTest` (Robolectric): each effect at healthy/low/lost, alert invisible when healthy, progressive growth, battery and charging, nothing runs when healthy and urgent pulses, reduced-motion urgent static and visible, comic lines dense when lost, both groups together, zero intensity, Radar Dimmed and True black carry the alert, settings preview responds to its slider; `IndicatorMathTest` (pulse, strength, vignette, comic fraction and determinism, fraction 1 = unchanged set).
- app `IndicatorRowsW35Test`: calm row, alert row with hint, Show row, independence of the groups, old rows gone.

## Tests changed or removed
- `IndicatorMathTest`: the perimeter, hash and ray-distance tests removed with the Snake outline and old comic code they served.
- `StateCodecTest.variants`: a null-by-default enum field gets its enum values as variants; the Set variant no longer uses the removed enum.
- `ScreenshotTest`: the Scene takes `calm`/`alert`; the old three-effect shot (bloom + snake + edge meter) is bloom only, the vignette shot uses the alert.

## Not matched / device only
- Thresholds (75 percent Signal, 60 percent Battery, 5 percent urgent), the pulse and vignette strength are not checked on a device or against real signal changes; battery EXTRA_PLUGGED is the charging test.
- With Signal and the comic-lines alert both the wallpaper's own no-signal lines (w34 stream) and the alert can draw; reconcile at merge.

# Wave 22 glassFix: landscape layout, clearer glass, Flat Close, frame cost (PAL-18, PAL-20, INP-12, NFR-13, NFR-15, NFR-05)

Nothing here was seen on a device. Tests are Robolectric (no real IME, cutout, gesture bar, TalkBack or GPU). Screenshots `feature-grid/build/screenshots/w22-*.png` were read.

Second review (adversarial) of the first pass 269d16b: the first pass changed nothing about the landscape layout, the shade or the Flat Close, and
left them as open owner decisions; this pass does the work. Decisions and numbers: docs/decisions/radarGlass.md.

Defects found in the first pass and the area, and fixed:
1. Landscape: the wheel was always five 34 dp rows; in the Filmstrip the fixed 182 dp strip overlapped the wheel, and in a short window the wheel overran the 72 dp strip. Fixed with `WheelSpec.fit` and a Filmstrip band that gives way.
2. The wheel row was 34 dp at every font scale, so labels overlapped at 200 percent. `WheelSpec.rowFor`.
3. The Orbit kept its fixed 90/100 dp wheel-text columns and did not re-lay when the wheel changed (the `remember` key lacked the wheel and font scale).
4. The first-pass landscape test checked only the selected label, with a hard-coded xhdpi row and a half-row slack, so it could not fail on the real overlap. Replaced by a bounds matrix (4 windows x 4 layouts x font 1.0 and 2.0 x keyboard closed and open); with the fix reverted 6 of its cases fail.
5. NFR-15: the shade was computed as if the mic and Close carried 4.5:1 text and as if every piece were pressed and nested; they carry icons (3:1) and the field is neither pressable nor nested over its text. Corrected (`GlassShade.Carries`).
6. NFR-15: the default pair's 0.37/0.40 shade came from the Spire tip entering the top bar at level 4 and the bloom under the mic; moved away in Radar only (`BackgroundConfig.glass`); shade now 0.00 for the default pair.
7. PAL-18: the duplicate Close in Flat removed (owner asked for the X in the field only); test and checklist updated.
8. NFR-05: the glass draw was an inline lambda no test could reach; now `GlassLayers` with an allocation test and a source scan for RenderEffect and blur.
9. Hygiene: a stray double blank line in SearchOverlay, the `WheelFadeRows` constant replaced by a per-wheel fade.

| ID | status | note |
|---|---|---|
| PAL-18 | built | Verified in code and tests: field with Clear inside (only with text), Close outside the field while empty in Orbit and Filmstrip only (Flat decided, see radarGlass.md), mic in a reserved 72 dp strip, scope pill inside the field in Flat, RTL mirrored. New: the wheel sizes itself (1, 3 or 5 rows) and its labels fade inside its own box; the Filmstrip gives way; bounds matrix `INP_13_*MicHasItsOwnRegion` (32 tests, each checked with the keyboard closed and then faked open); screenshots read (landscape Orbit, Filmstrip, Flat, keyboard, small landscape font 2.0, portrait). Open, device or owner: landscape with the keyboard up leaves a few dozen dp (one wheel row, Filmstrip clipped); owner may want the mic hidden then. |
| PAL-20 | built | Result area is still clipped above the mic strip; the visible parts of results never meet the mic at any size, font or keyboard state (same matrix). Preview and pinch gestures untouched (existing tests green). |
| INP-12 | partial | Focus on open and the mic riding `imePadding` verified with faked insets (the matrix opens a 40 percent keyboard at every size). Radar still raises the keyboard on every open regardless of the per-orientation option: owner decision. Real IME and gesture bar: device only. |
| NFR-13 | partial | Three traversal groups (query line, results, mic) with a test; every bar target 48 dp (existing test); wheel rows grow with the font so labels do not overlap at 200 percent (bounds matrix at font 2.0). Not done: the mic's listening state is not a state description (needs eight translations); no TalkBack run. |
| NFR-15 | built | Default pair now needs no shade (was 0.37/0.40): `BackgroundConfig.glass` keeps the Spire tip and bloom out of the glass in Radar; icon-only glass judged at 3:1, field text at 4.5:1. `NFR_15_glassTextHasAaOverEveryBackgroundPairAtLevels0And4` still measures every valid pair at levels 0 and 4, both orientations, held-out clock values: min field text 4.71, placeholder 4.70, icons 3.23. Pairs that still keep a shade (80 background and size cases, at most 0.58: Beam, Shaft, Laser and similar bright pairs) keep it, stated in radarGlass.md. Daylight look is a device check. |
| NFR-05 | partial | Code and JVM checks done: `RadarGlassFrameCostW22Test` shows the glass draw path allocates nothing (352 bytes over 2000 frames of five glass pieces, 136 of them the harness), brushes are built once per size, and no RenderEffect or blur appears in the glass, the Radar screen or the painters. Device measurement (gfxinfo, `FrameTimingMetric`, at most 1 percent janky) not done. |

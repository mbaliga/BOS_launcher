# Wave 42: music controls that do not always work

Owner report: "The music controls at the top dont always work." Causes and fixes are in `docs/decisions/musicControls.md`. Robolectric and JVM tests only; nothing was run on a device, and how real players (Spotify, YouTube Music, Apple Music, browsers) answer the fallback routes is reasoned, not observed.

| ID | status | note |
|---|---|---|
| STR-06 | done, device pending | All sessions watched, playing beats paused, re-pick on state change and on every tap (`MediaNow`, `MediaLogic.chooseSession`); fallback routes in `MediaCommands`; failure toast. Tests `MediaCommandsW42Test`, `MediaNowW42Test`. |
| HOM-23 | done, device pending | Notch pop-in is paint-only, composed on the first frame; skip controls dimmed only when the session reports actions without them; touch restarts the auto-hide. Tests `MusicControlsW42UiTest`. |

# Wave 27 soundPopup: Sound and Vibration sheet

Nothing was run on a device. Verified by JVM and Robolectric tests and by reading the screenshots (`feature-launchpad/build/screenshots/sound-vibration-*.png`) against the owner's mock-up.

| ID | Status | Note |
|---|---|---|
| SND-01..07 | built | State mapping: order, maxima, clamp, step, percent, disabled notification row, SecurityException, vibration steps (`SoundVibrationStateTest`). |
| SND-10..14 | built | `HapticPolicy` per strength, default Medium, tolerant read (`LauncherHapticPolicyTest`). |
| SND-15..17 | built | Routing pin: no `LocalHapticFeedback` outside the helper; strength changes what is performed (`LauncherHapticsRoutingTest`). |
| SND-20..31 | built | Bounds (bottom, 4 sliders, 48 dp), drag, tap, keyboard, TalkBack progress and SetProgress, Vibration row, DND row, dismiss (Esc, scrim, Back, drag down), reduced motion, system row button opens it (`SoundVibrationSheetPortraitTest`, `...LandscapeTest`). |

## Device-only / not verified
Real `Settings.System` observer delivery of volume changes (a 1.5 s poll backs it up), the DND `SecurityException` path on a real device, TalkBack speech, haptic feel of the four strengths, the sheet over the new Comms screen (commsDesign stream) and the header hook wiring.

## Old tests changed
`W13CrossAuditTest`: `launcher_haptics` added to the exported pref files list.

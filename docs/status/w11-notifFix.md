# Wave 11: notification tap-to-open and the roomy list

Nothing here is device-confirmed. Device steps: `docs/device-checklist-launchpad.md` ("Added by wave 11"). Notes: `docs/decisions/notifFix.md`.

| ID | Status | Note |
| --- | --- | --- |
| STR-02 | built | `PendingIntentLauncher` (core-data) with the Android 14 background-start opt-in, used at every send site; `LaunchpadOpen` closes only after a hand-off, falls back to the app, shows "Couldn't open" otherwise, cancels auto-cancel notifications. One scrolling list, roomy rows, "+N more", section strip (`LaunchpadRoomy.kt`). Tests `PendingIntentLauncherTest` (sdk 34, 35), `LaunchpadRoomyPortraitTest`, `LaunchpadRoomyLandscapeTest`. Screenshots: `feature-launchpad/build/screenshots/launchpad-roomy-{portrait,landscape}-list-{light,dark}.png` |
| STR-03 | built | Gestures untouched. Action chips and inline reply now go through the launcher and show a message when the send fails |
| STR-08 | built | Arrows walk the rows of the one list (focus order is built from what is shown); left on a title folds, right opens; Esc returns. Groups keyboard test updated |
| STR-09 | built | Indicator tap only calls open; no PendingIntent involved, no change needed (audited) |
| STR-11 | built | Digest widget click uses a PendingIntent we create and the system fires; not a send site, no change needed (audited) |

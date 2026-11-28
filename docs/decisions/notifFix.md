# Notification tap-to-open and the roomy list (wave 11, STR-02)

## What changed

- STR-02 text rewritten: the background column is one scrolling list with every section and notification visible, roomy rows (at least 72 dp, 16 dp and 12 dp padding, 40 dp icon, heavier larger title, two lines of text, time at the end, 8 dp row gaps, hairline and space between conversation groups), "+N more" that expands in place, quiet section titles and a compact title strip that jumps to a section. Tapping a row opens the notification through one shared hand-off.
- New `PendingIntentLauncher` (core-data) with a sender seam, used at every PendingIntent send site (content intent, action chips, inline reply, media session activity). The PendingIntents we build ourselves for RemoteViews or alarms are fired by the system, not by us, so they are not send sites.
- `HubNotification.autoCancel`: a tapped notification that asked for it is cancelled; mark-read stays.
- Launchpad closes only after a successful hand-off; failures show "Couldn't open <app>".

## Why

Owner request on build 43: "The notification won't open if I tap on it" (video: Launchpad closes, WhatsApp never opens) and "the notifications look too cramped, and they should not be collapsed this way."

Root cause (verified by reading the code): `onOpenItem` called `contentIntent.send()` with no options inside `runCatching` and treated "no exception" as success. From Android 14 a PendingIntent created by another app only starts an activity when the sender opts in with `ActivityOptions.setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)`. Without it `send()` returns normally while the system drops the launch, so the fallback to the app never ran and Launchpad closed. Not reproduced on a device (no device here); the fix follows the platform contract and is a checklist item.

## What the old spec said

STR-02: "in the background column beside the home card: the section titles, the selected one large and dark and the others small and grey, with the selected section's rows beneath it". Only the selected section expanded; every other section was one small grey line. That accordion is gone. Old tests that encoded it (`STR_01_card_titleSelectsASectionAndItsRowsSitBeneathIt` in both orientations, and the three groups tests "work rows not shown until Work is selected", "selecting a Work title", and "Enter selects a group title") were rewritten to the new behaviour, not deleted.

## Choices

- Folding survives only as the user's own choice: arrow left on a title, the pinch-in gesture, and tapping a folded title to open it. Tapping an open title does nothing, so a stray tap cannot hide rows.
- "+N more" applies to conversation and sender sections only; day, app, flat and pinned lists never fold, and a single notification is never hidden.
- Reduced motion: the strip jumps with no animated scroll.
- Not changed: STR-03 gestures, STR-08 keys, the card and sliver geometry, `LaunchpadCardGeometry.kt`, `LaunchpadCardLayer.kt`, `SystemStateRow.kt`.
- Robolectric runtimes for Android 13 (sdk 33) are not available offline, so "no opt-in below API 34" is tested through `options(sdkInt = 33)`; sdk 34 and 35 run on Robolectric.

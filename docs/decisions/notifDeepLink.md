# W42 notifDeepLink: a tapped notification lands on the notification

Owner report: "When I tap on a notification, it takes me to the app but not to that notification."

## Causes found (read in code)
1. History-only rows had no intent. `CommsRead.fromEntry` builds a row with no `contentIntent`, and the history branch of `open()` in Launchpad.kt (and the Hub `onOpen` for an entry not in the shade) called `launchApp(pkg)` directly: it could only ever open the app's front page.
2. Dismiss destroyed the only deep link. Wave 32 made Dismiss = mark read AND `NotificationProvider.dismiss` (`cancelNotification`); the row then became history-only (cause 1) and `HubNotification.contentIntent` is not persisted.
3. Nothing kept an intent after the notification left the shade: `NotificationProvider` only held the current feed and `NotificationHistory` stores text, never intents.
4. No other route to the conversation: the listener did not read `Notification.getShortcutId()`, so even apps that publish conversation shortcuts (chat apps) could not be deep linked after a restart.
5. The fire path itself: `PendingIntentLauncher.fire` already treats a normal return as sent and works for activity, broadcast and service PendingIntents; it set only the sender's background-start mode. On Android 15 the creator's mode matters for trampolines; it was not set.

## Decisions
- `RecentIntents` / `NotificationIntents.recent` (core-data NotificationOpener.kt): in memory, LRU, 300 keys, one slot per key holding the post time; a newer or equal post replaces, an older post is ignored, a lookup for a post newer than the one held returns nothing. Filled from `NotificationProvider.onNotificationsChanged`/`listedBy` for every live row that is not ongoing or progress. Never persisted.
- `NotificationOpener.open(HubNotification | HistoryEntry, context, launchApp)` is the one open path (Comms uses it through `LaunchpadOpen.open` and the history branch; the Timeline can call the `HistoryEntry` overload). Order, pure in `OpenPlan`: live intent, remembered intent, conversation shortcut, app launcher activity. Live and remembered intents and the shortcut count as Sent, the app as FellBack, nothing as Failed (Comms stays open and says so). A cancelled intent throws, which `PendingIntentLauncher.fire` already turns into false, so the next step runs.
- Shortcut: `HubNotification.shortcutId` (from `Notification.getShortcutId()`), `HistoryEntry.shortcutId` and `userId` stored as two optional trailing fields of the history line (written only when a shortcut exists; old lines and old readers of 9-field lines are unaffected). Started with `LauncherApps.startShortcut` (this app is the home app); the user handle is found among `LauncherApps.getProfiles()` by user id, else the current user. The history file is already never exported (`StateSchema.NEVER_EXPORTED_FILES`).
- Setting "Clear from the shade when dismissed in Comms" (Ground Control, Comms; `launchpad_hub` pref `clearFromShade`, default on = unchanged behaviour). Off: Dismiss marks read only and the notification stays in the shade (still openable from there, and the live row keeps its intent across a process restart of the launcher). `autoCancel` after a tap is unchanged.
- `PendingIntentLauncher.options(sdk)`: from API 35 also `setPendingIntentCreatorBackgroundActivityStartMode(ALLOWED)`, inside its own `runCatching` so a missing method cannot drop the API 34 opt-in.

## Not decided here
Whether a given app's intent or shortcut lands on the right chat is the app's behaviour; see docs/status/w42-notifDeepLink.md.

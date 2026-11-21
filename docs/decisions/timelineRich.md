# Wave 41 timelineRich: a richer Timeline

Owner: "I want notifications and alerts marked here too, as well as app opens and scheduled opens, alarms, everything that gets recorded. And these events need their own icons each in front of them to understand the nature of the event. I want this timeline to be richer and more informative." The earlier decision stands: no filter pills (usageSimplify), the usage block on top is unchanged.

## Sources (nothing is copied)
- App opens: the launch log (`TimelineLogStore`). Notifications: `NotificationHistoryStore`; its line format gained two optional trailing fields (category, priority) so an alert can be told from a plain post; 9-field lines of older installs still decode.
- `TimelineEventStore` (`files/usage/timeline-events.log`, 5000 lines, compacted at 5500, I/O on its own executor) records only what has no other record: scheduled launch fired, package installed / updated / removed, app hidden / unhidden. A callback repeating the same event within a minute is dropped. It is in `StateSchema.NEVER_EXPORTED_FILES` under the `usage` directory, like the other histories.
- `AlarmManager.getNextAlarmClock` gives the pinned "Next alarm" row (never a past event). An alarm that rang is known only as a notification (clock package or category alarm) because the history skips ongoing notifications.
- Not recorded, on purpose: connectivity and charging (no existing observer; a recorder would add a receiver for little), settings / theme / wallpaper changes (no audit log is invented), read and dismiss marks (shown as the unread dot; "dismiss = mark read").

## Model (`TimelineModel`, core-data, pure)
- `TimelineClassifier`: category first (alarm or clock package, call with "missed" wording, msg, email, event / reminder, progress, err, sys or system package, promo), then download packages, then priority 1 or more is an Alert, else Notification. A heads-up message stays a Message.
- Opens of one app less than 2 minutes apart (adjacent in the open stream) are one event with a count, row shows "x3"; the id is the first open so it stays stable while a run grows.
- Notifications of one app (notification family only) 5 minutes or less apart are one burst holding its members; alerts, calls and alarms are never grouped. A burst is unread when any member is.
- Merge, newest first, capped at 5000 events, grouped by calendar day (zone aware); a day summary counts opens, notifications, alerts and app changes. An upcoming scheduled launch sits in its future day (Tomorrow) and is not counted as an open.

## UI (`TimelineEventsUi`, feature-launchpad)
- Lazy list, stable keys, heading semantics for days, 48 dp rows (`commsTarget`), the merge off the main thread above 400 inputs (`TimelineMergeConfig`, as `CommsMergeConfig`). Notifications and alerts use the Comms read / unread tone and dot; opens, package changes and schedules use plain ink (they have no read state).
- Icon tiles are drawn in code (`TimelineGlyphs`), one distinct glyph and muted colour per kind; a test walks the enum so a new kind cannot lack one. The app's own icon is a 16 dp badge. Update uses a box with a small circular arrow.
- Tap: open the app; a notification tries its live content intent first and marks read. Long press: Open app, Hide this app (through the home's hidden-apps store), Copy text. Package removed rows open nothing.
- The old `TimelineView` / `Timeline.build` stay in core-catalog with their tests but no screen uses them.

## Limits
Nothing here ran on a device. Session length needs usage access; without it the detail line says just "Opened".

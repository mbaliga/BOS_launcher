# Comms categories and the pinned block (wave 53)

Owner request: "There are some things which are like pinned notifications, that should show as pinned in our comms centre as well. In the comms hamburger menu, we must have below all comms, also categories such as Messages, Notifications, Alerts. Notifications are app notifications that do not specifically address the user, like ads and other general notifications. Alerts are time sensitive notifications, like OTPs, alarms, etc."

Spec rows: STR-16 (pinned block), STR-17 (categories). Code: `NotificationClass.kt` (core-data, pure), `CommsCategoryUi.kt`, `LaunchpadMenuPanel.kt`, `Launchpad.kt` (feature-launchpad).

## Definitions (also the menu captions)
- **Messages**: notifications addressed to the user personally: chat, SMS, email, calls, missed calls, mentions, direct replies. Caption: "Chats, SMS, email and calls to you".
- **Notifications**: app notifications that do not address the user: ads and promos, news, social feed updates, app updates, general info. Caption: "Promos, news and app updates".
- **Alerts**: time-sensitive: OTP and verification codes, alarms, timers, reminders and calendar events, deliveries and rides that arrive (when the app marks them urgent), security and bank alerts, battery and system warnings, anything the app flags as time-sensitive. Caption: "Codes, alarms, reminders, warnings".

## Classification order (`NotificationClass.categoryOf`)
1. The owner's Treat-as rule (most specific matcher wins, ties go to the latest, like every other rule).
2. A promo or recommendation category is Notifications (an ad that quotes "code 4821" is not an OTP).
3. Alerts: category alarm, reminder, event, stopwatch; the app's time-sensitive marks (a full-screen intent or FLAG_INSISTENT); category err, sys or status with high importance; an OTP by the local heuristic; a security or bank text; an arrival text ("is arriving", "has arrived", "is outside") with high importance.
4. Messages: category msg, email, call, missed_call; MessagingStyle or a messaging notification; category social with a person or a conversation shortcut; a conversation shortcut with a person.
5. Everything else is Notifications.

So **Alerts beat Messages beat Notifications**: an OTP text message is an Alert, a calendar event in a chat app is an Alert. No network is used.

OTP heuristic (`looksLikeOtp`, conservative): a 4 to 8 digit run not inside a longer number, plus a word that says what it is (otp, one-time, verification, passcode, security code, login code, code, pin; Spanish codigo, clave, verificacion; Hindi ओटीपी, कोड, सत्यापन, पिन); rejected when the text reads as a promo ("% off", discount, coupon...) or when every digit run follows a currency sign or word. Security/bank (`looksLikeSecurityOrBank`): fraud and sign-in phrases, or a debit/credit word together with an amount. These are heuristics, not guarantees: the owner's override is the escape hatch.

History rows (a notification that left the shade) are classified from the stored category and text only: a history row's "is messaging" flag only means it had a sender and text, so it is ignored.

## Owner override
- Rule actions `AsMessages`, `AsNotifications`, `AsAlerts` ("Treat as ...") in the existing rules system (LaunchpadRules; the Settings rules pane lists them, the add form offers them).
- Long-press chips on a row: the two other categories ("Make this an Alert", "Make this a Message", "Make this a Notification"). The rule they add matches the sender in its app for chats, else the notification's channel in its app, else the app; a later narrower rule overrides a wider one.

## Menu and views
- The Show list: All Comms, then Messages, Notifications, Alerts (glyph, name, one-line caption, unread badge), then the apps. It is one radio list: picking a category clears an app choice and the other way round. The title in the Comms header names the category. Counts on the category rows are unread counts; pinned rows are never unread. Counts on app rows and All Comms are unchanged (they follow the Read / Unread filter).
- A category composes with the Read / Unread / All pinch, with groups (the groups are built from the filtered list) and with rules. It also filters the Unread and Read history tabs (the filter follows into them like the app choice). Picking a category from Files or Timeline goes to All.
- Group by is unchanged: a category as a grouping was not simple (a group is by sender, app, time), so categories are views only.
- Default view (Settings > Comms): All, Messages, Notifications, Alerts, then the other views; a category opens All filtered. Stored in `LaunchpadPrefs.defaultCategory`.

## Pinned block
- Pinned = FLAG_ONGOING_EVENT, FLAG_FOREGROUND_SERVICE or FLAG_NO_CLEAR (all folded into `HubNotification.isOngoing` by the listener, which now also keeps ongoing notifications it used to drop, except a minimised IMPORTANCE_MIN service line), or a live notification of the sticky categories transport, navigation, stopwatch, service, progress (with a progress bar), call (with the call style), workout, location sharing. A history row is never pinned.
- Where: the first section of the list, above every group, header "Pinned" with a pin glyph and the count. Without groups the owner's Pin-by-rule rows join the same section and show a Rule badge. With groups they stay at the top of their own group (a group's order and priority stay what the owner set); this is the one place where "the same block" is not literal.
- Locked like the system: a slide, the Delete key, the menu and TalkBack never dismiss or mark them; a slide or the key shows "Pinned notifications stay until the app ends them". They stay locked when "Show pinned at the top" is off.
- Tapping opens them like any row (live intent, remembered intent, shortcut, the app). They update live because the feed does.
- Unread: pinned rows never count toward unread or the edge indicator and have no unread dot. This is the same exclusion the indicator already applied to ongoing notifications; a Priority group does not override it (a running timer is not "new"). Nothing new in the Priority group rules was needed.
- The read filter does not hide the pinned block (they are status, not mail); the app and category choices do apply to it.
- More than three pinned fold into one compact row: icons of the apps, "N pinned", Show all / Show fewer.
- Setting "Show pinned at the top" (Settings > Comms, default on).

## Persistence
The two new settings live in `LaunchpadPrefs` (`launchpad_rules` file, `prefs` JSON: `pinnedAtTop` written only when off, `defaultCategory`), which is part of the exported `launchpad` section and round-trips through export and import (`NotificationClassW53Test`). The hub file `launchpad_hub` (Default view, Clear from the shade, the Widgets list) is excluded from backup and export as before; the Default view's view part stays there, so after an import the exported category applies only when the device's own Default view is All (otherwise the view part wins). Reported, not changed.

## Alternatives not taken
- Categories as new `HubView` tabs: the tab row is fixed to the owner's five sections.
- A separate pinned screen or a floating strip: the block in the list reuses rows, focus order and gestures.
- Category as a rule matcher: the rules system has matchers that describe a notification; the category is an outcome, so it is an action.

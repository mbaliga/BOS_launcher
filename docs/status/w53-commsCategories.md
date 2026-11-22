# Wave 53 commsCategories status

Owner request: a pinned block in Comms for notifications that are "like pinned", and Messages / Notifications / Alerts categories below All Comms in the Comms hamburger menu. Decisions and definitions: docs/decisions/commsCategories.md.

| ID | Status | Note |
|---|---|---|
| STR-16 Pinned block | done (Robolectric and JVM, no device) | ongoing, foreground-service and no-clear notifications and live notifications of sticky categories lead the list under "Pinned" with a pin glyph; locked (slide, Delete key, menu, TalkBack: no dismiss, no mark read; a slide says why); never unread, never counted (also not by Priority groups); tap opens like any row; live updates; compact row above three with Show all / Show fewer; Pin-by-rule rows join the block (without groups) with a Rule badge |
| STR-16 setting Show pinned at the top | done | Settings > Comms, default on; off lists pinned rows with the others, still locked and never unread |
| STR-17 Categories in the menu | done | Messages, Notifications, Alerts right below All Comms in the Show list, glyph, one-line caption, unread badge, radio semantics and TalkBack description; one radio list with the apps |
| STR-17 Classification | done | `NotificationClass` (core-data, pure): owner rule, promo guard, Alerts > Messages > Notifications; local OTP heuristic (en, es, hi), security and bank text, arrival text |
| STR-17 Owner override | done | rule actions Treat as Messages / Notifications / Alerts (rules list and add form), long-press chips "Make this a Message / a Notification / an Alert" (rule by sender in the app, else channel, else app) |
| STR-17 Composition | done | with the Unread / All / Read pinch, with groups and rules, with the Unread and Read history tabs (filter follows into them); header names the category |
| STR-17 Default view | done | Settings > Comms > Default view lists All, the three categories, then the rest; a category opens All filtered |
| Persistence | exported | `pinnedAtTop` and `defaultCategory` are in `LaunchpadPrefs` (file `launchpad_rules`, `prefs` JSON), part of the exported `launchpad` section, round trip tested. The Default view's view part and the other Comms options stay in `launchpad_hub`, which is NOT exported or backed up (existing behaviour, reported, not changed) |
| New strings | done | `L10nW53commsCategories.kt`, English plus es, fr, de, pt, hi, zh, ja, ar |

## Listener change (device-only to verify)
`SphereNotificationListener` now keeps ongoing notifications it used to drop (those with no progress bar and none of call, transport, progress, navigation, alarm, stopwatch, workout category), except a minimised (IMPORTANCE_MIN) service line and Liftoff's own. FLAG_NO_CLEAR counts as ongoing. It also reads the ranking's importance, the people list and the full-screen intent / insistent flag. None of this runs under unit tests (the listener is bound by the system).

## Not matched, honest limits
- With user groups, a Pin-by-rule row stays at the top of its own group; the single block holds the ongoing rows then (the group order and priority are the owner's).
- A category is a view, not a Group-by option (the grouping grid is by time, app, sender, conversation).
- The heuristics (OTP, bank, security, arrival) are conservative and local; real apps' notifications will contain cases they miss or catch wrongly. The Treat-as override exists for that.
- History-only rows lack MessagingStyle and importance, so they are classified from the stored category and the text.
- A category default restored by an import applies only when the device's own Default view (in `launchpad_hub`) is All.
- Device-only, not verified: real ongoing notifications (media, navigation, timers, VPN) in the block, haptics, TalkBack announcements, the real listener's flag handling.

## Tests
- core-data `NotificationClassW53Test` (12): table of about 50 real-looking notifications (OTP en/hi/es, WhatsApp, Gmail, Amazon promo, alarm, calendar, Uber arrival, bank debit, news, system update and more), OTP and bank heuristics with negatives, override precedence and specificity, rule codec, prefs round trip and old-export defaults, pinned table, split, indicator exclusion.
- feature-launchpad `CommsCategoriesLogicW53Test` (history tabs by category, Default view choices and persistence, the exported pref), `CommsCategoriesW53PortraitTest` and `CommsCategoriesW53LandscapeTest` (Robolectric, the same cases): menu entries and order, captions, counts, radios and descriptions, category views, read filter composition, default category, Treat-as rule, long-press chips adding the rule, pinned block position and glyph, never unread, locked swipe with the hint, tap opens, live update, compact row, three stay full, setting off, rule badge, read filter and category on pinned, call and timer categories, light look, RTL. Screenshots `build/screenshots/comms-categories-*.png` were read and fixed (the pin sat above the time: moved to the left margin where the unread dot is; compact icon overlap).
- TranslationCoverageTest, core-quality audits (SPEC_STATUS gained STR-16 and STR-17 rows and the Counts line, because CFG-01 requires a row for every spec requirement), spec coverage regenerated (`python3 tools/spec_coverage.py`).

## Existing tests
No existing test had to change: the menu geometry and design tests did not depend on the row after All Comms, and the older ongoing-row tests still hold (`canDismiss` is now `!isPinned`, a superset of `!isOngoing`).

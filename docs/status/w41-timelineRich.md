# Status: wave 41, richer Timeline (stream w41-timelineRich)

Nothing here is device-confirmed. Robolectric and JVM tests prove structure, model rules and semantics only.

| ID | Status | Note |
| --- | --- | --- |
| HOM-18 | built, partial on device | Unified event Timeline: opens (x3 collapse, session length), scheduled launches, notifications (burst rows), alerts (call, missed call, message, email, calendar, alarm, download, security, promo, alert), package events, hide / unhide, next alarm pinned; per-kind icon tiles; day headings with weekday, date and a plain count line; no filters. |

## Sources: real vs modelled
- Real stores read: launch log, notification history (now with category and priority), the new `timeline-events.log` (written by the scheduled-launch receiver, the `LauncherApps` callback in `AppCatalog`, `HiddenAppsStore`), `UsageStatsManager.queryEvents` for session lengths, `AlarmManager.getNextAlarmClock`.
- Modelled only (JVM and Robolectric with fakes): the classification of a real device's categories (missed call is recognised by wording, so a dialer using other words shows as Call), package callbacks, usage sessions, alarm and content intents. Device-only to confirm.
- Not built: connectivity lost / restored and charging events, settings / theme / wallpaper audit (not recorded anywhere, none invented), "created" schedules (only fired and upcoming), a retention setting (none exists).

## Tests run
- core-data `TimelineModelW41Test` (classification of every kind, collapse, bursts, merge order across sources, day grouping and counts, 5000 bound, store round trip, bound, repeat drop, backup classification, old history lines). feature-launchpad `TimelineRichW41Test` (every kind's icon tag and description, glyph per kind and 8-language names, day heading and summary, singular counts, 12 / 24 hour, x3, burst expand, tap, long press menu, copy, unread dot and state, next alarm, empty state, 5000 lazy and off-main-thread merge, 48 dp at 75 / 100 / 125, font 2.0, RTL, Tomorrow section). `TimelineRichW41ShotsTest` screenshots `feature-launchpad/build/screenshots/w41-timeline-{dark,light,dark-expanded,dark-tall,light-font2}.png`, read: icons, colours, badges, unread dots, x3 and the expanded burst look as intended; at font 2.0 a long burst title ellipsizes after two lines. App `TimelineRichW41Test` (real stores to the panel, tap marks read and opens the app).
- Old tests changed: `HomeLandingItemsW24Test.HOM_18_w34_...` looked for the bare app name; rows now read "Opened <app>". No test deleted.

## Strings
`L10nW41timelineRich.kt` (es, fr, de, pt, hi, zh, ja, ar). Decisions: `docs/decisions/timelineRich.md`.

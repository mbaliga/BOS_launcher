# Sphere's own home-screen widgets (`:feature-homewidgets`)

Five standard Android App Widgets that **Sphere provides**, so they can be placed in any launcher:
Pixel Launcher, One UI, Nova, or Sphere itself (through its widget hosting). They also work when
Sphere is not the default home app. Not to be confused with `:feature-widgets`, which **hosts**
other apps' widgets inside Sphere ([`widget-hosting.md`](widget-hosting.md)); nothing in hosting
changed for this.

> **Status (2026-11-04):** compiled and JVM-tested only. The build environment has no device or
> emulator, so **nothing below about rendering, resizing, taps, refreshes or TalkBack has been seen
> running**. Everything in the owner-verify list at the end is unverified.

Standard followed: the shared widget guide for the constellation (2026-11-04) and the widget
strategy (`Personal-Tracker/snapshots/widget-strategy-2026-10-14.md`, §2, §5.3, §5.4, §6; Orrery
Home's §8 row). The shared kit, pairing and Orrery-host parts of that strategy are not in scope:
these are Sphere's own widgets, built natively in this repo.

## The five widgets

Each has three layouts. The launcher picks one from size-mapped RemoteViews (API 31+, Sphere's
`minSdk`), without waking Sphere. A layout's key is the smallest size its content needs (dp,
`WidgetKind`); keys nest, so a widget never loses content as it grows (`WidgetTiersTest`). Below
the smallest key the small layout is used.

**Font scale:** each key also records how much of its height is text, and the keys handed to the
launcher grow with the system font scale (`WidgetTiers.atFontScale`). At 1.3× or 2× a widget
therefore shows the layout its text still fits in, instead of clipping a footer or the controls
(for example, Needs you on a portrait 4×3 shows five rows up to about 1.3× and three at 2×). Sphere
redraws every widget when the font scale changes (`SphereApplication.onConfigurationChanged`, and
on the next process start, since the scale last drawn is stored). The text shares are estimates
(a line ≈ 1.33 × its sp size), so this is in the owner-verify list.

States (access off, nothing playing, connecting, choose apps, couldn't read) have a compact small
layout (`hw_state_s`: one line of title, two of body) and a full one for medium and large.

| Widget | Template | Small | Medium | Large | Default size |
|---|---|---|---|---|---|
| **Gate clock** | `stat` | the time (the gate pill) · 100×40 | + the date · 110×90 | + the next alarm, or "No alarm set" · 110×150 | 3×2 |
| **Battery** | `progress` | glyph, level, state word, "as of" · 100×40 | + level bar · 110×115 | + time to full or power source, temperature · 110×155 | 2×2 |
| **Now playing** | `card` | state glyph, the app (or the title, if shown), state, "as of" · 100×40 | + "Now playing · App", artist · 180×100 | + previous / play-pause / next pads · 180×160 | 4×2 |
| **Needs you** | `list` | the total, how many apps, "as of" · 100×40 | + three apps with counts · 180×210 | + five apps with counts · 180×326 | 4×2 |
| **App pads** | `pads` | two apps · 100×48 | four in a row · 200×48 | six in two rows, with names · 200×135 | 4×1 |

Sizes are at font scale 1. Every provider declares `minResizeWidth` 110 dp × `minResizeHeight` 40 dp
(App pads: 48 dp, so a one-row pad is a 48 dp tap target; its pads fill the widget's height), `resizeMode`
horizontal|vertical, `targetCellWidth/Height`, a `previewLayout` (sample content) and a
`previewImage` fallback, `initialLayout` = "Loading…", `updatePeriodMillis="0"`.

### Gate clock
- **Shows:** time and date as `TextClock`s, which the launcher keeps current with no help from
  Sphere (12/24-hour follows the system). Date pattern from the locale (`EEEdMMM`). The next alarm
  (`AlarmManager.getNextAlarmClock()`, no permission) as "Alarm Wed 07:00": by weekday within the
  coming six days, by date beyond (`ClockModel`), so the label stays true without a midnight redraw.
- **Refresh:** `NEXT_ALARM_CLOCK_CHANGED`, `TIME_SET`, `TIMEZONE_CHANGED`, `LOCALE_CHANGED` (all
  still delivered to manifest receivers). Never stale: the time is drawn by the launcher.
- **Tap:** opens Sphere. (Opening the clock app's alarm list needs `SET_ALARM`, a new permission;
  not added.)
- **Privacy:** public (time, date, alarm time).

### Battery
- **Shows:** level, state as glyph + word (Charging: bolt / Full and Plugged in, not charging: plug /
  On battery: battery outline / Low: battery with "!"), a level bar (its length is the level; its colour means nothing), time to
  full (`BatteryManager.computeChargeTimeRemaining`, only when Android knows it), power source,
  temperature, and **"as of HH:MM"** at every size (`BatteryModel`). "Low" is the system's own flag
  (`EXTRA_BATTERY_LOW`), else 15 %.
- **Data:** the sticky `ACTION_BATTERY_CHANGED`, read live (`Sources.battery`); nothing cached.
- **Refresh:** while Sphere's process runs and a battery widget is placed, a context-registered
  `BATTERY_CHANGED` receiver (`WidgetRuntime`) redraws when level, state or plug change, or when the
  printed time is 15 minutes old (`BatteryModel.shouldRedraw`). Battery broadcasts can't be declared
  in a manifest, so **when Sphere's process isn't running the widget keeps its last reading and its
  "as of" time says how old it is.** Sphere's process normally runs while it is the home app, or
  while its notification listener is bound (notification access granted).
- **Tap:** reads the battery again and redraws (`WidgetActionReceiver`, not exported).
- **Error:** "No battery reading" when Android reports none; tap to try again.
- **Privacy:** public.

### Now playing
- **Shows:** the app and state (Playing: triangle / Paused: two bars / Loading: hourglass; glyph +
  word) of the first playing session, else the first loading, else the first paused, in the
  platform's order (`NowPlayingModel`), and, only if the widget's option is on, its title and
  artist. Controls only for actions the session accepts. No album art, no position.
- **Data:** `MediaSessionManager.getActiveSessions(Sphere's listener)`, which needs **notification
  access**; no new permission.
- **States:** "Notification access is off" (tap opens Sphere's notification-access setting) ·
  "Nothing playing" (with "as of") · the session. Every size prints "as of".
- **Refresh:** while the process runs and the widget is placed: active-sessions changes and each
  session's playback / metadata callbacks, coalesced (400 ms) and skipped when nothing shown
  changed; the hidden-apps set and `LauncherApps` changes. Access granted or revoked: the
  listener's connect / disconnect.
- **Tap:** the card opens the playing app (`LauncherApps`); previous / play-pause / next go to
  `WidgetActionReceiver`, which checks the command name and that the package has an active session
  now, then sends the command to that package's session the widget shows (the same
  playing > loading > paused rule, so with two sessions in one app, say two browser tabs, it
  reaches the one on screen).
- **Privacy:** **app name only by default** (`NowPlayingModel.SHOW_TRACK_DEFAULT`): a home screen is
  public and titles can name people (voice-message players put the sender in them). The widget then
  says "Track hidden by this widget's setting". The options offer **Show track and artist** (opt-in,
  per widget). An app **hidden in Sphere** is never named and its track never shown, whatever the
  option: the widget says "An app" / "Now playing", keeps the state and the controls, and the card
  opens Sphere rather than the app. **Owner decision:** whether showing the track should be the
  default after all.

### Needs you
- **Shows:** how many notifications are waiting, per app: the total and the number of apps (small),
  three or five apps with their counts (medium, large), "+N more apps", and "as of" at every size.
  **Never what a notification says**: Sphere's listener publishes package, profile and two flags
  only (`ActiveNotification`).
- **Work profile:** a personal-profile listener also receives the work profile's notifications, so
  an app is a package *in a profile*: the work copy is its own row, labelled the way the system
  badges that profile's apps ("Work Mail", via `LauncherApps` and `getUserBadgedLabel`), and its
  row opens the app in that profile.
- **Counting rule** (`NeedsYouModel.counted`), the same as the pages runtime's
  `launcher.notifications` channel: group summaries skipped (their children count), ongoing
  notifications skipped (media, navigation, foreground services), Sphere's own skipped.
  docs/pages.md §7 lists "which notifications count" as an open owner decision; it applies here too.
- **Privacy:** apps **hidden in Sphere** are never named and get no row: they count only in the
  total and in "+N more apps". (There is no "hidden apps" row, which would tell anyone holding the
  phone that the user hides apps.) Options offer **Counts only** (no app names at all). No badges,
  no colour for urgency; counts are calm status, sorted most first.
- **States:** "Notification access is off" (tap opens the setting) · "Connecting…" (access granted,
  listener not connected yet; with "as of") · "Nothing waiting" · the counts.
- **Refresh:** `NotificationProvider.active` (emits on every change, connect and disconnect),
  `LauncherApps` callbacks (labels), and the hidden-apps preferences (hiding an app renames its row
  at once). Coalesced, and skipped when nothing shown changed.
- **Tap:** a row opens that app in its profile (`LauncherApps`; an app with no launcher entry
  there opens Sphere's hub); the rest of the card opens **Sphere with the hub pulled down**
  (`MainActivity` + `HomeWidgets.EXTRA_OPEN = hub`).
- **The hub it opens (fixed in `:core-data` for this):** the hub used to show its **demo**
  notifications whenever its list was empty and `SphereNotificationListener.isEnabled` was false,
  and `isEnabled` checked Do Not Disturb access rather than listener access; its list also started
  empty every time it opened (a feed with no replay). Now `isEnabled` checks
  `isNotificationListenerAccessGranted`, `NotificationProvider.notifications` is a StateFlow filled
  on connect, and styled titles are read (`getCharSequence`). Demo cards remain only when
  notification access is off, a state in which the widget sends taps to the access setting
  instead. The hub's own list still differs from the widget's counts (it shows three, skips
  untitled notifications and doesn't skip ongoing ones): its design, not changed here.

### App pads
- **Shows:** up to six apps the user picks, as pads (2 / 4 / 6 by size; names in the large layout;
  TalkBack names every pad). Icons are bitmaps of the badged launcher icon, at most 144 px, one per
  app shared by all three layouts.
- **First run:** "Choose apps": tap opens the options screen. Launchers that support reconfiguring
  (Pixel's "Customize", Sphere's own widget menu) open it later; on launchers that don't, remove and
  re-add the widget to choose again.
- **Data:** the chosen entries (component + profile serial, `PadsCodec`) in SharedPreferences
  `home_widgets`; `LauncherApps.getActivityList` for every profile, read live.
- **Refresh:** `LauncherApps` callbacks (install / remove / change; these always redraw, since an
  app update can change its icon, which the "nothing changed" check doesn't see), the hidden-apps
  set, locale. A themed-icon change without a package change isn't seen until the next redraw.
- **Tap:** a pad launches its app through `LauncherApps.startMainActivity` (its own profile), after
  checking it is still a launchable activity; otherwise "That app isn't available any more".
- **Privacy:** apps hidden in Sphere aren't offered and aren't drawn even if chosen earlier.

## How it works

| Piece | What |
|---|---|
| `model/` (pure Kotlin, JVM-tested) | `WidgetKind` + `WidgetTiers` (size keys, the platform's closest-fit rule), `ClockModel`, `BatteryModel`, `NowPlayingModel`, `NeedsYouModel`, `PadsModel` / `PadsCodec`, `WidgetAction` (request codes), `MediaCommand` |
| `HomeWidgets` | process-wide entry: `init` (from `SphereApplication`), `refresh`, the single drawing thread, last-drawn keys |
| `WidgetRuntime` | the live sources, started only while a widget that needs them is placed (see each widget) |
| `Sources` | reads battery, media sessions, notification facts, hidden apps, launchable apps, labels, icons |
| `Renderers` | RemoteViews for every widget and tier; strings from resources in the current locale |
| `Providers.kt` | `HomeWidgetProvider` (goAsync + background draw) and the five providers |
| `Taps` | every PendingIntent: explicit, immutable, request code unique per widget id + action |
| `WidgetTapActivity` | not exported, no UI: launches an app via `LauncherApps` or opens the notification-access setting |
| `WidgetActionReceiver` | not exported: media commands, battery re-read |
| `WidgetOptionsActivity` | not exported, Compose: pads' apps (Sphere's search ranking for the filter), Needs you / Now playing privacy choice |
| `:app` | `SphereApplication` (calls `HomeWidgets.init`; font-scale changes), `MainActivity` / `HomeScreen` (`hubSignal`), backup rule; `HubPanel` collects the hub's list as a StateFlow |
| `:core-data` | `ActiveNotification` gained the posting profile; `SphereNotificationListener.isEnabled` checks listener access; the hub's list replays |

- **Updates are pushed:** no `updatePeriodMillis`, no alarms, no jobs. Sources live only as long as
  Sphere's process. Where data can lapse unseen (battery, now playing, needs you, and the "Nothing
  playing" and "Connecting…" states) the widget prints its "as of" time at every size, which stays
  true without a redraw. A redraw that would change nothing shown is skipped, so while Sphere runs
  the time can be older than the last check: it is when the content last changed, never later
  than the truth.
- **Providers only draw their own ids:** `APPWIDGET_UPDATE` isn't a protected broadcast, so any app
  can send one to an exported provider; `onUpdate` ignores ids that aren't that provider's, so one
  kind can't be drawn over another kind's widget (and its privacy option).
- **Taps keep the process alive until drawn:** the battery re-read and media commands use
  `goAsync` until their redraw is done.
- **Process start:** `SphereApplication` → `HomeWidgets.init` → `WidgetRuntime.sync`, so the
  sources start whenever Android starts Sphere (to bind the listener, deliver a widget broadcast,
  or show home).
- **Options storage:** SharedPreferences `home_widgets` (pads' apps, two privacy booleans, keyed
  by widget id and removed in `onDeleted`; plus the font scale last drawn), **left out of backup and device transfer**
  (`app/src/main/res/xml/data_extraction_rules.xml`): ids belong to one device. Nothing else is
  stored; no snapshot cache.
- **Colour:** day (`values/`) and night (`values-night/`) colours; night is Sphere's own palette,
  day keeps the same roles at contrasts checked by `WidgetColorsTest` (text 4.5:1 on the card and on
  pads, the level bar 3:1). Colour never carries meaning alone: every state has a glyph and a word;
  no red or green anywhere. Card radius is the system's widget radius.
- **Accessibility:** text in sp, ≥ 12 sp, one or two lines with ellipsis; tappable pads, rows and
  controls 48 dp (a pad that fills the widget's height is checked against its size key, since
  `minHeight` doesn't apply under an exact height); larger font scales pick smaller layouts (above);
  images labelled or hidden from TalkBack; `start`/`end` for RTL (`WidgetLayoutsTest` checks the
  layout files for all of this, plus that every view the renderer sets exists in that size's layout
  and that only RemoteViews-safe views are used).
- **Permissions and dependencies:** none added. Uses the existing notification-listener grant,
  `QUERY_ALL_PACKAGES` (labels) and `LauncherApps`. No network, no telemetry, no new library
  (JUnit 4 test-only, as in the other modules).

## Not built

- **All-apps globe / search shortcut** (suggested in the brief): Sphere's search is the portrait
  always-keyboard, already open on home, and the globe is a pinch away; a shortcut into either from
  another launcher seemed the least useful of the candidates. Easy to add later on the same
  pieces (`HomeWidgets.EXTRA_OPEN` would gain a `globe` value).
- **Weather:** Sphere has no weather source (the chrome's "24° Partly cloudy" is a placeholder), so
  no widget can be honest about it.
- **Album art, playback position:** templates are static and art is a bitmap of what's playing;
  left out.

## Owner-verify (none of this has been seen on a device)

1. Each widget appears in Pixel Launcher's / One UI's / Nova's picker, and in Sphere's own picker,
   with its name, description and preview (preview layout, and the image fallback where a launcher
   uses it).
2. Placing each one with Sphere **not** the default home app, and with Sphere as home (equator tile
   and hub stack).
3. Resizing each through small → medium → large in portrait and landscape: the right layout, no
   clipped text, the large Needs you showing five 48 dp rows.
4. Day and night themes; font scale 1.3× and 2× (the layout steps down rather than clipping; the
   switch happens after the font change while Sphere runs, or at its next start); an RTL language;
   TalkBack reading the clock's time, each pad's name, each row and control.
5. Gate clock: time and date tick; the next alarm appears / changes / clears when set in the clock
   app; time-zone and language changes redraw.
6. Battery: level and state follow plugging and unplugging while Sphere runs; tap re-reads; after
   Sphere's process is killed the "as of" time is the reading's; "Low" appears at the system's low
   level.
7. Now playing with Spotify / YouTube Music / a podcast app: app name by default, track and artist
   after turning on "Show track and artist"; state and controls work from the home screen (including
   from the background on Android 14+), and with two media tabs in one browser play/pause acts on
   the one shown; an app hidden in Sphere shows as "An app" and its tap opens Sphere; access off →
   "Notification access is off" → tap → Sphere's toggle in Settings → widget recovers.
8. Needs you: counts match the shade (group summaries, ongoing and Sphere's own not counted); hidden
   apps never named and no hint of them beyond "+N more apps"; a work-profile app as its own
   "Work …" row that opens the work copy; "Counts only"; row tap opens the app; card tap opens
   Sphere with the hub down showing real notifications (no demo cards with access on);
   "Connecting…" doesn't stick after a reboot.
9. App pads: choose up to six (work-profile apps too), launch each, uninstall one (pad disappears),
   hide one in Sphere (pad disappears), update one whose icon changes (pad icon follows),
   "Customize"/reconfigure from the launcher; a one-row widget in landscape keeps 48 dp pads.
10. OEM battery managers (One UI, MIUI…): whether Sphere's process and listener stay alive enough
    for the push updates; how stale the "as of" times get.
11. The options screen opened by a launcher's reconfigure (it isn't exported; launchers that start
    configure activities directly rather than through `AppWidgetHost` would fail to open it).

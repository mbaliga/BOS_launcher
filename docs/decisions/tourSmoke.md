# Whole-app tour smoke suite (wave 22)

Owner problem: integration defects kept reaching the phone that unit tests missed (Launchpad unreachable on Glance, start-up crash screens). The tour walks the real
`MainActivity` under Robolectric (the `AppSmokeHarness` pattern) and, after every step, asks the same questions: nothing threw, the expected surface is shown, at
most one full-screen surface is open, the activity is alive, no global snapshot write is pending (the Compose test clock stays idle), and the focus graph exists.
Every surface is also rotated (landscape and back) while open, and the same surface and state must be there after each.

## The suite (app module, run by `testDebugUnitTest`, about 2 minutes of test time)

| Class | What it walks |
| --- | --- |
| `TourFreshSmokeTest` | Fresh state (Glance default): start, tap pops the controls in and out, long press opens the quick sheet, landings Recents / All apps / Selected / Glance (and the cycle shortcut), Launchpad five ways (edge swipe, handle, indicator glyph, Ctrl+N, corner action) closed by sliver, Back, Esc; Recents by swipe, handle, Ctrl+R with Clear all; Radar by letter, corner, Ctrl+/ with typing, arrows, Enter, Esc; drawer filter and launch; Ground Control walked right and left; edit mode on Selected (card, carousel, add, delete page, Undo, Done); Launchpad Off and back, the background pair and the hotspots changed under an open home |
| `TourBusySmokeTest` | 500 apps, a work-profile app (best effort, see below), launch history, a media session, three notifications with real PendingIntents (the fake `PendingIntentLauncher.sender` records the send), a Launchpad group, notification access granted: the same surfaces, plus select a group, tap a notification and see the send, Recents rows and Clear all, Radar over 500 apps with Enter recorded in the history, drawer filter |
| `TourRecreateSmokeTest` | Process recreate (drawer and Radar restored when the open-surface setting is on, home otherwise, with Launchpad / Ground Control open), activity recreate with each surface open, the Lab crash row path (the report the shared handler stores, MainActivity hands over to the recovery screen, Continue, home), the library preview intent, safe mode on a seeded two-crash state with Restore last snapshot and Carry on |
| `TourPanesSmokeTest` | Ground Control: the category list built over five home styles and each category shown by the real `SettingsSpace`; every Pane and visual-picker page composed; the Left page and Hotspots rows written through their own callbacks |
| `TourMonkeySmokeTest` | Three seeds x 300 steps (fresh, busy, fresh) over taps, long presses, swipes from the four edges, letters, arrows, Enter, Esc, Back, every chord of the shortcut registry and rotations. Asserts only: nothing throws, the activity lives, no step takes more than 30 s of wall clock, and the walk reached at least four distinct surfaces (so it cannot pass by never leaving the home) |

## Defects found and fixed

1. **Launchpad opened instead of "return home" on Glance with the Recents panel open.** The Glance left handle (and the rail / waiting indicator of other landings)
   were drawn while `rightOpen`, over the sliver of the pushed-aside home card on the same edge, so a tap in the middle of the sliver opened Launchpad. Fix:
   `HomeScreen` draws the left handle block only when neither side card is open. Tests: `STR_14_onGlanceTheRecentsSliverReturnsHomeAndTheLaunchpadHandleStepsAsideWhileRecentsIsOpen`
   and the right-to-left twin (both failed before the fix).

2. **The Radar Filmstrip's drift never lets a UI test (or anything that waits for idle) settle.** Its endless drift loop called `withFrameNanos` directly, so with the layout
   open the Compose test clock never became idle (the busy monkey walk failed at step 239 with "Compose did not get idle"; a stack sample showed `Filmstrip` every frame). The loop now asks the
   platform's `InfiniteAnimationPolicy` for its frames (`infiniteFrameNanos` in `SearchLayouts.kt`): on a device no policy exists, so it is the same `withFrameNanos`; an auto-advancing test ends it.
   The monkey walk is the test (it failed before and passes now).

## Test environment notes

- The app unit tests now run with a 2 GB heap (`app/build.gradle.kts`): the tour keeps 500-app homes and many activities in one JVM, and the default 512 MB left Compose "never idle" under GC pressure.
- Assertions that depend on lists rebuilt off the main thread (Recents after Clear all) wait with real time (`waitFor`), not only with the Compose clock.

## Observations that need an owner decision (not changed)

- **Ctrl+E ("Edit home") is the keyboard move mode, not the edit card.** `LauncherCommand.EditMode` toggles `LauncherInput.editMode` ("arrows move the focused item",
  INP-07) and shows a toast; it does not open the edit card with the page carousel (HOM-12), although the shortcut sheet and Radar's "Edit home" action are named like it. The tour therefore enters the card
  through the empty-home hint, as the owner does. Whether "Edit home" should open the card is an owner decision.
- Esc on a plain home calls the Back dispatcher's fallback. On a device the system moves a root launcher to the back (Android 12+); Robolectric emulates the old
  "finish". The monkey does not send Esc or Back to a plain home for that reason.

## What this suite cannot cover (the device checklist stays honest)

- Real touch gestures: the tests inject synthetic pointer events through Compose; no real velocity, multi-touch, palm rejection, edge-back-gesture conflict or system gesture
  inset behaviour. The system Back gesture (and its predictive animation) is the activity's `OnBackPressedDispatcher` called directly.
- The IME: no soft keyboard, no composing text, no resize on `adjustResize`, no gesture-bar insets; keys come from `dispatchKeyEvent`, not from a keyboard.
- Speech input (the mic button's recogniser), Health Connect, the widget host, the real notification listener, usage access, the media session service: faked or absent
  (`NotificationProvider.publishForTest` and `MediaNow.publishForTest` stand in; `Settings.Secure` carries the access grant).
- Modal sheets and dialogs (quick sheet, app menu) are separate windows: they are closed by tapping their scrim; the activity-level Back and Esc do not reach them as on a device.
- The work-profile app is added through hidden platform constructors by reflection in the test only; whether the badge and pause chip look right is a device check. The
  profile list itself is not a separate Robolectric user.
- Rotation is `RuntimeEnvironment.setQualifiers`; MainActivity declares its configuration changes, so this proves the state survives the recomposition, not a system-initiated recreate
  (that is covered separately by `recreate()` in `TourRecreateSmokeTest`, which is not a process death either: a real process kill is simulated by closing the scenario and resetting the singletons).
- Two harness limits found by the monkey and avoided, not hidden: `Toast` from the input hub's Edit mode collector runs off a Looper thread under the Compose test dispatcher
  (Ctrl+E is not in the walk), and a long press on Radar's text field raises the platform magnifier, which needs a real surface (no long press while Radar is open).
- Frame time, jank, memory and thermal behaviour are not measured at all.

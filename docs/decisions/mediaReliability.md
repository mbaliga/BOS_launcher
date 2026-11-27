# mediaReliability (wave 60)

Owner report: "The media controls are flaky." A follow-up: [musicControls.md](musicControls.md) (wave 42) fixed several real bugs — stale
session picking, unadvertised actions making skip silently no-op, a pop-in hit-area timing bug — none of it ever confirmed on a device. The
owner's report after that lands means wave 42's fixes were real but not the whole story. This wave does not repeat wave 42; it goes after the
one thing wave 42 left standing: `MediaCommands.execute()` treated sending a command as the same thing as the command working.

## The root cause

`execute()`'s Transport route called `target.transport(cmd)` — `MediaController.transportControls.play()` / `pause()` / `skipToNext()` /
`skipToPrevious()` — and counted it a success the instant that one-way binder call was *accepted*, because Android gives no return value or
callback from that call. A transport call can be accepted by the binder and then silently ignored by the receiving app: it is not the
focused player, its remote `MediaSession.Callback` stub went stale, it races another command the user also sent, or the app's own callback
simply no-ops for a state it does not like. None of that is reliably distinguishable from success at the call site. Because `execute()`
already returned a route as having "worked," nothing ever reached the `ControllerKey` / `SystemKey` rungs wave 42 built — the ladder existed
but, for the single most common real failure, was never climbed.

## The fix: confirm, don't assume

`MediaNow.send()` (not `MediaCommands.execute()`, which stays as the pure, non-escalating decision table the wave 42 tests already cover)
now drives the ladder itself:

1. Send the current route (`MediaCommands.attempt`). A route the framework **refuses outright** (`false`, or an exception) escalates to the
   next route immediately, exactly as before — there is nothing to wait for.
2. A route the framework **accepts** is given `MediaCommands.CONFIRM_MS` (700 ms) to be confirmed by the *session's own* callback
   (`MediaPort.register`, already wired to `onPlaybackStateChanged` / `onMetadataChanged` / `onSessionDestroyed`) before it is trusted. If
   the window elapses with no confirming callback, the next route is tried.
3. What counts as confirmation is cmd-specific (`MediaCommands.confirmsPlayPause`, judged in `MediaNow.onPortSignal`):
   - **Play / Pause**: the session's own reported `isPlaying` must have flipped to the direction the tap asked for. A callback that arrives
     but does not show the flip (buffering through an unrelated state, an error) does not confirm — the ladder keeps waiting.
   - **Next / Previous**: track identity cannot be checked generically (no everything-advertises-metadata guarantee), so *any* callback at
     all from the targeted session — even one that changes nothing visible — counts as "it landed." Total silence from that session for the
     whole window is treated as the actual sign of a dropped command, which is the one thing we *can* tell apart from "it worked quietly."
4. Only once every route in the ladder has been tried **and** given its window with nothing confirming does `MediaNow.failures` fire
   ("Can't control `<app>`"). Previously this fired the instant the first accepted route turned out, later, to have done nothing — or never
   fired at all if the escalation stopped climbing after the first "success."
5. A session that disappears from the session list mid-ladder (the app died) fails the command at once instead of waiting out the timer
   (`MediaNow.dropPorts` / `adopt`).

## Timing: `CONFIRM_MS` and `DEBOUNCE_MS`

- **`MediaCommands.CONFIRM_MS` = 700 ms.** A `MediaSession` callback crosses one binder hop; the framework contract gives no number, but that
  is normally well under 200 ms. 700 ms leaves generous margin for a slow player (cold IPC, buffering) while keeping the ladder's worst case
  — three routes when the command is advertised, 2100 ms — a bound the owner can live with over the old "looks instant, is sometimes wrong."
  `GlanceNotch.PENDING_MS` (the optimistic glyph-flip's own revert window) is bumped from 1500 ms to 2200 ms alongside this, so a Play/Pause
  command that is slow but genuinely confirmed does not revert the tapped glyph only to flip it forward again a moment later. This is the one
  change to the build-73 optimistic-flip logic in this wave — the legibility/halo work merged separately in `GlanceHome.kt` is untouched.
- **`MediaCommands.DEBOUNCE_MS` = 280 ms.** Within the owner-suggested 250–300 ms. A second identical command (same `Cmd`, same session)
  arriving while the first is still pending (not yet confirmed, and less than `DEBOUNCE_MS` old) is dropped: this is the known small-target
  double-fire (two pointer events from one tap), not a second deliberate tap. Once the first resolves — confirmed, or every route exhausted
  — or `DEBOUNCE_MS` itself elapses, whichever comes first, the next identical command sends fresh. A deliberate quick double-skip (two
  separate Next taps) almost always has the first one's "any callback" confirmation land between the two presses, so it still fires twice;
  the one case this trades away is two *genuinely* separate taps landing inside 280 ms on a session that stays totally silent for that whole
  stretch, which is indistinguishable from the bounce it exists to catch.

## Stale session re-targeting (item 4 of the owner brief)

Checked, not assumed: `playPause()` / `next()` / `previous()` already call `refresh()` (re-reads the session list from the system) before
reading `current`, so a command always targets whatever the system reports *now*, not a controller object from before an app restart. A test
(`STR_06_playPauseRetargetsToTheFreshSessionWhenTheOldOneWasReplacedWithoutAListChangeNotification`) exercises this directly for `playPause`
(the existing wave 42 test already covered it for `next`); nothing needed changing here.

## Testability

`MediaScheduler` (`core-data/MediaScheduler.kt`) is the same idea as `ControlsPop`'s pure, passed-in-`now` timer, adapted for a singleton
that lives outside Compose and must schedule its own timeout rather than being re-driven by recomposition: `now()` plus a cancellable
`postDelayed`. The real implementation wraps `Handler` + `SystemClock.uptimeMillis` (same clock family as `ControlsPop`). Tests inject a
`FakeMediaScheduler` (`feature-grid`'s `MusicControlsW42Test.kt`, reused by the new `MediaReliabilityW60Test.kt`) that a test advances by
hand — "no callback arrived within the window" is simulated by never firing a task, never by sleeping a real thread.

## What is tested vs. what is not

Tested (`MediaReliabilityW60Test.kt`, `MediaNowW42Test.kt`): a fake session that accepts every route but never calls back escalates through
all three and then fails; a fake that confirms on the second route never tries the third; Play/Pause's state-must-flip confirmation vs.
Next/Previous's any-callback confirmation; the debounce dropping a bounce but not a confirmed-then-repeated tap, and expiring on its own; a
session vanishing mid-ladder failing at once; stale-session re-targeting for `playPause`.

**Not verifiable here, same as wave 42**: how Spotify, YouTube Music, Apple Music, browsers and podcast apps actually behave — whether a
real silently-dropped command looks like total silence (the assumption this whole fix rests on) or produces some callback that is not a true
confirmation either way, and whether 700 ms is actually enough margin on a real device under real load. The improvement here is
architectural — the ladder now actually climbs on silence instead of declaring victory at the first accepted call — not a guarantee that
every real player responds the way this reasoning assumes. None of this has been run against a device or a real player app.

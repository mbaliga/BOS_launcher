# w60-mediaReliability

Owner report: "The media controls are flaky." A follow-up to wave 42 (`docs/decisions/musicControls.md`): the owner says the fixes in that
wave did not resolve the real-device flakiness. Decision record: `docs/decisions/mediaReliability.md`.

## Root cause and fix

`MediaCommands.execute()` sent a command along a route (`Transport` → `ControllerKey` → `SystemKey`) and, for `Transport`, counted the call
as success the instant the one-way binder call was *accepted* — Android gives no confirmation that the receiving app actually acted on it.
A real player can silently swallow the call (not focused, a stale remote stub, a race with another command), and since `execute()` already
reported success, the working fallback routes below it were never reached. This is the single most likely source of the reported
flakiness, reasoned from the framework's own contract (a one-way `MediaController.transportControls` call has no return value and no
confirmation), not reproduced on a device (nothing here ever has been).

The fix: `MediaNow.send()` now drives the ladder itself and waits for the session's own callback before trusting a route:

- Each route gets up to `MediaCommands.CONFIRM_MS` (700 ms) to be confirmed before the next route is tried. A route the framework refuses
  outright still escalates immediately, as before.
- Confirmation is cmd-specific: Play/Pause needs the session's reported `isPlaying` to have actually flipped the right way; Next/Previous
  accepts *any* callback from the targeted session as evidence it landed (track identity cannot be verified generically; total silence for
  the whole window is the actual sign of a dropped command).
- "Can't control `<app>`" now fires only once every route has been tried **and** given its window with nothing confirming — not after one
  unconditionally-"successful" Transport call, as before.
- A session that disappears mid-ladder (app died) fails at once instead of waiting out the timer.
- A 280 ms debounce (`MediaCommands.DEBOUNCE_MS`) drops a same-command repeat that arrives while the first is still pending, so a
  double-fired tap (the known small-target bounce) cannot send two commands that race onto the player; a genuinely separate quick repeat
  still fires because the first one's confirmation (or the debounce window itself) almost always clears first.
- `GlanceNotch.PENDING_MS` (the optimistic play/pause glyph's revert window) is bumped from 1500 ms to 2200 ms to outlast the ladder's
  worst case (3 routes x 700 ms = 2100 ms), so a slow-but-genuinely-confirmed command does not flash the glyph back and then forward again.
  This is the only touch to the build-73 optimistic-flip code in this wave; the wave-60-merged legibility/halo work in `GlanceHome.kt`
  (`NowPlayingLine`/`ClockBlock`) was not touched.
- Stale-session re-targeting (owner brief item 4): checked, not assumed. `playPause()` / `next()` / `previous()` already call `refresh()`
  before reading `current`, so they were already correct; a new test confirms this for `playPause` directly (the existing wave 42 test
  already covered `next`).

New file `core-data/MediaScheduler.kt`: a postDelayed-and-clock abstraction (same idea as `ControlsPop`'s pure, passed-in-`now` timer,
adapted for a singleton outside Compose that must schedule its own timeout). The real implementation wraps `Handler` +
`SystemClock.uptimeMillis`; tests inject a hand-advanced `FakeMediaScheduler`.

## Tests

| ID | Status | Note |
| --- | --- | --- |
| STR-06 | built (architectural) | `MediaReliabilityW60Test.kt` (feature-grid, new): escalation through all three routes on total silence then failure; stops retrying once the 2nd route confirms; Play/Pause confirms only on an actual `isPlaying` flip, not any callback; Next/Previous confirms on any callback; debounce drops a bounce but lets a confirmed-then-repeated tap through, and expires on its own; a session vanishing mid-ladder fails at once; `playPause` re-targets to a fresh session. `MediaCommandsW42Test.kt` (core-data): unchanged, still green (the pure decision table — `routes`, `enabled`, `execute` — is untouched; `attempt`/`confirmsPlayPause` are additive). `MediaNowW42Test.kt` (feature-grid): one test rewritten (see below), the rest unchanged and still green. `MusicControlsW42UiTest.kt`: unchanged, still green (uses the `GlanceNotch.PENDING_MS` symbol, not a literal, so the 1500→2200 ms change needed no edit there). |

### Test updated, with reason

- `MediaNowW42Test.STR_06_theSystemKeyIsTheLastResortAndAFullFailureIsReported` → renamed
  `..._AFullFailureIsReportedOnlyAfterItsWindowRunsOutInSilence`. The old test expected the failure the instant the accepted system-key
  call returned — which is exactly the unconditional-success assumption this wave removes. Rewritten to inject a `FakeMediaScheduler` and
  advance it past `CONFIRM_MS` before asserting the failure; the calls-sent assertions (`controller:87`, `system:87`) are unchanged.

## What is and is not actually fixed

**Architecturally fixed and unit-tested**: the ladder now actually climbs past an accepted-but-unconfirmed route instead of stopping at it;
the final failure toast reflects the whole ladder having failed, not the first route having been "sent"; the debounce prevents two
same-command sends from racing; Play/Pause vs. Next/Previous confirmation is judged by the correct, cmd-specific rule; a vanished session
fails fast; stale-session re-targeting was checked and found already correct.

**Not fixed, not verifiable here, honestly unresolved**: whether a real silently-dropped command on a real device actually produces *total
silence* from the session (the assumption the whole fix rests on) or instead some callback that is not a true confirmation either way; how
Spotify, YouTube Music, Apple Music, browsers and podcast apps actually behave against this ladder; whether 700 ms is the right number
under real device load (it is reasoned from the framework's binder-hop contract, not measured); whether the owner's "flaky" report has some
other cause entirely that this reasoning missed. **None of this has been run against a real device or a real player app.** The claim being
made is narrower than "this fixes the flakiness" — it is "the ladder no longer gives up at the first accepted-but-unconfirmed call, which
was a real, identifiable bug in the previous fix," with everything about real players' actual behavior still an open question.

## Not touched

`HotspotsEditor.kt`, the page editor Settings tab, `GlanceHome.kt`'s `NowPlayingLine`/`ClockBlock` legibility/halo code (another stream's
merged work) — only `GlanceNotch.PENDING_MS` was changed, which is the optimistic glyph-flip timing this brief explicitly allowed.

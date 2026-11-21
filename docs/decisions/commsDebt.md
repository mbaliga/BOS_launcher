# Wave 33 commsDebt: paying the Comms screen's open debts

Five debts left by waves 29 to 31, in priority order. Nothing here was run on a device.

## 1. Comms survives the launcher process being killed
- Comms now stays open under an app opened from it (W31), so Back returns to it. If Android kills the launcher meanwhile, the activity is recreated and used to start on Glance.
- `leftOpen` (app `HomeScreen.kt`) is now `rememberCommsOpen(homeSignal)` (`CommsOpenState.kt`): a `rememberSaveable` boolean. A Home press (the `homeSignal` changing) closes it, as it closes every other surface; a signal that is already non-zero at the first composition is a recreation, not a press, so it does not close what was just restored. The old Home-press block in `HomeScreen` still clears it too.
- The Comms tab and the read filter: `HubState` is saved with `hubStateSaver` (`rememberSaveable` in `Launchpad.kt`): the view id and the filter name; an unknown or unavailable saved view falls back to the default. The list scroll position is saved by `rememberLazyListState`, which is already saveable, because Comms is composed (with the saved open state) after the restore.
- Not saved: expanded conversations, folded sections, an open row menu, filters by app (transient by design).
- Limit: this restores what Android restores. If the system removes the whole task, the launcher starts fresh on Glance as before.

## 2. The merge runs off the main thread
- `CommsRead.merge` (live feed, up to 5000 history entries, `dropTwins`) was computed during composition. `rememberCommsMerged` (`CommsMergeState.kt`) merges inline for at most 400 live rows plus history entries (a fraction of a millisecond, so no stale frame) and otherwise on `Dispatchers.Default` through `produceState`, keeping the previous result while the next one is computed (the live rows alone before the first one).
- Test seam: `CommsMergeConfig.dispatcher` and `instantLimit`; tests use a queue dispatcher to see the in-between state. JVM benchmark test: 5000 history entries plus 60 live rows merge in well under 100 ms (best of five after warm-up; the budget is 100 ms, a cold machine shared with builds is the risk).

## 3. The tab row fits
- ALL, UNREAD, FILES, TIMELINE: `CommsTabFit` decides, in this order: keep the mock-up slots; when they do not fit drop the slot minimum widths; when that does not fit tighten the letters, at most 0.06 em (about 1 sp); when that does not fit scroll horizontally. The selected tab is always brought into view (it also re-runs when the plan changes). At 100 percent on a normal phone nothing changes.
- The row's tag `hub.switcher` is now on the inner scrolling row (same bounds).

## 4. Usage block
- Ranked rows are at least 48 dp of real screen (`commsTarget()`, which keeps 48 dp at 75 and 125 percent) and the content is centred in them: icon (20 dp), name and 4 dp bar are unchanged, the extra is quiet padding above and below. The placeholder uses the same height so the block does not jump on load. The block is about 60 dp taller than with 36 dp rows (five rows; about 350 dp portrait, 370 dp landscape instead of about 300); the other parts and the gaps are unchanged. Supersedes the 36 dp row of the usage simplify decision.
- A selected day with no recorded time (the week has data, that day has none) shows "No recorded time" in one quiet 48 dp line in the rows' place (`usage.rows.none`) instead of an empty gap. Translated in the eight languages (`L10nW33commsDebt.kt`).

## 5. The pull-down notification shade (`HubPanel`, app module)
- Its rows reuse the Comms list row tone: `rememberHubShadeRowStyle` / `HubShadeUnreadDot` (feature-launchpad, `HubShadeRowStyle.kt`) wrap `CommsRowTone` with the dark Comms palette (the shade is a dark panel). Unread: SemiBold full-ink headline, secondary second line, accent-ink source line and the 8 dp accent dot in a 16 dp start margin that every row reserves (read and unread rows have the same layout); read: normal-weight headline at 60 percent ink, dimmer lines, no dot. Cross-fade 150 ms (instant with reduced motion); state description "Unread" or "Read".
- A row is read when its key is in `LaunchpadSettings.readKeys`, the same set the Comms list uses. The shade has no icon, so the 55 percent icon part of the table does not apply there.
- Supersedes the "Not done" line about `HubPanel` in [commsPolish.md](commsPolish.md).

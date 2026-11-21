# Wave 29 commsRead: the pinch filters by read state, read notifications stay in Comms (STR-02)

Owner: "Currently pinch in out changes the scale of the comms interface. Change that to filter to read/unread. Once a notification is read, it disappears from the comms menu. It shouldn't."

**Supersedes** the pinch part of [commsLayout.md](commsLayout.md) (the pinch no longer sets the Comms size) and the "All is the live list" decision of [hubData.md](hubData.md) (All is now the live list merged with the history).

## Why a read notification disappeared (found, reproduced, fixed)
- The ALL view was only the live list from the notification listener. Opening a notification (tap, Open) marks it read and, for notifications that ask for it (auto-cancel), cancels it; many apps also cancel on their own once read. Swiping it away in the shade did the same. It then left the listener's feed and therefore ALL.
- The persistent history did keep the entry (read, flagged dismissed), but the only places that showed history were the UNREAD tab (unread only) and the Read history (a row of the menu, no tab): so a read, cancelled notification was in no visible list at all.
- The menu's SHOW list was built from the same live list, so an app whose notifications were all read and cancelled lost its row.
- Reproduction before the fix: `CommsReadScreenCases.STR_02_w29_aNotificationMarkedReadAndThenCancelledByTheSystemIsStillListedInAll` (mark read, remove from the shade feed, assert the row is still listed) and the Show list test.

## ALL = live list + history
`CommsRead.merge(live, history, liveRead)`: one row per notification key; a key in the shade is its live row (reply, snooze, rules, intent unchanged), a key only in history is its newest history entry as a row without intent or actions. Newest first. Then the normal pipeline runs on the merged list (rules, pinned and priority groups, digests, grouping, the app filter), so rules apply to history rows too.
- Read state: a live row is read when the shade's read marks say so, or when the history entry of the same post (same key and time) is read (the shade's marks do not survive a restart; the history does). A history-only row is read as the history says. A newer message under the same key is unread again.
- A notification leaves Comms only when the user deletes it from history ("Delete from history" on a row, or "Delete history" in Settings) or the 5000-entry bound prunes the oldest. Clear all still only cancels the shade; it never touches history.
- History-only rows: tap reads it and opens the app; long-press shows chips Open, Mark as read/unread, Delete from history, Actions (rules and groups; Snooze and Reply are not offered, there is nothing to snooze or reply to); no swipe-to-dismiss (a slip must not delete history); TalkBack actions for the same. Read rows are quieter: normal weight, headline in the muted ink, the second line dimmer.
- Only the All view merges, and only when the hub is on (the app). A bare panel (previews, tests without the hub) is unchanged.

## The read filter: Unread - All - Read
One state machine in `CommsRead` (pure, tested): `ReadFilter` with three stops, `step(view, filter, dir)`. Pinch in (fingers together) one stop toward Unread, pinch out one stop toward Read. The spread must change by more than 8 percent (the dead zone, `CommsRead.DEAD_ZONE`), and one gesture moves one stop, so a long pinch does not run through both. The filter is a filter of the ALL view, not a tab; the tabs stay ALL UNREAD FILES TIMELINE. The UNREAD tab (unread history) and the Read history (menu row) are the end stops of the same machine: pinching away from them lands on ALL with filter All. Files and Timeline have no read state; the pinch does nothing there.
- The active filter shows as a quiet muted line at the top of the list ("Unread only", "Read only"; nothing for All); a tap on it resets to All.
- Haptic tick on each step through the launcher's strength-aware haptics (`rememberLauncherHaptics()` in HomeScreen, passed to the panel as `haptics`).
- Non-gesture equivalents: TalkBack custom actions on the Comms pane "Show unread only", "Show read only", "Show all"; Ctrl+U (one stop toward Unread) and Ctrl+Shift+U (toward Read) in the panel's key handler; the label's tap. The old gesture actions (show unread, toggle unread) set the same filter.
- Two-finger swipes (view switch, mark visible read) keep working and are told from a pinch by `CommsRead.pinchOf`: a pinch changes the spread by at least as much as the pair's centre moves, so a swipe whose fingers drift apart stays a swipe; once a pair has swiped it is not a pinch and the other way round; a pair that breaks up starts afresh. Mark visible as read marks only the rows on screen (not what a filter or rule hides). A chat read in the shade becomes unread again when a newer post arrives under the same key.
- The Comms size is changed by the stepper (Ground Control, the menu) and Ctrl+Plus / Ctrl+Minus only. `CommsScale.fromPinch` and its test are deleted.

## The menu's SHOW list
Every app that has any row in ALL stays listed with its total count (live + history). Under a filter the counts are that filter's counts and an app with none stays listed, dimmed (count 0), in the same order as unfiltered, so the list does not jump and the menu never loses an app because everything is read.

## Not done / device-only
Real two-finger feel (the 8 percent dead zone on a real display), TalkBack announcements and the haptic strength are verified with Robolectric only. Live rows have no "mark unread" (the shade's read marks have no un-mark); the history rows do.

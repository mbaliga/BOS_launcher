# Wave 29 commsRead status

| ID | Status | Note |
|---|---|---|
| STR-02 (Comms list) pinch filters by read state | done (Robolectric, no device) | Unread - All - Read, one stop per gesture, 8 percent dead zone, label, haptic tick, TalkBack actions, Ctrl+U / Ctrl+Shift+U |
| STR-02 read notifications stay in Comms | done | ALL = live + history merged by key, newest first; stays after read, app cancel, shade swipe; leaves only by delete from history or the 5000 bound |
| STR-01 (Comms menu) Show list | done | every app with a row stays listed; filter counts, zero apps dimmed |
| Comms size | unchanged | stepper and Ctrl+Plus/Minus only; the pinch no longer changes it |

Root cause: ALL was the live listener feed only; opening a notification cancels it (auto-cancel, or the app), then it was in no visible list (UNREAD shows unread history only, Read history had no tab) and its app lost the Show row.

Device-only: the pinch feel and dead zone on a real display, TalkBack announcements, haptic strength.

## Tests changed or deleted
- core-data CommsScaleTest CMS_06 (pinch ratio to detent) deleted with `CommsScale.fromPinch`.
- HubTest HUB_14 (pinch sets the size) replaced by the read-filter pinch tests; HUB_14 tiny spread test now asserts no filter step and no size change; HUB_17 and HUB_20 pinch asserts now check the filter and an unchanged size (HUB_20 also the label reset); the test host passes `onPinch`.
- CommsScaleCases CMS_11 (pinch changes the size and the layout follows) replaced by "the pinch leaves the size alone".
- CommsDesignCases: the history store is built from the items each screen shows (ALL now includes history, so a store holding other notifications would add rows).

## New tests
- CommsReadLogicTest (JVM): every filter transition, dead zone, merge (union, de-dup, order, read kept after leaving the shade, same-post read, delete), filtered counts, bound.
- CommsReadScreenPortraitTest (Robolectric): reproduction of the disappearing notification, Clear all keeps history, Show list keeps apps (all-read-and-gone; zero under Unread), history-only row tap / chips / delete, live row keeps its chips, pinch in and out with pointer injection (once per gesture, scale pref unchanged), label tap, TalkBack actions, Ctrl+U, two-finger swipe still switches views, screenshots `comms-read-portrait-*.png` (all, all with gone rows, unread, read, menu with a zero-unread app).

## Adversarial review (second pass): defects found and fixed
Each has a test that failed before the fix (screen cases in CommsReadScreenCases, pure rules in CommsReadLogicTest).
1. A two-finger swipe whose fingers drift apart by more than 8 percent was read as a pinch (the view switch never fired). `CommsRead.pinchOf` now needs the spread to change at least as much as the pair's centre travels. Test: aTwoFingerSwipeWhoseFingersDriftApartIsStillASwipeNotAPinch.
2. A pair that broke up (one finger lifted, a finger landed elsewhere) was measured against the old pair's spread and could fire a pinch. The reference now resets when fewer than two fingers are down. Test: aFingerThatLiftsAndLandsElsewhereDoesNotMakeAPinchAgainstTheOldPair.
3. "Mark visible as read" (two-finger swipe down, Ctrl+Shift+M, the menu) marked every unread row of the merged list, including rows hidden by the Read filter, the app filter or a rule. It now marks what is on screen. Test: markVisibleAsReadOnlyReadsTheRowsOnScreenNotWhatTheFilterHides.
4. A chat read in the shade stayed "read" when a new message arrived under the same key (the in-memory read mark is by key), so a new message was hidden under Unread only. The merge now takes the history into account: an unread newest post after an older read post makes the row unread. Tests: aLiveRowReadInTheShadeIsUnreadAgain... (JVM) and aChatReadInTheShadeIsUnreadAgainWhenANewMessageArrives... (screen).
5. The TalkBack read-filter actions did nothing visible from Files or Timeline (the filter belongs to the All view). They now switch to All. Test: theTalkBackFilterActionsFromFilesTakeTheUserBackToTheAllView.
6. Dismissing a live row (swipe, menu) looked like a no-op now that the row stays as history. It says "Cleared from the shade". Test: dismissingALiveRowSaysItWasClearedFromTheShadeAndTheRowStays.

Not changed: the merge runs on the main thread when the feed or the history changes (a sort of up to 5000 small entries, no I/O); a pinch landing on an end stop gives no haptic.

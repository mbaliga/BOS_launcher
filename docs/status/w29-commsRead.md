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

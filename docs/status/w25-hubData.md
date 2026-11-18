# Wave 25 hubData status

Nothing here is device-confirmed. Tests are JVM and Robolectric (pointer and key injection); screenshots read: switcher, filters, options, expanded row.

| ID | Status | Note |
|---|---|---|
| HUB-01 | done, JVM-tested | Persistent notification history: file store, bounded 5000, compaction, restart, dismissed flag, off-thread (NotificationHistoryTest) |
| HUB-02 | done, JVM-tested | Read/unread transitions: row tap, markRead hook from the live list, mark all, undo |
| HUB-03 | done, JVM-tested | Clear all keeps history; Delete history needs a confirmation; single delete |
| HUB-04 | done, JVM-tested | Privacy class: never exported, excluded from backup (file under usage/, pref file launchpad_hub) |
| HUB-10 | done, Robolectric | Views All/Unread/Read/Files/Usage/Timeline in one place; 48 dp switcher; old sheet removed; tiles, quick sheet and menu route to the hub |
| HUB-11 | done, Robolectric | Default view persists and is the opening view |
| HUB-12 | done, Robolectric | History rows: expand, open, flip read mark, delete; live options for rows still in the shade |
| HUB-13 | done, Robolectric | Quick filters (range, unread, app, sender), per view |
| HUB-14 | done, Robolectric | Pinch density and grouping, persisted; button equivalents |
| HUB-15 | done, Robolectric | Two-finger swipe left/right/down with undo; one-finger scroll and swipes untouched |
| HUB-16 | done, Robolectric | Options buttons and hardware keys |
| HUB-20 | done, Robolectric | In the app All is the live grouped list (groups, digests, row actions kept); Unread and Read are the persistent history |
| HUB-17 | done, Robolectric | Wired into LaunchpadContent (slot, gestures), Clear all keeps history |
| HUB-18 | screenshots | feature-launchpad/build/screenshots/hub-*.png |

Changed tests: none weakened. HomeLandingItemsW24Test HOM_18 (quick sheet to panels) now taps the hub's Timeline and Usage tabs, scrolling them into view (the old sheet is removed by this item). The old HomePanelsSheet had no test of its own. Device-only: real gesture thresholds, Launchpad row look beside the launchpadHub stream's rows, merge of the viewSwitcher slot.

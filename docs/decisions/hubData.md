# Wave 25 hubData: persistent history and views in the Launchpad (owner item 2, data and navigation part)

## What the owner asked
Persistent history with read/unread views; Files, Usage and Timeline rolled into the Launchpad; a default view; quick filters; pinch and two-finger gestures. The row look is the `launchpadHub` stream's work; this stream builds the data, the views, the switcher and the gestures and adds them to the Launchpad with a small edit.

## History store (`core-data` NotificationHistory.kt)
- One entry per post: key, package, app label, sender (else title), title, text, conversation, time, read, dismissed. Id is `key|time`: a chat that posts a new message under the same key is a new entry; a repeat of the same text under the same key is not.
- File `files/usage/notification-history.log`, one line per change, last line for an id wins. Bounded to 5000 entries (newest kept); once the file holds 5500 lines it is rewritten with one line per entry. Reading, appending and rewriting run on a background executor, never on the caller; the in-memory list changes at once. Changes made before the file is read are merged in.
- Hooks: `NotificationProvider.listedBy` (the listener's feed) calls `NotificationHistory.observe`; `LaunchpadSettings.markRead` (every open, tap and "Mark as read" in the live list) calls `NotificationHistory.markRead`. The listener service creates the store; the Launchpad creates it too if it opens first.
- Not recorded: ongoing and progress notifications (music, calls, a running download): they are state, not history.
- Dismissed: when a key leaves the shade its entries are flagged `dismissed`; they stay. If it comes back the flag clears.
- Read state is set by: tapping a history row (also expands it), opening it, Mark as read, Mark visible as read (button, Ctrl+Shift+M, two-finger swipe down). "Clear all" in the Launchpad only cancels the shade: history is untouched (tested). Entries leave history only through "Delete from history" on one entry or "Delete history" (two taps: the second is a confirmation shown inline).

## Privacy class (decision)
Message text is the most private data the app holds, so the history is **never exported** and excluded from backup and device transfer: it lives under `usage/` (already excluded in `backup_rules.xml` and `data_extraction_rules.xml`) and its path is in `StateSchema.NEVER_EXPORTED_FILES`. Tests: `HUB_04_...`. Consequence: a new device starts with an empty history.
The hub's own settings (default view, density, grouping) are in pref file `launchpad_hub`, classified in `BACKUP_EXCLUDED_PREF_FILES` and the three backup lists: they are view habits tied to the history, and the existing exporter allow-list is not widened.

## Views
`HubView`: All, Unread, Read, Files, Usage, Timeline. **In the app the All view is the Launchpad's own live list** (everything in the shade now, with groups, digests, priority and the row actions; the `launchpadHub` restyle shows there), so none of the STR-13 behaviour is lost; **Unread and Read are the persistent history** (shade and dismissed entries together, newest first, day headers, quick filters). `HubState(liveAll = true)` selects this; with `liveAll = false` (previews, tests of the hub body) All is the history too. The hub is switched on only when the host passes `panelBody`; a bare `LaunchpadPanel` is unchanged. The last three are the existing panels (`HomePanelsBody` in the app module, extracted from the old sheet: no duplicate code) passed to the Launchpad as `panelBody`; the old `HomePanelsSheet` is deleted. Every old entry point now lands on the hub: the home panel tiles (`liftoff-panel://files|usage|timeline`), the Home quick sheet "Files, usage and timeline", and the shortcut menu entry go through `LaunchpadHubNav.request(view)` and open the Launchpad on that view (when the left page is not the Launchpad the hub is shown by itself, full screen). Without `panelBody` (previews, tests) only the three history views exist.
Default view: "Set as default view" in Options stores the current view; each time the Launchpad opens it starts there (a request from a tile overrides once).
Quick filters (one tap, kept per view while the Launchpad is open, not persisted): Today, 7 days, Unread only (All view only), the six busiest apps, the six busiest senders, Clear filters.
Live notifications: a history row whose notification is still in the shade opens through the Launchpad's own open path (intent, auto-cancel) and offers "More options" (the existing reply, snooze, rule and group sheet). Rows of dismissed notifications open the app.

## Gestures (hub active)
- One finger is never touched: list scroll and normal taps work; a horizontal drag across a row is not a tap (`hubTap`).
- From the moment a second finger is down the pair belongs to the hub (consumed, so the list does not scroll and the one-finger close drag does not fire).
- **Pinch** (spread changes by more than 18 percent, once per gesture): fingers apart = looser rows (Compact, Comfortable, Expanded); once Expanded, a further pinch apart makes grouping finer (App, Day, Sender). Fingers together = denser rows, then coarser grouping. Persisted. This replaces the old pinch actions (show unread, collapse all) while the hub is on; they remain for a Launchpad without the hub.
- **Two-finger swipe** (both fingers move about 56 dp the same way, spread unchanged): left = next view, right = previous view (stops at the ends), down = mark the visible items read with an undo bar (6 seconds, restores exactly what changed).
- In the live All view the old pinch actions (show unread, collapse all, from the Launchpad gesture settings) still apply, the hub's density does nothing there, and two-finger swipe down / Mark visible as read marks the live rows read (a toast, no undo: the live read marks have no un-mark; the history views have the undo bar).
- Equivalents without gestures: the switcher tabs, Options (density, group by, mark visible as read, default view, delete history), the Undo button; keyboard Ctrl+Right/Left (views), Ctrl+Plus/Minus (density), Ctrl+Shift+M (mark visible read). The shortcut registry holds the global launcher commands; in-panel keys live with the panel (as the Launchpad's own list keys do), so no registry entry was added.
- Options are inline, not a popup, so nothing is clipped at a screen edge.

## Not done / limits
- Verified with Robolectric pointer injection only; no device run. Real two-finger feel (thresholds) is device-only.
- History records only while notification access is on and the listener is bound; the hub says so when access is off.
- History rows have no swipe actions (tap, expand, buttons); the live All list keeps its row swipes.
- Merge note: the `viewSwitcher` slot parameter of `LaunchpadContent` is declared here too (same name and type as the launchpadHub stream); the merger keeps one and places `HubSwitcher` in the top bar.

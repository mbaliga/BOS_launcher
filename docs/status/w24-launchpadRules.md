# Wave 24 stream launchpadRules: Launchpad grouping, priority, the card, notification taps

Robolectric and JVM tests only. Nothing here was run on a device. Decisions: docs/decisions/launchpadRules.md.

## What was found and done

1. **The Groups manager was not reachable from Launchpad** (only Ground Control > Launchpad > Grouping & rules, or one row at a
   time). Added "Groups and rules..." to the Launchpad header menu. It opens the manager over the column (two taps). Back, Escape
   and Done close it.
2. **"Pick a person and pull their conversations from every messaging app" did not exist as a manual action.** Each group card now
   has "Add people..." (people in the feed, merged across apps, with the app names shown), a typed name, a members list with Remove,
   and "Link another name".
3. **Priority did nothing during system Do Not Disturb.** While Do Not Disturb is on, only Priority groups count towards Liftoff's
   edge indicator and the Glance dot. Launchpad says so and still lists everything. A switch in the manager controls this (on by
   default, persisted and exported in the groups document). The setting is read only through the public interruption filter.
4. **End-to-end coverage through the real panel.** None of the earlier tests drove `LaunchpadPanel` from its own UI with a seeded
   feed. `LaunchpadGroupsEndToEndW24Test` now does.

5. **The translation tests were already red at the base commit.** The label "Previous" in `GlanceHome.kt` (the wave 22 player notch) had no translations. It is now in `L10nW24launchpadRules.kt` in all 8 languages, so `TranslationCoverageTest` and `W13TranslationDeepScanTest` pass again.

The new tests were written alongside the code. The core-data ones did not compile before the API existed (that was their failing
state). No run with the fix reverted was made for the UI tests.

## Second review (adversarial)

Read `git diff 1d71394..31e6245` and the code around it. Each new test below was run red before its fix (two failed, the
others are strengthened checks that already pass), then green.

Found and fixed:

1. **The Groups manager's Done button sat under the home card's return zone.** The overlay was full width while the column
   behind it is inset from the card sliver. At 400 dp, Done ended at 384 dp and the return zone starts at 352 dp, so a tap on Done
   took you home. The overlay now takes Launchpad's `endInset` like the column does
   (`LaunchpadGroupsReviewW24Test.STR_01_besideTheHomeCard...`, red then green).
2. **"Link another name" could move the person out of the group it was done from.** It called `linkSenders(label, ...)`. The
   merge keeps whichever assignment came first, so linking Priya (already in Family) to Ravi from Work put Ravi in Family. It
   also linked by display label, not by person id. New `GroupPeople.linkInto` links by id, keeps the person's contact, and
   re-assigns the person to the group the link was made from (`STR_13_linkingAnotherNameFromAGroupKeepsThePersonInThatGroup`,
   red then green, plus `StrGroupsReviewW24Test` in core-data).
3. **The Escape test could not catch Launchpad closing.** It used `onDismiss = {}` and only checked that `lp.root` existed. A
   counter now asserts that Launchpad was not dismissed. There is also a new Back test: the manager's `BackHandler` wins over
   Launchpad's predictive back.
4. **The background-start test accepted any non-null bundle.** It now also checks the bundle is not empty and has the same keys
   as `PendingIntentLauncher.options()`.

Found and not fixed (small, recorded here):

5. The people picker lists senders from every notification in the feed, including apps hidden by a Hide rule (it gets the raw
   `items`, not the visible list).
6. Picking a person does nothing, and says nothing, once the stored-people cap (`MAX_PEOPLE`) is reached. A members row for a
   Person assignment whose stored person is gone shows the raw id after "p:".
7. `GroupsSettings.forgetInMemoryCopy()` is a test hook in production code. It publishes an empty config to collectors. Only
   the tests call it.
8. Tapping a person who is already in the group (shown with a check mark) does not toggle them out. Removing them goes through
   the members list.

Verified by reading the code, no change needed: Glance's quiet dot (`HomeScreen` with `quiet = true`) uses the same
`LaunchpadWaitingIndicator` path as the edge indicator, so the Do Not Disturb rule applies to both. The new `priorityBreaksDnd`
goes through `StateCodec` in the groups part (`GroupsJson`) on export and import. The manager overlay is a sibling of
Launchpad's root, so Launchpad's own key handler never sees Escape.

| ID | status | note |
|---|---|---|
| STR-01 | built | Review: verified. Fixed the Groups manager overlay, which ignored the card inset so Done sat under the home card's return zone (now tested). First pass: verified, no change: the Launchpad card rests on the right with only a sliver, and the notifications are full screen on the left. The new `CardMirrorW24Test` (app) ties the real `EditCardGeometry` side to `CardEdge.forEdit`, and checks the edit card is the mirror in LTR and RTL at three window sizes. The real drag and finger feel are device only. |
| STR-02 | built | Review: verified. The background-start assertion now checks the actual options keys, not just a non-null bundle. First pass: verified through the real panel: a tap on a grouped row sends that notification's own PendingIntent through `PendingIntentLauncher` with the Android 14+ background-start options, and marks the row read (`STR_02_aTapOnAGroupedRowSends...`). Grouped rows are at least 72 dp (`STR_02_groupedRowsAreRoomyAt72dpOrMore`). A Quiet group sits last and is not folded. Whether a given third-party app accepts the opt-in is device only. |
| STR-03 | built | Review: no change in this area this wave. Status carried over, and its existing tests are in the green full run. Unchanged. The row menu's group actions ("Move to group...", "Make a rule from this") are as before. Not re-tested this wave beyond the existing `STR_03_*`. |
| STR-04 | built | Review: no change in this area this wave. Status carried over, and its existing tests are in the green full run. Unchanged. Priority still sits above the per-app digest and silent rules (asserted again with Do Not Disturb on, `STR_13_priorityStillBreaksThroughADigestWhileDndIsOn`). The new `priorityBreaksDnd` travels in the existing groups part of the export. |
| STR-05 | built | Review: no change in this area this wave. Status carried over, and its existing tests are in the green full run. Unchanged. The end-to-end tests seed the notification-access grant the way the system stores it. |
| STR-07 | built | Review: no change in this area this wave. Status carried over, and its existing tests are in the green full run. No new screenshots this wave: the new tests assert the layout (section and row bounds) instead of images. The existing screenshot cases are unchanged. |
| STR-08 | built | Review: the Escape test could not see Launchpad close (`onDismiss = {}`). A dismiss counter now proves only the manager closes, and Back is tested the same way (`LaunchpadGroupsReviewW24Test`). First pass: Escape closes the in-Launchpad Groups manager before it can reach Launchpad's own Escape (`STR_13_theGroupsManagerOpensFromLaunchpadAndEscapeClosesIt`). Focus goes into the manager on open and back to the column on close. A hardware keyboard is device only. |
| STR-09 | built | Review: verified by reading the code. Glance's quiet dot shares `LaunchpadWaitingIndicator`, so the rule covers it (only the non-quiet variant is driven end to end). First pass: with Do Not Disturb on and a Priority group present, the indicator counts only Priority items, for example "1 waiting, 1 priority". Off, it counts as before (end-to-end and core-data tests). Whether `ACTION_INTERRUPTION_FILTER_CHANGED` reaches the not-exported receiver on the owner's phone is device only; the check on resume covers it if not. |
| STR-10 | built | Review: no change in this area this wave. Status carried over, and its existing tests are in the green full run. Unchanged, not touched. |
| STR-11 | built | Review: no change in this area this wave. Status carried over, and its existing tests are in the green full run. Unchanged. The digest widget does not apply the Do Not Disturb breakthrough (it is a home widget the user placed, not the edge indicator). This is stated here, not changed. |
| STR-13 | built | Review: fixed "Link another name", which could move the person into another group and linked by label (now `GroupPeople.linkInto`, tested in the UI and core-data). Open small gaps 5 to 8 are listed above. First pass: audited against the owner request. Rule-based: a Priority "Family" group, my last name and the rule "sender last name is my last name", all made in the Launchpad UI, regroup the seeded feed. Priya's chats from Chat and SMS land first in Family. Deleting the rule in the UI falls back to "Everything else" with nothing lost. Manual: a person picked in the manager pulls their WhatsApp and Chat conversations into Work, and that survives a restart (the stores drop their in-memory copy and a new panel reads the file back). Priority ordering in the panel is Priority, Normal, built-in, Quiet. Strings are in 8 languages (`L10nW24launchpadRules.kt`). Still not done, as before: a group icon choice, drag to reorder, and contacts reading on a real device. The model's "Quiet groups folded" is not applied by the column (owner complaint, STR-02). The Spec row wording still says "folded". |
| STR-14 | built | Review: no change in this area this wave. Status carried over, and its existing tests are in the green full run. Unchanged. The Recents card shares `CardEdge` with Launchpad and edit; the mirror test covers Launchpad against edit, and Recents stays covered by `RecentsCardGeometryTest`. |

## Residue (device or owner)

- On the phone: turn on Do Not Disturb with a Priority group set up, check the edge indicator and the Glance dot show only Priority items, then turn Do Not Disturb off and check that everything counts again.
- On the phone: tap a WhatsApp or Signal notification in a group section and check that the app opens on the chat.
- "Restart" in the test means the stores drop their in-memory copy and re-read the file. Killing the real process is device only: force-stop Liftoff, reopen it, open Launchpad, and check the groups are still there.
- Owner: should the digest widget also follow the Do Not Disturb breakthrough? Should the Spec's "Quiet groups folded" wording be changed to "listed last"?

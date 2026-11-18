# Launchpad grouping, wave 24: the manager inside Launchpad, the people picker, Do Not Disturb breakthrough (STR-13)

Status: built and Robolectric-tested; nothing here was run on a device.

## What the owner asked

"Launchpad needs rule-based AND manual grouping: group chats from one person whatever the platform (manual: pick a person and pull
their conversations from every messaging app into one group), groups like Work, and rules such as 'anyone with the same last name
as mine is Family' that give priority access (they surface first, can break through quiet hours/do-not-disturb within the
launcher's own UI)." Plus: reachable from Launchpad in a few taps, persisted, exported, tested end to end.

## What was there (wave 10 to 13) and what was missing

The model, rules, precedence, Priority semantics, persistence and export were all built (docs/decisions/groups.md,
docs/decisions/launchpadGroups.md). Missing or wrong when audited:

1. **Reachability.** The Groups manager lived only in Ground Control > Launchpad > Grouping & rules. Inside Launchpad only a row's
   menu reached group actions. Now the Launchpad header menu has "Groups and rules..." (two taps), which opens the same manager
   over the column; Back, Escape and Done close it and the column regroups live behind it.
2. **"Pick a person".** A person could only be put in a group from one of their notifications (a row's "Move to group..."). Each
   group card in the manager now has "Add people...": the people in the current feed, each merged across every messaging app
   (the apps are named next to the name), and a tap puts all their 1:1 chats in the group with one Person assignment. A name can
   also be typed for someone with nothing waiting. The card lists what was put there by hand (people, apps, single chats), each
   with Remove, and a person can get another name linked ("Link another name", the same human under another name in another
   app). Group chats are not offered as people (a Person assignment never moves a group chat, as before).
3. **Do Not Disturb.** "Priority" bypassed Liftoff's own digest and silent rules (the launcher's quiet hours) but nothing reacted
   to system Do Not Disturb. Now, while Do Not Disturb is on, only Priority group items count towards Liftoff's edge indicator and
   the Glance waiting dot (they "break through"); the Launchpad list still lists everything, Priority first, and says
   "Do Not Disturb is on. Only Priority groups light the edge indicator." It is read through the public
   `NotificationManager.getCurrentInterruptionFilter` (no permission, no policy access) on every resume and on
   `ACTION_INTERRUPTION_FILTER_CHANGED`. Liftoff never changes Do Not Disturb and never makes a system notification sound.

## Judgement calls (owner may change)

- The breakthrough is a switch in the manager ("Priority groups break through Do Not Disturb"), default on, stored in the groups
  document (`priorityBreaksDnd`, written only when off, so older documents and exports read the same) and therefore exported,
  imported and kept out of cloud backup with the rest of the groups.
- With no Priority group defined, Do Not Disturb changes nothing (holding everything back would only hide the indicator).
- Do Not Disturb filters "priority only", "alarms only" and "total silence" all count as on; "unknown" counts as off.
- The list is not filtered during Do Not Disturb: pulling Launchpad open is a deliberate act.
- Quiet groups are listed last and not folded (STR-02 wave 11, owner complaint "not collapsed"); the old "Quiet groups start
  folded" of the model is not applied by the column. Unchanged here, restated because the STR-13 row still says "folded".

## Tests

`StrGroupsW24Test` (core-data: breakthrough, switch, persistence, picker, members, linking), `LaunchpadGroupsEndToEndW24Test`
(feature-launchpad, the real `LaunchpadPanel` with a seeded feed: a rule made in the UI regroups, deleting it falls back, a person
picked in the manager survives a restart of the stores, Priority ordering, the breakthrough in the indicator and the column, the
manager from the header menu and Escape, a row tap through `PendingIntentLauncher` with the opt-in, roomy rows), `CardMirrorW24Test`
(app: the Launchpad card on the right, the edit card its mirror).

## Device only

Whether `ACTION_INTERRUPTION_FILTER_CHANGED` reaches a not-exported runtime receiver on the owner's phone (the resume check covers
it if not), and how Do Not Disturb schedules from the system show up in the filter.

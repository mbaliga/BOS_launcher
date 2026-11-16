# Launchpad groups: design decisions (STR-13)

Code: `core-data` `LaunchpadGroups.kt` (model and pure edits), `PersonNames.kt` (normalisation, last names), `GroupResolver.kt` (people, conditions, precedence, previews, summaries), `GroupedView.kt` (view builder and priority semantics), `GroupsStore.kt` (JSON codec and the live store), `ContactsDirectoryLoader.kt` (optional contacts grant, public ContactsContract only).

## Model

- Group: id, name (max 40), icon, colour, order, priority (Priority / Normal / Quiet), optional delivery override. At most 50 groups, 200 rules, 500 people, 2000 assignments.
- Person: id, name, aliases (sender names from any app), optional contact key. Without a stored Person a sender still has an implicit identity from its normalised name ("n:<key>"), or from a matching contact ("c:<key>"), so the same name in two apps already merges with no setup.
- Assignment: kind (Person, App, Conversation), target, group id or null (null means "no group": the row stays in the built-in section and no rule may move it).
- Rule: ordered conditions (AND), target group, enabled, optional name. A rule with no conditions, a disabled rule, or one whose group is gone matches nothing.
- Condition: kind, value, optional from/to minutes, optional negate.

## Precedence (deterministic)

1. Manual assignment: a Conversation assignment, then a Person assignment, then an App assignment. A manual assignment always beats rules.
2. Rules in list order; the first enabled rule whose conditions all match wins.
3. Otherwise the built-in section.

A Person assignment applies to one-to-one chats only (a group chat has several senders, so the chat is moved with a Conversation assignment). Conditions such as sender name or last name read the sender literally, group chat or not, so a rule can still be aimed at them and the preview shows the effect.

## Conditions that cannot be evaluated

Contact conditions without the grant, and "last name is mine" with no last name set, never match, even when negated, so "not in my contacts" cannot sweep everything into a group when the grant is missing. Time-of-day uses the time the notification was posted, so a row does not hop groups as the clock moves.

## People across platforms

Names are cleaned of emoji, symbols, decorative letters and bracketed or "- WhatsApp" style suffixes, then folded for case and diacritics (marks are only folded for Latin, Greek, Cyrillic, Arabic and Hebrew so Devanagari and other scripts keep their letters). "Last, First" is turned around for the identity key. "This is the same person as..." joins two names into a stored Person and moves existing assignments onto it. With the contacts grant a sender matching a contact name, alternate name, nickname, phone (last nine digits) or email resolves to that contact, which a stored Person can be linked to.

## Last name

Handles "Last, First", trailing Jr/Sr/III, particles (de, van, von, della, bin...), hyphenated and multiple surnames, and single names (no candidates: no match, no crash). The user's own last name expands to its whole, its last word, its hyphen parts and each non-particle word, and matches when any form equals a form of the sender. Known trade-off: with a two-word surname the user also matches people who share only one of the words. A person who writes three-word names where the middle word is a surname (Spanish style) is matched by typing both surnames.

## Priority semantics

Priority groups sort first; their notifications bypass the per-app digest and the silent-in-Launchpad policy and the Silent rule, and may raise the waiting indicator with the group accent (`GroupLogic.priorityWaiting`, `priorityAccent`). The group's own override can be "silent in Launchpad"; a Digest override on a Priority group is read as Immediate. For Normal and Quiet groups the override is only the default for apps with no policy. Hide, auto-clear and snooze still apply. Quiet groups come last and start folded. None of this touches system notifications or Do Not Disturb.

Released digest items of an app that sits in a group show in that group, not in the digest section (which belongs to the built-in section).

## Persistence

A separate preference file `launchpad_groups`, written by `GroupsSettings`, carried in the state file under `launchpad.groups` (additive in schema 2, like the dock section; a file without it leaves the live groups untouched). The file is listed in `StateSchema.BACKUP_EXCLUDED_PREF_FILES` and in both backup rule files: exported with the config, never in cloud backup or device transfer, because it holds people's names and the user's last name. Deleting a group, a rule, an assignment or a person link snapshots first through `DestructiveEdits` (CFG-04); deleting a group also removes the assignments and rules that pointed at it.

## Not done in step 1

All UI, translations, Robolectric and screenshot tests, device checklist rows, and wiring the resolver (with the contacts index loaded off the main thread) into the Launchpad composables and the digest widget.

# Launchpad groups (wave 10, STR-13)

## What changed

- New requirement STR-13 "Groups" (M) in Spec section 11.
- STR-02: the section titles in the background column are the user's groups when any exist (Priority groups first, the built-in section, Quiet groups folded last); the by-app / by-conversation grouping stays inside a section. With no groups the column is as before.
- STR-04: a Priority group sits above the per-app delivery rules (its rows bypass the app digest and silent-in-Launchpad). A Normal or Quiet group's delivery override is only the default for an app with no rule of its own.
- STR-03 and the context menu gain group entries (step 2); STR-09 may raise the indicator with the accent of a Priority group (step 2); CFG-04 snapshots before a group, a rule, a link or an assignment is removed.

## Why

Owner request (wave 10): "Launchpad needs to have rule based and manual grouping options. So I should be able to group chats from a person, no matter the platform, to work, or set a rule that anyone with the same last name as mine is family, so they have priority access to me, etc."

## What the old spec said

STR-02: "Notifications grouped by app and conversation, newest first", with the section titles being those groups. STR-04: per-app rules decide immediate, digest or silent with nothing above them. Both still hold when no group is defined; the digest and silent behaviour is unchanged for everything outside a Priority group.

## Step 1 scope (this commit)

Data and logic only: the model, people identity, rule engine, last-name logic, the Priority semantics in the view builder, persistence, export/import, migration, snapshots, backup classification, the performance budget and the `STR_13_*` tests. The UI (sections in the column, row actions, manager, editor, translations, screenshots) is step 2. Design details are in `docs/decisions/launchpadGroups.md`.

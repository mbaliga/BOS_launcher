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

## Step 1 scope

Data and logic only: the model, people identity, rule engine, last-name logic, the Priority semantics in the view builder, persistence, export/import, migration, snapshots, backup classification, the performance budget and the `STR_13_*` tests. The UI (sections in the column, row actions, manager, editor, translations, screenshots) is step 2. Design details are in `docs/decisions/launchpadGroups.md`.

## Step 2 (UI) additions

- STR-03 gains `RowCommand.MoveToGroup` ("Move to group...") and `RowCommand.GroupRule` ("Make a rule from this") in the one menu definition, so palette, long press, secondary click and the Menu key stay identical. The sheet offers the scope (this chat only, always this person, always this app), the groups, "Keep out of all groups", "Back to automatic", a new-group field, "This is the same person as..." and "Make a rule from this". The person scope is hidden for group chats because a person assignment applies to 1:1 chats only.
- STR-09 changed as written in the Spec: Priority items count even when their app is on a digest or silent in Launchpad, a Quiet group's items never count (decided here; the old spec counted every unread item that the app's own rules let through), and the indicator tints with the group colour plus a star and a spoken "N priority".
- CFG-04: the Spec row now names the group deletions that take a snapshot.
- The group sections are one `Section` per group in the existing selection and keyboard-focus model, so the large and dark selected title, the small and grey others, STR-08 arrows and Enter all work unchanged; the by-app / by-conversation sections sit inside as small sub-headings (only when a group has more than one). The built-in section is titled "Everything else". A Priority or Quiet group shows a text badge as well as its colour dot.
- Matching in the column and the indicator runs on `Dispatchers.Default`; until the first result the column shows nothing rather than the wrong sections. The home-screen digest widget uses the same resolver with the last loaded contacts index (it never reads contacts itself).
- The contacts index is loaded off the main thread, cached for two minutes, and only when the grant exists (`rememberContactDirectory`). The grant is asked for only through `GrantHooks.ask` (CFG-07).
- A bug found on the way: the Launchpad key handler was a reference to a local function, and two such references compare equal whatever they captured, so a handler built while the sections were still empty was never refreshed (arrow keys did nothing). The handler now reads the latest function through a holder.
- Two strings of the existing Launchpad ("No unread notifications", "All caught up") were used through `L10n.tr(if ...)`, which the translation test cannot see, and had no translations; they are now in `L10nW10groups`.

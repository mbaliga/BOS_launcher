# commsRename (wave 27)

Owner: "Rename Launchpad to Comms ... Comms menu (previously Notification menu)".

## Mapping

| Before | After |
| --- | --- |
| Launchpad (any user-visible text, TalkBack label, toast, settings row or category, search result, tutorial or help text, checklist wording) | Comms |
| Notification menu (panel title, content description, click label) | Comms menu |
| Hindi transliteration of Launchpad | Comms (kept in Latin letters in all eight languages) |

L10n keys are looked up by their English text, so every English key and every translation value that said Launchpad
was changed together in `core-design/.../L10n*.kt`; `TranslationCoverageTest` stays green. Undo snapshot labels
("remove Comms rule", ...) changed with them.

## Search

Searching "launchpad" (Ground Control, `SettingsIndex` alias table) or in Radar (action keywords) still finds Comms.
The alias is hidden: it is never drawn.

## Deliberately not renamed

Kotlin package names, the module `feature-launchpad`, class and file names (`Launchpad`, `LaunchpadRules`, ...), enum
names (`LeftPage.Launchpad`, `CornerActionId`, action id `OpenLaunchpad`), preference keys and files
(`launchpad_rules`, `launchpad_groups`, `launchpad_hub`, `launchpadRail`), state keys, test tags
(`glance.handle.left`, `glance.indicator.launchpad`), test class names, and `docs/Spec.md` / `docs/SPEC_STATUS.md` /
older decision docs (the spec keeps its wording). No data migration is needed.

## Guard

`CommsRenameAuditTest` scans string literals of all main sources (outside comments) and fails on a user-visible
"Launchpad", the old Hindi word or "Notification menu"; L10n tables are also checked case-insensitively.

# Wave 27: commsRename

| ID | Status | Note |
| --- | --- | --- |
| Rename | built | Launchpad -> Comms in every user-visible string (L10n English keys and the eight translations, Ground Control category and rows, quick sheet, dock/Radar action titles, device checks, grant gate, backup note, TalkBack labels, undo labels), "Notification menu" -> "Comms menu", README and device checklists. Identifiers, pref keys, tags and the Spec unchanged (`docs/decisions/commsRename.md`). |
| Search alias | built | "launchpad" still finds Comms in Ground Control (`SettingsIndex` alias) and Radar (action keywords). |
| Guard | built | `CommsRenameAuditTest` (CMS_01..03). |

Tests updated to the new text (assertions kept as strong as before): A11ySweepTourTest, GlanceEdgesSmokeTest, GlanceHomeUiTest, HomeLandingUiTest, GlanceWaitingDotTest, LaunchpadTouchPathTest, LaunchpadGroupsCases, LaunchpadGroupsEndToEndW24Test, DestructiveLimiterTest, LaunchpadDestructiveEditTest, StrGroupsStateTest, StateGapsAuditTest, A11yPolishW23AuditTest, LaunchpadStringsTest, LaunchpadScreenshotCases, ScreenshotTest and assertion messages.
Device-only: TalkBack announcements in a real screen reader.

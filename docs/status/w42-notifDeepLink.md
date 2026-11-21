# Wave 42 notifDeepLink: tapping a notification opens that notification

Nothing was run on a device. Verified by JVM and Robolectric tests only.

| ID | Status | Note |
|---|---|---|
| STR-02 history-only rows deep link | built (device untested) | `NotificationDeepLinkW42Test` (remembered intent fires for a row without one, replaced by the later post, ongoing rows skipped), `NotificationOpenPlanW42Test` (LRU bound 300, stale/newer rules, order Live, Cached, Shortcut, App). |
| STR-02 shortcut fallback | built (device untested) | Cancelled remembered intent falls to `startShortcut` (seam `ShortcutStarter`); history entry after a restart uses its stored shortcut; missing shortcut falls to the app; nothing working is `Failed` and Comms stays open (`LaunchpadOpen` test). History line codec round trip and old 9-field lines. |
| STR-02 Dismiss keeps the shade (setting) | built | `CommsOptionsW29Test.STR_02_w42_theShadeRowDefaultsOnPersistsAndChangesWhatDismissDoes`; Launchpad reads `CommsOptions.clearFromShade` for Dismiss. Not covered by a UI test that the shade is untouched (the listener is not bound under Robolectric). |
| STR-02 PendingIntent options | built | `NotificationDeepLinkSdk35W42Test` (creator mode key present at API 35), API 34 has none, API 33 null. |
| Timeline reuse | built | `NotificationOpener.open(HistoryEntry, context, launchApp)`; the Timeline itself is another branch. |

## Changed existing tests
- None deleted. The history-only tap test (`STR_02_w29_tappingAHistoryOnlyRowReadsItAndOpensItsApp`) still passes unchanged: with an empty memory and no shortcut the opener falls to the app.

## Failing on this branch but not mine
- `core-design` TranslationCoverageTest / W13TranslationDeepScanTest: `"%s shortcut segments"` (SettingsOneUiSummaries.kt) missing in eight languages; the file is not touched here.

## Device only
- Whether a chat app's own PendingIntent or published shortcut (WhatsApp, Telegram, Signal, Messages) lands on the right chat; whether `startShortcut` is accepted for a non-primary profile; whether the Android 15 creator flag changes any trampoline's behaviour; the shade staying populated with the setting off.
- The full `testDebugUnitTest assembleRelease` was not run; modules run: core-data, core-quality, feature-launchpad (all green), core-design (only the pre-existing failure above).

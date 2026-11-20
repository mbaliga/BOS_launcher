# Wave 37 specSync: documents and small details

Nothing here was run on a device. Design notes: `docs/decisions/specSync.md`.

| Item | Status | Note |
|---|---|---|
| 1 Spec and status rows | built (docs) | PAL-24, INP-18, INP-19, CFG-15, HOM-01, HOM-05, HOM-12, HOM-15, HOM-16, HOM-21, STR-02, STR-10 and the section 2 table rewritten; "Wave 30 to 36 notes" and "Wave 37 notes" appended to SPEC_STATUS.md. No status word changed; device-only items stay unconfirmed. Guards: `tools/spec_coverage.py`, `RepoHygieneAuditTest`. |
| 2 Connectivity wording | built (device untested) | Labels "WiFi" and "Share Wifi"; `ConnectivitySheetCases.CON_30_w37_*`; `L10nW37specSync.kt` (8 languages). |
| 3 One popup title token | built (device untested) | `PopupTokens` 28 sp light centred, used by `PopupSheet` (Sound, Connectivity) and the Home sheet: `SND_30_w37_*`, `CON_31_w37_*`, `HOM_21_w37_*`. |
| 4 Glance Time, Weather, Battery reachable | built | Rows already existed in Ground Control > Home screen (Glance on); `GlanceRowsW37Test` pins them and that each changes `glanceContent`. |
| 5 Housekeeping | done | Removed `SpiderSenseAlert`, `SPIDER_SENSE_TAG`, `comicLinesAppearOnlyWithoutSignalAndOnlyAtTheEdges`. Unused English strings kept (no audit proves them unused). |

Tests changed: `ConnectivitySheetCases.CON_20` expects "WiFi" (was "Wi-Fi").
Device only: the look of the 28 sp title on a real display, everything listed device-only in waves 30 to 36.

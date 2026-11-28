# Wave 26 launchpadPolish

| ID | Status | Note |
|---|---|---|
| STR-01 | done (JVM), not device-verified | List, bands, dividers, switcher row and compose button reach the card's edge; time 16 dp inside; portrait, landscape, RTL. Tests STR_01_w26_*. Card drop shadow removed. |
| STR-02 | done (JVM), not device-verified | No header for one-item groups; 2+ items show the count; Flat none; Time keeps date bands; History App/Sender headers likewise (HUB_26_*, STR_02_w26_*). Files/Usage/Timeline panels are host-supplied lists with no header+row pair of this kind. |

Updated tests (they encoded the old behaviour):
- HubTest HUB_14_atTheEndOfTheDensityScaleAFurtherPinchChangesTheGroupingGranularity: single-row sender header no longer exists.
- LaunchpadInteractionTest STR_08_escapeClosesAndArrowsWalkGroupsThenRows: App B needs two items to have a header.
- LaunchpadGroupsCases STR_13_column_noGroupsLooksLikeBefore: checks a row instead of a one-item header.
- LaunchpadCardCases and LaunchpadHubW25Cases stages: endInset is now the sliver, not the return zone.

Screenshots: build/screenshots/launchpad-w26-*.png (read and compared with the owner's two).

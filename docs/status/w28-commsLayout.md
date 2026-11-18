# Wave 28 commsLayout status

| ID | Status | Note |
|---|---|---|
| STR-02 (Comms rows, header, tabs) | done | geometry within 2 px of the mock-ups (CommsLayoutGeometryTest); text 3 px on later rows |
| STR-01 (Comms menu panel) | done | 240 dp panel, grid, Show rows, 551 px shift, no dimming |
| Comms size 75-125 | done | CommsScale/CommsScaleSettings, Ground Control row, menu option, pinch, Ctrl+Plus/Minus |
| Density model | removed | HubDensity, its pref, density row, density pinch, grouping pinch |

Device-only: the real status bar inset (the mock-up has none), TalkBack announcements of the stepper, pinch feel.

## Tests changed
- HubTest: HUB_14 density pinch and grouping-granularity pinch replaced by size pinch tests; HUB_16 keys (Ctrl+Minus changes size); HUB_17/HUB_20 pinch asserts size; HUB_10 tab row 35 dp.
- HubStringsW26Test: HubDensity labels dropped.
- CommsDesignCases: metrics (17 sp, 38 dp icons, 32/18 margins, 35 dp tabs, 240 dp panel, SHOW capitals, 16.5 dp icons); density menu test replaced by a grouping test.
- LaunchpadRoomyCases: row metrics. LaunchpadHubW25Cases: row geometry, menu row heights. LaunchpadEdgeW26Cases: 18 dp margin. LaunchpadMenuMotionTest: panel 58.4 percent. W13CrossAuditTest: `comms_scale` classified.

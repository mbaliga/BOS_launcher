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

## Adversarial review findings
| # | Finding | Fixed | Test |
|---|---|---|---|
| 1 | At system font scale 2.0 the row's baseline layout (headline and second line 19.5 dp apart) overlapped: the time sat on the headline | yes: above font scale 1.3 the row stacks headline and second line like an opened row | CommsReviewTest REV_01 (plus screenshot comms-portrait-font2-scale125) |
| 2 | In a right-to-left layout the Comms size stepper has the plus key on the left, but Left still made it smaller | yes: Left and Right follow the layout direction | REV_02 |
| 3 | A size set before the pref file was opened (pinch or Ground Control before init) was not written and the next init reverted it | yes: kept as pending and written by init | REV_03 |
| 4 | TalkBack set-progress on a Ground Control stepper was not clamped to the range (500 gave 500) | yes: SettingItem.Stepper.fromProgress | StepperProgressW28Test STP_01 |
| 5 | Dead code left behind: CommsStyle.HEAD_ROW_DP, CommsStyle.scaled, CommsScale.index | yes: removed | REV_04 scans the sources |
| 6 | At 125 percent in portrait the TIMELINE tab runs under the home card's edge (the tab row scrolls) | no: the row is a scroller, so this is the scroll affordance; the mock-up's own TIMELINE ends past the margin too | none |

Also checked and fine: light theme, RTL mirroring of the menu, landscape 75/125 screenshots, the 48 dp targets at 75 percent, the export/classification of comms_scale.

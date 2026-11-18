# Wave 29 commsOptions: Comms options moved into Ground Control

Verified by JVM and Robolectric tests and by reading the screenshots (`feature-launchpad/build/screenshots/comms-portrait-menu-dark.png`, `app/build/screenshots/w29-comms-settings-font1|font2|font1-rtl|font2-rtl.png`). Nothing run on a device.

| ID | Status | Note |
|---|---|---|
| STR-01 | done | Menu = GROUP BY + SHOW; `CommsOptionsW29Test` covers every row, confirmations, search, font 2.0 and RTL |

Removed or changed tests
- `CommsDesignCases.STR_01_w27_optionsMovedIntoThePanelAndTheThreeDotsButtonIsGone`: replaced by `STR_01_w29_menuHasNoOptionsSectionAndNoOptionRowsAtAnyScale` (the options are gone, at 75, 100, 125 percent).
- `CommsDesignCases.STR_01_w27_markVisibleReadAndDeleteHistoryAreMenuRowsAndDeleteNeedsAConfirmation`: deleted; mark all read and delete-with-confirmation now live in `CommsOptionsW29Test`.
- `CommsScaleCases.CMS_13`, `CMS_14` and `CommsReviewCases.REV_02` (menu stepper targets, semantics, RTL keys): deleted with the menu stepper; the Ground Control stepper is covered by its own row tests and `STR_01_w29_theCommsSizeRowChangesTheScale`.
- `LaunchpadHubW25Cases` menu row heights: no longer list Groups and rules / Clear all.
- `LaunchpadGroupsReviewW24Test`, `LaunchpadGroupsEndToEndW24Test`: open the Groups manager through `LaunchpadHubNav.openComms(groupsManager = true)` instead of the menu row.
- `A11ySweepTourTest`: the menu delete-confirmation step is gone.
- `CommsStyle.SECTION_GAP_DP` removed (unused; `CommsReviewCases.REV_04`).

Device-only: the hand-off from Ground Control to the Comms surface in the real HomeScreen (covered by the nav request tests, not by a full-app run).

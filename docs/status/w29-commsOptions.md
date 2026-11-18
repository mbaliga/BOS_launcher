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

Adversarial review fixes (each with a test in `CommsOptionsW29Test`)
- The three action rows and the two navigation rows had no summary (the five hint strings sat unused in the table): `SettingItem.Action` got `hint`, shown on the row and searchable (`theActionRowsHaveASummaryLikeEveryOtherRow`).
- Back with a confirmation open closed Ground Control; it now answers the confirmation with no (`backDismissesTheConfirmationNotGroundControl`).
- The Comms size row title was squeezed to "Co m..." at font scale 2.0; the stepper goes beneath the title at 1.5 and above (`theCommsSizeTitleIsNotCutAtFontScale2`).
- The shared segment-slab rule was changed for every choice row (Web engine lost its slab); now only rows with `stepped = true` (Default view) use the arrows (`onlyTheDefaultViewRowIsForcedOffTheSegmentSlab`).
- The translation test only checked the English keys; it now checks every Comms row string (title, summary, confirmation, button) in all eight tables (`everyCommsRowStringIsTranslatedIn8Languages`).
- Dead `HubState.confirmDelete` and stale comments/docs that still put Options in the Comms menu (commsDesign, commsLayout, hubData, HubUi) removed or corrected (`noLeftoverOptionsInTheMenuCodeOrDocs`).
- Confirmation buttons checked on screen at font scale 2.0 (`confirmationButtonsStayOnScreenAtFontScale2`, passed).
- Not a defect: Ground Control forces left-to-right layout by design, so its RTL runs equal the LTR ones. TalkBack actions and the quick sheet have no links to the old Options (searched). Orphaned table strings of the removed menu rows ("Set as default view", "Mark visible as read") are left in place (the translation audit tolerates extras).

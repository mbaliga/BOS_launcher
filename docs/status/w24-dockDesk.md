# Wave 24 dockDesk: dock, desktop mode, input and palette residue

Nothing here was seen on a device. Tests are Robolectric and JVM (synthesized key events, no real keyboard, controller, mouse, IME,
second display or mirroring source). Decision record: docs/decisions/dockOptional.md.

Found and fixed (tests named below; fixes 1, 3 and 4 were checked to fail without the change, fix 2's test was not run against a revert):
1. **Ctrl+H ("Focus the dock") closed the open surface for a rail that never came.** With the dock off, or on the default Glance landing
   (which draws no dock unless its right side is the dock rail), Ctrl+H closed Radar, the drawer, the puck, Launchpad or Ground Control and
   focused nothing. Now nothing is closed and a toast says why (`FocusDockRouting.noDock`). `DockOptionalW24SmokeTest` (3 of its composed
   tests fail with the HomeScreen change reverted).
2. **Right side = Dock rail with the dock off did nothing at all** on landings other than Glance (swipe or Ctrl+R). Now the same toast
   (`STR_14_withTheRightSideOnDockRailAndTheDockOffOpenRecentsSaysTheDockIsOffInsteadOfNothing`; Glance still reveals the rail).
3. **The dock panel was reachable only from the dock.** Workspaces (create, switch, delete), app groups (create), sessions (save, restore),
   scheduled launches, Radar actions per workspace and the calm-centre filter could not be reached with the dock off. Ground Control,
   Desktop & dock now has "Workspaces, groups and dock settings", which opens the same panel with the dock on or off and installs the
   controller dialog hook itself. Opening it never turns the dock on.
4. **A dock text field put the caret before its prefilled text.** The session dialog opens with the group's name; a keyboard or controller
   user who focused the field and typed got "xStudy" instead of "Studyx". The dock's `Field` now starts the caret at the end.
5. **The INP-20 note named the wrong cause.** The panel, group editor and session dialog were "untestable because the cursor blinks". A
   stack sample shows the real cause: under the Compose test rule, its Robolectric idling strategy keeps forcing a layout of the dialog
   window's compose root and each measure republishes the text field's layout result, so the rule never idles, even for an unfocused or
   read-only `BasicTextField` in a plain Compose `Dialog`. Hosted in a plain `ComposeView` on the paused looper the same dialogs settle (no further text layouts over
   five seconds), so this is a test-rule limit, not an app defect. The three dialogs are now rendered and driven that way.

Second review (found after the first pass; tests named below; all checked by reading what the test asserts against the code before the
fix, not by a revert run, except where said):
6. **Ctrl+R with Right side = Dock rail left the drawer or Radar over the rail it was asked to reveal.** `openRecents` called the dock
   controller directly; it now takes the same route as Ctrl+H (`focusDockFromHome` in `HomeScreen`), so what hides the rail closes first
   and, with no rail to come, the same toast. `DockDeskReviewW24SmokeTest.STR_14_withRightSideDockRailOpenRecentsFromTheDrawerClosesItAndTheRailTakesFocus`.
7. **Ctrl+H left the Recents panel open**, and the rail steps aside for it (`DockLayer`'s visible flag includes `rightOpen`), so the
   key focused nothing. The Recents panel is now one of the overlays closed. `INP_05_focusDockWithTheRecentsPanelOpenClosesItAndTheRailTakesFocus`.
8. **The Ground Control row's hook test could not fail.** `DockDialogsW24Test` composed `DockInputWiring.Install()` around every
   content, so the "the row installs the hook itself" assertion passed without the row doing it. The Ground Control test now composes
   no hook and asserts none is set before the row is opened.
9. **A group run or session restore started from the Ground Control panel was cut off when Ground Control closed** (a Home press,
   or back, while launches were still spaced 350 ms apart; the notices at the end were lost). The run used Ground Control's
   composition scope; it now uses an unbound scope like the palette providers. Not covered by a test (the launcher is a concrete
   platform class; no headless way to observe a cancelled run).
10. **Ctrl+H or Ctrl+R in home edit mode did nothing at all** (the rail steps aside while editing). `FocusDockRouting.noDock` has a
    third reason, `Editing`, with its own toast. `DockDeskReviewW24RulesTest.INP_05_whileTheHomeIsEditedFocusDockSaysWhyInsteadOfNothing`
    (the rule; the composed edit mode is entered by touch only and is not driven here).
11. **The landing toast named a Ground Control category that does not exist**: "Ground Control, Home, Right side", but the category is
    "Home screen", and that title had no translation in any language (it is a constant, so the coverage scan never saw it). The toast
    now says "Home screen"; "Home screen" is added to the wave 24 table in eight languages; each language's toast is checked to contain
    the settings' own labels (`INP_05_theToastsNameGroundControlWithTheSettingsOwnLabelsInEveryLanguage`, which also caught the German
    and Chinese wordings of the first pass). The older "The right side is off..." toast (wave 15) has the same "Home" wording; not this
    stream's string, left for an owner pass.
12. **The panel row could not be found by search.** Ground Control (and Radar's settings results, CFG-14) index a pane by its title
    only, so "workspaces", "groups" or "sessions" found nothing; with the dock off that row is the panel's only way in. The Desktop &
    dock page now also lists "Workspaces, groups and sessions", which opens the same entry
    (`CFG_14_settingsSearchFindsTheDockPanelByWorkspacesAndGroups`).

| ID | status | note |
|---|---|---|
| INP-01 | partial | Unchanged code path; new: the dock panel and its nested dialogs raise the dock dialog layer (6) and release it when closed, from the dock and from Ground Control with no dock composed (`DockDialogsW24Test`). Ctrl+H with no rail to come no longer closes surfaces (see fix 1); Ctrl+H and Ctrl+R now close the same surfaces, the Recents panel included (fixes 6, 7). Residue (device): a physical keyboard and controller pass over every surface. |
| INP-02 | partial | No code gap. Residue (device): move a real mouse over Radar's tiles on a device and see the hand pointer; the system's pointer drawing is not observable headless. |
| INP-05 | partial | Fixed: "Focus the dock" with the dock off or on a landing without a dock closed the open overlay and focused nothing; now it closes nothing and toasts the reason (`INP_05_withTheDockOffFocusDockLeavesTheDrawerOpenAndSaysTheDockIsOff`, `..LeavesRadarOpen`, `INP_05_onTheDefaultGlanceLandingFocusDockLeavesRadarOpenAndSaysTheLandingHasNoDock`, the rule tests, and `INP_05_withTheDockOnFocusDockStillClosesTheDrawerAndTheRailTakesFocus` for the unchanged path). Second review: the Recents panel now closes too (fix 7), Ctrl+R uses the same route (fix 6), edit mode says why (fix 10), and the landing toast names the real category (fix 11). Alt+1 to 9 with no rail were already tested. Residue (device): a physical keyboard on a device. |
| INP-11 | partial | No code gap (drop targets cannot be driven headless; the routing behind them is tested). Residue (device): whether the platform delivers drops to the home activity (SPK-05 Lab row), the real drag shadow and drop of a finger drag on a phone, and file shortcuts while the drop permission lasts. |
| INP-20 | partial | Closed the render gap: the panel, the group editor and the session dialog (the three with text fields) are rendered with the app's real dialog hook, a field is focused and typed into with key events at the dialog's own window, and B at that window closes the dialog on top only (picker over editor, session over panel), never saving (`DockDialogsW24Test`, 4 tests); the source audit stays. Also: the panel from Ground Control with the dock off gets the hook (fix 3, and its test now proves the row installs it, fix 8); runs started there outlive Ground Control (fix 9); the caret starts after prefilled text (fix 4). The earlier "blinking cursor" cause was wrong (fix 5). Residue (device): a real controller (B, A, D-pad, shoulders) on every dock dialog. |
| PAL-03 | partial | No code gap. Residue (device): with Liftoff as the default home, type a query that matches an app with launcher shortcuts and see them nested in its tile (Filmstrip, Orbit) and row (Flat); `LauncherApps.getShortcuts` lists nothing without the default-home role. |
| PAL-08 | partial | No code gap. With the dock off, "Add to workspace" still writes that workspace's pins (used by Alt+1 to 9 and the rail when it is turned on). Residue (device): "Open on display" with a real second display; the workspace pin through Radar's chooser on the whole home stays tested at `AppActionList`/`WorkspaceManager` and `RadarMenuChooserTest` level. |
| PAL-23 | partial | No code gap. Residue (owner, device): tune the four Orbit feel values by feel on a phone (Lab, "Orbit feel: copy as defaults") and commit the pasted lines over `OrbitFeelValues`. |
| EXT-10 | partial | No code gap; the desktop surface's dock and panel are independent of the phone dock setting (`forceEnabled`), unchanged. Residue (second display): the phone panel driving a desktop surface on a real external display with a real touch pipeline (move, tap, long press, two-finger scroll, keys, text), the ongoing notification and the palette entry. |
| EXT-11 | partial | No code gap. Residue (device and a mirroring source): a real Miracast or cable mirror to confirm the mirror inference fires (tier T2 on the phone surface, no second surface offered) and does not fire for a real extended display; a rotated phone changes its pixel size, so whether a real mirror still matches is part of that check. |
| CFG-01 | partial | No code gap. The dock panel row added to Ground Control writes the same workspace store the export already carries; the second review adds a searchable "Workspaces, groups and sessions" row for it (fix 12). Residue (device): rebind a shortcut with a real hardware keyboard, and write an export through the system file picker (SAF) on a device. |
| CFG-02 | partial | No code gap. Residue (device, managed profile): the widget bind consent, a provider's configure screen, and re-adding a work-profile widget with a configure screen on a real managed profile. |
| CFG-07 | partial | No code change: the four Spec grants go through the explanation hook (tests unchanged). Residue (owner decision): whether activity recognition and approximate location should also go through the one-screen explanation (a Spec change); no device run. |
| CFG-12 | partial | No code change: only the AOSP `favorites` schema is documented. Residue (owner decision): name another launcher backup format to add; then a real backup file from it. |
| CFG-18 | partial | No code gap. Residue (device): the preview dock's look over the real home's pixels (the test uses a solid colour) and its live update cadence while a setting changes. |

Strings: four new strings in `L10nW24dockDesk.kt` (eight languages), appended to the two expressions in `L10n.kt`; the same table also adds
"Previous" (the Glance music control's label), which had no translation at the base and failed `TranslationCoverageTest` there.
Second review: the landing toast's key changed ("Home screen" for "Home"); added "Finish editing the home first: the dock steps aside
while you edit.", "Workspaces, groups and sessions" and "Home screen", all in eight languages.

Build: `./gradlew testDebugUnitTest assembleRelease --offline --continue` green. In the first full run `TourBusySmokeTest.HOM_06_busyTourTheWorkProfileAppIsOnTheDrawersWorkTab`
failed once ("a global snapshot write is pending" at the drawer step) under the shared machine's load; it passed alone and in the second full run. Not changed; noted as a load-sensitive tour check.
Second review build: `./gradlew testDebugUnitTest assembleRelease --offline --continue -q --max-workers=2` green (exit 0), with the
review tests passing (`DockDeskReviewW24RulesTest` 3, `DockDeskReviewW24SmokeTest` 2, `DockDialogsW24Test` 4, `DockOptionalW24SmokeTest` 8).
Shared files touched (small additive edits): `HomeScreen.kt` (the Ctrl+H branch and the Right side = Dock rail branch of `openRecents`,
one `focusDockFromHome` route since the second review), `L10n.kt`, `DesktopSettingsPane.kt` (one more Pane row).

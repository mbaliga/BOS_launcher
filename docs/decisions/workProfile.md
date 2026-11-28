# Work-profile pause in the disc app drawer (wave 48)

Source: the owner noticed the work-profile pause was lost when the flat app drawer was removed (wave 38, [drawerRing.md](drawerRing.md)).

## Where it lived
Wave 3 (HOM-06): the flat drawer's tab row (`DrawerFilterBar` in `DrawerTabs.kt`) had Personal / Work / Private tabs and, on the Work tab, a "Pause work apps" / "Resume work apps" chip. Paused work apps stayed listed (dimmed, a pause mark) so the profile could be resumed from the drawer; search and Radar were never filtered by the pause. The same state drives the home's pause chip (`WorkPauseChip`). `DrawerFilterBar` is now unused.

## Decisions
- **Same mechanism, no new permission.** `WorkProfileState` (profile availability broadcasts, `UserManager.requestQuietModeEnabled` through `AppCatalog.setPaused`, off the main thread) is unchanged. The pause itself is the system's persisted quiet mode; nothing about it is stored by the launcher.
- **A Work label on the disc.** Beside the grouping labels (A-Z, Category, Recent) the disc has a fourth curved label "Work", present only while a work profile exists (`WheelWork` is null otherwise, so nothing changes without one). It is a scope, not a grouping: tapping it lists only the work apps, grouped by the current grouping; tapping again returns to all apps. It is a tab for TalkBack, lit while on. The choice is the pref `wheelWorkOnly` (`launcher_prefs`, already exported); it is ignored while there is no work profile.
- **Pause pill.** With the scope on, a pill ("Pause work apps" / "Resume work apps", amber while paused) sits at the screen edge opposite the corner (inside the safe area) and calls `WorkProfileState.setWorkPaused`. The home's own chip steps aside while the disc is open so there is one pill.
- **Paused look.** In every grouping a paused work app is dimmed to 45%, carries two pause bars, is announced "<name>, paused", is disabled for TalkBack and its tap does nothing until the profile resumes. Search and Radar are unchanged (the old drawer did not filter them either).
- **Rules** are pure in `WorkDrawerRules` (core-catalog). Strings in `L10nW48workProfile.kt`.

## Not matched / device-only
Quiet mode is a system call that needs the launcher to be the default home app; the real pause and resume, the broadcast round trip, the label's fit on small windows next to three other labels, and the pill position on a real device were not checked on a phone. Private space has no disc entry (it had a tab and a lock chip in the flat drawer); not part of this ask.

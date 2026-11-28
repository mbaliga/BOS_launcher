# Audit of wave 25 (wave 26, stream auditW25)

Method: read each wave 25 decision (launchpadHub, hubData, musicNotch, dockIcons, radarGlass, actionIcons, shortcutForm, uninstall), look for the clause in the code, then for a test that would fail if it broke. Defects got a failing test first.

## Decisions
- **Strings that the source scan cannot see** (enum labels, `when` branches) get their own coverage test (`HubStringsW26Test`); direct `L10n.tr("...")` literals stay covered by `TranslationCoverageTest`.
- **`DrawerOrdering.relativeTime` now translates** (it reused English words in the drawer's details line); the line itself is built by `DrawerOrdering.detailsLine`, one place, tested in all eight languages. Section titles of the drawer go through `L10n.tr` too ("Today", "Earlier", categories).
- **The category wheel's labels shrink in steps until they fit their half of the screen** instead of being cut (font 2.0 turned EVERYTHING into EVERYTHIN). The bounds matrix `RadarWheelBottomW26UiTest` adds Actions and Files as the selected scope.
- **The hub options drop a half-made "Delete history" confirmation** when they are opened or closed, so a stale confirmation is never found waiting.
- **Every control of the hub is 48 dp both ways** (`HubChip` had a 48 dp height only: a short label such as "Day" was 42 dp wide) and the Files, Usage and Timeline controls inside the hub got a role, the selected state and 48 dp (they were bare clickable texts).
- **A confirmed uninstall is not special-cased in the dock.** The package-removed callback reaches the catalog, the drawer, Radar and the home layout at once (pinned by `PackageRemovedW26SmokeTest`); a dock pin of the removed app stays for the HOM-10 grace period (7 days) as an "App unavailable" dimmed slot. That is the existing HOM-10 rule for packages that are gone, and it cannot tell a user uninstall from a vanished package. Changing it means a decision in the dock stream; recorded here, not changed.
- **State on activity recreate**: the Launchpad panel, the hub view, filters and the menu state are `remember`ed, not saved. Rotation does not recreate (the manifest declares the configuration changes), and every other overlay (`leftOpen`, Radar, drawer) is likewise not restored after process death, so the launcher starts on the home. Consistent; not changed.

## Not changed on purpose
Two older English hints in `TouchPaths` still say "Ground Control, Home, ..." (they name the Home category of the settings list, which still exists for the page-level rows); the landing text there belongs to the home streams of wave 26.

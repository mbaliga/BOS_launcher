# Wave 38 popupsHidden: full-width Comms popups, Hidden apps

Nothing was run on a device. Verified by JVM and Robolectric tests and by reading the rendered screenshots (`feature-launchpad/build/screenshots/popups-w38-portrait-{sound,connectivity,sound-rtl}.png`, `feature-grid/build/screenshots/hidden-w38-{pane,confirm,rules,radar-collapsed,radar-open}.png`) against the owner's reference mock-ups (`connectivity-ref.png`). Design in `docs/decisions/popupsHidden.md`.

| ID | Status | Note |
|---|---|---|
| STR-02 | built (device untested) | The Sound and Vibration and Connectivity sheets span the whole window over the Comms panel and the shrunken home card, scrim over the whole screen, 32 dp margins each side, trailing glyphs and Done end at the margin. `PopupSheetFullWidthW38PortraitTest` (sheet left 0, right = window width; slider and switch margins 32 dp both sides; without the host the sheet is narrower; RTL; Back closes it and empties the host layer). |
| HOM-05 | built (device untested) | Ground Control: tab "Hidden apps" (count on the tab) with the Hidden apps page: search, icon, name, source named, Unhide (one confirmation line), Unhide all, "Hidden by Comms rules" with Remove. `HiddenAppsModelW38Test` (JVM), `HiddenAppsW38UiTest`, `HiddenAppsSettingsW38Test` (row exists, count, unhide clears the set and the Hide rules). |
| PAL-02 | built (device untested) | Radar: a quiet "N hidden apps match: Show" row at the end of the flat results list; tapping reveals the matches inline with Unhide; none mixed in otherwise (`HiddenAppsW38UiTest.PAL_02_w38_*`). |

## Finding: the "thunderbolt"
The owner's "strange icon with a thunderbolt next to it, I think that's recents" is the FLASHLIGHT shortcut wedge of the drawer disc (the bolt glyph), not Recents. No change to the drawer here (another stream rebuilds the disc); the glyph or its label may deserve a clearer one there.

## Why WhatsApp vanished from search
Only the hidden-apps set (the app menu's Hide) removes an app from Radar, the drawer, home and Recents; a Comms "Hide this app" rule only hides its notifications. WhatsApp was in the set while still reachable from Comms notifications. The page now lists both and unhiding clears both.

## Not matched / limits
- Radar's Orbit and Filmstrip typing layouts show rings or tiles, not a list, so the hidden-matches row exists only in the list layout (Flat, and typing in it).
- Rule rows read "Hide notifications from WhatsApp" in English (the existing `Rule.describe` text is not localised).
- Radar matching of hidden apps is a plain name-contains test, not the alias / initials / typo matching of the app results.
- The home quick sheet is unchanged (its own window already spans the width).

## Device only
The sheets over the real pushed card on a phone (sliver width, gesture insets, navigation bar), TalkBack focus order with the sheet drawn in the host layer, the predictive Back animation with a sheet open, Radar's row with a real keyboard.

## Existing tests changed
None changed. `PopupSheet` margin 28 to 32 dp and the Connectivity trailing alignment kept every existing sheet test green.

## Test runs
Run: `:feature-launchpad` (all), `:core-data` (HiddenAppsModelW38Test), `:feature-grid` (HiddenAppsW38UiTest), `:app` (HiddenAppsSettingsW38Test). See the final report for the full-suite and audit runs.

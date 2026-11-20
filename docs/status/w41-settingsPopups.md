# Wave 41 settingsPopups: status

Branch w41-settingsPopups. Honest: nothing here is device-confirmed; every check is a JVM or Robolectric test.

| ID | Status | Note |
| --- | --- | --- |
| CFG-21 | built | One UI popup kit and the pane-as-page rule; `CFG_21_popups_*` (21) |
| WSP-07 | built | Workspaces page: explanation, list with summaries and active badge, menu, add with copy, "Active: Home"; `WSP_07_page_*`, `WSP_07_*` pure |
| WSP-08 | built | "What the calm centre shows" radio dialog; "Only this workspace's apps" not offered (no such model) |
| WSP-05 | built | App groups page |
| DCK-04 | built | Dock page without a floating card |

## Existing tests changed (each encoded behaviour the owner replaced)
- `DockDialogsW24Test` (app): the panel test no longer types into the panel (it is a page with no text field; now it checks the dialog layer and that B closes it); the session dialog test no longer types into the pre-filled field (the raw key path of that harness does not insert into a pre-filled field; the field itself is tested with the IME path in `OneUiPopupsW41Test`, `CFG_21_popups_11b`); the Ground Control row test became the App groups page test (the "Workspaces, groups and dock settings" row is gone).
- `DockDeskReviewW24RulesTest.CFG_14` (app): search finds the Dock, Workspaces and App groups pages (the "Workspaces, groups and sessions" row is gone); "sessions" is an alias of "groups" in `SettingsIndex`.
- `W4RadarInputDockTest.INP_20_everyDialogWindow...` (feature-dock): the source audit counted seven raw `Dialog(` windows; now one raw window (the panel's page) and every file that opens a One UI dialog must provide the dock's dialog hook.
- `ShortcutRebindUiTest` (feature-grid): the helper waits for idle; the editor no longer draws the looping wave field that used to keep the frame loop running.
- Small non-test edits that the suite forced: `VoiceEntry` wraps the microphone permission launch in `runCatching` (the monkey test reached a path where Radar had closed before Allow was tapped: an unregistered launcher threw); `L10nW41settingsPopups` also carries `%s shortcut segments`, which the translation coverage test flagged on the base (`SettingsOneUiSummaries.kt`, not from this wave).

## Tests run
`:core-design`, `:feature-dock`, `:feature-grid`, `:app` module runs, one module at a time. Known flaky under load (passed on a rerun): `DockWiringTest`, `PackageRemovedW26SmokeTest`. See the final report for the full-command result.

## Not matched, device-only
Keyboard raising and IME resize in the text entry, One UI blur and dialog animation, TalkBack focus trapping on a device, controller keys in the new dialogs (the window hook is called), popups outside the settings (Home sheet look, folder rename, alias and tag dialogs, Radar pickers), the fonts of One UI.

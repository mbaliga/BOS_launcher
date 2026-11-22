# Wave 52: panelWidgets (STR-15)

Nothing was run on a device. Verified by JVM and Robolectric tests with a fake widget host; screenshots were generated and read (see below). Real widgets, the bind consent screen, provider configure screens, widgets surviving a reboot or provider update, and TalkBack speech are device-only and unverified.

| ID | Status | Note |
| --- | --- | --- |
| STR-15 | built (device untested) | Widgets is the fifth tab of the Comms panel, off by default. Setting "Widgets section" (Settings, Comms) and a card choice "Widgets position" (Last, First, Before Files). Real widgets through the home's `LauncherWidgetHost`, picker, bind and configure flow; full-width cards, Add widget tile, Remove, Move up, Move down, Widget settings (menu and TalkBack custom actions), unavailable card, RTL, landscape. |
| STR-14 | unchanged | The Recents panel is untouched. |

## Tests
- `PanelWidgetsModelTest`: tab order off and on and per slot, defaults and persistence, hub following the setting, process-death restore, list operations, saved text, sizing.
- `PanelWidgetsUiCases` (portrait, landscape): empty hint and Add tile, order, long-press menu entries, no Settings entry without a configure screen, move, remove and id release, unavailable card, TalkBack actions, focus, RTL, screenshots.
- `PanelWidgetsSwitcherTest`: the tab appears only when on, as the fifth, at 360 dp, in RTL and scrolled into sight.
- Changed old tests: `HubTest.HUB_10` (the Widgets tab is absent by default), `UsageViewMigrationTest.USG_08` (the enum now lists `widgets`).

## Screenshots
`feature-launchpad/build/screenshots/panelwidgets-*.png` (portrait and landscape: dark with an unavailable card, empty, RTL light).

## Device-only checks
Add a real widget (bind consent, configure screen), move and remove it, Widget settings on a configurable widget, reboot and provider update keep the cards, uninstall a provider and see the unavailable card, TalkBack custom actions, keyboard Enter and Menu on a card, no stutter scrolling several widgets.

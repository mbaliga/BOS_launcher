# Wave 25: launchpadHub (owner items 1 and 2, visual and interaction part)

Nothing here is confirmed on a device. Verified by Robolectric UI tests and JVM tests (screenshots read in portrait and landscape, dark and light, 0 / 1 / 30 notifications, and the open menu).

| ID | Status | Note |
| --- | --- | --- |
| STR-01 | Built, not device-checked | The three-bars menu is a left side panel above the notifications; the list shifts right, the home card slides fully out of view to the right (bounds tested, LTR and RTL), the panel content slides in from the left with the card's own settle; reduced motion fades only. Back, Esc, scrim, close button close it. The old dropdown is removed. The host half (HomeScreen applying `launchpadMenuAside` to the card) compiles and uses the tested modifier, but the full home composition is not covered by a UI test. |
| STR-02 | Built, not device-checked | Hub-style rows (flat, 72 dp, 4 dp stripe per app, 32 dp badge, bold unread, right-aligned time, one-line preview, dividers), date headers "Sun 10 Mar 2024", chips only on long press, floating 56 dp blue Compose button bottom right. The stripe colour is a brand table or a package-hash hue, not the icon's dominant colour. |
| STR-13 | Unchanged | The Groups manager is reached from the panel row "Groups and rules" (same tag, earlier tests still pass). |
| STR-14 | Unchanged | Recents panel untouched. |

## What the compose button opens

Launchpad has no composer and no search entry of its own. The button closes Launchpad and opens Radar on the People scope (needs the Contacts grant for names; without it Radar shows its usual grant prompt). Changed in shared files: `SearchOverlay` got an `initialCategory` parameter (focused edit), HomeScreen wires it.

## Not done here (stream hubData)

Persistent history, read/unread views, the files/usage/timeline merge, defaults, pinch and two-finger filtering: only the slot `viewSwitcher` in the top bar, and `NotificationRow(unread, stripeColor)`.

## Tests changed

- `LaunchpadRoomyCases.STR_02_row_isAtLeast72dpTall_withA40dpIcon` renamed `..._withA32dpBadge`: icon 32 and row gap 0 are now the owner's hub style.
- `LaunchpadInteractionTest.STR_02_rowShowsReplyAndActionChipsAndSendsText`: chips now appear after a long press (asserts they are absent before).
- `LaunchpadCardCases.STR_02_card_everyTitleAndItsLatestRowAreInTheOneList`: scrolls to "+1 more" first (the list is lazy and the shorter rows differ in landscape).
- New: `LaunchpadHubW25Cases` (portrait and landscape), `LaunchpadMenuMotionTest`.

# Launchpad device checklist (STR-07)

Nothing here has been run on a device by the agent. Each line is an entry for the owner to tick on the reference
devices (phone, tablet, external display with keyboard and mouse). Record the device, Android version and result.

Headless coverage that already exists (Robolectric, `feature-launchpad`): screenshots in portrait and landscape for no
grant, empty, grouped, digest, light and dark; the reveal maths; keyboard walking; menu parity. Those prove the logic
and layout only. The items below need real fingers, a real window and real notifications.

## Slide, peek and reveal (STR-01)

| # | Step | Expected |
| --- | --- | --- |
| 1 | Landscape. Put a finger on the very left edge of home and drag right slowly across about a fifth of the width, then let go | The home card follows the finger, then springs back. Launchpad is not left open |
| 2 | Same, but drag across more than a third of the width and let go | The card slides on to about 86 percent of the width and stays. A strip of home remains visible on the right |
| 3 | Same, with a quick short flick right from the edge | Opens, whatever the distance |
| 4 | With Launchpad open, drag the strip of home on the right to the left | Closes, following the finger |
| 5 | With Launchpad open, drag the strip a short way and let go | Stays open (it needs to be pulled back past about two thirds to close) |
| 6 | Tap the visible handle on the left edge | Opens |
| 7 | Tap the left-edge waiting indicator (turn off the unread strip first) | Opens |
| 8 | Press Ctrl+N with a keyboard attached and a home element focused | Opens. Rebind it in the shortcut settings, the new chord opens it and the old one does not |
| 9 | Portrait. Repeat 1 to 7 | Launchpad takes the whole screen. No strip of home remains. Back, the Home button in the header, Esc and a swipe left all return |
| 10 | Start a drag in the middle of the screen on page 2 of the grid | Launchpad does not open and the pager still scrolls |
| 11 | Rotate while Launchpad is open | Layout switches between strip and full screen without losing scroll position |

## Row gestures and their equivalents (STR-03)

| # | Step | Expected |
| --- | --- | --- |
| 1 | Slide a row left past a third of its width | The primary action runs (Dismiss by default) and the notification disappears from the system shade too |
| 2 | Slide a row left a little and release | Springs back, nothing runs |
| 3 | Set a per-app primary action (Settings, Launchpad, Grouping and rules, Delivery per app) and slide a row of that app left | That action runs instead of Dismiss |
| 4 | Slide a row right | A palette (Snooze, Read, Mute, Open, Rule) stays open until a cell or the row is tapped |
| 5 | Palette, Snooze, pick 10 minutes | The row leaves the list and returns after ten minutes |
| 6 | Palette, Rule | A sheet lists Hide this app, Mute, Pin and the other rules. Choosing one adds it to the rules page |
| 7 | Hover a row with a mouse | Dismiss and Actions buttons replace the time stamp |
| 8 | Tab or arrow to a row with a keyboard | The same two buttons show, with a focus ring. The ring is absent when the last input was touch |
| 9 | Press Delete on a focused row | Primary action runs. Focus lands on the neighbouring row |
| 10 | Press the Menu key (or Shift+F10) on a focused row | The same sheet as long-press opens, with the same entries |
| 11 | Right-click a row | The same sheet opens |
| 12 | Use TalkBack: swipe up then right on a row | The custom actions Dismiss and Actions are offered |

## Content, reply and dismissal (STR-02)

| # | Step | Expected |
| --- | --- | --- |
| 1 | Post notifications from two chat apps with two conversations each | Grouped by app, then conversation, newest first. The app whose latest message is newest leads |
| 2 | A chat notification that has a Reply action | A Reply chip shows. Tapping it opens a text field. Sending delivers the text to the app, and the app's own notification updates |
| 3 | A notification with plain actions (Mark as read, Archive) | The chips run the notification's own action |
| 4 | Dismiss in Launchpad | The notification also leaves the system shade |
| 5 | An ongoing notification (music, call) | It has no Dismiss action |

## Grant flow (STR-05, CFG-07)

| # | Step | Expected |
| --- | --- | --- |
| 1 | Revoke notification access, open Launchpad | One screen: what is read, that it stays on the device, and an Allow button. Not a blank list |
| 2 | Tap Allow, grant, come back | Launchpad fills without restarting |
| 3 | Go back without granting, open Launchpad again | The same screen says access is still off |
| 4 | Revoke access, turn on media controls | A chip offers to allow access. Tapping it opens the explanation, then the system settings page |
| 5 | Tap the contacts row in search with contacts denied | The explanation appears first, the system prompt only after its button |

## Digest, silent, indicator and system row (STR-04, STR-09, STR-10)

| # | Step | Expected |
| --- | --- | --- |
| 1 | Set an app to Batch into a digest at 08:00 and 18:00, post a notification at 10:00 | It is held: a line says one is held until 18:00. After 18:00 it appears under Digest |
| 2 | Set an app to Silent, Launchpad only | Its rows appear without the blue unread dot and do not change the waiting count |
| 3 | The rules page | States that these rules change what Liftoff shows, not Android |
| 4 | Export the configuration | The Launchpad rules, per-app delivery and gestures are present. No notification text is |
| 5 | Post a new notification with the unread strip off | The left-edge indicator widens for a few seconds, then shrinks to the count. It never takes keyboard focus |
| 6 | Post a notification from a muted app | The indicator does not widen |
| 7 | Tap each tile of the system row | Date settings, battery usage, the internet panel and the volume panel open. No tile changes anything |
| 8 | Airplane mode on | Connectivity reads Offline |

## Ground Control visuals (CFG-10, CFG-16, CFG-18)

| # | Step | Expected |
| --- | --- | --- |
| 1 | Open Ground Control | A small live preview of the real home sits at the top right, with the home's own aspect |
| 2 | Change icon size, theme, grid density | The preview changes at once |
| 3 | Theme, Wallpaper palette, choose From my wallpaper, then change the system wallpaper | Light or dark and the accent follow the wallpaper. A bright wallpaper gives light |
| 4 | Move Density and Text size | Density scales everything, text size scales only words |
| 5 | Steppers, section headers and the older pages (corner editor, app picker) | Use the groove and key look, in light and dark |
| 6 | Style row: the preview rails | Show real renders, not empty frames |

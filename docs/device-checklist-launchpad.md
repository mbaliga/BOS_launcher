# Launchpad device checklist (STR-07)

Nothing here has been run on a device by the agent. Each line is an entry for the owner to tick on the reference
devices (phone, tablet, external display with keyboard and mouse). Record the device, Android version and result.

Headless coverage that already exists (Robolectric, `feature-launchpad`): screenshots in portrait and landscape for no
grant, empty, grouped, digest, light and dark; the reveal maths; keyboard walking; menu parity. Those prove the logic
and layout only. The items below need real fingers, a real window and real notifications.

## Slide, peek and reveal (STR-01)

| # | Step | Expected |
| --- | --- | --- |
| 1 | Landscape. Put a finger on the very left edge of home and drag right slowly across about a fifth of the width, then let go | The home shrinks with the finger into a rounded card on the right, then springs back. Launchpad is not left open |
| 2 | Same, but drag across more than a third of the width and let go | The home leaves as a card (about 0.93 scale, 28 dp corners, dimmed to about half) and only a thin sliver of its inner edge, about 7 percent of the width, stays at the right edge. The notifications fill the rest of the screen and do not move; their content fades in near the end. No second card is visible. Wave 9: judge the motion against the owner's screen recording (0.45 s ease-out, dimming while it recedes, no bounce) |
| 3 | Same, with a quick short flick right from the edge | Opens, whatever the distance |
| 4 | With Launchpad open, drag the sliver to the left | The card grows back to full size following the finger 1:1 (its edge under the finger), and closes |
| 5 | With Launchpad open, drag the card a short way and let go | Stays open (it needs to be pulled back past about two thirds to close) |
| 6 | Tap the visible handle on the left edge | Opens |
| 7 | Tap the left-edge waiting indicator (turn off the unread strip first) | Opens |
| 8 | Press Ctrl+N with a keyboard attached and a home element focused | Opens. Rebind it in the shortcut settings, the new chord opens it and the old one does not |
| 9 | Portrait. Repeat 1 to 7 | The same card layout (wave 8: Launchpad no longer takes the whole screen). Back, the Home button, tapping the card, Esc and a drag back all return |
| 10 | Start a drag in the middle of the screen on page 2 of the grid | Launchpad does not open and the pager still scrolls |
| 12 | With Launchpad open, tap the sliver; then reopen and tap the clock, battery, connectivity and sound icons; TalkBack on | The sliver returns home (its touch zone is at least 48 dp wide). Each icon opens its system panel, is read as for example "Battery: 82%, charging", and shows a focus ring when reached with Tab or arrows. Icons are the only content of that row, no text. Right-to-left: the sliver is at the left edge and the card moves left |
| 11 | Rotate while Launchpad is open | The card re-poses for the new orientation without losing the selected section or scroll position |

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

## Wave 2 additions (STR-01, STR-06, STR-08, STR-11, CFG-07)

| # | Step | Expected |
| --- | --- | --- |
| 1 | With a hardware keyboard, focus any home element and press Ctrl+N | Launchpad opens. With another app in front, nothing happens (spec section 3) |
| 2 | With a hardware keyboard, focus a home element with Tab, open Launchpad, press Esc | Launchpad closes and the same element has focus again |
| 3 | Play music in any player | The media strip shows the cover (or a plain tile), title, artist and prev, play or pause, next. The dock media item shows the same session |
| 4 | Long-press the home, Widgets, add the Liftoff digest widget | It shows the released digest items (up to four) and a line with the number held and their release time. Tapping it opens the launcher |
| 5 | Change a digest time or snooze an item | The widget follows within a second while the launcher is running, and at the next system refresh otherwise |
| 6 | Revoke usage access, tap the enable row in the dock | The explanation screen first, then the system usage access page |
| 7 | Ground Control, Launchpad, Access Liftoff asks for | Five rows, each showing On or Off. Each row opens the explanation, then the system route (contacts and microphone prompt, folders picker, the two settings pages) |
| 8 | Reply inline to a messaging notification from Launchpad | The text arrives in the other app and the notification updates |

## Added by the wave 6 audit (streams and external display)

| # | Step | Expected |
| --- | --- | --- |
| 1 | Notification access off. Show the media strip, tap the grant chip, turn access on, come back | The strip fills in with the playing session at once, without leaving and re-entering home (STR-05, STR-06) |
| 2 | Switch the UI language to Arabic. Look at the edge handle, the waiting indicator and the rail, then drag in from the right edge | All three sit on the physical right edge (wave 8: the card goes to the left in right-to-left), where the drag starts; the home card is pushed to the left and the notifications read right to left on the right (STR-01, STR-09) |
| 3 | A new notification arrives (indicator widens), then dismiss it from the shade within three seconds | The indicator returns to its slim width after about three and a half seconds (STR-09) |
| 4 | Place the digest widget. Set an app to Digest with a release time a few minutes ahead | At that time the widget lists the released items within about a minute, not half an hour later (STR-11) |
| 5 | Desktop surface on an external display, phone panel open. Type a letter, press the arrow keys and Tab, then the return key | The drawer opens, a ring moves over the apps, return opens the highlighted one (EXT-10) |

## Added by wave 8 (Launchpad as the background of the shrunken home card)

| # | Step | Expected |
| --- | --- | --- |
| 1 | Open Launchpad in portrait and landscape, light and dark | Home is a rounded card on the right, partly off screen; the left holds the menu icon, section titles (selected one large and dark, others small and grey) and the selected section's rows; a second card peeks in below |
| 2 | Drag in from the left edge and watch the frame rate (Developer options, GPU rendering profile) | The shrink follows the finger at 60 fps; the home content under the card is not redrawn or reloaded (NFR-05) |
| 3 | Tap a section title, then the same title again | The first tap selects it and its rows appear beneath it; the second folds the rows |
| 4 | Tap the card, then repeat and press Back, then Esc on a keyboard, then Tab to the card and press Enter | Each returns home; the card grows back; focus returns to the home element that held it |
| 5 | TalkBack on: swipe through Launchpad | Titles read as selected or not selected tabs; the card reads "Home card. Return to home"; home icons behind the card are not reachable |
| 6 | Remove animations (Accessibility) and open Launchpad | The card does not shrink gradually: it appears in place and the background cross-fades (NFR-14) |
| 7 | Right-to-left language, drag in from the right edge | Card on the left, notifications on the right |

## Added by wave 11 (notification tap-to-open and the roomy list, STR-02)

| # | Step | Expected |
| --- | --- | --- |
| 1 | On Android 14 or later, open Launchpad, tap a WhatsApp message row | WhatsApp opens on that chat; Launchpad closes only after it did (the build 43 bug: Launchpad closed and nothing opened) |
| 2 | Tap rows from a few other apps (a browser, a mail app, an app with a lock screen) and one whose notification is already gone | Each opens through its own intent; the gone one starts the app instead; if even that fails Launchpad stays open with "Couldn't open <app>" |
| 3 | Tap a notification that auto-cancels | It disappears from the shade and from Launchpad; mark-read is kept for ones that do not |
| 4 | Tap an action chip and send an inline reply | The action runs in the other app; a failed send shows a message instead of "Reply sent" |
| 5 | Open Launchpad with 15 or more notifications in portrait and landscape, light and dark | Every section and every notification is in one scrolling list; rows are roomy (72 dp or more, 40 dp icon, bold title, two-line text, time at the end); nothing is a grey one-line title |
| 6 | Look at a chat with several messages; tap "+N more", then "Show fewer" | Latest message first; the rest open and close in place |
| 7 | Tap a title in the strip at the top | The list scrolls to that section; the strip highlights the section in view |
| 8 | Keyboard: arrows walk the rows, left on a title folds it, right opens it, Esc returns home | As before (STR-08); nothing is folded until you fold it |
| 9 | Android 13 or older device, if available | Tapping a row still opens the app |

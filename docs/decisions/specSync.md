# Wave 37 specSync: the Spec and status rows follow waves 30 to 36, and small details

Debt wave. Nothing here was run on a device.

## Spec and status rows
Waves 30 to 36 built the looped drawer disc with four wedge sections around a Back hub, the Comms read and unread work, the Files grid, the Connectivity and Home sheets, one wallpaper picker, the signal effects and the page editor, but left `docs/Spec.md` and `docs/SPEC_STATUS.md` describing the older behaviour (comic lines only without connectivity, a background of two signal slots only, a ring of action buttons, an add carousel, the Home sheet tiles). Rewritten rows: PAL-24, INP-18, INP-19, CFG-15, HOM-01, HOM-05, HOM-12, HOM-15, HOM-16, HOM-21, STR-02, STR-10 and the summary table of section 2. Each status row keeps its history and gains a "Current" or "Wave 37" sentence. No status word changed: the rows were `built` ("compiles, covered by unit tests, not on a device") or `partial` for device-only reasons, and the new text keeps device-only items listed as unconfirmed. `docs/SPEC_STATUS.md` has a "Wave 30 to 36 notes" and a "Wave 37 notes" section.

## One popup title token
The Sound and Vibration sheet and the Connectivity sheet share `PopupSheet`, which drew its title at 22 sp; the Home sheet copied that. The owner's Sound mock-up is about 28 to 30 sp. `core-design` `PopupTokens` now holds `TitleSize = 28.sp` and `TitleWeight = Light`; `PopupSheet` (both popups) and `HomeQuickSheetBody` read it, so the three cannot drift. The title stays centred, white on black. The sheets' other tokens are unchanged.

## Connectivity wording
Visible labels are exactly "WiFi" (the row) and "Share Wifi" (the button), as the owner's mock-up spells them; content descriptions merge from the same text. The rest of the app keeps "Wi-Fi" (header state, Radar, settings). "Share Wi-Fi" is deleted from `L10nW34connectivity.kt` and the two new strings are in `L10nW37specSync.kt` (es, fr, de, pt, hi, zh, ja, ar; German keeps the normal word WLAN).

## Glance content stays reachable
Time, Weather and Battery (the Glance content items) are one toggle row each in Ground Control > Home screen, listed while Glance is on (they do nothing with Glance off). They were already there from wave 16 (`glanceSettings`); wave 37 adds the test that pins it. The Battery row is labelled "Battery".

## Housekeeping
Removed: `SpiderSenseAlert` and `SPIDER_SENSE_TAG` (feature-grid `WallpaperSurfaces.kt`; the Alert effect of wave 35 owns the comic lines and nothing referenced them) and the unused private helper `comicLinesAppearOnlyWithoutSignalAndOnlyAtTheEdges` in `BackgroundRenderTest.kt`. Kept: the English strings of removed options in the L10n tables. No audit scans for unused keys, but many labels are reached through enum labels and the `x(...)` helpers, so "unused" cannot be proved by search; leaving them is harmless.

Supersedes the "Not matched" lines about the 22 sp title in [homeSheet.md](homeSheet.md), the spelling line in [connectivity.md](connectivity.md), section 6 of [wallpaper.md](wallpaper.md) (the spider-sense composable) and the "Spec.md rows still describe" line of [signalEffects.md](signalEffects.md).

# OEM notes: battery optimisation and gesture limits (NFR-21)

Short versions of these notes appear in the capability report as the "OEM notes" row (`OemNotes` in core-capabilities). The manufacturer only selects the text; no feature is switched on or off by it. Menu names change between versions; the intent matters more than the wording. Nothing here has been confirmed on a device yet: SPK-06 (Lab page) is the check that settles which reference devices restrict gestures or kill the launcher, and its results belong in `docs/capabilities.md`.

## What goes wrong, in general

1. **Background kill.** The launcher is a normal app. Aggressive battery managers close it when the screen is off, and the next Home press is a cold start (NFR-02 budget 700 ms) with state restored from the store (NFR-11).
2. **Gestures.** Recents and gesture navigation belong to the preinstalled launcher (spec section 3). Some OEMs disable or degrade swipe-up, the Recents swipe and the back swipe while a third-party home app is the default. Liftoff never intercepts system gestures (NFR-20); the only exclusion rect is the Orbit carousel (see the audit test `NFR_20_systemGestureExclusionUsageIsAllowlisted`).
3. **Three-button navigation** is the reliable fallback on every OEM below.

## Samsung (One UI)

- Settings > Apps > Liftoff > Battery > **Unrestricted**.
- Settings > Battery > Background usage limits: remove Liftoff from **Sleeping apps** and **Deep sleeping apps**; turn off "Put unused apps to sleep" for Liftoff.
- Gestures: with swipe-gesture navigation and a third-party home, the swipe-up for Home and Recents can stop working. Settings > Display > Navigation bar > choose **Buttons**. Good Lock "NavStar" does not change this.
- Edge panels and the Samsung back swipe use the screen edges: expect them to compete with the Orbit edge tiles.

## Xiaomi, Redmi, POCO (HyperOS / MIUI)

- Settings > Apps > Manage apps > Liftoff > **Autostart: on**; **Battery saver: No restrictions**.
- Lock Liftoff in Recents (long press the card > lock) so the memory cleaner leaves it alone.
- Settings > Apps > Permissions > Other permissions: allow "Display pop-up windows while running in the background" only if a feature the user turns on needs it (the core build does not).
- Gestures: full-screen gestures may revert to buttons when the home app changes. Settings > Home screen > Default launcher, then re-pick gestures in Settings > Additional settings > Full screen display.

## OnePlus, Oppo, realme (OxygenOS / ColorOS)

- Settings > Apps > App management > Liftoff > Battery usage > **Allow background activity** and **Allow auto launch**.
- Settings > Battery > Battery optimization > Liftoff > **Don't optimize**.
- Gestures: swipe-up and the Recents gesture can stop working with a third-party home app on some builds. Use **3-button navigation** (Settings > System settings > System navigation) if they do.

## Google Pixel

- No battery steps needed; leave Liftoff on **Optimized**.
- Recents and gesture navigation stay with the system launcher and keep working with a third-party home app (the spec's baseline on stock Android).

## Everything else

- Look for "Unrestricted" or "Don't optimize" battery use, an autostart toggle and any "app lock" for Recents.
- If swipe-up does nothing under gesture navigation, switch to three-button navigation and report the device in the Lab page.
- Third-party "dontkillmyapp" style guides list further per-device steps; this file covers the four families on the reference list.

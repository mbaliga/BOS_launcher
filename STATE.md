# STATE — Sphere Launcher

Living handoff. See `README.md` for the module map and `HANDOFF.md`/`ARCHITECTURE.md` for detail.

## Current state
- M0–M4 done: daily-drivable landscape coverflow (set-as-default), all-apps globe, type-to-search,
  pull-down hub, live surface settings, app management, **notifications** feed, and **voice**.
- Version: `v0.1.0-m1`.
- M5 (desktop / DeX large-screen mode) is pending.
- **Widget hosting (2026-11-04, unverified on device):** new `:feature-widgets` hosts any app's App
  Widgets on the equator (as tiles) or in a hub stack: picker, bind prompt + configure flow,
  persisted placements with reconciliation and backup-restore remap (restored placements wait,
  marked pending, for Android's restore broadcast), remove / reconfigure / move / resize. Pure logic JVM-tested (`feature-widgets/src/test`); see `docs/widget-hosting.md`.

- **Pages (2026-11-04, unverified on device):** new `:feature-pages`: an ordered page set (home =
  page 0) with a page manager; full-page App Widgets (reusing the hosting placements on a new `Page`
  surface), the Orrery widget runtime (D-AC packages with the host API and launcher channels; single-file
  pages such as Orrery's, network off by default per page) and website pages; three navigation modes as
  a setting. Adds `INTERNET`; Safe Browsing off (owner decision). Pure logic JVM-tested
  (`feature-pages/src/test`, plus `PageSurfaceTest` in `:feature-widgets`); see `docs/pages.md`.
  A review round (same day) fixed the two-finger release (it could snap back or turn backwards), the
  sandbox (packages can't navigate out or open windows; `open` only after a tap; intents sanitised;
  JS dialogs gated; head block can't be bypassed; per-page sites), page removal (storage really
  cleared), imports (errors can't crash; atomic swap; no sweep of an unread list), http pages
  (cleartext allowed) and backups (WebView profile excluded).

- **Sphere's own home-screen widgets (2026-11-04, unverified on device):** new `:feature-homewidgets`
  provides five App Widgets any launcher can place (also when Sphere isn't home): Gate clock, Battery,
  Now playing (app name only by default), Needs you (counts per app, never content; hidden apps never
  named), App pads. Three size layouts each (stepping down at larger font scales), day/night colours,
  push updates while Sphere's process runs ("as of" times at every size where data can lapse), no new
  permission. The review also fixed the hub the Needs you card opens (`isEnabled` checked DND access;
  its list started empty). Pure logic and the layout files JVM-tested
  (`feature-homewidgets/src/test`); see `docs/widgets.md`. Not hosting: that is `:feature-widgets`.

## Next steps
- M5: desktop / DeX external-display layout + windowing.
- Graduate the hub / settings UI out of `:app/ui` into `:feature-hub` / `:feature-settings` modules.
- Resolve the tracked `core-design` Hyle-consumer debt (see registry) rather than ad-hoc patches.

## Owner-verified (on-device behaviour)
- The build env has no device — all runtime/gesture/render/launcher behaviour is **owner-verified on the phone**.
- Set-as-default (`ROLE_HOME`), coverflow scrub/launch gestures, globe, search, hub, and voice are owner-confirmed on-device.
- **Pages are not yet owner-verified:** every gesture in every mode, two-finger swipes over real
  websites, back behaviour, Orrery's page in the runtime (state kept, fonts, Dial), WebView memory with
  several pages, rotation (list in `docs/pages.md` §8).
- **Sphere's own widgets are not yet owner-verified:** placing and resizing them in Pixel / One UI /
  Nova and in Sphere, refreshes while Sphere runs and staleness when it doesn't, media controls,
  TalkBack, OEM battery managers (list in `docs/widgets.md`).
- **Widget hosting is not yet owner-verified:** bind prompt, configure activities, restore after
  reboot and after backup restore, taps inside the transformed coverflow, hub stack resizing,
  work-profile widgets, OEM differences (list in `docs/widget-hosting.md`).

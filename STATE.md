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

## Next steps
- M5: desktop / DeX external-display layout + windowing.
- Graduate the hub / settings UI out of `:app/ui` into `:feature-hub` / `:feature-settings` modules.
- Resolve the tracked `core-design` Hyle-consumer debt (see registry) rather than ad-hoc patches.

## Owner-verified (on-device behaviour)
- The build env has no device — all runtime/gesture/render/launcher behaviour is **owner-verified on the phone**.
- Set-as-default (`ROLE_HOME`), coverflow scrub/launch gestures, globe, search, hub, and voice are owner-confirmed on-device.
- **Widget hosting is not yet owner-verified:** bind prompt, configure activities, restore after
  reboot and after backup restore, taps inside the transformed coverflow, hub stack resizing,
  work-profile widgets, OEM differences (list in `docs/widget-hosting.md`).

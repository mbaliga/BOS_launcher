# STATE — Sphere Launcher

Living handoff. See `README.md` for the module map and `HANDOFF.md`/`ARCHITECTURE.md` for detail.

## Current state
- M0–M4 done: daily-drivable landscape coverflow (set-as-default), all-apps globe, type-to-search,
  pull-down hub, live surface settings, app management, **notifications** feed, and **voice**.
- Version: `v0.1.0-m1`.
- M5 (desktop / DeX large-screen mode) is pending.

## Next steps
- M5: desktop / DeX external-display layout + windowing.
- Graduate the hub / settings UI out of `:app/ui` into `:feature-hub` / `:feature-settings` modules.
- Resolve the tracked `core-design` Hyle-consumer debt (see registry) rather than ad-hoc patches.
- Porting: see [`docs/PORTING_PLAN.md`](docs/PORTING_PLAN.md). Owner request 2026-11-17 overrides the plan's no-port proposal; Ubuntu Touch is built as an honest reframe (below).

## Owner-verified (on-device behaviour)
- The build env has no device — all runtime/gesture/render/launcher behaviour is **owner-verified on the phone**.
- Set-as-default (`ROLE_HOME`), coverflow scrub/launch gestures, globe, search, hub, and voice are owner-confirmed on-device.

## Ubuntu Touch reframe (added 2026-11-17) - see `ubuntu-touch/README.md`
- Not a port; an app picker + a prototype demo Click. Owner confirmed Liftoff == BOS Launcher (this repo).
- C0 fence: `tools/gen_sphere_vectors.js` -> `fixtures/sphere-vectors.json` (BUILT-HERE; regenerates byte-identically).
- U1 picker (`ubuntu-touch/picker/`): `SphereMath.js` + `AppInventory.js` match the prototype vectors (node, 12 tests, BUILT-HERE);
  QML lints and passes 6 qmltestrunner tests on desktop Qt 5.15 offscreen (BUILT-HERE; not a device).
- U0 confinement spike: code present; **verdict OPEN** (NEEDS-DEVICE-VALIDATION). Picker shows labelled demo entries until it passes.
- U2 demo Click (`ubuntu-touch/demo/`): packaging written; build CI-LANE-WRITTEN.
- CI: `.github/workflows/ubuntu-touch.yml` CI-LANE-WRITTEN (never run). Checklist: `docs/DEVICE_CHECKLIST_UT.md`.
- Owner: UT device; Click id/maintainer (`OWNER-FILL`); signing + OpenStore; Apple/other platforms untouched here.

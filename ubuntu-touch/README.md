# Liftoff for Ubuntu Touch (reframe)

**This is not a port of the Android launcher.** Lomiri owns the Ubuntu Touch home surface, so Liftoff
(BOS Launcher, code name Sphere) can only exist there as an *app picker inspired by Liftoff*, plus a demo
of the HTML prototype. No Waydroid, no Android runtime (program ruling OQ-21). No telemetry, no network
permission for the picker.

| Part | Dir | What it is | Evidence |
|---|---|---|---|
| U1 picker | `picker/` | QtQuick coverflow over `qml/SphereMath.js` (the prototype's lens math), type-to-search, tap to launch via `appid://` | math: BUILT-HERE (node, 12 tests vs prototype vectors); QML loads + 6 tests + render: BUILT-HERE on desktop Qt 5.15 (offscreen, software); Click build: CI-LANE-WRITTEN; on-device behaviour: NEEDS-DEVICE-VALIDATION |
| U0 spike | `picker/qml/SpikePage.qml` | In-app probe: can this confined app list and read `.desktop` files? Prints a verdict | probe code runs on desktop Qt (BUILT-HERE, proves nothing about confinement); verdict NEEDS-DEVICE-VALIDATION |
| U2 demo | `demo/` | `docs/spherelauncher13.html` in `webapp-container`; no inventory, no launching | Click build CI-LANE-WRITTEN; run NEEDS-DEVICE-VALIDATION |

## Behaviour you should expect
- Until the spike has found a real inventory the picker shows 15 **demo entries** under a banner saying so;
  tapping one only shows "launching is disabled". It never presents demo entries as installed apps.
- Launching uses `Qt.openUrlExternally("appid://pkg/app/version")` (policy group `url_dispatcher`). Whether
  Lomiri accepts that from this app, and whether confinement lets the spike read any `.desktop` file, is **unknown**.
- Hub, voice, notifications, set-as-default: dropped (no Lomiri analogue). Globe math is ported and tested
  (`globeItems`/`globeProject`) but the globe view is not built in QML yet.

## Layout / build
- `tools/gen_sphere_vectors.js` (repo root) evaluates the prototype's own functions and writes
  `fixtures/sphere-vectors.json` (C0 fence). `node tools/gen_sphere_vectors.js --check` fails if stale.
- Tests: `node --test ubuntu-touch/picker/tests/*.test.js` and
  `QT_QPA_PLATFORM=offscreen QT_QUICK_BACKEND=software qmltestrunner -input ubuntu-touch/picker/tests/tst_picker.qml`
  (apt: `qmlscene qml-module-qtquick2 qml-module-qtquick-window2 qml-module-qt-labs-folderlistmodel qml-module-qttest qtdeclarative5-dev-tools`).
- `python3 tools/check_ut_packaging.py` - manifest/apparmor/desktop/CMake consistency, common policy groups only.
- Click: `cd picker && clickable build` (and `demo/`: run `./sync-www.sh` first). Both use `pure-qml-cmake`,
  which Clickable forces to architecture `all` (verified in clickable-ut 8.10.0 source), so there is one Click
  per app, not per arch. CI: `.github/workflows/ubuntu-touch.yml` (logic, qml amd64+arm64, click, 26.04 canary).
- Findings while building: Qt 5.15 blocks `XMLHttpRequest` reads of local files unless `QML_XHR_ALLOW_FILE_READ=1`
  (set in the picker's desktop `Exec`); `Item.transform` takes item-local coordinates, hence `cardMatrixLocal`.

## Owner follow-ups
Real Click id/maintainer (`liftoff.mbaliga`, `OWNER-FILL` in both `manifest.json.in`), OpenStore submission,
Click signing, a UT device to run `docs/DEVICE_CHECKLIST_UT.md`, and a ruling on whether `webview` alone is
enough for the demo (some webapp-container templates also request `networking`; the prototype makes no network requests).

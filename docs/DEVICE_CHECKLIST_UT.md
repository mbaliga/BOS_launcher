# Ubuntu Touch device checklist (Liftoff reframe)

Nothing below has been run: there is no Ubuntu Touch device in the build environment. Tick on a real device and paste output.

1. [ ] Install `liftoff.mbaliga_0.1.0_all.click` (`clickable install`, or `pkcon install-local`). Note OS version (e.g. 20.04 / 24.04-1.x).
2. [ ] App opens (via qmlscene `Exec`), shows banner "DEMO entries", coverflow renders with perspective and a violet focus ring.
3. [ ] Drag scrubs cards; release snaps; frame rate acceptable (note device, Qt version). Pixel scale: card is 86 px; check legibility, DPI/grid-unit scaling.
4. [ ] **U0 verdict.** Tap "Run spike (U0)". Paste the full on-screen log here: ______. Outcome:
   - [ ] inventory readable (count: __) -> tap result; banner turns green; entries are real
   - [ ] listing/reads denied -> record the AppArmor denial from `journalctl`/`dmesg` (`grep DENIED`); U1 has no inventory, ship U2 only
   - [ ] `XMLHttpRequest` blocked despite `QML_XHR_ALLOW_FILE_READ=1`
5. [ ] If live: tap focused card; does `appid://...` open the app? Which app ids fail?
6. [ ] Search field: on-screen keyboard appears and filters.
7. [ ] Install `liftoff-demo.mbaliga_0.1.0_all.click`; prototype loads offline, gestures work, no network requests, webview policy sufficient (else add `networking` knowingly).
8. [ ] Uninstall both cleanly.
Then record the U0 verdict in `STATE.md` and update `docs/PORTING_PLAN.md` (U0 row).

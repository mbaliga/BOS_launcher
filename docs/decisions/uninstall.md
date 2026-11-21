# Uninstall (wave 25)

Owner feedback on build 53: "Uninstall doesn't work."

## Root cause
`ACTION_DELETE` on Android 9 (API 28) and later is ignored by the package installer unless the caller holds `REQUEST_DELETE_PACKAGES`. The manifest did not declare it, so `startActivity` returned normally and nothing appeared. Radar's handler and the repository also each built their own intent, and system apps were offered an entry that could never work.

## Decisions
- Declare `android.permission.REQUEST_DELETE_PACKAGES` (normal permission; it only lets the system show its own confirmation). The permission audit in `DeviceKitAuditTest` lists it as a deliberate addition.
- One helper, `core-data` `AppUninstall`: `intent(app)` is `ACTION_DELETE` on `package:<name>` with `EXTRA_USER` set to the app's own profile (a work-profile copy asks about itself), `start(context, app)` starts it, `starter` is the recorded-intent test seam (same idea as `PendingIntentLauncher.sender`). Radar (action list, Tab/Right menu, nested rows) and `AppRepository.requestUninstall` (drawer and home app menu, one shared `AppContextMenu`) all call it. Radar passes its activity context.
- `AppEntry.isSystem` is true for a system app that was never updated (`FLAG_SYSTEM` without `FLAG_UPDATED_SYSTEM_APP`). Such apps get no Uninstall row in Radar or the app menu; App info stays (it offers Disable).
- After the user confirms, the existing `LauncherApps.Callback.onPackageRemoved` path re-emits the inventory; the catalog, home items, dock and Radar already drop apps missing from it (HOM-10, HOM-22). No new broadcast receiver.

## Not verified
Nothing here is device-confirmed: the system dialog, the work-profile user routing and the post-removal reconcile need a device run.

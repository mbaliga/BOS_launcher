# Widget hosting (`:feature-widgets`)

Sphere hosts standard Android App Widgets: any installed app's widgets (the constellation's
own and everyone else's) can be placed as tiles on the coverflow equator or in a stack in the
pull-down hub. This is the launcher side of the widget strategy (`widget-strategy-2026-10-14.md`
§1 item 5, §3: "Orrery Home without pairing still shows every app's own App Widget, because it
is an AppWidgetHost"). No pairing, no network, no new permission.

> **Status (2026-11-04):** compiled and JVM-tested only. The build environment has no device or
> emulator, so **nothing below about touch, rendering, binding or restore has been seen running**.
> Everything in the owner-verify list at the end is unverified.

## Pieces

| Where | What |
|---|---|
| `feature-widgets/…/WidgetPlacement.kt` | `WidgetPlacement` (id, provider, profile serial, surface, slot, hub size), `PlacementCodec` (SharedPreferences line format) |
| `…/PlacementLogic.kt` | `PlacementReconciler` (start-up reconciliation, backup-restore remap), `EquatorMerge` (apps + widgets → equator list), `PlacementEdits` (insert / remove / move / resize) |
| `…/WidgetSizing.kt` | sizes passed to `updateAppWidgetSize`, bind options, hub presets, hub-stack scroll rule |
| `…/PickerModel.kt` | picker filtering, grouping, cell spans, configure rules |
| `…/BindFlow.kt` | the bind / configure state machine |
| `…/WidgetHosting.kt` | process-wide host: one `AppWidgetHost` (fixed `HOST_ID = 0x5348`), placement store, status of each widget, picker loading |
| `…/WidgetHostActivityBinding.kt` | activity glue: start/stop listening, reconcile on start, bind prompt + configure results |
| `…/WidgetHostRestoredReceiver.kt` | `ACTION_APPWIDGET_HOST_RESTORED` (declared in the module's own manifest, not exported) |
| `…/TouchGateLayout.kt` | wraps each hosted view; lets touches through only when the tile is interactive |
| `…/ui/` | Compose: equator tile content, hub stack, picker, widget menu |
| `feature-sphere` | `EquatorItem` (app or widget), `TouchPassThrough`, `SphereState.focusScale` / `focusedItemIndex`, widget card in `CoverflowLayer`, touch rules in `LauncherSurface` |

Everything pure (the first five rows) has JVM unit tests in `feature-widgets/src/test`.

## Host plumbing

- One `AppWidgetHost` per process (`WidgetHosting.get`), so Activity recreation, the restore
  receiver and the UI share it. `startListening` runs in the activity's `onStart`,
  `stopListening` in `onStop` (`WidgetHostActivityBinding`, a lifecycle observer).
- Hosted views are created once per widget id per Activity (`HostedViewCache`) and reused as
  tiles scroll in and out of the equator's ±15-column culling window. When the home composition
  goes away (the Activity is destroyed), the cache is cleared and the shared host is told to drop
  its own references too (`AppWidgetHost.clearViews`), so a recreated Activity doesn't leave the
  old one pinned by views it no longer shows.
- `SphereAppWidgetHost` re-checks placements on `onProvidersChanged`, `onProviderChanged` and
  `onAppWidgetRemoved`; the platform itself resets a hosted view when its provider updates.
  `HomeScreen` also re-checks whenever the app inventory re-emits (the repository's existing
  `LauncherApps.Callback`: package added / removed / changed / (un)available). These re-checks
  are coalesced into one pass posted to the main thread (`WidgetHosting.refresh`): the callbacks
  come in bursts (`startListening` replays everything missed while stopped, and every id the
  launcher deletes calls `onAppWidgetRemoved` back).

## Persistence and reconciliation

- Placements live in SharedPreferences `hosted_widgets` / key `placements`, one line per widget
  (`1;id;package;class;userSerial;surface;slot;hubSize`), the same zero-dependency approach as
  `HiddenAppsStore`. Widget ids themselves persist in the system's AppWidgetService, so both
  survive process death and reboot. Field 1 is always the id; a **negative** id marks a placement
  waiting for a backup restore (see *Lifecycle edge cases*).
- Lines this version can't read (a damaged line, or a newer format after a downgrade) are kept
  verbatim on every rewrite, and the id in their field 1 is **reserved**: never deleted as an
  orphan. One bad line can't destroy a widget.
- On every start (and on provider changes) `PlacementReconciler.plan`:
  - drops placements whose id the host no longer knows (the system deletes a widget's id when its
    provider app is uninstalled **or disabled**: AppWidgetService re-scans the package's
    receivers on `PACKAGE_CHANGED`, finds none, and deletes the provider with its widgets);
  - deletes host ids no placement uses, no bind flow is waiting on and no unreadable line reserves
    (orphans, e.g. a flow cut short by process death), except while a backup restore is pending,
    when nothing is deleted;
  - marks placements whose `getAppWidgetInfo` is null as **unavailable**: the tile says "Widget
    unavailable", names the app when it can, shows a struck-through circle (shape and words, not
    colour) and offers **Remove**. In practice that is a restored widget whose app isn't installed
    yet (a "zombie" provider), or a non-system provider while the phone is in safe mode.
  - If the host's ids can't be read, nothing is deleted or dropped.
- Equator position: a widget's `slot` is its index in the merged list (`EquatorMerge`). Adding at
  an index shifts later widgets; removing closes the gap. **Known limitation:** apps are sorted by
  label, so installing, uninstalling, hiding or unhiding an app that sorts before a widget moves
  that widget to a different neighbour (it keeps its index, not its neighbours). Anchoring a
  widget to the app it was placed next to is a follow-up.

## Picker

- `AppWidgetManager.getInstalledProvidersForProfile` for every profile `LauncherApps.getProfiles()`
  reports, read off the main thread. Home-screen providers only; `WIDGET_FEATURE_HIDE_FROM_PICKER`
  is respected. Grouped by app and profile (work-profile groups carry a "Work profile" tag in words),
  sorted by app then widget label. A local text filter matches app label, widget label and
  description. Nothing is searched or fetched over the network.
- Each row: preview, label, description (API 31+ `loadDescription`), and the size in cells
  (`targetCellWidth/Height` when declared, else the `70 × n − 30` dp rule on the minimum size).
- Preview order: `loadPreviewImage`; else, if the provider has a `previewLayout`, a rendering of it
  in an unbound `AppWidgetHostView` (as AOSP Launcher3 does); if that inflation fails, or there is
  neither, the profile-badged widget/app icon. The rendered preview is behind a closed touch gate.
- Destination: **Equator** or **Hub** (a check mark plus outline marks the choice).
- Entry points: Settings panel → **Widgets → Add widget**; long-press an app → **Add widget here**
  (inserted at that app's place); long-press empty surface → picker; hub stack → **+ Add widget**.

## Binding and configuration (`BindFlow`)

```
allocateAppWidgetId
  → bindAppWidgetIdIfAllowed(id, profile, provider, options with the target sizes)
      true  → configure needed? → startAppWidgetConfigureActivityForResult → OK → place
                                                                          → cancel → deleteAppWidgetId
              no               → place
      false → ACTION_APPWIDGET_BIND (EXTRA_APPWIDGET_ID / _PROVIDER / _PROVIDER_PROFILE / _OPTIONS)
              via an Activity Result launcher
              → OK and the id is now bound → (configure as above) → place
              → anything else → deleteAppWidgetId
  bindAppWidgetIdIfAllowed throws, or the prompt / configure activity can't start → deleteAppWidgetId
```

- "Configure needed" = the provider declares a configure activity and not
  `WIDGET_FEATURE_CONFIGURATION_OPTIONAL`.
- **Reconfigure** (widget menu) appears only for `WIDGET_FEATURE_RECONFIGURABLE` providers; a
  cancelled reconfigure keeps the widget.
- Starting a new add while one is pending gives the pending id back first. Ids a flow is waiting on
  are never deleted by reconciliation.
- The configure result arrives via `MainActivity.onActivityResult` (request code `0x0A51`, below
  the Activity Result registry's generated range); the platform API only offers a request-code form.
- No `BIND_APPWIDGET` (system-only) and no other new permission.

## Placement and sizes

- **Equator:** widgets are tiles mixed with app tiles (`EquatorItem`), keeping the coverflow's
  culling. A widget's view is laid out at the **focus-lens size** (`TILE_SIZE_DP × focusScale`,
  244 dp at the default focus 1.85 in one row; 120 dp in two rows) and scaled by `scale / lens`, so
  it fills the same box an app tile would, and the focused tile at rest is drawn at scale 1 with
  no rotation. The provider is told that one size, `244×244` by default, with
  `updateAppWidgetSize(Bundle, List<SizeF>)` and in the bind options (debounced 300 ms; re-sent
  only when it changes, e.g. the Focus slider or the row split).
  - *Departure from the brief's wording* ("the tile size and its focus-lens magnified size"): the
    view is never laid out at the plain tile size; off focus it is only scaled down as a picture.
    Reporting 132 dp as well would tell legacy providers (which read the min width / max height
    options) they have a 132 × 244 cell, a shape that never appears, and in two rows it would not
    even be a size drawn on screen. API 31+ size-mapped RemoteViews pick their layout from the
    laid-out size, which is this size.
- **Hub:** a stack under "Needs you", full panel width, each widget at **Small 120 dp / Medium
  200 dp / Large 290 dp** (default: the smallest preset at least as tall as the provider's minimum
  height). Each has a label row with an options button (48 dp). The stack is measured **before**
  the notifications, so it gets the height its widgets need; the notifications are clipped to
  what is left, but while there are notifications the stack leaves them about one row (120 dp, or
  half the space if less: `HubStackLayout.viewportDp`). The stack scrolls only when it doesn't fit
  that; when it fits, a drag inside a widget reaches the widget. Note the hub lives inside the
  equator surface, which in portrait keeps 300 dp at the bottom for the keyboard, so a portrait
  hub has roughly 450–550 dp in all.
- Per widget, the menu offers: **Reconfigure** (if reconfigurable), **Move to hub / Move to
  equator** (to the focused position), **Height in the hub** (hub only), **Remove widget**.
- A newly placed or moved widget is brought into view: the equator scrubs to it (closing the hub
  and globe first, so a widget added or moved from inside the hub isn't left behind it), or the
  hub opens.

## Touch model (all arbitrated in `LauncherSurface`)

| Where | Tap | Drag past slop | Long-press | Pinch / spread |
|---|---|---|---|---|
| Unfocused equator widget | focuses it (like an app tile) | scrub / hub pull | widget menu | unchanged |
| Focused equator widget, at rest, nothing in front | goes to the widget (its buttons, list rows) | scrub / hub pull; the widget gets `ACTION_CANCEL` | widget menu; the widget gets `ACTION_CANCEL`, so no click on release | unchanged |
| Hub stack, hub fully open (a `TouchPassThrough` region) | the widget / its options button | the widget (or the stack's scroll when it overflows); the hub does not move | the widget's own | ignored by the surface |
| Hub stack while the hub slides or fades | the surface (as before widgets) | the surface | the surface | unchanged |
| Empty surface | unchanged | unchanged | widget picker (hub closed) | unchanged |

How it works:
- Each hosted view sits in a `TouchGateLayout`. It is open only for the focused tile at rest
  (within 0.02 column) while the hub and globe are fully closed; otherwise it declines the stream,
  so the Compose handler sees an ordinary tile touch.
- Compose's view interop dispatches down/up to the view before the surface's Main-pass handler and
  moves after it, so when the surface claims a drag (and consumes), the view receives
  `ACTION_CANCEL`. After a long-press that **opened something** (a widget or app menu, or the
  picker) the surface reads the rest of the gesture in the Initial pass and consumes it, so the
  view is cancelled before the finger lifts. A long-press that opened nothing (e.g. held on the
  open hub away from any tile) leaves the gesture alone, so a held hub button still clicks on
  release, as before widgets.
- A gesture that starts inside the hub stack is ignored by the surface (no scrub, no hub
  pull/close, no long-press), so hub widgets get normal touch. This holds only once the hub has
  settled fully open (fraction ≥ 0.99); while it slides or fades the stack is not a pass-through
  region and its widgets' touch gates stay closed, so a tap can't reach a widget the user can't
  yet (or no longer) see.
- **A tap on a widget tile while the equator is still settling** (the half second after a scrub
  is released) reaches neither the widget (its gate opens only within 0.02 column of rest) nor a
  focus action (the tile already counts as focused). An app tile in that position would launch.
  The settle completes on its own, and the next tap reaches the widget. Opening the gate earlier
  would deliver touches to a tile that is still scaled or turned, where they would land in the
  wrong place (see *Known risk*).
- The globe and type-to-search stay apps-only.

**Known risk:** hosted views inside 3D `graphicsLayer` transforms. In the Compose version this
build uses (compose-ui 1.7.6 via BoM 2024.12.01), `AndroidView`'s pointer interop hands the view
the original `MotionEvent` shifted by the view's transformed origin only (`localToRoot(Offset.Zero)`):
no inverse scale or rotation. A scaled or rotated widget would therefore receive misplaced
touches. That is why only the focused tile *at rest* is interactive: it is laid out at the lens
size and drawn at scale 1 with no rotation (its transform is a pure translation), so taps land
where they are drawn. Unfocused tiles are scaled and turned, and their touch gate stays closed.
The hub stack is only translated (the hub slides in), so it is unaffected. This reading of the
interop code is from its bytecode, not from a device: owner-verify item 5 below.

## Lifecycle edge cases

- **Package removed / changed / provider updated:** the host's `onAppWidgetRemoved` /
  `onProvidersChanged` / `onProviderChanged` trigger a (coalesced) re-check. Uninstalling or
  disabling a provider app makes the system delete its widgets, so those placements drop out. An
  updated provider keeps its widgets; the platform resets the hosted view.
- **Configuration changes:** `MainActivity` declares `configChanges` for orientation, size, density,
  font scale, UI mode and more, so hosted views survive rotation. A change not in that list (e.g.
  locale) recreates the activity; views are re-created from the shared host.
- **Home key (`onNewIntent`):** closes the picker and widget menu along with the hub, globe and
  settings. A pending bind prompt or configure activity above the launcher is finished by the
  system and returns "cancelled", which deletes the id.
- **Process death / reboot:** ids persist in AppWidgetService, placements in SharedPreferences; the
  next start restores and reconciles them. A bind flow interrupted by process death leaves an
  allocated id that the next start deletes (the activity is `stateNotNeeded`, so its result is not
  delivered to a new process).
- **Backup restore** (`allowBackup` stays true): the placements file comes back with the app's
  data, holding the old device's widget ids. The system restores the widgets themselves under
  **new** ids and tells the host with `ACTION_APPWIDGET_HOST_RESTORED` (old and new ids) at the end
  of the restore, which can be after the launcher has already started.
  - *Detection:* a marker file in `noBackupFilesDir` (never backed up) means "these placements were
    written on this device". Placements found without it came from a restore and are marked
    **restore-pending** (stored with a negative id, `-oldId`), and a restore window starts.
  - *While pending:* the placement shows a "Restoring widget" tile (struck circle, words, Remove)
    and never touches the host: no view, no info lookup, and Remove only forgets it. Reconciliation
    drops nothing pending and **deletes no host id at all**, since any of them may be a restored
    widget whose old id the broadcast hasn't named yet.
  - *Broadcast:* each pending placement the broadcast names gets its new id; others are left alone.
    The system can send more than one broadcast (each carries only records it hasn't sent yet, e.g.
    after a provider app is restored later), so a placement one broadcast doesn't name keeps
    waiting rather than being dropped.
  - *Window:* placements still pending after 3 days are dropped as never restored (then orphan ids
    are cleaned up as usual). A window recorded with a clock that was ahead (possible early in
    setup) restarts when noticed, so a wrong clock can't keep tiles waiting forever.
  - Restored widgets whose app is not installed yet show as unavailable until it is.
  - Reading of AOSP for this: `AppWidgetServiceImpl.BackupRestoreController`
    (`maybeSendWidgetRestoreBroadcastsLocked` sends only un-notified records). Not seen on a device.

## Look

Widget tiles keep the launcher's design: the prototype's `.surf.widget` card (22 dp radius scaled
with the tile, `#161922` surface, violet hairline at 20 % / 55 % when focused) with a violet focus
glow, no label under the tile. Widget content is the provider's own RemoteViews, drawn as is.
Every control added has a content description and a ≥ 48 dp target; selection is shown with a
check mark and words, never by colour alone.

## Owner-verify (none of this has been seen running)

1. **Bind prompt:** adding a third-party widget shows the system "Allow Sphere to create widgets…"
   dialog; Allow places it, Cancel leaves no orphan (check `dumpsys appwidget` for this host's ids).
2. **Configure activities:** a widget with a required configure activity opens it; OK places the
   widget, Back removes it; `configuration_optional` widgets skip it; **Reconfigure** appears only
   for reconfigurable widgets and Back keeps the widget.
3. **Restore after reboot:** placed widgets (equator and hub) come back in place and update.
4. **Restore after backup restore** to a new phone: placed widgets first show "Restoring widget",
   then come back in place once Android sends the restore broadcast; widgets whose apps aren't
   installed yet show "Widget unavailable" until they are. Check both orders: launcher started
   before the broadcast (the usual case during setup) and after. Also check a device-to-device
   transfer: if it copies `no_backup` too, restored placements aren't detected and their widgets
   would be dropped (the old behaviour).
5. **Taps inside the transformed coverflow:** on the focused tile at rest, widget buttons and list
   rows respond exactly where drawn; unfocused tiles never trigger the widget; a horizontal drag
   starting on the focused widget scrubs and the widget's pressed state clears; long-press opens the
   widget menu and does not click the widget on release.
6. **Hub stack:** widgets take taps, their lists scroll when the stack fits, the stack scrolls when it
   doesn't, the hub does not close or move when touching the stack, and Small / Medium / Large
   resizing re-lays out responsive widgets. Check with **three notifications** in the hub (the demo
   feed shows three when notification access is off), in landscape and in portrait: the stack is
   visible and usable, and at least one notification stays visible above it. During the hub's
   open / close animation, taps on the stack area do not reach widgets.
7. **Responsive layouts on the equator:** widgets with API 31 size maps show their larger layout at
   the 244 dp focused size (120 dp in two rows); legacy widgets pick a layout for a square cell;
   changing Focus or the row split re-reports the size (after ~300 ms).
8. **Work-profile widgets:** listed with the "Work profile" tag, bind into the right profile, and
   show the system's paused state when work apps are paused.
9. **OEM launcher differences:** behaviour of the bind dialog and widget updates on the owner's
   phone (OEM skins sometimes restrict third-party hosts or batch updates while the launcher is
   stopped).
10. **Picker previews:** `previewLayout` renderings look right and don't crash on the owner's
    installed apps; work-profile previews may fall back to icons.
11. **Unavailable tile:** reboot into safe mode (third-party providers become unavailable), or
    restore a backup before the widget's app is installed: the struck-circle "Widget unavailable"
    tile shows with a working Remove, on the equator in both one-row and two-row mode (the
    two-row tile uses a compact layout) and in the hub.
12. **Disabled or uninstalled apps:** disabling (Settings → Apps → Disable) or uninstalling a
    widget's app removes its widgets from the equator and the hub (the system deletes them).
13. **Long-press on the open hub:** holding a notification's Dismiss or action button for over
    half a second and releasing still triggers it; long-press on a tile still opens its menu.

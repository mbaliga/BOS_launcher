# Liftoff — spec status

Source: [Spec.md](Spec.md). Status per requirement: **not started**, **partial** (some of it exists), **built** (compiles, covered by unit tests, not on a device), **unmet** (conflicts with the spec as it stands). **Device-confirmed** is set only by the owner after a device check; nothing here is device-confirmed.

Requirement texts are in Spec.md; this table is the single place that says how far each one is.

Counts: built 9, not started 102, partial 36, unmet 1 (of 148).

| ID | P | Status | Note |
| --- | --- | --- | --- |
| PAL-01 | M | partial | Search affordance and swipe exist; typing on home and the keyboard shortcut are not wired |
| PAL-02 | M | partial | App search exists; speed target, alias, typo tolerance and initials not verified |
| PAL-03 | M | partial | Launcher actions are searchable; app shortcuts are not |
| PAL-04 | M | not started |  |
| PAL-05 | M | not started |  |
| PAL-06 | M | partial | Web row exists; check NFR-16 (INTERNET permission is declared) |
| PAL-07 | M | not started |  |
| PAL-08 | M | partial | Menu model built and tested in core-input; not yet shown by Radar |
| PAL-09 | M | not started |  |
| PAL-10 | S | partial | Contacts search exists with channel buttons; enable row for no-permission not verified |
| PAL-11 | S | partial | Media file search exists; incremental index, folder grants not built |
| PAL-12 | S | not started |  |
| PAL-13 | S | not started |  |
| PAL-14 | S | not started |  |
| PAL-15 | C | not started |  |
| PAL-16 | C | not started |  |
| PAL-17 | C | not started |  |
| PAL-18 | M | partial | Three layouts exist; keyboard operation and keyboard-default Flat not built |
| PAL-19 | M | not started |  |
| PAL-20 | S | partial | Preview and pinch only in the typed results list |
| PAL-21 | M | partial | Browser hand-off exists; INTERNET permission still declared (weather), see NFR-16 |
| PAL-22 | S | partial | Provider contract documented (docs/SEARCH_PROVIDER.md); deep results empty until sibling apps ship it |
| PAL-23 | S | not started |  |
| INP-01 | M | not started |  |
| INP-02 | M | not started |  |
| INP-03 | M | built | ContextMenus in core-input, tested; not yet wired to long-press / right-click / Menu key |
| INP-04 | M | built | ShortcutRegistry with defaults in core-input, tested; no shortcut sheet or key handling yet |
| INP-05 | M | built | Default set declared for every command; not yet dispatched on any surface |
| INP-06 | M | built | InputModeTracker in core-input, tested; not yet fed by real events |
| INP-07 | M | not started |  |
| INP-08 | M | partial | Home gestures are assignable; defaults not audited against system gestures |
| INP-09 | M | not started |  |
| INP-10 | S | built | Rebind, conflict detection and export/import in core-input, tested; no UI |
| INP-11 | S | not started |  |
| INP-12 | S | partial | alwaysKeyboard preference exists; per-orientation option not built |
| INP-13 | S | partial | VoiceInputManager exists; on-device-only rule and hidden control not verified |
| INP-14 | C | not started |  |
| INP-15 | M | partial | Corner assignments exist; per-orientation sets and group targets not built |
| INP-16 | M | not started |  |
| INP-17 | M | not started |  |
| INP-18 | M | not started |  |
| INP-19 | M | not started |  |
| INP-20 | S | not started |  |
| HOM-01 | M | partial | Four styles, paged or continuous; contract adapters and frame budget not built |
| HOM-02 | M | not started |  |
| HOM-03 | M | partial | Adaptive icons and themed icon plates exist; icon packs and per-item overrides not verified |
| HOM-04 | M | not started |  |
| HOM-05 | M | partial | Drawer with A-Z index exists; tags, work tab not built |
| HOM-06 | M | not started |  |
| HOM-07 | M | not started |  |
| HOM-08 | M | not started |  |
| HOM-09 | M | not started |  |
| HOM-10 | M | not started |  |
| HOM-11 | M | partial | Grid derives from window metrics; per-size-class arrangements not built |
| HOM-12 | M | not started |  |
| HOM-13 | S | not started |  |
| HOM-14 | S | not started |  |
| HOM-15 | S | not started |  |
| HOM-16 | S | not started | Built-in backgrounds only |
| HOM-17 | C | not started |  |
| HOM-18 | C | not started |  |
| HOM-19 | M | built | Uniform frame (about 15% of short side) for paged and scrolling homes; screenshot tests do not assert it yet |
| HOM-20 | M | not started |  |
| HOM-21 | M | partial | Quick sheet exists on long-press; right-click and Menu key not wired |
| DCK-01 | M | not started |  |
| DCK-02 | M | not started |  |
| DCK-03 | M | not started |  |
| DCK-04 | M | not started |  |
| DCK-05 | M | not started |  |
| DCK-06 | M | not started |  |
| DCK-07 | S | not started |  |
| DCK-08 | S | not started |  |
| WSP-01 | M | built | LaunchRequest / LaunchResolver / LaunchExecutor in core-launch, tested; HomeScreen still launches via the old path |
| WSP-02 | M | partial | Resolver and offered-displays logic built; no menu entry yet |
| WSP-03 | M | partial | Resolver gates on SPK-02; no menu entry yet; spike button in Lab |
| WSP-04 | M | partial | Bounds presets and resolver built; no UI; spike button in Lab |
| WSP-05 | M | partial | AppGroup and sequential launch built; no saving UI or home item |
| WSP-06 | M | not started |  |
| WSP-07 | S | not started |  |
| WSP-08 | S | not started |  |
| WSP-09 | S | not started |  |
| WSP-10 | S | not started |  |
| WSP-11 | C | not started |  |
| WSP-12 | C | not started |  |
| EXT-01 | M | partial | Displays are read by CapabilityRegistry; live hot-plug listener not built |
| EXT-02 | M | not started |  |
| EXT-03 | M | not started |  |
| EXT-04 | M | not started |  |
| EXT-05 | M | not started |  |
| EXT-06 | M | not started |  |
| EXT-07 | M | not started |  |
| EXT-08 | M | not started |  |
| EXT-09 | S | not started |  |
| EXT-10 | S | not started |  |
| EXT-11 | C | not started |  |
| STR-01 | M | partial | Home floats as a rounded card and slides; peek, handle and shortcut not built; portrait takeover not built |
| STR-02 | M | partial | Grouping and dismiss exist; inline reply and pass-through actions not verified |
| STR-03 | M | partial | Slide left = one action, slide right = palette built; hover/focus buttons, menu entries and keys not built |
| STR-04 | M | partial | Rules exist; digest scheduling and export not built |
| STR-05 | M | not started |  |
| STR-06 | M | partial | Media strip exists; grant flow and dock feed not built |
| STR-07 | M | not started |  |
| STR-08 | M | not started |  |
| STR-09 | S | not started |  |
| STR-10 | S | not started |  |
| STR-11 | C | not started |  |
| STR-12 | C | not started |  |
| CFG-01 | M | not started |  |
| CFG-02 | M | not started |  |
| CFG-03 | M | not started |  |
| CFG-04 | M | not started |  |
| CFG-05 | M | partial | Cross-media-bar settings exist; keyboard operation and settings-as-Radar-results not built |
| CFG-06 | M | partial | Onboarding exists; scope not verified against spec |
| CFG-07 | M | not started |  |
| CFG-08 | M | built | Ground Control "Capabilities" category lists every capability with reason and fix, with Copy report |
| CFG-09 | M | partial | Crash-loop detection and a safe-mode banner (widgets off) built and tested; minimal grid surface and snapshot restore not built |
| CFG-10 | S | not started |  |
| CFG-11 | S | not started |  |
| CFG-12 | C | not started |  |
| CFG-13 | M | partial | Category and row navigation by touch only |
| CFG-14 | M | partial | Settings search exists; shared index with Radar not built |
| CFG-15 | M | not started |  |
| CFG-16 | M | partial | Toggles, sliders, segments, preview rails done; steppers, headers, older pages not |
| CFG-17 | M | built | Lab category with spikes SPK-01 to SPK-07 (SPK-05 not built); shown in release builds too during alpha, spec says debug only |
| CFG-18 | S | not started | Live preview dock not built; headless rail render still blank |
| CFG-19 | S | not started |  |
| CFG-20 | M | not started | Application ID decision pending (open decision) |
| NFR-01 | M | not started |  |
| NFR-02 | M | not started |  |
| NFR-03 | M | not started |  |
| NFR-04 | M | not started |  |
| NFR-05 | M | not started |  |
| NFR-06 | M | not started |  |
| NFR-07 | M | not started |  |
| NFR-08 | M | not started |  |
| NFR-09 | M | not started |  |
| NFR-10 | M | not started |  |
| NFR-11 | M | not started |  |
| NFR-12 | M | not started |  |
| NFR-13 | M | not started |  |
| NFR-14 | M | not started |  |
| NFR-15 | M | not started |  |
| NFR-16 | M | unmet | The manifest declares INTERNET (weather via open-meteo); the spec requires none in the core flavour |
| NFR-17 | M | not started |  |
| NFR-18 | M | not started |  |
| NFR-19 | M | not started |  |
| NFR-20 | M | not started |  |
| NFR-21 | M | not started |  |

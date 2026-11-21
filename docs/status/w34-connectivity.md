# Wave 34 connectivity: the Connectivity sheet

Nothing was run on a device. Verified by JVM and Robolectric tests; screenshots were generated and read (see below).

| Item | Status | Note |
|---|---|---|
| Rename "Internet" to Connectivity | built | No user-visible "Internet" existed in the app; the glyphs opened the system's Internet panel. They now open our Connectivity sheet (title, close label, state names, 8 languages). `docs/device-checklist-launchpad.md` row 7 reworded. |
| Sheet as in the mock-up | built (device untested) | `ConnectivitySheet.kt`, shared `PopupSheet.kt` (the Sound sheet now uses it too; `SoundVibrationSheet*Test` still green). |
| Toggles | built | Show the real state, open the system panel, never flip by themselves (`CON_21`, `CON_22`). |
| Network list | partly | Connected network only (location access needed for names and scan results, not added). Lock and tune rendering are tested with fake networks. |
| No SIM, Wi-Fi off, airplane, no permission | built | `ConnectivityStateTest`, `CON_25` to `CON_29`. |
| Share Wi-Fi | partly | No public share action; opens Wi-Fi settings. |
| Manifest | changed | `ACCESS_WIFI_STATE` added (normal permission). `DeviceKitAuditTest` set updated. |

Screenshots: `feature-launchpad/build/screenshots/connectivity-*.png` (portrait and landscape; dark, Wi-Fi on, names hidden, RTL with font scale 2, light, over Comms).

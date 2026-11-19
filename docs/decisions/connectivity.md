# Wave 34 connectivity: the Connectivity sheet

Owner request: "This is what the Connectivity Pop-up should look like (currently called Internet)", with a mock-up: a pure black bottom sheet, title, a mobile-data row (carrier, olive switch), a Wi-Fi row, the network list (connected one blue with a tune icon, others white with a lock), "See all", "Share Wifi" and "Done".

## What "Internet" was
Nothing in our code was called Internet. The signal and Wi-Fi glyphs of the Comms header called the system's `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` (the system names it "Internet"). Our tile kind and every accessible name already said "Connectivity". Those two glyphs now open our own sheet; the system panel is only behind the toggles and "See all".

## Decisions
- **Shared scaffolding**: `PopupSheet` (feature-launchpad) was extracted from the Sound and Vibration sheet: scrim, black 36 dp top radius, title, Back, Escape, tap outside, drag down, slide or fade (reduced motion), width cap, scroll. Both sheets use it (tags `snd.*`, `cn.*`). Like the Sound sheet it stays black in the light theme.
- **Seams**: `ConnectivitySource` (reads a `ConnectivityState`) and `ConnectivityActions` (opens system screens). `ConnectivityState` is a pure function of a `ConnectivitySnapshot` (no SIM hides the mobile row, airplane forces mobile off, Wi-Fi off dims the list, at most 3 networks, the note). Tests use fakes.
- **Toggles never fake a change**: apps cannot switch mobile data or Wi-Fi on Android 12+ (minSdk is 31). The switch shows the real state; a tap opens `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` (mobile) or `ACTION_WIFI` (Wi-Fi), with `ACTION_WIRELESS_SETTINGS` / `ACTION_WIFI_SETTINGS` as fallback. The state is re-read on resume and every 1.5 s while open.
- **Mobile data state** is inferred: a live cellular network with the Internet capability (`ConnectivityManager`). The setting itself needs READ_PHONE_STATE, which we do not add. Carrier name and SIM presence come from `TelephonyManager` calls that need no permission.
- **Network names**: reading the connected SSID and the scan list needs location access (ACCESS_FINE_LOCATION or NEARBY_WIFI_DEVICES); the app holds only coarse location and we add none. So without it the list is the connected network only, drawn as "Connected network" with a quiet note, and "See all" opens the system Wi-Fi panel. Where Android does give the SSID it is shown, secured networks would show the lock (the model and view support it; no code path produces other networks yet). With Wi-Fi off the last connected network seen in this process stays, dimmed.
- **One permission added**: `ACCESS_WIFI_STATE` (normal, no prompt) for `WifiManager.isWifiEnabled`. `DeviceKitAuditTest` permission set updated.
- **Tune and Share**: the tune icon opens the system Wi-Fi settings. There is no public Wi-Fi share action, so "Share Wi-Fi" also opens the Wi-Fi settings, where the network's Share button is.
- Strings: `L10nW34connectivity.kt` (es, fr, de, pt, hi, zh, ja, ar). The label is "Wi-Fi" and "Share Wi-Fi" (the app's spelling) where the mock-up says "WiFi" and "Share Wifi".

# Wave 43: radarWeb (Enter on the Web scope, live web results, Play Store rows)

| ID | Status | Note |
| --- | --- | --- |
| PAL-06 Enter on Web scope | built, not device-verified | Root cause: the Play Store row was the first tile of the Web scope (and of Everything when nothing matched), and Enter runs the first tile. The store row now lives under the Apps results; Enter on Web is the engine's search row (or a URL's page). `RadarWebW43UiTest` per scope (pure `enterTile` and the real IME Go). |
| PAL-06 live web results | built, endpoints not exercised | Suggestions per engine (Google, DuckDuckGo, Bing, Brave), DuckDuckGo answer row, in list, Filmstrip and Orbit (rings); top 3 in Everything below app matches. Client `WebSuggest.kt` + `WebSuggestHttp.kt` (core-data). Tests use a fake fetcher only: no real request was made. |
| PAL-06 Web suggestions switch | built | New setting (default On) in Ground Control > Radar; `searchWeb` ("Web results") kept as is (it hides the whole web hand-off, so it is not the same thing). Round trip and default tested. |
| PAL-06 first-use note | built | Shown once (`radar_ui` flag), tested both ways. |
| PAL-15 Play Store rows | built, not device-verified | Apps scope and Everything: "Search the Play Store for ..." (2+ chars, no network) plus store suggestions; green Play tile and "Play Store" label; installed matches first. In the typed Orbit the store row is a strip circle (always reachable), suggestions are ring tiles. |
| NFR-16 / NFR-17 audits | changed on purpose | INTERNET declared again; audits now pin exactly one connection-opening file (`WebSuggestHttp.kt`), HTTPS only, one manifest, no other network-like permission, no location. `NFR_16_internetIsNotDeclared...` became `NFR_16_internetIsDeclaredOnlyForRadarWebSuggestions...`; `NFR_17_noManifestDeclaresInternet` became `NFR_17_onlyTheAppManifestDeclaresInternet`; the permission set in `DeviceKitAuditTest` gained INTERNET. |
| Default engine | changed | Default stays DuckDuckGo (an attempt to make Google the default was reverted by the owner in wave 44); a stored choice is kept. Ecosia added. |

## Old tests changed
- `RadarPaletteTest.PAL_15_theStoreLookupSitsJustBeforeTheBrowserHandOff` replaced by `PAL_15_theStoreLookupIsInTheAppsResultsNotInTheWebOnes` (the wave 25 order that caused the bug).
- `OrbitTypingW20UiTest`: the Play Store label ("Search the Play Store for", also in `SearchModelTest`, `RadarSearchW18UiTest`, `RadarPaletteTest`); the landscape shot no longer expects the store row for a one-letter query; the strip test also asserts the store row stays a strip circle.
- `RadarSearchW18UiTest`, `RadarHostileQueriesW20Test`: default engine is DuckDuckGo (unchanged).
- `RadarDimW30UiTest.everyLayoutAndQuery`: runs with Web results off, because a coloured Play Store row now exists for every query of 2+ letters and that test is about the wheel's background pixels.

## Tests run
core-data (whole module, `WebSuggestW43Test`), core-design (whole), core-quality (whole), feature-search (whole), feature-grid (whole: 1290+ tests, `RadarWebW43UiTest` 27), app (whole: 2 failures, neither from this wave: `PackageRemovedW26SmokeTest` is the known flaky one; `SettingsOneUiW40Test.CFG_21_...` reports the row "Home screen / Clock" missing, from the wave 41 clock change). `assembleRelease` was not run.

## Device only / not verifiable here
Real endpoint behaviour (shape, refusal, rate limits; the Play suggestion endpoint is old and undocumented), latency, the 2 s timeouts on a bad network, the look on a phone, the market:// hand-off with a real Play Store, TalkBack.

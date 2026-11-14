# Decision: NFR-16, no INTERNET permission in the core flavour

**Status: open (owner decision). NFR-16 is unmet.** The manifest declares `android.permission.INTERNET` today, and it must stay until this is decided.

## Why it is declared

`core-data/.../Weather.kt` fetches current conditions for the clock from `api.open-meteo.com` with an approximate location (`ACCESS_COARSE_LOCATION`). That is the only network use in the code base. Web search results already hand off to the browser with an intent, which needs no permission (PAL-06, PAL-21).

Tests that pin this state: `NFR_16_internetIsDeclaredOnlyForWeatherAndTheDecisionIsDocumented`, `NFR_17_onlyWeatherOpensAnHttpConnection`. If the permission is removed or a second network user appears, those tests fail on purpose.

## Options

| Option | What the user sees | Cost | Meets NFR-16 | Meets the pre-production gate line "no network permission" |
| --- | --- | --- | --- | --- |
| A. Remove weather from the core flavour | No weather on the clock in the Play build | Lose a visible feature; delete or flag-off `Weather.kt` and the clock complication; remove `ACCESS_COARSE_LOCATION` | Yes | Yes |
| B. Keep weather in a separate flavour (e.g. `weather`) that adds INTERNET via a flavour manifest | Core build has none; weather build has it | Changes product flavours (an open decision, not done here); two APK variants to test and release | Yes for core | Yes for core |
| C. Weather from a sibling app through the provider contract (docs/SEARCH_PROVIDER.md style) | Weather appears when a weather provider app is installed | Needs a provider app and a contract; the launcher never touches the network | Yes | Yes |
| D. Weather from the system | Weather via a user-granted widget (host a weather AppWidget) | Zero network code in the launcher; less control over layout; the clock complication becomes a widget slot | Yes | Yes |
| E. Keep INTERNET and amend the spec | Unchanged | Spec text and the gate line change; store listing must declare network use; weakens the privacy claim (NFR-17 stays true: no analytics, no upload) | No, by amending it | No, by amending it |

## Recommendation

D if the existing widget hosting is good enough for a weather widget (cheapest, nothing to maintain), otherwise B (keeps the feature for people who want it without weakening the core build). A is the fallback if neither is wanted. The spec's wording ("Web results hand off to the browser") points to A, B, C or D.

## What changes in the code for each option

- A, B: remove the `INTERNET` and `ACCESS_COARSE_LOCATION` lines from `app/src/main/AndroidManifest.xml` (B: move them into the flavour manifest), gate `Weather` so it returns "unavailable" without the permission, update the two audit tests above to assert the new state.
- C, D: as A, plus the replacement surface.

Nothing in this stream removes the permission; this file is the input to the decision.

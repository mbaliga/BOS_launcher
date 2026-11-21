# Wave 33 commsDebt: Comms debts

Nothing was run on a device. Verified by JVM and Robolectric tests; no new screenshots were generated or judged in this wave.

| ID | Status | Note |
|---|---|---|
| 1 Comms open state survives process death | built (device untested) | `CommsOpenStateW33Test` (app): `StateRestorationTester` restores the open state, a Home press closes it, a non-zero first signal is not a press, tab and filter restore. `CommsDebtW33Test.CMS_W33_01`: same for `HubState` in feature-launchpad. Scroll position relies on `rememberLazyListState`'s own saver (no dedicated test). |
| 2 Merge off the main thread | built | `CommsDebtW33Test` CMS_W33_03..06 (inline for small, live rows until the background merge finishes, previous result kept while recomputing, hub off). `CommsMergeBenchW33Test`: 5000 entries well under 100 ms. |
| 3 Tab row fit | built (device untested) | `CommsTabFitW33Test`: pure plan (slots, no slots, tighten, scroll) and Robolectric on 360 dp at Comms 125 percent, font scale 2 and both: each selected tab is inside the row. The Robolectric cases would also have passed with the old always-scrolling row; only the pure test pins the letter-tightening. |
| 4 Usage rows 48 dp, empty day | built | `UsageDebtW33Test`: rows at least 48 dp, icon still 20 dp, block at most 380 dp, 48 real dp at Comms 125 percent, "No recorded time" for an empty day (and German), gone on a day with data. |
| 5 HubPanel read styling | built (device untested) | `HubPanelReadStyleW33Test`: dot, weight (600 versus 400), headline and second-line colours differ, state descriptions. |

## Changed existing tests
- None deleted. `UsageBlockCases.USG_10` and `UsageSimplifyW29Cases` (block height windows) widened from 250..320 dp to 250..380 dp because the rows are now 48 dp (five rows, +60 dp).

## Device only / not done
- Real process death (`adb shell am kill`) with Comms open under an app: only the saved-state emulation is tested.
- Tab row at 125 percent or font scale 2 on a real phone; TalkBack on the shade rows; contrast of the shade rows on the real translucent panel (the Comms palette is the dark page, the panel is translucent over the wallpaper).
- The merge benchmark depends on machine load; the 5000-entry case measured here is far below the budget.

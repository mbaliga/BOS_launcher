# Wave 23: independent sweep of waves 17 to 22

The sweep read the code of waves 17 to 22 as if written by someone else: lifecycle and state, threads, strings, colours, dead code, races and crash paths. Nothing here is device-verified; Robolectric and unit tests only. Defects and the test that pins each are in `docs/status/w23-auditSweep.md`. This file records the calls that are decisions rather than plain fixes.

## 1. The crash recovery screen stays English only

`:crash-recovery` is a vendored, app-independent library (no `core-design`, no `L10n`): after a crash the process must not depend on the launcher's string tables, which are the thing that may have crashed. Its words ("Replay the crash animation", "Discard this report", the toast) are therefore English. Translating them needs a resource-based string table inside the library and a new translation pass; that is an owner call (the library is meant to be reused by other apps), so it is left as residue, not hidden. The launcher's own safe mode and the Crash reports pane are translated as before.

## 2. A preview is inert, including Discard

The preview intent shows sample content so the screen can be reviewed. Continue and Reset were already inert; Discard was not: it deleted the real waiting report (when one existed) and said "Report deleted". Discard in a preview now says nothing was deleted and closes. Rule: nothing reachable from a preview may change stored data.

## 3. Retired preference keys are purged once per list, marked by wave

`RetiredPrefKeys.purgeOnce` writes a marker file so the purge runs once per install. A marker named for wave 20 meant a key retired later (`networkDots`, a toggle no screen ever offered) would never be purged on a phone that already ran it. The marker name now carries the wave (`retired_pref_keys_w23`); a later wave that retires a key renames it again. Settings imports drop the key as well.

## 4. Refinement chips are localized at the display edge

The search model (`feature-search`) emits English labels and stays Android and language free. `localizeChip` in feature-grid translates the words around the user's text where the chips are shown, and the "Name contains" chip is dropped by testing the model's English, never the translated words (on a translated phone it used to appear twice, once in English).

## Not decided here

* Several small battery/charging readers exist (Glance, Complications, IndicatorSource, SystemState). Merging them is a refactor across shared files; left as residue.
* `SignalLogic`'s old pure debounce copy was deleted; the real `settled` operator is tested on virtual time instead.

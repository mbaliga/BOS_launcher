# Landing: what changed in the spec (wave 12, owner request)

Request: "a home landing which is just recents ... Selected apps / recent apps / all apps and minimal, which is just the wallpaper and the four hotspots."

| Row | Old text | New behaviour |
| --- | --- | --- |
| HOM-01 | The home is the user's pages in a chosen style; a fresh install starts with one empty page and a hint. | Unchanged as the Selected apps landing. Added: the home can land on three other surfaces, switching changes nothing saved, and the empty-home hint also offers "Or show recent apps". |
| HOM-05 | The drawer lists all apps with index, filter, tabs, tags and hidden apps. | Unchanged. The same content is also the All apps landing. |
| HOM-22 | (new) | Four landings, recents rules, Minimal hotspots, switching paths, edit-mode rule, privacy class. |
| INP-16, INP-17 | Corner hotspots with dwell reveal, click, keyboard nodes. | Unchanged; on Minimal they are the only controls and carry default assignments when still on the factory set. |
| INP-07 | Every action has a touch path. | Two new corner actions and one command are covered (quick sheet, Ground Control, corner editor). |

Why: owner request. The first-run default of wave 8 (empty Selected home) is kept; Recents as the default is an owner choice and a one-line change.
Details: see `docs/decisions/homeLanding.md`.

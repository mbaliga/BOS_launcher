# Orbit stays while typing (wave 20; PAL-18, PAL-07, PAL-12, PAL-20, PAL-09)

Owner (wave 18): "the first circle should have apps most frequently searched for, and further rings have lesser frequency opened items". Wave 18 tiered the resting Orbit; typing still dropped to the Flat list. Wave 20 keeps the chosen layout while typing.

## Reading of PAL-18

PAL-18 says: "When the active input is keyboard or pointer, results default to Flat unless the user has pinned a layout." That rule is kept as written. `RadarLayoutPolicy.effective` still turns the layout into Flat for a hardware keyboard or pointer that is not pinned, and the wave 20 change (`RadarLayoutPolicy.showsFlatList`) only decides what an Orbit or Filmstrip layout does while typing. So: touch users on Orbit keep the rings; people with a keyboard get Flat unless they pinned Orbit (then they get the typed Orbit, walked with the arrows, PAL-07). The Filmstrip still gives way to the typed list (not asked to change).

## Typed Orbit

- Order: the Everything mix (best of each kind in turn: apps by the engine's match, people, actions, files), where apps and hub hits are already match quality blended with use (`AppSearch`, `HubOrdering`/`Ranker`, PAL-09); the answer line first. Not re-sorted a second time: a second blend would double count use.
- Rings: `RadarRings.assign`, capacities 5, 9, 13, 17 limited by what fits on screen. Best match innermost.
- Stability: `RadarRings.stableOrder` keeps the best match first and, inside a ring, the order of the previous keystroke (newcomers after incumbents), so tiles do not swap places while typing. Ring membership is the rank's ring, so a result changes ring only when its rank crosses a boundary. Boundaries use full ring capacities; where the screen has room for fewer, the Orbit drops the outer ones as before.
- TalkBack tiers while a query is typed: "Best match", "Close", "Related", "Other" (L10n, eight languages); the resting names ("Most used" ...) are unchanged.
- Result strip (`ResultStrip`): web hand-off rows (the URL's own row, "Search <engine> for ...", Play Store row) and a Filenames chip (scopes the wheel to Files when files can be searched) in one horizontally scrolling line of pills at the bottom of the free area. The rings and wheel are centred in the area above it. Web rows are never on the rings. They are in the keyboard walk after the ring tiles, and long-press/right-click previews them. Enter opens the first ring result; a URL query still goes to its own row (`enterTile`).
- The Kinds/Content refinement chips stay in the Flat list; the wheel is the scope in the Orbit.
- Not changed: PAL-09 learning, Flat, Filmstrip, preview (PAL-20, same tile actions on rings and strip).

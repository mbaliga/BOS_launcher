# Radar search (wave 18): input at the top only, Orbit rings by use

Owner message on build 49 (authoritative): "I think the search input should be at the top only, I was wrong before. For the
radar thing, I think the first circle should have apps most frequently searched for, and further rings have lesser frequency
opened items, that way it makes sense." (The prefix and bang removal is in `radarSearchCleanup.md`.)

## 1. The query line is at the top only

What the old spec and code said: while typing, Radar showed a results list with the query line at the bottom above the
keyboard (INP/PAL-18 text and `SearchOverlay`); while browsing (Orbit, Filmstrip) the text field was an invisible 1 dp box
that only fed the keyboard, and "SEARCH" next to the category wheel stood in for it. The owner had earlier asked for the line
at the bottom ("I was wrong before").

What changed:

- One visible field at the top of every layout, in both orientations, below the status bar and cutout (`safeDrawing`): a pill
  with the placeholder "Search", the scope menu and clear button (typed results and Flat) or a close button (Orbit and
  Filmstrip at rest), and push-to-talk (INP-13) when an on-device recogniser exists. The results fill the space below it and
  end where the keyboard starts (`imePadding`), so nothing is hidden under the keyboard. Focus lands in the field on open.
- **No bottom/top setting existed to remove.** The brief spoke of "INP-12's per-orientation bottom/top option". INP-12 is
  "raise the keyboard when Radar or the drawer opens, per orientation" (`keyboardOnOpenPortrait`, `keyboardOnOpenLandscape`);
  it is unchanged and still correct. The bottom position was hard-coded, so what was removed is the layout code that
  placed the line at the bottom, not a pref.
- Orbit: the rings are centred on the middle of the free area below the line (they used to be centred on the screen's middle),
  with the category wheel in the centre as before.
- The scope menu fans out downward from the top line.

## 2. Orbit rings are tiers of use

Definition (`RadarRings.kt`, pure and tested):

- Use per item = decayed count. Each launch in the home's launch history counts `0.5 ^ (age in days / 14)`. What Radar learned
  (`PaletteHost.learn`, key and count and last time only) counts `count * 0.5 ^ (age since last use / 14)`. The two overlap (a
  launch made from Radar is in both), so the larger of the two is used instead of their sum.
- Empty query: apps with a use above zero, most used first; ties keep the newest first; then people. "Everything" merges apps
  and people by use (apps before people when equal). Apps never opened are not drawn; with nothing learned the quiet line
  "Rings fill up as you use Radar" shows instead. Items on the rings: up to 44 (5 + 9 + 13 + 17).
- Ring assignment: results in rank order fill ring 0, then ring 1, and so on. Capacity of ring n is `5 + 4n`, limited by how
  many of the ring's slots fit on the screen when the rings do not move (on screen, clear of the wheel text and of each other).
  Results with no slot are not drawn and the keyboard never selects them.
- Query typed: Radar shows typed results as the Flat list as before (PAL-18 text unchanged on that point); the order is
  match quality blended with use (`Ranker.blend`, `HubOrdering`), and the Orbit, wherever it is shown with a query, lays results
  out in exactly that order, best on the innermost ring. Stability (PAL-12): ring is a function of rank, `StableOrder` keeps the
  selected row's rank, so a result changes ring only when its rank crosses 5, 14 or 27 results in.
- Each ring is a faint guide circle (brighter toward the middle) and a quiet TalkBack heading ("Most used", "Often used",
  "Sometimes", "Rarely"); every tile also states its tier. Keyboard Up/Down walks the results in rank order, which is ring by ring.
- Flat at rest used to show nothing; it now lists "Most used" apps and people in the same order. Filmstrip at rest uses the
  same order.

Not done, on purpose: typing does not keep the Orbit on screen (typed results remain the Flat list); that is an existing
design (PAL-18) the owner did not ask to change.

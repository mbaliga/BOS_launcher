# launchpadPolish (wave 26)

- The Launchpad list ends exactly at the home card's near edge (the visible sliver); the old extra 8 dp and the 48 dp return-zone inset are gone. Rows, section bands, dividers, the switcher row and the compose button are laid out against that edge. Row text keeps its own 16 dp end padding so the time stays whole.
- The card no longer casts a drop shadow (it fell over the rows beside it); the scrim and the hairline edge still separate it. The 48 dp return touch target is unchanged, so it may overlap the last few dp of a row.
- The Groups and rules manager is a screen of buttons, so it still stays clear of the 48 dp return zone.
- A group header names a group of two or more items and carries the count. A one-item sender, conversation or app group has no header, and cannot be collapsed. Date bands, Pinned, Digest and user groups keep theirs; Flat has none. The jump strip still lands on a headerless group's first row.
- History views: an app or sender header needs two or more rows and shows the count; day bands always show.

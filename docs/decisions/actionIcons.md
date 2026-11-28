# Radar action icons (wave 25, owner item 6)

Owner: "if I search for GPay, I'd get 'scan and pay' as a suggestion - can these suggestions have appropriate icons?"

Decisions
- Icon order for any suggestion, action or shortcut row or pill: (1) an app shortcut's own icon, read with the public
  `LauncherApps.getShortcutIconDrawable`, loaded on `Dispatchers.IO` and kept in a count-bounded (48) cache that also remembers misses and
  failures; (2) a built-in outline glyph per `ActionKind` (24 dp viewport, theme tint, mirrored when directional); (3) the engine glyph for
  web rows; (4) the avatar for contacts (unchanged); (5) the owning app's small icon, never a blank gap or a dot.
- `ActionKind` is the one table. `ActionIcons.kind(MenuAction)` is an exhaustive `when`, so a new menu action without an icon does not compile;
  `ActionIconsW25Test` also walks every launcher command, every result-menu entry and every kind.
- Where it shows: Actions rows and Orbit/Filmstrip tiles (via `HitFace`), the nested shortcut pills (16 dp, compact: the pill height is unchanged
  within 2 dp), the Tab/Right action list (20 dp), the Flat suggestion rows (20 dp), the preview (it reuses the tile face and pills).
- Icons carry a null content description (the text says it); a pill uses one merged description, so TalkBack never reads it twice.
- The shortcut icon source is a seam (`ShortcutIcons.source`) so tests seed a fake without a real launcher host.
- Not changed: the "+N" pill has no icon (it is a count, not an action).

# Wave 25 musicNotch (owner item 3)

| ID | Status | Note |
|---|---|---|
| HOM-23 | Done in code and tests | Notch is one Bezier path merging into y = 0 (geometry test: tangent along the top edge, concave, monotone, no vertical segment; overdraw past the edge). Title pushed down by the notch body and back, marquee/static/reduced-motion variants; portrait, landscape, font scale 2.0 no-overlap tests. Screenshots glance_w25_*.png read. |
| STR-06 | Changed | The media strip is removed everywhere; the controls are only in the notch. Quick-sheet media switch, `mediaControls` pref and ChromeElement.MediaStrip removed. Tests for the strip (screenshot, artwork, 200 percent) deleted because the strip is intentionally removed. |
| NFR-13 | Done | Reduced motion: title does not scroll (ellipsis), push snaps. |
| NFR-14 | Done | Font 2.0: title stays clear of the notch. |
| DCK-08 | Changed | The dock rail no longer has a media item (DockContent.media removed); DCK_08 test replaced by a semantics search on both edges. |

Verified in Robolectric only. Device-only: how the notch looks around a real cutout, marquee smoothness, edge fade over weather effects.

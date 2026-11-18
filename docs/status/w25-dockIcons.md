# w25 dockIcons status

| ID | Status | Note |
| --- | --- | --- |
| DCK-01 | done (Robolectric) | Icons only on bottom and side rail; no text, no "i", no "...". Long-press menus for icon and dock. Desktop dock uses the same rail. |
| DCK-05 | unchanged | Workspace step keys and swipe on the home button still work; TalkBack gets next/previous workspace actions. |
| WSP-05, WSP-07 | done (Robolectric) | Workspaces/groups/sessions panel reached from the dock menu ("Workspaces and groups", "Dock settings") and still from Ground Control. |
| INP-01 | done (Robolectric) | Menu key / Shift+F10 open the menu; the two removed buttons left the focus graph. |
| INP-20 | done | New Edit dock dialog installs the dialog hook (source audit counts 7 dialogs). |
| EXT-10 | not separately touched | Desktop dock shares DockRail. |

Verified: feature-dock unit tests, app dock smoke tests, core-quality audits (see final report). Device-only: how the popup looks and feels, long-press timing against reorder, Edit dock and pickers on real hardware. Nothing here is device-confirmed.

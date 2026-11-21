# w30 commsPolish status

| ID | Status | Note |
|---|---|---|
| CMP-01 header alignment | done | CommsPolishHeaderTest: glyph and title ink centres within 1 px at 100/75/125 percent, light, RTL, font 2.0; glyph height equals the cap height |
| CMP-02 gap and left edges | done | glyph left 70 px, title left 129 px as the mock-up |
| CMP-10..14 read/unread style | done | CommsPolishReadCases (portrait): dots, weight, colours, state descriptions, live flip, filters, history-only rows, digest dot |
| CMP-15 screenshots | done | build/screenshots/comms-read-style-portrait-*, comms-header-* (read) |
| CMP-20 contrast | done | CommsPolishContrastTest, dark and light |

Old tests that encoded the quiet styling: none needed changes (CommsReadScreenCases only asserts the text, which is unchanged). The CML_01 mock-up geometry test still passes at its 2 px tolerance, so its expectations were not edited.
Device-only: TalkBack announcements, real-display contrast and the 150 ms fade feel. The app module's HubPanel rows were not restyled (other stream).

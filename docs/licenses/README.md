# Bundled font licences

Liftoff ships these fonts for its own UI text (Settings, Look, Font). Every one is under the SIL Open Font License 1.1; the full licence texts are
the `OFL-*.txt` files here (copied unchanged from the upstream repositories listed in each file). The fonts are used as they are, only cut down to the
Latin character range (Basic Latin, Latin-1, Latin Extended-A, common punctuation, arrows and the euro sign) and, for the two with an optical-size axis, fixed at
their text size, so the APK stays small. The files are in `core-design/src/main/res/font/` and are named by `FontCatalog` (`core-design/.../Type.kt`).

| Font | Files | Licence text | Source |
| --- | --- | --- | --- |
| Space Grotesk (default UI font, all titles) | `space_grotesk.ttf` (variable, weight) | `OFL-spacegrotesk.txt` | github.com/floriankarsten/space-grotesk via google/fonts `ofl/spacegrotesk` |
| Inter | `inter.ttf` (variable, weight) | `OFL-inter.txt` | github.com/rsms/inter via google/fonts `ofl/inter` |
| Manrope | `manrope.ttf` (variable, weight) | `OFL-manrope.txt` | github.com/sharanda/manrope via google/fonts `ofl/manrope` |
| DM Sans | `dm_sans.ttf` (variable, weight) | `OFL-dmsans.txt` | github.com/googlefonts/dm-fonts via google/fonts `ofl/dmsans` |
| JetBrains Mono | `jetbrains_mono.ttf` (variable, weight) | `OFL-jetbrainsmono.txt` | github.com/JetBrains/JetBrainsMono via google/fonts `ofl/jetbrainsmono` |
| Atkinson Hyperlegible | `atkinson_hyperlegible_regular.ttf`, `..._bold.ttf` | `OFL-atkinsonhyperlegible.txt` | Braille Institute via google/fonts `ofl/atkinsonhyperlegible` |
| Lexend | `lexend.ttf` (variable, weight) | `OFL-lexend.txt` | github.com/googlefonts/lexend via google/fonts `ofl/lexend` |

The OFL lets the fonts be bundled and redistributed with software as long as the licence text travels with them and the fonts are not sold on their own.
Subsetting is a modification. Only Lexend declares a Reserved Font Name ("RevReading Lexend"); the cut file keeps the family name "Lexend" and does not use that reserved name.

# Decision records

One line per record, newest wave first. A record explains why a requirement is built the way it is and how to undo it; the requirement texts are in [../Spec.md](../Spec.md) and how far each one is in [../SPEC_STATUS.md](../SPEC_STATUS.md). Where a later record replaces part of an earlier one, the earlier line says so.

| Record | Wave | What it decides |
| --- | --- | --- |
| [homeLandingPlacement.md](homeLandingPlacement.md) | 24 | Placing a home item from Glance, Recents or All apps switches to Selected apps; a workspace switch ends edit mode (HOM-02, HOM-07, HOM-12, HOM-15) |
| [orbitTyping.md](orbitTyping.md) | 20 | Radar keeps the Orbit while typing: best match in the innermost ring, tiers named by match, web rows and Filenames in a compact strip (PAL-18, PAL-12) |
| [radarGlass.md](radarGlass.md) | 25 (21, 22) | **Wave 25 supersedes the glass:** black gradients only on the field, Clear X, Close and mic; rings fitted so nothing leaves the display and all recents show at once; no dead touch; landscape uses the free height above the keyboard (PAL-18, PAL-20, INP-12, NFR-13, NFR-15). Waves 21/22 glass kept as history |
| [startupCrashes.md](startupCrashes.md) | 18 | Startup crashes from old persisted state: tolerant preference reads, the crash-counting loop guard (CFG-09, NFR-12) |
| [radarSearchCleanup.md](radarSearchCleanup.md) | 18 | Radar loses the typed prefix and the `!bang` syntax; what was removed and how to restore it (PAL-13, PAL-19) |
| [radarSearch.md](radarSearch.md) | 18 | Radar's query field at the top only; Orbit rings tiered by use frequency (PAL-18, INP-12) |
| [crashRecovery.md](crashRecovery.md) | 18 | The vendored `:crash-recovery` library in front, our Safe mode as the second layer (CFG-09) |
| [glanceEdges.md](glanceEdges.md) | 17 | Glance edges: Launchpad on the left, recents on the right, quiet handles (STR-01, STR-14, NFR-20) |
| [glance.md](glance.md) | 16 | The Glance home: clock, date, now playing, weather ambience, hotspots, surface indicator (HOM-22, HOM-23, INP-16); replaces the Minimal landing |
| [recents.md](recents.md) | 15 | The right-hand Recents panel and its shared source with the Recent landing (STR-14, HOM-23) |
| [backgroundsReset.md](backgroundsReset.md) | 15 | The old backgrounds gallery is removed: what went, why, how to get it back |
| [backgrounds.md](backgrounds.md) | 15 | The two-slot, signal-driven background model: five living wallpapers, four effects (HOM-16, PAL-24) |
| [signalFx.md](signalFx.md) | 14 | Radar scope, glow lines, comic no-signal lines and the dot grid (PAL-23, PAL-24); the Radar-only settings it added were removed in wave 15 |
| [radarSignal.md](radarSignal.md) | 14 | The signal source and signal views behind the background (PAL-24, NFR-16, NFR-17) |
| [notifFix.md](notifFix.md) | 11 | Notification tap-to-open and the roomy list (STR-02) |
| [landing.md](landing.md) | 12 | The landing setting: what changed in the spec at the owner's request (HOM-22, INP-07, INP-16, INP-17); its Minimal option became Glance in wave 16 |
| [homeLanding.md](homeLanding.md) | 12 | Home landing decisions: four landings, switching without loss (HOM-22) |
| [groups.md](groups.md) | 10 | Launchpad groups: rules, people, assignments and the Launchpad views (STR-03, STR-04, STR-09, STR-13) |
| [launchpadGroups.md](launchpadGroups.md) | 10 to 13 | Launchpad groups: design decisions (STR-13) |
| [cardTune.md](cardTune.md) | 9 | Launchpad card tuning (STR-01, STR-10) |
| [launchpadCard.md](launchpadCard.md) | 8 | Launchpad as a shrunken home card instead of a full-screen panel (STR-01, STR-07 to STR-09) |
| [editCard.md](editCard.md) | 8 | Edit home as a card with a page carousel, empty default home, add carousel (HOM-12) |
| [CFG-17.md](CFG-17.md) | earlier | Who sees the Lab (spikes) category |
| [NFR-16.md](NFR-16.md) | earlier | `INTERNET` stays in the manifest for weather: options and costs (still an owner decision) |
| [CFG-20.md](CFG-20.md) | earlier | Package identity (application ID) |
| [tourSmoke.md](tourSmoke.md) | 22 | Whole-app tour smoke suite (fresh, busy, recreate, panes, seeded monkey walk), the defects it found, and what Robolectric cannot cover |
| [a11ySweep.md](a11ySweep.md) | 23 | Glance notch hit area (drawn = touchable, 48 dp), the whole-app TalkBack and semantics sweep, its exceptions and limits |
| [deviceKit.md](deviceKit.md) | 23 | Lab Device checks page: frame-timing probe, cold start, checklist and copyable report (NFR-05, NFR-12 to NFR-14, SPK-01); evidence only |
| [launchpadRules.md](launchpadRules.md) | 24 | Launchpad groups: the manager inside Launchpad, the people picker and members, Priority breaks through Do Not Disturb in Liftoff (STR-13) |
| [auditSweep.md](auditSweep.md) | 23 | Independent sweep of waves 17 to 22: what was found and fixed, what was removed as dead, and the three calls that are decisions (English-only recovery screen, preview inertness, a retired-key marker per wave) |
| [dockOptional.md](dockOptional.md) | 24 | The dock stays optional: dock panel from Ground Control, "Focus the dock" with no rail to come, survey of every dock feature with the dock off |

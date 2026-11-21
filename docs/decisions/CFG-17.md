# Decision: CFG-17, who sees the Lab (spikes) category

**Status: open (owner decision). CFG-17 stays partial.** Section 5 says the Lab category (spikes SPK-01 to SPK-07) is for debug builds. The alpha release APK lists it too, so the spikes can be run on the APK testers install. That behaviour is kept on purpose; it is now a switch instead of an accident.

## What the build does

- `BuildConfig.LAB_VISIBLE` (app module) is generated for every build type.
- **Debug builds: always true.** Nothing changes that.
- **Release builds: true by default while the project is alpha.** `-Pliftoff.lab=false` makes it false and the Lab category is not listed. Any other value (or no property) keeps it listed.
- `LabGate.visible(debug, labVisible)` is `debug || labVisible`. `SettingsCategories.kt` lists the Lab category only when this is true and a spike exists.
- Tests: `LabGateTest` pins both settings of the gate, that a debug build has the field on, the Gradle default and the property name, and that the category is listed through the gate.

## Owner step before a public build

Either build the public APK with `-Pliftoff.lab=false`, or change the default in `app/build.gradle.kts` (`labVisibleRelease`, the `!= "false"` test) so a plain release hides the Lab. The second is the safer choice: it cannot be forgotten on the command line. Flip it in the same change that fixes the application ID (CFG-20).

## Why this is not closed

Whether the alpha default stays visible until public release, or the spikes become debug-only now, is a product decision. The code can honour either; it cannot pick one. CFG-17 should be marked built only after the owner decides and the default is set accordingly.

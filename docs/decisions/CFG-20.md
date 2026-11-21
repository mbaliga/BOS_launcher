# Decision: CFG-20, package identity (application ID)

**Status: open (owner decision). CFG-20 is not met.** The application ID is still `com.bos.sphere`. Nothing in this stream changes it. The requirement says the ID is decided before any public or store build; that decision is the owner's, so the acceptance criterion cannot be ticked from code.

## Why it matters

Changing the application ID later makes Android treat the new build as a different app. Existing installs do not update; they sit beside it, with their own data, and the user would have to set the new one as home again and re-import a setup file. The ID also becomes the Play listing's permanent identity.

## What is already safe (audited in this stream)

- **Code never reads the literal.** Every `ComponentName` is built from `app.packageName` (the desktop activities in `DesktopController`, the dock's run activity, the notification listener). The only literals are class names under the Kotlin namespace `com.bos.sphere`, which is independent of the application ID (`namespace` in `app/build.gradle.kts`).
- **Intent actions and extras** in the automation contract (`com.bos.sphere.action.*`, `com.bos.sphere.extra.*`) are plain strings, not tied to the ID. Changing the ID does not break them, but it is the right moment to decide whether they should be renamed too (a breaking change for anyone who has automation built on them).
- **Manifest.** No `android:authorities`, no custom permission names and no `<queries>` entries depend on the ID. `taskAffinity="com.bos.sphere.desktop"` is only a task name.
- **Backup and state.** The state file does not contain the ID. Device-bound values (widget ids, the private background path, which holds `/data/user/0/<id>/files/...`) are never imported (tested), so a setup exported from one ID imports into another.
- **Benchmarks.** `benchmark/src/main/kotlin` targets `com.bos.sphere` by literal in three files and must follow whichever ID is chosen.

## Scaffolding added (non-breaking)

- `app/build.gradle.kts` reads `-Pliftoff.applicationId=<id>`; with no property the ID is exactly `com.bos.sphere`. This lets a trial build with the candidate ID be installed beside the current one to check that nothing depends on the literal. Test: `CFG_20_defaultApplicationIdIsUnchangedAndOverridableByProperty`.
- Guard tests (`CFG_20_*` in `core-data`) fail if a literal `ComponentName("com.bos...")` or a literal package is introduced in main code, so the audit above stays true.

## Product flavours (`core` / `elevated`): not added, on purpose

Section 5 names two flavours. Adding `productFlavors` now would change task names (`assembleRelease` would build every flavour), move the APK to `outputs/apk/<flavour>/release` (which breaks `checkReleaseApkSize`, NFR-08, and the benchmark module's expectations), and change the release signing path. Whether the elevated flavour exists at all for the first public release is itself an open decision in section 16. So flavours are left as an option below, not scaffolded.

## Options

| Option | Result | Cost |
| --- | --- | --- |
| A. Keep `com.bos.sphere` | No migration, existing installs keep updating | The ID says "bos", not Liftoff; permanent once published |
| B. Change to a Liftoff ID (for example `app.liftoff.launcher`) **before** the first public build | Clean identity | Existing alpha installs become a separate app: install the new one, set it as home, import a setup file (CFG-01/02 make this a minutes-long step). Benchmarks and any external automation need the new ID |
| C. Change later | Not recommended | Same as B but with real users stranded |
| D. Keep the ID for `core` and use `<id>.elevated` for an elevated flavour | Both can be installed side by side | Needs the flavour work above; only relevant if the elevated flavour ships |

## Recommendation

Decide before the closed beta. If the name Liftoff is staying, take B now: the only users affected are alpha testers, the state export already carries everything that is theirs, and the cost only grows. If the name is not settled (section 16 also lists a trademark check), take A and revisit before any store listing, accepting a one-time migration for alpha testers then.

## To close this item

1. Owner picks A, B, C or D.
2. If B or D: set `applicationId` (the default in `app/build.gradle.kts`), update the three benchmark files, decide on the automation action strings, and (for D) add the flavour dimension together with the `checkReleaseApkSize` path change.
3. Install over the previous build on a device to confirm what the user sees (not possible without a device here).

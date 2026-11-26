# Liftoff Studio

A static page that edits a Liftoff configuration in the browser, shows the home as you change it, and reads and writes the same JSON file the app's Backup uses.
Plain HTML, CSS and JavaScript: no build step, no framework, no network request (it loads nothing from outside its own folder, and it sends nothing anywhere).

Left: a live drawing of the phone (Glance, Pages, Radar, the drawer disc, Comms, Recents), portrait or landscape. It follows the setting you touch; the Glance
clock can be dragged. Right: the six sections as text tabs HOME, LOOK, APPS, RADAR, SIDE PANEL, SYSTEM, with the app's own sections in the app's order (APPS starts
with the phone's Profiles). Header: profiles (New, Duplicate, Rename, Delete, Use, quick looks, starter profiles Work, Calm and Desk),
Import, Export, Share link.

## Publish it at `<site>/liftoff/studio/`

Every URL inside the page is relative, so it works from any sub-path.

1. Copy the contents of this folder, without `tests/` and this README, to `liftoff/studio/` in the site, so that `liftoff/studio/index.html` exists:

   ```
   rsync -a --exclude tests --exclude README.md web/liftoff-studio/ <site-checkout>/liftoff/studio/
   ```

   The folder holds: `index.html`, `studio.css`, `app.js`, `core.js`, `preview.js`, `cards.js`, `schema.js`, `schema.json`, `fonts/` (with `fonts/licenses/`).
2. Serve it as static files. Fonts are `.ttf`: the server should send `font/ttf` (any common server does). No rewrite rules are needed; `/liftoff/studio` should
   redirect to `/liftoff/studio/` (with the trailing slash) so the relative URLs resolve, which most hosts do by themselves.
3. Open `https://<site>/liftoff/studio/` and check the preview draws and the tabs switch. The page also works from `file://`, which is how to try it locally.

GitHub Pages instead: `.github/workflows/liftoff-studio-pages.yml` stages the folder under `_site/liftoff/studio/` and deploys it. It is manual only
(`workflow_dispatch`) and does nothing until Pages is enabled for the repository. It publishes at the root of whatever Pages site the repository has.

## The file it writes

| Button | File | Carries |
| --- | --- | --- |
| Export, Settings only | `liftoff-<name>-settings.json`, format `liftoff-settings` | the same document as the app's "Export settings only" |
| Export, Settings with dock, corners and gestures | `liftoff-<name>-backup.json`, format `liftoff-state` | the settings, `dock.settings`, the four corner and the gesture actions, and the pref files `comms_scale`, `launcher_haptics`, `puck_actions` |

In the app use Backup, then Import. A Backup file of this kind has only those sections, so importing it changes only those parts; layout, groups and workspaces on the
phone are left alone. If a full Backup is imported into Studio and exported again, everything Studio does not edit travels through unchanged. Fields Studio does not know
are kept as they were. Device-bound settings (`onboardingDone`, `leftWidgetId`, the wallpaper picture files) are never written.

Importing checks types against the schema: a wrong type (text where a switch is expected) is refused with a message naming the setting; a number out of range is clamped
with a warning (the app does the same); an option name Studio does not know is kept with a warning. Share links hold only the values that differ from the defaults, in the
URL fragment (`#p=...`, at most 6000 characters), so they never reach a server.

## Where the fields come from

`schema.json` (and `schema.js`, the same document as a script so the page also works from `file://`) is **generated from the app's code**, not written by hand:
keys, defaults, option ids and ranges come from `LauncherPrefs`, `SettingsCodec`, the enums, `StateSchema` and the dock source. The tab and section of each field are
the app's: each field names the app Settings row that edits it, and the generator reads its tab and section from `app/.../ui/SettingsTabLayout.kt` (wave 58;
the app test `StudioTabsW58Test` checks them against the real rows). Only labels and the row of each field are editorial. The phone's profile starters and the
app group suggester's tables are generated too (`profileStarts`, `appSuggest`).
The JVM test `core-data/.../StudioSchemaW56Test.kt` fails when the export carries a key the schema lacks, when a schema key is not a real key, when a default or an option
id changed, or when the two files are out of date. To regenerate after a settings change:

```
STUDIO_REGEN=1 ./gradlew :core-data:testDebugUnitTest --tests '*StudioSchemaW56Test*' --offline --max-workers=2
node web/liftoff-studio/tests/make-fixtures.js
node --test web/liftoff-studio/tests/studio.test.js
```

(The same test also refreshes `tests/sample-settings.json` and `tests/sample-backup.json`, which are written by the app's own codec, and checks that the app's codec reads the two files in `tests/fixtures/` that Studio writes.)

## Tests

`node --test web/liftoff-studio/tests/studio.test.js` (Node 18 or newer, no dependencies): schema, import and export round trips, unknown fields, wrong types, share links,
profiles, and that every scene of the preview draws for every look and every option.

## Phone profiles (wave 57)

On the phone, Workspaces became Profiles, and a profile can carry groups of settings. In a Backup file each `workspaces[]` entry may have `carries`
(group ids) and `settings` (schema key to the value exactly as the Backup holds it: the prefs value, the `action:X` gesture string, the `dock.settings`
value with `dock.enabled` as `true` / `false` / `null`, the haptic id); `dock.globalSettings` holds the global values the active profile has set aside.
The groups and their keys are generated into `schema.json` as `profileGroups`. `core.js` reads them with `phoneProfiles(ix, doc)` (Studio values, a
warning for a key it does not know, which is kept) and writes them with `setPhoneProfile(ix, doc, id, values)` (only the keys the profile carries;
everything else in the Backup is carried through). Wave 58: the APPS tab's Profiles section edits them like the phone's Profiles page (New from Current setup,
Empty, Work, Calm, Desk; Rename, Duplicate, Delete, Switch; the groups that ride along; suggested app groups from the apps the file names). The list is saved in
the Backup and exported with it; a list started in Studio replaces the phone's list when imported. Studio's own browser profiles (the header) are separate drafts.
See `docs/decisions/profiles.md`.

## What the preview does not cover

A drawing from the settings, not the app. Signal wallpapers are still approximations of the app's painters in the theme colours; System live and Picture wallpapers
are placeholders; text, contacts and apps are samples; landscape covers the Glance and Pages only; dock magnification is a still sample under a sample pointer; Word
clock text is fixed to one time; the Glance spacing and the adaptive grid follow the app's rules approximately; motion, haptics and sound are not shown. Nothing was
compared with a device.

## Licences

Fonts in `fonts/` are the ones the app ships, under the SIL Open Font License 1.1; the texts are in `fonts/licenses/`.

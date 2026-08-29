# Sphere Launcher — Play Console answer sheet

> Only the **deltas** from `Personal-Tracker/store/HOUSE_DEFAULTS.md`.

| | |
|---|---|
| applicationId | `com.bos.sphere` |
| Version at time of writing | `0.1.0-m1` (versionCode `1`) |
| Category | **Personalization** |
| Tags | launcher, home screen, landscape, coverflow, customisation |
| Contact email | `sphere@asystemofcells.com` |
| Website | `https://asystemofcells.com/sphere` |
| Privacy policy | `https://asystemofcells.com/sphere/privacy` |

> The product name is owner-gated (`NAMES.md`: "Sphere Launcher" in docs, "BOS" in
> the repo). This sheet uses **Sphere Launcher**, which is what the README calls it.

## Deltas from the house defaults

### `QUERY_ALL_PACKAGES` — the one declaration, and it is an easy one
Play requires a declaration form. **A launcher is a named eligible use**, so this is
about writing two clear sentences, not about winning an argument:

> This app is a home-screen replacement. It declares the HOME and DEFAULT intent
> filters and requests `RoleManager.ROLE_HOME`. Enumerating every launchable
> application on the device is the irreducible core function of a launcher: it must
> show the user all of their apps in order to launch them.

Also true and worth adding: the app uses `LauncherApps` (the purpose-built system
API) with a `LauncherApps.Callback`, and is work-profile aware.

### `RECORD_AUDIO` — resolve before submitting
The manifest declares it, but nothing in the current milestone obviously uses it.
A launcher requesting microphone access is exactly the combination that draws a
manual review and unnerves users reading the permission list.

- If it is there for a planned assistant feature that has not shipped: **remove it
  now** and add it back with the feature.
- If something does use it: the listing and the privacy policy must both say what,
  and it needs an in-app rationale before the first request.

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" tools:node="remove" />
```

### `BIND_NOTIFICATION_LISTENER_SERVICE` — not yet
The hub currently shows **demo data**; the real notification feed lands at M3. A
notification listener is high-scrutiny, and Play will ask what it is for.

**Do not ship it while the feed is fake.** Remove the service until M3, then declare
it with the hub as the justification. Shipping a notification listener that does
nothing is the worst of both worlds: full scrutiny, no feature.

### Data safety
**No data collected. No data shared.**

| Question | Answer |
|---|---|
| Collect or share any user data? | **No** |
| Encrypted in transit? | Yes (nothing is transmitted) |
| Deletion? | Users can delete data in the app |

The app has **no `INTERNET` permission**, which is the strongest possible support
for that answer. Keep it that way for as long as you can.

> When the real notification feed arrives at M3, "App activity" and possibly
> "Messages" enter the conversation. It will still be **not collected** as long as
> nothing is transmitted, but the privacy policy must then say explicitly that
> notification content is read on device and never leaves it.

### Content rating
- Category `Utility, Productivity, Communication, or Other`. Expected **Everyone**.

### Screenshots must be landscape
This is a landscape-first launcher. Play accepts 16:9 landscape phone screenshots.
Shooting it in portrait would misrepresent the product and waste its best frame.

## F-Droid
- ⛔ **Blocked: no `LICENSE` file** (`CONSTELLATION.md` §2, D-I). A launcher with no
  network permission is close to an ideal F-Droid app, so this is worth five minutes.
- No anti-features once licensed.

## Pre-submit checklist

- [ ] Remove `RECORD_AUDIO` unless a shipped feature uses it.
- [ ] Remove the notification listener until the M3 feed is real.
- [ ] Write the `QUERY_ALL_PACKAGES` declaration using the wording above.
- [ ] Add a `LICENSE` file.
- [ ] Landscape screenshots: the coverflow equator, the all-apps globe mid-rotation,
      type-to-search with results, the pull-down hub.
- [ ] Confirm the set-as-default flow works from a cold install. It is the first
      thing every reviewer and every user does.

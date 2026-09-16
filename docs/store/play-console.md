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

### `RECORD_AUDIO` — a real feature, and it is NOT on-device
Voice search is implemented: `core-data/.../VoiceInputManager.kt` wraps
`SpeechRecognizer` behind a Flow API, and `app/.../ui/VoiceAssistant.kt` drives it.
Declare it, show an in-app rationale before the first request, and keep it.

⚠️ **The important part for Data safety.** `VoiceInputManager` builds a plain
`RecognizerIntent.ACTION_RECOGNIZE_SPEECH` with `LANGUAGE_MODEL_FREE_FORM`. It sets
**no** `EXTRA_PREFER_OFFLINE` and does **not** use
`SpeechRecognizer.createOnDeviceSpeechRecognizer`. On most devices that means the
system recognizer sends your audio to **Google's speech service** for recognition.

The app needs no `INTERNET` permission for this, because the transmission happens in
the system recognizer's process, not ours. **That does not make it private**, and
"we have no INTERNET permission so nothing leaves the device" is not a true
statement about this app while voice search works this way.

Two honest options, and this is a product decision:
1. **Keep cloud recognition** — then Data safety must disclose it (see below), and
   the listing should not imply voice is local.
2. **Force on-device** — `putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)`, or
   `createOnDeviceSpeechRecognizer` on API 31+, with a graceful failure when no
   on-device model is installed. Then the app can honestly claim local voice.

### `BIND_NOTIFICATION_LISTENER_SERVICE` — a real feature too
`SphereNotificationListener : NotificationListenerService()` is implemented in
`core-data/.../NotificationManager.kt` and registered in the manifest. It parses
notification title, text, source package and actions (`parseNotification`,
`categorizeSource`, `parseActions`) to build the hub feed.

Declare it, with the pull-down hub as the justification. Play scrutinises
notification access hard and may ask for a demo video showing the feature it powers.
Notification content is read and rendered **on device** and is not transmitted, which
is the answer to give — but it must still be disclosed as *used*, per below.

### Data safety
**Not the simple "no data collected" this sheet originally claimed.** Play's form
distinguishes data that is *collected* (transmitted off the device) from data that is
*accessed and used* on it — and this app does both, through two implemented features.

| Data type | Answer |
|---|---|
| **Audio → Voice or sound recordings** | **Collected: YES**, while voice search uses the default cloud recognizer (option 1 above). Purpose: App functionality. Shared: to the system speech provider. Optional: yes, only while the user holds the voice control. **If you take option 2 (on-device), this becomes "used but not collected" instead.** |
| **App activity / Messages** (notification content) | **Used, not collected.** Read on device to build the hub feed; never transmitted. Disclose as used. |
| Everything else | Not collected, not used. |

| Question | Answer |
|---|---|
| Encrypted in transit? | Yes, for the recognizer request (handled by the system provider) |
| Deletion? | Users can delete data in the app (uninstall removes everything the app stored) |

> **Why the app having no `INTERNET` permission does not settle this.** The speech
> recognition transmission happens in the system recognizer's process. The permission
> list is not evidence that nothing leaves the device, and a Data safety answer that
> disagrees with observable behaviour is the kind of error that gets an app removed
> rather than rejected. Resolve the on-device question above first; the answers here
> follow from it.

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

- [ ] Decide voice recognition: cloud (disclose it) or on-device (`EXTRA_PREFER_OFFLINE`).
- [ ] Write the notification-access declaration; be ready to record a demo video.
- [ ] Make the Data safety answers match whichever voice option you chose.
- [ ] Write the `QUERY_ALL_PACKAGES` declaration using the wording above.
- [ ] Add a `LICENSE` file.
- [ ] Landscape screenshots: the coverflow equator, the all-apps globe mid-rotation,
      type-to-search with results, the pull-down hub.
- [ ] Confirm the set-as-default flow works from a cold install. It is the first
      thing every reviewer and every user does.

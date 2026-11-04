# Pages (`:feature-pages`)

The launcher is an ordered, scrollable set of **pages**. Page 0 is the coverflow home; every other page
is one full-page App Widget, an HTML widget or page run by the **Orrery widget runtime**, or a
**website**. The owner's words (all 2026-11-04):

1. "also add the Orrery widget runtime to the launcher"
2. "We need to be able to add full-page widgets also. Orrery would be one such widget- it occupies the whole page."
3. "Another thing I want is the ability to set a page to be a website (url) as well. That way anything can become part of a scrollable set of pages."
4. "Let the user choose between all three options: 2 finger swipe + dots, swipe past the coverflow's ends, vertical pages (swipe up)"

> **Status (2026-11-04):** compiled and JVM-tested only. This build environment has no device and no
> emulator, so **nothing below about touch, rendering, WebView behaviour, imports or the network has
> been seen running.** Every gesture, every WebView setting and every runtime behaviour is in the
> owner-verify list at the end.

## 1. The page model

| Kind | What fills the page | Stored settings |
|---|---|---|
| `home` | the coverflow, its hub, globe and chrome | none (always page 0, never stored, never removable) |
| `appwidget` | one Android App Widget | `slot`: the link to its widget placement |
| `html` | a D-AC widget package, or a single-file page such as Orrery's | `package` (true / false), `network` (pages only), `rev` |
| `web` | a website | `url` (validated http / https) |

Every page has an id (`p` + 16 random hex digits, never reused) and a title (blank = use the content's
own name: the widget's label, the package's `name`, the page's `<title>`, the website's host).

**Persistence.** SharedPreferences `pages`, key `list`, one JSON document, the same zero-dependency
way `HiddenAppsStore` and the widget placement store keep theirs, so the list survives process death
and reboot. The navigation mode is key `nav_mode`.

```
{"schema":1,"pages":[{"id":"p…","kind":"web","title":"News","url":"https://…/"},
                     {"id":"p…","kind":"html","package":false,"network":false,"rev":0},
                     {"id":"p…","kind":"appwidget","slot":1}]}
```

- **Schema-versioned.** `PageListCodec` migrates a document step by step (`migrations[n]`: schema n →
  n + 1) up to the current schema. Schema 1 is the first, so there is nothing to migrate from yet;
  the chain is exercised in the tests with a test-only step. A document without a `schema` is read as
  schema 1. A document from a **newer** version is read best-effort and its raw text is copied to
  `list_schema_<n>` before this version rewrites it; an **unreadable** document is moved to
  `list_unreadable`. Both copies are written with a synchronous commit, and a second such document
  never overwrites the first (it goes to `…_1`, `…_2`, …; an identical one isn't copied twice).
- **Forward-compatible entries.** An entry this version can't read (an unknown kind, a damaged field,
  an invalid id) is kept verbatim and written back unchanged, so a downgrade or one bad entry never
  erases a page. It is not shown. (Its position among the pages is not kept: it is written after them.)
- **App Widget pages** reuse the widget-hosting placement store and its reconciliation (see
  `docs/widget-hosting.md`, *Pages*): a page's widget is a placement on the new `Page` surface whose
  `slot` equals the page's `slot`. Because the link is the slot, not the widget id, it survives the id
  remapping of a backup restore. A placement whose slot no page references (a widget just bound for a
  new page, or a list lost while placements survived) gets a page at the end
  (`PageEdits.syncWidgetPages`). A page whose widget is gone (its app uninstalled or disabled, so the
  system deleted the widget) is **kept** and says "No widget on this page", with **Choose a widget**
  and **Remove page**, rather than vanishing.
- **Imported files** live in `noBackupFilesDir/pages/<page id>/` (`record.json` + `content/`). They are
  kept out of Auto Backup on purpose: a large package could push the app past the 25 MB backup quota,
  which would fail the whole backup (widget placements included). After a backup restore the page
  list comes back, and each html page says its files need importing again (**Import again**).
  An import is built in `<id>.tmp/` and swapped in: the old files move to `<id>.old/` first and come
  back if the swap fails, so they are never deleted before the new ones are in place; a swap cut short
  by process death is put back at the next start (`PageDirs`). Any I/O error during an import (a
  damaged zip entry, a document the provider can't open, a full disk) is reported as "couldn't be
  read" and the page stays as it was; nothing an import throws reaches the launcher.
- **Tidying at start.** Files of pages no longer listed (an interrupted removal) are removed, but
  only when the list was read fully: never while the stored list was unreadable or written by a newer
  version (or such a list was kept aside), and never for an id that an entry this version can't read
  names. Widget slots named by such entries also stay theirs (no duplicate page, no reused slot).
- **Backups.** Auto Backup and device transfer leave out the WebView profile (`app_webview/`,
  `res/xml/data_extraction_rules.xml`): website cookies and session tokens, sites' IndexedDB and Cache
  Storage, and HTML pages' storage. They never leave the phone in a backup and can't push it past its
  quota. **The cost:** an HTML page's saved state (Orrery's `localStorage`) is not in backups or device
  transfers either; the page's info says so. See *Decisions for the owner*.

**Page manager.** Settings → Pages → **Manage pages**, or a long-press on the page dots. It lists every
page in order (home first, marked "The coverflow. Always first."), with ▲ / ▼ (reorder), Rename,
Info (html and web), Reconfigure (App Widget pages whose provider allows it) and Remove; tapping a
page goes to it. **Add a page** offers:

- **App widget**: the existing widget picker (destination fixed to the new page), bind prompt and
  configure activity; the page appears once the widget is placed, and the launcher turns to it.
- **HTML widget or page**: the system file picker (`.zip` package or `.html` page).
- **HTML widget package (folder)**: the system folder picker.
- **Website**: an address, validated as you type.

## 2. Moving between pages: "Move between pages"

Settings → **Move between pages** offers the owner's three modes; the first is the default. In every
mode: the **page dots** are there whenever there is more than one page; the **home key** returns to
page 0 (the existing `onNewIntent` reset, which also closes the page dialogs); the **system back
gesture** on a website (and on HTML pages) goes back through the page's history first, then to home;
on an App Widget page back goes home. Turns use the `core-design` motion primitive *Settle*; with
Android's "Remove animations" on (animator duration scale 0) they snap instead. A drag settles one
page on if it covered 22 % of the page or was flung faster than 650 dp/s (the fling decides over the
distance); never more than one page per gesture; there is a small rubber band (0.12 page) past the
first and last page, and no wrap-around.

**How touches are shared.** The pager reads every touch in Compose's *Initial* pass, before the
coverflow, a WebView or a hosted App Widget sees it, and only **claims** a gesture once it is clearly a
page gesture. Until then the content receives the touch untouched. A claimed gesture is consumed, so
the content receives `ACTION_CANCEL`, and the coverflow (`LauncherSurface`) is told through
`gestureClaimed` and does nothing more with it: a scrub or hub pull this gesture had started settles,
even when a second finger had already landed (the surface switches to pinch handling then). On home,
a drag that follows a long-press which opened something (an app's menu, the widget picker) is never
a page gesture (`LauncherSurfaceState.longPressOpened`). With only home there are no page gestures at
all, so every existing gesture behaves exactly as before.

**Release.** Android lifts two fingers one event apart (POINTER_UP, then UP). Only events with every
claimed finger down move the page and feed the velocity (pointers are followed by id), so the lift of
the first finger neither snaps the page back nor reads as a fling the other way; if the fingers
rested before lifting, a stationary sample at the lift keeps that from counting as a fling
(`DragFeed`). A turn (gesture, dot, home key, a page just added) is remembered by its target page,
and nothing reacting to a list change cancels it.

| Mode | On home | On full pages |
|---|---|---|
| **1. Two-finger swipe + dots** (default) | two-finger sideways swipe (starting on the equator surface, hub and globe closed) | two-finger sideways swipe over any content |
| **2. Swipe past the coverflow's ends** | from the last item, pull past it beyond a resistance threshold | a one-finger swipe inward from a 24 dp edge strip |
| **3. Vertical pages (swipe up)** | one-finger swipe up (hub and globe closed); pull down still opens the hub | two-finger vertical swipe |

The dots work in all three.

### Mode 1: two-finger swipe

A two-finger gesture is a **page turn** when both pointers travel the same way along the axis, each
past touch slop, the pair's centre moves more along the axis than across it, and the distance
between them stays roughly constant: it may change by at most 18 % (or touch slop, if that is larger).
A larger change first is a **pinch or spread**, which keeps its meaning (the coverflow's globe and
settings, a website's zoom). The coverflow's own pinch fires at a ratio of 0.78 / 1.28, so the 18 %
test always decides first. Measurements start when the second finger lands; a third finger, or a
finger lifting before the claim, declines; a two-finger scroll across the axis (vertical in this
mode) is the content's. Fingers moving left turn to the next page. One-finger input is never touched.

### Mode 2: past the ends

- **Home.** The coverflow reports how far the finger pulled past its first or last column
  (`LauncherSurfaceState.scrubPastEnd`, in columns, before its rubber-band clamp at 0.6), but **only
  for a scrub that began at that end** (within half a column of it; `ScrubPastEnd`). The coverflow has
  no fling, so finger travel alone would otherwise count, and a long, fast stroke from the middle of
  the list could overshoot the end by more than the threshold. Turning the page is therefore a
  second, deliberate pull from the last item: released at **1.4 columns or more** past it (about
  160 dp of finger travel at the default spacing, about 90 dp past where the rubber band stops), the
  next page opens. From 0.7 columns a hint names the page ("Keep pulling for Orrery", then "Release to
  open Orrery", with a violet outline and a › mark).
  **Past the first item** there is no page: home is page 0 and pages don't wrap, so nothing happens
  (see *Decisions for the owner*).
- **Full pages.** A 24 dp strip just inside each system back-gesture zone (read from
  `WindowInsets.systemGestures`, so the system's back swipe from the very edge is untouched; with
  three-button navigation the insets are 0 and the strips start at the edges). A one-finger swipe
  that starts in the strip and moves **inward** past touch slop turns the page (left strip → previous,
  right strip → next). Taps, vertical drags and outward drags in the strip are declined and reach
  the content.

### Mode 3: vertical pages

Pages stack below home. Exactly which vertical drags mean what, on home:

- With the **hub and globe closed**, a one-finger drag that **starts on the equator surface** and
  **above the bottom system-gesture inset** is decided where the surface decides it (the first move
  past touch slop, same tie-break): mostly sideways → the surface's scrub; mostly vertical and **up** →
  the next page; mostly vertical and **down** → the hub pull, as before.
- With the **hub open or opening** (or the globe in), every vertical drag stays the hub's: swiping up
  on an open hub closes it, as before.
- Touches on the portrait keyboard and its results are never page gestures (they are outside the
  equator surface).

On full pages vertical swipes belong to the content; a **two-finger vertical swipe** (the mode-1 test
on the vertical axis; up = next page) or the dots move between pages. A two-finger vertical swipe on
home works the same way.

### The page dots

A slim indicator in the launcher's chrome: a row at the bottom centre in every mode, clear of the
equator in both orientations (a column on the right edge, as first built for mode 3, sat on the
equator in landscape and took its touches). On home in portrait it sits above the keyboard and glides
down with the turn (no jump at the end of it). Each dot is a 48 dp target: tap = go to that page,
long-press = page manager (also a TalkBack long-click action). The current page is a larger filled
dot, the others are rings, so the current page never rests on colour; each dot's description is
"Page 2 of 4: Orrery, current page". Hidden while only home exists, and while the globe or hub
covers home (so an invisible dot can't take a tap). With many pages the indicator scrolls rather
than shrinking its targets. Full pages keep a 56 dp band at the bottom clear for it. When a turn
settles, TalkBack hears the page ("Page 2 of 4: Orrery, current page"); going to a page closes the
settings panel, which belongs to home.

## 3. The Orrery widget runtime

The host side of D-AC (the widget contract), in the launcher. One WebView per live HTML page or
widget.

### Serving

- Content comes from app-private storage through `WebViewClient.shouldInterceptRequest`, at an **https
  origin of its own per page**: `https://<page id>.invalid`. Each page or widget therefore has
  separate storage, and none can read another's; each is also its **own site** (registrable domain),
  so no page can set a cookie for a shared parent domain that the others read (a `Domain=invalid`
  cookie is refused: `invalid` is the registry itself). `.invalid` (RFC 2606) can never resolve, so a
  request that slipped past the interceptor could not reach any real host. Never `file://`; file and content
  access are off. The launcher's own origins never get a service worker (their script requests are
  refused by a process-wide `ServiceWorkerClient`), so nothing can fetch on a page's behalf behind
  the interceptor.
- A **package** serves `widget.html` and `assets/…`; a **page** serves its one file at `/`. Any other path
  on the own origin is 404; another page's origin is always refused. A package's `assets/` files are
  served with a CSP ending in `sandbox`: if one is an HTML or SVG document that the widget navigates to
  or frames, it gets no script and an opaque origin (subresources ignore `sandbox`).
- **The runtime's head block** (CSP `<meta>`, `x-dns-prefetch-control: off`, the WebRTC and storage
  removal, the host API) is put before anything of the document's own: after a BOM, leading whitespace
  and comments and the doctype, never by searching for `<head` (a match inside a comment, or a page
  script before its `<head>`, would otherwise run first or hide the block). The parser opens the
  implicit `<head>` for it; the page's own `<html>` attributes still apply.
- The platform's `shouldInterceptRequest`, `createWebMessageChannel` / `postWebMessage` and
  `WebSettings` cover everything the runtime needs, so `androidx.webkit` was not added.

### Imports (only through the system file picker)

- **A D-AC widget package**: `widget.json` + `widget.html` (+ `assets/`), as a `.zip` or a folder.
  `widget.json` and `widget.html` may sit at the top or inside one top-level folder (as zips of a folder
  are). Anything else is ignored (`__MACOSX/`, `.DS_Store`, a README). Any path that could escape the
  package (`..`, absolute, a drive letter) refuses the whole package (zip-slip). Limits: 512 files,
  16 MB per file, 24 MB in all, 32 MB zip. `widget.json` is validated against D-AC / D-BD:
  - `id` (letters, digits, `.`, `_`, `-`; ≤ 64), `name` (non-empty), `version` (whole number ≥ 1) and
    `sizes` are required; `sizes` must include **`fill`** (this launcher shows HTML widgets only as
    full pages) and otherwise hold grid sizes like `2x2`;
  - `reads` and `writes` are lists of channel names like `news.items` (may be left out = none);
    `writes` may not name a `launcher.*` channel (only the launcher publishes those);
  - `opens` is a route template or a list of them (may be left out = nothing can be opened); a
    template that isn't an `app:` or `page:` route (such as the spec's own `nooz://read/{id}`) is
    accepted with a note that it can't be opened here;
  - `network` must be `false` (or left out): a package that asks for `true` is refused, and `false`
    is enforced, never trusted.
- **A single-file page** (`*.html`), such as Orrery's `dist/personal-tracker.html`, is wrapped in a
  generated manifest (a page, size `fill`, no channels, no routes). Its name is its `<title>`, so
  Orrery's build (`<title>Orrery</title>`) appears as **Orrery**; without a title, the file name. Orrery's
  build writes the page without `<!doctype>` / `<html>`; such a **fragment** is served inside the same
  skeleton Orrery's own `build.py` wraps its test copy in ("the skeleton the publisher adds": doctype,
  charset, viewport, base style), so it renders here as it is tested there. The importer also notes
  the hosts the file mentions in http(s) addresses (XML namespace names excluded).
- **Re-importing** (page info → Re-import) builds the new files beside the old ones and swaps them in,
  so a failed import leaves the page as it was; the page keeps its place, its id and therefore its
  origin and storage (Orrery keeps its state), and its view reloads.
- **Removing** a page deletes its files and clears what it stored. `WebStorage.deleteOrigin` alone
  doesn't clear `localStorage` (where Orrery keeps `T.store`), so once the page's own view is gone a
  throwaway WebView loads a wipe document on the page's origin (served by the interceptor, nothing
  else) that clears `localStorage`, `sessionStorage`, every IndexedDB database, Cache Storage and the
  cookies it can see; then `deleteOrigin` runs and its cookies are expired for `/` and `/assets`,
  host-only and for its own domain (`OriginWipe`, `wipeOrigin`). It gives up after 10 s. A cookie set
  with another path can't be listed and may stay on the device, but no page can read it: the origin's
  id is random and never reused. If no WebView can be created at that moment, only `deleteOrigin`
  and the cookie expiries run.

### Sandbox

- JavaScript on. Every permission request is denied (`onPermissionRequest` → deny; geolocation
  off and its prompt answered no): no location, camera, microphone or notifications.
- No popups or new windows inside the launcher. **Single-file pages:** `target=_blank` and
  `window.open` (after a tap) reach `onCreateWindow`, whose address is caught by a throwaway WebView
  (JavaScript off, network blocked, every request refused) and handed on: http(s) links leaving the
  page go to **the user's default browser by an explicit intent** (its package set; a chooser if there
  is no default); other schemes go through an explicit chooser. Without a user gesture, nothing opens.
  The catcher is destroyed once it has the address and after 3 s whatever happens (a `window.open('')`
  that never navigates isn't kept), and it survives the shared renderer's death. Main-frame
  navigation away from the page's own origin follows the same rule; `javascript:`, `file:`,
  `content:` and `data:` navigations are refused.
- **D-AC packages** are sandboxed like D-AC §3.3's iframe (`sandbox="allow-scripts"` only): they never
  navigate away from their own origin and open no windows, gesture or not, so they can't carry data
  out through the browser or start another app. Their only way out is `orrery.open`.
- **What leaves for the system** (`IntentRules`): only VIEW intents; never `file:` or `content:` data
  (a `file://` intent would throw inside the launcher); never a URL over 8 K characters. An `intent:`
  URL loses its component, selector, flags (no `FLAG_GRANT_*` from a page) and clip data. Every start
  is guarded, so nothing a page hands over can crash the launcher.
- **JavaScript dialogs** (`alert`, `confirm`, `prompt`, "leave page?") show only for the page on
  screen (current, at rest, launcher in front), at most three per 30 s; the rest are answered
  "cancel" (a "leave page?" without a dialog lets the page go). A page on a neighbour, or one calling
  `alert()` in a loop, can't bury the home screen.
- Downloads (including Orrery's exports, which are `blob:` downloads) are refused with an honest
  toast ("… wasn't saved: pages in the launcher can't download files").
- `<input type=file>` gets no picker (not implemented; Orrery's own importers won't open one here).
- Page console output is not logged anywhere (it can carry the page's data).
- If the WebView renderer dies (crash or memory reclaim), the page says "This page stopped" with
  Reload; `onRenderProcessGone` returns true, so the launcher itself never dies with a page. If the
  system WebView can't be created (being updated), the page says so.

### Network

- **D-AC packages never reach the network.** The interceptor answers every request outside the
  package's own origin with an empty 403, and the document is served with D-AC §3.3's CSP (as a
  response header and a `<meta>`), which also covers what the interceptor can't see (WebSocket
  connections fall under `connect-src`, which falls back to `'none'`). **Departure from
  D-AC §3.3:** `'self'` is added to `img-src`, `style-src`, `font-src` and `media-src`, so the
  package's own `assets/` (which D-AC §3.1 allows) can load; that is the package's own origin, served
  from storage, still no network. `script-src` stays inline-only.
- **Single-file pages start with no network.** Each has a per-page switch **"Allow this page to reach
  the network"**, off by default (page info). Off: the same interceptor refusal plus an offline CSP
  (own origin, inline, `data:` and `blob:` only). On: the page may reach any http(s) host it asks for.
- Both: WebRTC (`RTCPeerConnection` and friends) is removed from the page's window before its own
  scripts run, because it could open connections neither the interceptor nor the CSP sees. DNS
  prefetching is off (`X-DNS-Prefetch-Control` header and `<meta>`).
  **Residual risk:** a page that deliberately reaches through a same-origin child frame (an
  `about:blank` iframe) could still find WebRTC, and so could a network-off single-file page through
  the blank window it opens after a tap (`window.open('')`) during the catcher's 3 s. Packages open no
  windows at all.
- **Watched object.** A page's info names the hosts its file mentions, the hosts it **reached** this
  session (network on) and the hosts it was **refused** this session (in memory only; nothing is
  persisted or sent). While the current page has its network on, a "Network on" tag with an eye shape
  sits beside the page dots and opens the page's info. WebSocket connections of a network-on page
  are not seen by the interceptor, so they are not in the "reached" list.
- With the switch off, **Orrery's own connectors (weather, models) and its Google Fonts don't load,
  so system fonts are used; its saved state stays** — the page's info says exactly this for Orrery's
  page.
- **Storage.** Single-file pages keep `localStorage` / DOM storage, at their own origin, so Orrery
  keeps its state (`T.store`). D-AC packages get **no storage** (D-AC §3.3; they receive everything
  over channels): DOM storage is off in their WebView, and IndexedDB, Cache Storage and
  `document.cookie` are removed from their window before their scripts run (the same child-frame
  residual as WebRTC applies).

### Host API (D-AC §3.4) for packages

The runtime puts a small script at the top of `widget.html` defining `window.orrery`:

```js
orrery.ready(({ theme, size, locale, reduceMotion }) => { … });  // once, with the tokens
orrery.on('news.items', env => { … });                            // declared reads only
orrery.publish('news.read', { id }, ttlSeconds);                  // declared writes only
orrery.open('app:com.example.nooz');                              // declared opens only
```

- **Transport:** after the page loads, the launcher creates a MessageChannel and posts one port to the
  widget's **own origin only** (`postWebMessage` with that origin as the target); the shim queues calls
  made before then. Messages are JSON strings: `{"op":"on"|"publish"|"open", …}` in, and
  `{"op":"ready"|"message"|"refused", …}` out.
- **Enforcement (host side, `HostProtocol`):** `on` only for channels in `reads`; `publish` only for
  channels in `writes`, never a `launcher.*` channel, at most 64 K characters; `open` only for a route
  that matches a declared `opens` template (`{name}` matches one run of characters other than `/`, `:`,
  `{`, `}`) **and** is `app:<package>` (launched through `LauncherApps`) or `page:<id>` (`page:home` is
  page 0). Anything else is refused: the widget receives an `orrery-refused` DOM event; nothing is
  logged anywhere. **Declared is not enough:** an `open` is honoured only right after a tap on that
  widget's page (within 5 s of a finger lifting on it; one open per tap) while the page is the current
  one, at rest, with the launcher in front. A widget on a neighbour page, or calling `open` on a timer,
  is refused, so it can't pull the user off the home screen.
- **Rate limit:** each widget's port takes 30 messages a second (bursts of 60); more are dropped before
  they are parsed, and a widget that keeps flooding has its port closed until its page reloads.
- **Envelope:** every channel message is D-AC's `{channel, at, from, ttl, data}`; `at` is ISO-8601 with
  the zone's offset to the second; `from` is the widget's `id` (or `launcher`).
- **`ready`:** `theme` is the launcher's palette read from `core-design` as CSS custom properties
  (`--field`, `--surface`, `--ink`, `--ink-bright`, `--ink-dim`, `--violet`, `--violet-soft`,
  `--hairline`, `--native`, `--cloud`, `--font-sans`, `--font-mono`), also applied to `:root` before the
  callbacks run; `size` is `{name:"fill", width, height}` in dp; `locale` is a BCP 47 tag;
  `reduceMotion` is Android's "Remove animations".
- **Widget-to-widget:** what a widget publishes on a channel it declared goes to the other live
  widgets that declared it in `reads` and called `on` (an in-process bus; nothing leaves the device).

**Channels the launcher publishes** (nothing else publishes yet). Each source runs only while a live
widget listens; a new listener gets the current value at once.

| Channel | `data` | When | `ttl` |
|---|---|---|---|
| `launcher.clock` | `{iso, hour, minute, zone, is24Hour}` | every minute | 60 |
| `launcher.battery` | `{level (0–100), charging}` | on change | 300 |
| `launcher.notifications` | `{access, total, apps: [{package, app, count}]}` | on change | 60 |

Notification counts carry **counts and app names only, never content**: the notification listener
hands the runtime only the posting package and two flags (`ActiveNotification` in `core-data`).
Group summaries (their children are counted) and ongoing notifications (media, navigation, foreground
services: status, not something waiting) are not counted. `access` is false when notification access
isn't granted. Apps sort by count, then name.

**Orrery's page** doesn't speak the host API (it is a standalone app page) and needs none: single-file
pages get no host API script.

## 4. Website pages

- Only `http` and `https` addresses are accepted (`UrlRules`): an address without a scheme gets
  `https://`; `javascript:`, `file:`, `intent:`, `data:`, `ftp:` and every other scheme are refused;
  credentials in the address (`user:pass@host`, `bank.example@evil.example`) are refused; the host must
  be a real host name (IDN converted to ASCII), an IPv4 or a bracketed IPv6 address; ports 1–65535.
  An http address is accepted with a "not https" warning. For http pages to load at all, the app
  allows cleartext traffic (`android:usesCleartextTraffic="true"`; without it Android refuses every
  http page with `ERR_CLEARTEXT_NOT_PERMITTED`). The launcher itself still sends nothing; HTML pages
  are https origins and never load http subresources (mixed content is never allowed).
- The address field offers the address keyboard, no autocorrect, and Go adds the page.
- The page shows a header: ‹ Back (enabled when the page has history), the **host** with an
  **https / not-https mark** (a closed padlock and the word "https", or an open, struck padlock and
  "not https": shape and word, never colour alone), and ↻ Reload, each a 48 dp target; tapping the host
  opens the page's info.
- WebView: JavaScript and DOM storage on; **third-party cookies off**; mixed content never; file and
  content access off; every permission request denied; `target=_blank`, new windows and non-http(s)
  schemes (`tel:`, `mailto:`, `intent:`, …) handed to the system with an **explicit chooser** (`intent:`
  URLs stripped of any component or selector), and only after a user gesture (a redirect can't launch
  an app); downloads refused with an honest message; pinch zoom on.
- `android.permission.INTERNET` was added for website pages (and network-on single-file pages). The
  launcher itself still sends nothing of its own: no telemetry, no analytics.
- **WebView usage metrics: opted out** (`android.webkit.WebView.MetricsOptOut` = true).
- **Safe Browsing is OFF** — the owner's decision (2026-11-04: "turn off Safe Browsing"): the manifest
  sets `android.webkit.WebView.EnableSafeBrowsing` to `false` and every WebView the launcher creates
  calls `WebSettings.setSafeBrowsingEnabled(false)`, so no visited URL is checked with Google. **Its
  cost: there is no warning before a known-malicious site.** Each website page's info says so.
- Removing a website page doesn't clear the cookies and storage the site left in the launcher's
  shared web storage (another website page may use the same site); the Remove confirmation says so.

## 5. Memory and lifecycle

- At most the **current page and its two neighbours** stay composed, so at most three pages' WebViews
  are alive (home is always composed too, for a fast home-key return; it has no WebView). Every other
  page's WebView is destroyed (`AndroidView` release → `saveState` into memory, `destroy()`); coming
  back restores it from that saved state, or loads its address if there is none.
- WebViews that are composed but **off screen** (the neighbours) are paused (`onPause`) and give up
  focus and the keyboard, so typing can't go on into a page that slid away; the current one is
  resumed. When the launcher goes to the background, every live WebView is paused and WebView timers
  stop (`pauseTimers`); they resume when it comes back.
- Saved states are kept in memory only: they don't survive the launcher's process. Turning a page's
  network switch or re-importing its files recreates its view.
- Prepared HTML documents (the stored file plus the head block) are cached for the three most recently
  served pages only; import records are cached (misses too) so page titles never read the disk while
  a turn animates.

## 6. Code map

| Where | What | Tests |
|---|---|---|
| `feature-pages/…/PageModel.kt` | `Page`, `PageKind`, `PageList`, `PageIds`, `PageEdits`, `NavMode` | `PageListTest.kt` |
| `…/PageListCodec.kt` | schema-versioned JSON, migrations, forward compatibility | `PageListTest.kt` |
| `…/PageGestures.kt` | `TwoFingerSwipe`, `EdgeSwipe`, `HomeSwipeUp`, `PageTurn`, `PastEnd`, `MotionPolicy` | `PageGesturesTest.kt` |
| `…/UrlRules.kt` | website address rules, navigation routing | `UrlRulesTest.kt` |
| `…/WidgetManifest.kt` | D-AC manifest validation, page manifests, `HtmlScan`, `PackageLayout` | `WidgetManifestTest.kt` |
| `…/HostProtocol.kt` | host API protocol and enforcement, envelopes, routes, theme tokens, launcher channel data | `RuntimeTest.kt` |
| `…/ServePolicy.kt` | origins, serve / deny decisions, CSPs, `HtmlPrep` (skeleton, head block), shims | `RuntimeTest.kt` |
| `…/MiniJson.kt` | dependency-free JSON reader / writer | `WidgetManifestTest.kt` |
| `…/PageDirs.kt` | imports on disk: staging, the swap, errors, the start-up sweep | `PageDirsTest.kt` |
| `…/RuntimeLimits.kt` | `IntentRules`, `DialogBudget`, `RateGate`, `OpenGate` | `RuntimeLimitsTest.kt` |
| `…/PageStore.kt`, `…/PageFiles.kt` | SharedPreferences store (backups: `PageBackups`, tested); SAF imports and caches | (Android) |
| `…/runtime/` | WebView factories and clients, `HostBridge`, `ChannelBus`, `LauncherChannels`, `PagesRuntime` | (Android) |
| `…/ui/` | `PagesHost` (pager + gestures), page views, dots, past-end hint, settings rows, dialogs | (Android) |
| `feature-widgets` | `WidgetSurface.Page`, `PlacementEdits.putOnPage` / `onPage`, `WidgetSizing.pageSizes`, `PageWidget`, page picker | `PageSurfaceTest.kt` |
| `feature-sphere/LauncherSurface.kt` | `gestureClaimed`, `onScrubReleased`, `scrubPastEnd`, `longPressOpened`, `ScrubPastEnd` (defaults keep the old behaviour) | `ScrubPastEndTest.kt` |
| `core-data/NotificationManager.kt` | `ActiveNotification`, `NotificationProvider.active` (package + flags only) | — |
| `app` | `HomeScreen` glue, Settings "PAGES" section, manifest (`INTERNET`, metrics opt-out, Safe Browsing off, cleartext for http pages, backup rules) | — |

## 7. Decisions for the owner

1. **Past the first item on home (mode 2).** Home is page 0 and pages don't wrap, so pulling past the
   coverflow's *start* does nothing. Should it wrap to the last page?
2. **D-AC packages and storage.** They get no DOM storage (D-AC §3.3, "no storage"); the brief's "an
   origin of its own per widget so each widget has separate storage" is met by the origins. Confirm, or
   give packages storage.
3. **CSP for `assets/`.** D-AC §3.3's CSP (`default-src 'none'`) would block a package's own `assets/`;
   `'self'` was added for images, styles, fonts and media. Confirm, or drop `assets/` from D-AC.
4. **File pickers inside pages** (`<input type=file>`, e.g. Orrery's importers) are not supported. Add
   them (system picker, user-initiated)?
5. **WebRTC** is removed from each page's window, but a page could reach it through a same-origin child
   frame, and a network-off single-file page also through a blank window it opens after a tap (for up
   to 3 s). Acceptable for owner-imported pages, or worth a stronger block?
6. **Website data** stays in the shared WebView storage when a website page is removed.
7. **Notification counts** leave out ongoing notifications and group summaries.
8. **http websites.** Cleartext traffic is allowed app-wide so http addresses load (marked "not
   https"), as the brief's http acceptance needs. The alternative is to refuse http addresses and drop
   the not-https path.
9. **Backups leave out the WebView profile** (website logins; HTML pages' storage). Privacy and the
   25 MB quota say leave it out; the cost is that Orrery's saved state isn't in backups or device
   transfers, and Orrery's exports are downloads, which pages can't do. Keep, or back up page storage
   (and with it website cookies)?

## 8. Owner-verify (none of this has been seen running)

1. **Every gesture in every mode, on the phone.** Mode 1: two-finger sideways swipes on home and on
   each kind of page; pinch → globe and spread → settings on home still work and never turn the page.
   Mode 2: scrub into the rubber band at both ends without turning; pull past the last item to see the
   hint and release to turn; the edge strips on full pages, and that the system back swipe from the
   very edge still goes back. Mode 3: swipe up on home → next page; pull down → hub; swipe up on an
   open hub → closes it; scrub sideways; two-finger vertical swipes on full pages. The dots in all
   three (tap, long-press → manager, TalkBack).
2. **Two-finger swipes over real websites** (scrolling pages, maps, pinch zoom): the page turns on a
   parallel swipe, the site keeps its pinch zoom, and one-finger scrolling and taps are untouched.
3. **Back behaviour:** on a website back goes through its history, then home; on an App Widget page
   back goes home; the home key returns to page 0 from any page and closes the page dialogs.
4. **Orrery's page in the runtime:** import `dist/personal-tracker.html`; it appears as "Orrery";
   state is kept across launcher restarts and reboots (localStorage at its origin) and across a
   re-import; fonts fall back to system fonts with the network off and Archivo / JetBrains Mono load
   with it on; the **Dial** (the phone view) works by touch, including with two-finger swipes nearby;
   its exports show the refusal toast; its weather connector fails quietly with the network off and
   the info lists the refused hosts.
5. **WebView memory with several pages** (say five websites and Orrery): only the current page and its
   neighbours stay alive (`adb shell dumpsys meminfo com.bos.sphere`), pages restore with their
   history when you return, and nothing leaks after many turns.
6. **Rotation:** pages keep their state and place (the activity handles orientation changes itself),
   and an App Widget page reports its new size so a responsive widget changes layout.
7. **Runtime details:** the CSP header and `<meta>` are honoured on intercepted responses; the host-API
   handshake reaches a test package (`orrery.ready` fires with the tokens, `launcher.clock` arrives each
   minute, `launcher.battery` on change, `launcher.notifications` with notification access); a
   refused `on` / `publish` / `open` fires `orrery-refused`; `open('app:…')` launches the app and
   `open('page:…')` turns the page; a package can't fetch anything (try `fetch`, an `<img>` from the
   web, a WebSocket).
8. **Imports from the owner's file managers:** `.zip` packages (which MIME type the picker reports),
   folder packages, `.html` pages; a damaged package shows the validation messages.
9. **Websites:** links with `target=_blank`, `tel:` / `mailto:` links, a site that tries to download, a
   site asking for location or camera (always refused), an http site (it loads, with the "not https"
   mark), and that Safe Browsing is really off (no interstitial on a test page such as
   `testsafebrowsing.appspot.com`). An `intent:` link with `scheme=file` and a huge `mailto:` link do
   nothing and the launcher stays up. `alert()` on the current page shows (at most three in 30 s); on
   a neighbour page it shows nothing. Typing in a site's field and then turning the page hides the
   keyboard.
10. **Renderer loss:** "This page stopped" with Reload after the renderer is killed
    (`adb shell am kill` of the WebView sandboxed process), and the launcher stays up.
11. **App Widget pages:** bind prompt and configure for a page widget; the widget fills the page,
    takes taps when current, and is told its real size; removing its app shows "No widget on this
    page"; a backup restore brings it back on its page.
12. **Reduced motion:** with "Remove animations" on, page turns snap.
13. **Release and claims:** a fast two-finger swipe lands on the next page (never snaps back or turns
    backwards), also after the fingers rest before lifting; a two-finger swipe that starts with one
    finger scrubbing settles the coverflow on a column; a long-press that opened a menu or the picker,
    followed by a drag, never turns the page. In mode 2, a fast stroke from mid-list past the end does
    not turn; a second pull from the last item does.
14. **Dots and TalkBack:** the dots stay clear of the equator in landscape in every mode and glide with
    the turn in portrait; TalkBack announces the page after a turn; the network switch reads as a
    switch with its state; going to a page from the manager in Settings closes the settings panel.
15. **Removal and backups:** remove Orrery's page, re-add the same file: it starts empty (its
    `localStorage` was cleared); a cloud backup no longer contains `app_webview/` (`adb shell bmgr`);
    a package's `orrery.open` works right after a tap and is refused from a timer or from a neighbour
    page; a package's `location.href = 'https://…'` and `window.open` do nothing.

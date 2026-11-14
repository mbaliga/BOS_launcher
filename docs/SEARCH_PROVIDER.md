# Deep-search provider contract (proposed)

Status: **proposed**. As of writing neither Fylz nor Fotoz implements this. The launcher side
(`DeepSearch`) is written and ready; until those apps ship a provider, deep results are simply empty.

## Idea

An app that has its own index (file names and contents, OCR text, labels, people) exposes one
read-only ContentProvider. The launcher queries it as the user types and shows the rows in the
Files category, with a small source badge.

## Endpoint

Authority: `<applicationId>.search` (so `io.github.mbaliga.fylz.search`, `com.fotoxplorr.app.search`;
debug / connect suffixes follow the applicationId).

| URI | Meaning |
|---|---|
| `content://<pkg>.search/ping` | Optional. Any non-null cursor means "alive". |
| `content://<pkg>.search/query?q=<urlencoded>&limit=<n>` | Search. `limit` defaults to 12; honour it. |

Query only; insert/update/delete must throw `UnsupportedOperationException`.

## Columns

| Column | Required | Notes |
|---|---|---|
| `_id` | yes | Stable id (string or long). |
| `title` | yes | Display name. |
| `uri` | yes | `content://` or `file://` uri the launcher opens with `ACTION_VIEW` (grant read via `FLAG_GRANT_READ_URI_PERMISSION` handled by your own FileProvider/DocumentsProvider, or make it openable by others). |
| `subtitle` | no | e.g. folder name, date, size. |
| `snippet` | no | Matching passage or text recognised in an image. |
| `kind` | no | `image` `video` `audio` `document` `archive` `folder` `other` (default `other`). |
| `mime` | no | MIME type of `uri`. |
| `thumb` | no | Thumbnail uri. |
| `score` | no | 0..1, higher is better (default 0.5). |

Return at most `limit` rows, best first. Answer within roughly 300 ms; the launcher does not wait
beyond what the user is willing to see.

## Access

Keep it simple: the provider must be `android:exported="true"` and must **not** use a signature
permission. Either leave it open to any caller, or guard it with a normal-protection permission
such as `com.bos.sphere.permission.READ_SEARCH` (declared by the launcher; the provider app
declares `android:readPermission` using it). If the launcher gets a `SecurityException` it marks
that source unavailable and moves on. Returned rows should expose only what the user could already
see in your app.

The launcher manifest must list the providers/packages in `<queries>` so they are visible on
Android 11+.

## Minimal examples (Kotlin)

Fylz, indexing file names and content:

```kotlin
class FylzSearchProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor {
        val c = MatrixCursor(arrayOf("_id", "title", "subtitle", "snippet", "kind", "uri", "mime", "score"))
        if (uri.lastPathSegment != "query") return c
        val q = uri.getQueryParameter("q").orEmpty()
        val limit = uri.getQueryParameter("limit")?.toIntOrNull() ?: 12
        index.search(q, limit).forEach {   // your own index of names + extracted text
            c.addRow(arrayOf(it.id, it.name, it.folder, it.excerpt, it.kind, it.contentUri, it.mime, it.score))
        }
        return c
    }
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, v: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = throw UnsupportedOperationException()
}
```

Fotoz is the same shape, backed by its OCR text, labels and people tables; `kind` is `image` or
`video`, `snippet` is the recognised text or label ("receipt - Total 12.40", "Anna, beach"), and
`thumb` points at a thumbnail uri.

Manifest (both apps):

```xml
<provider
    android:name=".FylzSearchProvider"
    android:authorities="${applicationId}.search"
    android:exported="true" />
```


## Registering as a third-party provider (PAL-17)

Any app, not only Fylz and Fotoz, can offer results to Radar with the same contract:

1. Declare the read-only content provider with authority `<applicationId>.search`, `android:exported="true"` and
   `android:readPermission="com.bos.sphere.permission.READ_SEARCH"` (a normal permission the launcher declares).
2. Add an intent filter with the action `com.bos.sphere.action.SEARCH_PROVIDER` to that provider declaration so the
   launcher can find it.
3. The user sees the app under Ground Control, Radar, "Search providers from other apps" and switches it on. Radar asks
   nothing until then, and only after the user types.

A provider that is not exported, not guarded by that permission or whose authority does not follow the rule is ignored
(`ThirdPartyProviders.acceptable`, tested). Rows appear in Files with the source badge; a `SecurityException` marks the
source unavailable, as for Fylz and Fotoz. Not verified against a real third-party app on a device.

package com.bos.sphere.core.data

/** The groups the search screen sorts results into (the category wheel). */
enum class SearchCategory(val label: String) { Web("The web"), Actions("Actions"), Apps("Apps"), Contacts("Contacts"), Files("Files") }

/**
 * One non-app, non-contact result. A hit knows how to be opened but not how to be drawn: the UI
 * picks the glyph from [kind] and a thumbnail from [thumbUri] when there is one.
 *
 * Exactly one of [actionId] (an in-launcher action), [uri] (open with ACTION_VIEW), or [intentUri]
 * (a full `Intent.toUri(URI_INTENT_SCHEME)` string) is normally set.
 */
data class SearchHit(
    val id: String,
    val category: SearchCategory,
    val title: String,
    val subtitle: String? = null,
    /** A matching excerpt, e.g. text found inside an image or a document. */
    val snippet: String? = null,
    val kind: HitKind = HitKind.Other,
    val uri: String? = null,
    val mime: String? = null,
    val thumbUri: String? = null,
    val intentUri: String? = null,
    val actionId: CornerActionId? = null,
    /** Package that produced the hit (e.g. Fylz / Fotoz), for a small source badge and for opening in it. */
    val sourcePackage: String? = null,
    /** 0..1, higher is better; only used to order hits inside a category. */
    val score: Float = 0f,
    /** What the primary action copies to the clipboard (calculator answers), if anything. */
    val copyText: String? = null,
    /** Provider-specific data for the runner (shortcut id, launcher command ...). Empty for ordinary hits. */
    val extras: Map<String, String> = emptyMap(),
)

enum class HitKind { Action, Document, Image, Video, Audio, Archive, Folder, Web, Other }

/** Anything that can answer a query with hits. Implementations must be quick and never throw. */
fun interface SearchProvider {
    suspend fun search(query: String, limit: Int): List<SearchHit>
}

/**
 * Aggregates the non-app providers (actions, files, Fylz/Fotoz deep search, …). Emits a fresh map
 * as each provider answers, so fast ones show immediately and slow ones fill in. Contacts and
 * apps are handled by the existing [ContactSource] and app search, not here.
 */
interface SearchHub {
    fun search(query: String, prefs: LauncherPrefs): kotlinx.coroutines.flow.Flow<Map<SearchCategory, List<SearchHit>>>

    /** Whether file results can be listed right now (media/storage access granted). */
    fun hasFileAccess(): Boolean

    /** Package ids of installed apps that contribute deep results (Fylz, Fotoz), for a "Deep search: Fylz, Fotoz" hint. */
    fun deepSources(): List<String>
}

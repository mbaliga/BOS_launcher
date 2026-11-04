package com.bos.sphere.feature.pages

/** How a stored page list was read. */
enum class DecodeStatus {
    /** Nothing stored yet: home only. */
    Empty,

    /** Read as this version's schema (after any migrations). */
    Current,

    /** Written by a newer version: the entries this version understands were read; keep the raw text. */
    Newer,

    /** Not JSON, or not a page list at all: home only; keep the raw text, never overwrite it silently. */
    Unreadable,
}

data class DecodedPages(val list: PageList, val status: DecodeStatus, val schema: Int)

/**
 * The page list as one JSON document in SharedPreferences:
 *
 * ```
 * {"schema":1,"pages":[{"id":"p…","kind":"web","title":"…","url":"https://…"}, …]}
 * ```
 *
 * Home is implicit (always page 0) and not stored. Schema-versioned: a document is migrated step by
 * step ([migrations]: schema n → n + 1) up to [CURRENT_SCHEMA]; a document from a newer version is read
 * best-effort. Entries this version can't read (an unknown kind, a damaged field) are kept verbatim in
 * [PageList.unknown] and written back unchanged, so a downgrade or one bad entry never erases a page.
 * Schema 1 is the first; there is nothing to migrate from yet, and [migrations] is where the next
 * format's step goes.
 */
object PageListCodec {
    const val CURRENT_SCHEMA = 1

    /** Migration steps by source schema: `migrations[n]` turns a schema-n document into schema n + 1. */
    val migrations: Map<Int, (Map<String, Any?>) -> Map<String, Any?>> = emptyMap()

    fun encode(list: PageList): String {
        val pages = list.pages.filter { it.kind != PageKind.Home }.map { encodePage(it) } + list.unknown
        return MiniJson.write(linkedMapOf("schema" to CURRENT_SCHEMA, "pages" to pages))
    }

    fun decode(
        text: String?,
        steps: Map<Int, (Map<String, Any?>) -> Map<String, Any?>> = migrations,
    ): DecodedPages {
        if (text.isNullOrBlank()) return DecodedPages(PageList.HomeOnly, DecodeStatus.Empty, CURRENT_SCHEMA)
        var doc = MiniJson.parseOrNull(text).asObject()
            ?: return DecodedPages(PageList.HomeOnly, DecodeStatus.Unreadable, 0)
        if (doc["pages"] !is List<*>) return DecodedPages(PageList.HomeOnly, DecodeStatus.Unreadable, 0)
        val stored = (doc.long("schema") ?: CURRENT_SCHEMA.toLong()).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        var schema = stored
        while (schema < CURRENT_SCHEMA) {
            val step = steps[schema] ?: return DecodedPages(PageList.HomeOnly, DecodeStatus.Unreadable, stored)
            doc = runCatching { step(doc) }.getOrNull()
                ?: return DecodedPages(PageList.HomeOnly, DecodeStatus.Unreadable, stored)
            schema++
        }
        val pages = ArrayList<Page>()
        val unknown = ArrayList<Map<String, Any?>>()
        val seen = HashSet<String>()
        for (raw in doc["pages"] as List<*>) {
            val entry = raw.asObject() ?: continue
            val page = decodePage(entry)
            when {
                page == null -> unknown += entry
                seen.add(page.id) -> pages += page
                // A duplicate id: the first entry already holds it.
            }
        }
        val list = PageEdits.normalize(PageList(listOf(Page.Home) + pages, unknown))
        val status = if (stored > CURRENT_SCHEMA) DecodeStatus.Newer else DecodeStatus.Current
        return DecodedPages(list, status, stored)
    }

    private fun encodePage(p: Page): Map<String, Any?> {
        val m = linkedMapOf<String, Any?>("id" to p.id, "kind" to p.kind.wire)
        if (p.title.isNotEmpty()) m["title"] = p.title
        when (p.kind) {
            PageKind.AppWidget -> m["slot"] = p.widgetSlot
            PageKind.Web -> m["url"] = p.url
            PageKind.Html -> {
                m["package"] = p.isPackage
                m["network"] = p.network && !p.isPackage
                m["rev"] = p.rev
            }
            PageKind.Home -> Unit
        }
        return m
    }

    private fun decodePage(e: Map<String, Any?>): Page? {
        val id = e.str("id")?.takeIf { PageIds.isValid(it) } ?: return null
        val kind = PageKind.fromWire(e.str("kind")) ?: return null
        val title = when (val t = e["title"]) {
            null -> ""
            is String -> PageEdits.cleanTitle(t)
            else -> return null
        }
        return when (kind) {
            PageKind.Home -> null
            PageKind.AppWidget -> {
                val slot = e.long("slot")?.takeIf { it in 1..Int.MAX_VALUE.toLong() } ?: return null
                Page(id, kind, title, widgetSlot = slot.toInt())
            }
            PageKind.Web -> {
                val ok = UrlRules.check(e.str("url") ?: return null) as? UrlCheck.Ok ?: return null
                Page(id, kind, title, url = ok.url)
            }
            PageKind.Html -> {
                val isPackage = e.bool("package") ?: return null
                val network = (e.bool("network") ?: false) && !isPackage
                val rev = e.long("rev")?.toInt()?.coerceAtLeast(0) ?: 0
                Page(id, kind, title, isPackage = isPackage, network = network, rev = rev)
            }
        }
    }
}

/**
 * Where a stored list this version can't fully read is kept aside before anything rewrites it: [base]
 * (`list_unreadable`, `list_schema_<n>`) if free, then `base_1`, `base_2`, …; null when one of those
 * already holds exactly [raw] (nothing new to keep). So a second unreadable or newer document never
 * overwrites, or is overwritten by, the first. [stored] reads a key's current value. Pure.
 */
object PageBackups {
    fun keyFor(base: String, raw: String?, stored: (String) -> String?): String? {
        var key = base
        var n = 1
        while (true) {
            val existing = stored(key) ?: return key
            if (existing == raw) return null
            key = "${base}_${n++}"
        }
    }

    fun isBackupKey(key: String): Boolean = key.startsWith(UNREADABLE) || key.startsWith(SCHEMA_PREFIX)

    const val UNREADABLE = "list_unreadable"
    const val SCHEMA_PREFIX = "list_schema_"
}

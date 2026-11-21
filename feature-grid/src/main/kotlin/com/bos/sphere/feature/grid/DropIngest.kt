package com.bos.sphere.feature.grid

import com.bos.sphere.core.catalog.ItemDef
import com.bos.sphere.core.catalog.ItemKeys
import com.bos.sphere.core.catalog.ItemKind

/** One clip item of a drop, as the platform delivered it. [mime] is the clip's first declared type. */
data class DropClip(val text: String? = null, val uri: String? = null, val mime: String? = null, val label: String? = null)

/** What a drop turned into. */
data class DropResult(val items: List<ItemDef>, val appKeys: List<String>, val ignored: Int)

/**
 * INP-11: turns what other apps (or this launcher's own drawer) drop on the home into home items. Pure, so the rules
 * are tested without a device. A link becomes a link item; text that holds a link uses that link; other text becomes a
 * web-search item; a content URI becomes a file item; a launcher app key (own drawer, [APP_MIME]) becomes an app.
 * Anything else, and any scheme that could run code, is ignored and counted.
 */
object DropIngest {
    const val APP_MIME = "application/vnd.liftoff.app"
    const val MAX_ITEMS = 8
    private const val MAX_LABEL = 60
    private val allowedSchemes = setOf("http", "https", "mailto", "tel", "geo", "sms")
    private val urlInText = Regex("""(?i)\b((?:https?://|mailto:|tel:)[^\s<>"']+)""")

    fun ingest(clips: List<DropClip>): DropResult {
        val items = LinkedHashMap<String, ItemDef>()
        val apps = LinkedHashSet<String>()
        var ignored = 0
        for (c in clips) {
            if (items.size + apps.size >= MAX_ITEMS) { ignored++; continue }
            val text = c.text?.trim().orEmpty()
            val uri = c.uri?.trim().orEmpty()
            when {
                c.mime == APP_MIME && text.isNotEmpty() -> apps += text
                uri.isNotEmpty() -> {
                    val scheme = uri.substringBefore(':', "").lowercase()
                    when {
                        scheme == "content" -> items[ItemKeys.link(uri)] = ItemDef(ItemKeys.link(uri), ItemKind.Link, labelOr(c.label, fileName(uri)), uri, isFile = true)
                        scheme in allowedSchemes -> items[ItemKeys.link(uri)] = ItemDef(ItemKeys.link(uri), ItemKind.Link, labelOr(c.label, hostOf(uri)), uri)
                        else -> ignored++ // file:// would leak a path, javascript: could run code, anything else is unknown
                    }
                }
                text.isNotEmpty() -> {
                    val url = urlInText.find(text)?.value?.trimEnd('.', ',', ')', ';')
                    if (url != null && url.substringBefore(':').lowercase() in allowedSchemes) {
                        items[ItemKeys.link(url)] = ItemDef(ItemKeys.link(url), ItemKind.Link, labelOr(c.label, hostOf(url)), url)
                    } else {
                        val q = text.take(200)
                        val target = "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(q, "UTF-8")
                        items[ItemKeys.link(target)] = ItemDef(ItemKeys.link(target), ItemKind.Link, labelOr(null, q), target)
                    }
                }
                else -> ignored++
            }
        }
        return DropResult(items.values.toList(), apps.toList(), ignored)
    }

    private fun labelOr(label: String?, fallback: String) = (label?.takeIf { it.isNotBlank() } ?: fallback).trim().replace('\n', ' ').take(MAX_LABEL).ifEmpty { "Link" }

    private fun hostOf(uri: String): String {
        val rest = uri.substringAfter("://", uri.substringAfter(':', uri))
        return rest.substringBefore('/').substringBefore('?').ifEmpty { uri }
    }

    private fun fileName(uri: String): String = uri.substringAfterLast('/').substringBefore('?').ifEmpty { "File" }
}

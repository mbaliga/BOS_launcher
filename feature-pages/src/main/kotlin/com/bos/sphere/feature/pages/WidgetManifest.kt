package com.bos.sphere.feature.pages

import java.util.Locale

/**
 * A widget's declarations (D-AC §3.2, with D-BD's reading of `opens` as routes), as the runtime
 * enforces them. For a D-AC package it comes from its `widget.json`; for a single-file page it is
 * generated ([ManifestRules.forPage]): a page at size `fill` that reads, writes and opens nothing.
 */
data class WidgetManifest(
    val id: String,
    val name: String,
    val version: Long,
    val sizes: List<String>,
    val reads: List<String>,
    val writes: List<String>,
    /** Route templates `orrery.open` may use (`app:<package>`, `page:<id>`, `{param}` placeholders). */
    val opens: List<String>,
    /** Always false: packages never reach the network; a page's network is its own per-page switch. */
    val network: Boolean = false,
    /** True for the manifest generated around a single-file page. */
    val isPage: Boolean = false,
)

enum class ManifestProblem {
    NotJson,
    MissingField,
    WrongType,
    BadId,
    BadName,
    BadVersion,
    BadSize,

    /** No `fill` in `sizes`: this launcher shows HTML widgets only as full pages. */
    NoFill,
    BadChannel,

    /** `writes` names a `launcher.*` channel: only the launcher publishes those. */
    LauncherChannelWrite,

    /** `network` is not false: D-AC v1 widgets never reach the network. */
    NetworkRequested,
    BadOpens,
}

data class ManifestIssue(val problem: ManifestProblem, val field: String, val value: String = "")

sealed interface ManifestCheck {
    /** [warnings]: accepted, but worth telling the user (e.g. an `opens` this launcher can't route). */
    data class Ok(val manifest: WidgetManifest, val warnings: List<ManifestIssue>) : ManifestCheck

    data class Invalid(val issues: List<ManifestIssue>) : ManifestCheck
}

/** Validates `widget.json` against D-AC / D-BD and builds the generated manifest for pages. Pure. */
object ManifestRules {
    const val LAUNCHER_PREFIX = "launcher."
    const val MAX_CHANNELS = 32
    const val MAX_OPENS = 16

    private val ID = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
    private val SIZE = Regex("^[1-9][0-9]?x[1-9][0-9]?$")
    val CHANNEL = Regex("^[a-z][a-z0-9_-]{0,31}(\\.[a-z][a-z0-9_-]{0,31}){1,3}$")

    fun validate(json: String): ManifestCheck {
        val doc = MiniJson.parseOrNull(json).asObject()
            ?: return ManifestCheck.Invalid(listOf(ManifestIssue(ManifestProblem.NotJson, "widget.json")))
        val issues = ArrayList<ManifestIssue>()
        val warnings = ArrayList<ManifestIssue>()

        val id = when (val v = doc["id"]) {
            null -> null.also { issues += ManifestIssue(ManifestProblem.MissingField, "id") }
            is String -> v.takeIf { ID.matches(it) } ?: null.also { issues += ManifestIssue(ManifestProblem.BadId, "id", v) }
            else -> null.also { issues += ManifestIssue(ManifestProblem.WrongType, "id") }
        }
        val name = when (val v = doc["name"]) {
            null -> null.also { issues += ManifestIssue(ManifestProblem.MissingField, "name") }
            is String -> PageEdits.cleanTitle(v).takeIf { it.isNotEmpty() }
                ?: null.also { issues += ManifestIssue(ManifestProblem.BadName, "name") }
            else -> null.also { issues += ManifestIssue(ManifestProblem.WrongType, "name") }
        }
        val version = when (val v = doc["version"]) {
            null -> null.also { issues += ManifestIssue(ManifestProblem.MissingField, "version") }
            is Long -> v.takeIf { it >= 1 } ?: null.also { issues += ManifestIssue(ManifestProblem.BadVersion, "version", v.toString()) }
            else -> null.also { issues += ManifestIssue(ManifestProblem.BadVersion, "version", v.toString()) }
        }
        val sizes = stringList(doc, "sizes", required = true, max = 16, issues)
        if (sizes != null) {
            if (sizes.isEmpty()) issues += ManifestIssue(ManifestProblem.MissingField, "sizes")
            sizes.filterNot { it == "fill" || SIZE.matches(it) }
                .forEach { issues += ManifestIssue(ManifestProblem.BadSize, "sizes", it) }
            if (sizes.isNotEmpty() && "fill" !in sizes) issues += ManifestIssue(ManifestProblem.NoFill, "sizes")
        }
        val reads = channels(doc, "reads", issues)
        val writes = channels(doc, "writes", issues)
        writes?.filter { it.startsWith(LAUNCHER_PREFIX) }
            ?.forEach { issues += ManifestIssue(ManifestProblem.LauncherChannelWrite, "writes", it) }
        val opens = when (val v = doc["opens"]) {
            null -> emptyList()
            is String -> listOf(v)
            is List<*> -> if (v.all { it is String }) v.map { it as String } else null
            else -> null
        }
        if (opens == null || opens.size > MAX_OPENS || opens.any { it.isBlank() || it.length > 256 }) {
            issues += ManifestIssue(ManifestProblem.BadOpens, "opens")
        } else {
            opens.filterNot { OpenRoutes.isRouteTemplate(it) }
                .forEach { warnings += ManifestIssue(ManifestProblem.BadOpens, "opens", it) }
        }
        when (val v = doc["network"]) {
            null, false -> Unit
            true -> issues += ManifestIssue(ManifestProblem.NetworkRequested, "network")
            else -> issues += ManifestIssue(ManifestProblem.WrongType, "network")
        }

        if (issues.isNotEmpty() || id == null || name == null || version == null || sizes == null ||
            reads == null || writes == null || opens == null
        ) {
            return ManifestCheck.Invalid(issues.ifEmpty { listOf(ManifestIssue(ManifestProblem.MissingField, "widget.json")) })
        }
        return ManifestCheck.Ok(
            WidgetManifest(id, name, version, sizes, reads.distinct(), writes.distinct(), opens.distinct()),
            warnings,
        )
    }

    /** The manifest generated around a single-file page: size `fill`, no channels, no routes, no network. */
    fun forPage(name: String): WidgetManifest = WidgetManifest(
        id = "page",
        name = PageEdits.cleanTitle(name).ifEmpty { "Page" },
        version = 1,
        sizes = listOf("fill"),
        reads = emptyList(),
        writes = emptyList(),
        opens = emptyList(),
        network = false,
        isPage = true,
    )

    fun encode(m: WidgetManifest): String = MiniJson.write(
        linkedMapOf(
            "id" to m.id,
            "name" to m.name,
            "version" to m.version,
            "sizes" to m.sizes,
            "reads" to m.reads,
            "writes" to m.writes,
            "opens" to m.opens,
            "network" to false,
            "page" to m.isPage,
        ),
    )

    /** Reads back what [encode] stored (already validated at import). */
    fun decodeStored(json: String): WidgetManifest? {
        val d = MiniJson.parseOrNull(json).asObject() ?: return null
        fun list(k: String) = (d[k] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        return WidgetManifest(
            id = d.str("id") ?: return null,
            name = d.str("name") ?: return null,
            version = d.long("version") ?: 1,
            sizes = list("sizes"),
            reads = list("reads").filter { CHANNEL.matches(it) },
            writes = list("writes").filter { CHANNEL.matches(it) && !it.startsWith(LAUNCHER_PREFIX) },
            opens = list("opens"),
            network = false,
            isPage = d.bool("page") ?: false,
        )
    }

    private fun stringList(
        doc: Map<String, Any?>,
        key: String,
        required: Boolean,
        max: Int,
        issues: MutableList<ManifestIssue>,
    ): List<String>? = when (val v = doc[key]) {
        null -> if (required) null.also { issues += ManifestIssue(ManifestProblem.MissingField, key) } else emptyList()
        is List<*> -> if (v.all { it is String } && v.size <= max) v.map { it as String }
        else null.also { issues += ManifestIssue(ManifestProblem.WrongType, key) }
        else -> null.also { issues += ManifestIssue(ManifestProblem.WrongType, key) }
    }

    private fun channels(doc: Map<String, Any?>, key: String, issues: MutableList<ManifestIssue>): List<String>? {
        val list = stringList(doc, key, required = false, max = MAX_CHANNELS, issues) ?: return null
        list.filterNot { CHANNEL.matches(it) }.forEach { issues += ManifestIssue(ManifestProblem.BadChannel, key, it) }
        return list
    }
}

/** What the importer learns from a single-file page. */
data class HtmlInfo(
    val title: String?,
    /** Orrery's own build (`web/build.py` writes `<title>Orrery</title>`). */
    val isOrrery: Boolean,
    /** Hosts the file names in http(s) addresses: what it may reach once its network is on. */
    val mentionedHosts: List<String>,
    /** No `<!doctype>` / `<html>` at the top: a page fragment, wrapped in a skeleton when served. */
    val isFragment: Boolean,
)

object HtmlScan {
    private val TITLE = Regex("<title[^>]*>(.*?)</title\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val URL_HOST = Regex(
        "(?i)\\bhttps?://(\\[[0-9a-f:.]+]|[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+)",
    )

    /** XML namespace names (`xmlns="http://www.w3.org/2000/svg"`) are identifiers, never fetched. */
    private val NAMESPACE_HOSTS = setOf("www.w3.org", "w3.org")
    const val MAX_HOSTS = 40

    fun scan(html: String): HtmlInfo {
        val title = title(html)
        return HtmlInfo(
            title = title,
            isOrrery = title == "Orrery",
            mentionedHosts = hosts(html),
            isFragment = HtmlPrep.isFragment(html),
        )
    }

    fun title(html: String): String? {
        val raw = TITLE.find(html.take(256 * 1024))?.groupValues?.get(1) ?: return null
        return PageEdits.cleanTitle(decodeEntities(raw)).takeIf { it.isNotEmpty() }
    }

    fun hosts(html: String): List<String> =
        URL_HOST.findAll(html)
            .map { it.groupValues[1].lowercase(Locale.ROOT) }
            .filter { it !in NAMESPACE_HOSTS }
            .distinct()
            .sorted()
            .take(MAX_HOSTS)
            .toList()

    /** The page's name: "Orrery" for Orrery's build, else its title, else the file name. */
    fun displayName(info: HtmlInfo, fileName: String?): String {
        if (info.isOrrery) return "Orrery"
        info.title?.let { return it }
        val base = fileName?.substringAfterLast('/')?.substringBeforeLast('.')?.let(PageEdits::cleanTitle)
        return base?.takeIf { it.isNotEmpty() } ?: "Page"
    }

    private fun decodeEntities(s: String): String =
        Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos|nbsp);").replace(s) { m ->
            when (val e = m.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                else -> {
                    val code = if (e.startsWith("#x")) e.drop(2).toIntOrNull(16) else e.drop(1).toIntOrNull()
                    code?.takeIf { Character.isValidCodePoint(it) }?.let { String(Character.toChars(it)) } ?: m.value
                }
            }
        }
}

/** A package file as found in a zip or folder: its path inside and its size. */
data class PackageEntry(val path: String, val size: Long)

enum class PackageProblem { Empty, UnsafePath, NoManifest, NoHtml, TooManyFiles, TooLarge }

sealed interface LayoutCheck {
    /** [files]: stored path (`widget.json`, `widget.html`, `assets/…`) → path inside the source. */
    data class Ok(val files: Map<String, String>, val ignored: Int) : LayoutCheck

    data class Invalid(val problem: PackageProblem, val detail: String = "") : LayoutCheck
}

/**
 * Picks the files of a D-AC package out of a zip or folder listing: `widget.json` and `widget.html`
 * at the top (or inside one top-level folder, as zips of a folder are), plus `assets/…`. Anything
 * else is ignored. Any path that could escape the package (`..`, absolute, a drive letter) refuses the
 * whole package (zip-slip). Pure.
 */
object PackageLayout {
    const val MAX_FILES = 512
    const val MAX_FILE_BYTES = 16L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 24L * 1024 * 1024
    const val MANIFEST = "widget.json"
    const val HTML = "widget.html"
    const val ASSETS = "assets/"

    /** A normalised relative path, or null if it isn't safe to write under the package's directory. */
    fun safePath(raw: String): String? {
        if (raw.isEmpty() || '\u0000' in raw) return null
        var p = raw.replace('\\', '/')
        if (p.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(p)) return null
        while (p.startsWith("./")) p = p.substring(2)
        val segments = p.split('/')
        val trailingSlash = p.endsWith("/")
        val parts = if (trailingSlash) segments.dropLast(1) else segments
        if (parts.isEmpty() || parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        return parts.joinToString("/") + if (trailingSlash) "/" else ""
    }

    fun select(entries: List<PackageEntry>): LayoutCheck {
        val files = ArrayList<PackageEntry>()
        for (e in entries) {
            val safe = safePath(e.path) ?: return LayoutCheck.Invalid(PackageProblem.UnsafePath, e.path)
            if (safe.endsWith("/")) continue // a directory entry
            val name = safe.substringAfterLast('/')
            if (safe.startsWith("__MACOSX/") || name == ".DS_Store") continue
            files += e.copy(path = safe)
        }
        if (files.isEmpty()) return LayoutCheck.Invalid(PackageProblem.Empty)
        val paths = files.mapTo(HashSet()) { it.path }
        val root = when {
            MANIFEST in paths -> ""
            else -> {
                val tops = files.map { it.path.substringBefore('/', missingDelimiterValue = "") }.toSet()
                val top = tops.singleOrNull()?.takeIf { it.isNotEmpty() }
                if (top != null && "$top/$MANIFEST" in paths) "$top/" else return LayoutCheck.Invalid(PackageProblem.NoManifest)
            }
        }
        if ("$root$HTML" !in paths) return LayoutCheck.Invalid(PackageProblem.NoHtml)
        val chosen = LinkedHashMap<String, String>()
        var total = 0L
        var ignored = 0
        for (f in files) {
            val rel = f.path.removePrefix(root)
            if (!f.path.startsWith(root)) { ignored++; continue }
            if (rel != MANIFEST && rel != HTML && !rel.startsWith(ASSETS)) { ignored++; continue }
            if (f.size > MAX_FILE_BYTES) return LayoutCheck.Invalid(PackageProblem.TooLarge, f.path)
            total += f.size.coerceAtLeast(0)
            chosen[rel] = f.path
        }
        if (chosen.size > MAX_FILES) return LayoutCheck.Invalid(PackageProblem.TooManyFiles)
        if (total > MAX_TOTAL_BYTES) return LayoutCheck.Invalid(PackageProblem.TooLarge)
        return LayoutCheck.Ok(chosen, ignored)
    }

    /** A zip starts with `PK\u0003\u0004` (or `PK\u0005\u0006` when empty). */
    fun isZip(head: ByteArray): Boolean =
        head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
            ((head[2] == 3.toByte() && head[3] == 4.toByte()) || (head[2] == 5.toByte() && head[3] == 6.toByte()))
}

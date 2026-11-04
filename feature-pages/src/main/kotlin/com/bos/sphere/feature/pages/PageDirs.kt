package com.bos.sphere.feature.pages

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Imported pages on disk under [root], in plain `java.io` (no Android), so the import, swap and sweep
 * rules are unit-tested:
 *
 * - `<id>/`: the page's files: `record.json` + `content/`;
 * - `<id>.tmp/`: an import being built (staging);
 * - `<id>.old/`: the previous files while a re-import swaps the new ones in.
 *
 * An import writes everything into staging first and only then swaps it in ([commit]), so a failed or
 * interrupted import leaves the page as it was. Any I/O failure while writing (a damaged zip entry, a
 * document the provider can't open, a full disk) becomes [ImportFailure.Unreadable]; nothing thrown
 * reaches the launcher. Call off the main thread.
 */
class PageDirs(val root: File) {

    fun dir(pageId: String): File = File(root, pageId)

    fun contentDir(pageId: String): File = File(dir(pageId), CONTENT)

    fun recordFile(pageId: String): File = File(dir(pageId), RECORD)

    private fun staging(pageId: String): File = File(root, pageId + STAGING)

    private fun old(pageId: String): File = File(root, pageId + OLD)

    /** Writes a package's files ([layout]: destination → source, opened by [open]), checks its manifest, swaps it in. */
    fun writePackage(
        pageId: String,
        layout: LayoutCheck.Ok,
        sourceName: String?,
        nowMillis: Long,
        open: (String) -> InputStream?,
    ): ImportResult = guarded(pageId) { staging ->
        val content = File(staging, CONTENT)
        var total = 0L
        for ((dest, source) in layout.files) {
            val target = File(content, dest)
            if (!target.canonicalPath.startsWith(content.canonicalPath + File.separator)) {
                return@guarded ImportResult.Failed(ImportFailure.Layout(PackageProblem.UnsafePath, dest))
            }
            target.parentFile?.mkdirs()
            val n = open(source)?.use { input -> target.outputStream().use { copyCapped(input, it, PackageLayout.MAX_FILE_BYTES) } }
                ?: return@guarded ImportResult.Failed(ImportFailure.Unreadable)
            if (n < 0) return@guarded ImportResult.Failed(ImportFailure.TooLarge)
            total += n
            if (total > PackageLayout.MAX_TOTAL_BYTES) return@guarded ImportResult.Failed(ImportFailure.TooLarge)
        }
        val manifestFile = File(content, PackageLayout.MANIFEST)
        if (manifestFile.length() > MAX_MANIFEST_BYTES) return@guarded ImportResult.Failed(ImportFailure.TooLarge)
        val check = ManifestRules.validate(manifestFile.readText())
        val ok = check as? ManifestCheck.Ok
            ?: return@guarded ImportResult.Failed(ImportFailure.Manifest((check as ManifestCheck.Invalid).issues))
        val record = ImportRecord(
            name = ok.manifest.name,
            isPackage = true,
            manifest = ok.manifest,
            isOrrery = false,
            mentionedHosts = emptyList(),
            bytes = total,
            sourceName = sourceName,
            importedAt = nowMillis,
            warnings = ok.warnings,
        )
        finish(pageId, staging, record)
    }

    /** Writes a single-file page ([bytes], already read and checked) with its [record] and swaps it in. */
    fun writePage(pageId: String, bytes: ByteArray, record: ImportRecord): ImportResult = guarded(pageId) { staging ->
        val content = File(staging, CONTENT).also { it.mkdirs() }
        File(content, ServePolicy.PAGE_FILE).writeBytes(bytes)
        finish(pageId, staging, record)
    }

    private fun finish(pageId: String, staging: File, record: ImportRecord): ImportResult {
        File(staging, RECORD).writeText(encodeRecord(record))
        return if (commit(pageId, staging)) ImportResult.Ok(record) else ImportResult.Failed(ImportFailure.Unreadable)
    }

    private inline fun guarded(pageId: String, block: (staging: File) -> ImportResult): ImportResult {
        val staging = staging(pageId)
        return try {
            staging.deleteRecursively()
            staging.mkdirs()
            block(staging)
        } catch (_: IOException) {
            ImportResult.Failed(ImportFailure.Unreadable)
        } catch (_: RuntimeException) {
            ImportResult.Failed(ImportFailure.Unreadable)
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * Swaps [staging] in as [pageId]'s files. The current files move aside to `<id>.old` first and
     * come back if the swap fails; if the process dies in between, [sweep] puts `<id>.old` back.
     * At no point are the old files deleted before the new ones are in place.
     */
    fun commit(pageId: String, staging: File): Boolean {
        val final = dir(pageId)
        val old = old(pageId)
        old.deleteRecursively()
        val hadFiles = final.exists()
        if (hadFiles && !final.renameTo(old)) return false
        if (!staging.renameTo(final)) {
            if (hadFiles) old.renameTo(final)
            return false
        }
        old.deleteRecursively()
        return true
    }

    /**
     * Tidies [root] at start: leftover staging is removed; a `<id>.old` whose `<id>` is missing (a
     * swap cut short) is put back; with [keep] given, the files of pages not in it (an interrupted
     * removal or import) are removed. [keep] null = the page list couldn't be fully read (unreadable,
     * or written by a newer version), so no page's files are removed.
     */
    fun sweep(keep: Set<String>?) {
        root.listFiles()?.forEach { f ->
            val name = f.name
            when {
                name.endsWith(STAGING) -> f.deleteRecursively()
                name.endsWith(OLD) -> {
                    val id = name.removeSuffix(OLD)
                    val final = dir(id)
                    when {
                        final.exists() -> f.deleteRecursively() // the swap finished; this is the old copy
                        keep == null || id in keep -> f.renameTo(final) // the swap was cut short: put it back
                        else -> f.deleteRecursively()
                    }
                }
                keep != null && name !in keep -> f.deleteRecursively()
            }
        }
    }

    fun delete(pageId: String) {
        dir(pageId).deleteRecursively()
        old(pageId).deleteRecursively()
        staging(pageId).deleteRecursively()
    }

    companion object {
        const val RECORD = "record.json"
        const val CONTENT = "content"
        const val STAGING = ".tmp"
        const val OLD = ".old"
        const val MAX_MANIFEST_BYTES = 64L * 1024

        /** Copies at most [cap] bytes; returns the count, or −1 if the stream had more. */
        fun copyCapped(input: InputStream, out: OutputStream, cap: Long): Long {
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) return total
                total += n
                if (total > cap) return -1
                out.write(buf, 0, n)
            }
        }

        /** `InputStream.readNBytes` is API 33+; minSdk is 31. */
        fun readUpTo(input: InputStream, max: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream(minOf(max, 64 * 1024))
            val buf = ByteArray(64 * 1024)
            var total = 0
            while (total < max) {
                val n = input.read(buf, 0, minOf(buf.size, max - total))
                if (n < 0) break
                out.write(buf, 0, n)
                total += n
            }
            return out.toByteArray()
        }

        fun encodeRecord(r: ImportRecord): String = MiniJson.write(
            linkedMapOf(
                "name" to r.name,
                "package" to r.isPackage,
                "orrery" to r.isOrrery,
                "hosts" to r.mentionedHosts,
                "bytes" to r.bytes,
                "source" to r.sourceName,
                "importedAt" to r.importedAt,
                "manifest" to MiniJson.parse(ManifestRules.encode(r.manifest)),
                "warnings" to r.warnings.map { listOf(it.problem.name, it.field, it.value) },
            ),
        )

        fun decodeRecord(json: String): ImportRecord? {
            val d = MiniJson.parseOrNull(json).asObject() ?: return null
            val manifest = ManifestRules.decodeStored(MiniJson.write(d["manifest"])) ?: return null
            return ImportRecord(
                name = d.str("name") ?: manifest.name,
                isPackage = d.bool("package") ?: !manifest.isPage,
                manifest = manifest,
                isOrrery = d.bool("orrery") ?: false,
                mentionedHosts = (d["hosts"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                bytes = d.long("bytes") ?: 0L,
                sourceName = d.str("source"),
                importedAt = d.long("importedAt") ?: 0L,
                warnings = (d["warnings"] as? List<*>)?.mapNotNull { w ->
                    val l = (w as? List<*>)?.filterIsInstance<String>() ?: return@mapNotNull null
                    val p = ManifestProblem.entries.firstOrNull { it.name == l.getOrNull(0) } ?: return@mapNotNull null
                    ManifestIssue(p, l.getOrElse(1) { "" }, l.getOrElse(2) { "" })
                } ?: emptyList(),
            )
        }
    }
}

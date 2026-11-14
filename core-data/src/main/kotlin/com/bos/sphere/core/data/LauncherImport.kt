package com.bos.sphere.core.data

import android.database.sqlite.SQLiteDatabase
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * CFG-12: reads the home layout from another launcher's backup, where the format is documented.
 *
 * The one documented format is the AOSP Launcher3 database: a SQLite file `launcher.db` with a `favorites` table (the
 * column and constant names are in AOSP's `LauncherSettings.Favorites`). Launchers built on it keep the same table, and some
 * wrap the file in a zip backup. This reads the table from the bare database or from a zip that holds `launcher.db`. Nothing
 * else is attempted: a launcher whose backup is not documented is not guessed at.
 *
 * What carries over: apps on desktop pages, in their order. What does not: widgets (their ids are per install; they are
 * counted so the screen can say so), deep shortcuts, and folder structure (folder contents are laid onto the page where the
 * folder was; Liftoff folders are a separate model). Hotseat apps go to the front of the first page.
 * Apps that are not installed here stay as placeholders and resolve when installed, as for a Liftoff import (CFG-02).
 */
object LauncherImport {
    /** The `favorites` columns this reads. */
    data class Row(val id: Long, val container: Long, val screen: Int, val cellX: Int, val cellY: Int, val itemType: Int, val intent: String?)

    const val CONTAINER_DESKTOP = -100L
    const val CONTAINER_HOTSEAT = -101L
    const val TYPE_APP = 0
    const val TYPE_SHORTCUT = 1
    const val TYPE_FOLDER = 2
    const val TYPE_WIDGET = 4

    enum class Kind { SqliteDatabase, Zip, Unknown }

    fun detect(bytes: ByteArray): Kind = when {
        bytes.size >= 16 && String(bytes, 0, 15, Charsets.ISO_8859_1) == "SQLite format 3" -> Kind.SqliteDatabase
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2].toInt() == 3 && bytes[3].toInt() == 4 -> Kind.Zip
        else -> Kind.Unknown
    }

    /** The database bytes: the file itself, or the `launcher.db` entry of a zip. Throws [StateFormatException] when neither. */
    fun databaseBytes(bytes: ByteArray): ByteArray = when (detect(bytes)) {
        Kind.SqliteDatabase -> bytes
        Kind.Zip -> {
            var found: ByteArray? = null
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                while (found == null) {
                    val e = z.nextEntry ?: break
                    if (!e.isDirectory && e.name.substringAfterLast('/').equals("launcher.db", ignoreCase = true)) found = z.readBytes()
                }
            }
            found?.takeIf { detect(it) == Kind.SqliteDatabase } ?: throw StateFormatException("This backup has no launcher.db, so its layout cannot be read")
        }
        Kind.Unknown -> throw StateFormatException("This is not a launcher database or a backup that holds one")
    }

    /** Reads the `favorites` rows from database bytes, through a temporary read-only file. */
    fun readRows(db: ByteArray, tempDir: File): List<Row> {
        val f = File.createTempFile("import", ".db", tempDir)
        try {
            f.writeBytes(db)
            val d = try { SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY) } catch (e: Exception) { throw StateFormatException("The launcher database could not be opened", e) }
            d.use {
                val rows = ArrayList<Row>()
                try {
                    it.rawQuery("SELECT _id, container, screen, cellX, cellY, itemType, intent FROM favorites", null).use { c ->
                        while (c.moveToNext()) rows += Row(c.getLong(0), c.getLong(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5), c.getString(6))
                    }
                } catch (e: Exception) { throw StateFormatException("This database has no favorites table, so it is not a Launcher3 layout", e) }
                return rows
            }
        } finally { f.delete() }
    }

    /** The app key (`pkg/.Cls#0`, the form [AppEntry.key] uses for the owner profile) named by an intent URI's `component=`; null for anything else. */
    fun appKey(intent: String?): String? {
        val comp = Regex("""component=([^;]+)""").find(intent ?: return null)?.groupValues?.get(1) ?: return null
        val pkg = comp.substringBefore('/', "").takeIf { it.isNotEmpty() } ?: return null
        var cls = comp.substringAfter('/', "").takeIf { it.isNotEmpty() } ?: return null
        if (cls.startsWith(".")) cls = pkg + cls
        // Same short form as ComponentName.flattenToShortString.
        val short = if (cls.startsWith(pkg) && cls.length > pkg.length && cls[pkg.length] == '.') cls.substring(pkg.length) else cls
        return "$pkg/$short#0"
    }

    class Result(
        val layout: HomeLayout,
        val apps: Int,
        val pages: Int,
        val foldersFlattened: Int,
        val widgetsSkipped: Int,
        val shortcutsSkipped: Int,
        /** Keys of apps not installed here (placeholders that resolve when installed). */
        val placeholders: Set<String>,
    )

    /**
     * Rows to a Liftoff layout. An app whose exact component is not installed but whose package is takes the installed
     * component of that package (launchers rename activities between versions). [installed] empty means "unknown": every app is kept as is.
     */
    fun toLayout(rows: List<Row>, installed: Set<String>): Result {
        fun resolve(k: String): String {
            if (installed.isEmpty() || k in installed) return k
            val pkg = k.substringBefore('/')
            return installed.firstOrNull { it.substringBefore('/') == pkg && it.endsWith("#0") } ?: k
        }
        val byContainer = rows.groupBy { it.container }
        var folders = 0; var widgets = 0; var shortcuts = 0
        fun keysOf(r: Row): List<String> = when (r.itemType) {
            TYPE_APP -> listOfNotNull(appKey(r.intent)?.let(::resolve))
            TYPE_FOLDER -> { folders++; (byContainer[r.id].orEmpty()).sortedWith(compareBy({ it.screen }, { it.cellY }, { it.cellX })).flatMap(::keysOf) }
            TYPE_WIDGET -> { widgets++; emptyList() }
            else -> { shortcuts++; emptyList() }
        }
        val screens = rows.filter { it.container == CONTAINER_DESKTOP }.groupBy { it.screen }.toSortedMap()
        val seen = HashSet<String>()
        val pages = ArrayList<List<String>>()
        for ((_, items) in screens) {
            pages += items.sortedWith(compareBy({ it.cellY }, { it.cellX })).flatMap(::keysOf).filter { seen.add(it) }
        }
        val hot = (byContainer[CONTAINER_HOTSEAT].orEmpty()).sortedWith(compareBy({ it.screen }, { it.cellX }, { it.cellY })).flatMap(::keysOf).filter { seen.add(it) }
        if (hot.isNotEmpty()) { if (pages.isEmpty()) pages += hot else pages[0] = hot + pages[0] }
        val homePages = pages.filter { it.isNotEmpty() }.mapIndexed { i, keys -> HomePage(keys = keys, id = i + 1L) }
        val all = homePages.flatMap { it.keys }
        return Result(
            layout = HomeLayout(pages = homePages, removed = emptySet(), seeded = true),
            apps = all.size,
            pages = homePages.size,
            foldersFlattened = folders,
            widgetsSkipped = widgets,
            shortcutsSkipped = shortcuts,
            placeholders = if (installed.isEmpty()) emptySet() else all.filterTo(LinkedHashSet()) { it !in installed },
        )
    }

    /** Whole pipeline for the UI: bytes of the picked file to a [Result]. */
    fun parse(bytes: ByteArray, installed: Set<String>, tempDir: File): Result = toLayout(readRows(databaseBytes(bytes), tempDir), installed)
}

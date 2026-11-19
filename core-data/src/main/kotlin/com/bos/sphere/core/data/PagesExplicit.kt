package com.bos.sphere.core.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Wave 30 (pagesExplicit): the home pages hold only what the user put there. Older builds drew every installed app onto the pages (the
 * "All apps" default of `appsOnHome`) and the first edit wrote that auto-filled arrangement down as if the user had built it, so an
 * existing install can hold many full pages the user never made. This is the one-time migration that finds such a layout and starts
 * over with ONE empty page, keeping the old arrangement in [LegacyHomeBackup] so "Restore previous pages" brings it back (30 days).
 * Pure: it takes the stored pages and the installed apps, so every rule is JVM-tested with fixtures.
 */
object PagesExplicit {
    /** Bumped when the explicit-pages rules change; a store at this version has been through the migration. */
    const val VERSION = 1

    /** More than this many consecutive full pages, covering most of the installed apps, is the auto-fill signature. */
    const val FULL_RUN = 4
    const val COVERAGE_RUN = 0.60
    /** Pages from the legacy "All apps" mode with no recorded manual edit: this many pages covering this share of the apps. */
    const val MIN_PAGES_NO_EDIT = 3
    const val COVERAGE_NO_EDIT = 0.50

    /** What the migration decided. */
    enum class Verdict { Keep, Reset }

    /**
     * Whether [pages] is an arrangement the launcher filled by itself. [installedOrders] are the orders the old auto-fill used (catalog order,
     * alphabetical, most used first), each a list of every installed launchable app key. A layout with a recorded manual edit
     * ([explicitEdit]) is never touched, and neither is one with few pages or few items (a hand-built home).
     */
    fun verdict(
        pages: List<HomePage>,
        installedOrders: List<List<String>>,
        capacityOf: (PageLayout) -> Int,
        explicitEdit: Boolean,
        legacyEverything: Boolean,
    ): Verdict {
        if (explicitEdit) return Verdict.Keep
        val installed = installedOrders.firstOrNull()?.toHashSet() ?: return Verdict.Keep
        if (installed.isEmpty()) return Verdict.Keep
        val nonEmpty = pages.filter { it.keys.isNotEmpty() }
        if (nonEmpty.size < 2) return Verdict.Keep
        val placed = pages.flatMap { it.keys }
        val apps = placed.filter { it in installed }
        val appSet = apps.toHashSet()
        // 1. Exactly the apps, nothing else, in one of the automatic orders, every page full but the last.
        val onlyApps = placed.size == apps.size
        val full = { p: HomePage -> p.keys.size >= capacityOf(p.layout).coerceAtLeast(1) }
        if (onlyApps && nonEmpty.dropLast(1).all(full) && installedOrders.any { order -> order.filter { it in appSet } == apps }) return Verdict.Reset
        // 2. A long run of full pages that between them hold most of what is installed.
        var run = 0; var best = 0; var runKeys = HashSet<String>(); var bestKeys = emptySet<String>()
        for (p in pages) {
            if (full(p)) { run++; runKeys.addAll(p.keys.filter { it in installed }); if (run > best) { best = run; bestKeys = HashSet(runKeys) } }
            else { run = 0; runKeys = HashSet() }
        }
        if (best > FULL_RUN && bestKeys.size > installed.size * COVERAGE_RUN) return Verdict.Reset
        // 3. The old "All apps" mode, never edited by hand, spread over several pages.
        if (legacyEverything && nonEmpty.size >= MIN_PAGES_NO_EDIT && appSet.size >= installed.size * COVERAGE_NO_EDIT) return Verdict.Reset
        return Verdict.Keep
    }
}

/** The previous arrangement, kept in `files/home_layout_legacy.json` (excluded from backup and device transfer, see [StateSchema.BACKUP_EXCLUDED_FILES]). */
class LegacyHomeBackup(private val dir: File) {
    class Saved(val layout: HomeLayout, val atMillis: Long)

    private val file get() = File(dir, FILE)

    fun save(layout: HomeLayout, atMillis: Long) {
        runCatching {
            dir.mkdirs()
            val o = JSONObject().put("at", atMillis).put("pages", HomeLayoutCodec.encode(layout.pages)).put("removed", JSONArray(layout.removed.toList()))
            file.writeText(o.toString())
        }
    }

    fun load(): Saved? = runCatching {
        if (!file.exists()) return null
        val o = JSONObject(file.readText())
        val removed = o.optJSONArray("removed")?.let { a -> List(a.length()) { a.getString(it) }.toSet() } ?: emptySet()
        Saved(HomeLayout(HomeLayoutMath.normalizeIds(HomeLayoutCodec.decode(o.optString("pages"))), removed, seeded = true), o.optLong("at", 0L))
    }.getOrNull()

    /** The backup if it is still within [PagesExplicit]'s restore window (30 days); an older one is deleted. */
    fun available(nowMillis: Long): Saved? {
        val s = load() ?: return null
        if (nowMillis - s.atMillis > WINDOW_MS) { discard(); return null }
        return s
    }

    fun discard() { runCatching { file.delete() } }

    companion object {
        const val FILE = "home_layout_legacy.json"
        const val WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    }
}

/** The result of running the migration. */
enum class PagesMigrationResult { AlreadyDone, Kept, Reset }

object PagesExplicitMigration {
    /**
     * Runs once per install (the store remembers it): decides on the stored layout, and on a reset keeps the old one in [backup] and leaves ONE
     * empty page. A layout that is kept is marked as the user's own, so it can never be reset later. [installedOrders] must not be empty (the
     * caller waits for the app list: an empty catalog decides nothing and the migration stays pending).
     */
    fun run(
        store: HomeLayoutStore,
        backup: LegacyHomeBackup,
        installedOrders: List<List<String>>,
        capacityOf: (PageLayout) -> Int,
        legacyEverything: Boolean,
        nowMillis: Long,
    ): PagesMigrationResult {
        if (store.pagesExplicitVersion() >= PagesExplicit.VERSION) return PagesMigrationResult.AlreadyDone
        val cur = store.layout.value
        val verdict = PagesExplicit.verdict(cur.pages, installedOrders, capacityOf, store.explicitEdit(), legacyEverything)
        if (verdict == PagesExplicit.Verdict.Keep) {
            store.markPagesExplicitDone()
            return PagesMigrationResult.Kept
        }
        backup.save(cur, nowMillis)
        store.replace(FreshHome.emptyLayout())
        store.markPagesExplicitDone()
        store.setNoticePending(true)
        return PagesMigrationResult.Reset
    }

    /** "Restore previous pages": puts the backed-up arrangement back (a restore is an edit by the user: it is never reset again). */
    fun restore(store: HomeLayoutStore, backup: LegacyHomeBackup, nowMillis: Long): Boolean {
        val saved = backup.available(nowMillis) ?: return false
        store.replace(saved.layout)
        store.markExplicit()
        store.markPagesExplicitDone()
        store.setNoticePending(false)
        backup.discard()
        return true
    }
}

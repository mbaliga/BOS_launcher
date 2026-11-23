package com.bos.sphere.feature.grid

/**
 * CFG-14 / CFG-05: one searchable entry per setting. Ground Control filters with it and Radar can ask the same
 * index for settings results (PAL-04).
 */
data class SettingEntry(
    /** Stable id: `category index / item index`. */
    val id: String,
    val categoryIndex: Int,
    val itemIndex: Int,
    val category: String,
    val title: String,
    /** The hint line under the setting, if it has one. */
    val description: String,
    /** The current value as shown on the row ("On", "Rounded"). */
    val value: String,
    /** Wave 56 (CFG-22): hidden synonyms ("vibration" for haptics); never shown, scored below the hint. */
    val keywords: List<String> = emptyList(),
    /** Wave 56 (CFG-25): why the row cannot be used right now ("Needs Circle layout"); the row is still found. */
    val disabledReason: String? = null,
    /** Wave 56 (CFG-22): set when the hit is a row inside a pane: its stable id. Opening the hit opens the pane's page and flashes that row. */
    val paneEntryId: String? = null,
    /** The title of the pane a [paneEntryId] row lives in (empty for a row of a category page). */
    val parent: String = "",
)

/**
 * What Radar needs from the settings: a query in, ranked results out, and a way to open one. The palette stream
 * (PAL-04) feeds these into its own result type; this interface lives in feature-grid because the entries are
 * built from Ground Control's own rows, so a host passes it down to Radar without a second list to maintain.
 */
interface SettingsResultSource {
    fun results(query: String, limit: Int = 8): List<SettingEntry>
    /** Opens Ground Control on the setting. */
    fun open(entry: SettingEntry)
}

class SettingsIndex(val entries: List<SettingEntry>) {
    /**
     * Matches by name and description (and the category name). Every word of the query must match somewhere;
     * ranking: title starts with the query, a title word starts with it, title contains it, description, category.
     */
    fun search(query: String, limit: Int = Int.MAX_VALUE): List<SettingEntry> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return entries.mapNotNull { e ->
            var total = 0
            for (w in words) {
                val s = maxOf(score(e, w), ALIASES[w]?.let { score(e, it) } ?: 0)
                if (s == 0) return@mapNotNull null
                total += s
            }
            if (e.title.lowercase().startsWith(words.joinToString(" "))) total += 20
            e to total
        }.sortedWith(compareByDescending<Pair<SettingEntry, Int>> { it.second }.thenBy { it.first.categoryIndex }.thenBy { it.first.itemIndex })
            .map { it.first }.take(limit)
    }

    private fun score(e: SettingEntry, w: String): Int {
        val t = e.title.lowercase()
        return when {
            t.startsWith(w) -> 100
            t.split(' ', '-', '·').any { it.startsWith(w) } -> 80
            t.contains(w) -> 60
            e.description.lowercase().contains(w) -> 30
            e.keywords.any { k -> k.lowercase().split(' ', '-', '/').any { it.startsWith(w) } } -> KEYWORD_SCORE
            e.value.lowercase() == w -> 20
            e.category.lowercase().contains(w) -> 10
            CATEGORY_KEYWORDS.any { (k, words) -> (e.category == k || e.category == com.bos.sphere.core.design.L10n.tr(k)) && words.contains(w) } -> 10
            else -> 0
        }
    }

    fun byId(id: String): SettingEntry? = entries.firstOrNull { it.id == id }

    companion object {
        /** Hidden search aliases: the product word before the rename ("Launchpad") still finds Comms. Never shown. */
        private val ALIASES = mapOf(
            "launchpad" to "comms", "sessions" to "groups", // wave 41: sessions live on the App groups page
            // Wave 55: the names of the removed calm and alert effects still find the Calm style row.
            "vignette" to "calm style", "comic" to "calm style", "alert" to "calm style", "bloom" to "calm style", "animated" to "calm style",
        )

        /**
         * Wave 52: words that find every row of a category besides its title: the "Side panel" category was "Comms", and the panel's Comms section
         * is still a word people type. Hidden, never shown. Keyed by the English category title.
         */
        private val CATEGORY_KEYWORDS = mapOf("Side panel" to "comms widgets right-hand panel", "Pages" to "page", "Workspaces" to "workspace")

        /** Wave 56: a hidden synonym scores above the value and the category, below the hint. */
        const val KEYWORD_SCORE = 25

        fun id(category: Int, item: Int) = "$category/$item"
        /** The id of a row inside a pane: the pane's own id plus the stable string of the row. */
        fun paneId(category: Int, item: Int, entry: String) = "$category/$item/$entry"

        /** An index of every row of every category, built from the live list so no setting can be missing. */
        fun fromCategories(categories: List<SettingCategory>): SettingsIndex = SettingsIndex(
            categories.flatMapIndexed { ci, c ->
                c.items.flatMapIndexed { ii, item ->
                    val own = SettingEntry(id(ci, ii), ci, ii, c.title, item.title, item.description().orEmpty(), item.value, item.meta.keywords, item.meta.disabledReason)
                    // Wave 56: the rows a pane holds are entries of their own, found by their stable id; a hit opens the pane's page.
                    listOf(own) + item.meta.entries.map { e ->
                        SettingEntry(paneId(ci, ii, e.id), ci, ii, c.title, e.title, e.summary, "", e.keywords, item.meta.disabledReason, paneEntryId = e.id, parent = item.title)
                    }
                }
            },
        )
    }
}

/** The hint line of a setting row, if it has one. */
fun SettingItem.description(): String? = when (this) {
    is SettingItem.Toggle -> hint
    is SettingItem.Slider -> hint
    is SettingItem.Choice<*> -> hint
    is SettingItem.Stepper -> hint
    is SettingItem.Action -> hint
    is SettingItem.Pane -> null
}

/** A [SettingsResultSource] over a category list; [onOpen] jumps to the entry's category and row. */
class CategorySettingsSource(private val categories: () -> List<SettingCategory>, private val onOpen: (SettingEntry) -> Unit) : SettingsResultSource {
    override fun results(query: String, limit: Int) = SettingsIndex.fromCategories(categories()).search(query, limit)
    override fun open(entry: SettingEntry) = onOpen(entry)
}

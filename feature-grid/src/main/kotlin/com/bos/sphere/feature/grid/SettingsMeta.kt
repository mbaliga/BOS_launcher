package com.bos.sphere.feature.grid

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Wave 56 (settingsCore), CFG-28 / CFG-31: what a settings row carries besides what it shows. None of it is drawn as a row of its own:
 * a stable [id] (the section table and a search hit name a row by it, not by its English title), hidden search [keywords] (synonyms:
 * "vibration" finds Haptics), the one-line [disabledReason] of a row that stays on the page but cannot be used right now ("Needs Circle
 * layout"), and the [entries] a pane holds (its rows, searchable by a stable string id).
 */
class ItemMeta {
    var id: String? = null
    var keywords: List<String> = emptyList()
    var disabledReason: String? = null
    var entries: List<PaneEntry> = emptyList()
    /** The row changes preferences in bulk: the host takes a snapshot first and offers Undo afterwards (reset). */
    var snapshotFirst: Boolean = false

    fun copyFrom(o: ItemMeta) { id = o.id; keywords = o.keywords; disabledReason = o.disabledReason; entries = o.entries; snapshotFirst = o.snapshotFirst }
}

/** One searchable row inside a pane. [id] is a stable string; a pane that draws the row with [paneFlash] under that id flashes it on arrival. */
data class PaneEntry(val id: String, val title: String, val keywords: List<String> = emptyList(), val summary: String = "")

fun <T : SettingItem> T.rowId(id: String): T { meta.id = id; return this }
fun <T : SettingItem> T.keywords(vararg words: String): T { meta.keywords = words.toList(); return this }
/** Dims the row and says why in its summary line; null leaves it as it is. */
fun <T : SettingItem> T.disabled(reason: String?): T { meta.disabledReason = reason; return this }
/** The row is usable only while [enabled]; otherwise it stays on the page, dimmed, with [reason] (translated by the caller). */
fun <T : SettingItem> T.disabledUnless(enabled: Boolean, reason: String): T { meta.disabledReason = if (enabled) null else reason; return this }
fun <T : SettingItem> T.paneEntries(vararg e: PaneEntry): T { meta.entries = e.toList(); return this }
fun <T : SettingItem> T.snapshotFirst(): T { meta.snapshotFirst = true; return this }

val SettingItem.isEnabled: Boolean get() = meta.disabledReason == null

/** The id of the pane row a search hit asked to flash (null = none). Provided by the settings host around every page. */
val LocalPaneFlash = compositionLocalOf<String?> { null }

/** A row of a pane lights for a moment when a search hit named [id] (the hit opened the pane's page). */
@Composable
fun Modifier.paneFlash(id: String?): Modifier {
    val on = id != null && LocalPaneFlash.current == id
    return if (on) this.background(OneUi.Highlight) else this
}

/** Pure rules of the dimmed rows. */
object Disabled {
    /** The screen reader's description of a row that is off: what it is, that it is unavailable, and why. */
    fun description(title: String, reason: String, unavailable: String): String = "$title, $unavailable, $reason"
    const val ALPHA = 0.45f
}

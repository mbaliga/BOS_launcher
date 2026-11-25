package com.bos.sphere.feature.pages

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

/**
 * The page list and the navigation mode, persisted in SharedPreferences (`pages`), the same
 * zero-dependency way `HiddenAppsStore` and the widget placement store keep theirs, so both survive
 * process death and reboot. Main thread.
 *
 * A stored list this version can't read is never overwritten silently: an unreadable document is
 * moved aside ([PageBackups]: `list_unreadable`, then `list_unreadable_1`, …) and a document from a newer
 * version is copied aside (`list_schema_<n>`, …), both with a synchronous commit before anything new is
 * written. While such a copy exists, [listReadFully] is false, and nothing that belongs to pages the
 * decoded list doesn't show (their imported files) is removed.
 */
class PageStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val random = SecureRandom()

    private val _pages: MutableStateFlow<PageList>
    val pages: StateFlow<PageList>

    private val _mode = MutableStateFlow(NavMode.fromWire(prefs.getString(KEY_MODE, null)))
    val mode: StateFlow<NavMode> = _mode.asStateFlow()

    /** The stored list was read completely, and no list this version couldn't read was kept aside. */
    val listReadFully: Boolean

    init {
        val raw = prefs.getString(KEY_LIST, null)
        val decoded = PageListCodec.decode(raw)
        when (decoded.status) {
            DecodeStatus.Unreadable -> {
                val edit = prefs.edit()
                PageBackups.keyFor(PageBackups.UNREADABLE, raw) { prefs.getString(it, null) }?.let { edit.putString(it, raw) }
                edit.remove(KEY_LIST).commit()
            }
            DecodeStatus.Newer -> {
                PageBackups.keyFor(PageBackups.SCHEMA_PREFIX + decoded.schema, raw) { prefs.getString(it, null) }
                    ?.let { prefs.edit().putString(it, raw).commit() }
            }
            else -> Unit
        }
        listReadFully = (decoded.status == DecodeStatus.Empty || decoded.status == DecodeStatus.Current) &&
            prefs.all.keys.none { PageBackups.isBackupKey(it) }
        _pages = MutableStateFlow(decoded.list)
        pages = _pages.asStateFlow()
    }

    fun newId(): String = PageIds.newId { random.nextLong() }

    /** Applies [transform] and persists the result if it changed. */
    fun update(transform: (PageList) -> PageList) {
        val current = _pages.value
        val next = PageEdits.normalize(transform(current))
        if (next == current) return
        _pages.value = next
        prefs.edit().putString(KEY_LIST, PageListCodec.encode(next)).apply()
    }

    fun setMode(mode: NavMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        prefs.edit().putString(KEY_MODE, mode.wire).apply()
    }

    private companion object {
        const val PREFS = "pages"
        const val KEY_LIST = "list"
        const val KEY_MODE = "nav_mode"
    }
}

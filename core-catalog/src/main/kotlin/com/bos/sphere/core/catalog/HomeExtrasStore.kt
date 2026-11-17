package com.bos.sphere.core.catalog

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * HOM-02: everything on home that is not a plain app key on a page: folders, shortcut / widget /
 * link definitions, widget spans and the dock list. The page order itself stays in the home layout
 * store (page keys refer to these items by their prefixed keys), so this survives process death
 * next to it. Stored through [HomeStateCodec] with an empty page list.
 */
class HomeExtrasStore(context: Context) {
    private val sp = com.bos.sphere.core.data.TolerantPrefs.of(context.applicationContext, "home_extras")
    /** NFR-18: real widget ids live in the device file; the backed-up file holds re-add markers ([WidgetIdSplit]). */
    private val device = com.bos.sphere.core.data.TolerantPrefs.of(context.applicationContext, com.bos.sphere.core.data.DeviceBound.FILE)
    private val _state = MutableStateFlow(HomeStateCodec.decode(com.bos.sphere.core.data.WidgetIdSplit.getString(sp, device, "home_extras", "extras")).copy(pages = emptyList()))
    val state: StateFlow<HomeState> = _state.asStateFlow()

    fun set(next: HomeState) {
        val v = next.copy(pages = emptyList())
        if (v == _state.value) return
        _state.value = v
        com.bos.sphere.core.data.WidgetIdSplit.putString(sp, device, "home_extras", "extras", HomeStateCodec.encode(v))
    }

    companion object {
        @Volatile private var instance: HomeExtrasStore? = null

        /** HOM-02: one store per process, so the home, the dock and a desktop surface see the same dock list. */
        fun get(context: Context): HomeExtrasStore =
            instance ?: synchronized(this) { instance ?: HomeExtrasStore(context).also { instance = it } }
    }
}

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
    private val sp = context.applicationContext.getSharedPreferences("home_extras", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(HomeStateCodec.decode(sp.getString("extras", null)).copy(pages = emptyList()))
    val state: StateFlow<HomeState> = _state.asStateFlow()

    fun set(next: HomeState) {
        val v = next.copy(pages = emptyList())
        if (v == _state.value) return
        _state.value = v
        sp.edit().putString("extras", HomeStateCodec.encode(v)).apply()
    }
}

package com.bos.sphere.feature.grid

import com.bos.sphere.core.data.SearchCategory

/** The category wheel as a list, so the keyboard can move it (PAL-19): Everything first, then the categories in wheel order. */
object ScopeWheel {
    val entries: List<SearchCategory?> = listOf<SearchCategory?>(null) + SearchCategory.entries

    fun label(c: SearchCategory?): String = c?.label ?: "Everything"

    /** One step along the wheel; it stops at the ends like the wheel itself. */
    fun step(current: SearchCategory?, delta: Int): SearchCategory? =
        entries[(entries.indexOf(current) + delta).coerceIn(0, entries.lastIndex)]
}

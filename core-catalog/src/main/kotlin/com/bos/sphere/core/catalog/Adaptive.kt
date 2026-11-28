package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.GridSpec
import kotlin.math.abs

/**
 * HOM-11: window size classes and the grid derived from them. Each (width class, height class)
 * pair keeps its own arrangement, so rotating, folding or resizing never rewrites another size's
 * layout, and a size never seen before starts from the nearest known one.
 */
enum class SizeBucket { Compact, Medium, Expanded }

data class SizeClass(val width: SizeBucket, val height: SizeBucket) {
    val key: String get() = "${width.name.lowercase()}-${height.name.lowercase()}"
    private val rank: Int get() = width.ordinal * 3 + height.ordinal

    fun distanceTo(o: SizeClass): Int = abs(width.ordinal - o.width.ordinal) * 3 + abs(height.ordinal - o.height.ordinal)

    companion object {
        const val WIDTH_MEDIUM_DP = 600
        const val WIDTH_EXPANDED_DP = 840
        const val HEIGHT_MEDIUM_DP = 480
        const val HEIGHT_EXPANDED_DP = 900

        fun of(widthDp: Int, heightDp: Int) = SizeClass(
            when { widthDp < WIDTH_MEDIUM_DP -> SizeBucket.Compact; widthDp < WIDTH_EXPANDED_DP -> SizeBucket.Medium; else -> SizeBucket.Expanded },
            when { heightDp < HEIGHT_MEDIUM_DP -> SizeBucket.Compact; heightDp < HEIGHT_EXPANDED_DP -> SizeBucket.Medium; else -> SizeBucket.Expanded },
        )
    }
}

object AdaptiveGrid {
    const val CELL_DP = 88f

    /**
     * Columns x rows for an app area of [areaWidthDp] x [areaHeightDp]. [density] is the user's
     * setting: 1 is the default, above 1 packs more (smaller cells), below 1 fewer.
     */
    fun dims(areaWidthDp: Float, areaHeightDp: Float, density: Float = 1f): GridSpec {
        val cell = CELL_DP / density.coerceIn(0.5f, 2f)
        return GridSpec((areaWidthDp / cell).toInt().coerceIn(3, 12), (areaHeightDp / cell).toInt().coerceIn(3, 10))
    }
}

/** One arrangement per size class; unseen classes are seeded from the nearest existing one. */
data class SizeClassArrangements(val byClass: Map<String, HomeState> = emptyMap(), val classes: Map<String, SizeClass> = emptyMap()) {

    fun stateFor(sc: SizeClass): HomeState? =
        byClass[sc.key] ?: classes.values.minByOrNull { it.distanceTo(sc) }?.let { byClass[it.key] }

    /** Records [state] as the arrangement of [sc] only; other classes are untouched. */
    fun with(sc: SizeClass, state: HomeState) = copy(byClass = byClass + (sc.key to state), classes = classes + (sc.key to sc))

    /** The arrangement to show for [sc], seeding (copying) it from the nearest class on first use. */
    fun resolve(sc: SizeClass, fallback: HomeState): Pair<HomeState, SizeClassArrangements> {
        byClass[sc.key]?.let { return it to this }
        val seed = stateFor(sc) ?: fallback
        return seed to with(sc, seed)
    }
}

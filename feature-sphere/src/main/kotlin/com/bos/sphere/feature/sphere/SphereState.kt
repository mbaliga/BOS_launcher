package com.bos.sphere.feature.sphere

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Tunable surface parameters — the prototype's `P` object (Surface defaults from §Appendix).
 * Exposed so `:feature-settings` can drive them live later. Distances are dp.
 */
data class SphereParams(
    val perspective: Float = 600f,
    val radius: Float = 550f,
    val maxRotation: Float = 51f,
    val depth: Float = 1.60f,
    val minScale: Float = 1.00f,
    val arc: Float = 0f,
    val tilt: Float = 12f,
    val focus: Float = 1.85f,
) {
    /** dp travelled per one-column scrub: `radius * sin(STEP_DEG)`. */
    val dragScale: Float
        get() = (radius * Math.sin(SphereGeometry.STEP_R)).toFloat()
}

/** How many rows the equator splits into. Auto resolves from orientation at the call site. */
enum class RowMode { Auto, One, Two }

/**
 * Resolved per-tile transform in the dp domain. The perspective `translateZ` has already been
 * folded into [scale]/[translationX]/[translationY]; [rotationY]/[rotationX] are passed to
 * `graphicsLayer` directly (with `cameraDistance` = perspective) for the card turn.
 */
data class TileTransform(
    val visible: Boolean,
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val rotationX: Float,
    val rotationY: Float,
    val zIndex: Float,
    val focused: Boolean,
)

/**
 * Drives the coverflow. [position] is the continuous scrub position in *column* units; integer
 * values sit a column at the focal point. Owns the math only — animation/gesture wiring lives
 * in the [Coverflow] composable so this stays test-friendly and frame-independent.
 */
@Stable
class SphereState(
    itemCount: Int,
    rows: Int = 1,
    params: SphereParams = SphereParams(),
) {
    var itemCount: Int by mutableStateOf(itemCount)
    var rows: Int by mutableStateOf(rows.coerceIn(1, 2))
    var params: SphereParams by mutableStateOf(params)

    /** Continuous scrub position, in columns. */
    var position: Float by mutableStateOf(0f)

    /** Columns occupied given the current row split. */
    val columnCount: Int
        get() = if (rows == 2) ceil(itemCount / 2.0).toInt() else itemCount

    /** Largest snap index. */
    val maxIndex: Int
        get() = (columnCount - 1).coerceAtLeast(0)

    /** Allowed scrub bounds (the prototype lets you over-scroll ±0.6 for rubber-band feel). */
    val minPosition: Float get() = -0.6f
    val maxPosition: Float get() = maxIndex + 0.6f

    /** Nearest snap target for the current [position]. */
    fun nearestIndex(): Int = position.roundToInt().coerceIn(0, maxIndex)

    /** Maps item index → (row, column) for the current row split. */
    fun rowOf(index: Int): Int = if (rows == 2) index and 1 else 0
    fun columnOf(index: Int): Int = if (rows == 2) index shr 1 else index

    /**
     * Remaps [position] when the row split changes so the focused app stays focused
     * (1→2 rows halves the column index, 2→1 doubles it). Mirrors the prototype's `remapRows`.
     */
    fun updateRows(newRows: Int) {
        val target = newRows.coerceIn(1, 2)
        if (target == rows) return
        position = if (target == 2) position / 2f else position * 2f
        rows = target
        position = position.coerceIn(0f, maxIndex.toFloat())
    }

    /** Computes the transform for item [index] at the current [position]. Pure. */
    fun transformFor(index: Int): TileTransform {
        val p = params
        // Two-row mode shrinks the whole surface (U) and softens the lens (foc), per render().
        val u = if (rows == 2) 0.66 else 1.0
        val foc = if (rows == 2) 1.0 + (p.focus - 1.0) * 0.45 else p.focus.toDouble()

        val row = rowOf(index)
        val col = columnOf(index)
        val o = col - position.toDouble()
        val ao = abs(o)
        val s = sign(o)

        if (ao > SphereGeometry.range + 0.8) {
            return TileTransform(false, 0f, 0f, 1f, 0f, 0f, 0f, false)
        }

        val phi = (o * SphereGeometry.STEP_DEG).coerceIn(-84.0, 84.0) * (Math.PI / 180.0)
        val m = SphereGeometry.magAt(o, foc)

        val rangeD = SphereGeometry.range.toDouble()
        var sc = (1.0 + (p.minScale - 1.0) * (minOf(ao, rangeD) / rangeD)) * m * u
        var tx = SphereGeometry.txAt(o, foc, p.radius.toDouble()) * u
        val tz = -p.radius * (1.0 - cos(phi)) * p.depth

        var ty = p.arc * (1.0 - cos(phi))
        if (rows == 2) ty += (if (row == 1) 1 else -1) * (66.0 * sc + 5.0)

        val rotY = -s * p.maxRotation * SphereGeometry.smooth01(minOf(ao, 1.0))
        val rotX = if (p.arc > 0f) p.tilt * (1.0 - cos(phi)) else 0.0

        // Fold the CSS perspective(translateZ) into a 2D scale/offset (no translationZ in Compose).
        val depthFactor = p.perspective / (p.perspective - tz)
        sc *= depthFactor
        tx *= depthFactor
        ty *= depthFactor

        return TileTransform(
            visible = true,
            translationX = tx.toFloat(),
            translationY = ty.toFloat(),
            scale = sc.toFloat(),
            rotationX = rotX.toFloat(),
            rotationY = rotY.toFloat(),
            zIndex = (2000 + tz).toFloat(),
            focused = ao < 0.5,
        )
    }
}

/** Remembers a [SphereState], keyed so it survives recomposition but tracks item/row changes. */
@Composable
fun rememberSphereState(
    itemCount: Int,
    rows: Int,
    params: SphereParams = SphereParams(),
): SphereState {
    val state = remember { SphereState(itemCount, rows, params) }
    state.itemCount = itemCount
    if (state.rows != rows) state.updateRows(rows)
    state.params = params
    return state
}

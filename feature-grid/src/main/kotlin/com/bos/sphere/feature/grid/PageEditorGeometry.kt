package com.bos.sphere.feature.grid

import kotlin.math.abs
import kotlin.math.sign

/**
 * Wave 36 (pageEditor): the pure maths of the page editor. Plain JVM, no Compose types, so the layout, the swing and the drop hit
 * test are unit tested without a device.
 *
 * The editor is the edit card of wave 8 with a new arrangement (the live page, shrunken, stays the one editable surface):
 *  - Paginated, portrait: the pages are a vertical strip on the END side (right; left in RTL or with the right-to-left order), the live
 *    page in the middle of the strip with its neighbours peeking above and below; the tabs and their content fill the other side.
 *  - Continuous, portrait: the same, turned: the strip runs across the TOP, the tabs and their content go BELOW it.
 *  - Landscape (both modes): the strip stays on the side. DECISION: a horizontal strip would take half of a 360 dp high window.
 */
object PageEditorGeometry {
    /** Wave 37 (editorDebt): the gap between two cards of the strip, nearly edge to edge like the mock-up (was 12 dp). */
    const val GAP_DP = 6f
    const val CAMERA_DISTANCE_DP = 14f
    const val SWING_MS = 350
    const val SWING_REDUCED_MS = 160
    const val SWING_DEGREES = 42f
    const val SWING_START_SCALE = 0.92f

    /** The side strip's card scale: the live card is half the window wide. */
    const val SIDE_SCALE = 0.5f
    /** The share of the free height the top strip (continuous) takes. */
    const val TOP_STRIP_SHARE = 0.45f
    const val TOP_SCALE_MAX = 0.34f

    enum class Axis { Vertical, Horizontal }

    /** The strip is on the right unless the language or the page order is right to left. Either one mirrors it; both together are the home's physical layout again. */
    fun mirrored(layoutRtl: Boolean, orderRtl: Boolean): Boolean = layoutRtl || orderRtl

    fun axis(continuous: Boolean, landscape: Boolean): Axis = if (continuous && !landscape) Axis.Horizontal else Axis.Vertical

    /** The tabs are stacked as titles (like the reference) only beside a side strip in portrait; elsewhere they are one row. */
    fun tabsVertical(continuous: Boolean, landscape: Boolean): Boolean = !continuous && !landscape

    data class Layout(
        /** card = the focus slot (where the live page is drawn), carousel = the strip, addBand = the area for the tabs and their content. */
        val target: EditCardGeometry.Target,
        val axis: Axis,
        /** The strip of cards (clips them). */
        val strip: EdgeRect,
        /** The room beside (or under) the focus card for Move earlier, Move later and Delete page. */
        val rail: EdgeRect,
        /** Distance between the centres of two neighbouring cards along the strip. */
        val pitch: Float,
        val mirrored: Boolean,
    ) {
        val tabs: EdgeRect get() = target.addBand
        val card: EdgeRect get() = target.card
    }

    fun layout(
        widthPx: Float, heightPx: Float, continuous: Boolean, mirrored: Boolean,
        topReservePx: Float, bottomReservePx: Float, railPx: Float, gapPx: Float,
    ): Layout {
        val w = widthPx.coerceAtLeast(1f); val h = heightPx.coerceAtLeast(1f)
        val landscape = w > h
        val ax = axis(continuous, landscape)
        val top = topReservePx.coerceAtLeast(0f)
        val bottom = (h - bottomReservePx).coerceAtLeast(top + 1f)
        val avail = bottom - top
        val side = if (mirrored) CardSide.Left else CardSide.Right
        val card: EdgeRect; val strip: EdgeRect; val rail: EdgeRect; val tabs: EdgeRect; val pitch: Float
        val scale: Float
        if (ax == Axis.Vertical) {
            val byColumn = (0.7f * w - railPx) / (w * (1f - EditCardGeometry.OFF_SCREEN))
            scale = minOf(SIDE_SCALE, avail * 0.64f / h, byColumn).coerceAtLeast(0.2f)
            val cw = w * scale; val ch = h * scale
            val off = cw * EditCardGeometry.OFF_SCREEN
            val left = if (side == CardSide.Right) w - cw + off else -off
            val cardTop = top + (avail - ch) / 2f
            card = EdgeRect(left, cardTop, left + cw, cardTop + ch)
            strip = if (side == CardSide.Right) EdgeRect(left - railPx, top, w, bottom) else EdgeRect(0f, top, card.right + railPx, bottom)
            rail = if (side == CardSide.Right) EdgeRect(left - railPx, cardTop, left, cardTop + ch) else EdgeRect(card.right, cardTop, card.right + railPx, cardTop + ch)
            tabs = if (side == CardSide.Right) EdgeRect(0f, top, strip.left, bottom) else EdgeRect(strip.right, top, w, bottom)
            pitch = ch + gapPx
        } else {
            val stripH = avail * TOP_STRIP_SHARE
            scale = minOf(TOP_SCALE_MAX, (stripH - railPx - gapPx) / h).coerceAtLeast(0.2f)
            val cw = w * scale; val ch = h * scale
            val left = (w - cw) / 2f
            card = EdgeRect(left, top, left + cw, top + ch)
            strip = EdgeRect(0f, top, w, top + ch + railPx)
            rail = EdgeRect(0f, card.bottom, w, card.bottom + railPx)
            tabs = EdgeRect(0f, strip.bottom + gapPx, w, bottom)
            pitch = cw + gapPx
        }
        val tx = card.left + card.width / 2f - w / 2f
        val ty = card.top + card.height / 2f - h / 2f
        val t = EditCardGeometry.Target(scale, tx, ty, card, strip, tabs, side, landscape)
        return Layout(t, ax, strip, rail, pitch, mirrored)
    }

    // ---- the strip's entries ----

    enum class Kind { Glance, Page, Add }
    data class Entry(val kind: Kind, val page: Int = -1)

    /** Glance first (when it is on), then every page, then the Add card. */
    fun entries(glance: Boolean, pageCount: Int): List<Entry> =
        (if (glance) listOf(Entry(Kind.Glance)) else emptyList()) + List(pageCount) { Entry(Kind.Page, it) } + Entry(Kind.Add)

    /** The position in [entries] of the focused card: Glance, or page [current]. */
    fun focusPosition(glance: Boolean, glanceFocus: Boolean, current: Int): Int = if (glance && glanceFocus) 0 else current.coerceAtLeast(0) + (if (glance) 1 else 0)

    /** The offset (px) of the card at [position] from the focus slot, along the strip; mirrored flips a horizontal strip. */
    fun offsetOf(position: Int, focus: Int, layout: Layout): Pair<Float, Float> {
        val d = (position - focus) * layout.pitch
        return if (layout.axis == Axis.Vertical) 0f to d else (if (layout.mirrored) -d else d) to 0f
    }

    // ---- the swing ----

    /** What the live page's layer adds to the card transform while it swings in: a translation from the tapped card's place, a tilt, a little scale. */
    data class Swing(val translationX: Float, val translationY: Float, val rotationX: Float, val rotationY: Float, val scale: Float, val alpha: Float) {
        val atRest: Boolean get() = translationX == 0f && translationY == 0f && rotationX == 0f && rotationY == 0f && scale == 1f && alpha == 1f
    }

    val REST = Swing(0f, 0f, 0f, 0f, 1f, 1f)

    /**
     * [progress] 0..1 (already eased), (dx, dy) the tapped card's offset from the focus slot at the moment of the tap. A card below (above) tilts
     * about the horizontal axis, a card on the side about the vertical one; at 1 the layer is exactly the card. With reduced motion there is
     * no movement and no tilt: the page cross-fades in place.
     */
    fun swing(progress: Float, dx: Float, dy: Float, reduced: Boolean): Swing {
        val p = progress.coerceIn(0f, 1f)
        if (p >= 1f) return REST
        if (reduced) return Swing(0f, 0f, 0f, 0f, 1f, p)
        val inv = 1f - p
        return Swing(
            translationX = dx * inv, translationY = dy * inv,
            rotationX = -sign(dy) * SWING_DEGREES * inv, rotationY = sign(dx) * SWING_DEGREES * inv,
            scale = 1f - (1f - SWING_START_SCALE) * inv, alpha = 1f,
        )
    }

    // ---- dropping an app on a card ----

    data class DropTarget(val page: Int, val rect: EdgeRect)

    /** The part of [r] that is inside [clip] (the strip clips the peeking cards), or null when nothing of it is visible. */
    fun visible(r: EdgeRect, clip: EdgeRect): EdgeRect? {
        val l = maxOf(r.left, clip.left); val t = maxOf(r.top, clip.top); val rr = minOf(r.right, clip.right); val b = minOf(r.bottom, clip.bottom)
        return if (rr > l && b > t) EdgeRect(l, t, rr, b) else null
    }

    /** The page under the point (x, y), or null (a drop anywhere else does nothing). */
    fun hit(targets: List<DropTarget>, x: Float, y: Float): Int? =
        targets.firstOrNull { x >= it.rect.left && x <= it.rect.right && y >= it.rect.top && y <= it.rect.bottom }?.page

    /** A soft tint per page (hue degrees), so the cards read as distinct pages. */
    fun hue(index: Int): Float = HUES[abs(index) % HUES.size]
    private val HUES = floatArrayOf(350f, 18f, 150f, 205f, 265f, 40f)

    // ---- Continuous: scroll the surface to a page (wave 37, editorDebt) ----

    /**
     * The first column of every page in the packed scroll surface (columns fill page after page, so a page starts at the column of its first
     * item that is on the surface). A page with nothing on the surface (empty, or only items that are gone) starts where the next page with
     * items does; trailing empty pages start at the last column.
     */
    fun pageStartColumns(pageKeys: List<List<String>>, cells: List<com.bos.sphere.core.catalog.ColumnFill.Cell>, columns: Int): List<Int> {
        val colOf = HashMap<String, Int>(); cells.forEach { colOf.putIfAbsent(it.key, it.col) }
        val own = pageKeys.map { ks -> ks.mapNotNull { colOf[it] }.minOrNull() }
        val last = (columns - 1).coerceAtLeast(0)
        val out = IntArray(pageKeys.size)
        var next = last
        for (i in pageKeys.indices.reversed()) { next = own[i] ?: next; out[i] = next }
        return out.toList()
    }

    /** The scroll offset (px) that puts column [col] at the start edge of a surface that scrolls [maxPx]; the right edge when [rtl] (columns fill from the right). */
    fun scrollForColumn(col: Int, cellPx: Float, maxPx: Int, rtl: Boolean): Int {
        val o = (col * cellPx).toInt().coerceIn(0, maxPx.coerceAtLeast(0))
        return if (rtl) (maxPx - o).coerceIn(0, maxPx.coerceAtLeast(0)) else o
    }

    /**
     * The page the surface shows after the user scrolled it: the last page that starts at or before the column at the start edge. The selected
     * page is kept when the surface is where it would put it (including the clamped position of pages near the end that cannot reach the start
     * edge, and pages that share a column with the next one).
     */
    fun pageInView(starts: List<Int>, scrollPx: Int, maxPx: Int, cellPx: Float, rtl: Boolean, selected: Int): Int {
        if (starts.isEmpty()) return 0
        val sel = selected.coerceIn(0, starts.lastIndex)
        if (kotlin.math.abs(scrollPx - scrollForColumn(starts[sel], cellPx, maxPx, rtl)) < cellPx / 2f) return sel
        val offset = if (rtl) maxPx - scrollPx else scrollPx
        val col = kotlin.math.floor(offset / cellPx.coerceAtLeast(1f) + 0.5f).toInt()
        val at = starts.indexOfLast { it <= col }.coerceAtLeast(0)
        // pages sharing that column: the one the user was on if it is one of them, else the first of them
        return if (starts[sel] == starts[at]) sel else starts.indexOfFirst { it == starts[at] }
    }
}

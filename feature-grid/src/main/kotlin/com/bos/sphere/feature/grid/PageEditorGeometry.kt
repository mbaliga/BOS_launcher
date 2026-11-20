package com.bos.sphere.feature.grid

import kotlin.math.abs
import kotlin.math.sign

/**
 * Wave 36 (pageEditor), redesigned in wave 45 (pageEditorLayout): the pure maths of the page editor. Plain JVM, no Compose types, so the
 * layout, the swing, the reorder and the drop hit test are unit tested without a device.
 *
 * The live page (the home's own layer, shrunken) is the one editable surface and is NEVER cropped: it is centred, fully inside the window.
 *  - Portrait (narrower than 600 dp): top to bottom a 56 dp slot for the edit chrome's top bar, a HORIZONTAL strip of small page cards, the
 *    live page, the tab row, and the tab content in a fixed-height panel (about 30 percent of the window), so the live card keeps its size.
 *  - Landscape, or 600 dp wide and more: the strip is VERTICAL at the end side; the live page sits beside it and the tabs with their content
 *    take the remaining start side.
 * Continuous and Paginated share this layout.
 */
object PageEditorGeometry {
    /** The gap between two cards of the strip. */
    const val GAP_DP = 8f
    /** A strip card at its default size (focused: scale 1.0; neighbours 0.9 and dimmer). */
    const val CARD_W_DP = 64f
    const val CARD_H_DP = 112f
    const val NEIGHBOUR_SCALE = 0.9f
    const val NEIGHBOUR_ALPHA = 0.7f
    /** Room around the strip's cards (room for the 2 dp outline and the neighbours' shrink). */
    const val STRIP_PAD_DP = 8f
    /** The slot the edit chrome's top bar (w45-editChrome) fills, below the status bar inset. */
    const val TOP_BAR_DP = 56f
    /**
     * The room the edit chrome's bottom tray / the page editor's own bar takes at the foot of the window. INTEGRATION POINT: until the chrome's top bar replaces
     * the bar's four pills this stays at the pills' height; set it to 0 when they are gone and the live card grows by that much.
     */
    const val BOTTOM_BAR_DP = 68f
    const val TAB_ROW_DP = 48f
    /** The tab row grows with the font scale up to this factor (the tabs scroll sideways beyond it). */
    const val TAB_FONT_MAX = 1.5f
    /** The tab content panel's share of the window height (portrait). */
    const val CONTENT_SHARE = 0.30f
    const val CONTENT_MIN_DP = 120f
    const val CONTENT_FLOOR_DP = 80f
    const val LIVE_SCALE_MAX = 0.88f
    /** The live card should not drop below this share of the window (the content panel gives way first), unless the window is tiny. */
    const val LIVE_SCALE_WISH = 0.38f
    const val LIVE_SCALE_FLOOR = 0.2f
    const val SIDE_MIN_DP = 600f
    const val SIDE_LIVE_SHARE = 0.45f
    const val SIDE_TABS_MIN_DP = 260f
    const val MARGIN_DP = 16f

    const val CAMERA_DISTANCE_DP = 14f
    const val SWING_MS = 350
    const val SWING_REDUCED_MS = 160
    const val SWING_DEGREES = 42f
    const val SWING_START_SCALE = 0.92f

    enum class Axis { Vertical, Horizontal }

    /** The strip is on the right (side mode) or reads left to right unless the language or the page order is right to left. Either one mirrors it. */
    fun mirrored(layoutRtl: Boolean, orderRtl: Boolean): Boolean = layoutRtl || orderRtl

    /** The strip is a column at the end side in landscape and in windows 600 dp wide or more; in portrait phones it is a row. */
    fun sideMode(widthPx: Float, heightPx: Float, density: Float): Boolean = widthPx > heightPx || widthPx / density.coerceAtLeast(0.1f) >= SIDE_MIN_DP

    fun axis(sideMode: Boolean): Axis = if (sideMode) Axis.Vertical else Axis.Horizontal

    data class Layout(
        /** card = the live page's slot, carousel = the strip, addBand = the tab row plus its content. */
        val target: EditCardGeometry.Target,
        val axis: Axis,
        /** True for the vertical strip beside the live card (landscape, tablets). */
        val side: Boolean,
        /** The slot the edit chrome's top bar fills. */
        val topBar: EdgeRect,
        /** The strip of cards (clips them). */
        val strip: EdgeRect,
        val tabRow: EdgeRect,
        val content: EdgeRect,
        /** The focused strip card's size (px). */
        val cardW: Float,
        val cardH: Float,
        /** Distance between the centres of two neighbouring cards along the strip. */
        val pitch: Float,
        val mirrored: Boolean,
        /** The centre of the strip's focus slot (the focused card sits there). */
        val slotX: Float,
        val slotY: Float,
        /** Where the live page's swing starts, as a scale: the strip card's size relative to the live card. */
        val swingFrom: Float,
    ) {
        val tabs: EdgeRect get() = target.addBand
        val card: EdgeRect get() = target.card
    }

    /**
     * [topInsetPx] / [bottomInsetPx] are the system bars' insets; [fontScale] grows the tab row. The live card is centred, never wider or taller than the room
     * it is given, so it is always completely inside the window.
     */
    fun layout(
        widthPx: Float, heightPx: Float, density: Float, fontScale: Float, mirrored: Boolean,
        topInsetPx: Float, bottomInsetPx: Float, topBarDp: Float = TOP_BAR_DP, bottomBarDp: Float = BOTTOM_BAR_DP,
    ): Layout {
        val w = widthPx.coerceAtLeast(1f); val h = heightPx.coerceAtLeast(1f)
        val dp = density.coerceAtLeast(0.1f)
        val side = sideMode(w, h, dp)
        val landscape = w > h
        val sf = (h / dp / 740f).coerceIn(0.8f, 1f)
        val cW = CARD_W_DP * dp * sf; val cH = CARD_H_DP * dp * sf
        val gap = GAP_DP * dp; val pad = STRIP_PAD_DP * dp; val margin = MARGIN_DP * dp
        val tabH = TAB_ROW_DP * dp * fontScale.coerceIn(1f, TAB_FONT_MAX)
        val topBar = EdgeRect(0f, topInsetPx.coerceAtLeast(0f), w, topInsetPx.coerceAtLeast(0f) + topBarDp * dp)
        val limit = (h - bottomInsetPx.coerceAtLeast(0f) - bottomBarDp * dp).coerceAtLeast(topBar.bottom + 1f)
        val card: EdgeRect; val strip: EdgeRect; val tabRow: EdgeRect; val content: EdgeRect
        val scale: Float; val pitch: Float; val slotX: Float; val slotY: Float
        if (!side) {
            strip = EdgeRect(0f, topBar.bottom, w, topBar.bottom + cH + 2 * pad)
            val free = limit - strip.bottom - tabH - 2 * gap
            var contentH = minOf(CONTENT_SHARE * h, free - LIVE_SCALE_WISH * h).coerceAtLeast(CONTENT_MIN_DP * dp)
            if (free - contentH < LIVE_SCALE_FLOOR * h) contentH = (free - LIVE_SCALE_FLOOR * h).coerceAtLeast(CONTENT_FLOOR_DP * dp)
            contentH = contentH.coerceAtMost((free - 1f).coerceAtLeast(1f))
            val avail = (free - contentH).coerceAtLeast(1f)
            scale = minOf(LIVE_SCALE_MAX, avail / h, (w - 2 * margin) / w).coerceAtLeast(0.05f)
            val cw = w * scale; val ch = h * scale
            val left = (w - cw) / 2f
            val top = strip.bottom + gap + (avail - ch) / 2f
            card = EdgeRect(left, top, left + cw, top + ch)
            val tabsTop = strip.bottom + gap + avail + gap
            tabRow = EdgeRect(margin / 2f, tabsTop, w - margin / 2f, tabsTop + tabH)
            content = EdgeRect(0f, tabRow.bottom, w, tabRow.bottom + contentH)
            pitch = cW + gap; slotX = w / 2f; slotY = (strip.top + strip.bottom) / 2f
        } else {
            val stripW = cW + 2 * pad
            strip = if (mirrored) EdgeRect(0f, topBar.bottom, stripW, limit) else EdgeRect(w - stripW, topBar.bottom, w, limit)
            val availH = limit - topBar.bottom
            val byWidth = (w - stripW - 3 * margin - SIDE_TABS_MIN_DP * dp) / w
            scale = minOf(LIVE_SCALE_MAX, (availH - 2 * gap) / h, SIDE_LIVE_SHARE, byWidth).coerceAtLeast(0.1f)
            val cw = w * scale; val ch = h * scale
            val left = if (mirrored) strip.right + margin else strip.left - margin - cw
            val top = topBar.bottom + (availH - ch) / 2f
            card = EdgeRect(left, top, left + cw, top + ch)
            val colL = if (mirrored) card.right + margin else margin
            val colR = if (mirrored) w - margin else card.left - margin
            tabRow = EdgeRect(colL, topBar.bottom, colR.coerceAtLeast(colL + 1f), topBar.bottom + tabH)
            content = EdgeRect(colL, tabRow.bottom, tabRow.right, limit.coerceAtLeast(tabRow.bottom + 1f))
            pitch = cH + gap; slotX = (strip.left + strip.right) / 2f; slotY = (strip.top + strip.bottom) / 2f
        }
        val addBand = EdgeRect(tabRow.left.coerceAtMost(content.left), tabRow.top, maxOf(tabRow.right, content.right), content.bottom)
        val tx = card.left + card.width / 2f - w / 2f
        val ty = card.top + card.height / 2f - h / 2f
        val t = EditCardGeometry.Target(scale, tx, ty, card, strip, addBand, if (mirrored) CardSide.Right else CardSide.Left, landscape)
        val from = (cW / card.width.coerceAtLeast(1f)).coerceIn(0.15f, SWING_START_SCALE)
        return Layout(t, axis(side), side, topBar, strip, tabRow, content, cW, cH, pitch, mirrored, slotX, slotY, from)
    }

    // ---- the strip's entries ----

    enum class Kind { Glance, Page, Add }
    data class Entry(val kind: Kind, val page: Int = -1)

    /** Glance first (when it is on), then every page, then the Add card. */
    fun entries(glance: Boolean, pageCount: Int): List<Entry> =
        (if (glance) listOf(Entry(Kind.Glance)) else emptyList()) + List(pageCount) { Entry(Kind.Page, it) } + Entry(Kind.Add)

    /** The position in [entries] of the focused card: Glance, or page [current]. */
    fun focusPosition(glance: Boolean, glanceFocus: Boolean, current: Int): Int = if (glance && glanceFocus) 0 else current.coerceAtLeast(0) + (if (glance) 1 else 0)

    /**
     * The offset (px) of the strip card at [position] from the live card's centre (what the swing starts from): the focus slot's centre, moved along the
     * strip by the cards between; a mirrored horizontal strip runs the other way.
     */
    fun offsetOf(position: Int, focus: Int, layout: Layout): Pair<Float, Float> {
        val d = (position - focus) * layout.pitch
        val cx = layout.card.left + layout.card.width / 2f; val cy = layout.card.top + layout.card.height / 2f
        return if (layout.axis == Axis.Vertical) (layout.slotX - cx) to (layout.slotY + d - cy)
        else (layout.slotX + (if (layout.mirrored) -d else d) - cx) to (layout.slotY - cy)
    }

    // ---- reordering the strip (drag, keys, actions) ----

    /**
     * The page a dragged card lands on: [from] moved by how many card pitches the finger travelled ([delta] px along the strip, in READING direction: positive
     * = later pages), rounded to the nearest slot and kept inside 0 until [count].
     */
    fun dropIndex(from: Int, delta: Float, pitch: Float, count: Int): Int =
        (from + kotlin.math.floor(delta / pitch.coerceAtLeast(1f) + 0.5f).toInt()).coerceIn(0, (count - 1).coerceAtLeast(0))

    /** The gap (0 until count) where the insertion line is drawn while card [from] is dragged over slot [to]: after the card it lands behind, before the card it lands before. */
    fun insertionGap(from: Int, to: Int): Int = if (to > from) to + 1 else to

    /** The page [from] moves to for an arrow key with Shift (or a "Move earlier / later" action): [step] -1 / +1, or null at the ends. */
    fun stepTarget(from: Int, step: Int, count: Int): Int? = (from + step).takeIf { it in 0 until count && it != from }

    /** The reading-direction step of an arrow: along a horizontal strip, Right is later unless the strip is mirrored; along a vertical one, Down is later. */
    fun stepOf(horizontalStrip: Boolean, mirrored: Boolean, right: Boolean, down: Boolean): Int =
        if (horizontalStrip) (if (right) 1 else -1) * (if (mirrored) -1 else 1) else (if (down) 1 else -1)

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
    fun swing(progress: Float, dx: Float, dy: Float, reduced: Boolean, startScale: Float = SWING_START_SCALE): Swing {
        val p = progress.coerceIn(0f, 1f)
        if (p >= 1f) return REST
        if (reduced) return Swing(0f, 0f, 0f, 0f, 1f, p)
        val inv = 1f - p
        // The tilt is about the axis the card travels across: a card above or below tilts about the horizontal axis, one at the side about the vertical one (the larger offset wins).
        val vertical = abs(dy) >= abs(dx)
        return Swing(
            translationX = dx * inv, translationY = dy * inv,
            rotationX = if (vertical) -sign(dy) * SWING_DEGREES * inv else 0f, rotationY = if (vertical) 0f else sign(dx) * SWING_DEGREES * inv,
            scale = 1f - (1f - startScale) * inv, alpha = 1f,
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

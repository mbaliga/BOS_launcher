package com.bos.sphere.core.input

import kotlin.math.abs

/** Four arrow directions (INP-01). */
enum class FocusDir { Up, Down, Left, Right }

/** Plain rectangle so the graph stays JVM-testable (no Compose types). */
data class FRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val cx get() = (left + right) / 2f
    val cy get() = (top + bottom) / 2f
    val width get() = right - left
    val height get() = bottom - top
}

/** What a node is, so each home style can order its own and corners and the puck join the same graph. */
enum class FocusKind { Item, Category, Corner, PuckRing, PuckHub, PuckSpoke, Control }

/**
 * One interactive element. [layer] is the surface it belongs to (home 0, drawer, palette, puck ...
 * higher on top): only the highest layer present can take focus. [order] is an explicit linear
 * position (XMB, DSi, puck) used for Tab and for the accessible traversal order (NFR-13); when it is
 * null the node falls back to reading order by position.
 */
data class FocusNode(
    val id: String,
    val rect: FRect,
    val layer: Int = 0,
    val kind: FocusKind = FocusKind.Item,
    val order: Int? = null,
    /** Explicit neighbours that override geometry, e.g. XMB wraps, corner to nearest edge item. */
    val links: Map<FocusDir, String> = emptyMap(),
)

/**
 * Pure focus navigation: arrows pick the nearest node in that direction (beam first, like a
 * directional pad), Tab walks the linear order. Same input always gives the same answer, which is
 * what makes the order predictable.
 */
object FocusGraph {
    fun activeNodes(all: Collection<FocusNode>): List<FocusNode> {
        val top = all.maxOfOrNull { it.layer } ?: return emptyList()
        return all.filter { it.layer == top }
    }

    /** Reading order: rows top to bottom (nodes within half a node height are one row), then left to right. */
    fun readingOrder(nodes: List<FocusNode>): List<FocusNode> {
        if (nodes.isEmpty()) return nodes
        val sorted = nodes.sortedWith(compareBy({ it.rect.cy }, { it.rect.cx }, { it.id }))
        val rows = mutableListOf<MutableList<FocusNode>>()
        for (n in sorted) {
            val row = rows.lastOrNull()
            val tol = n.rect.height * 0.5f
            if (row != null && abs(row.first().rect.cy - n.rect.cy) <= tol) row += n else rows += mutableListOf(n)
        }
        return rows.flatMap { r -> r.sortedWith(compareBy({ it.rect.cx }, { it.id })) }
    }

    /**
     * The linear order used by Tab and by accessibility traversal: explicit [FocusNode.order] first
     * (ascending), then the home items in reading order, then corners clockwise from top-left.
     */
    fun linearOrder(nodes: List<FocusNode>): List<FocusNode> {
        val ordered = nodes.filter { it.order != null }.sortedWith(compareBy({ it.order }, { it.id }))
        val rest = nodes.filter { it.order == null }
        val corners = rest.filter { it.kind == FocusKind.Corner }
            .sortedBy { cornerRank(it, nodes) }
        val others = readingOrder(rest.filter { it.kind != FocusKind.Corner })
        return ordered + others + corners
    }

    private fun cornerRank(n: FocusNode, all: List<FocusNode>): Int {
        val maxX = all.maxOf { it.rect.right }; val maxY = all.maxOf { it.rect.bottom }
        val right = n.rect.cx > maxX / 2f; val bottom = n.rect.cy > maxY / 2f
        return when { !right && !bottom -> 0; right && !bottom -> 1; right && bottom -> 2; else -> 3 }
    }

    /** Where focus goes when nothing in the active layer has it: the first node of the linear order. */
    fun entry(all: Collection<FocusNode>): FocusNode? = linearOrder(activeNodes(all)).firstOrNull { it.kind != FocusKind.Corner }
        ?: linearOrder(activeNodes(all)).firstOrNull()

    fun tab(all: Collection<FocusNode>, current: String?, backwards: Boolean, wrap: Boolean = true): FocusNode? {
        val order = linearOrder(activeNodes(all))
        if (order.isEmpty()) return null
        val i = order.indexOfFirst { it.id == current }
        if (i < 0) return if (backwards) order.last() else order.first()
        val j = if (backwards) i - 1 else i + 1
        return when {
            j in order.indices -> order[j]
            wrap -> order[Math.floorMod(j, order.size)]
            else -> null
        }
    }

    /** The node an arrow key reaches from [current], or null when there is nothing that way. */
    fun move(all: Collection<FocusNode>, current: String?, dir: FocusDir): FocusNode? {
        val nodes = activeNodes(all)
        val cur = nodes.firstOrNull { it.id == current } ?: return entry(all)
        cur.links[dir]?.let { id -> nodes.firstOrNull { it.id == id }?.let { return it } }
        val candidates = nodes.filter { it.id != cur.id && beyond(cur.rect, it.rect, dir) }
        if (candidates.isEmpty()) return null
        val beam = candidates.filter { inBeam(cur.rect, it.rect, dir) }
        val pool = beam.ifEmpty { candidates }
        return pool.minWithOrNull(compareBy<FocusNode>({ score(cur.rect, it.rect, dir) }, { it.id }))
    }

    private fun beyond(a: FRect, b: FRect, d: FocusDir): Boolean = when (d) {
        FocusDir.Left -> b.cx < a.cx - 0.5f
        FocusDir.Right -> b.cx > a.cx + 0.5f
        FocusDir.Up -> b.cy < a.cy - 0.5f
        FocusDir.Down -> b.cy > a.cy + 0.5f
    }

    private fun inBeam(a: FRect, b: FRect, d: FocusDir): Boolean = when (d) {
        FocusDir.Left, FocusDir.Right -> b.bottom > a.top && b.top < a.bottom
        FocusDir.Up, FocusDir.Down -> b.right > a.left && b.left < a.right
    }

    private fun score(a: FRect, b: FRect, d: FocusDir): Float {
        val major: Float; val minor: Float
        when (d) {
            FocusDir.Left, FocusDir.Right -> { major = abs(b.cx - a.cx); minor = abs(b.cy - a.cy) }
            FocusDir.Up, FocusDir.Down -> { major = abs(b.cy - a.cy); minor = abs(b.cx - a.cx) }
        }
        return 13f * major * major + minor * minor
    }

    /** Which nodes cannot be reached by any sequence of arrows or Tab. Empty means the graph is sound (INP-01). */
    fun unreachable(all: Collection<FocusNode>): List<String> {
        val nodes = activeNodes(all)
        if (nodes.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        val queue = ArrayDeque<FocusNode>()
        entry(all)?.let { seen += it.id; queue += it }
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val next = FocusDir.entries.mapNotNull { move(all, n.id, it) } + listOfNotNull(tab(all, n.id, false, wrap = false))
            for (m in next) if (seen.add(m.id)) queue += m
        }
        return nodes.map { it.id }.filter { it !in seen }
    }
}

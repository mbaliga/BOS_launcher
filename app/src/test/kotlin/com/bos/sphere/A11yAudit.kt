package com.bos.sphere

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * NFR-13 / NFR-14 (wave 23): a pure walk over a Compose semantics tree, the same tree TalkBack reads. Every node that can be clicked must have
 * a non-empty label (content description or text), a role, and a touch area of at least 48 dp both ways, unless a documented [Exception] says why
 * not. Controls that show a state (switch, checkbox, radio, tab) must expose it. Pure JVM logic on a tree: the tour test feeds it the real screens
 * under Robolectric. It cannot say how TalkBack speaks the result or in which order a real device walks it.
 */
object A11yAudit {
    /** A documented exception: a node matching [matches] is exempt from the named [rules] ("label", "role", "size", "state"), for [why]. */
    class Exception(val name: String, val rules: Set<String>, val why: String, val matches: (SemanticsNode) -> Boolean)

    class Finding(val surface: String, val rule: String, val node: SemanticsNode, val detail: String) {
        private val where = describe(node) // read now: the node's layout changes with the next rotation
        override fun toString() = "[$surface] $rule: $detail ($where)"
    }

    fun describe(n: SemanticsNode): String {
        val b = n.boundsInRoot
        return "id=${n.id} tag=${n.config.getOrNull(SemanticsProperties.TestTag)} text=${labelOf(n).take(30)} role=${n.config.getOrNull(SemanticsProperties.Role)} " +
            "bounds=${b.left.toInt()},${b.top.toInt()},${b.width.toInt()}x${b.height.toInt()}"
    }

    fun labelOf(n: SemanticsNode): String {
        val cd = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        val tx = n.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        return (cd + " " + tx).trim()
    }

    fun all(root: SemanticsNode): Sequence<SemanticsNode> = sequence { yield(root); for (c in root.children) yieldAll(all(c)) }

    private val stateRoles = setOf(Role.Switch, Role.Checkbox, Role.RadioButton, Role.Tab)

    /** Findings for the merged tree under [roots]; [density] converts pixels to dp. Nodes that are hidden or fully outside the window are skipped. */
    fun audit(surface: String, roots: List<SemanticsNode>, density: Float, exceptions: List<Exception>): List<Finding> {
        val out = ArrayList<Finding>()
        for (root in roots) {
            val window = root.boundsInRoot
            for (n in all(root)) {
                if (n.config.contains(SemanticsProperties.InvisibleToUser)) continue
                val b = n.boundsInRoot
                if (b.width <= 0f || b.height <= 0f) continue
                if (b.right <= window.left || b.left >= window.right || b.bottom <= window.top || b.top >= window.bottom) continue // off screen, not reachable
                val clickable = n.config.contains(SemanticsActions.OnClick)
                val role = n.config.getOrNull(SemanticsProperties.Role)
                if (!clickable && role !in stateRoles) continue
                val skip = exceptions.filter { it.matches(n) }.flatMap { it.rules }.toSet()
                fun fail(rule: String, detail: String) { if (rule !in skip) out += Finding(surface, rule, n, detail) }
                if (labelOf(n).isBlank()) fail("label", "no label")
                if (clickable && role == null) fail("role", "clickable with no role")
                val touch = n.touchBoundsInRoot
                val w = maxOf(b.width, touch.width) / density
                val h = maxOf(b.height, touch.height) / density
                // A node cut by a scroll viewport (its drawn bounds are smaller than its layout size) is judged whole when it scrolls fully into view.
                val clipped = b.width < n.size.width - 1f || b.height < n.size.height - 1f
                if (clickable && !clipped && (w < 47.5f || h < 47.5f)) fail("size", "touch area %.0fx%.0f dp".format(w, h))
                if (role in stateRoles && n.config.getOrNull(SemanticsProperties.ToggleableState) == null && n.config.getOrNull(SemanticsProperties.Selected) == null) fail("state", "$role exposes no state")
            }
        }
        return out
    }

    /**
     * Information, not a failure: clickable nodes whose own layout is under 48 dp either way. Compose widens the hit-test area of such a node to 48 dp
     * (the touch area checked above, which is why they pass), but the drawn control stays small and two neighbours can share the widened area.
     */
    fun smallLayouts(surface: String, roots: List<SemanticsNode>, density: Float): List<String> = roots.flatMap { r ->
        all(r).filter { n -> n.config.contains(SemanticsActions.OnClick) && n.boundsInRoot.let { it.width > 0f && it.height > 0f } &&
            n.size.width >= n.boundsInRoot.width - 1f && n.size.height >= n.boundsInRoot.height - 1f && (n.size.width / density < 47.5f || n.size.height / density < 47.5f) }
            .map { "[$surface] ${describe(it)} layout %.0fx%.0f dp".format(it.size.width / density, it.size.height / density) }.toList()
    }

    fun hasHeading(roots: List<SemanticsNode>) = roots.any { r -> all(r).any { it.config.contains(SemanticsProperties.Heading) } }
    fun hasPaneTitle(roots: List<SemanticsNode>) = roots.any { r -> all(r).any { it.config.contains(SemanticsProperties.PaneTitle) } }
}

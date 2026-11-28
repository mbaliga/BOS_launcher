package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.HomeLayoutMath
import com.bos.sphere.core.data.PageLayout

/**
 * HOM-14: widget stacks on the home. A stack is a widget-kind item (`widget:stack-<id>`) that takes one slot
 * on a page. Its [ItemDef.target] holds the active index and the member widget keys (`active;k1,k2`); the
 * members keep their own definitions and spans but are not on any page, so the page resolver must treat
 * them as held ([members]). Pure functions from state to state; the live home draws a stack with
 * `WidgetStackView` and switches by swipe, wheel or keys.
 */
object WidgetStacks {
    const val PREFIX = ItemKeys.WIDGET + "stack-"
    private const val MAX = 6

    fun isStack(key: String) = key.startsWith(PREFIX)

    fun stackKey(id: String) = PREFIX + id

    fun stackOf(def: ItemDef?): WidgetStack {
        val t = def?.target.orEmpty()
        val at = t.indexOf(';')
        if (at < 0) return WidgetStack(emptyList())
        val keys = t.substring(at + 1).split(',').filter { it.isNotEmpty() }
        return WidgetStack(keys, (t.substring(0, at).toIntOrNull() ?: 0).coerceIn(0, (keys.size - 1).coerceAtLeast(0)))
    }

    fun encode(stack: WidgetStack) = "${stack.active};${stack.keys.joinToString(",")}"

    fun stackAt(state: HomeState, key: String): WidgetStack? = if (isStack(key)) state.items[key]?.let(::stackOf) else null

    /** Every widget key that lives inside some stack (these are never drawn as page items). */
    fun members(state: HomeState): Set<String> =
        state.items.values.filter { isStack(it.key) }.flatMap { stackOf(it).keys }.toSet()

    /** Stack the widget [b] onto the slot of [a] (a becomes the first member). Both must be widgets on a page. */
    fun create(state: HomeState, a: String, b: String, id: String): HomeState {
        if (a == b || isStack(b) || ItemKeys.kind(a) != ItemKind.Widget || ItemKeys.kind(b) != ItemKind.Widget) return state
        if (HomeLayoutMath.pageOf(state.pages, b) < 0) return state
        val key = stackKey(id)
        if (key in state.items) return state
        val aMembers = if (isStack(a)) (stackAt(state, a)?.keys ?: return state) else listOf(a)
        if (aMembers.size + 1 > MAX || HomeLayoutMath.pageOf(state.pages, a) < 0) return state
        if (isStack(a)) return add(state, a, b)
        val stack = WidgetStack(listOf(a, b), active = 1)
        val pages = state.pages.map { p -> p.copy(keys = p.keys.filter { it != b }.map { if (it == a) key else it }) }
        return state.copy(
            pages = pages,
            items = state.items + (key to ItemDef(key, ItemKind.Widget, state.items[a]?.label ?: "Stack", encode(stack))),
            widgets = state.widgets + (key to state.spanOf(a)),
        )
    }

    /** Add the page widget [w] to an existing stack. */
    fun add(state: HomeState, stackKey: String, w: String): HomeState {
        val st = stackAt(state, stackKey) ?: return state
        if (w in st.keys || isStack(w) || ItemKeys.kind(w) != ItemKind.Widget || st.keys.size >= MAX) return state
        if (HomeLayoutMath.pageOf(state.pages, w) < 0) return state
        val pages = state.pages.map { p -> p.copy(keys = p.keys.filter { it != w }) }
        return state.copy(pages = pages, items = state.items + (stackKey to state.items.getValue(stackKey).copy(target = encode(st.add(w)))))
    }

    fun setActive(state: HomeState, stackKey: String, stack: WidgetStack): HomeState {
        val def = state.items[stackKey] ?: return state
        if (stackOf(def) == stack) return state
        return state.copy(items = state.items + (stackKey to def.copy(target = encode(stack))))
    }

    /**
     * Take [member] out of the stack onto [page] just after the stack. A stack left with one widget is
     * dissolved into that widget, at the stack's place.
     */
    fun takeOut(state: HomeState, stackKey: String, member: String, page: Int, cap: (PageLayout) -> Int): HomeState {
        val st = stackAt(state, stackKey) ?: return state
        if (member !in st.keys) return state
        val left = st.remove(member)
        val at = HomeLayoutMath.pageOf(state.pages, stackKey)
        val idx = if (at >= 0) state.pages[at].keys.indexOf(stackKey) + 1 else 0
        var s = state
        s = if (left.keys.size <= 1) {
            val only = left.keys.firstOrNull()
            val pages = s.pages.map { p -> p.copy(keys = p.keys.mapNotNull { if (it == stackKey) only else it }) }
            s.copy(pages = pages, items = s.items - stackKey, widgets = s.widgets - stackKey)
        } else s.copy(items = s.items + (stackKey to s.items.getValue(stackKey).copy(target = encode(left))))
        val target = if (at >= 0) at else page.coerceIn(0, (s.pages.size - 1).coerceAtLeast(0))
        val placed = HomeLayoutMath.add(s.pages, member, target, cap)
        return s.copy(pages = HomeLayoutMath.move(placed, member, target.coerceIn(0, placed.lastIndex), idx, cap))
    }

    /** Remove the stack and every widget inside it; returns the state and the member keys that were dropped. */
    fun removeAll(state: HomeState, stackKey: String, cap: (PageLayout) -> Int): Pair<HomeState, List<String>> {
        val st = stackAt(state, stackKey) ?: return state to emptyList()
        var s = HomeEdit.remove(state, stackKey, cap)
        st.keys.forEach { s = s.copy(items = s.items - it, widgets = s.widgets - it) }
        return s to st.keys
    }
}

package com.bos.sphere.core.catalog

import com.bos.sphere.core.data.PageLayout

/**
 * WSP-05: an app group placed on home is a tile (`group:<id>`) that points at a saved group. The dock panel owns the
 * groups, so the tiles follow it: a group that is deleted loses its tile everywhere (page, folder), and a group that is
 * renamed renames its tile. Pure, so it is tested without the dock; the app applies it whenever the saved groups change.
 */
object GroupTiles {
    /** [groups] is every saved group as (id, name). Returns [state] itself when nothing needs to change. */
    fun reconcile(state: HomeState, groups: List<Pair<String, String>>, cap: (PageLayout) -> Int): HomeState {
        val names = groups.toMap()
        var s = state
        for ((key, def) in state.items) {
            if (def.kind != ItemKind.Group) continue
            val id = ItemKeys.groupId(key) ?: continue
            val name = names[id]
            if (name == null) {
                s = HomeEdit.remove(s, key, cap)
            } else if (def.label != name) {
                s = s.copy(items = s.items + (key to def.copy(label = name)))
            }
        }
        return s
    }
}

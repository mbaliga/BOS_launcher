package com.bos.sphere.ui

import com.bos.sphere.core.design.GrantGateLogic
import com.bos.sphere.core.design.GrantKind

/** CFG-07: the rows of the "Access" pane. Pure, so the wording and the stage per row are unit-tested. */
data class AccessRow(val kind: GrantKind, val title: String, val state: String, val action: String)

object AccessLogic {
    const val ON = "On"
    const val OFF = "Off"
    const val MANAGE = "Manage"
    const val INTRO = "Each of these is asked for only when you first use its feature. All of it stays on this device."

    fun rows(granted: (GrantKind) -> Boolean): List<AccessRow> = GrantKind.entries.map { k ->
        val g = granted(k)
        val c = GrantGateLogic.copy(k)
        AccessRow(k, c.title, if (g) ON else OFF, if (g) MANAGE else c.button)
    }

    val allEnglish: List<String> get() = listOf(ON, OFF, MANAGE, INTRO, "Access Liftoff asks for", "Turn on or review what Liftoff may read")
}

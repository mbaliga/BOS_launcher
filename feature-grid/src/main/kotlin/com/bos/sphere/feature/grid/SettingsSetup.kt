package com.bos.sphere.feature.grid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/**
 * Wave 56 (settingsCore), CFG-32: what the "Finish setup" row at the top of Settings is made of. The four things Liftoff cannot do for itself
 * (it is not the default home, it may not read notifications, it may not read which apps ran, a Do Not Disturb action is set but the access is
 * off) are looked at once here. Pure, so the rules run on the JVM with fakes; the app fills [SetupState] from the system and wires the buttons.
 */
data class SetupState(
    val defaultHome: Boolean,
    val notificationAccess: Boolean,
    val usageAccess: Boolean,
    val dndAccess: Boolean,
    /** A corner, gesture or ring section runs Do Not Disturb or Silent mode: only then does the access matter. */
    val dndActionBound: Boolean,
) {
    companion object {
        /** Everything is done (or not asked): no Finish setup row. */
        val Complete = SetupState(defaultHome = true, notificationAccess = true, usageAccess = true, dndAccess = true, dndActionBound = false)
    }
}

enum class SetupStep(val id: String, val title: String, val offSummary: String, val button: String) {
    DefaultHome("defaultHome", "Default home app", "Another launcher is your home app", "Set as default home"),
    Notifications("notifications", "Notification access", "Comms, badges and the media strip stay empty", "Allow notification access"),
    Usage("usage", "Usage access", "Recent apps from other launchers are not listed", "Allow usage access"),
    DoNotDisturb("dnd", "Do Not Disturb access", "A Do Not Disturb action is set but cannot run", "Allow Do Not Disturb access"),
}

data class SetupRow(val step: SetupStep, val done: Boolean)

object FinishSetup {
    /** The steps that apply right now, in order, each with whether it is done. The Do Not Disturb step only applies while an action of it is bound. */
    fun rows(s: SetupState): List<SetupRow> = buildList {
        add(SetupRow(SetupStep.DefaultHome, s.defaultHome))
        add(SetupRow(SetupStep.Notifications, s.notificationAccess))
        add(SetupRow(SetupStep.Usage, s.usageAccess))
        if (s.dndActionBound) add(SetupRow(SetupStep.DoNotDisturb, s.dndAccess))
    }

    fun pending(s: SetupState): List<SetupStep> = rows(s).filter { !it.done }.map { it.step }

    fun isComplete(s: SetupState): Boolean = pending(s).isEmpty()

    /** The grey line of the root row: "2 left". */
    fun summary(left: Int): String = L10n.tr("%d left", left)

    /**
     * The Finish setup page as a category, or null once everything is done (the row and the page then disappear). It is a category with one
     * bare pane, so it opens as a page like Hidden apps does; [run] opens the right system page for a step (through the existing grant explainers).
     */
    fun category(s: SetupState, run: (SetupStep) -> Unit): SettingCategory? {
        val rows = rows(s)
        if (rows.none { !it.done }) return null
        val left = rows.count { !it.done }
        return SettingCategory(
            L10n.tr(OneUiModel.FINISH_SETUP), Glyphs.Check,
            listOf(
                SettingItem.Pane(L10n.tr(OneUiModel.FINISH_SETUP), Glyphs.Check, summary(left), bare = true, pageSubtitle = null) { FinishSetupList(rows, run) }
                    .keywords("setup", "permissions", "access", "default launcher", "home app", "notifications", "usage", "do not disturb"),
            ),
        )
    }
}

object SetupTags {
    const val ROW = "oneui.finishRow"
    const val LIST = "oneui.finishList"
    fun step(id: String) = "oneui.finish.$id"
    fun button(id: String) = "oneui.finish.$id.button"
}

/** The body of the Finish setup page: one flat row per step with its state and, while it is open, a text button that opens the system page. */
@Composable
fun ColumnScope.FinishSetupList(rows: List<SetupRow>, run: (SetupStep) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(SetupTags.LIST)) {
        OneUiRows(rows.size) {
            rows.forEachIndexed { i, r ->
                if (i > 0) OneUiDivider(20.dp)
                val state = L10n.tr(if (r.done) "On" else "Off")
                OneUiRowFrame(
                    L10n.tr(r.step.title), summary = if (r.done) state else L10n.tr(r.step.offSummary),
                    role = null,
                    modifier = Modifier.testTag(SetupTags.step(r.step.id)).semantics(mergeDescendants = true) { contentDescription = "${L10n.tr(r.step.title)}, $state" },
                    trailing = {
                        if (r.done) Icon(Glyphs.Check, L10n.tr("Done"), tint = HyleColors.InkDim, modifier = Modifier.size(22.dp))
                        else Text(
                            L10n.tr("Open"), color = HyleColors.Violet, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag(SetupTags.button(r.step.id)).heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = L10n.tr(r.step.button)) { run(r.step) }
                                .padding(horizontal = 8.dp, vertical = 14.dp)
                                .semantics { contentDescription = L10n.tr(r.step.button) },
                        )
                    },
                )
            }
        }
    }
}

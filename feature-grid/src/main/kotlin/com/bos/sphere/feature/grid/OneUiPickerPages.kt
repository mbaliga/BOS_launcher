package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/**
 * Wave 46 (settingsPages): the pickers the settings open are full pages, not popups. Dialogs stay for confirmations and short text entry
 * only ([OneUiDialog]). A page hosts the shared One UI page chrome (back arrow, large collapsing title with the one-line explanation under
 * it, flat lists) in a full-screen window so a pane that lives in a list can still open one; the window has no card, no scrim and no
 * Cancel button: the back arrow (and the system Back) is the way out.
 */
@Composable
fun OneUiFullPage(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        LocalOneUiDialogHook.current()
        Box(Modifier.fillMaxSize().background(HyleColors.Background)) { content() }
    }
}

/**
 * Single choice as a full page: the options are radio rows inside one flat list under the large title, [body] is the one-line
 * explanation under the title. Picking a row applies it and returns.
 */
@Composable
fun <T> OneUiRadioPage(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    detail: (T) -> String? = { null },
    body: String? = null,
) = OneUiFullPage(onDismiss) {
    OneUiPageFrame(title, onDismiss, subtitle = body, tag = OneUiDialogTags.PAGE) {
        item(key = "options") {
            OneUiRows(options.size, Modifier.selectableGroup()) {
                options.forEachIndexed { i, o ->
                    if (i > 0) OneUiDivider(20.dp)
                    OneUiRadioRow(label(o), o == selected, detail(o), Modifier.testTag(OneUiDialogTags.option(i))) { onSelect(o); onDismiss() }
                }
            }
        }
    }
}

/**
 * Multiple choice as a full page: checkbox rows in one flat list and a Done button under it; the back arrow leaves without applying.
 * [min] and [max] bound the count: Done is off outside them.
 */
@Composable
fun <T> OneUiCheckPage(
    title: String,
    options: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onDone: (Set<T>) -> Unit,
    onDismiss: () -> Unit,
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    doneLabel: String = L10n.tr("Done"),
    body: String? = null,
) {
    var picked by remember { mutableStateOf(selected) }
    OneUiFullPage(onDismiss) {
        OneUiPageFrame(title, onDismiss, subtitle = body, tag = OneUiDialogTags.PAGE) {
            item(key = "options") {
                OneUiRows(options.size) {
                    options.forEachIndexed { i, o ->
                        if (i > 0) OneUiDivider(20.dp)
                        val on = o in picked
                        OneUiCheckRow(label(o), on, Modifier.testTag(OneUiDialogTags.option(i))) {
                            picked = if (on) picked - o else if (picked.size < max) picked + o else picked
                        }
                    }
                }
            }
            item(key = "done") {
                val ok = picked.size in min..max
                androidx.compose.foundation.layout.Column {
                    OneUiGap()
                    OneUiPrimaryButton(doneLabel, { onDone(picked) }, Modifier.testTag(OneUiDialogTags.CONFIRM), glyph = Glyphs.Check, enabled = ok)
                }
            }
        }
    }
}


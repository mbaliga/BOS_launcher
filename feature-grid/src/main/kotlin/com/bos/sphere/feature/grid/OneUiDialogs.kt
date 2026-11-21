package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/** Test tags of the One UI dialogs. */
object OneUiDialogTags {
    const val DIALOG = "oneui.dialog"
    const val TITLE = "oneui.dialog.title"
    const val BODY = "oneui.dialog.body"
    const val CANCEL = "oneui.dialog.cancel"
    const val CONFIRM = "oneui.dialog.confirm"
    const val FIELD = "oneui.dialog.field"
    /** Wave 46: the full page a radio or checkbox picker opens (it is not a dialog card). */
    const val PAGE = "oneui.picker.page"
    /** Wave 48: the full page an editor (corner, gesture, drawer colour, group, session, dock) opens. */
    const val EDITOR_PAGE = "oneui.editor.page"
    fun option(i: Int) = "oneui.dialog.option.$i"
}

/**
 * A dialog window may need the host's own window hook (the dock's controller layer, INP-20). The default installs the shared controller
 * keys; a host that has its own hook provides it here once and every One UI dialog under it calls it.
 */
val LocalOneUiDialogHook = compositionLocalOf<@Composable () -> Unit> { { DialogControllerKeys() } }

/** The One UI dialog numbers, kept pure so a test reads them. */
object OneUiDialogSpec {
    const val CORNER_DP = 28
    const val TITLE_SP = 20
    const val BODY_SP = 15
    const val BUTTON_HEIGHT_DP = 56
    const val OPTION_HEIGHT_DP = 56
    const val MENU_CORNER_DP = 16
    const val MENU_ROW_DP = 48
    const val SHEET_CORNER_DP = 28
}

private val Danger = Color(0xFFE5484D)

/**
 * One UI dialog: a centred rounded rectangle (28 dp corners), a centred 20 sp title (a heading), grey 15 sp body text, optional [content],
 * and a row of full-width text buttons at the bottom (Cancel | primary) cut by hairlines, accent-coloured, 56 dp tall, never filled.
 * A destructive primary is red. The window traps focus and TalkBack stays inside it; Back and a tap outside call [onDismiss].
 */
@Composable
fun OneUiDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    cancelLabel: String? = L10n.tr("Cancel"),
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    scrollable: Boolean = true,
    bodyTag: String = OneUiDialogTags.BODY,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LocalOneUiDialogHook.current()
        OneUiDialogCard(title, onDismiss, modifier, body, cancelLabel, confirmLabel, onConfirm, confirmEnabled, destructive, scrollable, bodyTag, content)
    }
}

/** The dialog's card without its window, so a screenshot test can draw it on a plain surface. */
@Composable
internal fun OneUiDialogCard(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    cancelLabel: String? = L10n.tr("Cancel"),
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    scrollable: Boolean = true,
    bodyTag: String = OneUiDialogTags.BODY,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    run {
        val shape = RoundedCornerShape(OneUiDialogSpec.CORNER_DP.dp)
        val maxH = (LocalConfiguration.current.screenHeightDp * 0.86f).dp
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier.widthIn(max = 380.dp).fillMaxWidth().heightIn(max = maxH).clip(shape).background(HyleColors.Surface).imePadding()
                    .testTag(OneUiDialogTags.DIALOG).semantics { paneTitle = title },
            ) {
                Column(Modifier.weight(1f, fill = false).then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
                    Text(
                        title, color = HyleColors.InkBright, fontSize = OneUiDialogSpec.TITLE_SP.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, lineHeight = 26.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = if (body == null && content == null) 20.dp else 12.dp)
                            .testTag(OneUiDialogTags.TITLE).semantics { heading() },
                    )
                    if (body != null) Text(
                        body, color = HyleColors.InkDim, fontSize = OneUiDialogSpec.BODY_SP.sp, lineHeight = 21.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp).testTag(bodyTag),
                    )
                    if (content != null) Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), content = content)
                }
                if (cancelLabel != null || confirmLabel != null) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(HyleColors.Hairline))
                    Row(Modifier.fillMaxWidth()) {
                        if (cancelLabel != null) OneUiDialogButton(cancelLabel, OneUiDialogTags.CANCEL, onDismiss)
                        if (cancelLabel != null && confirmLabel != null) Box(Modifier.width(1.dp).height(OneUiDialogSpec.BUTTON_HEIGHT_DP.dp).background(HyleColors.Hairline))
                        if (confirmLabel != null) OneUiDialogButton(confirmLabel, OneUiDialogTags.CONFIRM, { onConfirm?.invoke() }, enabled = confirmEnabled, destructive = destructive)
                    }
                }
            }
        }
    }
}


@Composable
private fun RowScope.OneUiDialogButton(label: String, tag: String, onClick: () -> Unit, enabled: Boolean = true, destructive: Boolean = false) {
    val color = when {
        !enabled -> HyleColors.InkDim.copy(alpha = 0.5f)
        destructive -> Danger
        else -> HyleColors.Violet
    }
    Box(
        Modifier.weight(1f).heightIn(min = OneUiDialogSpec.BUTTON_HEIGHT_DP.dp).testTag(tag)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { if (!enabled) disabled() },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) }
}

/** A yes/no question: Cancel | [confirmLabel], the confirm in red when [destructive]. */
@Composable
fun OneUiConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String = L10n.tr("Cancel"),
    destructive: Boolean = false,
    bodyTag: String = OneUiDialogTags.BODY,
) = OneUiDialog(title, onDismiss, body = body, cancelLabel = cancelLabel, confirmLabel = confirmLabel, onConfirm = onConfirm, destructive = destructive, bodyTag = bodyTag)

/** One UI's radio button: a ring that fills with the accent and a white dot. */
@Composable
fun OneUiRadioMark(selected: Boolean) {
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(if (selected) HyleColors.Violet else OneUi.Track),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(if (selected) 8.dp else 16.dp).clip(CircleShape).background(if (selected) Color.White else HyleColors.Surface)) }
}

/** One UI's checkbox: a rounded square that fills with the accent and shows a tick. */
@Composable
fun OneUiCheckMark(checked: Boolean) {
    Box(
        Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (checked) HyleColors.Violet else OneUi.Track),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        else Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(HyleColors.Surface))
    }
}

/** A 56 dp row of a choice dialog: the mark at the start, the label (and a grey line) after it. */
@Composable
private fun OneUiChoiceLine(label: String, detail: String?, selected: Boolean, role: Role, tag: String, mark: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = OneUiDialogSpec.OPTION_HEIGHT_DP.dp).testTag(tag)
            .clickable(role = role, onClick = onClick).semantics(mergeDescendants = true) { this.selected = selected; contentDescription = label }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mark()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = HyleColors.InkBright, fontSize = 17.sp)
            if (!detail.isNullOrEmpty()) Text(detail, color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * A text-entry dialog: one underlined field (accent while focused) with the keyboard up, Cancel | [confirmLabel]. The confirm is off while the
 * text is blank. [extra] draws under the field (a "Copy from" choice, for one).
 */
@Composable
fun OneUiTextEntryDialog(
    title: String,
    initial: String,
    hint: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
    allowBlank: Boolean = false,
    /** A problem with the typed text (a name already used): shown in red under the field, and the confirm is off while it is non-null. */
    error: ((String) -> String?)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val problem = error?.invoke(value.text.trim())
    val ok = (allowBlank || value.text.isNotBlank()) && problem == null
    OneUiDialog(title, onDismiss, body = body, confirmLabel = confirmLabel, onConfirm = { if (ok) onConfirm(value.text.trim()) }, confirmEnabled = ok) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                if (value.text.isEmpty()) Text(hint, color = HyleColors.InkDim, fontSize = 17.sp)
                BasicTextField(
                    value, { value = it }, singleLine = true,
                    textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 17.sp), cursorBrush = SolidColor(HyleColors.Violet),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ok) onConfirm(value.text.trim()) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused }.testTag(OneUiDialogTags.FIELD).semantics { contentDescription = hint },
                )
            }
            Box(Modifier.fillMaxWidth().height(if (focused) 2.dp else 1.dp).background(if (focused) HyleColors.Violet else HyleColors.Hairline))
            if (problem != null) Text(problem, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).testTag("oneui.dialog.error"))
            if (extra != null) { Spacer(Modifier.height(12.dp)); extra() }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus(); keyboard?.show() } }
}

/** A row of [OneUiRadioMark] used inside a dialog's [OneUiDialog] content slot for a small inline choice (not a list dialog of its own). */
@Composable
fun OneUiInlineRadio(label: String, selected: Boolean, tag: String? = null, onClick: () -> Unit) {
    OneUiChoiceLine(label, null, selected, Role.RadioButton, tag ?: "oneui.inlineRadio", { OneUiRadioMark(selected) }, onClick)
}

/** A single underlined field for a dialog (accent underline while focused). The caret starts after the text already there. */
@Composable
fun OneUiUnderlineField(text: String, onText: (String) -> Unit, hint: String, modifier: Modifier = Modifier, focus: FocusRequester? = null, tag: String = OneUiDialogTags.FIELD) {
    var edited by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val shown = if (edited.text == text) edited else TextFieldValue(text, TextRange(text.length))
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text(hint, color = HyleColors.InkDim, fontSize = 17.sp)
            BasicTextField(
                shown, { edited = it; if (it.text != text) onText(it.text) }, singleLine = true,
                textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 17.sp), cursorBrush = SolidColor(HyleColors.Violet),
                modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier).onFocusChanged { focused = it.isFocused }
                    .testTag(tag).semantics { contentDescription = hint },
            )
        }
        Box(Modifier.fillMaxWidth().height(if (focused) 2.dp else 1.dp).background(if (focused) HyleColors.Violet else HyleColors.Hairline))
    }
}

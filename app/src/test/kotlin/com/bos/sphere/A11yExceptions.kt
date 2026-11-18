package com.bos.sphere

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties

/** Documented exceptions of the sweep: each says which rules it waives and why. Anything else the audit finds is a defect. */
object A11yExceptions {
    /**
     * A text field has no Compose role (TalkBack announces it as an edit box from its set-text action). Its name is its text; a contentDescription would
     * replace the typed text in what TalkBack reads, so none is set. The empty Radar and drawer fields therefore announce no "Search" word: a device check.
     */
    val textFields = A11yAudit.Exception("text field", setOf("label", "role"), "an edit box is announced from its text and set-text action") {
        it.config.contains(SemanticsActions.SetText) || it.config.contains(SemanticsProperties.EditableText)
    }

    val all: List<A11yAudit.Exception> = listOf(textFields)
}

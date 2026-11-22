package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.UiLanguage
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.Glyphs

/**
 * Wave 54 (settingsVisual): Language stays a page (ten entries are a list, not a card row), but every entry now has a leading glyph: the first
 * letter of its own script (or the globe for "System"), and its name is written in that language ([UiLanguage.label] is the native name, "System"
 * is the only one that is translated). The picked row has a check and radio semantics.
 */
object LanguageGlyphs {
    /** The letter that stands for a language in its own script; null for System (the globe is drawn). */
    fun letter(l: UiLanguage): String? = when (l) {
        UiLanguage.System -> null
        UiLanguage.English -> "Aa"
        UiLanguage.Spanish -> "Ñ"
        UiLanguage.French -> "É"
        UiLanguage.German -> "Ä"
        UiLanguage.Portuguese -> "Ã"
        UiLanguage.Hindi -> "अ"
        UiLanguage.Chinese -> "字"
        UiLanguage.Japanese -> "あ"
        UiLanguage.Arabic -> "ع"
    }

    fun tag(l: UiLanguage) = "cfg.language.${l.name}"
}

@Composable
fun ColumnScope.LanguagePane(current: UiLanguage, onPick: (UiLanguage) -> Unit) {
    Text(L10n.tr("Each language is written in its own script."), color = HyleColors.InkDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
    Column2 {
        UiLanguage.entries.forEach { l ->
            val picked = l == current
            val name = if (l == UiLanguage.System) L10n.tr("System") else l.label
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(LanguageGlyphs.tag(l))
                    .selectable(selected = picked, role = Role.RadioButton, onClick = { onPick(l) })
                    .semantics(mergeDescendants = true) {}.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(40.dp).background(if (picked) HyleColors.Violet.copy(alpha = 0.22f) else HyleColors.Hairline, CircleShape), contentAlignment = Alignment.Center) {
                    val letter = LanguageGlyphs.letter(l)
                    if (letter == null) Icon(Glyphs.Globe, null, tint = HyleColors.InkBright, modifier = Modifier.size(22.dp))
                    else Text(letter, color = HyleColors.InkBright, fontSize = 17.sp, textAlign = TextAlign.Center, maxLines = 1)
                }
                Text(name, color = HyleColors.InkBright, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (picked) Icon(Glyphs.Check, null, tint = HyleColors.Violet, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ColumnScope.Column2(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth().selectableGroup()) { content() }
}

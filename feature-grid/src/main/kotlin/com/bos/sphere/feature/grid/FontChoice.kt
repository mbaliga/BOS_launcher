package com.bos.sphere.feature.grid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.FontCatalog
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

object FontChoiceTags {
    /** The card of a font ([FontCatalog.Entry.id]). */
    fun card(id: String) = "font.card.$id"
    /** The sample text on a card; it is drawn in that card's own family. */
    fun sample(id: String) = "font.sample.$id"
    const val SAMPLE_TEXT_1 = "Liftoff"
    const val SAMPLE_TEXT_2 = "12:30 Aa"
}

/**
 * Wave 52 (settingsFont): the font picker of the Look page, visual like the other pickers: one card per font showing "Liftoff 12:30 Aa" in that
 * font, the chosen card outlined. The pick applies at once to every text of the launcher (the theme's typography), no restart.
 */
@Composable
fun FontChoice(selectedId: String, onSelect: (String) -> Unit) {
    val ctx = LocalContext.current
    val selected = FontCatalog.entry(selectedId)
    VisualChoice(FontCatalog.entries, selected, { onSelect(it.id) }, { L10n.tr(it.label) }, columns = 2, previewHeight = 72.dp) { e ->
        val family = remember(ctx, e.id) { FontCatalog.family(ctx, e.id) }
        Column(Modifier.testTag(FontChoiceTags.sample(e.id)), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(FontChoiceTags.SAMPLE_TEXT_1, color = HyleColors.InkBright, fontSize = 24.sp, fontFamily = family, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1)
            Text(FontChoiceTags.SAMPLE_TEXT_2, color = HyleColors.InkDim, fontSize = 17.sp, fontFamily = family, fontWeight = FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

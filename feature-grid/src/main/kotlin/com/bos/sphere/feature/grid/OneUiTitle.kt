package com.bos.sphere.feature.grid

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.rememberTitleFontFamily
import com.bos.sphere.core.design.titleCaps

/** Wave 52: the text a title really shows (capitals), for tests; the semantics text stays the plain title so a screen reader does not spell it. */
internal val ShownTitleKey = SemanticsPropertyKey<String>("shownTitle")
internal var SemanticsPropertyReceiver.shownTitle by ShownTitleKey

/**
 * Wave 52 (settingsFont), CFG-21: every settings title (the large one, the small collapsed one, a dialog's) is shown in capitals (the locale's own
 * rules; Arabic, Hindi and CJK have no case and stay as they are) in Space Grotesk, centred. The Box carries the semantics (the plain title as its
 * text, a heading) so a screen reader reads the words, not capital letters, and the node has the size of the words only, which the centring tests measure.
 */
@Composable
internal fun OneUiTitleText(
    title: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 34.sp,
    lineHeight: TextUnit = size * 1.18f,
    weight: FontWeight = FontWeight.Medium,
    color: Color = HyleColors.InkBright,
    maxLines: Int = Int.MAX_VALUE,
    heading: Boolean = true,
) {
    val shown = titleCaps(title)
    val family = rememberTitleFontFamily()
    Box(modifier.clearAndSetSemantics { text = AnnotatedString(title); if (heading) heading(); shownTitle = shown }, contentAlignment = Alignment.Center) {
        Text(
            shown, color = color, fontSize = size, fontWeight = weight, fontFamily = family, lineHeight = lineHeight, letterSpacing = 0.6.sp,
            textAlign = TextAlign.Center, maxLines = maxLines, overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        )
    }
}

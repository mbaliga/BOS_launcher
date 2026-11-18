package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.HomeScroll
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/**
 * Wave 26 (HOM-01): the one-time question of the add-page flow: are the pages after Glance one horizontal SCROLL (the start-screen look,
 * items filled column by column) or traditional PAGINATED pages with page dots? Asked when the first page is added; the answer is saved
 * (`pagesModeChosen`) and Ground Control, Home can change it any time.
 */
@Composable
fun PagesModeDialog(onPick: (HomeScroll) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HyleColors.Surface,
        title = { Text(L10n.tr("Pages after Glance"), color = HyleColors.InkBright, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(L10n.tr("Choose once how the pages after Glance work. You can change it in Ground Control, Home."), color = HyleColors.InkDim, fontSize = 13.sp)
                Option("addpage.scroll", L10n.tr("Scroll"), L10n.tr("One continuous screen that scrolls sideways; icons fill it column by column.")) { onPick(HomeScroll.Continuous) }
                Option("addpage.paginated", L10n.tr("Paginated"), L10n.tr("Traditional pages with page dots; you add each page and what goes on it.")) { onPick(HomeScroll.Paged) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.tr("Cancel"), color = HyleColors.Ink) } },
    )
}

@Composable
private fun Option(tag: String, title: String, body: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x1AFFFFFF)).clickable(onClick = onClick).padding(14.dp).testTag(tag),
    ) {
        Text(title, color = HyleColors.InkBright, fontSize = 15.sp)
        Text(body, color = HyleColors.InkDim, fontSize = 12.sp)
    }
}

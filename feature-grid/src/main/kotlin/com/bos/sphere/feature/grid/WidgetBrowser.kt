package com.bos.sphere.feature.grid

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.catalog.WidgetChoiceRef
import com.bos.sphere.core.catalog.WidgetPickerSearch
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/** Test tags of the in-place widget browser (W51). */
object WidgetBrowserTags {
    const val BROWSER = "widget.browser"
    const val SEARCH = "widget.search"
    const val CARD_PREFIX = "widget.card."
    const val APP_PREFIX = "widget.app."
}

/** The identity of a provider across profiles; also the card's key and its focus id. */
internal fun WidgetChoice.id(): String = info.provider.flattenToString() + "#" + isWork

/** "5x2" for the card caption and the spoken name. */
internal fun WidgetChoice.sizeLabel(): String = widgetCellsOf(info).let { (c, r) -> "${c}x$r" }

/**
 * W51 (addBrowse): the widgets as a visual browser, directly in the tab or the tray panel (one layer, no second sheet). A search field that never takes focus
 * on its own, a row of app chips to filter by app, and a sideways carousel (two rows when [maxHeight] allows) of provider preview cards, each with its name and its
 * size under it. A tap on a card calls [onPick] (the host places the widget on the page being edited through the picker's own bind and configure flow).
 * [choices] is null while loading; the default loads the device's providers.
 */
@Composable
fun WidgetBrowser(
    onPick: (WidgetChoice) -> Unit,
    modifier: Modifier = Modifier,
    choices: List<WidgetChoice>? = rememberWidgetChoices(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var appFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val list = remember(choices, query, appFilter) {
        choices?.let { l ->
            val byApp = if (appFilter == null) l else l.filter { it.appLabel == appFilter }
            if (query.isBlank()) byApp // grouped by app, as the loader sorted them
            else {
                val order = WidgetPickerSearch.filter(byApp.map { WidgetChoiceRef(it.id(), it.appLabel, it.label) }, query).map { it.provider }
                val byId = byApp.associateBy { it.id() }
                order.mapNotNull { byId[it] }
            }
        }
    }
    val apps = remember(choices) { choices?.map { it.appLabel }?.distinct().orEmpty() }
    BoxWithConstraints(modifier.fillMaxSize().testTag(WidgetBrowserTags.BROWSER).semantics { contentDescription = L10n.tr("Widget previews") }) {
        val headerH = 44.dp
        val gridH = maxHeight - headerH
        val rows = if (gridH >= CARD_H2 * 2 + 8.dp) 2 else 1
        // Two rows of compact cards when the room allows, else one row of cards that use the whole height (a larger preview).
        val cardH = if (rows == 2) CARD_H2 else gridH.coerceIn(CARD_H2, 156.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // One header row: the search field (it does not request focus: the keyboard rises only when the user taps it) and the app chips to its side.
            Row(Modifier.fillMaxWidth().height(headerH - 4.dp).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 14.sp), cursorBrush = SolidColor(HyleColors.Violet),
                    modifier = Modifier.width(if (apps.size > 1) 150.dp else 280.dp).height(40.dp).testTag(WidgetBrowserTags.SEARCH)
                        .background(HyleColors.InkDim.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 14.dp)
                        .semantics { contentDescription = L10n.tr("Search widgets") },
                    decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { if (query.isEmpty()) Text(L10n.tr("Search widgets"), color = HyleColors.Ink, fontSize = 14.sp, maxLines = 1); inner() } },
                )
                if (apps.size > 1) Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppChip(L10n.tr("All"), appFilter == null, WidgetBrowserTags.APP_PREFIX + "all") { appFilter = null }
                    apps.forEachIndexed { i, a -> AppChip(a, appFilter == a, WidgetBrowserTags.APP_PREFIX + i) { appFilter = if (appFilter == a) null else a } }
                }
            }
            when {
                list == null -> Note(L10n.tr("Loading widgets…"))
                list.isEmpty() -> Note(L10n.tr(if (query.isEmpty() && appFilter == null) "No widgets installed" else "No widget matches"))
                else -> LazyHorizontalGrid(
                    GridCells.Fixed(rows), Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.id() }) { c -> WidgetCard(c, list.indexOf(c), cardH, onPick) }
                }
            }
        }
    }
}

private val CARD_H2 = 96.dp
private val TEXT_H = 40.dp // the name and the size under the preview, plus the card's padding

@Composable
private fun AppChip(label: String, on: Boolean, tag: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (on) HyleColors.OnAccent else HyleColors.Ink,
        modifier = Modifier.defaultMinSize(minHeight = 40.dp).clip(RoundedCornerShape(50))
            .background(if (on) HyleColors.Violet else HyleColors.InkDim.copy(alpha = 0.14f))
            .clickable(role = Role.Tab, onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(tag).semantics { role = Role.Tab; selected = on },
    )
}

@Composable
private fun WidgetCard(choice: WidgetChoice, index: Int, cardH: androidx.compose.ui.unit.Dp, onPick: (WidgetChoice) -> Unit) {
    val previewH = cardH - TEXT_H
    val previewW = (previewH.value * 1.75f).coerceIn(96f, 220f).dp
    val preview = rememberWidgetPreview(choice.info, previewW, previewH)
    val shape = RoundedCornerShape(14.dp)
    val size = choice.sizeLabel()
    Column(
        Modifier.size(previewW + 16.dp, cardH).clip(shape)
            .launcherItem("addbrowse.widget.$index", focusable = false, onActivate = { onPick(choice) }, ringRadiusDp = 14f)
            .clickable(onClickLabel = L10n.tr("Add to home")) { onPick(choice) }
            .padding(4.dp).testTag(WidgetBrowserTags.CARD_PREFIX + index)
            .semantics { role = Role.Button; contentDescription = L10n.tr("Add widget: %s, %s, %s", choice.label, choice.appLabel, size) },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier.size(previewW, previewH).clip(RoundedCornerShape(10.dp)).background(HyleColors.Surface).border(1.dp, HyleColors.Hairline, RoundedCornerShape(10.dp)).padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) Image(preview, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else {
                // No preview image from the provider (or still loading): the widget's footprint, drawn to its cell proportions.
                val (c, r) = widgetCellsOf(choice.info)
                Box(Modifier.fillMaxSize(0.92f).aspectRatio(c.toFloat() / r, matchHeightConstraintsFirst = r > c).clip(RoundedCornerShape(8.dp)).background(HyleColors.Violet.copy(alpha = 0.22f)).border(1.dp, HyleColors.Violet.copy(alpha = 0.5f), RoundedCornerShape(8.dp)))
            }
        }
        Text(choice.label, color = HyleColors.InkBright, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(size, color = HyleColors.InkDim, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Note(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = HyleColors.InkDim, fontSize = 13.sp) }
}

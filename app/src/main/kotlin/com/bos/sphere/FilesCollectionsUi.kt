package com.bos.sphere

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bos.sphere.core.catalog.BuiltInCollection
import com.bos.sphere.core.catalog.CustomCollection
import com.bos.sphere.core.catalog.FileCollection
import com.bos.sphere.core.catalog.FileCollections
import com.bos.sphere.core.catalog.FileRule
import com.bos.sphere.core.catalog.FileText
import com.bos.sphere.core.catalog.FilesSortText
import com.bos.sphere.core.catalog.GroupKey
import com.bos.sphere.core.catalog.PinnedFolder
import com.bos.sphere.core.catalog.SortKey
import com.bos.sphere.core.catalog.TypeGroup
import com.bos.sphere.core.catalog.ViewOptions
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/**
 * W53 (filesCollections): the pages and sheets of the Files section. The sort and group sheet, the full-page chooser ("Add to Files": the ready-made
 * collections as cards with a glyph and an example, the order of what is shown, the folders) and the "New collection" editor. Every choice is a drawn
 * card with an accent ring and a check when on; a dialog stacks its main action as a coloured box on top and the other one as plain text below.
 */
private val Tile = Color(0xFF15161B)
private val PageBg = Color(0xFF0E0F13)
private val Faint = Color(0x33E7E9F0)
private val Soft = Color(0x80E7E9F0)
private val InkC = Color(0xFFE7E9F0)

internal fun glyphOfCollection(b: BuiltInCollection?): Glyph = when (b) {
    BuiltInCollection.Recents -> Glyph.Clock
    BuiltInCollection.Documents -> Glyph.Page
    BuiltInCollection.Images -> Glyph.Picture
    BuiltInCollection.Videos -> Glyph.Play
    BuiltInCollection.Audio -> Glyph.Note
    BuiltInCollection.Downloads -> Glyph.Download
    BuiltInCollection.Archives -> Glyph.Archive
    BuiltInCollection.Screenshots -> Glyph.Shot
    BuiltInCollection.Large -> Glyph.Large
    null -> Glyph.Layers
}

private fun glyphOfType(t: TypeGroup): Glyph = when (t) {
    TypeGroup.Documents -> Glyph.Page; TypeGroup.Images -> Glyph.Picture; TypeGroup.Videos -> Glyph.Play
    TypeGroup.Audio -> Glyph.Note; TypeGroup.Archives -> Glyph.Archive; TypeGroup.Apps -> Glyph.Grid
}

/** A choice card: [art] over a label; a radio (one of several) or a checkbox (any number). Selected: accent ring and check. At least 48 dp, focus ring for keys. */
@Composable
internal fun ChoiceCard(
    label: String, selected: Boolean, multi: Boolean, tag: String, accent: Color, onClick: () -> Unit,
    modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp = 80.dp, art: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val tile = RoundedCornerShape(16.dp)
    val click = Modifier.filesFocus("files.card.$tag", onClick)
    val select = if (multi) Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }) else Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    Column(
        modifier.width(width).testTag(tag).onFocusChanged { focused = it.isFocused }.then(click).then(select).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().height(64.dp).clip(tile).background(Tile).border(if (selected) 2.5.dp else 1.dp, if (selected) accent else if (focused) Color.White else Faint, tile),
            contentAlignment = Alignment.Center,
        ) {
            art()
            if (selected) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.Check, HyleColors.OnAccent, Modifier.size(13.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (selected) Color.White else Color(0xB3FFFFFF), fontSize = 12.sp, lineHeight = 15.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 18.dp))
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CardArt(draw: DrawScope.(Size) -> Unit) { Canvas(Modifier.fillMaxSize()) { draw(size) } }

@Composable
private fun GlyphArt(g: Glyph, color: Color = InkC) { GlyphIcon(g, color, Modifier.size(34.dp)) }

// ---- illustrations for the sort and group cards (static, drawn in physical coordinates) ----------------------------------------------------------

private fun DrawScope.rowBars(s: Size, lengths: List<Float>, color: Color = Soft, startX: Float = .2f, top: Float = .24f, step: Float = .2f) {
    lengths.forEachIndexed { i, l -> drawRoundRect(color, Offset(s.width * startX, s.height * (top + i * step)), Size(s.width * l, s.height * .09f), CornerRadius(s.height * .045f)) }
}

private fun sortArt(k: SortKey): DrawScope.(Size) -> Unit = { s ->
    when (k) {
        SortKey.Name -> { for (i in 0..2) drawCircle(Soft, s.height * .05f, Offset(s.width * .26f, s.height * (.285f + i * .2f))); rowBars(s, listOf(.5f, .34f, .42f), Color.White, .36f) }
        SortKey.Modified -> {
            drawRoundRect(Soft, Offset(s.width * .24f, s.height * .2f), Size(s.width * .52f, s.height * .6f), CornerRadius(s.width * .05f), style = Stroke(2f * density))
            drawRect(Color.White, Offset(s.width * .24f, s.height * .2f), Size(s.width * .52f, s.height * .16f))
            for (i in 0..2) drawCircle(Soft, 2.2f * density, Offset(s.width * (.36f + i * .14f), s.height * .6f))
        }
        SortKey.Added -> {
            drawRoundRect(Soft, Offset(s.width * .24f, s.height * .2f), Size(s.width * .52f, s.height * .6f), CornerRadius(s.width * .05f), style = Stroke(2f * density))
            drawRect(Color.White, Offset(s.width * .24f, s.height * .2f), Size(s.width * .52f, s.height * .16f))
            drawLine(Color.White, Offset(s.width * .5f, s.height * .5f), Offset(s.width * .5f, s.height * .72f), 2f * density)
            drawLine(Color.White, Offset(s.width * .39f, s.height * .61f), Offset(s.width * .61f, s.height * .61f), 2f * density)
        }
        SortKey.Size -> for (i in 0..3) drawRoundRect(if (i == 3) Color.White else Soft, Offset(s.width * (.26f + i * .14f), s.height * (.76f - (i + 1) * .13f)), Size(s.width * .1f, s.height * (.13f * (i + 1))), CornerRadius(3f * density))
        SortKey.Type -> {
            drawCircle(Soft, s.height * .13f, Offset(s.width * .3f, s.height * .5f))
            drawRoundRect(Color.White, Offset(s.width * .43f, s.height * .37f), Size(s.height * .26f, s.height * .26f), CornerRadius(3f * density))
            val p = androidx.compose.ui.graphics.Path().apply { moveTo(s.width * .72f, s.height * .36f); lineTo(s.width * .8f, s.height * .64f); lineTo(s.width * .64f, s.height * .64f); close() }
            drawPath(p, Soft)
        }
    }
}

private fun orderArt(up: Boolean): DrawScope.(Size) -> Unit = { s ->
    val x = s.width * .5f; val y1 = s.height * .24f; val y2 = s.height * .76f
    drawLine(Color.White, Offset(x, y1), Offset(x, y2), 2.5f * density)
    val tip = if (up) y1 else y2; val d = if (up) 1f else -1f
    drawLine(Color.White, Offset(x, tip), Offset(x - s.width * .1f, tip + d * s.height * .14f), 2.5f * density)
    drawLine(Color.White, Offset(x, tip), Offset(x + s.width * .1f, tip + d * s.height * .14f), 2.5f * density)
}

private fun groupArt(k: GroupKey): DrawScope.(Size) -> Unit = { s ->
    fun block(top: Float, marker: ((Float) -> Unit)?) {
        drawRoundRect(Color.White, Offset(s.width * .2f, s.height * top), Size(s.width * .24f, s.height * .06f), CornerRadius(2f * density))
        marker?.invoke(top)
        drawRoundRect(Soft, Offset(s.width * .2f, s.height * (top + .12f)), Size(s.width * .6f, s.height * .06f), CornerRadius(2f * density))
        drawRoundRect(Soft, Offset(s.width * .2f, s.height * (top + .22f)), Size(s.width * .45f, s.height * .06f), CornerRadius(2f * density))
    }
    when (k) {
        GroupKey.None -> rowBars(s, listOf(.6f, .6f, .45f, .6f), Soft, .2f, .2f, .17f)
        GroupKey.Date -> { block(.16f) { t -> drawCircle(Soft, 3f * density, Offset(s.width * .72f, s.height * (t + .03f))) }; block(.54f) { t -> drawCircle(Soft, 3f * density, Offset(s.width * .72f, s.height * (t + .03f))) } }
        GroupKey.Type -> { block(.16f) { t -> drawCircle(Soft, 3.5f * density, Offset(s.width * .72f, s.height * (t + .03f))) }; block(.54f) { t -> drawRect(Soft, Offset(s.width * .69f, s.height * (t + .03f) - 3.5f * density), Size(7f * density, 7f * density)) } }
        GroupKey.Folder -> for (i in 0..1) {
            val y = s.height * (.2f + i * .34f)
            drawRoundRect(Soft, Offset(s.width * .2f, y), Size(s.width * .2f, s.height * .08f), CornerRadius(2f * density))
            drawRoundRect(Color.White, Offset(s.width * .2f, y + s.height * .05f), Size(s.width * .6f, s.height * .2f), CornerRadius(3f * density))
        }
        GroupKey.Size -> for (i in 0..2) drawRoundRect(if (i == 0) Color.White else Soft, Offset(s.width * .2f, s.height * (.2f + i * .22f)), Size(s.width * (.62f - i * .17f), s.height * .12f), CornerRadius(2f * density))
    }
}

/** Small section label of a page. */
@Composable
private fun PageLabel(text: String, tag: String? = null) {
    Text(text, color = Soft, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier).semantics { heading() })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Cards(content: @Composable () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

// ---- sort and group sheet ------------------------------------------------------------------------------------------------------------------------

/**
 * The sort and group sheet: cards for what to sort by, the order, and how to group; changes apply at once to [options]. [showAdded] adds Date added
 * (only when a source gives that date). The coloured Done box closes it; below it, plain text sets this choice for every source or resets this source.
 */
@Composable
internal fun SortGroupSheet(
    options: ViewOptions, showAdded: Boolean, ownChoice: Boolean, accent: Color,
    onChange: (ViewOptions) -> Unit, onUseForAll: () -> Unit, onReset: () -> Unit, onDone: () -> Unit,
) {
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SortGroupSheetBody(options, showAdded, ownChoice, accent, onChange, onUseForAll, onReset, onDone)
    }
}

/** The sheet's content, without its window (so a test can draw it on the activity to take a picture). */
@Composable
internal fun SortGroupSheetBody(
    options: ViewOptions, showAdded: Boolean, ownChoice: Boolean, accent: Color,
    onChange: (ViewOptions) -> Unit, onUseForAll: () -> Unit, onReset: () -> Unit, onDone: () -> Unit,
) {
    run {
        Box(Modifier.fillMaxSize().background(Color(0xB0000000)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 600.dp).testTag("files.sheet")
                    .background(Color(0xFF1B1E24), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(36.dp).height(4.dp).background(Faint, RoundedCornerShape(2.dp)))
                Text(L10n.tr("Sort and group"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 12.dp).semantics { heading() })
                Column(Modifier.fillMaxWidth()) {
                    PageLabel(L10n.tr("Sort by"))
                    Cards {
                        SortKey.entries.filter { it != SortKey.Added || showAdded || options.sort == SortKey.Added }.forEach { k ->
                            ChoiceCard(L10n.tr(FilesSortText.sortLabel(k)), options.sort == k, false, "files.sort.${k.name}", accent, { onChange(options.copy(sort = k)) }) { CardArt(sortArt(k)) }
                        }
                    }
                    PageLabel(L10n.tr("Order"))
                    Cards {
                        ChoiceCard(L10n.tr(FilesSortText.orderLabel(options.sort, true)), options.ascending, false, "files.order.asc", accent, { onChange(options.copy(ascending = true)) }) { CardArt(orderArt(true)) }
                        ChoiceCard(L10n.tr(FilesSortText.orderLabel(options.sort, false)), !options.ascending, false, "files.order.desc", accent, { onChange(options.copy(ascending = false)) }) { CardArt(orderArt(false)) }
                    }
                    PageLabel(L10n.tr("Group by"))
                    Cards {
                        GroupKey.entries.forEach { g ->
                            ChoiceCard(L10n.tr(FilesSortText.groupLabel(g)), options.group == g, false, "files.group.${g.name}", accent, { onChange(options.copy(group = g)) }) { CardArt(groupArt(g)) }
                        }
                    }
                }
                PrimaryBox(L10n.tr("Done"), accent, "files.sheet.done", Modifier.padding(top = 18.dp).fillMaxWidth(), onClick = onDone)
                SecondaryText(L10n.tr("Use this for every source"), Soft, "files.sheet.all", onClick = onUseForAll)
                if (ownChoice) SecondaryText(L10n.tr("Reset this source to the default"), Soft, "files.sheet.reset", onClick = onReset)
            }
        }
    }
}

// ---- confirm dialog ------------------------------------------------------------------------------------------------------------------------------

/** A small centred dialog: the coloured box on top does the action, plain text below keeps things as they are. */
@Composable
internal fun FilesConfirm(title: String, body: String, primary: String, secondary: String, accent: Color, tag: String, onPrimary: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().testTag(tag).background(Color(0xFF1B1E24), RoundedCornerShape(24.dp)).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Text(body, color = Soft, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            PrimaryBox(primary, accent, "$tag.primary", Modifier.padding(top = 18.dp).fillMaxWidth(), onClick = onPrimary)
            SecondaryText(secondary, Soft, "$tag.secondary", onClick = onDismiss)
        }
    }
}

// ---- chooser and editor pages --------------------------------------------------------------------------------------------------------------------

/** Where the chooser dialog opens: the list, a new collection, or the editor of one custom collection. */
internal sealed interface ChooserStart {
    object List : ChooserStart
    object New : ChooserStart
    data class Edit(val id: String) : ChooserStart
}

@Composable
private fun PageBar(title: String, tag: String, onBack: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val back = L10n.tr("Back")
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).testTag("$tag.back").filesFocus("$tag.back", onBack).semantics { contentDescription = back }.clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(Glyph.Back, Color.White, Modifier.size(24.dp).graphicsLayer { scaleX = if (rtl) -1f else 1f }) }
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp).testTag("$tag.title").semantics { heading() })
    }
}

/** A built-in collection card of the chooser: a glyph tile, the name, and an example line; a switch for "show it in Files". */
@Composable
private fun CollectionCardTile(b: BuiltInCollection, on: Boolean, accent: Color, width: androidx.compose.ui.unit.Dp, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.width(width).testTag("files.chooser.card." + b.id).onFocusChanged { focused = it.isFocused }
            .clip(shape).background(Tile).border(if (on) 2.5.dp else 1.dp, if (on) accent else if (focused) Color.White else Faint, shape)
            .filesFocus("files.chooser.card." + b.id, onToggle).toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
            .semantics(mergeDescendants = true) { stateDescription = L10n.tr(if (on) "Shown in Files" else "Not shown") }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = .22f)), contentAlignment = Alignment.Center) { GlyphIcon(glyphOfCollection(b), accent, Modifier.size(26.dp)) }
            Spacer(Modifier.weight(1f))
            if (on) Box(Modifier.size(20.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.Check, HyleColors.OnAccent, Modifier.size(14.dp)) }
        }
        Text(L10n.tr(b.title), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        Text(L10n.tr(b.example), color = Soft, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun IconButton(g: Glyph, desc: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).testTag(tag).filesFocus("$tag.focus") { if (enabled) onClick() }
            .semantics { contentDescription = desc; if (!enabled) disabled() }.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(g, if (enabled) Color.White else Faint, Modifier.size(22.dp)) }
}

/** The full-page chooser ("Add to Files") and its "New collection" editor, in one full-screen dialog. */
@Composable
internal fun FilesChooser(
    collections: FileCollections, folders: List<PinnedFolder>, accent: Color, start: ChooserStart,
    onChange: (FileCollections) -> Unit, onAddFolder: () -> Unit, onClose: () -> Unit,
) {
    var editing by remember { mutableStateOf<ChooserStart>(start) }
    Dialog(onDismissRequest = { if (editing == ChooserStart.List) onClose() else if (start == ChooserStart.List) editing = ChooserStart.List else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        FilesChooserPages(collections, folders, accent, start, editing, { editing = it }, onChange, onAddFolder, onClose)
    }
}

/** The pages without their window: the list, or the editor of a new or an existing collection. */
@Composable
internal fun FilesChooserPages(
    collections: FileCollections, folders: List<PinnedFolder>, accent: Color, start: ChooserStart, editing: ChooserStart, setEditing: (ChooserStart) -> Unit,
    onChange: (FileCollections) -> Unit, onAddFolder: () -> Unit, onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(PageBg).testTag("files.chooser")) {
        when (val e = editing) {
            ChooserStart.List -> ChooserList(collections, folders, accent, onChange, onAddFolder, onClose, onNew = { setEditing(ChooserStart.New) }, onEdit = { setEditing(ChooserStart.Edit(it)) })
            else -> {
                val back = { if (start == ChooserStart.List) setEditing(ChooserStart.List) else onClose() }
                CollectionEditor(collections, (e as? ChooserStart.Edit)?.id, folders, accent, onSave = { onChange(collections.upsert(it)); back() }, onDelete = { id -> onChange(collections.remove(id)); back() }, onBack = back)
            }
        }
    }
}

@Composable
private fun ChooserList(
    collections: FileCollections, folders: List<PinnedFolder>, accent: Color,
    onChange: (FileCollections) -> Unit, onAddFolder: () -> Unit, onClose: () -> Unit, onNew: () -> Unit, onEdit: (String) -> Unit,
) {
    var confirm by remember { mutableStateOf<FileCollection?>(null) }
    val shown = collections.resolved()
    Column(Modifier.fillMaxSize()) {
        PageBar(L10n.tr("Add to Files"), "files.chooser", onClose)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(L10n.tr("A collection is a saved search over the folders you added. It shows up in the Files switcher next to All and your folders."), color = Soft, fontSize = 13.sp)
            PageLabel(L10n.tr("Ready-made collections"))
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Two cards across on a phone, four on a wide window, always filling the row.
                val cols = if (maxWidth >= 640.dp) 4 else 2
                val w = (maxWidth - 10.dp * (cols - 1)) / cols
                Cards { BuiltInCollection.entries.forEach { b -> CollectionCardTile(b, collections.isShown(b.id), accent, w) { onChange(collections.toggleBuiltIn(b)) } } }
            }
            PageLabel(L10n.tr("Your own collections"))
            Box(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("files.chooser.new").clip(RoundedCornerShape(18.dp)).border(1.dp, accent.copy(alpha = .7f), RoundedCornerShape(18.dp))
                    .filesFocus("files.chooser.new", onNew).clickable(role = Role.Button, onClick = onNew).padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.Plus, accent, Modifier.size(24.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(L10n.tr("New collection"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(L10n.tr("Pick file types, a name, a folder, an age or a size."), color = Soft, fontSize = 12.sp)
                    }
                }
            }
            PageLabel(L10n.tr("Shown in Files, in this order"))
            if (shown.isEmpty()) Text(L10n.tr("No collection is shown. Switch one on above."), color = Soft, fontSize = 13.sp, modifier = Modifier.testTag("files.chooser.noneShown"))
            shown.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("files.chooser.row." + c.id), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = .22f)), contentAlignment = Alignment.Center) { GlyphIcon(glyphOfCollection(c.builtIn), accent, Modifier.size(22.dp)) }
                    Column(
                        Modifier.weight(1f).padding(horizontal = 12.dp).then(if (c.builtIn == null) Modifier.testTag("files.chooser.edit." + c.id).filesFocus("files.chooser.edit." + c.id) { onEdit(c.id) }.clickable(role = Role.Button, onClickLabel = L10n.tr("Edit")) { onEdit(c.id) } else Modifier),
                    ) {
                        Text(if (c.builtIn != null) L10n.tr(c.name) else c.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.builtIn?.let { L10n.tr(it.example) } ?: ruleSummary(c.rule, folders), color = Soft, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(Glyph.Up, L10n.tr("Move %s up", if (c.builtIn != null) L10n.tr(c.name) else c.name), "files.chooser.up." + c.id, enabled = i > 0) { onChange(collections.move(c.id, -1)) }
                    IconButton(Glyph.Down, L10n.tr("Move %s down", if (c.builtIn != null) L10n.tr(c.name) else c.name), "files.chooser.down." + c.id, enabled = i < shown.lastIndex) { onChange(collections.move(c.id, 1)) }
                    IconButton(Glyph.Close, L10n.tr("Remove %s", if (c.builtIn != null) L10n.tr(c.name) else c.name), "files.chooser.remove." + c.id) { if (c.builtIn != null) onChange(collections.remove(c.id)) else confirm = c }
                }
            }
            PageLabel(L10n.tr("Folders"))
            if (folders.isEmpty()) Text(L10n.tr("No folder added yet"), color = Soft, fontSize = 13.sp, modifier = Modifier.testTag("files.chooser.noFolders"))
            folders.forEach { f ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("files.chooser.folder." + f.name), verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.Folder, Color(0xFF5AA9F6), Modifier.size(24.dp))
                    Text(f.name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                }
            }
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp).testTag("files.chooser.addFolder").clip(RoundedCornerShape(18.dp)).background(Tile).border(1.dp, Faint, RoundedCornerShape(18.dp))
                    .filesFocus("files.chooser.addFolder", onAddFolder).clickable(role = Role.Button, onClick = onAddFolder).padding(12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.Folder, accent, Modifier.size(24.dp))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(L10n.tr("Add a folder"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(L10n.tr("The system picker asks which folder. Take one back in Settings, Home panels."), color = Soft, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    confirm?.let { c ->
        FilesConfirm(L10n.tr("Remove %s?", c.name), L10n.tr("This deletes the collection and its rules. The files themselves are not touched."), L10n.tr("Remove"), L10n.tr("Keep"), accent, "files.chooser.confirm", onPrimary = { onChange(collections.remove(c.id)); confirm = null }, onDismiss = { confirm = null })
    }
}

/** One line that says what a rule picks ("Documents, last 7 days, over 1 MB"). */
@Composable
internal fun ruleSummary(r: FileRule, folders: List<PinnedFolder>): String {
    val parts = ArrayList<String>()
    parts += r.types.map { L10n.tr(it.label) }
    if (r.extensions.isNotEmpty()) parts += r.extensions.joinToString(" ") { ".$it" }
    if (r.nameContains.isNotBlank()) parts += "\"" + r.nameContains.trim() + "\""
    if (r.folders.isNotEmpty()) parts += r.folders.joinToString(", ") { t -> folders.firstOrNull { it.uri == t }?.name ?: L10n.tr("Folder not on this device") }
    if (r.maxAgeDays > 0) parts += L10n.tr(AGES.firstOrNull { it.first == r.maxAgeDays }?.second ?: "Last %d days", r.maxAgeDays)
    if (r.minBytes >= 0) parts += L10n.tr("At least %s", sizeText(r.minBytes))
    if (r.maxBytes >= 0) parts += L10n.tr("At most %s", sizeText(r.maxBytes))
    return if (parts.isEmpty()) L10n.tr("Every file") else parts.joinToString(" · ")
}

private fun sizeText(b: Long) = com.bos.sphere.core.catalog.FileFormat.size(b)
private val AGES = listOf(0 to "Any time", 1 to "Last 24 hours", 7 to "Last 7 days", 30 to "Last 30 days", 90 to "Last 90 days", 365 to "Last year")
private val SIZES = listOf(-1L to "Any", 1024L * 1024 to "1 MB", 10L * 1024 * 1024 to "10 MB", 100L * 1024 * 1024 to "100 MB", 1024L * 1024 * 1024 to "1 GB")

@Composable
private fun EditorField(label: String, value: String, onChange: (String) -> Unit, hint: String, tag: String, keyboard: KeyboardType = KeyboardType.Text) {
    Text(label, color = Soft, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
    BasicTextField(
        value, onChange, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag).background(Tile, RoundedCornerShape(14.dp)).border(1.dp, Faint, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 14.dp),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = Soft, fontSize = 15.sp); inner() } },
    )
}

/** The "New collection" page: name, then visual rules (types, extensions, name contains, folders, age, size). Save is the coloured box; Cancel and Delete are plain text below. */
@Composable
private fun CollectionEditor(
    collections: FileCollections, editId: String?, folders: List<PinnedFolder>, accent: Color,
    onSave: (CustomCollection) -> Unit, onDelete: (String) -> Unit, onBack: () -> Unit,
) {
    val existing = editId?.let { id -> collections.custom.firstOrNull { it.id == id } }
    val r0 = existing?.rule ?: FileRule()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var types by remember { mutableStateOf(r0.types) }
    var exts by remember { mutableStateOf(r0.extensions.joinToString(", ")) }
    var contains by remember { mutableStateOf(r0.nameContains) }
    var picked by remember { mutableStateOf(r0.folders) }
    var age by remember { mutableStateOf(r0.maxAgeDays) }
    var min by remember { mutableStateOf(r0.minBytes) }
    var max by remember { mutableStateOf(r0.maxBytes) }
    var confirmDelete by remember { mutableStateOf(false) }
    val rule = FileRule(types, FileText.parseExtensions(exts), contains.trim(), picked, emptySet(), age, min, max)
    val canSave = name.isNotBlank() && !rule.isEmpty
    val folderChoices = folders.map { it.uri to it.name } + picked.filter { p -> folders.none { it.uri == p } }.map { it to L10n.tr("Folder not on this device") }
    Column(Modifier.fillMaxSize()) {
        PageBar(L10n.tr(if (existing == null) "New collection" else "Edit collection"), "files.editor", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).testTag("files.editor")) {
            EditorField(L10n.tr("Name"), name, { name = it.take(40) }, L10n.tr("For example Work PDFs"), "files.editor.name")
            PageLabel(L10n.tr("File types"))
            Cards { TypeGroup.entries.forEach { t -> ChoiceCard(L10n.tr(t.label), t in types, true, "files.editor.type." + t.name, accent, { types = if (t in types) types - t else types + t }) { GlyphArt(glyphOfType(t)) } } }
            EditorField(L10n.tr("Extensions"), exts, { exts = it.take(80) }, L10n.tr("pdf, docx, txt"), "files.editor.ext")
            EditorField(L10n.tr("Name contains"), contains, { contains = it.take(60) }, L10n.tr("invoice"), "files.editor.contains")
            PageLabel(L10n.tr("Only in these folders"))
            if (folderChoices.isEmpty()) Text(L10n.tr("Add a folder first to limit a collection to it."), color = Soft, fontSize = 13.sp)
            Cards { folderChoices.forEachIndexed { i, (uri, label) -> ChoiceCard(label, uri in picked, true, "files.editor.folder.$i", accent, { picked = if (uri in picked) picked - uri else picked + uri }, width = 96.dp) { GlyphArt(Glyph.Folder, Color(0xFF5AA9F6)) } } }
            PageLabel(L10n.tr("Changed"))
            Cards { AGES.forEach { (d, label) -> ChoiceCard(L10n.tr(label), age == d, false, "files.editor.age.$d", accent, { age = d }) { GlyphArt(if (d == 0) Glyph.Layers else Glyph.Calendar) } } }
            PageLabel(L10n.tr("Size at least"))
            Cards { SIZES.forEach { (b, label) -> ChoiceCard(if (b < 0) L10n.tr(label) else label, min == b, false, "files.editor.min.$b", accent, { min = b }) { GlyphArt(Glyph.Large) } } }
            PageLabel(L10n.tr("Size at most"))
            Cards { SIZES.forEach { (b, label) -> ChoiceCard(if (b < 0) L10n.tr(label) else label, max == b, false, "files.editor.max.$b", accent, { max = b }) { GlyphArt(Glyph.Large) } } }
            Text(L10n.tr("A file must fit every rule you set. Types and extensions together count as one rule: either one fits."), color = Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
            if (!canSave) Text(L10n.tr("Give it a name and at least one rule."), color = Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp).testTag("files.editor.hint"))
            PrimaryBox(L10n.tr("Save collection"), accent, "files.editor.save", Modifier.padding(top = 18.dp).fillMaxWidth(), enabled = canSave) {
                onSave(CustomCollection(existing?.id ?: FileCollections.newId(collections.custom.map { it.id }.toSet()), name.trim(), rule))
            }
            SecondaryText(L10n.tr("Cancel"), Soft, "files.editor.cancel", Modifier.fillMaxWidth(), onClick = onBack)
            if (existing != null) SecondaryText(L10n.tr("Delete collection"), Color(0xFFFF6B6B), "files.editor.delete", Modifier.fillMaxWidth()) { confirmDelete = true }
        }
    }
    if (confirmDelete && existing != null) FilesConfirm(L10n.tr("Remove %s?", existing.name), L10n.tr("This deletes the collection and its rules. The files themselves are not touched."), L10n.tr("Remove"), L10n.tr("Keep"), accent, "files.editor.confirm", onPrimary = { confirmDelete = false; onDelete(existing.id) }, onDismiss = { confirmDelete = false })
}

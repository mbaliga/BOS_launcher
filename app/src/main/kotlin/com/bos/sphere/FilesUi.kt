package com.bos.sphere

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.semantics.heading
import com.bos.sphere.core.catalog.ArrangedFiles
import com.bos.sphere.core.catalog.FileSection
import com.bos.sphere.core.catalog.FileSourceIds
import com.bos.sphere.core.catalog.FilesSortText
import com.bos.sphere.core.catalog.GroupKey
import com.bos.sphere.core.catalog.ViewOptions
import com.bos.sphere.core.input.FocusKind
import com.bos.sphere.feature.grid.launcherItem
import com.bos.sphere.core.catalog.FileFormat
import com.bos.sphere.core.catalog.FileKind
import com.bos.sphere.core.catalog.FileNames
import com.bos.sphere.core.catalog.FilesViewMode
import com.bos.sphere.core.catalog.PinnedFolder
import com.bos.sphere.core.catalog.RecentFile
import com.bos.sphere.core.catalog.RecentFilesModel
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.gate
import com.bos.sphere.core.design.rememberMotionPrefs
import kotlinx.coroutines.CancellationException
import java.text.DateFormat
import java.util.Date

/**
 * W31 (filesViews): the Files tab as a list or a grid of real previews (the way the Files and Preview apps show them): photos, a video frame with
 * its length, a PDF's first page on a sheet, album art, an app's own icon, and a coloured type tile with the extension for anything else. A
 * long-press opens a large preview. The data is what the tab already lists; previews come through [FileThumbs] (off the main thread, cached,
 * cancelled with the cell that scrolls away).
 */

/** The tile colour of a kind (the generic icon's accent). */
internal fun kindColor(k: FileKind): Color = when (k) {
    FileKind.Image -> Color(0xFF3F8CFF); FileKind.Video -> Color(0xFF8E5CF0); FileKind.Audio -> Color(0xFFE5557F)
    FileKind.Pdf -> Color(0xFFE5483D); FileKind.Apk -> Color(0xFF3DB06A); FileKind.Archive -> Color(0xFFD9A233)
    FileKind.Document -> Color(0xFF3F7FE0); FileKind.Spreadsheet -> Color(0xFF2FA05A); FileKind.Slides -> Color(0xFFE8803A)
    FileKind.Text -> Color(0xFF8A94A6); FileKind.Other -> Color(0xFF7C8699)
}

internal enum class Glyph { Page, Play, Note, Picture, Folder, Grid, List, Clock, Download, Archive, Shot, Large, Plus, Sort, Back, Up, Down, Close, Calendar, Layers, Check }

@Composable
internal fun GlyphIcon(g: Glyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        when (g) {
            Glyph.Page -> {
                val p = Path().apply {
                    moveTo(w * .22f, h * .1f); lineTo(w * .62f, h * .1f); lineTo(w * .8f, h * .3f); lineTo(w * .8f, h * .9f); lineTo(w * .22f, h * .9f); close()
                }
                drawPath(p, color)
                drawPath(Path().apply { moveTo(w * .62f, h * .1f); lineTo(w * .62f, h * .3f); lineTo(w * .8f, h * .3f); close() }, color.copy(alpha = .55f))
            }
            Glyph.Play -> drawPath(Path().apply { moveTo(w * .3f, h * .18f); lineTo(w * .82f, h * .5f); lineTo(w * .3f, h * .82f); close() }, color)
            Glyph.Note -> {
                drawCircle(color, w * .17f, Offset(w * .36f, h * .76f))
                drawRect(color, Offset(w * .48f, h * .16f), Size(w * .07f, h * .6f))
                drawPath(Path().apply { moveTo(w * .55f, h * .16f); lineTo(w * .82f, h * .3f); lineTo(w * .82f, h * .46f); lineTo(w * .55f, h * .34f); close() }, color)
            }
            Glyph.Picture -> {
                drawRoundRect(color.copy(alpha = .35f), Offset(w * .1f, h * .18f), Size(w * .8f, h * .64f), androidx.compose.ui.geometry.CornerRadius(w * .08f))
                drawPath(Path().apply { moveTo(w * .1f, h * .82f); lineTo(w * .4f, h * .44f); lineTo(w * .62f, h * .7f); lineTo(w * .74f, h * .56f); lineTo(w * .9f, h * .82f); close() }, color)
                drawCircle(color, w * .08f, Offset(w * .72f, h * .34f))
            }
            Glyph.Folder -> {
                drawRoundRect(color.copy(alpha = .7f), Offset(w * .08f, h * .2f), Size(w * .4f, h * .2f), androidx.compose.ui.geometry.CornerRadius(w * .05f))
                drawRoundRect(color, Offset(w * .08f, h * .3f), Size(w * .84f, h * .52f), androidx.compose.ui.geometry.CornerRadius(w * .07f))
            }
            Glyph.Grid -> for (i in 0..1) for (j in 0..1)
                drawRoundRect(color, Offset(w * (.16f + i * .38f), h * (.16f + j * .38f)), Size(w * .30f, h * .30f), androidx.compose.ui.geometry.CornerRadius(w * .05f))
            Glyph.Clock -> {
                drawCircle(color, w * .4f, Offset(w * .5f, h * .5f), style = Stroke(w * .08f))
                drawLine(color, Offset(w * .5f, h * .5f), Offset(w * .5f, h * .26f), w * .08f, StrokeCap.Round)
                drawLine(color, Offset(w * .5f, h * .5f), Offset(w * .68f, h * .6f), w * .08f, StrokeCap.Round)
            }
            Glyph.Download -> {
                drawLine(color, Offset(w * .5f, h * .14f), Offset(w * .5f, h * .62f), w * .09f, StrokeCap.Round)
                drawPath(Path().apply { moveTo(w * .28f, h * .46f); lineTo(w * .5f, h * .7f); lineTo(w * .72f, h * .46f) }, color, style = Stroke(w * .09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(color, Offset(w * .2f, h * .86f), Offset(w * .8f, h * .86f), w * .09f, StrokeCap.Round)
            }
            Glyph.Archive -> {
                drawRoundRect(color, Offset(w * .16f, h * .2f), Size(w * .68f, h * .64f), androidx.compose.ui.geometry.CornerRadius(w * .07f))
                for (j in 0..3) drawRect(Color(0x66000000), Offset(w * (.44f + (j % 2) * .1f), h * (.2f + j * .1f)), Size(w * .1f, h * .07f))
            }
            Glyph.Shot -> {
                drawRoundRect(color, Offset(w * .3f, h * .08f), Size(w * .4f, h * .84f), androidx.compose.ui.geometry.CornerRadius(w * .07f), style = Stroke(w * .07f))
                drawRect(color.copy(alpha = .5f), Offset(w * .38f, h * .22f), Size(w * .24f, h * .5f))
            }
            Glyph.Large -> {
                drawRoundRect(color.copy(alpha = .4f), Offset(w * .1f, h * .62f), Size(w * .8f, h * .22f), androidx.compose.ui.geometry.CornerRadius(w * .05f))
                drawRoundRect(color.copy(alpha = .7f), Offset(w * .1f, h * .36f), Size(w * .8f, h * .22f), androidx.compose.ui.geometry.CornerRadius(w * .05f))
                drawRoundRect(color, Offset(w * .1f, h * .1f), Size(w * .8f, h * .22f), androidx.compose.ui.geometry.CornerRadius(w * .05f))
            }
            Glyph.Plus -> {
                drawLine(color, Offset(w * .5f, h * .2f), Offset(w * .5f, h * .8f), w * .1f, StrokeCap.Round)
                drawLine(color, Offset(w * .2f, h * .5f), Offset(w * .8f, h * .5f), w * .1f, StrokeCap.Round)
            }
            Glyph.Sort -> for (j in 0..2) drawLine(color, Offset(w * .18f, h * (.28f + j * .22f)), Offset(w * (.82f - j * .2f), h * (.28f + j * .22f)), w * .09f, StrokeCap.Round)
            Glyph.Back -> drawPath(Path().apply { moveTo(w * .62f, h * .2f); lineTo(w * .3f, h * .5f); lineTo(w * .62f, h * .8f) }, color, style = Stroke(w * .1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            Glyph.Up -> drawPath(Path().apply { moveTo(w * .22f, h * .62f); lineTo(w * .5f, h * .34f); lineTo(w * .78f, h * .62f) }, color, style = Stroke(w * .1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            Glyph.Down -> drawPath(Path().apply { moveTo(w * .22f, h * .38f); lineTo(w * .5f, h * .66f); lineTo(w * .78f, h * .38f) }, color, style = Stroke(w * .1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            Glyph.Close -> {
                drawLine(color, Offset(w * .26f, h * .26f), Offset(w * .74f, h * .74f), w * .09f, StrokeCap.Round)
                drawLine(color, Offset(w * .74f, h * .26f), Offset(w * .26f, h * .74f), w * .09f, StrokeCap.Round)
            }
            Glyph.Calendar -> {
                drawRoundRect(color, Offset(w * .14f, h * .22f), Size(w * .72f, h * .64f), androidx.compose.ui.geometry.CornerRadius(w * .08f), style = Stroke(w * .07f))
                drawRect(color, Offset(w * .14f, h * .22f), Size(w * .72f, h * .18f))
                for (i in 0..2) drawCircle(color, w * .04f, Offset(w * (.32f + i * .18f), h * .62f))
            }
            Glyph.Layers -> for (j in 0..2) drawRoundRect(color.copy(alpha = .4f + j * .3f), Offset(w * (.14f + j * .06f), h * (.14f + j * .16f)), Size(w * .6f, h * .3f), androidx.compose.ui.geometry.CornerRadius(w * .06f))
            Glyph.Check -> drawPath(Path().apply { moveTo(w * .2f, h * .52f); lineTo(w * .43f, h * .74f); lineTo(w * .8f, h * .28f) }, color, style = Stroke(w * .11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            Glyph.List -> for (j in 0..2) {
                drawCircle(color, w * .045f, Offset(w * .2f, h * (.25f + j * .25f)))
                drawRoundRect(color, Offset(w * .32f, h * (.25f + j * .25f) - h * .035f), Size(w * .52f, h * .07f), androidx.compose.ui.geometry.CornerRadius(w * .03f))
            }
        }
    }
}

private fun glyphOf(k: FileKind) = when (k) { FileKind.Video -> Glyph.Play; FileKind.Audio -> Glyph.Note; FileKind.Image -> Glyph.Picture; else -> Glyph.Page }

/** The generic tile (the iOS-style file icon): the kind's colour, a glyph, and the extension. Also the fallback when a preview is missing or fails. */
@Composable
internal fun TypeTile(kind: FileKind, label: String, modifier: Modifier = Modifier, labelSp: Float = 9f, folder: Boolean = false) {
    val c = if (folder) Color(0xFF5AA9F6) else kindColor(kind)
    Box(modifier.background(c.copy(alpha = .22f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            GlyphIcon(if (folder) Glyph.Folder else glyphOf(kind), c, Modifier.fillMaxWidth(if (labelSp >= 11f) .5f else .55f).aspectRatio(1f))
            if (!folder) Text(label, color = c, fontSize = labelSp.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

/** A preview of [file]: neutral placeholder while loading, a fade-in when ready (instant under reduced motion), the type tile when none. */
@Composable
internal fun FileThumb(file: RecentFile, kind: FileKind, reqPx: Int, modifier: Modifier = Modifier, labelSp: Float = 9f, badges: Boolean = true, fit: ContentScale = ContentScale.Crop) {
    val loader = LocalFileThumbs.current
    val key = FileThumbCache.key(file, reqPx, reqPx)
    val cached = remember(key) { FileThumbCache.cache[key] }
    var data by remember(key) { mutableStateOf(cached) }
    if (kind.previewable && data == null) LaunchedEffect(key) {
        val d = try { loader.load(file, kind, reqPx, reqPx) } catch (e: CancellationException) { throw e } catch (e: Throwable) { ThumbData(null) }
        FileThumbCache.cache.put(key, d)
        data = d
    }
    val tag = "files.thumb." + file.name
    val d = data
    val bmp = d?.bitmap
    Box(modifier.testTag(tag).clip(RoundedCornerShape(10.dp))) {
        when {
            !kind.previewable || (d != null && bmp == null) -> TypeTile(kind, FileNames.tileLabel(file.name), Modifier.fillMaxSize().testTag("$tag.tile"), labelSp)
            bmp == null -> Box(Modifier.fillMaxSize().background(Color(0x14FFFFFF)).testTag("$tag.placeholder"))
            else -> {
                var shown by remember(key) { mutableStateOf(cached != null) }
                LaunchedEffect(key) { shown = true }
                val a by animateFloatAsState(if (shown) 1f else 0f, rememberMotionPrefs().gate(tween(180)), label = "thumbFade")
                val image = remember(bmp) { bmp.asImageBitmap() }
                Box(Modifier.fillMaxSize().alpha(a).testTag("$tag.loaded")) {
                    when (kind) {
                        FileKind.Pdf -> Box(Modifier.fillMaxSize().padding(6.dp).shadow(3.dp, RoundedCornerShape(2.dp)).background(Color.White).border(0.5.dp, Color(0x33000000), RoundedCornerShape(2.dp))) {
                            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                        }
                        FileKind.Apk -> Box(Modifier.fillMaxSize().background(kindColor(kind).copy(alpha = .22f)), contentAlignment = Alignment.Center) {
                            Image(image, null, Modifier.fillMaxSize(.66f), contentScale = ContentScale.Fit)
                        }
                        else -> {
                            Image(image, null, Modifier.fillMaxSize(), contentScale = fit)
                            if (kind == FileKind.Video && badges) {
                                Box(Modifier.align(Alignment.Center).size(24.dp).background(Color(0x99000000), CircleShape), contentAlignment = Alignment.Center) {
                                    GlyphIcon(Glyph.Play, Color.White, Modifier.size(14.dp))
                                }
                                FileFormat.duration(d.durationMs).takeIf { it.isNotEmpty() }?.let {
                                    Text(it, color = Color.White, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp).background(Color(0xAA000000), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp).testTag("$tag.duration"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The grid/list switch: a 48 dp target that shows the layout it switches to and announces the current one. */
@Composable
internal fun FilesViewToggle(mode: FilesViewMode, ink: Color, onToggle: () -> Unit) {
    val toGrid = mode == FilesViewMode.List
    val action = L10n.tr(if (toGrid) "Switch to grid view" else "Switch to list view")
    val state = L10n.tr(if (toGrid) "List view" else "Grid view")
    Box(
        Modifier.size(48.dp).testTag("files.viewToggle")
            .filesFocus("files.viewToggle", onToggle)
            .semantics(mergeDescendants = true) { contentDescription = action; stateDescription = state }
            .clickable(role = Role.Button, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(if (toGrid) Glyph.Grid else Glyph.List, ink, Modifier.size(24.dp)) }
}

@Composable
private fun kindLabel(k: FileKind) = L10n.tr(k.label)

@Composable
private fun NameLine(name: String, color: Color, sp: Float, modifier: Modifier = Modifier) {
    val (stem, ext) = remember(name) { FileNames.split(name) }
    Row(modifier) {
        Text(stem, color = color, fontSize = sp.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (ext.isNotEmpty()) Text(ext, color = color, fontSize = sp.sp, maxLines = 1, softWrap = false)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListRow(f: RecentFile, now: Long, bright: Color, dim: Color, onOpen: () -> Unit, onPreview: () -> Unit) {
    val kind = remember(f) { FileKind.classify(f.name, f.mime) }
    val px = with(LocalDensity.current) { bucket(48.dp.toPx()) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("files.row." + f.name)
            .combinedClickable(onLongClickLabel = L10n.tr("Preview"), onLongClick = onPreview, role = Role.Button, onClick = onOpen)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileThumb(f, kind, px, Modifier.size(48.dp), 9f, badges = false)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            NameLine(f.name, bright, 14f)
            Text(FileFormat.meta(localAge(now, f.modifiedMs), f.sizeBytes, kindLabel(kind)), color = dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridCell(f: RecentFile, now: Long, bright: Color, dim: Color, onOpen: () -> Unit, onPreview: () -> Unit) {
    val kind = remember(f) { FileKind.classify(f.name, f.mime) }
    val px = with(LocalDensity.current) { bucket(144.dp.toPx()) }
    Column(
        Modifier.fillMaxWidth().testTag("files.cell." + f.name)
            .combinedClickable(onLongClickLabel = L10n.tr("Preview"), onLongClick = onPreview, role = Role.Button, onClick = onOpen)
            .padding(4.dp),
    ) {
        FileThumb(f, kind, px, Modifier.fillMaxWidth().aspectRatio(1f), 12f)
        Text(f.name, color = bright, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(FileFormat.meta(localAge(now, f.modifiedMs), f.sizeBytes, ""), color = dim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Thumbnail request sizes are rounded to 32 px steps so a few sizes share cache entries. */
internal fun bucket(px: Float): Int = ((px / 32f).toInt() + 1) * 32

/** One chip of the source switcher: All, a collection, or a folder. [id] is the source id (`all`, `b:documents`, `c:7f3a`, `f:<tree>`). */
internal class FilesChip(val id: String, val label: String, val glyph: Glyph)

/** A focus-graph node for a clickable (keyboard Enter and controller confirm do what a tap does). Put it directly before the clickable. */
internal fun Modifier.filesFocus(id: String, onActivate: () -> Unit): Modifier = launcherItem(id, FocusKind.Item, focusable = false, onActivate = onActivate)

/** The source switcher: a row of chips (All, the collections, the folders) that scrolls sideways and keeps the selected one in sight, and the Add tile, which stays put beside it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FilesSourceBar(chips: List<FilesChip>, selected: String, bright: Color, dim: Color, accent: Color, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).testTag("files.sources"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            chips.forEach { c ->
                val on = c.id == selected
                val shape = RoundedCornerShape(50)
                val inSight = remember { BringIntoViewRequester() }
                if (on) LaunchedEffect(selected, chips.size) { inSight.bringIntoView() }
                Box(
                    Modifier.heightIn(min = 48.dp).bringIntoViewRequester(inSight).testTag("files.source." + c.id).filesFocus("files.src." + c.id) { onSelect(c.id) }
                        .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(c.id) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.height(38.dp).background(if (on) accent.copy(alpha = .30f) else Color(0x14FFFFFF), shape).border(1.dp, if (on) accent else Color.Transparent, shape).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GlyphIcon(c.glyph, if (on) bright else dim, Modifier.size(16.dp))
                        Text(c.label, color = if (on) bright else dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
                    }
                }
            }
        }
        val addLabel = L10n.tr("Add a folder or a collection")
        Box(
            Modifier.padding(start = 8.dp).heightIn(min = 48.dp).testTag("files.add").filesFocus("files.add", onAdd)
                .semantics { contentDescription = addLabel }.clickable(role = Role.Button, onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier.height(38.dp).border(1.dp, accent.copy(alpha = .7f), RoundedCornerShape(50)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GlyphIcon(Glyph.Plus, accent, Modifier.size(16.dp))
                Text(L10n.tr("Add"), color = accent, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

/** The words of the current sort and grouping ("Date modified, newest first, grouped by Date"), for the control's state description. */
@Composable
internal fun optionsSummary(o: ViewOptions): String {
    val base = L10n.tr(FilesSortText.sortLabel(o.sort)) + ", " + L10n.tr(FilesSortText.orderLabel(o.sort, o.ascending))
    return if (o.group == GroupKey.None) base else base + ", " + L10n.tr("Grouped by %s", L10n.tr(FilesSortText.groupLabel(o.group)))
}

@Composable
private fun SectionHeader(s: FileSection, dim: Color, ground: Color, modifier: Modifier = Modifier) {
    val title = if (s.literal) s.title else L10n.tr(s.title)
    val spoken = title + ", " + (if (s.count == 1) L10n.tr("1 file") else L10n.tr("%d files", s.count))
    Row(
        modifier.fillMaxWidth().background(ground).heightIn(min = 32.dp).padding(top = 6.dp, bottom = 2.dp).testTag("files.section." + s.id)
            .semantics(mergeDescendants = true) { heading(); contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = dim, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text(s.count.toString(), color = dim.copy(alpha = .8f), fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp).testTag("files.section.count." + s.id))
    }
}

/** Why a source is empty and what to do about it: it names the next step (add a folder, edit the rules) and offers it as the coloured button. */
@Composable
private fun FilesEmptyState(hasFolders: Boolean, collection: Boolean, bright: Color, ink: Color, dim: Color, accent: Color, onChoose: () -> Unit, onAdd: () -> Unit, onEdit: (() -> Unit)?) {
    val kind = if (!hasFolders) "nofolders" else if (collection) "nomatch" else "nofiles"
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 8.dp).testTag("files.empty").semantics { stateDescription = kind }, horizontalAlignment = Alignment.CenterHorizontally) {
        GlyphIcon(if (!hasFolders) Glyph.Folder else Glyph.Layers, accent, Modifier.size(48.dp))
        Text(
            L10n.tr(if (!hasFolders) "No folder added yet" else if (collection) "Nothing matches this collection yet" else "No files here yet"),
            color = bright, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp).testTag("files.empty.title"),
        )
        Text(
            L10n.tr(if (!hasFolders) "Choose a folder and its files show up here. Collections such as Recents and Documents are built from the folders you add; nothing else is read." else if (collection) "Files from your folders that fit the rules of this collection appear here. Add a folder, or change the rules." else "Files in this folder appear here once it has some."),
            color = ink, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp).testTag("files.empty.hint"),
        )
        val primary: Pair<String, () -> Unit> = if (!hasFolders) "Choose a folder" to onChoose else if (collection && onEdit != null) "Edit the rules" to onEdit else "Choose a folder" to onChoose
        PrimaryBox(L10n.tr(primary.first), accent, "files.empty.primary", Modifier.padding(top = 16.dp).fillMaxWidth(.8f), onClick = primary.second)
        SecondaryText(L10n.tr(if (!hasFolders) "Browse collections" else if (collection && onEdit != null) "Choose a folder" else "Browse collections"), dim, "files.empty.secondary", onClick = if (!hasFolders || !(collection && onEdit != null)) onAdd else onChoose)
    }
}

/** The dialog button pair every Files page uses: a coloured box for the main action, plain text for the other one below it. */
@Composable
internal fun PrimaryBox(text: String, accent: Color, tag: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 52.dp).testTag(tag).background(if (enabled) accent else accent.copy(alpha = .3f), RoundedCornerShape(16.dp))
            .filesFocus("$tag.focus", { if (enabled) onClick() }).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = HyleColors.OnAccent, fontSize = 15.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
}

@Composable
internal fun SecondaryText(text: String, color: Color, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 14.sp, textAlign = TextAlign.Center,
        modifier = modifier.heightIn(min = 48.dp).testTag(tag).filesFocus("$tag.focus", onClick).clickable(role = Role.Button, onClick = onClick).wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 16.dp),
    )
}

/**
 * The whole tab: the source switcher, a line with the count and the sort and view buttons, and below them the files as a lazy list or a lazy grid, in
 * sections when the view is grouped (sticky headers in the list, a pinned header in the grid). [arranged] null means still loading.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FilesPanelContent(
    chips: List<FilesChip>, selected: String, arranged: ArrangedFiles?, indexing: Boolean, hasFolders: Boolean,
    options: ViewOptions, mode: FilesViewMode, now: Long,
    bright: Color, ink: Color, dim: Color, accent: Color,
    onSelect: (String) -> Unit, onAdd: () -> Unit, onToggle: () -> Unit, onSortGroup: () -> Unit, onChoose: () -> Unit,
    onOpenFolder: (() -> Unit)?, onEdit: (() -> Unit)?, onFileOpen: (RecentFile) -> Unit,
    ground: Color = HyleColors.Surface,
) {
    var preview by remember { mutableStateOf<RecentFile?>(null) }
    val grid = mode == FilesViewMode.Grid
    val grouped = options.group != GroupKey.None
    val isCollection = selected != FileSourceIds.ALL && !FileSourceIds.isFolder(selected)
    Column(Modifier.fillMaxSize()) {
        FilesSourceBar(chips, selected, bright, dim, accent, onSelect, onAdd)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val total = arranged?.total ?: 0
            val summary = when {
                arranged == null -> L10n.tr(if (indexing) "Looking through your folders..." else "Loading...")
                arranged.capped -> L10n.tr("Showing the first %d of %d files", arranged.shown, arranged.total)
                total == 0 -> ""
                total == 1 -> L10n.tr("1 file")
                else -> L10n.tr("%d files", total)
            }.let { if (arranged != null && indexing) (if (it.isEmpty()) "" else "$it · ") + L10n.tr("Updating...") else it }
            Text(summary, color = dim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("files.summary"))
            if (onEdit != null) SecondaryText(L10n.tr("Edit"), accent, "files.edit", onClick = onEdit)
            if (onOpenFolder != null) SecondaryText(L10n.tr("Open folder"), accent, "files.openFolder", onClick = onOpenFolder)
            val sortLabel = L10n.tr("Sort and group")
            val sortState = optionsSummary(options)
            Box(
                Modifier.size(48.dp).testTag("files.sortGroup").filesFocus("files.sortGroup", onSortGroup)
                    .semantics(mergeDescendants = true) { contentDescription = sortLabel; stateDescription = sortState }.clickable(role = Role.Button, onClick = onSortGroup),
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(Glyph.Sort, bright, Modifier.size(24.dp)) }
            FilesViewToggle(mode, bright, onToggle)
        }
        Box(Modifier.fillMaxSize().weight(1f)) {
            when {
                arranged == null -> Unit
                arranged.total == 0 -> Column(Modifier.verticalScroll(rememberScrollState())) { FilesEmptyState(hasFolders, isCollection, bright, ink, dim, accent, onChoose, onAdd, onEdit) }
                grid -> {
                    val state = rememberLazyGridState()
                    // Grid positions of the section headers, to pin the current one on top.
                    val headerAt = remember(arranged, grouped) {
                        val m = ArrayList<Pair<Int, FileSection>>(); var i = 0
                        if (grouped) arranged.sections.forEach { m += i to it; i += 1 + it.files.size }
                        m
                    }
                    LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize().testTag("files.grid"), state = state) {
                        arranged.sections.forEach { sec ->
                            if (grouped) item(key = "h:" + sec.id, span = { GridItemSpan(maxLineSpan) }) { SectionHeader(sec, dim, ground) }
                            items(sec.files, key = { it.uri }) { f -> FileGridCell(f, now, bright, dim, { onFileOpen(f) }, { preview = f }) }
                        }
                    }
                    if (grouped) {
                        val pinned by remember(headerAt) { derivedStateOf { headerAt.lastOrNull { it.first <= state.firstVisibleItemIndex }?.takeIf { state.firstVisibleItemIndex > it.first || state.firstVisibleItemScrollOffset > 0 }?.second } }
                        pinned?.let { SectionHeader(it, dim, ground, Modifier.align(Alignment.TopStart).testTag("files.pinned")) }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize().testTag("files.list")) {
                    arranged.sections.forEach { sec ->
                        if (grouped) stickyHeader(key = "h:" + sec.id) { SectionHeader(sec, dim, ground) }
                        items(sec.files, key = { it.uri }) { f -> FileListRow(f, now, bright, dim, { onFileOpen(f) }, { preview = f }) }
                    }
                }
            }
        }
    }
    preview?.let { f -> FilePreviewDialog(f, bright, dim, accent, onOpen = { preview = null; onFileOpen(f) }, onDismiss = { preview = null }) }
}

/** Quick Look: a large centred preview with the name, kind, size and date, an Open button; Back, a tap outside or a swipe down closes it. */
@Composable
internal fun FilePreviewDialog(f: RecentFile, bright: Color, dim: Color, accent: Color, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val kind = remember(f) { FileKind.classify(f.name, f.mime) }
    val px = with(LocalDensity.current) { bucket(minOf(320.dp.toPx(), 960f)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0xB0000000)).testTag("files.preview.scrim")
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .pointerInput(Unit) { var dy = 0f; detectVerticalDragGestures(onDragStart = { dy = 0f }, onDragEnd = { if (dy > 120f) onDismiss() }) { _, d -> dy += d } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth(.9f).widthIn(max = 420.dp).testTag("files.preview")
                    .background(Color(0xFF1B1E24), RoundedCornerShape(20.dp)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FileThumb(f, kind, px, Modifier.fillMaxWidth().heightIn(max = 340.dp).aspectRatio(if (kind == FileKind.Pdf) .78f else 1f), 16f, fit = ContentScale.Fit)
                Text(f.name, color = bright, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 12.dp).testTag("files.preview.name"))
                val details = listOf(L10n.tr(kind.label), FileFormat.size(f.sizeBytes), DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(f.modifiedMs))).filter { it.isNotEmpty() }.joinToString(" · ")
                Text(details, color = dim, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp).testTag("files.preview.details"))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(L10n.tr("Open"), color = accent, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 72.dp).testTag("files.preview.open").clickable(role = Role.Button, onClick = onOpen).wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 12.dp), textAlign = TextAlign.Center)
                    Text(L10n.tr("Close"), color = dim, fontSize = 14.sp, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 72.dp).testTag("files.preview.close").clickable(role = Role.Button, onClick = onDismiss).wrapContentHeight(Alignment.CenterVertically).padding(horizontal = 12.dp), textAlign = TextAlign.Center)
                }
            }
        }
    }
}

internal fun openRecentFile(context: android.content.Context, f: RecentFile) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(f.uri), f.mime.ifEmpty { "*/*" })
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}

/** W33: the relative age in the user's language (the catalog model's text is English only). */
internal fun localAge(nowMs: Long, thenMs: Long): String {
    val d = (nowMs - thenMs).coerceAtLeast(0) / 1000
    return when {
        d < 60 -> com.bos.sphere.core.design.L10n.tr("Just now")
        d < 3600 -> com.bos.sphere.core.design.L10n.tr("%d min ago", d / 60)
        d < 86_400 -> com.bos.sphere.core.design.L10n.tr("%d h ago", d / 3600)
        d < 172_800 -> com.bos.sphere.core.design.L10n.tr("Yesterday")
        else -> com.bos.sphere.core.design.L10n.tr("%d days ago", d / 86_400)
    }
}

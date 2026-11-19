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

private enum class Glyph { Page, Play, Note, Picture, Folder, Grid, List }

@Composable
private fun GlyphIcon(g: Glyph, color: Color, modifier: Modifier = Modifier) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderEntry(f: PinnedFolder, sub: String, grid: Boolean, bright: Color, dim: Color, onOpen: () -> Unit, onLong: () -> Unit) {
    val tile = Modifier.background(Color.Transparent)
    if (grid) Column(Modifier.fillMaxWidth().testTag("files.folder." + f.name).combinedClickable(onLongClick = onLong, role = Role.Button, onClick = onOpen).padding(4.dp)) {
        TypeTile(FileKind.Other, "", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)), 12f, folder = true)
        Text(f.name, color = bright, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text(sub, color = dim, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    } else Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("files.folder." + f.name).combinedClickable(onLongClick = onLong, role = Role.Button, onClick = onOpen).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TypeTile(FileKind.Other, "", tile.size(48.dp).clip(RoundedCornerShape(10.dp)), 9f, folder = true)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(f.name, color = bright, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = dim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Thumbnail request sizes are rounded to 32 px steps so a few sizes share cache entries. */
internal fun bucket(px: Float): Int = ((px / 32f).toInt() + 1) * 32

/** The whole tab: header with the toggle, the folders, "Choose a folder", and the recent files, as a lazy list or a lazy grid. */
@Composable
internal fun FilesPanelContent(
    folders: List<PinnedFolder>, files: List<RecentFile>, only: String?, mode: FilesViewMode, now: Long,
    bright: Color, ink: Color, dim: Color, accent: Color,
    onToggle: () -> Unit, onChoose: () -> Unit, onFolderOpen: (PinnedFolder) -> Unit, onFolderLong: (PinnedFolder) -> Unit, onFileOpen: (RecentFile) -> Unit,
) {
    var preview by remember { mutableStateOf<RecentFile?>(null) }
    val grid = mode == FilesViewMode.Grid
    fun folderSub(f: PinnedFolder) = if (only == f.uri) L10n.tr("Pinned folder") + " · " + L10n.tr("Showing only this folder (long-press it again for all)") else L10n.tr("Pinned folder")
    val header: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(L10n.tr("Folders"), color = dim, fontSize = 11.sp, modifier = Modifier.weight(1f))
            FilesViewToggle(mode, bright, onToggle)
        }
        if (folders.isEmpty()) Text(L10n.tr("No folder chosen yet. Pick one and its newest files show here; nothing else is read."), color = ink, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
    }
    val choose: @Composable () -> Unit = {
        Text(L10n.tr("Choose a folder"), color = accent, fontSize = 13.sp, modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onChoose).wrapContentHeight(Alignment.CenterVertically))
        Text(L10n.tr("Recent files"), color = dim, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
    if (grid) LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.testTag("files.grid")) {
        item(key = "hdr", span = { GridItemSpan(maxLineSpan) }) { Column { header() } }
        items(folders, key = { "d" + it.uri }) { f -> FolderEntry(f, folderSub(f), true, bright, dim, { onFolderOpen(f) }, { onFolderLong(f) }) }
        item(key = "choose", span = { GridItemSpan(maxLineSpan) }) { Column { choose() } }
        items(files, key = { it.uri }) { f -> FileGridCell(f, now, bright, dim, { onFileOpen(f) }, { preview = f }) }
    } else LazyColumn(Modifier.testTag("files.list")) {
        item(key = "hdr") { Column { header() } }
        items(folders, key = { "d" + it.uri }) { f -> FolderEntry(f, folderSub(f), false, bright, dim, { onFolderOpen(f) }, { onFolderLong(f) }) }
        item(key = "choose") { Column { choose() } }
        items(files, key = { it.uri }) { f -> FileListRow(f, now, bright, dim, { onFileOpen(f) }, { preview = f }) }
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

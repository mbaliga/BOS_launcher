package com.bos.sphere.feature.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bos.sphere.core.catalog.Folder
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n

/** HOM-04: a folder on home: up to four of its icons on a rounded tile. Tap, click or Enter opens it. */
@Composable
fun FolderTile(folder: Folder, resolve: (String) -> AppEntry?, size: Dp, onOpen: () -> Unit, modifier: Modifier = Modifier, interactive: Boolean = true) {
    val shape = RoundedCornerShape(22)
    Box(
        modifier.size(size).clip(shape).background(Color(0x26FFFFFF), shape)
            .then(
                if (interactive) Modifier
                    .onKeyEvent { if (it.type == KeyEventType.KeyUp && (it.key == Key.Enter || it.key == Key.NumPadEnter)) { onOpen(); true } else false }
                    .focusable().clickable(onClick = onOpen)
                else Modifier,
            )
            .padding(size * 0.12f),
    ) {
        val mini = size * 0.36f
        val apps = folder.keys.mapNotNull(resolve).take(4)
        Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(size * 0.04f)) {
            apps.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(size * 0.04f)) { row.forEach { AppIcon(it, mini, Modifier.size(mini)) } }
            }
        }
    }
}

/**
 * The opened folder: its name (editable), its items in order. Enter or a tap launches an item;
 * with [editing] each item shows reorder and take-out controls so it works by touch and by keys.
 */
@Composable
fun FolderSheet(
    folder: Folder,
    resolve: (String) -> AppEntry?,
    editing: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onRename: (String) -> Unit,
    onReorder: (key: String, toIndex: Int) -> Unit,
    onTakeOut: (key: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(folder.id) { mutableStateOf(folder.name) }
    Dialog(onDismissRequest = { onRename(name); onDismiss() }) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(HyleColors.Surface.copy(alpha = 0.97f)).border(1.dp, HyleColors.Hairline, RoundedCornerShape(24.dp)).padding(16.dp)) {
            BasicTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                textStyle = TextStyle(color = HyleColors.InkBright, fontSize = 18.sp), cursorBrush = SolidColor(HyleColors.InkBright),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            LazyVerticalGrid(GridCells.Adaptive(76.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(folder.keys, key = { it }) { k ->
                    val app = resolve(k) ?: return@items
                    val i = folder.keys.indexOf(k)
                    Column(
                        Modifier.onKeyEvent { e ->
                            if (e.type != KeyEventType.KeyUp) false
                            else when {
                                e.key == Key.Enter || e.key == Key.NumPadEnter -> { onLaunch(app); true }
                                editing && e.key == Key.DirectionLeft && e.isAltPressed -> { onReorder(k, i - 1); true }
                                editing && e.key == Key.DirectionRight && e.isAltPressed -> { onReorder(k, i + 1); true }
                                else -> false
                            }
                        }.focusable().clickable { onLaunch(app) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(app, 52.dp, Modifier.size(52.dp))
                        Text(app.label, color = HyleColors.Ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (editing) Row {
                            Text("‹", color = HyleColors.InkBright, fontSize = 18.sp, modifier = Modifier.clickable { onReorder(k, i - 1) }.padding(horizontal = 8.dp))
                            Text("↥", color = HyleColors.InkBright, fontSize = 16.sp, modifier = Modifier.clickable { onTakeOut(k) }.padding(horizontal = 4.dp))
                            Text("›", color = HyleColors.InkBright, fontSize = 18.sp, modifier = Modifier.clickable { onReorder(k, i + 1) }.padding(horizontal = 8.dp))
                        }
                    }
                }
            }
            if (editing) Text(L10n.tr("Alt+arrows reorder. ↥ moves an item out of the folder."), color = HyleColors.InkDim, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

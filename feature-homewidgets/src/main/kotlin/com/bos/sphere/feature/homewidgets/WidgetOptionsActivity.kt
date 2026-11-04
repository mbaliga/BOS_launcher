package com.bos.sphere.feature.homewidgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.SphereTheme
import com.bos.sphere.feature.homewidgets.model.PadRef
import com.bos.sphere.feature.homewidgets.model.PadsCodec
import com.bos.sphere.feature.homewidgets.model.PadsModel
import com.bos.sphere.feature.homewidgets.model.WidgetKind
import com.bos.sphere.feature.search.AppSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A widget's options: which apps sit on an App pads widget, whether Needs you names apps (on by
 * default) and whether Now playing shows track and artist (off by default: titles can name people). Opened by the launcher (reconfigure; the widgets declare
 * configuration optional, so placing one never waits on it) or by a pads widget's "Choose apps".
 *
 * Not exported: launchers start it through `AppWidgetHost.startAppWidgetConfigureActivityForResult`,
 * which the system runs on Sphere's behalf. The result is always OK, so backing out keeps the
 * widget as it was (or with its defaults when just placed).
 */
class WidgetOptionsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val kind = if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            null
        } else {
            HomeWidgets.kindOf(this, runCatching { AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider }.getOrNull())
        }
        if (kind == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))

        val done = { HomeWidgets.refresh(this, kind); finish() }
        setContent {
            SphereTheme {
                Box(Modifier.fillMaxSize().background(HyleColors.Background).safeDrawingPadding().padding(16.dp)) {
                    when (kind) {
                        WidgetKind.PADS -> PadsOptions(
                            initial = WidgetPrefs.pads(this@WidgetOptionsActivity, id),
                            onCancel = ::finish,
                            onDone = { refs -> WidgetPrefs.setPads(this@WidgetOptionsActivity, id, refs); done() },
                        )
                        WidgetKind.NEEDS_YOU -> TwoChoices(
                            title = stringResource(R.string.hw_options_needs_you_title),
                            first = stringResource(R.string.hw_options_names) to stringResource(R.string.hw_options_names_body),
                            second = stringResource(R.string.hw_options_counts_only) to stringResource(R.string.hw_options_counts_only_body),
                            firstChosen = WidgetPrefs.showNames(this@WidgetOptionsActivity, id),
                            onCancel = ::finish,
                            onDone = { names -> WidgetPrefs.setShowNames(this@WidgetOptionsActivity, id, names); done() },
                        )
                        WidgetKind.NOW_PLAYING -> TwoChoices(
                            title = stringResource(R.string.hw_options_now_playing_title),
                            first = stringResource(R.string.hw_options_track) to stringResource(R.string.hw_options_track_body),
                            second = stringResource(R.string.hw_options_app_only) to stringResource(R.string.hw_options_app_only_body),
                            firstChosen = WidgetPrefs.showTrack(this@WidgetOptionsActivity, id),
                            onCancel = ::finish,
                            onDone = { track -> WidgetPrefs.setShowTrack(this@WidgetOptionsActivity, id, track); done() },
                        )
                        else -> Text(stringResource(R.string.hw_options_none), color = HyleColors.Ink, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, color = HyleColors.InkBright, fontSize = 22.sp, fontWeight = FontWeight.Light)
}

@Composable
private fun Buttons(onCancel: () -> Unit, onDone: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.hw_options_cancel), color = HyleColors.Ink, fontSize = 14.sp)
        }
        TextButton(onClick = onDone, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.hw_options_done), color = HyleColors.VioletSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** A choice between two settings; the chosen one carries a filled mark and the word "chosen" for TalkBack. */
@Composable
private fun TwoChoices(
    title: String,
    first: Pair<String, String>,
    second: Pair<String, String>,
    firstChosen: Boolean,
    onCancel: () -> Unit,
    onDone: (Boolean) -> Unit,
) {
    var chosenFirst by remember { mutableStateOf(firstChosen) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Title(title)
        ChoiceRow(first, chosenFirst) { chosenFirst = true }
        ChoiceRow(second, !chosenFirst) { chosenFirst = false }
        Buttons(onCancel) { onDone(chosenFirst) }
    }
}

@Composable
private fun ChoiceRow(text: Pair<String, String>, chosen: Boolean, onClick: () -> Unit) {
    val cd = if (chosen) stringResource(R.string.hw_options_chosen_cd, text.first) else text.first
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .border(1.dp, if (chosen) HyleColors.Violet else HyleColors.Hairline, RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = chosen; contentDescription = "$cd. ${text.second}" }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Shape, not colour: a filled disc for the chosen setting, an empty ring otherwise.
        Box(
            Modifier.size(18.dp)
                .border(2.dp, HyleColors.VioletSoft, CircleShape)
                .padding(4.dp)
                .background(if (chosen) HyleColors.VioletSoft else HyleColors.Background, CircleShape),
        )
        Column(Modifier.weight(1f)) {
            Text(text.first, color = HyleColors.InkBright, fontSize = 15.sp)
            Text(text.second, color = HyleColors.InkDim, fontSize = 13.sp)
        }
    }
}

private class Choosable(val ref: PadRef, val entry: AppEntry, val work: Boolean, val app: Sources.App)

/** Pick up to six apps, in order. Apps hidden in Sphere aren't offered. */
@Composable
private fun PadsOptions(initial: List<PadRef>, onCancel: () -> Unit, onDone: (List<PadRef>) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<Choosable>?>(initialValue = null) {
        val loaded = withContext(Dispatchers.IO) { loadChoosable(context) }
        value = loaded
    }
    var chosen by remember { mutableStateOf<List<PadRef>?>(null) }
    var query by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title(stringResource(R.string.hw_options_pads_title))
        Text(stringResource(R.string.hw_options_pads_help), color = HyleColors.Ink, fontSize = 14.sp)
        val list = apps
        if (list == null) {
            Text(stringResource(R.string.hw_options_loading), color = HyleColors.InkDim, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Buttons(onCancel) { onDone(initial) }
        } else {
            // Chosen apps that are gone or now hidden drop out of the choice.
            val kept = remember(list) { initial.filter { r -> list.any { it.ref == r } } }
            val current = chosen ?: kept
            Text(stringResource(R.string.hw_options_count, current.size, PadsCodec.MAX), color = HyleColors.InkDim, fontSize = 13.sp)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.hw_options_filter)) },
                modifier = Modifier.fillMaxWidth(),
            )
            val shown = remember(list, query) {
                if (query.isBlank()) {
                    list
                } else {
                    // Sphere's own search ranking (exact, prefix, word prefix, substring).
                    val byKey = list.associateBy { it.entry.key }
                    AppSearch.rank(list.map { it.entry }, query, limit = list.size).mapNotNull { byKey[it.key] }
                }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(shown, key = { "${it.ref.component}#${it.ref.userSerial}" }) { item ->
                    val position = current.indexOf(item.ref)
                    AppRow(item, position, full = current.size >= PadsCodec.MAX) { chosen = PadsModel.toggle(current, item.ref) }
                }
            }
            Buttons(onCancel) { onDone(current) }
        }
    }
}

@Composable
private fun AppRow(item: Choosable, position: Int, full: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    var icon by remember(item.ref) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.ref) {
        icon = withContext(Dispatchers.IO) { Sources.icon(context, item.app.info)?.asImageBitmap() }
    }
    val label = item.entry.label
    val cd = when {
        position >= 0 -> stringResource(R.string.hw_options_selected_cd, label, position + 1)
        full -> stringResource(R.string.hw_options_full_cd, label)
        else -> stringResource(R.string.hw_options_unselected_cd, label)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { selected = position >= 0; contentDescription = cd }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp)) { icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) } }
        Column(Modifier.weight(1f)) {
            Text(label, color = HyleColors.InkBright, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.work) Text(stringResource(R.string.hw_options_work), color = HyleColors.InkDim, fontSize = 12.sp)
        }
        // Shape and number, not colour: a filled disc with the pad's number when chosen, a ring otherwise.
        Box(
            Modifier.size(28.dp)
                .border(2.dp, HyleColors.VioletSoft, CircleShape)
                .background(if (position >= 0) HyleColors.VioletSoft else HyleColors.Background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (position >= 0) Text("${position + 1}", color = HyleColors.Background, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun loadChoosable(context: Context): List<Choosable> {
    val me = Process.myUserHandle()
    return Sources.launchableApps(context).values
        .filter { !it.fact.hidden && it.info.componentName.packageName != context.packageName }
        .map { app ->
            val user = app.info.user
            val entry = AppEntry(app.info.applicationInfo.packageName, app.info.componentName, app.fact.label, user) { null }
            Choosable(app.fact.ref, entry, work = user != me, app = app)
        }
        .sortedBy { it.entry.label.lowercase() }
}

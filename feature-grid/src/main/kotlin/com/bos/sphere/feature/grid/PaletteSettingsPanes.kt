package com.bos.sphere.feature.grid

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.search.palette.FileGrants
import com.bos.sphere.feature.search.palette.FileIndexHolder
import com.bos.sphere.feature.search.palette.FileIndexScheduler
import com.bos.sphere.feature.search.palette.FileIndexer
import com.bos.sphere.feature.search.palette.PaletteHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** PAL-11: folders the user grants through the system picker, indexed incrementally. No all-files access. */
@Composable
fun FolderGrantsPane() {
    val context = LocalContext.current
    var trees by remember { mutableStateOf(FileGrants.trees(context)) }
    var status by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri: Uri? = r.data?.data
        if (uri != null) {
            runCatching { FileGrants.persist(context, uri) }
            trees = FileGrants.trees(context)
            FileIndexScheduler.schedule(context)
            scope.launch { withContext(Dispatchers.IO) { FileIndexer.reconcile(context) }; status = L10n.tr("Indexed") }
        }
    }
    Column {
        Text(L10n.tr("Radar finds files in photos, video and audio you allow, and in the folders you add here. Only the folders you pick are read."), color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp))
        ActionRow(label = L10n.tr("Add a folder"), summary = L10n.tr("Opens the system folder picker"), onClick = { picker.launch(FileGrants.pickerIntent()) })
        trees.forEach { t ->
            ActionRow(label = Uri.decode(t.lastPathSegment.orEmpty()).substringAfter(':').ifEmpty { t.toString() }, summary = L10n.tr("Tap to stop searching this folder"), onClick = {
                FileGrants.release(context, t); trees = FileGrants.trees(context)
                // The released folder's files leave the index at once, not at the next job.
                scope.launch { withContext(Dispatchers.IO) { FileIndexer.reconcile(context) } }
            })
        }
        ActionRow(label = L10n.tr("Update the file index now"), summary = status.ifEmpty { L10n.tr("%s files indexed", FileIndexHolder.get(context).size()) }, onClick = {
            status = L10n.tr("Indexing…")
            scope.launch { withContext(Dispatchers.IO) { FileIndexer.reconcile(context) }; status = L10n.tr("%s files indexed", FileIndexHolder.get(context).size()) }
        })
    }
}

/** PAL-09 and PAL-02: what Radar learned from your selections lives on this device only; this page clears it. Also the app aliases. */
@Composable
fun SearchHistoryPane() {
    val context = LocalContext.current
    var done by remember { mutableStateOf(false) }
    var aliases by remember { mutableStateOf(PaletteHost.aliases(context)) }
    Column {
        Text(L10n.tr("Radar ranks the things you pick more often higher. This is kept on this device only and never sent anywhere."), color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp))
        ActionRow(label = L10n.tr("Forget what Radar learned"), summary = if (done) L10n.tr("Forgotten") else null, onClick = { RadarLearning.forget(context); done = true })
        aliases.entries.sortedBy { it.value.lowercase() }.forEach { (pkg, alias) ->
            ActionRow(label = alias, summary = pkg + " · " + L10n.tr("Tap to remove this alias"), onClick = { PaletteHost.setAlias(context, pkg, ""); aliases = PaletteHost.aliases(context) })
        }
    }
}

/** PAL-17: apps that registered a guarded search provider; each needs the user's approval before Radar queries it. */
@Composable
fun SearchProvidersPane() {
    val context = LocalContext.current
    val prefs = remember { PaletteHost.get(context).prefs }
    var approved by remember { mutableStateOf(prefs.approvedSources()) }
    val found = remember(approved) { com.bos.sphere.feature.search.palette.ThirdPartyProviders.discover(context, approved) }
    Column {
        Text(L10n.tr("Apps can offer search results to Radar through a read-only provider that uses the READ_SEARCH permission. Only the ones you switch on are asked, and only after you type."), color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(vertical = 6.dp))
        if (found.isEmpty()) Text(L10n.tr("No app offers one right now."), color = HyleColors.Ink, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
        found.forEach { c ->
            ActionRow(label = c.label, summary = if (c.approved) L10n.tr("On: tap to turn off") else L10n.tr("Off: tap to turn on"), selected = c.approved, onClick = {
                prefs.setApproved(c.packageName, !c.approved); approved = prefs.approvedSources()
            })
        }
    }
}

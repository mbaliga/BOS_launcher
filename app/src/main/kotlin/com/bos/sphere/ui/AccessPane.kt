package com.bos.sphere.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.GrantKind
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.ActionRow
import com.bos.sphere.feature.search.palette.FileGrants

/**
 * CFG-07: every grant in one place. Each row opens the same one-screen explanation the features use on first touch,
 * then the system route (settings page, permission prompt or the folder picker).
 */
@Composable
fun AccessPane() {
    val ctx = LocalContext.current
    val prompter = rememberGrantPrompter()
    var tick by remember { mutableIntStateOf(0) }
    val contacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.data?.let { runCatching { FileGrants.persist(ctx, it) } }
        tick++
    }
    // Re-read after every return from a system screen.
    val resumed = rememberGranted(GrantKind.Notifications) // recomposes on resume
    val rows = remember(tick, resumed) { AccessLogic.rows { GrantSupport.isGranted(ctx, it) } }
    Column {
        Text(L10n.tr(AccessLogic.INTRO), color = HyleColors.InkDim, fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 6.dp))
        rows.forEach { row ->
            ActionRow(label = L10n.tr(row.title), summary = L10n.tr(row.state) + " · " + L10n.tr(row.action)) {
                prompter.ask(row.kind) {
                    GrantSupport.markAsked(ctx, row.kind)
                    when (row.kind) {
                        GrantKind.Contacts -> contacts.launch(android.Manifest.permission.READ_CONTACTS)
                        GrantKind.Microphone -> mic.launch(android.Manifest.permission.RECORD_AUDIO)
                        GrantKind.Folders -> folder.launch(FileGrants.pickerIntent())
                        else -> GrantSupport.request(ctx, row.kind)
                    }
                }
            }
        }
    }
    prompter.Host()
}

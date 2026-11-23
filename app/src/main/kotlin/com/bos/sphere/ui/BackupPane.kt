package com.bos.sphere.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.ImportReport
import com.bos.sphere.core.data.LauncherImport
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.PendingWidget
import com.bos.sphere.core.data.WidgetRebind
import com.bos.sphere.feature.grid.LauncherWidgetHost
import com.bos.sphere.feature.grid.RebindOutcome
import com.bos.sphere.feature.grid.rememberWidgetRebinder
import com.bos.sphere.core.data.LauncherAppsAppRepository
import com.bos.sphere.core.data.SnapshotStore
import com.bos.sphere.core.data.StateCodec
import com.bos.sphere.core.data.StateDocument
import com.bos.sphere.core.data.StateFormatException
import com.bos.sphere.core.data.StateSchema
import com.bos.sphere.core.data.StateService
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.ActionRow
import com.bos.sphere.feature.grid.BackupPane
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.SettingCategory
import com.bos.sphere.feature.grid.SettingItem
import com.bos.sphere.feature.grid.TextButtonPill
import com.bos.sphere.feature.grid.SettingsResetFlow
import com.bos.sphere.feature.grid.keywords
import com.bos.sphere.feature.grid.snapshotFirst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** CFG-01 / CFG-02 / CFG-04 / CFG-11: the Backup category of Ground Control. */
fun backupCategory(resetAll: (() -> Unit)? = null): SettingCategory = SettingCategory(
    L10n.tr("Backup"),
    Glyphs.Info,
    listOfNotNull(
        SettingItem.Pane(L10n.tr("Export, import and restore"), Glyphs.Info, L10n.tr("One file, or the last five snapshots")) { BackupPane { reloadAfterStateChange(it) } },
        crashReportsItem(),
        // Wave 56 (CFG-30): every preference back to its default; the host takes a snapshot first and offers Undo. User data (layout, hidden apps, shortcuts, rules) stays.
        resetAll?.let { r ->
            SettingItem.Action(SettingsResetFlow.ALL_TITLE, Glyphs.Sliders, "", confirm = SettingsResetFlow.ALL_BODY, confirmLabel = SettingsResetFlow.CONFIRM, onClick = r)
                .snapshotFirst().keywords("restore defaults", "factory", "default", "clear settings")
        },
    ),
)


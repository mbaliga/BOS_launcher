package com.bos.sphere.feature.grid

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.bos.sphere.core.design.HyleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/** Handle returned by [rememberWidgetPicker]; [launch] opens the in-app widget picker. */
class WidgetPickerLauncher internal constructor(private val onLaunch: () -> Unit) {
    fun launch() = onLaunch()
}

/** One installed provider as shown in the picker. */
internal data class WidgetChoice(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val isWork: Boolean,
)

/**
 * Picker + bind + configure flow. [WidgetPickerLauncher.launch] shows a dialog of every home-screen
 * widget provider across user profiles. Choosing one allocates an id, binds it
 * (`bindAppWidgetIdIfAllowed`, else the system ACTION_APPWIDGET_BIND consent screen), then runs
 * the provider's configure activity if it needs one. [onPicked] fires on success; on cancel or
 * failure the allocated id is deleted.
 */
@Composable
fun rememberWidgetPicker(
    host: LauncherWidgetHost,
    onPicked: (appWidgetId: Int, label: String) -> Unit,
): WidgetPickerLauncher {
    val context = LocalContext.current
    val currentOnPicked by rememberUpdatedState(onPicked)

    var showPicker by rememberSaveable { mutableStateOf(false) }
    // Pending flow state survives activity recreation while the bind/configure screens are up.
    var pendingId by rememberSaveable { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    var pendingLabel by rememberSaveable { mutableStateOf("") }
    var pendingInfo by rememberSaveable { mutableStateOf<AppWidgetProviderInfo?>(null) }

    fun finish(success: Boolean) {
        val id = pendingId
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        pendingId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingInfo = null
        if (success && host.providerInfo(id) != null) currentOnPicked(id, pendingLabel)
        else host.deleteWidget(id)
    }

    val configureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> finish(result.resultCode == Activity.RESULT_OK) }

    fun configureOrFinish() {
        val id = pendingId
        val info = pendingInfo ?: host.providerInfo(id)
        if (info?.configure == null || isConfigurationOptional(info)) {
            finish(true)
            return
        }
        // Preferred: launch it ourselves so we get the result (works for exported, same-profile
        // configure activities — the common case).
        val launched = info.profile == Process.myUserHandle() && runCatching {
            configureLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(info.configure)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            )
        }.isSuccess
        if (launched) return
        // Fallback: let the system launch it (handles non-exported / cross-profile). Its result
        // lands in Activity.onActivityResult, which Compose cannot observe — treat as success.
        val activity = context.findActivity()
        val ok = activity != null && runCatching {
            host.host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST_CONFIGURE, null)
        }.isSuccess
        finish(ok)
    }

    val bindLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) configureOrFinish() else finish(false)
    }

    fun begin(choice: WidgetChoice) {
        showPicker = false
        finish(false) // abandon any stale pending id
        val info = choice.info
        val id = runCatching { host.allocateAppWidgetId() }.getOrNull() ?: return
        pendingId = id
        pendingLabel = choice.label
        pendingInfo = info
        val bound = runCatching {
            host.appWidgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
        }.getOrDefault(false)
        if (bound) {
            configureOrFinish()
        } else {
            val asked = runCatching {
                bindLauncher.launch(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
                )
            }.isSuccess
            if (!asked) finish(false)
        }
    }

    if (showPicker) {
        WidgetPickerDialog(
            onDismiss = { showPicker = false },
            onSelect = ::begin,
        )
    }

    return remember { WidgetPickerLauncher { showPicker = true } }
}

private const val REQUEST_CONFIGURE = 0x5349

private fun isConfigurationOptional(info: AppWidgetProviderInfo): Boolean {
    val f = info.widgetFeatures
    return f and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0 &&
        f and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Every home-screen-capable provider across the user's profiles, sorted by app then label. */
internal fun loadWidgetChoices(context: Context): List<WidgetChoice> {
    val manager = AppWidgetManager.getInstance(context)
    val users = context.getSystemService(UserManager::class.java)?.userProfiles
        ?: listOf(Process.myUserHandle())
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val pm = context.packageManager
    val me = Process.myUserHandle()
    return users.flatMap { user ->
        runCatching { manager.getInstalledProvidersForProfile(user) }.getOrDefault(emptyList())
    }
        .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
        .map { info ->
            val pkg = info.provider.packageName
            val appLabel = runCatching {
                launcherApps?.getApplicationInfo(pkg, 0, info.profile)?.loadLabel(pm)?.toString()
            }.getOrNull() ?: pkg
            WidgetChoice(
                info = info,
                label = runCatching { info.loadLabel(pm) }.getOrNull()?.takeIf { it.isNotBlank() } ?: appLabel,
                appLabel = appLabel,
                isWork = info.profile != me,
            )
        }
        .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.isWork }, { it.label.lowercase() }))
}

@Composable
private fun WidgetPickerDialog(onDismiss: () -> Unit, onSelect: (WidgetChoice) -> Unit) {
    val context = LocalContext.current
    val choices by produceState<List<WidgetChoice>?>(null) {
        value = withContext(Dispatchers.Default) { runCatching { loadWidgetChoices(context) }.getOrDefault(emptyList()) }
    }
    val shape = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .fillMaxHeight(0.82f)
                .clip(shape)
                .background(HyleColors.SurfaceTranslucent, shape)
                .border(1.dp, HyleColors.Hairline, shape),
        ) {
            Text(
                "Add a widget",
                color = HyleColors.InkBright,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 4.dp),
            )
            Text(
                "It will peek out of the corner; tap it to pop it out.",
                color = HyleColors.InkDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(HyleColors.Hairline))
            val list = choices
            when {
                list == null -> CenteredNote("Loading widgets…")
                list.isEmpty() -> CenteredNote("No widgets installed")
                else -> LazyColumn(Modifier.fillMaxSize().padding(vertical = 6.dp)) {
                    items(list, key = { "${it.info.provider.flattenToString()}#${it.info.profile.hashCode()}" }) { choice ->
                        WidgetChoiceRow(choice, onClick = { onSelect(choice) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredNote(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = HyleColors.InkDim, fontSize = 13.sp)
    }
}

private val PreviewW = 88.dp
private val PreviewH = 64.dp

@Composable
private fun WidgetChoiceRow(choice: WidgetChoice, onClick: () -> Unit) {
    val context = LocalContext.current
    val info = choice.info
    val dpi = context.resources.displayMetrics.densityDpi
    val pxW = (PreviewW.value * context.resources.displayMetrics.density).roundToInt()
    val pxH = (PreviewH.value * context.resources.displayMetrics.density).roundToInt()
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val d = info.loadPreviewImage(context, dpi) ?: info.loadIcon(context, dpi) ?: return@runCatching null
                val iw = d.intrinsicWidth.takeIf { it > 0 } ?: pxW
                val ih = d.intrinsicHeight.takeIf { it > 0 } ?: pxH
                val scale = min(pxW.toFloat() / iw, pxH.toFloat() / ih).coerceAtMost(1f)
                d.toBitmap(
                    (iw * scale).roundToInt().coerceAtLeast(1),
                    (ih * scale).roundToInt().coerceAtLeast(1),
                ).asImageBitmap()
            }.getOrNull()
        }
    }
    val tileShape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(PreviewW, PreviewH)
                .clip(tileShape)
                .background(HyleColors.Surface, tileShape)
                .border(1.dp, HyleColors.Hairline, tileShape)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            preview?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f)) {
            Text(
                choice.label,
                color = HyleColors.InkBright,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    choice.appLabel + sizeHint(info),
                    color = HyleColors.InkDim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (choice.isWork) {
                    Text(
                        "Work",
                        color = HyleColors.Violet,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .border(1.dp, HyleColors.Violet.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private fun sizeHint(info: AppWidgetProviderInfo): String =
    if (info.targetCellWidth > 0 && info.targetCellHeight > 0) {
        " · ${info.targetCellWidth}×${info.targetCellHeight}"
    } else {
        ""
    }

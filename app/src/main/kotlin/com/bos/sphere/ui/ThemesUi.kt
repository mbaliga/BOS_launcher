package com.bos.sphere.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.DockStyle
import com.bos.sphere.core.data.GradientSpec
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.LauncherStores
import com.bos.sphere.core.data.ThemeApplier
import com.bos.sphere.core.data.ThemeChange
import com.bos.sphere.core.data.ThemeError
import com.bos.sphere.core.data.ThemeErrorCode
import com.bos.sphere.core.data.ThemeField
import com.bos.sphere.core.data.ThemeLook
import com.bos.sphere.core.data.ThemePack
import com.bos.sphere.core.data.ThemePacks
import com.bos.sphere.core.data.ThemeParse
import com.bos.sphere.core.data.ThemeUndo
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.dock.WorkspaceRepository
import com.bos.sphere.feature.grid.ActionRow
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.TextButtonPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Wave 39 (dockThemes): Ground Control's Themes rows (import, export, reset, undo), the import preview and the error list.
 * The file pickers sit behind [ThemeFiles] and the settings behind [ThemeHost], so a test drives the whole flow without a picker or a store.
 * A theme file is untrusted data: it is read at most 64 KB at a time, parsed by [ThemePacks.parse] and applied only after the person taps Apply.
 */
interface ThemeFiles {
    /** Opens the document picker; [onBytes] gets at most 64 KB + 1 byte of the chosen file, or null when it could not be read. A cancelled picker calls nothing. */
    fun pickImport(onBytes: (ByteArray?) -> Unit)
    /** Asks where to save [text] as [fileName]; [onDone] says whether it was written. A cancelled picker calls nothing. */
    fun saveExport(fileName: String, text: String, onDone: (Boolean) -> Unit)
}

/** The settings a theme reads and changes. */
class ThemeHost(val prefs: () -> LauncherPrefs, val applier: ThemeApplier) {
    fun dockStyle(): DockStyle = applier.current().dockStyle
    fun changesFor(pack: ThemePack): List<ThemeChange> = ThemeLook.changes(prefs(), dockStyle(), pack.settings)

    companion object {
        fun of(context: Context): ThemeHost {
            val store = LauncherStores.get(context).prefs
            val dock = WorkspaceRepository.get(context)
            return ThemeHost(
                { store.prefs.value },
                ThemeApplier({ store.prefs.value }, { f -> store.update(f) }, { dock.current.dock.style }, { s -> dock.setDock { it.copy(style = s) } }),
            )
        }
    }
}

object ThemeFileIo {
    /** At most [ThemePacks.MAX_BYTES] + 1 bytes: one over is enough for the parser to say "too large" without reading a huge file. */
    fun readBounded(context: Context, uri: Uri): ByteArray? = context.contentResolver.openInputStream(uri)?.use { s ->
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() <= ThemePacks.MAX_BYTES) { val n = s.read(buf); if (n < 0) break; out.write(buf, 0, n) }
        out.toByteArray()
    }
}

@Composable
fun rememberThemeFiles(): ThemeFiles {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var callback by remember { mutableStateOf<((ByteArray?) -> Unit)?>(null) }
    var export by remember { mutableStateOf<Pair<String, (Boolean) -> Unit>?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { callback?.invoke(withContext(Dispatchers.IO) { runCatching { ThemeFileIo.readBounded(context, uri) }.getOrNull() }) }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ThemePacks.MIME)) { uri ->
        val job = export
        if (uri != null && job != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(job.first.toByteArray(Charsets.UTF_8)) } != null }.getOrDefault(false) }
            job.second(ok)
        }
    }
    return remember {
        object : ThemeFiles {
            override fun pickImport(onBytes: (ByteArray?) -> Unit) { callback = onBytes; pick.launch(arrayOf(ThemePacks.MIME, "application/octet-stream", "text/plain", "*/*")) }
            override fun saveExport(fileName: String, text: String, onDone: (Boolean) -> Unit) { export = text to onDone; save.launch(fileName) }
        }
    }
}

@Composable
fun rememberThemeHost(): ThemeHost { val c = LocalContext.current; return remember { ThemeHost.of(c) } }

object ThemeTexts {
    fun field(f: ThemeField): String = L10n.tr(f.label)
    fun message(e: ThemeError): String = if (e.field != null) L10n.tr(e.code.english, field(e.field!!)) else L10n.tr(e.code.english)

    /** The preview's row label for a field (capitalised, no placeholder). */
    fun chipLabel(f: ThemeField): String = when (f) {
        ThemeField.Mode -> L10n.tr("Mode")
        ThemeField.Accent -> L10n.tr("Accent")
        ThemeField.IconShape -> L10n.tr("Icon shape")
        ThemeField.IconTint -> L10n.tr("Icon colour")
        ThemeField.DockStyle -> L10n.tr("Dock style")
        ThemeField.Wallpaper -> L10n.tr("Wallpaper")
        ThemeField.Font -> L10n.tr("Font")
        ThemeField.CalmStyle -> L10n.tr("Calm style")
        ThemeField.IconPlate -> L10n.tr("Icon plate")
        ThemeField.Name -> L10n.tr("Name")
        ThemeField.Author -> L10n.tr("Author")
        ThemeField.Version -> L10n.tr("Version")
    }

    /** A value in a chip: colours are shown as hex, the rest through the setting's own label translated. */
    fun value(c: ThemeChange, text: String): String =
        if (c.field == ThemeField.Font) L10n.tr(com.bos.sphere.core.design.FontCatalog.entry(text).label) else if (text.startsWith("#")) text else L10n.tr(text)
}

object ThemeTags {
    const val PREVIEW = "theme.preview"
    const val APPLY = "theme.apply"
    const val CANCEL = "theme.cancel"
    const val ERRORS = "theme.errors"
    const val APPLIED = "theme.applied"
    fun change(f: ThemeField) = "theme.change.${f.name}"
}

// ---------------------------------------------------------------------------------------------------------------------------------------

@Composable
fun ColumnScope.ThemesPane(files: ThemeFiles = rememberThemeFiles(), host: ThemeHost = rememberThemeHost()) {
    var parsed by remember { mutableStateOf<ThemeParse?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val undo by ThemeUndo.snapshot.collectAsState()

    val ok = parsed as? ThemeParse.Ok
    if (ok != null) {
        ThemePreviewScreen(
            ok.pack, host.changesFor(ok.pack),
            onApply = { host.applier.apply(ok.pack); parsed = null; message = L10n.tr("Applied \"%s\". You can undo it below.", ok.pack.name) },
            onCancel = { parsed = null },
        )
        return
    }

    Text(L10n.tr("A theme is a small file that sets the light or dark mode, accent colour, icon shape, icon colour, dock style and a solid or gradient wallpaper. It holds nothing else: no apps, layout, pictures or links."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
    message?.let { Text(it, color = HyleColors.InkBright, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp).testTag(ThemeTags.APPLIED).semantics { liveRegion = LiveRegionMode.Polite }) }
    (parsed as? ThemeParse.Failed)?.let { ThemeErrors(it.errors) }

    ActionRow(L10n.tr("Import theme"), L10n.tr("Choose a .liftofftheme file; you see what it changes before anything happens"), Glyphs.Plus) {
        message = null
        files.pickImport { bytes -> parsed = if (bytes == null) ThemeParse.Failed(listOf(ThemeError(ThemeErrorCode.Unreadable))) else ThemePacks.parse(bytes) }
    }
    ActionRow(L10n.tr("Export current look"), L10n.tr("Saves your mode, accent, icon, dock and wallpaper choices as a theme file"), Glyphs.Image) {
        val pack = ThemeLook.capture(host.prefs(), host.dockStyle(), L10n.tr("My look"))
        files.saveExport(ThemePacks.fileName(pack.name), ThemePacks.encode(pack)) { done ->
            message = if (done) L10n.tr("Theme exported. A wallpaper picture, video or live wallpaper is not included.") else L10n.tr("The theme could not be saved.")
        }
    }
    ActionRow(L10n.tr("Reset to default look"), L10n.tr("Dark mode, the default accent, rounded icons, the standard dock; you can undo it"), Glyphs.Home) {
        host.applier.reset(); parsed = null; message = L10n.tr("Look reset to the default. You can undo it below.")
    }
    if (undo != null) ActionRow(L10n.tr("Undo last theme change"), L10n.tr("Puts back the look you had before"), Glyphs.Close) {
        if (host.applier.undo()) message = L10n.tr("Your previous look is back.")
    }
}

@Composable
fun ThemeErrors(errors: List<ThemeError>) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFB3261E).copy(alpha = 0.16f)).padding(12.dp).testTag(ThemeTags.ERRORS)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(L10n.tr("This file cannot be used as a theme"), color = HyleColors.InkBright, fontSize = 14.sp)
        errors.forEach { Text("• " + ThemeTexts.message(it), color = HyleColors.Ink, fontSize = 13.sp) }
        Text(L10n.tr("Nothing was changed."), color = HyleColors.InkDim, fontSize = 12.sp)
    }
}

/** The preview: the theme's name and author, and what it will change as before and after chips. Apply and Cancel are the only ways out. */
@Composable
fun ThemePreviewScreen(pack: ThemePack, changes: List<ThemeChange>, onApply: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().testTag(ThemeTags.PREVIEW), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pack.name, color = HyleColors.InkBright, fontSize = 20.sp, modifier = Modifier.semantics { heading() })
        pack.author?.let { Text(L10n.tr("By %s", it), color = HyleColors.Ink, fontSize = 13.sp) }
        pack.version?.let { Text(L10n.tr("Version %s", it), color = HyleColors.InkDim, fontSize = 12.sp) }
        Text(L10n.tr("This theme will change:"), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        changes.forEach { ChangeRow(it) }
        Text(L10n.tr("Nothing else changes: not your apps, layout, pictures or other settings."), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.testTag(ThemeTags.APPLY)) { TextButtonPill(L10n.tr("Apply"), primary = true, onClick = onApply) }
            Box(Modifier.testTag(ThemeTags.CANCEL)) { TextButtonPill(L10n.tr("Cancel"), onClick = onCancel) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChangeRow(c: ThemeChange) {
    val arrow = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "←" else "→"
    val label = ThemeTexts.chipLabel(c.field)
    val before = ThemeTexts.value(c, c.before); val after = ThemeTexts.value(c, c.after)
    Column(
        Modifier.fillMaxWidth().testTag(ThemeTags.change(c.field))
            .semantics(mergeDescendants = true) { contentDescription = if (c.changes) L10n.tr("%s: %s, will become %s", label, before, after) else L10n.tr("%s: %s, stays the same", label, before) },
    ) {
        Text(label, color = HyleColors.InkDim, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(before, c.beforeColors, strong = false)
            if (c.changes) { Text(arrow, color = HyleColors.InkDim, fontSize = 16.sp); Chip(after, c.afterColors, strong = true) }
            else Text(L10n.tr("stays the same"), color = HyleColors.InkDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Chip(text: String, colors: List<Long>, strong: Boolean) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).background(if (strong) HyleColors.Violet.copy(alpha = 0.2f) else HyleColors.Hairline.copy(alpha = 0.08f)).border(1.dp, if (strong) HyleColors.Violet else HyleColors.Hairline, shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (colors.isNotEmpty()) Box(
            Modifier.size(18.dp).clip(CircleShape).border(1.dp, HyleColors.Hairline, CircleShape)
                .background(if (colors.size == 1) Brush.linearGradient(listOf(Color(colors[0].toInt()), Color(colors[0].toInt()))) else Brush.linearGradient(colors.map { Color(it.toInt()) })),
        )
        Text(text, color = HyleColors.InkBright, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------------

/**
 * Opening a .liftofftheme file from outside (a file manager, a message): [load] reads the file, the same validation runs, the same preview shows,
 * and only Apply changes anything. [onClose] ends the activity.
 */
@Composable
fun ThemeImportFlow(load: suspend () -> ByteArray?, host: ThemeHost, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var parsed by remember { mutableStateOf<ThemeParse?>(null) }
    var appliedName by remember { mutableStateOf<String?>(null) }
    val undo by ThemeUndo.snapshot.collectAsState()
    LaunchedEffect(Unit) {
        val bytes = withContext(Dispatchers.IO) { runCatching { load() }.getOrNull() }
        parsed = if (bytes == null) ThemeParse.Failed(listOf(ThemeError(ThemeErrorCode.Unreadable))) else ThemePacks.parse(bytes)
    }
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(L10n.tr("Theme"), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.semantics { heading() })
        val p = parsed
        when {
            appliedName != null -> {
                Text(L10n.tr("Applied \"%s\".", appliedName!!), color = HyleColors.InkBright, fontSize = 16.sp, modifier = Modifier.testTag(ThemeTags.APPLIED).semantics { liveRegion = LiveRegionMode.Polite })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (undo != null) TextButtonPill(L10n.tr("Undo")) { if (host.applier.undo()) appliedName = null }
                    TextButtonPill(L10n.tr("Done"), primary = true, onClick = onClose)
                }
            }
            p == null -> Text(L10n.tr("Reading the theme…"), color = HyleColors.InkDim, fontSize = 13.sp)
            p is ThemeParse.Failed -> { ThemeErrors(p.errors); TextButtonPill(L10n.tr("Close"), primary = true, onClick = onClose) }
            p is ThemeParse.Ok -> ThemePreviewScreen(p.pack, host.changesFor(p.pack), onApply = { host.applier.apply(p.pack); appliedName = p.pack.name }, onCancel = onClose)
        }
    }
}

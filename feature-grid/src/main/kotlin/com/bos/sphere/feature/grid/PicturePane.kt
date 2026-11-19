package com.bos.sphere.feature.grid

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.GifMode
import com.bos.sphere.core.data.GifPolicy
import com.bos.sphere.core.data.PictureFit
import com.bos.sphere.core.data.PictureLimits
import com.bos.sphere.core.data.PowerWarning
import com.bos.sphere.core.data.Wallpaper
import com.bos.sphere.core.data.WallpaperSurface
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import kotlinx.coroutines.launch

/** Seam: what "Choose from gallery" does. The app opens the system Photo Picker (images and videos, no storage permission); a test supplies a fake that answers at once. */
internal fun interface MediaPickerFactory {
    /** Returns the function that opens the picker; [onResult] gets the chosen content Uri, or null when the user backed out. */
    @Composable fun create(onResult: (Uri?) -> Unit): () -> Unit
}

private class GetMediaContent : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String) = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
}

internal object DefaultMediaPicker : MediaPickerFactory {
    @Composable override fun create(onResult: (Uri?) -> Unit): () -> Unit {
        val ctx = LocalContext.current
        val photoPicker = remember(ctx) { runCatching { ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(ctx) }.getOrDefault(false) }
        val latest by rememberUpdatedState(onResult)
        val modern = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { latest(it) }
        val fallback = rememberLauncherForActivityResult(GetMediaContent()) { latest(it) }
        return {
            // No storage permission either way: the Photo Picker hands over one chosen file; devices without it use the system's file chooser.
            runCatching { if (photoPicker) modern.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) else fallback.launch("*/*") }
        }
    }
}

internal val LocalMediaPicker = staticCompositionLocalOf<MediaPickerFactory> { DefaultMediaPicker }
internal val LocalPictureImporter = staticCompositionLocalOf<PictureImporter?> { null }

/** Seam for the system's own live wallpaper: its name, and a way to open the chooser. */
internal interface SystemWallpaperAccess {
    fun currentLabel(): String?
    /** Opens the live wallpaper chooser (else the wallpaper picker, else display settings); false when nothing could be opened. */
    fun openChooser(): Boolean
}

internal class AndroidSystemWallpaper(private val ctx: Context) : SystemWallpaperAccess {
    override fun currentLabel(): String? = runCatching {
        ctx.getSystemService(WallpaperManager::class.java)?.wallpaperInfo?.loadLabel(ctx.packageManager)?.toString()
    }.getOrNull()

    override fun openChooser(): Boolean {
        val tries = listOf(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER), Intent(Intent.ACTION_SET_WALLPAPER), Intent(Settings.ACTION_DISPLAY_SETTINGS))
        for (i in tries) {
            if (runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
        }
        return false
    }
}

internal val LocalSystemWallpaper = staticCompositionLocalOf<SystemWallpaperAccess?> { null }

/** A pane button: at least 48 dp tall, a Button role, one text line. */
@Composable
internal fun PaneButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(if (primary) HyleColors.Violet.copy(alpha = 0.35f) else Color(0x1AFFFFFF))
            .clickable(onClick = onClick).semantics { role = Role.Button; contentDescription = text }.padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = HyleColors.InkBright, fontSize = 13.sp) }
}

private fun dimLabel(p: Int) = "$p%"

@Composable
private fun Note(text: String, modifier: Modifier = Modifier, warn: Boolean = false) {
    Text(text, color = if (warn) HyleColors.InkBright else HyleColors.InkDim, fontSize = 11.sp, modifier = modifier.padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite })
}

/** The animated choices of a picture: Still / Animated, then Play once / Loop, then Continuous / Bounce (not for a video). */
@Composable
private fun AnimationChoices(w: Wallpaper, apply: (Wallpaper) -> Unit) {
    val pic = w.picture
    val animated = pic.gif != GifMode.Still
    val loop = pic.gif.loops
    Text(L10n.tr("Animation"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
    ChipRow(listOf(false, true), animated, { L10n.tr(if (it) (if (pic.video) "Video" else "Animated") else "Still (first frame)") }) { on ->
        apply(w.copy(picture = pic.copy(gif = if (on) GifMode.Once else GifMode.Still)))
    }
    if (animated) {
        Text(L10n.tr("Playback"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        ChipRow(listOf(false, true), loop, { L10n.tr(if (it) "Loop" else "Play once") }) { l ->
            apply(w.copy(picture = pic.copy(gif = if (l) GifMode.LoopContinuous else GifMode.Once)))
        }
        if (loop && !pic.video) {
            Text(L10n.tr("Loop style"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            ChipRow(listOf(GifMode.LoopContinuous, GifMode.LoopBounce), pic.gif, { L10n.tr(it.label) }) { m -> apply(w.copy(picture = pic.copy(gif = m))) }
            Note(L10n.tr("Continuous plays forward again and again. Bounce plays forward, then rewinds, again and again."))
        }
    }
}

/**
 * Wave 34 (wallpaper): the Picture kind's controls. Choose from the gallery (images, animated GIF / WebP and videos, through [LocalMediaPicker]), the fit,
 * the dim, the animation choices, the standing power warning and the confirmation dialog that comes before an animated wallpaper is switched on.
 * [apply] stores a whole new wallpaper; [onDeleteFile] removes a private copy that is no longer used.
 */
@Composable
internal fun PictureEditor(surface: WallpaperSurface, w: Wallpaper, apply: (Wallpaper) -> Unit, onDeleteFile: (String) -> Unit) {
    val ctx = LocalContext.current
    val importer = LocalPictureImporter.current ?: remember(ctx) { AndroidPictureImporter(ctx) }
    val latest by rememberUpdatedState(w)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<ImportFailure?>(null) }
    var pending by remember { mutableStateOf<Wallpaper?>(null) }
    val metrics = ctx.resources.displayMetrics
    val open = LocalMediaPicker.current.create { uri ->
        if (uri == null) return@create
        busy = true; failure = null
        scope.launch {
            val r = runCatching { importer.import(uri, surface, metrics.widthPixels, metrics.heightPixels) }.getOrElse { ImportResult.Failed(ImportFailure.Unreadable) }
            busy = false
            when (r) {
                is ImportResult.Failed -> failure = r.reason
                is ImportResult.Ok -> {
                    val cur = latest
                    val old = cur.picture.file
                    // A new file starts as a still with the fit and dim kept; the user turns animation on afterwards (and then confirms the power warning).
                    apply(cur.copy(kind = com.bos.sphere.core.data.WallpaperKind.Picture, picture = r.spec.copy(fit = cur.picture.fit, dim = cur.picture.dim, gif = GifMode.Still)))
                    if (old.isNotEmpty() && old != r.spec.file) onDeleteFile(old)
                }
            }
        }
    }
    // Every change goes through here: an animated wallpaper that was not confirmed yet waits for the dialog.
    val change: (Wallpaper) -> Unit = { n -> if (PowerWarning.mustAsk(n.picture)) pending = n else apply(n) }
    val pic = w.picture
    Column(Modifier.fillMaxWidth()) {
        PaneButton(L10n.tr(if (pic.hasFile) "Change picture or video" else "Choose from gallery"), Modifier.padding(top = 10.dp), primary = true) { if (!busy) open() }
        if (busy) Note(L10n.tr("Working on it"))
        failure?.let { Note(L10n.tr(it.text), warn = true) }
        if (pic.hasFile) {
            PaneButton(L10n.tr("Remove picture"), Modifier.padding(top = 8.dp)) { onDeleteFile(pic.file); apply(w.copy(picture = pic.copy(file = "", animated = false, video = false, durationMs = 0L, powerOk = false, gif = GifMode.Still))) }
            val missing by androidx.compose.runtime.produceState(false, pic.file) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { PictureFiles.resolve(ctx, pic.file) == null } } // a disk check: never on the main thread
            if (missing) Note(L10n.tr("The picture file is missing, so the solid colour is shown. Choose it again."), warn = true)
            Text(L10n.tr("Picture fit"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            ChipRow(PictureFit.entries.toList(), pic.fit, { L10n.tr(it.label) }) { f -> change(w.copy(picture = pic.copy(fit = f))) }
            Text(L10n.tr("Dim"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            ChipRow(PictureLimits.DIMS, pic.dim, ::dimLabel) { d -> change(w.copy(picture = pic.copy(dim = d))) }
            if (pic.animated) {
                AnimationChoices(w) { change(it) }
                val plan = rememberPicturePlan(w)
                if (pic.gif != GifMode.Still) plan.note?.let { Note(L10n.tr(it.text), warn = true) }
                if (pic.plays) PowerNote()
            }
        }
    }
    pending?.let { n ->
        val video = n.picture.video
        PowerDialog(video, onUse = { pending = null; apply(n.copy(picture = n.picture.copy(powerOk = true))) }) {
            // Cancel (a video has no still to fall back on) or "Choose a still instead" (a GIF): the wallpaper stays a still.
            pending = null
            if (!video) apply(n.copy(picture = n.picture.copy(gif = GifMode.Still)))
        }
    }
}

/** The confirmation before an animated wallpaper is switched on: a small centred card, two 48 dp buttons, read as a dialog by TalkBack. */
@Composable
internal fun PowerDialog(video: Boolean, onUse: () -> Unit, onOther: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onOther) {
        Column(
            Modifier.clip(RoundedCornerShape(20.dp)).background(HyleColors.SurfaceTranslucent.copy(alpha = 0.98f)).padding(20.dp).semantics { liveRegion = LiveRegionMode.Assertive },
        ) {
            Text(L10n.tr("Animated wallpapers use more power"), color = HyleColors.InkBright, fontSize = 17.sp)
            Text(L10n.tr("This may consume excessive battery and compute, and may cause the phone to heat up or lag."), color = HyleColors.Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
            PaneButton(L10n.tr("Use anyway"), Modifier.fillMaxWidth(), primary = true, onClick = onUse)
            PaneButton(L10n.tr(if (video) "Cancel" else "Choose a still instead"), Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onOther)
        }
    }
}

/** The standing warning line, shown while an animated picture or a video is the wallpaper. */
@Composable
internal fun PowerNote(modifier: Modifier = Modifier) {
    Note(L10n.tr("Uses more power: this may drain the battery, heat the phone up or make it lag."), modifier.semantics { contentDescription = L10n.tr("Warning") + ": " + L10n.tr("Uses more power: this may drain the battery, heat the phone up or make it lag.") }, warn = true)
}

/** The System live wallpaper kind: what it is, the chooser button, the name of the active live wallpaper, and the dim. */
@Composable
internal fun SystemEditor(w: Wallpaper, apply: (Wallpaper) -> Unit) {
    val ctx = LocalContext.current
    val access = LocalSystemWallpaper.current ?: remember(ctx) { AndroidSystemWallpaper(ctx) }
    var label by remember { mutableStateOf(access.currentLabel()) }
    var failed by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Note(L10n.tr("Shows what your phone has behind Liftoff, a live wallpaper too. Liftoff draws nothing over it except the dim below."), Modifier.padding(top = 4.dp))
        Note(L10n.tr("System wallpaper: %s", label ?: L10n.tr("not a live wallpaper, or hidden by the system")))
        PaneButton(L10n.tr("Choose live wallpaper"), Modifier.padding(top = 10.dp), primary = true) { failed = !access.openChooser(); label = access.currentLabel() }
        if (failed) Note(L10n.tr("The wallpaper chooser could not be opened."), warn = true)
        Text(L10n.tr("Dim"), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
        ChipRow(PictureLimits.DIMS, w.systemDim, ::dimLabel) { d -> apply(w.copy(systemDim = d)) }
    }
}

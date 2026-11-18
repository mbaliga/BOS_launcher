package com.bos.sphere.feature.grid

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.UserManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.SearchHit
import com.bos.sphere.core.input.MenuAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PAL-02, PAL-03, PAL-06, PAL-18 (wave 25, owner item 6): every suggestion, action and shortcut row or pill of Radar has a
 * leading icon. The order is: the app shortcut's own icon, else a built-in glyph for system and launcher actions, else the
 * engine glyph (web), the avatar (contacts), and last the owning app's small icon. Never a blank gap.
 */
internal enum class ActionKind(val glyph: ImageVector) {
    Open(ActionGlyphs.Open), OpenOnDisplay(ActionGlyphs.Display), OpenBeside(ActionGlyphs.Beside), AppInfo(Glyphs.Info),
    AddToDock(ActionGlyphs.Dock), AddToWorkspace(ActionGlyphs.Layers), AddToHome(Glyphs.Home), Uninstall(ActionGlyphs.Trash),
    Rename(ActionGlyphs.Pencil), RemoveFromHome(ActionGlyphs.MinusCircle), MoveToFolder(FolderGlyph),
    SearchAlias(ActionGlyphs.Tag), MoreOptions(ActionGlyphs.MoreDots), Preview(ActionGlyphs.Eye),
    Share(ActionGlyphs.Share), Copy(ActionGlyphs.Copy), Call(Glyphs.Phone), Message(Glyphs.Message), Email(Glyphs.Mail),
    OpenInBrowser(Glyphs.Globe), SearchWeb(Glyphs.Search), SetAlarm(ActionGlyphs.Alarm), SetTimer(ActionGlyphs.Timer),
    ToggleSetting(Glyphs.Sliders), OpenSettingsPage(Glyphs.Gear), CreateContact(ActionGlyphs.UserPlus), OpenFile(DocGlyph),
    LauncherSettings(Glyphs.Sliders), EditHome(ActionGlyphs.Pencil), SwitchWorkspace(ActionGlyphs.Layers), OpenDrawer(Glyphs.Apps),
    OpenLaunchpad(Glyphs.Bell), ShowKeys(ActionGlyphs.Keyboard), Answer(Glyphs.Calculator), AppShortcut(Glyphs.Bolt),
    FilterFiles(FolderGlyph), FilterContent(ActionGlyphs.Eye), FilterKind(ActionGlyphs.Tag), RefineApps(Glyphs.Apps),
}

internal object ActionIcons {
    /** Every shared menu action of an app result, exhaustive: a new [MenuAction] without an icon does not compile. */
    fun kind(a: MenuAction): ActionKind = when (a) {
        MenuAction.Open -> ActionKind.Open
        MenuAction.OpenOnDisplay -> ActionKind.OpenOnDisplay
        MenuAction.OpenBeside -> ActionKind.OpenBeside
        MenuAction.AppInfo -> ActionKind.AppInfo
        MenuAction.AddToDock -> ActionKind.AddToDock
        MenuAction.AddToWorkspace -> ActionKind.AddToWorkspace
        MenuAction.Uninstall -> ActionKind.Uninstall
        MenuAction.Rename -> ActionKind.Rename
        MenuAction.Remove -> ActionKind.RemoveFromHome
        MenuAction.MoveToFolder -> ActionKind.MoveToFolder
    }

    /** The launcher commands the palette offers ([dev.aarso.search.palette.LauncherActions]) by their command name. */
    fun kindOfCommand(command: String): ActionKind = when (command) {
        "OpenSettings" -> ActionKind.LauncherSettings
        "EditMode" -> ActionKind.EditHome
        "NextWorkspace", "PreviousWorkspace", "GoToWorkspace" -> ActionKind.SwitchWorkspace
        "OpenDrawer" -> ActionKind.OpenDrawer
        "OpenLaunchpad" -> ActionKind.OpenLaunchpad
        "ShortcutSheet" -> ActionKind.ShowKeys
        else -> ActionKind.AppShortcut
    }

    fun kind(e: ResultMenuEntry): ActionKind = when {
        e.action != null -> kind(e.action)
        e.preview -> ActionKind.Preview
        e.alias -> ActionKind.SearchAlias
        e.more -> ActionKind.MoreOptions
        else -> ActionKind.AppShortcut
    }

    /** The glyph of a hit that is not an app shortcut, or null when its kind decides (files, web). */
    fun kind(hit: SearchHit): ActionKind? = when {
        hit.copyText != null -> ActionKind.Answer
        hit.extras["command"] != null -> kindOfCommand(hit.extras.getValue("command"))
        hit.extras["intentAction"]?.startsWith("android.settings") == true -> ActionKind.OpenSettingsPage
        hit.extras["intentAction"] != null -> ActionKind.OpenSettingsPage
        NestedShortcuts.isAppShortcut(hit) -> ActionKind.AppShortcut
        else -> null
    }
}

/** Outline glyphs in the style of [Glyphs]: 24 dp viewport, round caps, tinted by the [Icon] that draws them. */
internal object ActionGlyphs {
    private fun g(name: String, autoMirror: Boolean = false, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 20.dp, 20.dp, 24f, 24f, autoMirror = autoMirror).apply {
            path(fill = null, stroke = SolidColor(Color.White), strokeLineWidth = 2.1f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block)
        }.build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }
    private fun PathBuilder.box(l: Float, t: Float, r: Float, b: Float, rad: Float = 2.5f) {
        moveTo(l + rad, t); lineTo(r - rad, t); quadTo(r, t, r, t + rad); lineTo(r, b - rad); quadTo(r, b, r - rad, b)
        lineTo(l + rad, b); quadTo(l, b, l, b - rad); lineTo(l, t + rad); quadTo(l, t, l + rad, t); close()
    }

    val Open = g("open", autoMirror = true) { moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); moveTo(20f, 4f); lineTo(11f, 13f); moveTo(18f, 14f); lineTo(18f, 18f); quadTo(18f, 20f, 16f, 20f); lineTo(6f, 20f); quadTo(4f, 20f, 4f, 18f); lineTo(4f, 8f); quadTo(4f, 6f, 6f, 6f); lineTo(10f, 6f) }
    val Display = g("display") { box(3f, 4f, 21f, 16f); moveTo(9f, 20f); lineTo(15f, 20f); moveTo(12f, 16f); lineTo(12f, 20f) }
    val Beside = g("beside") { box(3f, 5f, 21f, 19f); moveTo(12f, 5f); lineTo(12f, 19f) }
    val Dock = g("dock") { box(3f, 5f, 21f, 19f); moveTo(7f, 15f); lineTo(17f, 15f) }
    val Layers = g("layers") { moveTo(12f, 4f); lineTo(21f, 9f); lineTo(12f, 14f); lineTo(3f, 9f); close(); moveTo(3f, 14f); lineTo(12f, 19f); lineTo(21f, 14f) }
    val Trash = g("trash") { moveTo(4f, 7f); lineTo(20f, 7f); moveTo(9f, 7f); lineTo(9f, 4.5f); lineTo(15f, 4.5f); lineTo(15f, 7f); moveTo(6f, 7f); lineTo(7f, 19f); quadTo(7.1f, 20f, 8.2f, 20f); lineTo(15.8f, 20f); quadTo(16.9f, 20f, 17f, 19f); lineTo(18f, 7f) }
    val Pencil = g("pencil", autoMirror = true) { moveTo(4f, 20f); lineTo(5f, 15f); lineTo(16f, 4f); lineTo(20f, 8f); lineTo(9f, 19f); close(); moveTo(14f, 6f); lineTo(18f, 10f) }
    val MinusCircle = g("minus-circle") { circle(12f, 12f, 8.5f); moveTo(8f, 12f); lineTo(16f, 12f) }
    val Tag = g("tag", autoMirror = true) { moveTo(3f, 12f); lineTo(3f, 4f); lineTo(11f, 4f); lineTo(21f, 14f); lineTo(14f, 21f); close(); moveTo(7.5f, 8.5f); lineTo(7.6f, 8.5f) }
    val MoreDots = g("more") { moveTo(6f, 12f); lineTo(6.1f, 12f); moveTo(12f, 12f); lineTo(12.1f, 12f); moveTo(18f, 12f); lineTo(18.1f, 12f) }
    val Eye = g("eye") { moveTo(2.5f, 12f); quadTo(12f, 3f, 21.5f, 12f); quadTo(12f, 21f, 2.5f, 12f); close(); circle(12f, 12f, 3f) }
    val Share = g("share", autoMirror = true) { circle(6f, 12f, 2.6f); circle(18f, 6f, 2.6f); circle(18f, 18f, 2.6f); moveTo(8.3f, 10.8f); lineTo(15.7f, 7.2f); moveTo(8.3f, 13.2f); lineTo(15.7f, 16.8f) }
    val Copy = g("copy") { box(8f, 8f, 20f, 20f); moveTo(16f, 8f); lineTo(16f, 6f); quadTo(16f, 4f, 14f, 4f); lineTo(6f, 4f); quadTo(4f, 4f, 4f, 6f); lineTo(4f, 14f); quadTo(4f, 16f, 6f, 16f); lineTo(8f, 16f) }
    val Alarm = g("alarm") { circle(12f, 13f, 7f); moveTo(12f, 9.5f); lineTo(12f, 13f); lineTo(14.5f, 14.5f); moveTo(4f, 6f); lineTo(7f, 3.5f); moveTo(20f, 6f); lineTo(17f, 3.5f) }
    val Timer = g("timer") { circle(12f, 13.5f, 7f); moveTo(12f, 13.5f); lineTo(14.5f, 10.5f); moveTo(9.5f, 3.5f); lineTo(14.5f, 3.5f) }
    val UserPlus = g("user-plus") { circle(10f, 8f, 3.6f); moveTo(3.5f, 20f); quadTo(3.5f, 14f, 10f, 14f); quadTo(14f, 14f, 15.5f, 16.5f); moveTo(19f, 14f); lineTo(19f, 20f); moveTo(16f, 17f); lineTo(22f, 17f) }
    val Keyboard = g("keyboard") { box(2.5f, 6f, 21.5f, 18f); moveTo(6f, 10f); lineTo(6.1f, 10f); moveTo(10f, 10f); lineTo(10.1f, 10f); moveTo(14f, 10f); lineTo(14.1f, 10f); moveTo(18f, 10f); lineTo(18.1f, 10f); moveTo(8f, 14.5f); lineTo(16f, 14.5f) }
}

/** The shortcut icons of Radar's app shortcuts: loaded off the main thread, kept in a small bounded cache (PAL-03). */
internal fun interface ShortcutIconSource {
    /** The shortcut's own icon, or null when it has none or cannot be read. Runs on a background thread. */
    fun load(context: Context, packageName: String, shortcutId: String, userSerial: Int, densityDpi: Int): Drawable?
}

internal object ShortcutIcons {
    /** Replaced in tests; the default reads the icon through the public [LauncherApps.getShortcutIconDrawable]. */
    @Volatile var source: ShortcutIconSource = ShortcutIconSource { ctx, pkg, id, serial, dpi ->
        runCatching {
            val la = ctx.getSystemService(LauncherApps::class.java) ?: return@runCatching null
            if (!la.hasShortcutHostPermission()) return@runCatching null
            val um = ctx.getSystemService(UserManager::class.java)
            val user = um.userProfiles.firstOrNull { um.getSerialNumberForUser(it).toInt() == serial } ?: android.os.Process.myUserHandle()
            val q = LauncherApps.ShortcutQuery().setPackage(pkg).setShortcutIds(listOf(id)).setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
            la.getShortcuts(q, user)?.firstOrNull()?.let { la.getShortcutIconDrawable(it, dpi) }
        }.getOrNull()
    }

    /** At most 48 icons (a count bound: a result page never shows more); a miss is remembered too, so a row does not reload on every scroll. */
    private val cache = LruCache<String, Any>(48)
    private val none = Any()

    private fun key(h: SearchHit) = "${h.extras["package"]}/${h.extras["user"] ?: "0"}/${h.extras["shortcutId"]}"

    /** The icon if it is already loaded; [Miss] when it is known to have none; null when not tried yet. */
    fun peek(h: SearchHit): Any? = cache.get(key(h))
    val Miss: Any get() = none

    suspend fun load(context: Context, h: SearchHit, px: Int): ImageBitmap? {
        val k = key(h)
        cache.get(k)?.let { return it as? ImageBitmap }
        val pkg = h.extras["package"] ?: return null
        val id = h.extras["shortcutId"] ?: return null
        val app = context.applicationContext
        val bmp = withContext(Dispatchers.IO) {
            runCatching { source.load(app, pkg, id, h.extras["user"]?.toIntOrNull() ?: 0, app.resources.displayMetrics.densityDpi)?.let { toBitmap(it, px) } }.getOrNull()
        }
        cache.put(k, bmp ?: none)
        return bmp
    }

    /** The owning app's launcher icon for a shortcut with none of its own (last resort), cached under its package. */
    suspend fun loadAppIcon(context: Context, pkg: String, px: Int): ImageBitmap? {
        val k = "app:$pkg"
        cache.get(k)?.let { return it as? ImageBitmap }
        val app = context.applicationContext
        val bmp = withContext(Dispatchers.IO) { runCatching { toBitmap(app.packageManager.getApplicationIcon(pkg), px) }.getOrNull() }
        cache.put(k, bmp ?: none)
        return bmp
    }

    fun clear() = cache.evictAll()

    private fun toBitmap(d: Drawable, px: Int): ImageBitmap {
        val size = px.coerceIn(16, 256)
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return b.asImageBitmap()
    }
}

/**
 * The leading icon of an Actions row, a shortcut pill or a menu entry: the shortcut's own icon when it has one, else the built-in
 * glyph of its kind, else (for an app shortcut) the owning app's small icon. [contentDescription] is null: the row's text says it.
 */
@Composable
internal fun HitLeadingIcon(hit: SearchHit, size: Dp, tint: Color, modifier: Modifier = Modifier, app: AppEntry? = null) {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    if (NestedShortcuts.isAppShortcut(hit)) {
        val icon by produceState<Any?>(ShortcutIcons.peek(hit), hit.id) {
            if (value == null) value = ShortcutIcons.load(ctx, hit, px) ?: ShortcutIcons.Miss
        }
        val appIcon by produceState<Any?>(null, hit.id, icon) {
            val pkg = hit.extras["package"]
            if (icon === ShortcutIcons.Miss && app == null && pkg != null) value = ShortcutIcons.loadAppIcon(ctx, pkg, px)
        }
        val shown = (icon as? ImageBitmap) ?: (appIcon as? ImageBitmap)
        when {
            shown != null -> Image(shown, null, contentScale = ContentScale.Fit, modifier = modifier.size(size))
            icon === ShortcutIcons.Miss && app != null -> AppIcon(app, size, modifier)
            else -> Icon(ActionKind.AppShortcut.glyph, null, tint = tint, modifier = modifier.size(size)) // while loading: the built-in glyph, never a gap
        }
    } else {
        Icon(hitGlyph(hit), null, tint = tint, modifier = modifier.size(size))
    }
}

/** A built-in glyph of an [ActionKind] at [size] (menu rows 20 dp, pills 16 dp). */
@Composable
internal fun KindIcon(kind: ActionKind, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Icon(kind.glyph, null, tint = tint, modifier = modifier.size(size))
}

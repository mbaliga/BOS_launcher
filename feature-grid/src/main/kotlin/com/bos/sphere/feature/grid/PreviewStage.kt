package com.bos.sphere.feature.grid

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import com.bos.sphere.core.design.LocalPreviewSandbox
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.data.Corner
import com.bos.sphere.core.data.HomeGesture
import com.bos.sphere.core.data.LauncherPrefs
import com.bos.sphere.core.data.WebSuggestSource
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.core.design.rememberMotionPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Wave 57 (previewAll), CFG-51..CFG-53: the renderers the Preview window uses for the surfaces that are not the home. The app supplies them (it
 * holds the prefs, the apps, the dock and the Comms panel); each is composed off-screen in [PreviewSandbox] at the size of the real screen and drawn
 * scaled into the window. A missing renderer falls back to the home capture. [prefs] is read only through [rememberCoalesced], so a slider drag
 * refreshes the picture at most every [PreviewStage.COALESCE_MS] and an unchanged value never does.
 */
class PreviewSurfaces(
    val prefs: () -> LauncherPrefs,
    /** True while the home behind Settings shows the Glance page: the capture is then the exact Glance picture. */
    val homeOnGlance: () -> Boolean = { false },
    val glance: (@Composable (LauncherPrefs) -> Unit)? = { p -> PreviewGlance(p) },
    val radar: (@Composable (LauncherPrefs) -> Unit)? = null,
    val disc: (@Composable (LauncherPrefs) -> Unit)? = null,
    val sidePanel: (@Composable (LauncherPrefs) -> Unit)? = null,
    val dock: (@Composable (LauncherPrefs) -> Unit)? = null,
    /** The dock's own words for the screen reader ("Standard, Bottom"): its settings are not in [LauncherPrefs]. */
    val dockDetail: @Composable () -> String? = { null },
)

/** Test tags of the stage: one per topic, on the layer that draws it. */
object PreviewStageTags {
    /** The window bar's title: the caption of the topic shown. */
    const val CAPTION = "previewCaption"
    const val SAMPLE = "previewSample"
    const val SANDBOX = "previewSandbox"
    const val CORNERS = "previewCorners"
    const val GESTURE = "previewGesture"
    fun topic(t: PreviewTopic) = "previewTopic.${t.name}"
}


/** Pure text of the stage: the caption and what the screen reader says ("Preview of Radar, Orbit layout"). */
object PreviewText {
    fun caption(t: PreviewTopic): String = L10n.tr(t.label)

    fun detail(s: PreviewSubject, p: LauncherPrefs, dock: String? = null): String? = when (s.topic) {
        PreviewTopic.Home -> L10n.tr(if (p.homePages == com.bos.sphere.core.data.HomePages.Continuous) "Scroll" else "Pages")
        PreviewTopic.Glance -> s.corner?.let { L10n.tr("%s hotspot", L10n.tr(it.label)) } ?: L10n.tr(p.clockStyle.label)
        PreviewTopic.Corners -> s.corner?.let { L10n.tr(it.label) } ?: L10n.tr(p.cornerStyle.label)
        PreviewTopic.Gestures -> s.gesture?.let { L10n.tr(it.label) }
        PreviewTopic.Dock -> dock
        PreviewTopic.Radar -> L10n.tr("%s layout", L10n.tr(p.searchLayout.label))
        PreviewTopic.Disc -> L10n.tr(p.wheelGrouping.label)
        PreviewTopic.SidePanel -> L10n.tr(p.launchpadTheme.label)
        PreviewTopic.Look -> L10n.tr(p.themeMode.label)
        PreviewTopic.None -> null
    }

    fun description(s: PreviewSubject, p: LauncherPrefs, dock: String? = null): String {
        if (s.topic == PreviewTopic.Home) return L10n.tr("Live preview of your home") // the wording of wave 39, kept for the home
        val d = detail(s, p, dock)
        return if (d.isNullOrBlank()) L10n.tr("Preview of %s", caption(s.topic)) else L10n.tr("Preview of %s, %s", caption(s.topic), d)
    }

    /** Topics whose picture holds sample data (never the user's own notifications or a real web request). */
    fun sample(t: PreviewTopic): Boolean = t == PreviewTopic.Radar || t == PreviewTopic.SidePanel
}

/** The sample data of the sandboxes: no network, no private content. */
object PreviewSamples {
    /** A two-letter query from the real app list, so the results are the user's own apps. */
    fun query(apps: List<AppEntry>): String = apps.firstOrNull { it.label.trim().length >= 3 }?.label?.trim()?.take(2)?.lowercase() ?: "ma"

    /** Web and Play Store suggestions made up on the device: "<query> sample 1" and so on. Never opens a connection. */
    fun webSource(): WebSuggestSource = WebSuggestSource(fetcher = { url ->
        val q = Regex("[?&](?:q|query|term)=([^&]*)").find(url)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
        val word = L10n.tr("sample")
        "[\"$q\",[\"$q $word 1\",\"$q $word 2\",\"$q $word 3\"]]"
    }, debounceMs = 0L)
}

/**
 * A value read through a snapshot-aware [read], held at most once per [windowMs]: the first change shows at once, later ones within the window
 * are folded into one (the latest wins), and a value equal to the last one shown changes nothing.
 */
@Composable
fun <T> rememberCoalesced(read: () -> T, windowMs: Long = PreviewStage.COALESCE_MS): State<T> {
    val latest by rememberUpdatedState(read)
    val state = remember { mutableStateOf(Snapshot.withoutReadObservation { read() }) }
    LaunchedEffect(Unit) {
        snapshotFlow { latest() }.distinctUntilChanged().collect { v ->
            if (v != state.value) { state.value = v; delay(windowMs) }
        }
    }
    return state
}

object PreviewStage {
    /** CFG-51: a slider drag refreshes a sandboxed picture at most this often. */
    const val COALESCE_MS = 60L
    const val CROSSFADE_MS = 180
    /** Test seam: keep the sandboxes' own semantics, so a test can find the composable a topic draws. The app never sets it. */
    @Volatile var exposeSandboxSemantics: Boolean = false
}

/**
 * Composes [content] at the home's own size ([homeW] x [homeH] pixels, the real screen's density) and draws it scaled into the window with one
 * layer transform, so the real layouts (insets, sizes, text) come out exactly as on the screen. Nothing in it can be touched or focused, and the
 * screen reader hears the window's one description instead of the sandbox's rows.
 */
@Composable
internal fun PreviewSandbox(homeW: Int, homeH: Int, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    if (homeW <= 0 || homeH <= 0) return
    val semantics = if (PreviewStage.exposeSandboxSemantics) Modifier else Modifier.clearAndSetSemantics { }
    CompositionLocalProvider(LocalPreviewSandbox provides true) {
        Layout(
            content = { Box(Modifier.fillMaxSize(), content = content) },
            modifier = modifier.fillMaxSize().clipToBounds().then(semantics).testTag(PreviewStageTags.SANDBOX),
        ) { measurables, constraints ->
            val k = if (homeW == 0) 1f else constraints.maxWidth.toFloat() / homeW
            val p = measurables.first().measure(Constraints.fixed(homeW, homeH))
            layout(constraints.maxWidth, constraints.maxHeight) {
                p.placeWithLayer(0, 0) { scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0f, 0f) }
            }
        }
    }
}

/** The live picture for [subject]: cross-fades between topics; sample data carries a "Sample" badge (the caption is the window bar's title). */
@Composable
internal fun PreviewStageContent(capture: HomeCapture, subject: PreviewSubject, surfaces: PreviewSurfaces?, prefs: LauncherPrefs) {
    val reduced = rememberMotionPrefs().reduced
    Crossfade(subject.topic, animationSpec = tween(if (reduced) 0 else PreviewStage.CROSSFADE_MS), label = "previewTopic") { topic ->
        Box(Modifier.fillMaxSize().clipToBounds().testTag(PreviewStageTags.topic(topic))) {
            val hw = capture.size.width
            val hh = capture.size.height
            fun sandboxed(r: (@Composable (LauncherPrefs) -> Unit)?): (@Composable () -> Unit)? = r?.let { f -> { PreviewSandbox(hw, hh) { f(prefs) } } }
            when (topic) {
                PreviewTopic.Home, PreviewTopic.Look -> CaptureLayer(capture, home = true)
                PreviewTopic.Glance -> {
                    val g = sandboxed(surfaces?.glance)
                    if (g == null || surfaces?.homeOnGlance?.invoke() == true) CaptureLayer(capture, home = true)
                    else { CaptureLayer(capture, home = false); g() }
                    if (subject.corner != null) CornerMarks(subject.corner, reduced, prefs.glanceHotspots)
                }
                PreviewTopic.Corners -> { CaptureLayer(capture, home = true); CornerMarks(subject.corner, reduced, Corner.entries.toSet()) }
                PreviewTopic.Gestures -> { CaptureLayer(capture, home = true); GestureHint(subject.gesture, reduced) }
                PreviewTopic.Dock -> sandboxed(surfaces?.dock)?.let { CaptureLayer(capture, home = false); it() } ?: CaptureLayer(capture, home = true)
                PreviewTopic.Radar -> sandboxed(surfaces?.radar)?.let { CaptureLayer(capture, home = false); it() } ?: CaptureLayer(capture, home = true)
                PreviewTopic.Disc -> sandboxed(surfaces?.disc)?.let { CaptureLayer(capture, home = true); it() } ?: CaptureLayer(capture, home = true)
                PreviewTopic.SidePanel -> sandboxed(surfaces?.sidePanel)?.invoke() ?: CaptureLayer(capture, home = true)
                PreviewTopic.None -> Unit
            }
            if (PreviewText.sample(topic)) Badge(L10n.tr("Sample"), Modifier.align(Alignment.TopEnd))
        }
    }
}

/** The recorded home (and the fixed wallpaper under it), scaled to the window; [home] false draws the wallpaper alone. */
@Composable
private fun CaptureLayer(capture: HomeCapture, home: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val k = DockGeometry.scale(capture.size.width, size.width)
        clipRect { scale(k, k, pivot = Offset.Zero) { drawLayer(capture.wallpaperLayer); if (home) drawLayer(capture.layer) } }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier) {
    BasicText(
        text, modifier.padding(4.dp).background(HyleColors.Violet.copy(alpha = 0.9f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp).testTag(PreviewStageTags.SAMPLE),
        style = TextStyle(color = Color.White, fontSize = 9.sp), maxLines = 1,
    )
}

/** Pure geometry of the corner marks and the gesture hints, in fractions of the picture. */
object PreviewMarks {
    /** Centre of [c]'s mark (the screen corner itself). */
    fun cornerCentre(c: Corner, w: Float, h: Float): Offset = when (c) {
        Corner.TopLeft -> Offset(0f, 0f)
        Corner.TopRight -> Offset(w, 0f)
        Corner.BottomRight -> Offset(w, h)
        Corner.BottomLeft -> Offset(0f, h)
    }

    /** The start and end of a swipe hint, or null for a gesture that is not a swipe. */
    fun swipe(g: HomeGesture?, w: Float, h: Float): Pair<Offset, Offset>? = when (g) {
        null, HomeGesture.SwipeUp -> Offset(w / 2, h * 0.72f) to Offset(w / 2, h * 0.34f)
        HomeGesture.SwipeDown -> Offset(w / 2, h * 0.34f) to Offset(w / 2, h * 0.72f)
        HomeGesture.SwipeLeft -> Offset(w * 0.75f, h / 2) to Offset(w * 0.25f, h / 2)
        HomeGesture.SwipeRight -> Offset(w * 0.25f, h / 2) to Offset(w * 0.75f, h / 2)
        else -> null
    }
}

/** The four corners ringed, the one being configured pulsing (still under reduced motion: a thicker ring). Only [shown] corners are ringed. */
@Composable
private fun CornerMarks(selected: Corner?, reduced: Boolean, shown: Set<Corner>) {
    val motion = rememberMotionPrefs()
    val pulse = if (reduced || motion.reduced || selected == null) remember { mutableStateOf(1f) } else
        rememberInfiniteTransition(label = "cornerPulse").animateFloat(0.55f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    Canvas(Modifier.fillMaxSize().testTag(PreviewStageTags.CORNERS)) {
        val r = minOf(size.width, size.height) * 0.16f
        for (c in Corner.entries) {
            if (c !in shown && c != selected) continue
            val centre = PreviewMarks.cornerCentre(c, size.width, size.height)
            val on = c == selected
            val rr = if (on) r * (0.85f + 0.25f * pulse.value) else r
            drawCircle(HyleColors.Violet.copy(alpha = if (on) 0.30f * pulse.value else 0.12f), rr, centre)
            drawCircle(if (on) HyleColors.Violet else Color.White.copy(alpha = 0.55f), rr, centre, style = Stroke(if (on) 2.5.dp.toPx() else 1.dp.toPx()))
        }
    }
}

/** A small hint of the gesture being configured, drawn over the home: a moving dot for a swipe, two for a pinch, ripples for taps; an arrow under reduced motion. */
@Composable
private fun GestureHint(g: HomeGesture?, reduced: Boolean) {
    val motion = rememberMotionPrefs()
    val still = reduced || motion.reduced
    val t = if (still) remember { mutableStateOf(1f) } else
        rememberInfiniteTransition(label = "gestureHint").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "t")
    Canvas(Modifier.fillMaxSize().testTag(PreviewStageTags.GESTURE)) {
        val ink = Color.White
        val accent = HyleColors.Violet
        val dot = minOf(size.width, size.height) * 0.045f
        val sw = 2.dp.toPx()
        val p = t.value
        val swipe = PreviewMarks.swipe(g, size.width, size.height)
        val centre = Offset(size.width / 2, size.height / 2)
        when {
            swipe != null -> {
                val (a, b) = swipe
                if (still) arrow(a, b, ink, sw)
                else {
                    val f = (p / 0.8f).coerceIn(0f, 1f)
                    val at = a + (b - a) * f
                    drawLine(ink.copy(alpha = 0.5f * (1f - f)), a, at, sw, StrokeCap.Round)
                    drawCircle(accent.copy(alpha = 1f - (p - 0.8f).coerceAtLeast(0f) * 5f), dot, at)
                }
            }
            g == HomeGesture.PinchIn || g == HomeGesture.PinchOut -> {
                val far = minOf(size.width, size.height) * 0.3f
                val near = far * 0.25f
                val inward = g == HomeGesture.PinchIn
                val d = if (still) far else if (inward) far + (near - far) * p else near + (far - near) * p
                val v = Offset(d * 0.7071f, d * 0.7071f)
                if (still) {
                    val n = Offset(near * 0.7071f, near * 0.7071f)
                    if (inward) { arrow(centre - v, centre - n, ink, sw); arrow(centre + v, centre + n, ink, sw) } else { arrow(centre - n, centre - v, ink, sw); arrow(centre + n, centre + v, ink, sw) }
                } else { drawCircle(accent, dot, centre - v); drawCircle(accent, dot, centre + v) }
            }
            g == HomeGesture.DoubleTap || g == HomeGesture.LongPress -> {
                val r = minOf(size.width, size.height) * 0.12f
                if (still) { drawCircle(ink, r * 0.6f, centre, style = Stroke(sw)); if (g == HomeGesture.DoubleTap) drawCircle(ink, r, centre, style = Stroke(sw)) }
                else {
                    val phase = if (g == HomeGesture.DoubleTap) (p * 2f) % 1f else p
                    drawCircle(accent.copy(alpha = 1f - phase), r * (0.3f + phase), centre, style = Stroke(sw))
                    drawCircle(accent, dot, centre)
                }
            }
            else -> { // a motion gesture: the phone tilting back and forth
                val ang = if (still) 0f else kotlin.math.sin(p * 2 * Math.PI).toFloat() * 14f
                val w = size.width * 0.22f
                val h = w * 1.8f
                rotate(ang, centre) {
                    drawRoundRect(ink, Offset(centre.x - w / 2, centre.y - h / 2), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(w * 0.18f), style = Stroke(sw))
                }
                if (still) { arrow(Offset(centre.x - w, centre.y + h * 0.6f), Offset(centre.x + w, centre.y + h * 0.6f), ink, sw) }
            }
        }
    }
}

private fun DrawScope.arrow(a: Offset, b: Offset, c: Color, sw: Float) {
    drawLine(Color.Black.copy(alpha = 0.45f), a, b, sw * 2.4f, StrokeCap.Round) // a dark halo: the arrow reads over light icons too
    drawLine(c, a, b, sw, StrokeCap.Round)
    val d = b - a
    val len = d.getDistance().coerceAtLeast(1f)
    val u = Offset(d.x / len, d.y / len)
    val head = 8.dp.toPx()
    val n = Offset(-u.y, u.x)
    val path = Path().apply { moveTo(b.x, b.y); lineTo(b.x - u.x * head + n.x * head * 0.6f, b.y - u.y * head + n.y * head * 0.6f); lineTo(b.x - u.x * head - n.x * head * 0.6f, b.y - u.y * head - n.y * head * 0.6f); close() }
    drawPath(path, c)
}

// ---- the renderers of feature-grid's own surfaces --------------------------------------------------------------------------------------

/** CFG-51: the Glance page with the current prefs: the clock at its edited place, its style, date and colour (the wallpaper is drawn below it). */
@Composable
fun PreviewGlance(prefs: LauncherPrefs) {
    GlanceSurface(prefs = prefs, hasLaunchpad = prefs.leftPage != com.bos.sphere.core.data.LeftPage.Off, indicator = false, transparentBase = true)
}

/**
 * CFG-52: the real Radar ([SearchScreen]) with a sample query taken from the real app list, the user's layout, background and motion, and made-up
 * web suggestions (the "Sample" badge says so): no contacts, no files, no providers, no network.
 */
@Composable
fun PreviewRadar(prefs: LauncherPrefs, apps: List<AppEntry>) {
    val q = remember(apps) { PreviewSamples.query(apps) }
    val web = remember { PreviewSamples.webSource() }
    SearchScreen(
        apps = apps, recent = emptyList(), prefs = prefs, contacts = null,
        onLaunch = {}, onLongPressApp = {}, onOpenIntent = {}, onRequestContactsPermission = {}, onDismiss = {},
        hub = null, onOpenHit = null, onRunAction = {}, onRequestFilePermission = {}, sounds = null,
        initialQuery = q, otherDisplaysOverride = 0, learnedOverride = emptyMap(), voiceAvailable = false,
        webSuggest = web, webNoteSeen = { true },
    )
}

/** CFG-52: the real open disc with the user's ring segments, shortcuts, labels and wheel groups. */
@Composable
fun PreviewDisc(prefs: LauncherPrefs, apps: List<AppEntry>, actions: List<PuckAction>, ringSegments: Int, showRingLabels: Boolean, anchor: Corner = Corner.BottomLeft) {
    WheelPreviewContent(anchor, apps, prefs.wheelGrouping, actions, ringSegments, showRingLabels, Modifier.fillMaxSize())
}

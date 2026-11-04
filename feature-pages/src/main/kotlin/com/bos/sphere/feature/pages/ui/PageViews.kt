package com.bos.sphere.feature.pages.ui

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.feature.pages.ImportRecord
import com.bos.sphere.feature.pages.NavMode
import com.bos.sphere.feature.pages.OpenTarget
import com.bos.sphere.feature.pages.Page
import com.bos.sphere.feature.pages.PageKind
import com.bos.sphere.feature.pages.PageTurn
import com.bos.sphere.feature.pages.PastEnd
import com.bos.sphere.feature.pages.R
import com.bos.sphere.feature.pages.ReadyContext
import com.bos.sphere.feature.pages.ThemeTokens
import com.bos.sphere.feature.pages.UrlRules
import com.bos.sphere.feature.pages.runtime.HostBridge
import com.bos.sphere.feature.pages.runtime.PageViewEvents
import com.bos.sphere.feature.pages.runtime.createHtmlView
import com.bos.sphere.feature.pages.runtime.createWebsiteView
import com.bos.sphere.feature.pages.runtime.installServiceWorkerGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private var serviceWorkerGuardInstalled = false

/** The launcher's palette as widget theme tokens (read from `core-design`, not changed). */
private val themeTokens: Map<String, String> by lazy {
    fun argb(c: Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL
    ThemeTokens.build(
        linkedMapOf(
            "--field" to argb(HyleColors.Background),
            "--surface" to argb(HyleColors.Surface),
            "--ink" to argb(HyleColors.Ink),
            "--ink-bright" to argb(HyleColors.InkBright),
            "--ink-dim" to argb(HyleColors.InkDim),
            "--violet" to argb(HyleColors.Violet),
            "--violet-soft" to argb(HyleColors.VioletSoft),
            "--hairline" to argb(HyleColors.Hairline),
            "--native" to argb(HyleColors.ProvenanceOnDevice),
            "--cloud" to argb(HyleColors.ProvenanceCloud),
        ),
    )
}

/** What a page's name is when the user hasn't given one. */
@Composable
internal fun PagesUi.titleOf(page: Page, hooks: AppWidgetPageHooks?): String = page.title.ifBlank {
    when (page.kind) {
        PageKind.Home -> stringResource(R.string.pages_home)
        PageKind.AppWidget -> hooks?.label(page.widgetSlot) ?: stringResource(R.string.pages_widget_default)
        PageKind.Html -> runtime.files.record(page.id)?.name ?: stringResource(R.string.pages_kind_html_page)
        PageKind.Web -> UrlRules.hostOf(page.url) ?: page.url
    }
}

/**
 * True while this page is the one on screen: current, the pager at rest, the launcher in front. Gates
 * what a page may do that the user sees (JavaScript dialogs, a widget's `orrery.open`).
 */
@Composable
private fun PagesUi.rememberInFront(isCurrent: Boolean): () -> Boolean {
    val current by rememberUpdatedState(isCurrent)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return remember(lifecycle) {
        { current && PageTurn.atRest(position.value) && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
    }
}

/**
 * Off screen or on screen: a page that stops being current is paused and gives up focus and the
 * keyboard, so typing can't go on into a page that slid away (neighbour pages stay composed).
 */
private fun WebView.trackCurrent(isCurrent: Boolean) {
    if (isCurrent) {
        onResume()
        return
    }
    onPause()
    if (hasFocus()) {
        val token = windowToken
        clearFocus()
        if (token != null) {
            runCatching { context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(token, 0) }
        }
    }
}

// --- an HTML widget or page --------------------------------------------------------------------

@Composable
internal fun PagesUi.HtmlPage(page: Page, isCurrent: Boolean) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val loaded by produceState<Pair<Boolean, ImportRecord?>>(false to null, page.id, page.rev) {
        value = true to withContext(Dispatchers.IO) { runtime.files.record(page.id) }
    }
    val (done, record) = loaded
    var crashed by remember(page.id, page.rev, page.network) { mutableStateOf(false) }
    var missingWebView by remember(page.id, page.rev, page.network) { mutableStateOf(false) }
    var sizeDp by remember { mutableStateOf(0 to 0) }
    val inFront = rememberInFront(isCurrent)
    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .onSizeChanged { sizeDp = (it.width / density).toInt() to (it.height / density).toInt() },
    ) {
        when {
            !done -> Message(stringResource(R.string.pages_loading), null)
            record == null -> Message(
                stringResource(R.string.pages_files_missing_title),
                stringResource(R.string.pages_files_missing_body),
                action = stringResource(R.string.pages_import_again) to { dialog = PagesDialog.Info(page.id) },
            )
            missingWebView -> Message(stringResource(R.string.pages_webview_missing), null)
            crashed -> Message(
                stringResource(R.string.pages_crashed_title),
                stringResource(R.string.pages_crashed_body),
                action = stringResource(R.string.pages_reload) to { crashed = false },
            )
            else -> key(page.id, page.rev, page.network) {
                // Survives recomposition, so onRelease closes the bridge the factory made.
                val bridge = remember { arrayOfNulls<HostBridge>(1) }
                AndroidView(
                    factory = { ctx ->
                        if (!serviceWorkerGuardInstalled) {
                            installServiceWorkerGuard()
                            serviceWorkerGuardInstalled = true
                        }
                        val events = object : PageViewEvents {
                            override fun onRendererGone() { crashed = true }
                            override fun onDownloadRefused(fileName: String) = refusedDownload(ctx, fileName)
                            override fun onOpen(target: OpenTarget) = open(target)
                        }
                        val created = createHtmlView(
                            context = ctx,
                            runtime = runtime,
                            pageId = page.id,
                            isPackage = page.isPackage,
                            networkOn = page.network && !page.isPackage,
                            manifest = record.manifest,
                            ready = {
                                ReadyContext(themeTokens, sizeDp.first, sizeDp.second, Locale.getDefault().toLanguageTag(), reduceMotion)
                            },
                            events = events,
                            inFront = inFront,
                        )
                        if (created == null) {
                            missingWebView = true
                            return@AndroidView FrameLayout(ctx)
                        }
                        val (view, b) = created
                        bridge[0] = b
                        liveViews[view] = page.id
                        view.resumeTimers()
                        backActions[page.id] = { if (view.canGoBack()) { view.goBack(); true } else false }
                        val saved = webStates.remove(page.id)
                        if (saved == null || view.restoreState(saved) == null) {
                            view.loadUrl(com.bos.sphere.feature.pages.RuntimeOrigin.entryUrl(page.id, page.isPackage))
                        }
                        view
                    },
                    onRelease = { v ->
                        bridge[0]?.close()
                        bridge[0] = null
                        releaseWebView(v, page.id, save = !crashed)
                    },
                    update = { v -> (v as? WebView)?.trackCurrent(isCurrent) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun PagesUi.releaseWebView(v: View, pageId: String, save: Boolean) {
    val view = v as? WebView ?: return
    // A removed page keeps no saved state.
    if (save && store.pages.value.byId(pageId) != null) {
        val b = Bundle()
        runCatching { view.saveState(b) }.onSuccess { webStates[pageId] = b }
    } else {
        webStates.remove(pageId)
    }
    liveViews.remove(view)
    backActions.remove(pageId)
    runCatching {
        view.stopLoading()
        view.destroy()
    }
}

private fun refusedDownload(context: android.content.Context, fileName: String) {
    Toast.makeText(context, context.getString(R.string.pages_download_refused, fileName), Toast.LENGTH_LONG).show()
}

// --- a website ------------------------------------------------------------------------------

@Composable
internal fun PagesUi.WebPage(page: Page, isCurrent: Boolean) {
    var url by remember(page.id) { mutableStateOf(page.url) }
    var canGoBack by remember(page.id) { mutableStateOf(false) }
    var crashed by remember(page.id, page.url) { mutableStateOf(false) }
    var missingWebView by remember(page.id, page.url) { mutableStateOf(false) }
    var viewRef by remember { mutableStateOf<WebView?>(null) }
    val inFront = rememberInFront(isCurrent)
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        WebHeader(
            url = url,
            canGoBack = canGoBack,
            onBack = { viewRef?.goBack() },
            onReload = { if (crashed) crashed = false else viewRef?.reload() },
            onInfo = { dialog = PagesDialog.Info(page.id) },
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                missingWebView -> Message(stringResource(R.string.pages_webview_missing), null)
                crashed -> Message(
                    stringResource(R.string.pages_crashed_title),
                    stringResource(R.string.pages_crashed_body),
                    action = stringResource(R.string.pages_reload) to { crashed = false },
                )
                else -> key(page.id, page.url) {
                    AndroidView(
                        factory = { ctx ->
                            val showUrl: (String) -> Unit = { url = it }
                            val showBack: (Boolean) -> Unit = { canGoBack = it }
                            val events = object : PageViewEvents {
                                override fun onNavigated(url: String?, canGoBack: Boolean) {
                                    if (url != null && UrlRules.schemeOf(url).let { it == "http" || it == "https" }) showUrl(url)
                                    showBack(canGoBack)
                                }
                                override fun onRendererGone() { crashed = true }
                                override fun onDownloadRefused(fileName: String) = refusedDownload(ctx, fileName)
                            }
                            val view = createWebsiteView(ctx, events, inFront)
                            if (view == null) {
                                missingWebView = true
                                return@AndroidView FrameLayout(ctx)
                            }
                            viewRef = view
                            liveViews[view] = page.id
                            view.resumeTimers()
                            backActions[page.id] = { if (view.canGoBack()) { view.goBack(); true } else false }
                            val saved = webStates.remove(page.id)
                            if (saved == null || view.restoreState(saved) == null) view.loadUrl(page.url)
                            view
                        },
                        onRelease = { v ->
                            releaseWebView(v, page.id, save = !crashed)
                            viewRef = null
                        },
                        update = { v -> (v as? WebView)?.trackCurrent(isCurrent) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** Host, an https / not-https mark (shape + word), back and reload, each a ≥ 48 dp target. */
@Composable
private fun WebHeader(url: String, canGoBack: Boolean, onBack: () -> Unit, onReload: () -> Unit, onInfo: () -> Unit) {
    val secure = UrlRules.isSecure(url)
    val host = UrlRules.hostOf(url) ?: url
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderButton("‹ " + stringResource(R.string.pages_back), stringResource(R.string.pages_back_cd), enabled = canGoBack, onClick = onBack)
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clickable(onClick = onInfo)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SecurityMark(secure)
            Text(
                host,
                color = HyleColors.InkBright,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HeaderButton("↻ " + stringResource(R.string.pages_reload), stringResource(R.string.pages_reload_cd), onClick = onReload)
    }
}

/** Closed padlock + "https", or an open, struck padlock + "not https": shape and word, not colour. */
@Composable
internal fun SecurityMark(secure: Boolean) {
    val cd = stringResource(if (secure) R.string.pages_secure_cd else R.string.pages_not_secure_cd)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = cd },
    ) {
        Canvas(Modifier.size(14.dp).clearAndSetSemantics {}) {
            val s = 1.6.dp.toPx()
            val w = size.width
            val h = size.height
            drawRoundRect(
                HyleColors.Ink,
                topLeft = Offset(w * 0.12f, h * 0.45f),
                size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.5f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s, s),
                style = if (secure) androidx.compose.ui.graphics.drawscope.Fill else Stroke(s),
            )
            // The shackle: closed over the body, or swung open.
            val left = if (secure) w * 0.28f else w * 0.52f
            drawArc(
                HyleColors.Ink,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(left, h * 0.08f),
                size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.6f),
                style = Stroke(s),
            )
            if (!secure) drawLine(HyleColors.Ink, Offset(w * 0.05f, h * 0.98f), Offset(w * 0.95f, h * 0.4f), s)
        }
        Text(
            stringResource(if (secure) R.string.pages_secure else R.string.pages_not_secure),
            color = HyleColors.Ink,
            fontSize = 12.sp,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun HeaderButton(label: String, contentDescription: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) HyleColors.InkBright else HyleColors.InkDim, fontSize = 13.sp)
    }
}

// --- App Widget page whose widget is gone ------------------------------------------------------

@Composable
internal fun PagesUi.WidgetGone(page: Page, hooks: AppWidgetPageHooks?) {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Message(
            stringResource(R.string.pages_widget_gone_title),
            stringResource(R.string.pages_widget_gone_body),
            action = stringResource(R.string.pages_choose_widget) to { hooks?.add(page.widgetSlot) },
            secondary = stringResource(R.string.pages_remove_page) to { dialog = PagesDialog.Remove(page.id) },
        )
    }
}

/** An honest state in the middle of a page: a title, a line, and up to two actions. */
@Composable
internal fun Message(
    title: String,
    body: String?,
    action: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = HyleColors.InkBright, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        if (body != null) Text(body, color = HyleColors.InkDim, fontSize = 13.sp, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            action?.let { (label, run) -> PillButton(label, label, onClick = run) }
            secondary?.let { (label, run) -> PillButton(label, label, onClick = run) }
        }
    }
}

/** A bordered text button with a 48 dp minimum target. */
@Composable
internal fun PillButton(label: String, contentDescription: String, selected: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .background(if (selected) HyleColors.Violet.copy(alpha = 0.18f) else HyleColors.Surface.copy(alpha = 0.6f), shape)
                .border(1.dp, if (selected) HyleColors.Violet else HyleColors.Hairline, shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(label, color = if (selected) HyleColors.InkBright else HyleColors.Ink, fontSize = 12.sp)
        }
    }
}

// --- page dots ---------------------------------------------------------------------------------

/**
 * The page indicator: one dot per page, each a 48 dp target (tap = go to that page, long-press =
 * manage pages). The current page is a filled, larger dot; the others are rings, so the current page
 * never rests on colour. Hidden while only home exists. A row in every mode (a column on the right
 * edge would sit on the equator in landscape). When the current page is an HTML page with its network
 * switch on, a "Network on" tag sits beside the dots (the page is a watched object); tapping it opens
 * the page's info. Recomposes when the current page changes, not on every frame of a turn.
 */
@Composable
fun PagesUi.PageDots(hooks: AppWidgetPageHooks?, modifier: Modifier = Modifier) {
    val list by pages
    if (list.size < 2) return
    val current by remember { derivedStateOf { PageTurn.current(position.value, list.size) } }
    val currentPage = list.pages.getOrNull(current)
    val titles = list.pages.map { titleOf(it, hooks) }
    val dots: @Composable () -> Unit = {
        list.pages.forEachIndexed { index, _ ->
            val selected = index == current
            val cd = if (selected) {
                stringResource(R.string.pages_dot_current_cd, index + 1, list.size, titles[index])
            } else {
                stringResource(R.string.pages_dot_cd, index + 1, list.size, titles[index])
            }
            val manage = stringResource(R.string.pages_manage)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .pointerInput(index) {
                        detectTapGestures(onTap = { turnTo(index) }, onLongPress = { openManager() })
                    }
                    .semantics {
                        contentDescription = cd
                        role = Role.Button
                        onClick { turnTo(index); true }
                        onLongClick(label = manage) { openManager(); true }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(12.dp)) {
                    val r = size.minDimension / 2
                    if (selected) {
                        drawCircle(HyleColors.InkBright, radius = r)
                    } else {
                        drawCircle(HyleColors.Ink, radius = r * 0.55f, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
    }
    val shape = RoundedCornerShape(24.dp)
    val base = modifier.background(HyleColors.SurfaceTranslucent, shape).border(1.dp, HyleColors.Hairline, shape)
    val networkTag: @Composable () -> Unit = {
        if (currentPage?.kind == PageKind.Html && currentPage.network && !currentPage.isPackage) {
            val cd = stringResource(R.string.pages_network_on_cd)
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable { dialog = PagesDialog.Info(currentPage.id) }
                    .semantics {
                        contentDescription = cd
                        role = Role.Button
                    }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // An eye: the page is a watched object.
                    Canvas(Modifier.size(width = 14.dp, height = 10.dp).clearAndSetSemantics {}) {
                        val s = 1.4.dp.toPx()
                        drawOval(HyleColors.Ink, style = Stroke(s))
                        drawCircle(HyleColors.Ink, radius = size.height * 0.22f)
                    }
                    Text(stringResource(R.string.pages_network_on), color = HyleColors.Ink, fontSize = 12.sp, modifier = Modifier.clearAndSetSemantics {})
                }
            }
        }
    }
    // Many pages: the indicator scrolls rather than shrinking its 48 dp targets.
    Row(
        base.widthIn(max = 480.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dots()
        networkTag()
    }
}

/**
 * Mode 2 on home: names the page that pulling further past the coverflow's end will open.
 * [pastEndColumns] is read here only (a lambda), so a scrub recomposes this hint, not the home screen.
 */
@Composable
fun PagesUi.PastEndHint(pastEndColumns: () -> Float, hooks: AppWidgetPageHooks?, modifier: Modifier = Modifier) {
    val list by pages
    val navMode by mode
    if (navMode != NavMode.PastEnds || list.size < 2) return
    val past = pastEndColumns()
    if (current() != 0) return
    val stage = PastEnd.stage(past)
    if (stage == PastEnd.Stage.None) return
    val dir = if (past > 0f) 1 else -1
    val target = PageTurn.neighbour(0, dir, list.size)?.let { list.pages[it] } ?: return
    val name = titleOf(target, hooks)
    val text = stringResource(
        if (stage == PastEnd.Stage.Ready) R.string.pages_past_end_ready else R.string.pages_past_end_approach,
        name,
    )
    val shape = RoundedCornerShape(14.dp)
    Text(
        (if (stage == PastEnd.Stage.Ready) "› " else "") + text,
        color = HyleColors.InkBright,
        fontSize = 13.sp,
        modifier = modifier
            .background(HyleColors.SurfaceTranslucent, shape)
            .border(1.dp, if (stage == PastEnd.Stage.Ready) HyleColors.Violet else HyleColors.Hairline, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** A dot with a ring, used in lists to show which page is current (filled) or not (ring). */
@Composable
internal fun CurrentMark(current: Boolean) {
    Box(Modifier.size(10.dp).background(if (current) HyleColors.InkBright else Color.Transparent, CircleShape)
        .border(1.5.dp, HyleColors.Ink, CircleShape))
}

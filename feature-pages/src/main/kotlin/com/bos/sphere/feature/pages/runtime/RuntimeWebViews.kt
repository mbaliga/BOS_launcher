package com.bos.sphere.feature.pages.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebMessage
import android.webkit.WebMessagePort
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import com.bos.sphere.feature.pages.CookieWipe
import com.bos.sphere.feature.pages.DialogBudget
import com.bos.sphere.feature.pages.HostDecision
import com.bos.sphere.feature.pages.HostProtocol
import com.bos.sphere.feature.pages.IntentRules
import com.bos.sphere.feature.pages.LauncherChannelData
import com.bos.sphere.feature.pages.LinkRoute
import com.bos.sphere.feature.pages.OpenGate
import com.bos.sphere.feature.pages.OpenTarget
import com.bos.sphere.feature.pages.OriginWipe
import com.bos.sphere.feature.pages.PackageLayout
import com.bos.sphere.feature.pages.RateGate
import com.bos.sphere.feature.pages.ReadyContext
import com.bos.sphere.feature.pages.RuntimeOrigin
import com.bos.sphere.feature.pages.ServeDecision
import com.bos.sphere.feature.pages.ServePolicy
import com.bos.sphere.feature.pages.UrlRules
import com.bos.sphere.feature.pages.WidgetManifest
import java.io.ByteArrayInputStream
import java.time.ZoneId

/**
 * Hands links that leave a page to the system, always visibly: http(s) links from HTML pages go to
 * the user's default browser by an explicit intent (its package set), falling back to a chooser;
 * other schemes (`tel:`, `mailto:`, `intent:`, …) go through an explicit chooser. [IntentRules] decide
 * what may leave: only VIEW intents, never `file:` / `content:` data, never an over-long URL; an
 * `intent:` URL is also stripped of its component, selector, flags and clip data and limited to
 * browsable activities. Nothing a page hands over can throw inside the launcher: every start is
 * guarded (a home app must never die with its pages).
 */
object ExternalLinks {
    fun openInBrowser(context: Context, url: String) {
        if (!IntentRules.urlFits(url) || !IntentRules.allowed(IntentRules.VIEW, UrlRules.schemeOf(url))) return
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        val browser = runCatching {
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName
        }.getOrNull()?.takeUnless { it == "android" } // "android" = the resolver: no default browser
        if (browser != null) {
            val explicit = Intent(view).setPackage(browser).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(explicit) }.isSuccess) return
        }
        choose(context, view)
    }

    fun openWithChooser(context: Context, url: String) {
        if (!IntentRules.urlFits(url)) return
        val intent = if (UrlRules.schemeOf(url) == "intent") {
            val parsed = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return
            if (!IntentRules.allowed(parsed.action, parsed.data?.scheme)) return
            parsed.apply {
                component = null
                selector = null
                flags = 0 // no FLAG_GRANT_* (or any other flag) from a page
                clipData = null
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
        } else {
            if (!IntentRules.allowed(IntentRules.VIEW, UrlRules.schemeOf(url))) return
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        }
        choose(context, intent)
    }

    /** Any RuntimeException (FileUriExposedException, a failed binder call, …) is swallowed here. */
    private fun choose(context: Context, intent: Intent) {
        runCatching {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun route(context: Context, url: String, route: LinkRoute) {
        when (route) {
            LinkRoute.Browser -> openInBrowser(context, url)
            LinkRoute.System -> openWithChooser(context, url)
            LinkRoute.InPage, LinkRoute.Blocked -> Unit
        }
    }
}

/** What a page view reports back to its composable. Main thread. */
interface PageViewEvents {
    fun onNavigated(url: String?, canGoBack: Boolean) {}
    fun onRendererGone() {}
    fun onDownloadRefused(fileName: String) {}
    fun onOpen(target: OpenTarget) {}
}

/** Refusal sent to a widget whose `open` didn't follow a tap on its page while it was on screen. */
private val OPEN_NOT_NOW = HostDecision.Refuse("open", "only right after a tap on this widget's page, while it is on screen")

/**
 * The host end of one D-AC widget's MessagePort (spec §3.4). Every request is checked against the
 * widget's declarations by [HostProtocol]; launcher channels come from [LauncherChannels], the
 * others travel over the in-process [ChannelBus]. The port is posted only to the widget's own
 * origin.
 */
class HostBridge(
    private val pageId: String,
    private val manifest: WidgetManifest,
    private val runtime: PagesRuntime,
    private val ready: () -> ReadyContext,
    private val events: PageViewEvents,
    /** The page is the current one, at rest, with the launcher in front. */
    private val inFront: () -> Boolean,
) : ChannelSubscriber {
    override val widgetId: String get() = manifest.id
    private val main = Handler(Looper.getMainLooper())
    private var port: WebMessagePort? = null
    private val rate = RateGate()
    private val openGate = OpenGate()

    /** The user lifted a finger on this widget's view (an `open` may follow). */
    fun tapped() = openGate.tapped(SystemClock.uptimeMillis())

    fun connect(view: WebView) {
        close()
        val ports = runCatching { view.createWebMessageChannel() }.getOrNull() ?: return
        val host = ports[0]
        host.setWebMessageCallback(
            object : WebMessagePort.WebMessageCallback() {
                override fun onMessage(p: WebMessagePort, message: WebMessage) = handle(message.data)
            },
            main,
        )
        runCatching {
            view.postWebMessage(
                WebMessage(HostProtocol.HANDSHAKE, arrayOf(ports[1])),
                Uri.parse(RuntimeOrigin.originFor(pageId)),
            )
        }.onFailure { host.close(); return }
        port = host
        send(HostProtocol.readyMessage(ready()))
    }

    private fun handle(text: String?) {
        // Rate-limited before anything is parsed, so a flooding widget can't stall the launcher.
        when (rate.admit(SystemClock.uptimeMillis())) {
            RateGate.Admit.Ok -> Unit
            RateGate.Admit.Drop -> return
            RateGate.Admit.Close -> { close(); return }
        }
        val request = HostProtocol.parse(text) ?: return
        when (val d = HostProtocol.decide(manifest, request, System.currentTimeMillis(), ZoneId.systemDefault())) {
            is HostDecision.Subscribe ->
                if (d.channel in LauncherChannelData.ALL) runtime.channels.subscribe(d.channel, this)
                else runtime.bus.subscribe(d.channel, this)
            is HostDecision.Deliver -> runtime.bus.publish(d.envelope, from = this)
            // Declared is not enough: an open must answer a tap on this page while it is on screen,
            // so a widget on a neighbour page (or on a timer) can't pull the user away.
            is HostDecision.Open ->
                if (inFront() && openGate.take(SystemClock.uptimeMillis())) events.onOpen(d.target)
                else send(HostProtocol.refusedMessage(OPEN_NOT_NOW))
            is HostDecision.Refuse -> send(HostProtocol.refusedMessage(d))
        }
    }

    override fun deliver(envelope: Map<String, Any?>) {
        send(HostProtocol.messageFor(envelope))
    }

    private fun send(text: String) {
        runCatching { port?.postMessage(WebMessage(text)) }
    }

    fun close() {
        runtime.bus.unsubscribe(this)
        runtime.channels.unsubscribe(this)
        runCatching { port?.close() }
        port = null
    }
}

/** Settings shared by every WebView the launcher creates (pages, widgets, websites). */
@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.applyCommonSettings(domStorage: Boolean) {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = domStorage
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        setGeolocationEnabled(false)
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        setSupportMultipleWindows(true) // so target=_blank / window.open reach onCreateWindow (and leave)
        javaScriptCanOpenWindowsAutomatically = false
        mediaPlaybackRequiresUserGesture = true
        // Owner decision 2026-11-04: Safe Browsing off, so no visited URL is checked with Google.
        safeBrowsingEnabled = false
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
}

/**
 * Chrome client for every launcher WebView: no permissions, no popups inside, no console logging, and
 * JavaScript dialogs only for the page on screen ([inFront]), at most a few at a time ([DialogBudget]).
 * [allowPopups] false (D-AC packages) refuses every new window outright.
 */
internal open class SandboxChromeClient(
    private val context: Context,
    private val allowPopups: Boolean,
    private val inFront: () -> Boolean,
    private val routeFor: (url: String) -> LinkRoute,
) : WebChromeClient() {
    private val dialogs = DialogBudget()

    override fun onPermissionRequest(request: PermissionRequest) = request.deny()

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) {
        callback.invoke(origin, false, false)
    }

    /** Page console output is not logged anywhere (it can carry the page's data). */
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true

    /** The WebView's own dialog (return false) only for the page on screen and within the budget. */
    private fun dialogAllowed(): Boolean = inFront() && dialogs.take(SystemClock.uptimeMillis())

    override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        if (dialogAllowed()) return false
        result.cancel()
        return true
    }

    override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        if (dialogAllowed()) return false
        result.cancel()
        return true
    }

    override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
        if (dialogAllowed()) return false
        result.cancel()
        return true
    }

    /** Without a dialog, leaving is allowed: the user asked for it (back, a link). */
    override fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        if (dialogAllowed()) return false
        result.confirm()
        return true
    }

    /**
     * A new window (`target=_blank`, `window.open`) never opens inside the launcher. With a user
     * gesture, its address is caught by a throwaway WebView that loads nothing (JavaScript off,
     * network blocked, every request refused) and handed to the browser or the chooser; without one,
     * refused. The catcher is destroyed as soon as it has the address, and after [CATCHER_MILLIS]
     * whatever happens, so a window that never navigates (`window.open('')`) isn't kept; it also
     * survives the shared renderer's death (returning true there, so the launcher does too).
     */
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        if (!allowPopups || !isUserGesture) return false
        val catcher = runCatching { WebView(view.context) }.getOrNull() ?: return false
        // The catcher is never attached to a window, so View.post would never run: use the looper.
        val main = Handler(Looper.getMainLooper())
        var done = false
        fun finish() {
            if (done) return
            done = true
            main.post { runCatching { catcher.stopLoading(); catcher.destroy() } }
        }
        fun take(url: String?) {
            if (done || url.isNullOrBlank() || url == "about:blank") return
            ExternalLinks.route(context, url, routeFor(url))
            finish()
        }
        catcher.applyCommonSettings(domStorage = false)
        catcher.settings.javaScriptEnabled = false
        catcher.settings.blockNetworkLoads = true
        catcher.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                take(request.url.toString())
                return true
            }

            override fun onPageStarted(v: WebView, url: String?, favicon: Bitmap?) {
                take(url)
                v.stopLoading()
            }

            override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest) = denied()

            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                finish()
                return true
            }
        }
        main.postDelayed({ finish() }, CATCHER_MILLIS)
        (resultMsg.obj as? WebView.WebViewTransport)?.webView = catcher
        resultMsg.sendToTarget()
        return true
    }

    private companion object {
        const val CATCHER_MILLIS = 3_000L
    }
}

internal fun denied(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", 403, "Blocked", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))

private fun notFound(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", 404, "Not Found", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))

/**
 * Creates the WebView for an HTML page or widget package, served from app-private storage at its own
 * origin through [WebViewClient.shouldInterceptRequest] (never `file://`). Returns null if the
 * system WebView can't be created (being updated, missing): the page shows that honestly.
 */
@SuppressLint("ClickableViewAccessibility")
fun createHtmlView(
    context: Context,
    runtime: PagesRuntime,
    pageId: String,
    isPackage: Boolean,
    networkOn: Boolean,
    manifest: WidgetManifest?,
    ready: () -> ReadyContext,
    events: PageViewEvents,
    inFront: () -> Boolean,
): Pair<WebView, HostBridge?>? {
    val view = runCatching { WebView(context) }.getOrNull() ?: return null
    // D-AC packages get no storage (spec §3.3); pages keep localStorage, so Orrery keeps its state.
    view.applyCommonSettings(domStorage = !isPackage)
    view.setBackgroundColor(0xFF0A0B10.toInt())
    val origin = RuntimeOrigin.originFor(pageId)
    val bridge = if (isPackage && manifest != null) HostBridge(pageId, manifest, runtime, ready, events, inFront) else null
    // Taps on the widget are what an orrery.open may answer (the listener only watches; the
    // WebView still gets every event).
    if (bridge != null) {
        view.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP) bridge.tapped()
            false
        }
    }
    view.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            return when (val d = ServePolicy.decide(url, pageId, isPackage, networkOn)) {
                is ServeDecision.Serve -> serve(runtime, pageId, d.path, isPackage, networkOn)
                ServeDecision.NotFound -> notFound()
                ServeDecision.Inline -> null
                is ServeDecision.Network -> { runtime.recordReached(pageId, d.host); null }
                is ServeDecision.Deny -> { runtime.recordBlocked(pageId, d.host); denied() }
            }
        }

        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false // sub-frames: the interceptor decides
            val url = request.url.toString()
            val route = UrlRules.runtimeRoute(url, origin, request.hasGesture(), isPackage)
            if (route == LinkRoute.InPage) return false
            ExternalLinks.route(context, url, route)
            return true
        }

        override fun onPageFinished(v: WebView, url: String?) {
            if (url != null && UrlRules.originOf(url) == origin) bridge?.connect(v)
            events.onNavigated(url, v.canGoBack())
        }

        override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
            bridge?.close()
            events.onRendererGone()
            return true // a home app must never die with its pages
        }
    }
    // A package opens no windows at all (D-AC §3.3: sandbox="allow-scripts" only).
    view.webChromeClient = SandboxChromeClient(context, allowPopups = !isPackage, inFront = inFront) { url ->
        UrlRules.runtimeRoute(url, origin, hasGesture = true, isPackage = isPackage)
    }
    view.setDownloadListener { url, _, disposition, mime, _ ->
        events.onDownloadRefused(URLUtil.guessFileName(url, disposition, mime))
    }
    return view to bridge
}

private fun serve(runtime: PagesRuntime, pageId: String, path: String, isPackage: Boolean, networkOn: Boolean): WebResourceResponse {
    val headers = HashMap<String, String>()
    headers["Cache-Control"] = "no-store"
    val isDocument = path == (if (isPackage) PackageLayout.HTML else ServePolicy.PAGE_FILE)
    ServePolicy.responseCsp(isPackage, networkOn, isDocument)?.let {
        headers["Content-Security-Policy"] = it
        headers["X-DNS-Prefetch-Control"] = "off"
    }
    if (isDocument) {
        val bytes = runtime.files.document(pageId, isPackage, networkOn) ?: return notFound()
        return WebResourceResponse("text/html", "utf-8", 200, "OK", headers, ByteArrayInputStream(bytes))
    }
    val file = runtime.files.file(pageId, path) ?: return notFound()
    val stream = runCatching { file.inputStream() }.getOrNull() ?: return notFound()
    return WebResourceResponse(ServePolicy.mimeFor(path), null, 200, "OK", headers, stream)
}

/**
 * Creates the WebView for a website page: JavaScript and DOM storage on, third-party cookies off,
 * mixed content never, file and content access off, every permission denied, Safe Browsing off
 * (owner decision), new windows and non-http(s) schemes handed to the system, downloads refused.
 */
fun createWebsiteView(context: Context, events: PageViewEvents, inFront: () -> Boolean): WebView? {
    val view = runCatching { WebView(context) }.getOrNull() ?: return null
    view.applyCommonSettings(domStorage = true)
    view.settings.apply {
        useWideViewPort = true
        loadWithOverviewMode = true
        builtInZoomControls = true
        displayZoomControls = false
    }
    view.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            val route = UrlRules.websiteRoute(url, request.hasGesture())
            if (route == LinkRoute.InPage) return false
            ExternalLinks.route(context, url, route)
            return true
        }

        override fun doUpdateVisitedHistory(v: WebView, url: String?, isReload: Boolean) {
            events.onNavigated(url, v.canGoBack())
        }

        override fun onPageFinished(v: WebView, url: String?) {
            events.onNavigated(url, v.canGoBack())
        }

        override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
            events.onRendererGone()
            return true
        }
    }
    // A website's new window (target=_blank, window.open after a tap) goes to the system's chooser.
    view.webChromeClient = SandboxChromeClient(context, allowPopups = true, inFront = inFront) { url ->
        UrlRules.websiteRoute(url, hasGesture = true).let { if (it == LinkRoute.InPage) LinkRoute.System else it }
    }
    view.setDownloadListener { url, _, disposition, mime, _ ->
        events.onDownloadRefused(URLUtil.guessFileName(url, disposition, mime))
    }
    return view
}

/**
 * Service workers run outside any one WebView's client. Pages and widgets served from the runtime's
 * origins never get one (their script requests are refused here), so nothing can fetch on their
 * behalf behind the interceptor; websites' own service workers work as in a browser.
 */
fun installServiceWorkerGuard() {
    runCatching {
        android.webkit.ServiceWorkerController.getInstance().apply {
            serviceWorkerWebSettings.allowContentAccess = false
            serviceWorkerWebSettings.allowFileAccess = false
            setServiceWorkerClient(object : android.webkit.ServiceWorkerClient() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                    if (RuntimeOrigin.isRuntimeHost(request.url.host)) denied() else null
            })
        }
    }
}

/**
 * Clears what a removed HTML page stored at its origin ([OriginWipe]): a throwaway WebView (DOM
 * storage on, served only the wipe document by the interceptor, every other request refused) runs
 * the wipe script, then `WebStorage.deleteOrigin` and the cookie expiries run and the view is
 * destroyed. Call it after the page's own view is gone. Gives up after [WIPE_MILLIS] (the cookie
 * and `deleteOrigin` steps still run); if no WebView can be created, only those steps run. Main thread.
 */
@SuppressLint("SetJavaScriptEnabled")
fun wipeOrigin(context: Context, pageId: String) {
    val origin = RuntimeOrigin.originFor(pageId)
    val main = Handler(Looper.getMainLooper())
    var done = false
    var view: WebView? = null
    fun finish() {
        if (done) return
        done = true
        runCatching { WebStorage.getInstance().deleteOrigin(origin) }
        runCatching {
            val cm = CookieManager.getInstance()
            val host = RuntimeOrigin.hostFor(pageId)
            val names = listOfNotNull(cm.getCookie("$origin/"), cm.getCookie("$origin/assets/")).joinToString(";")
            CookieWipe.expiries(names, host).forEach { cm.setCookie("$origin/", it) }
            cm.flush()
        }
        view?.let { v -> main.post { runCatching { v.stopLoading(); v.destroy() } } }
        view = null
    }
    val wiper = runCatching { WebView(context.applicationContext) }.getOrNull()
    if (wiper == null) {
        finish()
        return
    }
    view = wiper
    wiper.applyCommonSettings(domStorage = true)
    wiper.webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            if (OriginWipe.isDone(url, pageId)) main.post { finish() }
            return if (OriginWipe.isWipeRequest(url, pageId)) {
                WebResourceResponse(
                    "text/html", "utf-8", 200, "OK",
                    mapOf("Cache-Control" to "no-store"),
                    ByteArrayInputStream(OriginWipe.DOCUMENT.toByteArray(Charsets.UTF_8)),
                )
            } else {
                denied()
            }
        }

        override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
            if (OriginWipe.isDone(request.url.toString(), pageId)) main.post { finish() }
            return true
        }

        override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
            main.post { finish() }
            return true
        }
    }
    wiper.webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
    }
    main.postDelayed({ finish() }, WIPE_MILLIS)
    wiper.loadUrl(origin + OriginWipe.PATH)
}

private const val WIPE_MILLIS = 10_000L

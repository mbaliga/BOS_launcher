package com.bos.sphere.feature.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

private val IST = ZoneId.of("Asia/Kolkata")

class HostProtocolTest {

    private val manifest = WidgetManifest(
        id = "nooz.headlines",
        name = "Headlines",
        version = 1,
        sizes = listOf("fill"),
        reads = listOf("news.items", "launcher.clock"),
        writes = listOf("news.read"),
        opens = listOf("app:{pkg}", "page:{id}"),
    )

    private fun decide(json: String) = HostProtocol.decide(manifest, HostProtocol.parse(json)!!, 0L, IST)

    @Test
    fun parsing() {
        assertEquals(HostRequest.On("news.items"), HostProtocol.parse("""{"op":"on","channel":"news.items"}"""))
        assertEquals(HostRequest.Open("app:x.y"), HostProtocol.parse("""{"op":"open","route":"app:x.y"}"""))
        assertEquals(
            HostRequest.Publish("news.read", mapOf("id" to "1"), HostProtocol.DEFAULT_TTL),
            HostProtocol.parse("""{"op":"publish","channel":"news.read","data":{"id":"1"}}"""),
        )
        assertEquals(HostProtocol.MAX_TTL, (HostProtocol.parse("""{"op":"publish","channel":"a.b","ttl":99999999}""") as HostRequest.Publish).ttl)
        assertNull(HostProtocol.parse("garbage"))
        assertNull(HostProtocol.parse("""{"op":"eval","code":"x"}"""))
        assertNull(HostProtocol.parse("""{"op":"on"}"""))
        assertNull(HostProtocol.parse(null))
        assertNull(HostProtocol.parse("\"" + "a".repeat(HostProtocol.MAX_MESSAGE_CHARS) + "\""))
    }

    @Test
    fun onlyDeclaredReadsCanBeListenedTo() {
        assertEquals(HostDecision.Subscribe("news.items"), decide("""{"op":"on","channel":"news.items"}"""))
        assertEquals(HostDecision.Subscribe("launcher.clock"), decide("""{"op":"on","channel":"launcher.clock"}"""))
        assertTrue(decide("""{"op":"on","channel":"launcher.notifications"}""") is HostDecision.Refuse)
        assertTrue(decide("""{"op":"on","channel":"eeg.bands"}""") is HostDecision.Refuse)
    }

    @Test
    fun onlyDeclaredWritesCanBePublishedAndNeverLauncherChannels() {
        val d = decide("""{"op":"publish","channel":"news.read","data":{"id":"7"},"ttl":5}""") as HostDecision.Deliver
        assertEquals(
            linkedMapOf("channel" to "news.read", "at" to "1970-01-01T05:30:00+05:30", "from" to "nooz.headlines", "ttl" to 5, "data" to mapOf("id" to "7")),
            d.envelope,
        )
        assertEquals(listOf("channel", "at", "from", "ttl", "data"), d.envelope.keys.toList())
        assertEquals("publish", (decide("""{"op":"publish","channel":"tasks.changed","data":1}""") as HostDecision.Refuse).op)
        val forged = HostProtocol.decide(manifest.copy(writes = listOf("launcher.clock")), HostRequest.Publish("launcher.clock", 1, 60), 0L, IST)
        assertTrue(forged is HostDecision.Refuse)
    }

    @Test
    fun oversizedPublicationsAreRefused() {
        val big = "x".repeat(HostProtocol.MAX_MESSAGE_CHARS)
        assertTrue(HostProtocol.decide(manifest, HostRequest.Publish("news.read", big, 60), 0L, IST) is HostDecision.Refuse)
    }

    @Test
    fun opensOnlyDeclaredAppAndPageRoutes() {
        assertEquals(HostDecision.Open(OpenTarget.App("com.example.nooz")), decide("""{"op":"open","route":"app:com.example.nooz"}"""))
        assertEquals(HostDecision.Open(OpenTarget.Page("home")), decide("""{"op":"open","route":"page:home"}"""))
        assertEquals(HostDecision.Open(OpenTarget.Page("p0123456789abcdef")), decide("""{"op":"open","route":"page:p0123456789abcdef"}"""))
        for (route in listOf("https://evil.example", "nooz://read/1", "app:not a package", "page:nope", "app:")) {
            assertTrue(route, decide("""{"op":"open","route":"$route"}""") is HostDecision.Refuse)
        }
        val none = manifest.copy(opens = emptyList())
        assertTrue(HostProtocol.decide(none, HostRequest.Open("app:com.example.nooz"), 0L, IST) is HostDecision.Refuse)
        val narrow = manifest.copy(opens = listOf("app:com.example.nooz"))
        assertTrue(HostProtocol.decide(narrow, HostRequest.Open("app:com.example.other"), 0L, IST) is HostDecision.Refuse)
    }

    @Test
    fun routeTemplates() {
        assertTrue(OpenRoutes.matches("app:{pkg}", "app:com.x"))
        assertTrue(OpenRoutes.matches("app:com.a.{x}", "app:com.a.b"))
        assertTrue(OpenRoutes.matches("page:home", "page:home"))
        assertFalse(OpenRoutes.matches("page:home", "page:homex"))
        assertFalse(OpenRoutes.matches("app:{pkg}", "app:a/b"))
        assertFalse(OpenRoutes.matches("app:com.a", "app:comXa")) // literal dots stay literal
        assertNull(OpenRoutes.parse("tel:123"))
        assertTrue(OpenRoutes.isRouteTemplate("page:{id}"))
        assertFalse(OpenRoutes.isRouteTemplate("nooz://read/{id}"))
    }

    @Test
    fun messagesToTheWidget() {
        val ready = MiniJson.parse(
            HostProtocol.readyMessage(ReadyContext(mapOf("--ink" to "#c9cdd6"), 411, 780, "en-IN", reduceMotion = true)),
        ).asObject()!!
        assertEquals("ready", ready["op"])
        val ctx = ready["ctx"].asObject()!!
        assertEquals(mapOf("--ink" to "#c9cdd6"), ctx["theme"])
        assertEquals(mapOf("name" to "fill", "width" to 411L, "height" to 780L), ctx["size"])
        assertEquals("en-IN", ctx["locale"])
        assertEquals(true, ctx["reduceMotion"])
        val env = Envelope.build("launcher.clock", 0L, IST, "launcher", 60, mapOf("hour" to 5))
        assertEquals("message", MiniJson.parse(HostProtocol.messageFor(env)).asObject()!!["op"])
        val refused = MiniJson.parse(HostProtocol.refusedMessage(HostDecision.Refuse("open", "route not allowed"))).asObject()!!
        assertEquals(mapOf("op" to "refused", "what" to "open", "detail" to "route not allowed"), refused)
    }

    @Test
    fun envelopeTimeCarriesTheOffset() {
        assertEquals("1970-01-01T05:30:00+05:30", Envelope.at(0L, IST))
        assertEquals("1970-01-01T00:00:01Z", Envelope.at(1_999L, ZoneId.of("UTC")))
    }

    @Test
    fun theShimSpeaksTheProtocol() {
        val js = RuntimeScripts.HOST_API
        for (part in listOf(HostProtocol.HANDSHAKE, "op:'on'", "op:'publish'", "op:'open'", "'ready'", "'message'", "orrery-refused", "Object.freeze")) {
            assertTrue(part, js.contains(part))
        }
    }
}

class ServePolicyTest {
    private val id = "p0123456789abcdef"
    private val own = RuntimeOrigin.originFor(id)

    @Test
    fun originsAreOwnAndUnresolvable() {
        // Each page is its own site under the TLD .invalid: no shared parent domain for cookies.
        assertEquals("https://p0123456789abcdef.invalid", own)
        assertEquals("$own/widget.html", RuntimeOrigin.entryUrl(id, isPackage = true))
        assertEquals("$own/", RuntimeOrigin.entryUrl(id, isPackage = false))
        assertTrue(RuntimeOrigin.isRuntimeHost("p1111111111111111.invalid"))
        assertTrue(RuntimeOrigin.isRuntimeHost("P1111111111111111.INVALID"))
        assertFalse(RuntimeOrigin.isRuntimeHost("invalid"))
        assertFalse(RuntimeOrigin.isRuntimeHost("x.p1111111111111111.invalid"))
        assertFalse(RuntimeOrigin.isRuntimeHost("p1111111111111111.invalid.example.com"))
        assertFalse(RuntimeOrigin.isRuntimeHost("example.invalid"))
        assertFalse(RuntimeOrigin.isRuntimeHost(null))
    }

    @Test
    fun aPackageServesItsHtmlAndAssetsOnly() {
        assertEquals(ServeDecision.Serve("widget.html"), ServePolicy.decide("$own/widget.html", id, true, false))
        assertEquals(ServeDecision.Serve("assets/img/a.png"), ServePolicy.decide("$own/assets/img/a.png?v=2", id, true, false))
        assertEquals(ServeDecision.NotFound, ServePolicy.decide("$own/widget.json", id, true, false))
        assertEquals(ServeDecision.NotFound, ServePolicy.decide("$own/", id, true, false))
        assertEquals(ServeDecision.NotFound, ServePolicy.decide("$own/assets/../widget.json", id, true, false))
        assertEquals(ServeDecision.NotFound, ServePolicy.decide("$own/assets/%2e%2e/x", id, true, false))
    }

    @Test
    fun aPageServesItsOneFile() {
        assertEquals(ServeDecision.Serve("index.html"), ServePolicy.decide("$own/", id, false, false))
        assertEquals(ServeDecision.Serve("index.html"), ServePolicy.decide("$own/index.html#dial", id, false, false))
        assertEquals(ServeDecision.NotFound, ServePolicy.decide("$own/app.js", id, false, false))
    }

    @Test
    fun aPackageNeverReachesTheNetworkWhateverItDeclares() {
        assertEquals(ServeDecision.Deny("fonts.googleapis.com"), ServePolicy.decide("https://fonts.googleapis.com/css2", id, true, true))
        assertEquals(ServeDecision.Deny("example.com"), ServePolicy.decide("http://example.com/x", id, true, false))
    }

    @Test
    fun aPageReachesTheNetworkOnlyWithItsSwitchOn() {
        assertEquals(ServeDecision.Deny("fonts.googleapis.com"), ServePolicy.decide("https://fonts.googleapis.com/css2", id, false, false))
        assertEquals(ServeDecision.Network("fonts.googleapis.com"), ServePolicy.decide("https://fonts.googleapis.com/css2", id, false, true))
        assertEquals(ServeDecision.Deny("example.com"), ServePolicy.decide("ftp://example.com/x", id, false, true))
    }

    @Test
    fun otherOriginsAndOddRequests() {
        // Another page's origin: never, even with the network on (pages can't read each other).
        assertEquals(ServeDecision.Deny("p1111111111111111.invalid"), ServePolicy.decide("https://p1111111111111111.invalid/", id, false, true))
        // Our host over http is not our origin.
        assertTrue(ServePolicy.decide("http://$id.invalid/", id, false, true) is ServeDecision.Deny)
        assertEquals(ServeDecision.Inline, ServePolicy.decide("data:image/png;base64,AAAA", id, true, false))
        assertTrue(ServePolicy.decide("not a url", id, false, true) is ServeDecision.Deny)
    }

    @Test
    fun contentSecurityPolicies() {
        assertEquals(ServePolicy.PACKAGE_CSP, ServePolicy.csp(isPackage = true, networkOn = true))
        assertEquals(ServePolicy.OFFLINE_PAGE_CSP, ServePolicy.csp(isPackage = false, networkOn = false))
        assertNull(ServePolicy.csp(isPackage = false, networkOn = true))
        // D-AC §3.3: nothing by default; scripts and styles inline only.
        assertTrue(ServePolicy.PACKAGE_CSP.startsWith("default-src 'none';"))
        assertTrue("script-src 'unsafe-inline';" in ServePolicy.PACKAGE_CSP)
        assertFalse("connect-src" in ServePolicy.PACKAGE_CSP) // falls back to 'none'
        assertTrue("connect-src 'self' data: blob:;" in ServePolicy.OFFLINE_PAGE_CSP)
    }

    @Test
    fun mimeTypes() {
        assertEquals("font/woff2", ServePolicy.mimeFor("assets/a.WOFF2"))
        assertEquals("image/svg+xml", ServePolicy.mimeFor("assets/i.svg"))
        assertEquals("application/octet-stream", ServePolicy.mimeFor("assets/noext"))
    }

    @Test
    fun aPackagesAssetsAreSandboxedDocuments() {
        // widget.html gets the package CSP; an asset (if it is a document someone navigates to or
        // frames) gets no script and an opaque origin, so it can't run without the runtime's head block.
        assertEquals(ServePolicy.PACKAGE_CSP, ServePolicy.responseCsp(isPackage = true, networkOn = false, isDocument = true))
        val asset = ServePolicy.responseCsp(isPackage = true, networkOn = false, isDocument = false)!!
        assertTrue(asset.endsWith("; sandbox"))
        assertFalse("script-src" in asset)
        assertTrue(asset.startsWith("default-src 'none';"))
        assertEquals(ServePolicy.OFFLINE_PAGE_CSP, ServePolicy.responseCsp(isPackage = false, networkOn = false, isDocument = true))
        assertNull(ServePolicy.responseCsp(isPackage = false, networkOn = true, isDocument = true))
    }

    @Test
    fun cookieWipe() {
        val host = "$id.invalid"
        val out = CookieWipe.expiries("a=1; b=2; a=3", host)
        assertEquals(8, out.size) // 2 names × 2 paths × host-only / domain
        assertTrue("a=; Max-Age=0; Path=/" in out)
        assertTrue("b=; Max-Age=0; Path=/assets; Domain=$host" in out)
        assertTrue(CookieWipe.expiries(null, host).isEmpty())
    }

    @Test
    fun theWipeDocumentClearsEveryStoreThenSignals() {
        val doc = OriginWipe.DOCUMENT
        for (part in listOf("localStorage.clear()", "sessionStorage.clear()", "indexedDB.deleteDatabase", "caches.delete", "Max-Age=0")) {
            assertTrue(part, doc.contains(part))
        }
        assertTrue(doc.contains("location.replace('${OriginWipe.DONE_PATH}')"))
        assertTrue(OriginWipe.isWipeRequest("$own${OriginWipe.PATH}", id))
        assertFalse(OriginWipe.isWipeRequest("https://p1111111111111111.invalid${OriginWipe.PATH}", id))
        assertTrue(OriginWipe.isDone("$own${OriginWipe.DONE_PATH}", id))
        assertFalse(OriginWipe.isDone("$own/", id))
        // Neither path is anything a page or package serves.
        assertEquals(ServeDecision.NotFound, ServePolicy.decide("$own${OriginWipe.PATH}", id, false, false))
        assertEquals(ServeDecision.NotFound, ServePolicy.decide("$own${OriginWipe.PATH}", id, true, false))
    }
}

class HtmlPrepTest {

    @Test
    fun aFragmentWithItsNetworkOnIsOnlyWrapped() {
        val html = "<title>Orrery</title><p>hi</p>"
        assertEquals(HtmlPrep.SKELETON_HEAD + html + HtmlPrep.SKELETON_TAIL, HtmlPrep.prepare(html, isPackage = false, networkOn = true))
    }

    @Test
    fun aFragmentWithItsNetworkOffGetsTheOfflinePolicyFirstInItsHead() {
        val out = HtmlPrep.prepare("<title>Orrery</title>", isPackage = false, networkOn = false)
        // Right after the skeleton's doctype, so it opens the (implicit) head before anything else.
        val head = "<!doctype html><meta http-equiv=\"Content-Security-Policy\" content=\"${ServePolicy.OFFLINE_PAGE_CSP}\">"
        assertTrue(out.startsWith(head))
        assertTrue(out.contains(RuntimeScripts.NO_RTC))
        assertFalse(out.contains("window.orrery"))
        assertTrue(out.indexOf(RuntimeScripts.NO_RTC) < out.indexOf("<title>Orrery</title>"))
    }

    @Test
    fun aPackageGetsTheHostApiAndIsNeverWrapped() {
        val html = "<!doctype html><html><head><title>W</title></head><body></body></html>"
        val out = HtmlPrep.prepare(html, isPackage = true, networkOn = true)
        assertTrue(out.startsWith("<!doctype html><meta http-equiv=\"Content-Security-Policy\" content=\"${ServePolicy.PACKAGE_CSP}\">"))
        assertTrue(out.contains(RuntimeScripts.HOST_API))
        assertTrue(out.contains(RuntimeScripts.NO_RTC))
        assertTrue(out.contains(RuntimeScripts.NO_STORAGE))
        assertTrue(out.contains("x-dns-prefetch-control"))
        assertTrue(out.indexOf(RuntimeScripts.HOST_API) < out.indexOf("<html>"))
        assertFalse(out.contains("background:#faf9f5")) // no skeleton
    }

    @Test
    fun singleFilePagesKeepTheirStorage() {
        assertFalse(HtmlPrep.prepare("<!doctype html><title>O</title>", isPackage = false, networkOn = false).contains(RuntimeScripts.NO_STORAGE))
        for (name in listOf("'indexedDB'", "'caches'", "Document.prototype,'cookie'")) assertTrue(name, RuntimeScripts.NO_STORAGE.contains(name))
    }

    @Test
    fun injectionPoints() {
        assertEquals("<!DOCTYPE html>B<p>x", HtmlPrep.injectAtTop("<!DOCTYPE html><p>x", "B"))
        assertEquals("B<p>x", HtmlPrep.injectAtTop("<p>x", "B"))
        // Before the document's own <html> and <head>: the parser opens an implicit head for the block.
        assertEquals("B<html lang=en><body>", HtmlPrep.injectAtTop("<html lang=en><body>", "B"))
        assertEquals("B<HEAD ><title>", HtmlPrep.injectAtTop("<HEAD ><title>", "B"))
        assertEquals("B<header>x</header>", HtmlPrep.injectAtTop("<header>x</header>", "B"))
        // A BOM, whitespace and leading comments stay first; so does the doctype.
        assertEquals("\uFEFF <!-- c -->\n<!doctype html>B<html>", HtmlPrep.injectAtTop("\uFEFF <!-- c -->\n<!doctype html><html>", "B"))
        // An unterminated comment: the block goes before it.
        assertEquals("B<!-- <head>", HtmlPrep.injectAtTop("<!-- <head>", "B"))
    }

    @Test
    fun aHeadInsideACommentOrAfterAScriptCantRunCodeBeforeTheBlock() {
        // The first "<head>" is inside a comment: a search for it would have put the block in the comment.
        val commented = HtmlPrep.prepare("<!--<head>--><html><head><script>evil()</script>", isPackage = true, networkOn = false)
        assertTrue(commented.indexOf(RuntimeScripts.NO_RTC) < commented.indexOf("evil()"))
        assertTrue(commented.startsWith("<!--<head>--><meta http-equiv"))
        // The page's own script comes before its <head>: it would have run before the block.
        val early = HtmlPrep.prepare("<html><script>evil()</script><head>", isPackage = true, networkOn = false)
        assertTrue(early.indexOf(RuntimeScripts.NO_RTC) < early.indexOf("evil()"))
        // A network-off page as well.
        val page = HtmlPrep.prepare("<!doctype html><script>evil()</script><head>", isPackage = false, networkOn = false)
        assertTrue(page.indexOf(RuntimeScripts.NO_RTC) < page.indexOf("evil()"))
    }

    @Test
    fun hugeLeadingWhitespaceIsScannedWithoutRegex() {
        val html = " ".repeat(200_000) + "<p>x"
        assertTrue(HtmlPrep.isFragment(html))
        assertEquals(" ".repeat(200_000) + "B<p>x", HtmlPrep.injectAtTop(html, "B"))
    }
}

class LauncherChannelDataTest {

    @Test
    fun clock() {
        assertEquals(
            linkedMapOf("iso" to "1970-01-01T05:30:00+05:30", "hour" to 5, "minute" to 30, "zone" to "Asia/Kolkata", "is24Hour" to true),
            LauncherChannelData.clock(0L, IST, is24Hour = true),
        )
        assertEquals(45_000L, LauncherChannelData.millisToNextMinute(5 * 60_000L + 15_000L))
        assertEquals(60_000L, LauncherChannelData.millisToNextMinute(5 * 60_000L))
    }

    @Test
    fun battery() {
        assertEquals(mapOf("level" to 100, "charging" to true), LauncherChannelData.battery(150, true))
        assertEquals(mapOf("level" to 0, "charging" to false), LauncherChannelData.battery(-5, false))
    }

    @Test
    fun notificationCountsCarryAppNamesAndCountsOnly() {
        val facts = listOf(
            NotificationFact("com.b", "Beta", isGroupSummary = false, isOngoing = false),
            NotificationFact("com.a", "Alpha", false, false),
            NotificationFact("com.a", "Alpha", false, false),
            NotificationFact("com.a", "Alpha", isGroupSummary = true, isOngoing = false), // its children are counted
            NotificationFact("com.music", "Music", isGroupSummary = false, isOngoing = true), // status, not waiting
            NotificationFact("com.z", "", false, false),
        )
        val data = LauncherChannelData.notifications(facts, access = true)
        assertEquals(true, data["access"])
        assertEquals(4, data["total"])
        assertEquals(
            listOf(
                mapOf("package" to "com.a", "app" to "Alpha", "count" to 2),
                mapOf("package" to "com.b", "app" to "Beta", "count" to 1),
                mapOf("package" to "com.z", "app" to "com.z", "count" to 1),
            ),
            data["apps"],
        )
        assertEquals(setOf("access", "total", "apps"), data.keys)
    }

    @Test
    fun withoutAccessThereIsNothingToCount() {
        assertEquals(mapOf("access" to false, "total" to 0, "apps" to emptyList<Any>()), LauncherChannelData.notifications(emptyList(), access = false))
    }

    @Test
    fun themeTokens() {
        assertEquals("#8e7bff", ThemeTokens.css(0xFF8E7BFFL))
        assertEquals("rgba(255,255,255,0.102)", ThemeTokens.css(0x1AFFFFFFL))
        val t = ThemeTokens.build(mapOf("--ink" to 0xFFC9CDD6L))
        assertEquals("#c9cdd6", t["--ink"])
        assertTrue(t.containsKey("--font-sans") && t.containsKey("--font-mono"))
    }
}

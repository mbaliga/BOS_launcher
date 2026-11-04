package com.bos.sphere.feature.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRulesTest {

    private fun ok(input: String) = UrlRules.check(input) as UrlCheck.Ok
    private fun problem(input: String) = (UrlRules.check(input) as UrlCheck.Rejected).problem

    @Test
    fun aBareHostGetsHttps() {
        val r = ok("example.com")
        assertEquals("https://example.com/", r.url)
        assertEquals("example.com", r.host)
        assertTrue(r.secure)
        assertTrue(r.schemeAdded)
        assertEquals("https://example.com/news", ok("//example.com/news").url)
    }

    @Test
    fun schemeAndHostAreNormalisedPathIsKept() {
        val r = ok("HTTPS://Example.COM/Path?q=1#x")
        assertEquals("https://example.com/Path?q=1#x", r.url)
        assertFalse(r.schemeAdded)
    }

    @Test
    fun httpIsAcceptedButMarkedNotSecure() {
        val r = ok("http://example.com")
        assertFalse(r.secure)
        assertFalse(UrlRules.isSecure(r.url))
        assertTrue(UrlRules.isSecure(ok("example.com").url))
    }

    @Test
    fun onlyHttpAndHttpsCanBePages() {
        for (bad in listOf(
            "javascript:alert(1)", "file:///sdcard/x.html", "intent://scan#Intent;end", "data:text/html,hi",
            "ftp://x.org", "content://media/1", "mailto:a@b.example", "about:blank", "chrome://flags",
        )) {
            assertEquals(bad, UrlProblem.Scheme, problem(bad))
        }
    }

    @Test
    fun credentialsInTheAddressAreRefused() {
        assertEquals(UrlProblem.Credentials, problem("https://user:pass@example.com/"))
        assertEquals(UrlProblem.Credentials, problem("bank.example@evil.example"))
    }

    @Test
    fun hostsAndPorts() {
        assertEquals("https://localhost:8080/", ok("localhost:8080").url)
        assertEquals("https://example.com:8443/path", ok("example.com:8443/path").url)
        assertEquals("https://192.168.1.1/", ok("https://192.168.1.1").url)
        assertEquals("https://[::1]:8443/", ok("https://[::1]:8443/").url)
        assertEquals("xn--bcher-kva.example", ok("https://bücher.example").host)
        assertEquals(UrlProblem.BadPort, problem("https://example.com:0/"))
        assertEquals(UrlProblem.BadPort, problem("https://example.com:70000/"))
        assertEquals(UrlProblem.BadPort, problem("https://example.com:abc/"))
        assertEquals(UrlProblem.NoHost, problem("https://999.1.1.1/"))
        assertEquals(UrlProblem.NoHost, problem("https://-bad-.example/"))
        assertEquals(UrlProblem.NoHost, problem("https://"))
        assertEquals(UrlProblem.NoHost, problem("https://a..b/"))
    }

    @Test
    fun malformedEmptyAndTooLong() {
        assertEquals(UrlProblem.Empty, problem(""))
        assertEquals(UrlProblem.Empty, problem("   "))
        assertEquals(UrlProblem.Malformed, problem("exa mple.com"))
        assertEquals(UrlProblem.Malformed, problem("https://example.com/a b"))
        assertEquals(UrlProblem.Malformed, problem("https://example.com/%zz"))
        assertEquals(UrlProblem.TooLong, problem("https://e.example/" + "a".repeat(UrlRules.MAX_LENGTH)))
    }

    @Test
    fun hostOfForTheHeader() {
        assertEquals("example.com", UrlRules.hostOf("https://example.com/a"))
        assertNull(UrlRules.hostOf("javascript:x"))
    }

    @Test
    fun websiteNavigation() {
        assertEquals(LinkRoute.InPage, UrlRules.websiteRoute("https://example.org/next", hasGesture = false))
        assertEquals(LinkRoute.InPage, UrlRules.websiteRoute("http://example.org/", hasGesture = false))
        assertEquals(LinkRoute.InPage, UrlRules.websiteRoute("about:blank", hasGesture = false))
        assertEquals(LinkRoute.System, UrlRules.websiteRoute("tel:+911234", hasGesture = true))
        assertEquals(LinkRoute.System, UrlRules.websiteRoute("mailto:a@b.example", hasGesture = true))
        assertEquals(LinkRoute.System, UrlRules.websiteRoute("intent://scan/#Intent;scheme=zxing;end", hasGesture = true))
        // A redirect (no gesture) can't launch an app.
        assertEquals(LinkRoute.Blocked, UrlRules.websiteRoute("market://details?id=x", hasGesture = false))
        assertEquals(LinkRoute.Blocked, UrlRules.websiteRoute("javascript:alert(1)", hasGesture = true))
        assertEquals(LinkRoute.Blocked, UrlRules.websiteRoute("file:///sdcard/a", hasGesture = true))
        assertEquals(LinkRoute.Blocked, UrlRules.websiteRoute("content://x/y", hasGesture = true))
    }

    @Test
    fun runtimeNavigation() {
        val own = RuntimeOrigin.originFor("p0123456789abcdef")
        assertEquals(LinkRoute.InPage, UrlRules.runtimeRoute("$own/widget.html#x", own, hasGesture = false))
        assertEquals(LinkRoute.Browser, UrlRules.runtimeRoute("https://example.org/", own, hasGesture = true))
        assertEquals(LinkRoute.Blocked, UrlRules.runtimeRoute("https://example.org/", own, hasGesture = false))
        assertEquals(LinkRoute.System, UrlRules.runtimeRoute("mailto:a@b.example", own, hasGesture = true))
        assertEquals(LinkRoute.Blocked, UrlRules.runtimeRoute("javascript:x", own, hasGesture = true))
        // Another page's origin exists only inside the launcher.
        assertEquals(
            LinkRoute.Blocked,
            UrlRules.runtimeRoute(RuntimeOrigin.originFor("p1111111111111111") + "/", own, hasGesture = true),
        )
    }

    @Test
    fun aPackageNeverNavigatesOutsideItsOwnOrigin() {
        // D-AC §3.3: sandbox="allow-scripts" only. Its only way out is orrery.open, checked against opens.
        val own = RuntimeOrigin.originFor("p0123456789abcdef")
        assertEquals(LinkRoute.InPage, UrlRules.runtimeRoute("$own/widget.html#x", own, hasGesture = false, isPackage = true))
        for (url in listOf("https://attacker.example/?d=secret", "http://example.org/", "intent://x#Intent;scheme=https;end", "market://details?id=x", "mailto:a@b.example", "tel:123")) {
            assertEquals(url, LinkRoute.Blocked, UrlRules.runtimeRoute(url, own, hasGesture = true, isPackage = true))
        }
        // A single-file page still hands links to the browser after a tap.
        assertEquals(LinkRoute.Browser, UrlRules.runtimeRoute("https://example.org/", own, hasGesture = true, isPackage = false))
    }

    @Test
    fun originOf() {
        assertEquals("https://example.com", UrlRules.originOf("HTTPS://Example.com/x"))
        assertEquals("https://example.com:8443", UrlRules.originOf("https://example.com:8443/x"))
        assertNull(UrlRules.originOf("mailto:x@y.example"))
    }
}

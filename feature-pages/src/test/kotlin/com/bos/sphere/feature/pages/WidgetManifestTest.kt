package com.bos.sphere.feature.pages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetManifestTest {

    private val valid = """{
        "id": "nooz.headlines", "app": "nooz", "name": "Headlines", "version": 1,
        "sizes": ["2x1", "2x2", "4x2", "fill"], "reads": ["news.items", "launcher.clock"],
        "writes": ["news.read"], "refresh": "5m", "opens": ["app:com.example.nooz", "page:{id}"],
        "network": false, "license": "Apache-2.0"
    }"""

    private fun issues(json: String) = (ManifestRules.validate(json) as ManifestCheck.Invalid).issues
    private fun problems(json: String) = issues(json).map { it.problem }.toSet()
    private fun with(field: String, value: String): String {
        val doc = MiniJson.parse(valid).asObject()!!.toMutableMap()
        if (value == "<absent>") doc.remove(field) else doc[field] = MiniJson.parse(value)
        return MiniJson.write(doc)
    }

    @Test
    fun aValidPackageIsAccepted() {
        val ok = ManifestRules.validate(valid) as ManifestCheck.Ok
        assertEquals("nooz.headlines", ok.manifest.id)
        assertEquals("Headlines", ok.manifest.name)
        assertEquals(1L, ok.manifest.version)
        assertEquals(listOf("2x1", "2x2", "4x2", "fill"), ok.manifest.sizes)
        assertEquals(listOf("news.items", "launcher.clock"), ok.manifest.reads)
        assertEquals(listOf("news.read"), ok.manifest.writes)
        assertEquals(listOf("app:com.example.nooz", "page:{id}"), ok.manifest.opens)
        assertFalse(ok.manifest.network)
        assertFalse(ok.manifest.isPage)
        assertTrue(ok.warnings.isEmpty())
    }

    @Test
    fun theSpecsOwnExampleIsValidButItsDeepLinkCantBeOpenedHere() {
        val ok = ManifestRules.validate(with("opens", "\"nooz://read/{id}\"")) as ManifestCheck.Ok
        assertEquals(listOf("nooz://read/{id}"), ok.manifest.opens)
        assertEquals(listOf(ManifestIssue(ManifestProblem.BadOpens, "opens", "nooz://read/{id}")), ok.warnings)
    }

    @Test
    fun requiredFields() {
        for (f in listOf("id", "name", "version", "sizes")) {
            assertTrue(f, ManifestIssue(ManifestProblem.MissingField, f) in issues(with(f, "<absent>")))
        }
        // reads, writes, opens and network may be left out: nothing read, written or opened, offline.
        for (f in listOf("reads", "writes", "opens", "network")) {
            assertTrue(f, ManifestRules.validate(with(f, "<absent>")) is ManifestCheck.Ok)
        }
    }

    @Test
    fun fieldValuesAreChecked() {
        assertEquals(setOf(ManifestProblem.BadId), problems(with("id", "\"no spaces please\"")))
        assertEquals(setOf(ManifestProblem.WrongType), problems(with("id", "7")))
        assertEquals(setOf(ManifestProblem.BadName), problems(with("name", "\"   \"")))
        assertEquals(setOf(ManifestProblem.BadVersion), problems(with("version", "\"1\"")))
        assertEquals(setOf(ManifestProblem.BadVersion), problems(with("version", "0")))
        assertEquals(setOf(ManifestProblem.BadVersion), problems(with("version", "1.5")))
        assertEquals(setOf(ManifestProblem.WrongType), problems(with("sizes", "\"fill\"")))
        assertEquals(setOf(ManifestProblem.BadSize, ManifestProblem.NoFill), problems(with("sizes", "[\"huge\"]")))
        assertEquals(setOf(ManifestProblem.BadChannel), problems(with("reads", "[\"News Items\"]")))
        assertEquals(setOf(ManifestProblem.WrongType), problems(with("writes", "[1]")))
        assertEquals(setOf(ManifestProblem.BadOpens), problems(with("opens", "5")))
        assertEquals(setOf(ManifestProblem.WrongType), problems(with("network", "\"no\"")))
    }

    @Test
    fun aPackageMustFillAPage() {
        assertEquals(setOf(ManifestProblem.NoFill), problems(with("sizes", "[\"2x2\",\"4x2\"]")))
    }

    @Test
    fun packagesNeverReachTheNetwork() {
        assertEquals(setOf(ManifestProblem.NetworkRequested), problems(with("network", "true")))
    }

    @Test
    fun onlyTheLauncherPublishesLauncherChannels() {
        val i = issues(with("writes", "[\"launcher.clock\",\"news.read\"]"))
        assertEquals(listOf(ManifestIssue(ManifestProblem.LauncherChannelWrite, "writes", "launcher.clock")), i)
    }

    @Test
    fun notJsonOrNotAnObject() {
        assertEquals(setOf(ManifestProblem.NotJson), problems("{"))
        assertEquals(setOf(ManifestProblem.NotJson), problems("[1]"))
    }

    @Test
    fun theGeneratedPageManifestFillsAPageAndDeclaresNothing() {
        val m = ManifestRules.forPage("  Orrery ")
        assertEquals("Orrery", m.name)
        assertEquals(listOf("fill"), m.sizes)
        assertTrue(m.reads.isEmpty() && m.writes.isEmpty() && m.opens.isEmpty())
        assertFalse(m.network)
        assertTrue(m.isPage)
        assertEquals("Page", ManifestRules.forPage(" ").name)
    }

    @Test
    fun storedManifestsRoundTrip() {
        val ok = (ManifestRules.validate(valid) as ManifestCheck.Ok).manifest
        assertEquals(ok, ManifestRules.decodeStored(ManifestRules.encode(ok)))
        val page = ManifestRules.forPage("Orrery")
        assertEquals(page, ManifestRules.decodeStored(ManifestRules.encode(page)))
        assertNull(ManifestRules.decodeStored("nope"))
    }
}

class HtmlScanTest {

    @Test
    fun titleAndOrrery() {
        assertEquals("Orrery", HtmlScan.title("<title>Orrery</title><div>"))
        assertTrue(HtmlScan.scan("<title>Orrery</title>\n<link rel=stylesheet href=\"https://fonts.googleapis.com/css2\">").isOrrery)
        assertFalse(HtmlScan.scan("<title>Orrery Home</title>").isOrrery)
        assertEquals("A & B", HtmlScan.title("<TITLE lang=en>  A &amp;\n B </TITLE>"))
        assertEquals("é", HtmlScan.title("<title>&#233;</title>"))
        assertNull(HtmlScan.title("<p>no title</p>"))
    }

    @Test
    fun hostsTheFileMentionsNotXmlNamespaces() {
        val html = """
            <link href="https://fonts.googleapis.com/css2?family=Archivo">
            <svg xmlns="http://www.w3.org/2000/svg"></svg>
            <script>fetch('https://api.open-meteo.com/v1/forecast?x=' + y); fetch("https://API.Open-Meteo.com/b")</script>
        """
        assertEquals(listOf("api.open-meteo.com", "fonts.googleapis.com"), HtmlScan.hosts(html))
    }

    @Test
    fun displayName() {
        val orrery = HtmlScan.scan("<title>Orrery</title>")
        assertEquals("Orrery", HtmlScan.displayName(orrery, "personal-tracker.html"))
        assertEquals("My page", HtmlScan.displayName(HtmlScan.scan("<title>My page</title>"), "x.html"))
        assertEquals("notes.v2", HtmlScan.displayName(HtmlScan.scan("<p>"), "notes.v2.html"))
        assertEquals("Page", HtmlScan.displayName(HtmlScan.scan("<p>"), null))
    }

    @Test
    fun fragments() {
        // Orrery's build writes the page without a doctype or <html>: a fragment.
        assertTrue(HtmlScan.scan("<title>Orrery</title>\n<style>").isFragment)
        assertFalse(HtmlPrep.isFragment("<!doctype html><html>"))
        assertFalse(HtmlPrep.isFragment("\uFEFF  <!-- c -->\n<!DOCTYPE html>\n<html>"))
        assertFalse(HtmlPrep.isFragment("<html lang=en><body>"))
    }
}

class PackageLayoutTest {

    private fun e(path: String, size: Long = 10) = PackageEntry(path, size)

    @Test
    fun safePaths() {
        assertEquals("widget.json", PackageLayout.safePath("widget.json"))
        assertEquals("assets/a.png", PackageLayout.safePath("./assets/a.png"))
        assertEquals("assets/img.png", PackageLayout.safePath("assets\\img.png"))
        assertEquals("assets/", PackageLayout.safePath("assets/"))
        for (bad in listOf("../evil", "/abs/x", "a/../b", "C:\\x", "a//b", "", "a/./b", "x\u0000y")) {
            assertNull(bad, PackageLayout.safePath(bad))
        }
    }

    @Test
    fun filesAtTheTop() {
        val r = PackageLayout.select(listOf(e("widget.json"), e("widget.html"), e("assets/a.png"), e("README.md"), e("assets/")))
            as LayoutCheck.Ok
        assertEquals(mapOf("widget.json" to "widget.json", "widget.html" to "widget.html", "assets/a.png" to "assets/a.png"), r.files)
        assertEquals(1, r.ignored)
    }

    @Test
    fun filesInsideOneFolderAsZipsOfAFolderAre() {
        val r = PackageLayout.select(
            listOf(e("pkg/"), e("pkg/widget.json"), e("pkg/widget.html"), e("pkg/assets/x.css"), e("__MACOSX/pkg/._widget.json"), e("pkg/.DS_Store")),
        ) as LayoutCheck.Ok
        assertEquals(mapOf("widget.json" to "pkg/widget.json", "widget.html" to "pkg/widget.html", "assets/x.css" to "pkg/assets/x.css"), r.files)
    }

    @Test
    fun refusals() {
        assertEquals(LayoutCheck.Invalid(PackageProblem.UnsafePath, "../../x"), PackageLayout.select(listOf(e("widget.json"), e("widget.html"), e("../../x"))))
        assertEquals(PackageProblem.NoManifest, (PackageLayout.select(listOf(e("a/widget.json"), e("b/widget.html"))) as LayoutCheck.Invalid).problem)
        assertEquals(PackageProblem.NoHtml, (PackageLayout.select(listOf(e("widget.json"), e("index.html"))) as LayoutCheck.Invalid).problem)
        assertEquals(PackageProblem.Empty, (PackageLayout.select(listOf(e("pkg/"))) as LayoutCheck.Invalid).problem)
        assertEquals(PackageProblem.TooLarge, (PackageLayout.select(listOf(e("widget.json"), e("widget.html", PackageLayout.MAX_FILE_BYTES + 1))) as LayoutCheck.Invalid).problem)
        val many = listOf(e("widget.json"), e("widget.html")) + (1..PackageLayout.MAX_FILES).map { e("assets/$it.png", 1) }
        assertEquals(PackageProblem.TooManyFiles, (PackageLayout.select(many) as LayoutCheck.Invalid).problem)
        val big = listOf(e("widget.json"), e("widget.html")) + (1..3).map { e("assets/$it.bin", 10L * 1024 * 1024) }
        assertEquals(PackageProblem.TooLarge, (PackageLayout.select(big) as LayoutCheck.Invalid).problem)
    }

    @Test
    fun zipSniffing() {
        assertTrue(PackageLayout.isZip(byteArrayOf(0x50, 0x4B, 3, 4, 0)))
        assertTrue(PackageLayout.isZip(byteArrayOf(0x50, 0x4B, 5, 6)))
        assertFalse(PackageLayout.isZip("<!do".toByteArray()))
        assertFalse(PackageLayout.isZip(byteArrayOf(0x50, 0x4B)))
    }
}

class MiniJsonTest {

    @Test
    fun roundTrips() {
        val v = linkedMapOf(
            "s" to "quote \" slash \\ line\n tab\t uni é \u2028",
            "n" to 42L,
            "d" to 1.5,
            "b" to true,
            "z" to null,
            "a" to listOf(1L, "x", listOf<Any?>(), mapOf<String, Any?>()),
        )
        assertEquals(v, MiniJson.parse(MiniJson.write(v)))
    }

    @Test
    fun numbers() {
        assertEquals(-3L, MiniJson.parse("-3"))
        assertEquals(2.5e3, MiniJson.parse("2.5e3"))
        assertEquals("1", MiniJson.write(1.0))
        assertEquals("null", MiniJson.write(Double.NaN))
    }

    @Test
    fun strictness() {
        for (bad in listOf("", "{", "[1,]", "{\"a\":1,}", "01", "1.", "\"\\x\"", "tru", "{\"a\" 1}", "[1] 2", "\"a\u0001\"")) {
            assertNull(bad, MiniJson.parseOrNull(bad))
        }
        assertNull(MiniJson.parseOrNull("[".repeat(MiniJson.MAX_DEPTH + 2)))
    }

    @Test
    fun unicodeEscapes() {
        assertEquals("é☃", MiniJson.parse("\"\\u00e9\\u2603\""))
        assertEquals("\"\\u2028\"", MiniJson.write("\u2028"))
    }
}

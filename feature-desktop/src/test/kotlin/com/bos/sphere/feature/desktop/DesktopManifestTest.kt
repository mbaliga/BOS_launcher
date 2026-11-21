package com.bos.sphere.feature.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The app manifest is where EXT-02 (a resizable activity, not a home app) and EXT-05 (resizes without recreation) are declared. */
class DesktopManifestTest {
    private val ns = "http://schemas.android.com/apk/res/android"

    private fun activity(): Element {
        val f = File(System.getProperty("user.dir"), "../app/src/main/AndroidManifest.xml")
        assertTrue("manifest not found at ${f.canonicalPath}", f.exists())
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(f)
        val list = doc.getElementsByTagName("activity")
        return (0 until list.length).map { list.item(it) as Element }.first { it.getAttributeNS(ns, "name") == ".DesktopActivity" }
    }

    @Test fun EXT_05_theSurfaceIsResizableHandlesResizeInPlaceAndDeclaresAFreeformLayout() {
        val a = activity()
        assertEquals("true", a.getAttributeNS(ns, "resizeableActivity"))
        val changes = a.getAttributeNS(ns, "configChanges").split("|").toSet()
        for (c in listOf("screenSize", "smallestScreenSize", "screenLayout", "density", "orientation")) assertTrue("configChanges lacks $c", c in changes)
        val layout = a.getElementsByTagName("layout").item(0) as Element
        assertNotNull(layout)
        assertEquals("360dp", layout.getAttributeNS(ns, "minWidth"))
        assertTrue(layout.getAttributeNS(ns, "defaultWidth").removeSuffix("dp").toInt() >= 600)
    }

    @Test fun EXT_02_theSurfaceIsAnOrdinaryActivityNeverTheHomeOrAnExportedEntry() {
        val a = activity()
        assertEquals("false", a.getAttributeNS(ns, "exported"))
        assertFalse(a.getElementsByTagName("category").length > 0)   // no HOME or LAUNCHER category: it is opened with a display id, not claimed
        assertEquals("standard", a.getAttributeNS(ns, "launchMode"))
    }
}

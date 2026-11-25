package com.bos.sphere.feature.homewidgets.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow

/**
 * The widgets' day and night colours (res/values, res/values-night) meet WCAG contrast on their
 * card: text 4.5:1, glyphs and the level bar 3:1. Reads the resource files themselves, so a colour
 * edit that breaks contrast fails here.
 */
class WidgetColorsTest {

    private fun colors(dir: String): Map<String, Long> {
        val file = listOf(File("src/main/res/$dir/colors.xml"), File("feature-homewidgets/src/main/res/$dir/colors.xml"))
            .first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("color")
        return (0 until nodes.length).associate { i ->
            val n = nodes.item(i)
            n.attributes.getNamedItem("name").nodeValue to n.textContent.trim().removePrefix("#").toLong(16)
        }
    }

    private fun channel(argb: Long, shift: Int) = ((argb shr shift) and 0xFF) / 255.0

    /** [fg] composited over the opaque [bg]. */
    private fun over(fg: Long, bg: Long): DoubleArray {
        val a = ((fg shr 24) and 0xFF) / 255.0
        return intArrayOf(16, 8, 0).map { s -> channel(fg, s) * a + channel(bg, s) * (1 - a) }.toDoubleArray()
    }

    private fun luminance(rgb: DoubleArray): Double {
        val lin = rgb.map { c -> if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]
    }

    private fun contrast(a: DoubleArray, b: DoubleArray): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun check(dir: String) {
        val c = colors(dir)
        val surface = c.getValue("hw_surface")
        assertEquals("$dir: the card is opaque", 0xFFL, (surface shr 24) and 0xFF)
        val card = over(surface, surface)
        // Pads and rows sit on a faint tint of the card; text must hold on that too.
        val pad = over(c.getValue("hw_pad"), surface)
        val padArgb = pad.toArgb()
        for (text in listOf("hw_ink_bright", "hw_ink", "hw_ink_dim", "hw_accent")) {
            val onCard = contrast(over(c.getValue(text), surface), card)
            assertTrue("$dir: $text on card $onCard", onCard >= 4.5)
        }
        // Text drawn on pads and rows (labels, counts) never uses hw_ink_dim, which is for the card only.
        for (text in listOf("hw_ink_bright", "hw_ink", "hw_accent")) {
            val onPad = contrast(over(c.getValue(text), padArgb), pad)
            assertTrue("$dir: $text on pad $onPad", onPad >= 4.5)
        }
        // The level bar's fill against its track, and against the card.
        val track = over(c.getValue("hw_track"), surface)
        val fill = over(c.getValue("hw_fill"), surface)
        assertTrue("$dir: fill vs track ${contrast(fill, track)}", contrast(fill, track) >= 3.0)
        assertTrue("$dir: fill vs card", contrast(fill, card) >= 3.0)
    }

    private fun DoubleArray.toArgb(): Long =
        0xFF000000L or map { (it * 255).toLong().coerceIn(0, 255) }.let { (r, g, b) -> (r shl 16) or (g shl 8) or b }

    @Test
    fun nightColoursHoldContrast() = check("values-night")

    @Test
    fun dayColoursHoldContrast() = check("values")

    @Test
    fun dayAndNightDefineTheSameRoles() {
        assertEquals(colors("values").keys, colors("values-night").keys)
    }
}

package com.bos.sphere.core.quality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** NFR-15 (wave 4): reachesAa drives an automatic scrim strengthening and a one-line report for the Lab. */
class HomeLegibilityW4Test {
    private val midGrey = 0xFF808080.toInt()

    @Test fun NFR_15_aPlanThatFitsTheNormalCapIsNotStrengthened() {
        val p = HomeLegibility.plan(listOf(Contrast.WHITE), listOf(0xFFFFE000.toInt()))
        assertTrue(p.reachesAa); assertFalse(p.strengthened)
        assertTrue(p.alpha <= HomeLegibility.MAX_ALPHA)
    }

    @Test fun NFR_15_whenTheNormalCapCannotReachAaTheScrimIsStrengthenedAndStillReachesIt() {
        // Mid-grey text over white needs a black scrim of about 90%: past the 85% cap, inside the 95% limit.
        val p = HomeLegibility.plan(listOf(midGrey), listOf(Contrast.WHITE))
        assertTrue(p.strengthened)
        assertTrue("alpha ${p.alpha}", p.alpha > HomeLegibility.MAX_ALPHA && p.alpha <= HomeLegibility.STRONG_ALPHA)
        assertTrue(p.reachesAa)
        assertTrue(Contrast.ratio(midGrey, Contrast.blend(p.scrimColor, Contrast.WHITE, p.alpha)) >= Contrast.AA_NORMAL - 0.02)
    }

    @Test fun NFR_15_whenNoScrimCanReachAaTheFailureIsReportedNotHidden() {
        // Text of luminance about 0.18 sits between both scrim colours: over white it needs a black scrim past 95%.
        val p = HomeLegibility.plan(listOf(0xFF777777.toInt()), listOf(Contrast.WHITE))
        assertTrue(p.strengthened)
        assertFalse(p.reachesAa)
        assertEquals(HomeLegibility.STRONG_ALPHA, p.alpha, 1e-9)
        assertTrue(HomeLegibility.describe("Live aurora", p).contains("NOT reached"))
    }

    @Test fun NFR_15_theLabLineSaysWhichScrimAndWhetherTheTextReachesAa() {
        val none = HomeLegibility.plan(listOf(Contrast.WHITE), listOf(Contrast.BLACK))
        assertEquals("Black: no scrim needed, text reaches AA", HomeLegibility.describe("Black", none))
        val some = HomeLegibility.plan(listOf(Contrast.WHITE), listOf(0xFFF1EBDD.toInt()))
        val line = HomeLegibility.describe("Solid ivory", some)
        assertTrue(line, line.startsWith("Solid ivory: dark scrim ") && line.endsWith("text reaches AA"))
        val strong = HomeLegibility.describe("Picture horizon", HomeLegibility.plan(listOf(midGrey), listOf(Contrast.WHITE)))
        assertTrue(strong, strong.contains("after strengthening"))
    }

    @Test fun NFR_15_sampledGridsFeedThePlanLikeAnyOtherSamples() {
        // The sampler returns opaque ARGB lists; a mixed bright/dark scene still gets a plan for the worst sample.
        val scene = List(25) { if (it % 5 == 0) 0xFFEFEFEF.toInt() else 0xFF101820.toInt() }
        val p = HomeLegibility.plan(listOf(0xFFE7E9F0.toInt(), 0xFFC9CDD6.toInt()), scene)
        assertTrue(p.reachesAa)
        scene.forEach { w -> listOf(0xFFE7E9F0.toInt(), 0xFFC9CDD6.toInt()).forEach { t -> assertTrue(Contrast.ratio(t, Contrast.blend(p.scrimColor, w, p.alpha)) >= Contrast.AA_NORMAL - 0.02) } }
    }
}

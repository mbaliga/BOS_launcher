package com.bos.sphere.core.quality

import org.junit.Assert.assertTrue
import org.junit.Test

/** Wave 3: source-level enforcement for what code can prove about the NFRs; the numbers still need a device. */
class NfrWave3Test {
    private fun text(path: String) = Repo.file(path).readText()
    private val grid = "feature-grid/src/main/kotlin/com/bos/sphere/feature/grid"

    @Test fun NFR_14_everyInfiniteAnimationAsksTheMotionPreferenceOrTheAnimatorScale() {
        val offenders = Repo.mainSources().filter { it.readText().contains("rememberInfiniteTransition(") }
            .filter { val t = it.readText(); !t.contains("rememberMotionPrefs()") && !t.contains("ANIMATOR_DURATION_SCALE") }
            .map { it.name }
        assertTrue("infinite animations that ignore reduced motion: $offenders", offenders.isEmpty())
    }

    @Test fun NFR_14_homePagingJumpsInsteadOfSlidingWhenAnimationsAreOff() {
        val t = text("$grid/GridHome.kt")
        assertTrue(t.contains("internal suspend fun PagerState.pageTo"))
        // The only direct animateScrollToPage calls left are the helper itself and the host-driven goToPage(animate = ...).
        assertTrue(Regex("animateScrollToPage").findAll(t).count() == 2)
        assertTrue(text("$grid/XmbHome.kt").contains("stillXmb"))
        assertTrue(text("$grid/SearchLayouts.kt").contains("motionRequested && !com.bos.sphere.core.design.rememberMotionPrefs().reduced"))
    }

    @Test fun NFR_13_everyRadarLayoutNamesItsTilesAndFlatSectionsAreHeadings() {
        val t = text("$grid/SearchLayouts.kt")
        // Orbit (rank order), Filmstrip (per-tile name) and Flat (headings) each have their own semantics.
        assertTrue(t.contains("traversalIndex = i.toFloat(); contentDescription = tile.caption()"))
        assertTrue(t.contains("the strip shows the caption of the centred tile only"))
        assertTrue(t.contains("semantics { heading() }"))
    }

    @Test fun NFR_15_texturesAreSampledForTheHomeScrimAndLiveScenesAreTheDocumentedGap() {
        val t = text("$grid/HomeLegibility.kt")
        assertTrue(t.contains("kind == BackgroundKind.Texture"))
        assertTrue(t.contains("live scenes and the bundled Horizon picture"))
    }

    @Test fun NFR_01_NFR_05_macrobenchmarkIsOneGradleFlagAndDeviceStepsAreOneScript() {
        assertTrue(text("settings.gradle.kts").contains("liftoff.benchmark"))
        assertTrue(!text("benchmark/build.gradle.kts").contains("androidx.baselineprofile"))
        val s = text("tools/device_nfr.sh")
        listOf("perf.sh", "measure_pss.sh", "soak.sh", "connectedBenchmarkAndroidTest").forEach { assertTrue(it, s.contains(it)) }
    }

    @Test fun NFR_01_toNFR_19_everyNfrHasAnExactPassFailStepAndARecordingPlaceInTheDeviceChecklist() {
        val c = text("docs/device-checklist.md")
        val ids = listOf("01", "02", "03", "04", "05", "06", "07", "09", "10", "11", "12", "13", "14", "15", "17", "19")
        ids.forEach { assertTrue("NFR-$it missing from the checklist table", c.contains("| NFR-$it |")) }
        assertTrue(c.contains("## 13."))
    }
}

package com.bos.sphere.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.ColdStart
import com.bos.sphere.core.quality.CheckStatus
import com.bos.sphere.core.quality.DeviceCheck
import com.bos.sphere.core.quality.FrameStats
import com.bos.sphere.core.quality.ProbeSurface
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Wave 23 (CFG-17, NFR-05, NFR-12, NFR-13, SPK-01): the Device checks page is reachable from the Lab, remembers its state and uses public APIs only. */
@RunWith(RobolectricTestRunner::class)
class DeviceChecksWiringTest {
    private fun text(path: String): String {
        val dirs = listOf(File("."), File(".."), File("../.."))
        return dirs.map { File(it, path) }.first { it.exists() }.readText()
    }
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun CFG_17_theDeviceChecksPaneIsAnItemOfTheLabCategoryBehindTheLabGate() {
        val cats = text("app/src/main/kotlin/com/bos/sphere/ui/SettingsCategories.kt")
        val lab = cats.substringAfter("if (m.lab.isNotEmpty() && LabGate.visible())").substringBefore("// --- Backup")
        assertTrue(lab.contains("deviceChecksItem()"))
    }

    @Test fun NFR_05_theStoreKeepsTheLastRunPerSurfaceAndTheChecklistAcrossOpens() {
        val store = DeviceChecksStore.open(ctx)
        assertNull(store.probe(ProbeSurface.Glance))
        val r = FrameStats().apply { add(5_000_000); add(40_000_000) }.summary(16_666_666, 7)!!
        store.saveProbe(ProbeSurface.Glance, r)
        store.setStatus(DeviceCheck.TalkBack, CheckStatus.Fail); store.setNote(DeviceCheck.TalkBack, "dock is read last")
        val again = DeviceChecksStore.open(ctx)
        assertEquals(r, again.probe(ProbeSurface.Glance))
        assertNull(again.probe(ProbeSurface.PageSwipe))
        assertEquals(CheckStatus.Fail, again.check(DeviceCheck.TalkBack).status)
        assertEquals("dock is read last", again.check(DeviceCheck.TalkBack).note)
        assertEquals(CheckStatus.Unset, again.check(DeviceCheck.Speech).status)
        assertEquals(DeviceCheck.entries.size, again.checks().size)
    }

    @Test fun NFR_05_aHandEditedProbeValueOfTheWrongTypeReadsAsNotRun() {
        ctx.getSharedPreferences(DeviceChecksStore.FILE, Context.MODE_PRIVATE).edit().putInt("probe.drawer", 5).putString("check.crash.status", "banana").commit()
        val store = DeviceChecksStore.open(ctx)
        assertNull(store.probe(ProbeSurface.DrawerScroll))
        assertEquals(CheckStatus.Unset, store.check(DeviceCheck.RealCrash).status)
    }

    @Test fun NFR_12_theColdStartIsCapturedOncePerProcessFromTheProcessClockAndSaved() {
        assertNull(ColdStart.thisProcessMs)
        ColdStart.firstFrameDrawn(ctx, processStartElapsedMs = 1_000, nowElapsedMs = 1_900)
        ColdStart.firstFrameDrawn(ctx, processStartElapsedMs = 1_000, nowElapsedMs = 9_000) // a later call never overwrites
        assertEquals(900L, ColdStart.thisProcessMs)
        val deadline = System.currentTimeMillis() + 3_000
        while (DeviceChecksStore.open(ctx).lastColdStartMs() == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(900L, DeviceChecksStore.open(ctx).lastColdStartMs())
        assertTrue(text("app/src/main/kotlin/com/bos/sphere/HomeTiming.kt").contains("ColdStart.firstFrameDrawn"))
    }

    @Test fun NFR_13_everyCheckAndSurfaceHasAnEnglishNameAndInstructionThroughTheTranslator() {
        for (c in DeviceCheck.entries) { assertTrue(checkTitle(c).isNotBlank()); assertTrue(checkInstruction(c).length > 30); assertEquals(c.english, checkTitle(c)) }
        for (s in ProbeSurface.entries) assertEquals(s.english, surfaceLabel(s))
    }

    @Test fun SPK_01_theProbeIsRemovedWhenTheRunEndsAndWhenTheActivityStops() {
        val src = text("app/src/main/kotlin/com/bos/sphere/ui/DeviceProbe.kt")
        assertTrue(src.contains("addOnFrameMetricsAvailableListener(l, Handler(t.looper))")) // off the main thread
        assertTrue(src.contains("removeOnFrameMetricsAvailableListener"))
        assertTrue(src.contains("override fun onStop") && src.contains("override fun onDestroy"))
        assertTrue(src.contains("quitSafely()"))
        assertNotNull(DeviceProbe.RUN_MS.takeIf { it == 10_000L })
    }
}

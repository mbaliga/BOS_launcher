package com.bos.sphere

import androidx.test.core.app.ApplicationProvider
import com.bos.sphere.core.data.ActionRunner
import com.bos.sphere.core.data.CornerActionId
import com.bos.sphere.core.design.L10n
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** INP-15, CFG-19 (wave 23 sweep): the toasts of the corner and gesture actions are in the app language; they were English on every phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xhdpi")
class ActionToastsW23Test {
    @After fun english() { L10n.setLanguage("en") }

    @Test fun INP_15_aToastOfAnActionGoesThroughTheTranslator() {
        L10n.setLanguage("de")
        val runner = ActionRunner(ApplicationProvider.getApplicationContext(), onInternal = {}, translate = { L10n.tr(it) })
        runner.run(CornerActionId.Flashlight) // a Robolectric phone has no torch
        assertEquals("Dieses Gerät hat keine Taschenlampe", ShadowToast.getTextOfLatestToast())
    }

    @Test fun INP_15_withoutATranslatorTheEnglishWordsStayAsTheyAre() {
        val runner = ActionRunner(ApplicationProvider.getApplicationContext(), onInternal = {})
        runner.run(CornerActionId.Flashlight)
        assertEquals("No flashlight on this device", ShadowToast.getTextOfLatestToast())
    }
}

package com.bos.sphere.core.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** HOM-12 (wave 24): a workspace switch ends edit mode; nothing else about the pin list does. */
class EditWorkspaceSwitchTest {
    @Test fun HOM_12_aWorkspaceSwitchEndsAnActiveEditSession() {
        assertTrue(EditWorkspaceSwitch.endsEdit(active = true, sessionWorkspace = "home", seenWorkspace = "travel"))
    }

    @Test fun HOM_12_aPinChangeInTheSameWorkspaceDoesNotEndIt() {
        assertFalse(EditWorkspaceSwitch.endsEdit(active = true, sessionWorkspace = "home", seenWorkspace = "home"))
    }

    @Test fun HOM_12_noSessionOrNoKnownWorkspaceNeverEnds() {
        assertFalse(EditWorkspaceSwitch.endsEdit(active = false, sessionWorkspace = "home", seenWorkspace = "travel"))
        assertFalse(EditWorkspaceSwitch.endsEdit(active = true, sessionWorkspace = null, seenWorkspace = "travel"))
        assertFalse("a home without a dock reports an empty id", EditWorkspaceSwitch.endsEdit(active = true, sessionWorkspace = "", seenWorkspace = "travel"))
    }
}

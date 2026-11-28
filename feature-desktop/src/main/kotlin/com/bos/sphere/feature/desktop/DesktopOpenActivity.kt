package com.bos.sphere.feature.desktop

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.bos.sphere.core.design.L10n

/**
 * Transparent trampoline behind the Radar result and the dock item that reopen the desktop surface (EXT-02). It asks the
 * controller for the first usable display, opens the surface there and finishes. Nothing opens when no display can host it.
 */
class DesktopOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = DesktopController.get(this).also { it.start() }
        val action = controller.reopenAction()
        if (action != null) action.run() else Toast.makeText(this, L10n.tr("No external display is ready for the desktop"), Toast.LENGTH_SHORT).show()
        finish()
    }
}

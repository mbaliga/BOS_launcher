package com.bos.sphere.core.design

/**
 * CFG-07: lets any feature module ask for a grant without depending on the module that draws the explanation.
 * A feature calls [ask] when the user first touches it; the home installs [interceptor] to show the one-screen
 * explanation and only then runs [proceed] (the system settings page or permission prompt). With no interceptor
 * installed (tests, headless tools) [proceed] runs at once, so nothing is blocked.
 */
object GrantHooks {
    @Volatile var interceptor: ((GrantKind, () -> Unit) -> Unit)? = null

    fun ask(kind: GrantKind, proceed: () -> Unit) {
        val i = interceptor
        if (i == null) proceed() else i(kind, proceed)
    }
}

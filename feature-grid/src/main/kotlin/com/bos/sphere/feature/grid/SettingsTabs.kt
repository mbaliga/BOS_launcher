package com.bos.sphere.feature.grid

import com.bos.sphere.core.design.L10n

/**
 * Wave 57 (settingsTabs), CFG-40 / CFG-41: the six sections of Settings, navigated with Liftoff's own text tabs. The owner: "Keep our style of tabs
 * however, so that the 6 sections are navigable, clear and consistent." Every category the host is given is one tab (the app gives exactly the six
 * below, in this order); the Finish setup page is not a tab (it is the one row above them while something is unfinished). Pure: no Compose in here,
 * so the rules are tested on the JVM.
 */
object SettingsTabs {
    const val HOME = "Home"
    const val LOOK = "Look"
    const val APPS = "Apps"
    const val RADAR = "Radar"
    const val SIDE_PANEL = "Side panel"
    const val SYSTEM = "System"

    /** The English keys of the six sections, in tab order (left to right; a right-to-left layout mirrors the row). */
    val KEYS: List<String> = listOf(HOME, LOOK, APPS, RADAR, SIDE_PANEL, SYSTEM)

    /** The English key of a tab from its (maybe translated) title; null for a title that is not one of the six. */
    fun key(title: String): String? = KEYS.firstOrNull { title == it || title == L10n.tr(it) }

    /** The category indices of [categories] that are tabs, in order: everything except the Finish setup page. */
    fun tabIndices(categories: List<SettingCategory>): List<Int> = categories.indices.filter { OneUiModel.key(categories[it].title) != OneUiModel.FINISH_SETUP }

    /** The tab one step from [current] of [count] ([forward] = the next one); wraps, so Ctrl+Tab from the last section returns to the first. */
    fun step(current: Int, count: Int, forward: Boolean): Int = if (count <= 0) 0 else (current + if (forward) 1 else -1).mod(count)

    /** Keys that switch the section. */
    enum class KeyStep { Next, Previous }

    /**
     * Ctrl+Tab and the controller's R1 go to the next section, Ctrl+Shift+Tab and L1 to the previous one. [keyCode] is an Android key code (so the
     * rule is testable without a key event). Null for every other key.
     */
    fun keyStep(keyCode: Int, ctrl: Boolean, shift: Boolean): KeyStep? = when {
        keyCode == KEYCODE_TAB && ctrl -> if (shift) KeyStep.Previous else KeyStep.Next
        keyCode == KEYCODE_BUTTON_R1 -> KeyStep.Next
        keyCode == KEYCODE_BUTTON_L1 -> KeyStep.Previous
        else -> null
    }

    // android.view.KeyEvent values, kept here so the JVM tests need no Android stubs.
    const val KEYCODE_TAB = 61
    const val KEYCODE_BUTTON_L1 = 102
    const val KEYCODE_BUTTON_R1 = 103

    /**
     * Where Settings opens: the tab ([tabOf]) of a category index, or the remembered tab ([remembered], an English key) when the request names
     * no tab, else the first one. Returns a position among [tabs] (category indices).
     */
    fun initialTab(categories: List<SettingCategory>, tabs: List<Int>, requested: Int, remembered: String?): Int {
        val direct = tabs.indexOf(requested)
        if (direct >= 0) return direct
        val mem = remembered?.let { r -> tabs.indexOfFirst { key(categories[it].title) == r || categories[it].title == r } } ?: -1
        return mem.coerceAtLeast(0)
    }

    /** Where the user is: the tab position, and the row of a category whose own page is open on top ([subCategory] / [subItem] >= 0). */
    data class Nav(val tab: Int = 0, val subCategory: Int = -1, val subItem: Int = -1) {
        val subOpen: Boolean get() = subCategory >= 0 && subItem >= 0
        fun openSub(category: Int, item: Int) = copy(subCategory = category, subItem = item)
        /** One step back; null when Settings closes (no page is open on top of the tabs). */
        fun back(): Nav? = if (subOpen) copy(subCategory = -1, subItem = -1) else null
    }
}

/**
 * Wave 57: which section was shown last, so Settings opens on it again. The app keeps it in a preference file; the default lives as long as the
 * process (tests, previews).
 */
interface SettingsTabMemory {
    fun get(): String?
    fun set(key: String)

    /** A memory of its own (a preview, a test): it remembers within one Settings host only. */
    class Local : SettingsTabMemory {
        private var last: String? = null
        override fun get(): String? = last
        override fun set(key: String) { last = key }
    }

    object Process : SettingsTabMemory {
        @Volatile private var last: String? = null
        override fun get(): String? = last
        override fun set(key: String) { last = key }
        fun clear() { last = null }
    }
}

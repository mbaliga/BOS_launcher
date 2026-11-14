package com.bos.sphere.core.data

import org.json.JSONArray

/** Where Launchpad settings go on apply; the default writes to [LaunchpadSettings]. */
interface LaunchpadSink {
    fun rules(): List<Rule>
    fun removeRule(id: String)
    fun addRule(r: Rule)
    fun setDefaultMode(m: GroupMode)
    fun perApp(): Map<String, GroupMode>
    fun setAppMode(pkg: String, m: GroupMode?)
    fun setGesture(g: LaunchpadGesture, a: LaunchpadAction)
    /** STR-04 delivery policies; defaults keep older sinks compiling. */
    fun policies(): List<AppPolicy> = emptyList()
    fun setPolicies(list: List<AppPolicy>) {}

    object Live : LaunchpadSink {
        override fun rules() = LaunchpadSettings.rules.value
        override fun removeRule(id: String) = LaunchpadSettings.removeRule(id)
        override fun addRule(r: Rule) = LaunchpadSettings.addRule(r)
        override fun setDefaultMode(m: GroupMode) = LaunchpadSettings.setDefaultMode(m)
        override fun perApp() = LaunchpadSettings.prefs.value.perApp
        override fun setAppMode(pkg: String, m: GroupMode?) = LaunchpadSettings.setAppMode(pkg, m)
        override fun setGesture(g: LaunchpadGesture, a: LaunchpadAction) = LaunchpadSettings.setGesture(g, a)
        override fun policies() = LaunchpadSettings.policies.value
        override fun setPolicies(list: List<AppPolicy>) = LaunchpadSettings.replacePolicies(list)
    }
}

/** Where the hidden-apps set lives (a SharedPreferences string set; the in-memory store re-reads it when the activity is recreated). */
interface HiddenSink {
    fun get(): Set<String>
    fun set(keys: Set<String>)
}

/** Reads the live stores into [StateParts] and writes parts back (import, snapshot restore). Pure over the store objects, so JVM-testable. */
class StateApplier(
    private val prefs: LauncherPrefsStore,
    private val layout: HomeLayoutStore,
    private val shortcuts: ShortcutStore,
    private val launchpad: LaunchpadSink,
    private val extras: () -> StateExtras,
    private val writeExtras: (StateExtras) -> Unit,
    private val launchpadPrefs: () -> LaunchpadPrefs = { LaunchpadSettings.prefs.value },
    private val launchpadGestures: () -> Map<LaunchpadGesture, LaunchpadAction> = { LaunchpadSettings.gestures.value },
    private val hiddenApps: HiddenSink? = null,
) {
    fun current(): StateParts {
        val g = shortcuts.gestures.value
        return StateParts(
            prefs = prefs.prefs.value,
            layout = layout.layout.value,
            corners = shortcuts.corners.value,
            landscapeCorners = shortcuts.explicitLandscapeCorners(),
            hidden = hiddenApps?.get().orEmpty(),
            gestures = HomeGesture.entries.associateWith { g[it] },
            launchpadRules = launchpad.rules(),
            launchpadPrefs = launchpadPrefs(),
            launchpadGestures = launchpadGestures(),
            launchpadPolicies = launchpad.policies(),
            extras = extras(),
        )
    }

    /**
     * Applies the sections in [present] (a settings-only file leaves layout and shortcuts alone). Values that belong
     * to this device (widget bindings, the picked background picture) are kept from the live state.
     */
    fun apply(parts: StateParts, present: Set<String> = StateSchema.KNOWN_TOP) {
        if (StateSchema.K_SETTINGS in present) {
            val cur = prefs.prefs.value
            prefs.update {
                parts.prefs.copy(
                    leftWidgetId = cur.leftWidgetId,
                    backgroundPicturePath = cur.backgroundPicturePath,
                    onboardingDone = true,
                )
            }
        }
        if (StateSchema.K_LAYOUT in present) {
            layout.replace(parts.layout)
            hiddenApps?.set(parts.hidden)
        }
        if (StateSchema.K_SHORTCUTS in present) {
            val live = shortcuts.corners.value
            parts.corners.forEach { (c, slot) ->
                val bound = live[c]
                val keepWidget = slot == CornerSlot.Empty && bound is CornerSlot.Widget &&
                    parts.extras.widgets.any { it.slot == "corner.${c.name}" }
                if (!keepWidget) shortcuts.setCorner(c, slot)
            }
            val liveLandscape = shortcuts.landscapeCorners.value
            parts.landscapeCorners.forEach { (c, slot) ->
                val keepWidget = slot == CornerSlot.Empty && liveLandscape[c] is CornerSlot.Widget &&
                    parts.extras.widgets.any { it.slot == "lcorner.${c.name}" }
                if (!keepWidget) shortcuts.setLandscapeCorner(c, slot)
            }
            parts.gestures.forEach { (g, item) -> shortcuts.setGesture(g, item) }
        }
        if (StateSchema.K_LAUNCHPAD in present) {
            launchpad.rules().forEach { launchpad.removeRule(it.id) }
            parts.launchpadRules.forEach(launchpad::addRule)
            launchpad.setDefaultMode(parts.launchpadPrefs.defaultMode)
            launchpad.perApp().keys.forEach { launchpad.setAppMode(it, null) }
            parts.launchpadPrefs.perApp.forEach { (k, v) -> launchpad.setAppMode(k, v) }
            parts.launchpadGestures.forEach { (g, a) -> launchpad.setGesture(g, a) }
            launchpad.setPolicies(parts.launchpadPolicies)
        }
        val cur = extras()
        val ex = parts.extras
        val touched = StateSchema.OPAQUE_SECTIONS.any { it in present } || StateNested.KEYS_BY_SECTION.keys.any { it in present } ||
            StateSchema.K_WIDGETS in present || StateSchema.OBJECT_SECTIONS.any { it in present }
        if (touched) {
            writeExtras(
                cur.copy(
                    opaque = cur.opaque + StateSchema.OPAQUE_SECTIONS.filter { it in present }.associateWith { ex.opaque[it] ?: JSONArray() },
                    // A settings-only file carries no top-level extras, so it must not wipe the ones already kept.
                    unknownTop = if (StateSchema.K_LAYOUT in present) ex.unknownTop else cur.unknownTop,
                    unknownPrefs = if (StateSchema.K_SETTINGS in present) ex.unknownPrefs else cur.unknownPrefs,
                    keyboardShortcuts = if (StateSchema.K_SHORTCUTS in present) ex.keyboardShortcuts else cur.keyboardShortcuts,
                    widgets = if (StateSchema.K_WIDGETS in present) ex.widgets else cur.widgets,
                    nested = StateNested.merge(cur.nested, ex.nested, present),
                    objects = cur.objects + StateSchema.OBJECT_SECTIONS.filter { it in present }.mapNotNull { k -> ex.objects[k]?.let { k to it } },
                ),
            )
        }
    }
}

package com.bos.sphere.core.capabilities

/** The three capability tiers of the spec (section 4), detected at runtime and never from a device list. */
enum class Tier { T1, T2, T3 }

/** How a capability is reached on non-root Android (spec section 3). */
enum class Access { Yes, Grant, Partial, No }

enum class CapState { On, Off, Unknown }

/** One row of the capability report (CFG-08): what it is, whether it is on here, and why not and how to fix it. */
data class Capability(
    val id: String,
    val title: String,
    val access: Access,
    val state: CapState,
    val reason: String? = null,
    val fix: String? = null,
)

data class DisplayInfo(
    val id: Int,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val isDefault: Boolean,
    /** Null when the platform does not say (older API levels). */
    val canHostTasks: Boolean? = null,
)

/** Everything the evaluator needs, gathered once by [CapabilityRegistry]; plain data so it can be unit-tested. */
data class CapabilityInputs(
    val apiLevel: Int,
    val windowWidthDp: Int,
    val hasKeyboard: Boolean = false,
    val hasPointer: Boolean = false,
    val hasController: Boolean = false,
    val displays: List<DisplayInfo> = emptyList(),
    val desktopWindowing: Boolean? = null,
    val isHomeRole: Boolean = false,
    val notificationAccess: Boolean = false,
    val usageAccess: Boolean = false,
    val contactsGranted: Boolean = false,
    val folderGranted: Boolean = false,
    val speechOnDevice: Boolean? = null,
    val pinShortcutsSupported: Boolean = false,
    /** Used only to pick the text of the OEM notes row (NFR-21), never to gate a feature. */
    val manufacturer: String = "",
)

object CapabilityEvaluator {
    const val COMPACT_MAX_DP = 600

    /** T3 on a desktop-windowing or task-hosting second display (Android 16+), T2 for large windows or attached keyboard / pointer, else T1. */
    fun tier(i: CapabilityInputs): Tier {
        val external = i.displays.any { !it.isDefault && it.canHostTasks == true }
        if (i.apiLevel >= 36 && (i.desktopWindowing == true || external)) return Tier.T3
        if (i.windowWidthDp >= COMPACT_MAX_DP || i.hasKeyboard || i.hasPointer) return Tier.T2
        return Tier.T1
    }

    fun externalDisplays(i: CapabilityInputs): List<DisplayInfo> = i.displays.filter { !it.isDefault }

    fun evaluate(i: CapabilityInputs): List<Capability> {
        fun grant(id: String, title: String, granted: Boolean, fix: String) =
            Capability(id, title, Access.Grant, if (granted) CapState.On else CapState.Off, if (granted) null else "Not granted", if (granted) null else fix)

        val tier = tier(i)
        val ext = externalDisplays(i)
        return listOf(
            Capability("tier", "Capability tier", Access.Yes, CapState.On, reason = "Detected as ${tier.name}"),
            Capability(
                "home_role", "Home app role", Access.Yes, if (i.isHomeRole) CapState.On else CapState.Off,
                if (i.isHomeRole) null else "Liftoff is not the default home app", if (i.isHomeRole) null else "Set Liftoff as the home app",
            ),
            Capability("launcher_apps", "List and launch apps, work profile", Access.Yes, CapState.On),
            Capability(
                "private_space", "Private space", Access.Partial,
                if (i.apiLevel >= 35 && i.isHomeRole) CapState.On else CapState.Off,
                if (i.apiLevel < 35) "Needs Android 15 or later" else "Needs the home role", if (i.apiLevel < 35) null else "Set Liftoff as the home app",
            ),
            Capability(
                "pin_shortcuts", "App shortcuts and pinning", Access.Yes,
                if (i.pinShortcutsSupported && i.isHomeRole) CapState.On else CapState.Off,
                if (!i.isHomeRole) "Only while Liftoff is the home app" else if (!i.pinShortcutsSupported) "Not supported here" else null,
            ),
            Capability("widgets", "Host widgets", Access.Yes, CapState.On, reason = "Each widget asks for bind consent once"),
            grant("notification_access", "Notification content, badges, media", i.notificationAccess, "Allow notification access"),
            grant("usage_access", "Recent and foreground app history", i.usageAccess, "Allow usage access"),
            grant("contacts", "Contact search", i.contactsGranted, "Allow contacts"),
            grant("folders", "File search (media and granted folders)", i.folderGranted, "Allow media or grant a folder"),
            Capability(
                "launch_display", "Launch on a chosen display", Access.Yes,
                if (ext.isNotEmpty()) CapState.On else CapState.Off,
                if (ext.isEmpty()) "No other display is connected" else null,
            ),
            Capability(
                "launch_bounds", "Launch with window bounds", Access.Partial,
                if (i.desktopWindowing == true) CapState.On else if (i.desktopWindowing == null) CapState.Unknown else CapState.Off,
                if (i.desktopWindowing == true) null else "Ignored outside desktop windowing; run the Lab spike SPK-03",
            ),
            Capability(
                "launch_adjacent", "Launch into split", Access.Partial, CapState.Unknown,
                "Varies by version and device; run the Lab spike SPK-02",
            ),
            Capability("windowing_mode", "Force a windowing mode", Access.No, CapState.Off, "Hidden API, not used in the core build"),
            Capability("window_mgmt", "Move, resize or close other apps' windows", Access.No, CapState.Off, "Owned by the system; launch-time placement only"),
            Capability("recents", "Replace Recents and gesture navigation", Access.No, CapState.Off, "Owned by the system launcher"),
            Capability(
                "external_desktop", "Desktop surface on an external display", Access.Partial,
                if (tier == Tier.T3 && ext.isNotEmpty()) CapState.On else CapState.Off,
                if (i.apiLevel < 36) "Needs Android 16 or later" else if (ext.isEmpty()) "No external display connected" else null,
            ),
            Capability("global_keys", "Global shortcuts outside the launcher", Access.No, CapState.Off, "Needs an accessibility service; shortcuts work on launcher surfaces only"),
            Capability("toggle_radios", "Toggle Wi-Fi, Bluetooth, data", Access.No, CapState.Off, "Blocked for apps since Android 10; the state row opens system panels"),
            Capability("open_shade", "Open the notification shade", Access.Partial, CapState.Unknown, "No stable public API; run the Lab spike SPK-04"),
            Capability("clipboard_history", "Background clipboard history", Access.No, CapState.Off, "Readable only while a launcher surface is focused"),
            Capability(
                "speech", "On-device speech recognition", Access.Yes,
                when (i.speechOnDevice) { true -> CapState.On; false -> CapState.Off; null -> CapState.Unknown },
                if (i.speechOnDevice == false) "No on-device recogniser; voice entry is hidden" else null,
            ),
            Capability("keyboard", "Hardware keyboard", Access.Yes, if (i.hasKeyboard) CapState.On else CapState.Off),
            Capability("pointer", "Mouse or trackpad", Access.Yes, if (i.hasPointer) CapState.On else CapState.Off),
            Capability("controller", "Game controller", Access.Yes, if (i.hasController) CapState.On else CapState.Off),
            OemNotes.row(i.manufacturer),
        )
    }

    /** The report as plain text, for copying out of the Lab page. */
    fun asText(i: CapabilityInputs): String = buildString {
        appendLine("Android API ${i.apiLevel}, window ${i.windowWidthDp} dp, tier ${tier(i).name}")
        appendLine("Displays: " + i.displays.joinToString { "#${it.id} ${it.name} ${it.widthPx}x${it.heightPx}@${it.densityDpi}${if (it.isDefault) " (default)" else ""}${it.canHostTasks?.let { t -> if (t) " hosts-tasks" else "" } ?: ""}" })
        evaluate(i).forEach { c ->
            append("${c.title}: ${c.state}")
            c.reason?.let { append(" - $it") }
            c.fix?.let { append(" ($it)") }
            appendLine()
        }
    }
}

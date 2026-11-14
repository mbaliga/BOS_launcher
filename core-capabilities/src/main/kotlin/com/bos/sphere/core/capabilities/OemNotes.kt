package com.bos.sphere.core.capabilities

/**
 * NFR-21: short per-manufacturer notes for the capability report. The long version is docs/oem-notes.md.
 * The manufacturer string only chooses which note to show; it never switches a feature on or off.
 */
object OemNotes {
    enum class Oem(val label: String) { Samsung("Samsung"), Xiaomi("Xiaomi"), OnePlus("OnePlus"), Pixel("Pixel"), Other("Other") }

    fun oemFor(manufacturer: String): Oem {
        val m = manufacturer.trim().lowercase()
        return when {
            m.contains("samsung") -> Oem.Samsung
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> Oem.Xiaomi
            m.contains("oneplus") || m.contains("oppo") || m.contains("realme") -> Oem.OnePlus
            m.contains("google") -> Oem.Pixel
            else -> Oem.Other
        }
    }

    fun text(oem: Oem): String = when (oem) {
        Oem.Samsung -> "Samsung: set Liftoff to Unrestricted under Settings > Apps > Liftoff > Battery, and remove it from Sleeping apps. " +
            "With gesture navigation, Samsung's Good Lock or One UI home settings can disable swipe-up while a third-party home app is set; use three-button navigation if the swipe does nothing."
        Oem.Xiaomi -> "Xiaomi: turn on Autostart and set Battery saver to No restrictions for Liftoff; lock it in Recents so MIUI does not close it. " +
            "Full-screen gestures may fall back to buttons under a third-party home app."
        Oem.OnePlus -> "OnePlus and Oppo: allow background activity and auto-launch for Liftoff in App battery usage. " +
            "Recents and the swipe-up gesture can stop working with a third-party home app; switch to three-button navigation if so."
        Oem.Pixel -> "Pixel: no special steps. Recents and gesture navigation stay with the system launcher and keep working. Battery: leave Liftoff on Optimized."
        Oem.Other -> "Some manufacturers close background apps or disable gestures under a third-party home app. If Liftoff restarts unexpectedly, " +
            "set its battery use to Unrestricted; if swipe-up does nothing, switch to three-button navigation. See docs/oem-notes.md."
    }

    /** The "OEM notes" row of the capability report. Informational, so it is always On. */
    fun row(manufacturer: String): Capability =
        Capability("oem_notes", "OEM notes", Access.Yes, CapState.On, reason = text(oemFor(manufacturer)))
}

package com.bos.sphere.core.data

/**
 * HOM-16, PAL-24 (wave 15, owner decision): the background of the app is up to two items chosen from nine, at most one of
 * them a living wallpaper. Each item is a signal view: it glows differently with the signal level (see feature-grid
 * `SignalBackground`). [None] is a valid state of each slot (pure black). Everything before this model (the old gallery of
 * solid colours, gradients, textures, live scenes, pictures and the system wallpaper) was removed on purpose, see
 * docs/decisions/backgroundsReset.md.
 */
enum class BgItem(val id: String, val label: String, val living: Boolean) {
    None("none", "None", false),
    Beam("beam", "Beam", true),
    Spire("spire", "Spire", true),
    Halo("halo", "Halo", true),
    Laser("laser", "Laser", true),
    Shaft("shaft", "Shaft", true),
    GlowLines("glow-lines", "Glow lines", false),
    BreathingDots("breathing-dots", "Breathing dots", false),
    BottomGlow("bottom-glow", "Bottom glow", false),
    RadarSweep("radar-sweep", "Radar sweep", false);

    companion object {
        fun forId(id: String?): BgItem? = entries.firstOrNull { it.id == id }
        /** The five living wallpapers, in the order Ground Control lists them. */
        val livingWallpapers: List<BgItem> = entries.filter { it.living }
        /** The four effects. */
        val effects: List<BgItem> = entries.filter { !it.living && it != None }
    }
}

/** The two slots. Construct through [normalised] or [BackgroundChoice.with] so the rules always hold. */
data class BackgroundChoice(val slot1: BgItem = BgItem.Spire, val slot2: BgItem = BgItem.BreathingDots) {
    /** The items that are drawn, bottom slot first, without None. */
    val items: List<BgItem> get() = listOf(slot1, slot2).filter { it != BgItem.None }
    /** The living wallpaper in use, if any. */
    val wallpaper: BgItem? get() = items.firstOrNull { it.living }
    val isValid: Boolean get() = BackgroundChoice.valid(slot1, slot2)

    /** Why [item] cannot go in slot [slot] (1 or 2) next to the other slot, or null when it can. */
    fun refusal(slot: Int, item: BgItem): BackgroundRefusal? {
        val other = if (slot == 1) slot2 else slot1
        if (item == BgItem.None) return null
        if (item == other) return BackgroundRefusal.SameItem
        if (item.living && other.living) return BackgroundRefusal.TwoWallpapers
        return null
    }

    fun isAllowed(slot: Int, item: BgItem): Boolean = refusal(slot, item) == null

    /** The choice with [item] in [slot]; unchanged when the rules refuse it. */
    fun with(slot: Int, item: BgItem): BackgroundChoice {
        if (!isAllowed(slot, item)) return this
        return if (slot == 1) copy(slot1 = item) else copy(slot2 = item)
    }

    companion object {
        val DEFAULT = BackgroundChoice(BgItem.Spire, BgItem.BreathingDots)

        fun valid(a: BgItem, b: BgItem): Boolean = !(a != BgItem.None && a == b) && !(a.living && b.living)

        /** A valid choice from any pair: when the rules are broken the second slot is emptied. */
        fun normalised(a: BgItem, b: BgItem): BackgroundChoice = if (valid(a, b)) BackgroundChoice(a, b) else BackgroundChoice(a, BgItem.None)

        /**
         * The choice for two stored ids. Both absent (a fresh install, or an install or file from before wave 15, whose
         * old background choice is dropped) gives [DEFAULT]; an id this build does not know falls back to that slot's default.
         */
        fun fromStored(a: String?, b: String?): BackgroundChoice {
            if (a == null && b == null) return DEFAULT
            return normalised(BgItem.forId(a) ?: DEFAULT.slot1, BgItem.forId(b) ?: DEFAULT.slot2)
        }
    }
}

enum class BackgroundRefusal { SameItem, TwoWallpapers }

/** The preference keys of the background before wave 15. They are no longer read; imports drop them (see [StateSchema.RETIRED_PREF_KEYS]). */
object RetiredBackgroundKeys {
    val KEYS: Set<String> = setOf("bgKind", "bgPreset", "bgA", "bgB", "bgPicture", "wallpaper", "searchBackdrop", "searchBdStyle", "radarSweep", "radarSignalView", "radarComic")
}

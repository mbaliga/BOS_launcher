package com.bos.sphere.feature.desktop

enum class OfferSetting(val label: String) { Always("Always open"), Ask("Ask me"), Never("Never open") }

enum class SkipReason { NotEligible, Mirrored, AlreadyOpen, PlatformProvidesHome, Never }

sealed interface OfferDecision {
    /** Open the surface without asking. */
    data object OpenNow : OfferDecision
    /** Show the offer (notification with Open, Always, Never). */
    data object Prompt : OfferDecision
    data class Skip(val reason: SkipReason) : OfferDecision
}

/** What to do with a desktop surface when its display goes away (EXT-06). */
enum class DisconnectAction { None, Close, MigrateToPrimary }

/** A surface that is open: [ownerDisplayId] is where it was opened, [currentDisplayId] where it is now. */
data class SurfaceInfo(val ownerDisplayId: Int, val currentDisplayId: Int, val identity: String)

object DesktopPolicy {
    /**
     * EXT-02. A display that cannot host tasks, or only mirrors, never gets an offer. If the platform already
     * gave the launcher home on that display (SPK-01 positive case) the offer is skipped. Otherwise the first
     * connection of a display is offered once, after that the user setting decides.
     */
    fun onConnected(display: TrackedDisplay, setting: OfferSetting, offeredBefore: Boolean, platformProvidesHome: Boolean, surfaceOpenHere: Boolean): OfferDecision = when {
        display.mirrored -> OfferDecision.Skip(SkipReason.Mirrored)
        !display.eligible -> OfferDecision.Skip(SkipReason.NotEligible)
        surfaceOpenHere -> OfferDecision.Skip(SkipReason.AlreadyOpen)
        platformProvidesHome -> OfferDecision.Skip(SkipReason.PlatformProvidesHome)
        setting == OfferSetting.Never -> OfferDecision.Skip(SkipReason.Never)
        !offeredBefore -> OfferDecision.Prompt
        setting == OfferSetting.Always -> OfferDecision.OpenNow
        else -> OfferDecision.Prompt
    }

    /**
     * EXT-06. The system itself moves tasks off a removed display onto the primary one, which is how a
     * second home surface could appear. Migration is therefore allowed only if nothing already shows a
     * surface on the primary display; otherwise the extra one closes.
     */
    fun onDisconnected(surface: SurfaceInfo, removedId: Int, surfaces: List<SurfaceInfo>, primaryId: Int, migrate: Boolean): DisconnectAction {
        if (surface.ownerDisplayId != removedId) return DisconnectAction.None
        if (!migrate) return DisconnectAction.Close
        val primaryTaken = surfaces.any { it !== surface && it.currentDisplayId == primaryId }
        return if (primaryTaken) DisconnectAction.Close else DisconnectAction.MigrateToPrimary
    }

    /** A surface that migrated to the primary display gives way when its display comes back. */
    fun migratedShouldYield(surface: SurfaceInfo, primaryId: Int, reconnected: TrackedDisplay): Boolean =
        surface.currentDisplayId == primaryId && surface.identity == reconnected.identity && reconnected.eligible

    /** Home is "provided" when this launcher's own home activity is running on that display (SPK-01). */
    fun platformProvidesHome(homeDisplayIds: Set<Int>, displayId: Int) = displayId in homeDisplayIds
}

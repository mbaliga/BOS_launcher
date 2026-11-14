package dev.aarso.search.palette

/** What a palette search is limited to (PAL-13). One enum for prefixes, the category wheel and providers. */
enum class PaletteScope { Everything, Apps, Files, Settings, Contacts, Actions, Web }

/**
 * Everything a result can offer. App ids deliberately share names with core-input's `MenuAction` so one list
 * drives long-press, right-click and the palette action list (PAL-08); the feature-search adapter maps them.
 */
enum class ResultActionId {
    Open, OpenOnDisplay, OpenBeside, AppInfo, AddToDock, AddToWorkspace, Uninstall, Rename, Remove, MoveToFolder,
    Copy, Call, Message, Email, OpenContact, EnablePermission, SearchWeb, SearchStore, Run,
}

/** One entry of a result's action list. Labels are resolved by the UI layer (localised), never here. */
data class ResultAction(val id: ResultActionId, val enabled: Boolean = true)

/** A result from any provider. The first action is the primary one (Enter); Tab or Right opens the full list. */
data class PaletteResult(
    val id: String,
    val provider: String,
    val scope: PaletteScope,
    val title: String,
    val subtitle: String? = null,
    /** Match quality from the provider, 0..1 (the calculator uses a larger value to pin its answer on top). */
    val score: Double,
    val actions: List<ResultAction>,
    /** Provider specific data the runner needs (package, user, uri, intent action, url ...). */
    val payload: Map<String, String> = emptyMap(),
    /** What Enter copies for a [ResultActionId.Copy] action. */
    val copyText: String? = null,
    /** Sorted after every other result, never reordered (the web fallback, PAL-06). */
    val pinnedLast: Boolean = false,
    /** Selections of this result are learned for ranking (PAL-09). */
    val learn: Boolean = true,
) {
    val primary: ResultAction? get() = actions.firstOrNull()
    val usageKey: String get() = payload["usage"] ?: id
}

data class PaletteQuery(val text: String, val scope: PaletteScope = PaletteScope.Everything)

/**
 * The one internal provider interface (PAL-14): query, cancel, result with actions. A new provider is registered
 * in a [ProviderRegistry] and its results render with the existing result row; no palette UI change is needed.
 */
interface PaletteProvider {
    val id: String

    /** Scope that selects this provider on its own. Providers with [PaletteScope.Everything] only run in unscoped search. */
    val scope: PaletteScope

    /** Slow providers stream in after the fast ones and never block typing (PAL-12). */
    val slow: Boolean get() = false

    /** Results for [q]; must honour coroutine cancellation. Never throw for "no results". */
    suspend fun query(q: PaletteQuery, limit: Int): List<PaletteResult>

    /** What to show before anything is typed (recent apps, pinned actions). Default: nothing. */
    suspend fun suggestions(limit: Int): List<PaletteResult> = emptyList()

    /** Called when the palette closes or the query changes, in addition to coroutine cancellation. */
    fun cancel() {}

    fun serves(requested: PaletteScope): Boolean = requested == PaletteScope.Everything || requested == scope
}

class ProviderRegistry(initial: List<PaletteProvider> = emptyList()) {
    private val list = ArrayList(initial)

    @Synchronized fun register(p: PaletteProvider) { list.removeAll { it.id == p.id }; list += p }
    @Synchronized fun unregister(id: String) { list.removeAll { it.id == id } }
    @Synchronized fun providers(): List<PaletteProvider> = list.toList()
    @Synchronized fun forScope(s: PaletteScope): List<PaletteProvider> = list.filter { it.serves(s) }
}

/** Localisable strings the engine needs for rows it creates itself. The Android adapter supplies L10n-backed ones. */
class PaletteStrings(
    val searchWeb: (String) -> String = { "Search the web for “$it”" },
    val searchStore: (String) -> String = { "Search the app store for “$it”" },
    val enableContacts: String = "Allow contacts to search people",
    val enableFiles: String = "Allow files to search them",
    val calculatorHint: String = "Press Enter to copy",
)

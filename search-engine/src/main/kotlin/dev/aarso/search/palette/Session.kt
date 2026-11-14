package dev.aarso.search.palette

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaletteState(
    val rawQuery: String = "",
    val scope: PaletteScope = PaletteScope.Everything,
    val text: String = "",
    val results: List<PaletteResult> = emptyList(),
    /** Ids of providers that have not answered yet (a spinner, never a blocker). */
    val pending: Set<String> = emptySet(),
    val selectedId: String? = null,
    val open: Boolean = false,
)

/**
 * One open palette (PAL-12). [setQuery] returns at once; providers run on [scope]. A new query or [close] cancels the
 * pending work, and reopening with the same query restores the previous results and only re-asks providers that had
 * not answered. The selected row never moves when late results arrive.
 */
class PaletteSession(private val engine: PaletteEngine, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(PaletteState())
    val state: StateFlow<PaletteState> = _state.asStateFlow()

    private var job: Job? = null
    private var byProvider = LinkedHashMap<String, List<PaletteResult>>()
    private var completed = LinkedHashSet<String>()
    private var lastRaw: String? = null

    /** Opens the palette with [raw]; the same query as last time restores its results instead of starting empty. */
    fun open(raw: String = "") {
        _state.update { it.copy(open = true) }
        start(raw, restore = raw == lastRaw)
    }

    fun setQuery(raw: String) {
        if (raw == _state.value.rawQuery && job != null) return
        start(raw, restore = false)
    }

    /** Call from keyboard or pointer navigation only; late results then keep this row in place. */
    fun select(id: String?) = _state.update { it.copy(selectedId = id) }

    fun close() {
        job?.cancel(); job = null
        engine.cancelProviders()
        _state.update { it.copy(open = false, pending = emptySet()) }
    }

    private fun start(raw: String, restore: Boolean) {
        job?.cancel()
        engine.cancelProviders()
        val plan = engine.plan(raw)
        if (!restore) { byProvider = LinkedHashMap(); completed = LinkedHashSet() }
        lastRaw = raw
        val pending = plan.providers.map { it.id }.filter { it !in completed }.toSet()
        val keepSel = if (restore) _state.value.selectedId else null
        publish(plan, raw, pending, keepSel, fresh = !restore)
        if (pending.isEmpty()) { job = null; return }
        job = scope.launch {
            engine.stream(plan, skip = completed.toSet()).collect { b ->
                byProvider[b.providerId] = b.results
                completed += b.providerId
                publish(plan, raw, plan.providers.map { it.id }.filter { it !in completed }.toSet(), _state.value.selectedId, fresh = false)
            }
        }
    }

    private fun publish(plan: Plan, raw: String, pending: Set<String>, selectedId: String?, fresh: Boolean) {
        val ideal = engine.finalList(plan, byProvider.values.flatten())
        _state.update { old ->
            val shown = if (fresh) emptyList() else old.results
            old.copy(rawQuery = raw, scope = plan.scope, text = plan.text, results = StableMerge.merge(shown, ideal, selectedId), pending = pending, selectedId = selectedId)
        }
    }
}

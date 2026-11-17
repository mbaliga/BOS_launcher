package dev.aarso.search.palette

/**
 * A query and the scope it is limited to. The scope is chosen with the category wheel (PAL-19) and handed in by the
 * caller; the text is never parsed for a typed prefix (PAL-13 was removed, see docs/decisions/radarSearchCleanup.md).
 */
data class ScopedQuery(val scope: PaletteScope, val rest: String)

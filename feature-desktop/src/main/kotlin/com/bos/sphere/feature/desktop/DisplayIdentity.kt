package com.bos.sphere.feature.desktop

/** Stable per-display key for profiles (EXT-09). Pure so it can be tested without a device. */
object DisplayIdentity {
    /**
     * Uses the panel's product info (manufacturer, product id, model year) when the platform gives it,
     * otherwise resolution and name. The resolution is stored long-side first so rotation does not
     * change the key. Two identical monitors share a key, which is the honest limit of public APIs.
     */
    fun key(name: String, widthPx: Int, heightPx: Int, manufacturer: String? = null, productId: String? = null, modelYear: Int? = null): String {
        if (!manufacturer.isNullOrBlank() && !productId.isNullOrBlank()) {
            return "dev:" + clean(manufacturer) + ":" + clean(productId) + (modelYear?.takeIf { it > 0 }?.let { ":$it" } ?: "")
        }
        val w = maxOf(widthPx, heightPx)
        val h = minOf(widthPx, heightPx)
        return "res:${w}x$h:" + clean(name)
    }

    /** A readable name for a stored identity, for the list of remembered displays (EXT-09). */
    fun label(identity: String): String {
        val parts = identity.split(":")
        return when (parts.firstOrNull()) {
            "res" -> if (parts.size >= 3) parts.drop(2).joinToString(":").replace('-', ' ').ifBlank { parts[1] } + " (" + parts[1] + ")" else identity
            "dev" -> parts.drop(1).filter { it.isNotBlank() }.joinToString(" ").ifBlank { identity }
            else -> identity
        }
    }

    private fun clean(s: String) = s.trim().lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-')
}

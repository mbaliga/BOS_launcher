package com.bos.sphere.shaders

import android.os.Build

/**
 * Capability gate for AGSL [android.graphics.RuntimeShader] materials.
 *
 * Per the build brief (§3, Open Decision 1) the launcher targets minSdk 31 for reach but
 * `RuntimeShader` / AGSL is only available from API 33 (Android 13, [Build.VERSION_CODES.TIRAMISU]).
 * Every shader-backed material (provenance glow, controlled surfaces) must check
 * [agslSupported] and fall back to a flat-color / gradient rendering on older devices.
 */
object ShaderCapability {

    /** True when `android.graphics.RuntimeShader` (AGSL) can be used on this device. */
    val agslSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Runs [supported] when AGSL is available, otherwise [fallback]. Keeps the
     * gate in one place so feature code never branches on the SDK level directly.
     */
    inline fun <T> withAgsl(supported: () -> T, fallback: () -> T): T =
        if (agslSupported) supported() else fallback()
}

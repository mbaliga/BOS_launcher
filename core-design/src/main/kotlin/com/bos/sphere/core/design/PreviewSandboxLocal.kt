package com.bos.sphere.core.design

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Wave 57 (previewAll, CFG-52): true inside the Settings Preview's off-screen sandbox. A surface composed there (Radar, the disc, the Comms panel)
 * takes no focus, raises no keyboard, consumes no navigation request meant for the real surface and registers no global hooks.
 */
val LocalPreviewSandbox = staticCompositionLocalOf { false }

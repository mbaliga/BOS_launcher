package com.bos.sphere.ui

import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.SettingItem

/** Wave 39 (dockThemes): the Themes entry of Ground Control, built here so the shared settings builder gets one line. W55 (dockSheet): the Dock style entry left Home screen; the style is chosen in the dock's sheet and on Settings > Desktop & dock > Dock. */
fun themesSettingItem(): SettingItem = SettingItem.Pane(L10n.tr("Themes"), Glyphs.Palette, L10n.tr("Import, export, reset")) { ThemesPane() }

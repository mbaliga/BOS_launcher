package com.bos.sphere.feature.grid

import com.bos.sphere.core.design.L10n

/** Ground Control, Home, "Keyboard shortcuts": the row that opens the shortcut sheet by touch (INP-07, ShortcutSheet). One definition, so a test renders the real row. */
fun keyboardShortcutsRow(): SettingItem.Action = SettingItem.Action(
    L10n.tr("Keyboard shortcuts"), Glyphs.Info, L10n.tr("Every shortcut and its keys; Meta shortcuts stay with the system"), navigates = true,
) { LauncherInput.sheetOpen.value = true }

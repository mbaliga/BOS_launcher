package com.bos.sphere.feature.widgets

internal fun placement(
    id: Int,
    surface: WidgetSurface = WidgetSurface.Equator,
    slot: Int = 0,
    hubSize: HubSize = HubSize.Medium,
    pkg: String = "com.example.app$id",
    cls: String = "com.example.app$id.Provider",
    serial: Long = 0L,
) = WidgetPlacement(id, pkg, cls, serial, surface, slot, hubSize)

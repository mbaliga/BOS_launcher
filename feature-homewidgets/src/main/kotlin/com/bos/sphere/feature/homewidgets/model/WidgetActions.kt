package com.bos.sphere.feature.homewidgets.model

/**
 * Every tap a widget can carry. Each widget id + action gets its own PendingIntent request code,
 * so no two taps on the home screen share (and overwrite) one PendingIntent.
 */
enum class WidgetAction(val slot: Int) {
    OPEN(0),
    ROW_1(1), ROW_2(2), ROW_3(3), ROW_4(4), ROW_5(5),
    PAD_1(6), PAD_2(7), PAD_3(8), PAD_4(9), PAD_5(10), PAD_6(11),
    PREVIOUS(12), PLAY_PAUSE(13), NEXT(14),
    REFRESH(15),
    CONFIGURE(16),
    ;

    companion object {
        val ROWS = listOf(ROW_1, ROW_2, ROW_3, ROW_4, ROW_5)
        val PADS = listOf(PAD_1, PAD_2, PAD_3, PAD_4, PAD_5, PAD_6)

        /** Room for this many actions per widget id. */
        const val STRIDE = 32

        fun requestCode(appWidgetId: Int, action: WidgetAction): Int = appWidgetId * STRIDE + action.slot
    }
}

/** A media command a Now playing pad sends, validated by name when it comes back. */
enum class MediaCommand {
    PREVIOUS,
    PLAY_PAUSE,
    NEXT,
    ;

    companion object {
        fun parse(name: String?): MediaCommand? = entries.firstOrNull { it.name == name }
    }
}

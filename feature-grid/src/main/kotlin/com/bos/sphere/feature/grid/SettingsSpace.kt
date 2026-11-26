package com.bos.sphere.feature.grid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import com.bos.sphere.core.design.gate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.focusable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/** One row in a settings category. Everything simple is edited right on the row; only [Pane] opens a page. */
sealed interface SettingItem {
    val title: String
    val glyph: ImageVector
    val value: String
    /** Wave 56: the hidden side of the row (id, search keywords, why it is off); see [ItemMeta]. */
    val meta: ItemMeta

    /** A switch on the row; tapping anywhere on the row flips it. */
    data class Toggle(override val title: String, override val glyph: ImageVector, val checked: Boolean, val hint: String? = null, val onChange: (Boolean) -> Unit) : SettingItem {
        override val value: String get() = if (checked) "On" else "Off"
        override val meta = ItemMeta()
    }

    /** Title and current value on one line, the slider beneath. [step] 0 = automatic (1% for fractions, whole numbers otherwise). */
    class Slider(
        override val title: String,
        override val glyph: ImageVector,
        val amount: Float,
        val range: ClosedFloatingPointRange<Float>,
        val valueText: String,
        val hint: String? = null,
        val step: Float = 0f,
        /** Wave 56 (CFG-29): where the default sits; a small tick is drawn there and a "Default" text action shows while [amount] differs. Null = no default known. */
        val default: Float? = null,
        /** Wave 56 (CFG-29): the preference is derived (it follows something) and [valueText] says so ("Auto"); the "Default" action is then not offered. */
        val auto: Boolean = false,
        val onChange: (Float) -> Unit,
    ) : SettingItem {
        override val value: String get() = valueText
        override val meta = ItemMeta()
        /** The same slider with a default and a readout that has its unit (the central table of the app fills these in). */
        fun with(default: Float? = this.default, valueText: String = this.valueText, auto: Boolean = this.auto): Slider =
            Slider(title, glyph, amount, range, valueText, hint, step, default, auto, onChange).also { it.meta.copyFrom(meta) }
        /** Whether the "Default" action shows: a default is known, the value is not it, and the preference is not derived. */
        val offersDefault: Boolean get() = !auto && default != null && !SettingsMath.sameValue(amount, default, range, if (step > 0f) step else SettingsMath.autoStep(range))
    }

    /** The current value on the row with up / down arrows that step through [options] (wrapping); more than 6 options open a menu. */
    class Choice<T>(
        override val title: String,
        override val glyph: ImageVector,
        val options: List<T>,
        val selected: T,
        val label: (T) -> String,
        val hint: String? = null,
        /** When set, a "Preview…" link on the row opens this as a full page (the visual picker). */
        val preview: (@Composable ColumnScope.() -> Unit)? = null,
        /** Wave 29: never the one-slab segment control, always the arrows (for labels too long to share a slab, like the five Comms views). */
        val stepped: Boolean = false,
        val onSelect: (T) -> Unit,
    ) : SettingItem {
        override val value: String get() = label(selected)
        override val meta = ItemMeta()
    }

    /** − value + for small whole numbers. */
    data class Stepper(
        override val title: String, override val glyph: ImageVector, val count: Int, val range: IntRange, val hint: String? = null,
        /** Wave 28: the size of one step (the Comms size moves in 5s) and the text shown for the value ("100%"); by default one and the number. */
        val step: Int = 1, val valueText: String? = null,
        val onChange: (Int) -> Unit,
    ) : SettingItem {
        override val value: String get() = valueText ?: count.toString()
        override val meta = ItemMeta()
        /** The value a screen reader's set-progress asks for: snapped to [step] from the range start and kept inside the range. */
        fun fromProgress(v: Float): Int = (Math.floor(((v - range.first) / step).toDouble() + 0.5).toInt() * step + range.first).coerceIn(range.first, range.last)
    }

    /** Runs on tap (opens a dialog, adds a page …). */
    data class Action(override val title: String, override val glyph: ImageVector, override val value: String = "", val navigates: Boolean = false,
        /** Wave 29: a destructive action asks first: this text in a confirmation sheet whose confirm button is [confirmLabel]. */
        val confirm: String? = null, val confirmLabel: String = "Confirm",
        /** Wave 29: the summary line under the title, like every other row. */
        val hint: String? = null, val onClick: () -> Unit) : SettingItem {
        override val meta = ItemMeta()
    }

    /** Opens a full-screen page with its own controls; the row shows the current value and a chevron. */
    class Pane(
        override val title: String,
        override val glyph: ImageVector,
        override val value: String,
        /** Wave 41: true when the pane lays out its own One UI groups ([OneUiGroup] and friends): the host then adds no padding around it. */
        val bare: Boolean = false,
        /** Wave 41: a live line under the large title of the page (the active workspace, for one). */
        val pageSubtitle: (@Composable () -> String)? = null,
        val content: @Composable ColumnScope.() -> Unit,
    ) : SettingItem {
        override val meta = ItemMeta()
        constructor(title: String, glyph: ImageVector, value: String, content: @Composable ColumnScope.() -> Unit) : this(title, glyph, value, false, null, content)
    }
}

/**
 * [reset] (wave 56, CFG-30): puts the preferences of this page back to their defaults; null = the page has nothing of its own to reset (it only
 * holds the user's data or actions). The page shows "Reset this page to defaults" at its bottom when it is set; the host asks first and takes a snapshot.
 */
data class SettingCategory(
    val title: String, val glyph: ImageVector, val items: List<SettingItem>, val reset: (() -> Unit)? = null,
    /** Wave 57 (CFG-40): the grey section headers of the page: item index to the translated header shown above that row. */
    val headers: Map<Int, String> = emptyMap(),
    /** Wave 58 (CFG-65): a short line under some headers (same keys as [headers]): the help that left the rows' grey lines (the help diet). */
    val notes: Map<Int, String> = emptyMap(),
)

/**
 * Settings, wave 57 (settingsTabs): six sections behind Liftoff's text tabs (see [OneUiSettingsHost]); every category is one tab, except the
 * Finish setup page (a row above the tabs). Every change still clicks with the console tones ([UiSounds]) and a haptic tick. [initialCategory]
 * names the tab to open (a category index; below zero = the tab shown last, [tabMemory]); [initialRow] is a row of it to scroll to and flash.
 */
@Composable
fun SettingsSpace(
    visible: Boolean,
    categories: List<SettingCategory>,
    initialCategory: Int,
    sounds: UiSounds?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Bump to reset the selection to [initialCategory] for a fresh visit. */
    session: Int = 0,
    /** What sits behind Settings (the dimmed wallpaper, or black). Null = the plain One UI background. */
    backdrop: (@Composable () -> Unit)? = null,
    /** CFG-18: the always-visible live preview of the home (see [SettingsPreviewDock]). */
    previewDock: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    /** CFG-30 (wave 56): the snapshots a reset takes first and its Undo restores (the state service in the app). */
    snapshots: SettingsSnapshots? = null,
    /** Wave 57: the row of [initialCategory] a deep link lands on (scrolled to and flashed); -1 = none. */
    initialRow: Int = -1,
    /** Wave 57: where the last section shown is kept (the app passes its preference-backed one). */
    tabMemory: SettingsTabMemory = androidx.compose.runtime.remember { SettingsTabMemory.Local() },
) {
    // NFR-14: the fade becomes quicker when animations are off.
    val reducedMotion = com.bos.sphere.core.design.rememberMotionPrefs().reduced
    AnimatedVisibility(
        visible = visible,
        enter = if (reducedMotion) fadeIn(tween(90)) else fadeIn(tween(220)) + scaleIn(initialScale = 1.06f, animationSpec = tween(260)),
        exit = if (reducedMotion) fadeOut(tween(90)) else fadeOut(tween(180)) + scaleOut(targetScale = 1.04f, animationSpec = tween(180)),
        modifier = modifier,
    ) {
        // Wave 57 (CFG-50): what the open page configures, written by the settings host and read only by the Preview window.
        val previewFocus = remember { PreviewFocusState() }
        androidx.compose.runtime.CompositionLocalProvider(LocalSettingsBackdrop provides backdrop, LocalPreviewFocus provides previewFocus) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                androidx.compose.runtime.key(session) { OneUiSettingsHost(categories, initialCategory, sounds, onClose, snapshots, initialRow, tabMemory) }
                previewDock?.invoke(this)
            }
        }
    }
}


/** The Settings backdrop slot, shared with the full-screen option pages. */
internal val LocalSettingsBackdrop = androidx.compose.runtime.compositionLocalOf<(@Composable () -> Unit)?> { null }


/** Slowly drifting translucent ribbons — the "waves" behind a console menu, in Hyle colours. */
@Composable
internal fun WaveField(modifier: Modifier) {
    // NFR-14: with system animations off the ribbons hold one frame.
    val still = com.bos.sphere.core.design.rememberMotionPrefs().reduced
    val phase by if (still) androidx.compose.runtime.rememberUpdatedState(0.25f)
    else rememberInfiniteTransition(label = "waves").animateFloat(0f, 1f, infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "phase")
    Canvas(modifier) {
        val ribbons = listOf(
            Triple(HyleColors.Violet, 0.58f, 0.0f),
            Triple(HyleColors.ProvenanceCloud, 0.64f, 0.33f),
            Triple(HyleColors.VioletSoft, 0.70f, 0.66f),
        )
        ribbons.forEach { (color, yFrac, offset) ->
            val path = Path()
            val baseY = size.height * yFrac
            val amp = size.height * 0.07f
            val steps = 48
            for (i in 0..steps) {
                val x = size.width * i / steps
                val a = 2 * PI * (i.toDouble() / steps * 1.3 + phase + offset)
                val y = baseY + amp * sin(a).toFloat() + amp * 0.4f * sin(2.1 * a + offset * 5).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color.copy(alpha = 0.16f), style = Stroke(width = 1.4.dp.toPx()))
            val band = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
            drawPath(band, Brush.verticalGradient(listOf(color.copy(alpha = 0.05f), Color.Transparent), startY = baseY - amp, endY = size.height))
        }
        // Faint dot lattice.
        val spacing = 26.dp.toPx()
        var y = spacing / 2
        while (y < size.height) {
            var x = spacing / 2
            while (x < size.width) {
                drawCircle(HyleColors.InkDim.copy(alpha = 0.08f), radius = 1.dp.toPx(), center = Offset(x, y))
                x += spacing
            }
            y += spacing
        }
    }
}

/** Left inset a full-bleed sideways rail uses as its own start padding, so its cards line up with the list but scroll to the edge. */
internal val LocalRailInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

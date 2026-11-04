package com.bos.sphere.feature.widgets.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.feature.sphere.EquatorItem
import com.bos.sphere.feature.sphere.TILE_SIZE_DP
import com.bos.sphere.feature.sphere.TouchPassThrough
import com.bos.sphere.feature.sphere.touchPassThrough
import com.bos.sphere.feature.widgets.EquatorMerge
import com.bos.sphere.feature.widgets.HubStackLayout
import com.bos.sphere.feature.widgets.PlacementEdits
import com.bos.sphere.feature.widgets.PlacementTarget
import com.bos.sphere.feature.widgets.R
import com.bos.sphere.feature.widgets.SizeDp
import com.bos.sphere.feature.widgets.WidgetHostActivityBinding
import com.bos.sphere.feature.widgets.WidgetHosting
import com.bos.sphere.feature.widgets.WidgetPlacement
import com.bos.sphere.feature.widgets.WidgetSizing
import com.bos.sphere.feature.widgets.WidgetStatus
import com.bos.sphere.feature.widgets.WidgetSurface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val NoPlacements: StateFlow<List<WidgetPlacement>> = MutableStateFlow(emptyList())
private val NoStatus: StateFlow<Map<Int, WidgetStatus>> = MutableStateFlow(emptyMap())
private val NoFocus: StateFlow<Int?> = MutableStateFlow(null)

/** Key of the hub stack's touch pass-through region. */
private const val HUB_STACK_REGION = "hub-widget-stack"

/** Width assumed for the hub stack until it has been measured once. */
private const val DEFAULT_HUB_WIDTH_DP = 360f

/**
 * The home screen's handle on App Widget hosting: the merged equator items, the equator tile and
 * hub stack content, the picker and the widget menu. With a null binding (no activity to run the
 * bind flow) everything here is inert and the launcher behaves as before.
 */
@Stable
class WidgetHostUi internal constructor(
    internal val binding: WidgetHostActivityBinding?,
    internal val views: HostedViewCache?,
) {
    internal val hosting: WidgetHosting? get() = binding?.hosting

    /** True when widgets can be added (an activity is attached). */
    val enabled: Boolean get() = binding != null

    /** Regions (the hub stack) whose gestures belong to the hosted widgets, not the surface. */
    val passThrough = TouchPassThrough()

    internal var pickerTarget by mutableStateOf<PlacementTarget?>(null)
    internal var menuWidgetId by mutableStateOf<Int?>(null)
    internal var hubWidthDp by mutableFloatStateOf(DEFAULT_HUB_WIDTH_DP)

    /**
     * Opens the picker. [equatorIndex] is where an equator widget is inserted (its index in the
     * merged list); pass -1 to use the focused tile's index at the time of picking.
     */
    fun openPicker(surface: WidgetSurface, equatorIndex: Int = -1) {
        if (!enabled) return
        menuWidgetId = null
        pickerTarget = PlacementTarget(surface, equatorIndex)
    }

    /** Opens the widget menu (remove, reconfigure, move, size). */
    fun openMenu(appWidgetId: Int) {
        if (enabled) menuWidgetId = appWidgetId
    }

    /** Re-checks hosted widgets after the app inventory changed (packages added / removed / changed). */
    fun onPackagesChanged() {
        hosting?.refresh()
    }

    /** Closes the picker and menu (home key, settings opening). */
    fun dismissAll() {
        pickerTarget = null
        menuWidgetId = null
    }

    @Composable
    internal fun placementsState(): State<List<WidgetPlacement>> =
        (hosting?.placements ?: NoPlacements).collectAsState()

    @Composable
    internal fun statusState(): State<Map<Int, WidgetStatus>> =
        (hosting?.status ?: NoStatus).collectAsState()
}

/** Remembers the widget hosting UI for this composition. */
@Composable
fun rememberWidgetHostUi(binding: WidgetHostActivityBinding?): WidgetHostUi {
    val context = LocalContext.current
    val ui = remember(binding) {
        WidgetHostUi(binding, binding?.let { HostedViewCache(context, it.hosting) })
    }
    val placements by ui.placementsState()
    LaunchedEffect(ui, placements) {
        ui.views?.retain(placements.mapTo(HashSet()) { it.appWidgetId })
    }
    DisposableEffect(ui) {
        onDispose {
            ui.views?.clear()
            // The shared host also keeps every view it created (and so this Activity) until told.
            ui.hosting?.releaseViews()
        }
    }
    return ui
}

/** Apps plus equator widgets, merged in slot order: the coverflow's item list. */
@Composable
fun WidgetHostUi.equatorItems(apps: List<AppEntry>): List<EquatorItem> {
    val placements by placementsState()
    val status by statusState()
    val unavailable = stringResource(R.string.widgets_unavailable_label)
    val restoring = stringResource(R.string.widgets_restoring_title)
    val generic = stringResource(R.string.widgets_generic_label)
    return remember(apps, placements, status, unavailable, restoring, generic) {
        EquatorMerge.merge(apps.map { EquatorItem.App(it) }, placements) { p ->
            val label = when (val s = status[p.appWidgetId]) {
                is WidgetStatus.Available -> s.label
                is WidgetStatus.Unavailable -> s.appLabel.ifBlank { if (s.restoring) restoring else unavailable }
                null -> generic
            }
            EquatorItem.Widget(p.appWidgetId, label)
        }
    }
}

/**
 * Brings a just-placed (or just-moved) widget into view once: [onFocusEquatorIndex] with its index
 * on the equator, or [onOpenHub] when it went to the hub.
 */
@Composable
fun WidgetHostUi.FocusNewWidgets(
    items: List<EquatorItem>,
    onFocusEquatorIndex: (Int) -> Unit,
    onOpenHub: () -> Unit,
) {
    val focus by (hosting?.focusRequest ?: NoFocus).collectAsState()
    val placements by placementsState()
    val focusEquator by rememberUpdatedState(onFocusEquatorIndex)
    val openHub by rememberUpdatedState(onOpenHub)
    LaunchedEffect(focus, items, placements) {
        val id = focus ?: return@LaunchedEffect
        val placement = placements.firstOrNull { it.appWidgetId == id } ?: return@LaunchedEffect
        when (placement.surface) {
            WidgetSurface.Hub -> {
                hosting?.consumeFocusRequest(id)
                openHub()
            }
            WidgetSurface.Equator -> {
                val index = items.indexOfFirst { it is EquatorItem.Widget && it.appWidgetId == id }
                if (index >= 0) {
                    hosting?.consumeFocusRequest(id)
                    focusEquator(index)
                }
            }
        }
    }
}

/**
 * Content of an equator widget tile (the card itself is drawn by the coverflow). [lens] is the
 * equator's focus scale: the tile is laid out at that size, which is the size the provider is told.
 */
@Composable
fun WidgetHostUi.EquatorTile(item: EquatorItem.Widget, interactive: Boolean, lens: Float) {
    val status by statusState()
    val sizes = remember(lens) { WidgetSizing.equatorSizes(TILE_SIZE_DP, lens) }
    WidgetBody(
        appWidgetId = item.appWidgetId,
        status = status[item.appWidgetId],
        interactive = interactive,
        sizes = sizes,
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * The hub's widget stack: full panel width, each widget at its chosen height, with a label row and
 * an options button, plus an "Add widget" entry. Gestures that start here belong to the widgets
 * (the stack is a [TouchPassThrough] region), but only once the hub is fully open ([hubFraction]
 * ≈ 1): while the hub is sliding or fading, the surface keeps every gesture and the widgets take
 * none. It scrolls only when it doesn't fit, so when it fits a drag inside a widget reaches the
 * widget's own lists. [sharesPanel]: other hub content (notifications) needs room too, so the stack
 * leaves some of the height to it ([HubStackLayout.viewportDp]).
 */
@Composable
fun WidgetHostUi.HubStack(
    hubFraction: Float,
    sharesPanel: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val placements by placementsState()
    val status by statusState()
    val stack = remember(placements) { PlacementEdits.hubStack(placements) }
    val live = HubStackLayout.isLive(hubFraction)
    // Re-keyed on [live]: closing or sliding the hub unregisters the region at once.
    DisposableEffect(this, live) {
        onDispose { passThrough.unregister(HUB_STACK_REGION) }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = if (constraints.hasBoundedHeight) maxHeight.value else Float.POSITIVE_INFINITY
        val viewport = HubStackLayout.viewportDp(available, sharesPanel)
        val scroll = HubStackLayout.needsScroll(stack.map { it.hubSize }, viewport)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (live) Modifier.touchPassThrough(passThrough, HUB_STACK_REGION) else Modifier)
                .then(
                    if (scroll) {
                        Modifier.heightIn(max = viewport.dp).verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(HubStackLayout.GAP_DP.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = HubStackLayout.ROW_HEADER_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.widgets_section),
                    color = HyleColors.InkDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Chip(
                    label = "+ " + stringResource(R.string.widgets_add),
                    contentDescription = stringResource(R.string.widgets_add),
                    onClick = { openPicker(WidgetSurface.Hub) },
                )
            }
            stack.forEach { placement ->
                key(placement.appWidgetId) {
                    HubWidgetCard(placement, status[placement.appWidgetId], interactive = live)
                }
            }
        }
    }
}

@Composable
private fun WidgetHostUi.HubWidgetCard(
    placement: WidgetPlacement,
    status: WidgetStatus?,
    interactive: Boolean,
) {
    val density = LocalDensity.current.density
    val label = labelOf(status)
    var widthDp by remember { mutableFloatStateOf(hubWidthDp) }
    val shape = RoundedCornerShape(22.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = HubStackLayout.ROW_HEADER_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                label,
                color = HyleColors.Ink,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if ((status as? WidgetStatus.Available)?.isWorkProfile == true) WorkProfileTag()
            OptionsButton(
                contentDescription = stringResource(R.string.widgets_options_cd, label),
                onClick = { openMenu(placement.appWidgetId) },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(placement.hubSize.heightDp.dp)
                .onSizeChanged {
                    widthDp = it.width / density
                    hubWidthDp = widthDp
                }
                .clip(shape)
                .background(HyleColors.Surface, shape)
                .border(1.dp, HyleColors.Violet.copy(alpha = 0.2f), shape),
        ) {
            WidgetBody(
                appWidgetId = placement.appWidgetId,
                status = status,
                interactive = interactive,
                sizes = WidgetSizing.hubSizes(widthDp, placement.hubSize),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A placed widget's content: the hosted view, an honest "unavailable" card, or a placeholder. */
@Composable
private fun WidgetHostUi.WidgetBody(
    appWidgetId: Int,
    status: WidgetStatus?,
    interactive: Boolean,
    sizes: List<SizeDp>,
    modifier: Modifier,
) {
    val cache = views ?: return
    when (status) {
        is WidgetStatus.Available -> HostedWidgetView(
            cache = cache,
            appWidgetId = appWidgetId,
            info = status.info,
            interactive = interactive,
            sizes = sizes,
            modifier = modifier,
        )

        is WidgetStatus.Unavailable -> {
            LaunchedEffect(appWidgetId) { cache.evict(appWidgetId) }
            UnavailableWidget(
                appLabel = status.appLabel,
                restoring = status.restoring,
                interactive = interactive,
                onRemove = { hosting?.remove(appWidgetId) },
                modifier = modifier,
            )
        }

        null -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.widgets_loading),
                color = HyleColors.InkDim,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Shown when there is no provider to draw: `getAppWidgetInfo` returned null (mainly a restored
 * widget whose app isn't installed yet), or, with [restoring], the widget is still waiting for the
 * backup restore. A struck-through circle and words, never colour alone, and a Remove action (a
 * 48 dp target, live only while the tile is interactive). Below [COMPACT_BELOW_DP] of height (the
 * two-row equator tile, the Small hub preset) the icon and title share one row and the body text
 * is left out, so the icon and the whole Remove target stay visible.
 */
@Composable
private fun UnavailableWidget(
    appLabel: String,
    restoring: Boolean,
    interactive: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val title = stringResource(
        if (restoring) R.string.widgets_restoring_title else R.string.widgets_unavailable_title,
    )
    val body = when {
        restoring -> stringResource(R.string.widgets_restoring_body)
        appLabel.isNotBlank() -> stringResource(R.string.widgets_unavailable_body, appLabel)
        else -> stringResource(R.string.widgets_unavailable_body_unknown)
    }
    val remove: @Composable () -> Unit = {
        Chip(
            label = stringResource(R.string.widgets_remove),
            contentDescription = stringResource(R.string.widgets_remove_cd, appLabel.ifBlank { title }),
            enabled = interactive,
            onClick = onRemove,
        )
    }
    val titleText: @Composable () -> Unit = {
        Text(
            title,
            color = HyleColors.InkBright,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
    BoxWithConstraints(modifier.padding(10.dp), contentAlignment = Alignment.Center) {
        if (maxHeight.value < COMPACT_BELOW_DP) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StruckCircle()
                    titleText()
                }
                remove()
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StruckCircle()
                titleText()
                Text(
                    body,
                    color = HyleColors.InkDim,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                remove()
            }
        }
    }
}

/** Inner height under which [UnavailableWidget] drops its body text (full layout ≈ 130 dp). */
private const val COMPACT_BELOW_DP = 140f

/** The "unavailable" shape: a circle struck through, so the state never rests on colour. */
@Composable
private fun StruckCircle() {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(HyleColors.InkDim, radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
        drawLine(
            HyleColors.InkDim,
            start = Offset(size.width * 0.22f, size.height * 0.78f),
            end = Offset(size.width * 0.78f, size.height * 0.22f),
            strokeWidth = stroke,
        )
    }
}

/** A bordered text button with a 48 dp minimum touch target. */
@Composable
internal fun Chip(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .background(
                    if (selected) HyleColors.Violet.copy(alpha = 0.18f) else HyleColors.Surface.copy(alpha = 0.6f),
                    shape,
                )
                .border(
                    1.dp,
                    if (selected) HyleColors.Violet else HyleColors.Hairline,
                    shape,
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(label, color = if (selected) HyleColors.InkBright else HyleColors.Ink, fontSize = 12.sp)
        }
    }
}

@Composable
private fun OptionsButton(contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("•••", color = HyleColors.InkBright, fontSize = 12.sp)
    }
}

@Composable
internal fun WorkProfileTag() {
    val shape = RoundedCornerShape(8.dp)
    Text(
        stringResource(R.string.widgets_picker_work),
        color = HyleColors.Ink,
        fontSize = 12.sp,
        modifier = Modifier
            .border(1.dp, HyleColors.Hairline, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Label for a placed widget: its own, the missing provider's app, or a generic word. */
@Composable
internal fun labelOf(status: WidgetStatus?): String = when (status) {
    is WidgetStatus.Available -> status.label
    is WidgetStatus.Unavailable -> status.appLabel.ifBlank {
        stringResource(
            if (status.restoring) R.string.widgets_restoring_title else R.string.widgets_unavailable_label,
        )
    }
    null -> stringResource(R.string.widgets_generic_label)
}

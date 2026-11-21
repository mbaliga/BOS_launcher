package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.data.ContactDirectory
import com.bos.sphere.core.data.ContactsDirectoryLoader
import com.bos.sphere.core.data.NoContacts
import com.bos.sphere.core.design.GrantKind
import com.bos.sphere.core.design.HyleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The few colours the groups UI needs; the settings pane and the Launchpad sheets each supply their own. */
internal class GColors(val surface: Color, val ink: Color, val ink2: Color, val dim: Color, val line: Color, val accent: Color)

internal fun hyleGColors() = GColors(HyleColors.Surface, HyleColors.InkBright, HyleColors.Ink, HyleColors.InkDim, HyleColors.Hairline, HyleColors.Violet)

@Composable
internal fun lpGColors(): GColors = LocalLp.current.let { GColors(it.surface, it.ink, it.ink2, it.dim, it.line, BlueLight) }

/** A pill button / chip at least 48dp high (INP-01), announced as a button (or a selected toggle) with its label. */
@Composable
internal fun GChip(c: GColors, label: String, selected: Boolean = false, description: String? = null, tag: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .background(if (selected) c.accent.copy(alpha = 0.22f) else Color.Transparent, shape)
            .border(1.dp, if (selected) c.accent.copy(alpha = 0.6f) else c.line, shape)
            .semantics { role = Role.Button; this.selected = selected; if (description != null) contentDescription = description }
            .then(if (tag != null) Modifier.androidTag(tag) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        Alignment.Center,
    ) { Text(label, color = if (selected) c.ink else c.ink2, fontSize = 13.sp) }
}

private fun Modifier.androidTag(tag: String) = this.testTag(tag)

@Composable
internal fun GField(c: GColors, value: String, onChange: (String) -> Unit, hint: String, label: String = hint, tag: String? = null, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.fillMaxWidth().heightIn(min = 48.dp).border(1.dp, c.line, shape).padding(horizontal = 12.dp, vertical = 12.dp),
        Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(hint, color = c.dim, fontSize = 14.sp)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = c.ink, fontSize = 14.sp), cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }.then(if (tag != null) Modifier.androidTag(tag) else Modifier),
        )
    }
}

/** Loads the optional contacts grant into an in-memory index once in a while; never required, never on the main thread. */
internal object ContactsRuntime {
    private val lock = Mutex()
    private var cached: Pair<Long, ContactDirectory>? = null
    private const val TTL_MS = 120_000L

    suspend fun get(ctx: android.content.Context, now: Long = System.currentTimeMillis()): ContactDirectory = lock.withLock {
        cached?.takeIf { now - it.first < TTL_MS }?.second
            ?: withContext(Dispatchers.IO) { ContactsDirectoryLoader.load(ctx) }.also { cached = now to it }
    }

    /** The last loaded index without loading anything (for the home-screen widget, which must not do I/O here). */
    fun peek(): ContactDirectory = cached?.second ?: NoContacts

    fun invalidate() { cached = null }
}

@Composable
internal fun rememberContactDirectory(): ContactDirectory {
    val ctx = LocalContext.current
    val granted = rememberGranted(GrantKind.Contacts)
    val dir by produceState<ContactDirectory>(NoContacts, granted) {
        value = if (granted) ContactsRuntime.get(ctx) else { ContactsRuntime.invalidate(); NoContacts }
    }
    return dir
}

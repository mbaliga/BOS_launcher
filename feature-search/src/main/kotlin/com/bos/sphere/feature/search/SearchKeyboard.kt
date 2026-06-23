package com.bos.sphere.feature.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.bos.sphere.core.data.AppEntry
import com.bos.sphere.core.design.HyleColors

private val KeyRows = listOf(
    "qwertyuiop".map { it.toString() },
    "asdfghjkl".map { it.toString() },
    listOf("⇧") + "zxcvbnm".map { it.toString() } + listOf("⌫"),
    listOf("123", "space", ".", "⏎"),
)

/**
 * Portrait always-open keyboard with type-to-search (brief §2.B). Filters apps live; Enter or a
 * unique match launches the top result, tapping a result launches it directly. Self-contained
 * query state — the host just supplies the inventory and an [onLaunch] sink.
 */
@Composable
fun SearchKeyboard(
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(apps, query) { AppSearch.rank(apps, query) }

    fun setQuery(q: String) { query = q }
    fun launchTop() {
        results.firstOrNull()?.let { onLaunch(it); query = "" }
    }
    fun onKey(k: String) {
        when (k) {
            "⌫" -> setQuery(query.dropLast(1))
            "space" -> setQuery("$query ")
            "⏎" -> launchTop()
            "⇧", "123" -> Unit
            else -> setQuery(query + k)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HyleColors.Background.copy(alpha = 0.92f))
            .safeContentPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchField(query)
        if (results.isNotEmpty()) ResultsRow(results, onLaunch = { onLaunch(it); query = "" })
        KeyGrid(onKey = ::onKey)
    }
}

@Composable
private fun SearchField(query: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .background(HyleColors.Surface.copy(alpha = 0.5f), RoundedCornerShape(13.dp))
            .border(1.dp, HyleColors.Violet.copy(alpha = 0.35f), RoundedCornerShape(13.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (query.isEmpty()) {
            Text("Search apps · type a command", color = HyleColors.InkDim, size = 16)
        } else {
            Text(query, color = HyleColors.InkBright, size = 16)
        }
    }
}

@Composable
private fun ResultsRow(results: List<AppEntry>, onLaunch: (AppEntry) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        results.forEach { app ->
            Column(
                modifier = Modifier
                    .width(64.dp)
                    .clickable { onLaunch(app) }
                    .background(HyleColors.Surface.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .border(1.dp, HyleColors.Hairline, RoundedCornerShape(12.dp))
                    .padding(vertical = 7.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val bmp = remember(app.key) {
                    app.loadIcon()?.let { runCatching { it.toBitmap().asImageBitmap() }.getOrNull() }
                }
                if (bmp != null) {
                    Image(bitmap = bmp, contentDescription = app.label, modifier = Modifier.size(26.dp))
                }
                Text(app.label, color = HyleColors.InkDim, size = 10, maxLines = 1)
            }
        }
    }
}

@Composable
private fun KeyGrid(onKey: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { key ->
                    val weight = when (key) {
                        "space" -> 5f
                        "⇧", "⌫", "123", "⏎", "." -> 1.5f
                        else -> 1f
                    }
                    Key(label = if (key == "space") "" else key, modifier = Modifier.weight(weight)) {
                        onKey(key)
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(42.dp)
            .clickable(onClick = onClick)
            .background(HyleColors.Surface.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
            .border(1.dp, HyleColors.Hairline, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = HyleColors.InkBright, size = 16)
    }
}

/** Small text helper so call sites read cleanly. */
@Composable
private fun Text(text: String, color: androidx.compose.ui.graphics.Color, size: Int, maxLines: Int = 1) {
    androidx.compose.material3.Text(
        text = text,
        color = color,
        fontSize = size.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

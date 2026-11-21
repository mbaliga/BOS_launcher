package com.bos.sphere.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bos.sphere.core.data.LaunchpadTheme

/** CFG-10, CFG-18: the Launchpad theme card of the settings rail, drawn from the same tokens Launchpad itself uses. */
@Composable
fun LaunchpadThemePreview(theme: LaunchpadTheme, modifier: Modifier = Modifier) {
    val looks = when (theme) {
        LaunchpadTheme.Dark -> listOf(LpDark)
        LaunchpadTheme.Light -> listOf(LpLight)
        LaunchpadTheme.System -> listOf(LpDark, LpLight)
    }
    Row(modifier.fillMaxSize()) {
        for (c in looks) {
            Column(Modifier.weight(1f).fillMaxSize().background(c.bg)) {
                Box(Modifier.fillMaxWidth().height(14.dp).background(BlueDark))
                repeat(3) {
                    Box(Modifier.padding(horizontal = 6.dp, vertical = 5.dp).fillMaxWidth().height(6.dp).background(c.dim, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}

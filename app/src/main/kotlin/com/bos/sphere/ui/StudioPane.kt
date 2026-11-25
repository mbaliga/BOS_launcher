package com.bos.sphere.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.core.design.L10n
import com.bos.sphere.feature.grid.ActionRow
import com.bos.sphere.feature.grid.Glyphs
import com.bos.sphere.feature.grid.SettingItem

/**
 * Wave 57 (CFG-43): Liftoff Studio (CFG-34) is the browser editor of a Liftoff setup: the same six sections on a computer, with a drawing of the
 * phone. It writes the files Settings > Backup and restore imports. The site is not published yet, so [URL] is a placeholder (the reserved
 * example.com host, so it can never open somebody else's page); the page says so.
 */
object LiftoffStudio {
    const val URL = "https://example.com/liftoff/studio/"
    const val PUBLISHED = false
    const val ABOUT = "Liftoff Studio is a web page that edits your Liftoff setup on a computer: the same six sections, with a drawing of the phone that follows what you change. Nothing you do there is sent anywhere; it saves a file. Bring that file here with Import a Studio file, under Export, import and restore."
    const val NOT_PUBLISHED = "Studio is not published yet: the address below is a placeholder."

    /** Opens the page in the browser (no network code in Liftoff: the browser does the rest). */
    fun intent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** The "Open Liftoff Studio" row of System > Backup and restore: what Studio is, its address and a button that opens it in the browser. */
fun studioSettingItem(): SettingItem = SettingItem.Pane(L10n.tr("Open Liftoff Studio"), Glyphs.Globe, L10n.tr("Edit your setup in a browser")) { StudioPane() }

@Composable
fun StudioPane() {
    val context = LocalContext.current
    Column {
        Text(L10n.tr(LiftoffStudio.ABOUT), color = HyleColors.Ink, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        if (!LiftoffStudio.PUBLISHED) Text(L10n.tr(LiftoffStudio.NOT_PUBLISHED), color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp).testTag("studio.placeholder"))
        Text(LiftoffStudio.URL, color = HyleColors.InkDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp).testTag("studio.url"))
        ActionRow(L10n.tr("Open in the browser"), LiftoffStudio.URL, Glyphs.Globe, flashId = "studio.open") { runCatching { context.startActivity(LiftoffStudio.intent()) } }
    }
}

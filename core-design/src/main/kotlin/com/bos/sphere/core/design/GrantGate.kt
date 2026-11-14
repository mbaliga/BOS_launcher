package com.bos.sphere.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The special accesses and permissions Liftoff asks for, each only when its feature is first touched (CFG-07). */
enum class GrantKind { Notifications, UsageAccess, Contacts, Folders, Microphone }

/** The plain-English text of one grant screen. Every field is an English key that goes through [L10n.tr]. */
data class GrantCopy(
    val title: String,
    /** What is read. */
    val reads: String,
    /** The promise that it stays on the device. */
    val stays: String,
    val button: String,
    /** Shown instead of nothing when the user has already been asked and it is still off. */
    val stillOff: String,
) {
    val all: List<String> get() = listOf(title, reads, stays, button, stillOff)
}

enum class GrantStage {
    /** Access is on: show the feature. */
    Granted,
    /** First touch: explain, then offer the button. */
    Explain,
    /** Asked before and still off: explain again and say so, so nothing looks broken. */
    ExplainStillOff,
}

object GrantGateLogic {
    const val NOT_NOW = "Not now"
    const val STAYS_ON_DEVICE = "Stays on this device. Nothing is sent anywhere."

    fun stage(granted: Boolean, askedBefore: Boolean): GrantStage = when {
        granted -> GrantStage.Granted
        askedBefore -> GrantStage.ExplainStillOff
        else -> GrantStage.Explain
    }

    /** Special accesses open a system settings page; runtime permissions show the system prompt. */
    fun opensSettingsPage(kind: GrantKind): Boolean = kind == GrantKind.Notifications || kind == GrantKind.UsageAccess

    fun copy(kind: GrantKind): GrantCopy = when (kind) {
        GrantKind.Notifications -> GrantCopy(
            "Show notifications here",
            "Reads the title, text and actions of your notifications, and which media is playing, so Launchpad and the media strip can show them.",
            STAYS_ON_DEVICE, "Allow notification access",
            "Notification access is still off, so Launchpad and the media strip stay empty.",
        )
        GrantKind.UsageAccess -> GrantCopy(
            "Show recent apps",
            "Reads which apps you opened recently, so the dock can show them.",
            STAYS_ON_DEVICE, "Allow usage access",
            "Usage access is still off, so recent apps are not shown.",
        )
        GrantKind.Contacts -> GrantCopy(
            "Find people",
            "Reads your contact names and numbers, so search can find people and offer call and message buttons.",
            STAYS_ON_DEVICE, "Allow contacts",
            "Contacts are still off, so search does not list people.",
        )
        GrantKind.Folders -> GrantCopy(
            "Search your files",
            "Reads the names of files in the folders you choose, so search can find them. Only folders you pick are read.",
            STAYS_ON_DEVICE, "Choose folders",
            "No folder is chosen yet, so file search is empty.",
        )
        GrantKind.Microphone -> GrantCopy(
            "Speak to search",
            "Listens while you hold the voice button, so you can search by speaking. Speech is turned into text on this device only.",
            STAYS_ON_DEVICE, "Allow microphone",
            "The microphone is still off, so voice search is unavailable.",
        )
    }

    /** Every English string this screen can show, for the translation check. */
    val allEnglish: List<String> get() = GrantKind.entries.flatMap { copy(it).all } + NOT_NOW
}

/** Colours for the grant screen; defaults follow the shared design tokens. */
data class GrantColors(
    val background: Color, val surface: Color, val ink: Color, val dim: Color, val accent: Color, val onAccent: Color,
) {
    companion object {
        @Composable
        fun fromTheme() = GrantColors(HyleColors.Background, HyleColors.Surface, HyleColors.InkBright, HyleColors.InkDim, HyleColors.Violet, HyleColors.OnAccent)
    }
}

/**
 * One reusable just-in-time grant screen (CFG-07, STR-05). When [granted] is true it shows [content]; otherwise
 * it replaces it with a one-screen explanation of what is read, the on-device promise and an enable button.
 */
@Composable
fun GrantGate(
    kind: GrantKind,
    granted: Boolean,
    askedBefore: Boolean,
    onEnable: () -> Unit,
    modifier: Modifier = Modifier,
    onNotNow: (() -> Unit)? = null,
    colors: GrantColors = GrantColors.fromTheme(),
    content: @Composable () -> Unit,
) {
    when (GrantGateLogic.stage(granted, askedBefore)) {
        GrantStage.Granted -> content()
        else -> GrantScreen(kind, askedBefore, onEnable, modifier, onNotNow, colors)
    }
}

@Composable
fun GrantScreen(
    kind: GrantKind,
    askedBefore: Boolean,
    onEnable: () -> Unit,
    modifier: Modifier = Modifier,
    onNotNow: (() -> Unit)? = null,
    colors: GrantColors = GrantColors.fromTheme(),
) {
    val c = GrantGateLogic.copy(kind)
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Short windows (landscape phones) drop the badge and tighten spacing so the whole explanation stays on one screen.
        val compact = maxHeight < 420.dp
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if (compact) 12.dp else 24.dp), Alignment.Center) {
            Column(
                Modifier.widthIn(max = 460.dp).clip(RoundedCornerShape(22.dp)).background(colors.surface).padding(if (compact) 16.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 14.dp),
            ) {
                if (!compact) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(colors.accent), Alignment.Center) {
                        Text(L10n.tr(c.title).take(1).uppercase(), color = colors.onAccent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(L10n.tr(c.title), color = colors.ink, fontSize = if (compact) 17.sp else 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(L10n.tr(c.reads), color = colors.ink, fontSize = if (compact) 13.sp else 15.sp, textAlign = TextAlign.Center)
                Text(L10n.tr(c.stays), color = colors.dim, fontSize = 13.sp, textAlign = TextAlign.Center)
                if (askedBefore) Text(L10n.tr(c.stillOff), color = colors.dim, fontSize = 13.sp, textAlign = TextAlign.Center)
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(26.dp)).background(colors.accent)
                        .semantics { role = Role.Button }.clickable(onClick = onEnable),
                    Alignment.Center,
                ) { Text(L10n.tr(c.button), color = colors.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) }
                if (onNotNow != null) {
                    Text(
                        L10n.tr(GrantGateLogic.NOT_NOW), color = colors.dim, fontSize = 14.sp,
                        modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onNotNow).padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

/** A one-line prompt for places too small for the full screen (the media strip); tapping it opens [GrantScreen]. */
@Composable
fun GrantChip(kind: GrantKind, onClick: () -> Unit, modifier: Modifier = Modifier, colors: GrantColors = GrantColors.fromTheme()) {
    val c = GrantGateLogic.copy(kind)
    val label = L10n.tr(c.button)
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(colors.surface).heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) { contentDescription = label; role = Role.Button }
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(colors.accent))
        Text(label, color = colors.ink, fontSize = 14.sp, maxLines = 1)
    }
}

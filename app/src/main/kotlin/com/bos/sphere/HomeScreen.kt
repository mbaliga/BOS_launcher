package com.bos.sphere

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.bos.sphere.core.data.LauncherAppsAppRepository
import com.bos.sphere.core.design.HyleColors
import com.bos.sphere.feature.sphere.Coverflow
import com.bos.sphere.feature.sphere.rememberSphereState

/**
 * The home surface. Builds the app inventory, picks the row split from orientation
 * (Auto = 2 rows portrait / 1 landscape, per §3 state model), renders the [Coverflow], and
 * surfaces a one-tap "set as default" affordance until the launcher owns the HOME role.
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val repository = remember { LauncherAppsAppRepository(context) }
    val apps by repository.apps.collectAsStateWithLifecycle(initialValue = emptyList())

    val configuration = LocalConfiguration.current
    val rows = if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 1

    val sphereState = rememberSphereState(itemCount = apps.size, rows = rows)

    // Centre the equator on the middle app the first time the inventory arrives.
    var centred by remember { mutableStateOf(false) }
    LaunchedEffect(apps.size) {
        if (!centred && apps.isNotEmpty()) {
            sphereState.position = (sphereState.maxIndex / 2).toFloat()
            centred = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HyleColors.Background.copy(alpha = 0.35f)), // subtle scrim over wallpaper
    ) {
        Coverflow(
            apps = apps,
            state = sphereState,
            onLaunch = { repository.launch(it) },
            modifier = Modifier.fillMaxSize(),
        )

        DefaultLauncherBanner(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(top = 12.dp),
        )
    }
}

/**
 * Shows a prompt to become the default home app while the launcher does not hold the role.
 * Re-checks each time the activity resumes (the user returns from the role dialog / settings).
 */
@Composable
private fun DefaultLauncherBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isDefault by remember { mutableStateOf(DefaultLauncher.isDefault(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefault = DefaultLauncher.isDefault(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (isDefault) return

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.set_default_launcher),
            color = HyleColors.InkBright,
        )
        Button(
            onClick = {
                val intent = DefaultLauncher.requestRoleIntent(context)
                    ?: DefaultLauncher.homeSettingsIntent()
                context.startActivity(intent)
            },
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = HyleColors.Violet,
                contentColor = HyleColors.Background,
            ),
        ) {
            Text(stringResource(R.string.set_default_cta))
        }
    }
}

package com.miferstlab.binauralbeats

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miferstlab.binauralbeats.data.AppearanceMode
import com.miferstlab.binauralbeats.data.BinauralMode
import com.miferstlab.binauralbeats.data.PlaybackState
import com.miferstlab.binauralbeats.ui.screens.HomeScreen
import com.miferstlab.binauralbeats.ui.screens.SettingsScreen
import com.miferstlab.binauralbeats.ui.theme.BinauralBeatsTheme
import com.miferstlab.binauralbeats.viewmodel.BinauralViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BinauralViewModel by viewModels()

    private var notificationPermissionAsked = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Optional: playback works without the notification shade action if denied.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (state.appearanceMode) {
                AppearanceMode.NIGHT -> true
                AppearanceMode.LIGHT -> false
                AppearanceMode.SYSTEM -> systemDark
            }

            BinauralBeatsTheme(darkTheme = darkTheme) {
                LaunchedEffect(state.keepScreenOn) {
                    if (state.keepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                BinauralApp(
                    state = state,
                    onModeSelected = viewModel::selectMode,
                    onPlayPause = {
                        ensureNotificationPermission()
                        viewModel.togglePlayPause()
                    },
                    onVolumeChange = viewModel::setVolume,
                    onCustomCarrier = viewModel::setCustomCarrier,
                    onCustomBeat = viewModel::setCustomBeat,
                    onMixChanged = viewModel::setMixWithOtherApps,
                    onKeepScreenOnChanged = viewModel::setKeepScreenOn,
                    onAppearanceChanged = viewModel::setAppearanceMode
                )
            }
        }
    }

    /**
     * Request POST_NOTIFICATIONS once (API 33+), preferably right before first play
     * so the system dialog has clear context. Avoids re-prompting every tap.
     */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted && !notificationPermissionAsked) {
            notificationPermissionAsked = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private enum class Screen { Home, Settings }

@Composable
private fun BinauralApp(
    state: PlaybackState,
    onModeSelected: (BinauralMode) -> Unit,
    onPlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onCustomCarrier: (Float) -> Unit,
    onCustomBeat: (Float) -> Unit,
    onMixChanged: (Boolean) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onAppearanceChanged: (AppearanceMode) -> Unit
) {
    // Survive configuration changes / process recreation of composition.
    var screen by rememberSaveable { mutableStateOf(Screen.Home.name) }
    val current = runCatching { Screen.valueOf(screen) }.getOrDefault(Screen.Home)

    when (current) {
        Screen.Home -> HomeScreen(
            state = state,
            onModeSelected = onModeSelected,
            onPlayPause = onPlayPause,
            onVolumeChange = onVolumeChange,
            onCustomCarrier = onCustomCarrier,
            onCustomBeat = onCustomBeat,
            onOpenSettings = { screen = Screen.Settings.name }
        )
        Screen.Settings -> SettingsScreen(
            state = state,
            onMixChanged = onMixChanged,
            onKeepScreenOnChanged = onKeepScreenOnChanged,
            onAppearanceChanged = onAppearanceChanged,
            onBack = { screen = Screen.Home.name }
        )
    }
}

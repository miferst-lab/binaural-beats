package com.miferstlab.binauralbeats.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.miferstlab.binauralbeats.R
import com.miferstlab.binauralbeats.data.AmbientSound
import com.miferstlab.binauralbeats.data.Entitlements
import com.miferstlab.binauralbeats.data.BinauralMode
import com.miferstlab.binauralbeats.data.FrequencyMath
import com.miferstlab.binauralbeats.data.PlaybackState
import com.miferstlab.binauralbeats.ui.components.BrainWaveVisual
import com.miferstlab.binauralbeats.ui.theme.ElectricViolet
import com.miferstlab.binauralbeats.ui.theme.GalaxyBackdrop
import com.miferstlab.binauralbeats.ui.theme.NebulaCyan
import com.miferstlab.binauralbeats.ui.theme.NebulaMagenta
import com.miferstlab.binauralbeats.ui.theme.SoftAmber
import com.miferstlab.binauralbeats.ui.theme.SoftEmber
import com.miferstlab.binauralbeats.ui.theme.SoftSky
import com.miferstlab.binauralbeats.ui.theme.SoftCoral
import com.miferstlab.binauralbeats.ui.theme.SoftLavender
import com.miferstlab.binauralbeats.ui.theme.SoftTeal
import com.miferstlab.binauralbeats.ui.theme.VoidBg

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: PlaybackState,
    onModeSelected: (BinauralMode) -> Unit,
    onPlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeNudge: (Float) -> Unit,
    onAmbientSelected: (AmbientSound) -> Unit,
    onAmbientVolumeChange: (Float) -> Unit,
    onAmbientVolumeNudge: (Float) -> Unit,
    onCustomCarrier: (Float) -> Unit,
    onCustomBeat: (Float) -> Unit,
    onOpenSettings: () -> Unit,
    onDismissFreeLimit: () -> Unit = {},
    onDismissPremiumUpsell: () -> Unit = {},
    onUpgradePremium: () -> Unit = {}
) {
    val playPauseLabel = stringResource(
        if (state.isPlaying) R.string.pause else R.string.play
    )
    val glassSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    val showGalaxy = MaterialTheme.colorScheme.background.luminance() < 0.3f

    Box(Modifier.fillMaxSize()) {
        if (showGalaxy) {
            GalaxyBackdrop(Modifier.fillMaxSize())
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.home_choose_mode),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BinauralMode.entries.forEach { mode ->
                        val selected = state.mode == mode
                        val accent = modeAccent(mode)
                        FilterChip(
                            selected = selected,
                            onClick = { onModeSelected(mode) },
                            label = { Text(stringResource(modeTitleRes(mode))) },
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) {
                                    accent.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                }
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.28f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                containerColor = glassSurface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = stringResource(modeDescriptionRes(state.mode)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )

                Spacer(Modifier.height(16.dp))

                BrainWaveVisual(
                    isPlaying = state.isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )

                Spacer(Modifier.height(8.dp))

                FloatingActionButton(
                    onClick = onPlayPause,
                    modifier = Modifier
                        .size(96.dp)
                        .semantics { contentDescription = playPauseLabel },
                    containerColor = NebulaCyan,
                    contentColor = VoidBg,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 12.dp
                    )
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = VoidBg
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = playPauseLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricViolet.copy(alpha = 0.9f)
                )

                if (!state.isPremium) {
                    val remaining = Entitlements.remainingFreeMs(state.sessionElapsedMs)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.free_session_remaining,
                            Entitlements.formatMmSs(remaining)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(20.dp))

                VolumeCard(
                    title = stringResource(R.string.volume),
                    volume = state.volume,
                    onVolumeChange = onVolumeChange,
                    onNudge = onVolumeNudge,
                    glassSurface = glassSurface,
                    accent = NebulaCyan,
                    decreaseCd = stringResource(R.string.volume_decrease),
                    increaseCd = stringResource(R.string.volume_increase)
                )

                Spacer(Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = glassSurface),
                    border = BorderStroke(1.dp, SoftTeal.copy(alpha = 0.28f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = SoftTeal
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.ambient_section),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AmbientSound.entries.forEach { ambient ->
                                val selected = state.ambient == ambient
                                val locked = ambient.isPremium && !state.isPremium
                                FilterChip(
                                    selected = selected,
                                    onClick = { onAmbientSelected(ambient) },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (locked) {
                                                Icon(
                                                    Icons.Default.Lock,
                                                    contentDescription = stringResource(R.string.ambient_locked_cd),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(stringResource(ambientLabelRes(ambient)))
                                        }
                                    },
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) {
                                            SoftTeal.copy(alpha = 0.85f)
                                        } else if (locked) {
                                            SoftAmber.copy(alpha = 0.55f)
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        }
                                    ),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SoftTeal.copy(alpha = 0.28f),
                                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        if (state.ambient != AmbientSound.OFF) {
                            Spacer(Modifier.height(12.dp))
                            VolumeSliderRow(
                                volume = state.ambientVolume,
                                onVolumeChange = onAmbientVolumeChange,
                                onNudge = onAmbientVolumeNudge,
                                accent = SoftTeal,
                                decreaseCd = stringResource(R.string.ambient_volume_decrease),
                                increaseCd = stringResource(R.string.ambient_volume_increase),
                                showTitle = true,
                                title = stringResource(R.string.ambient_volume)
                            )
                        }
                    }
                }

                if (state.mode == BinauralMode.NIESTANDARDOWY) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = glassSurface),
                        border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.22f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text(
                                text = stringResource(R.string.carrier_freq) +
                                    ": ${state.customCarrierHz.toInt()} Hz",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Slider(
                                value = state.customCarrierHz,
                                onValueChange = onCustomCarrier,
                                valueRange = FrequencyMath.CARRIER_MIN..FrequencyMath.CARRIER_MAX,
                                colors = SliderDefaults.colors(
                                    thumbColor = ElectricViolet,
                                    activeTrackColor = ElectricViolet.copy(alpha = 0.85f),
                                    inactiveTrackColor = NebulaCyan.copy(alpha = 0.2f)
                                )
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.beat_freq) +
                                    ": ${"%.1f".format(state.customBeatHz)} Hz",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Slider(
                                value = state.customBeatHz,
                                onValueChange = onCustomBeat,
                                valueRange = FrequencyMath.BEAT_MIN..FrequencyMath.BEAT_MAX,
                                colors = SliderDefaults.colors(
                                    thumbColor = NebulaMagenta,
                                    activeTrackColor = NebulaMagenta.copy(alpha = 0.85f),
                                    inactiveTrackColor = ElectricViolet.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = SoftTeal.copy(alpha = 0.14f)
                    ),
                    border = BorderStroke(1.dp, SoftTeal.copy(alpha = 0.4f))
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Headset,
                            contentDescription = null,
                            tint = SoftTeal,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.headphones_tip),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                val left = state.mode.leftHz(state.customCarrierHz, state.customBeatHz)
                val right = state.mode.rightHz(state.customCarrierHz, state.customBeatHz)
                val delta = FrequencyMath.beatFromEars(left, right)
                Text(
                    text = stringResource(
                        R.string.freq_summary,
                        "%.1f".format(left),
                        "%.1f".format(right),
                        "%.1f".format(delta)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))
            }
        }

        if (state.showFreeLimitDialog) {
            AlertDialog(
                onDismissRequest = onDismissFreeLimit,
                title = { Text(stringResource(R.string.free_limit_title)) },
                text = { Text(stringResource(R.string.free_limit_message)) },
                confirmButton = {
                    TextButton(onClick = onUpgradePremium) {
                        Text(stringResource(R.string.upgrade_premium))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissFreeLimit) {
                        Text(stringResource(R.string.free_limit_ok))
                    }
                }
            )
        }

        if (state.showPremiumUpsellDialog) {
            AlertDialog(
                onDismissRequest = onDismissPremiumUpsell,
                title = { Text(stringResource(R.string.premium_upsell_title)) },
                text = { Text(stringResource(R.string.premium_upsell_message)) },
                confirmButton = {
                    TextButton(onClick = onUpgradePremium) {
                        Text(stringResource(R.string.upgrade_premium))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissPremiumUpsell) {
                        Text(stringResource(R.string.premium_upsell_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun VolumeCard(
    title: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onNudge: (Float) -> Unit,
    glassSurface: Color,
    accent: Color,
    decreaseCd: String,
    increaseCd: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = glassSurface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = accent
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            VolumeSliderRow(
                volume = volume,
                onVolumeChange = onVolumeChange,
                onNudge = onNudge,
                accent = accent,
                decreaseCd = decreaseCd,
                increaseCd = increaseCd,
                showTitle = false,
                title = title
            )
        }
    }
}

@Composable
private fun VolumeSliderRow(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onNudge: (Float) -> Unit,
    accent: Color,
    decreaseCd: String,
    increaseCd: String,
    showTitle: Boolean,
    title: String
) {
    if (showTitle) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${(volume * 100).toInt()}%",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = { onNudge(-0.01f) },
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = decreaseCd }
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = null,
                tint = accent
            )
        }
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent.copy(alpha = 0.85f),
                inactiveTrackColor = ElectricViolet.copy(alpha = 0.25f)
            )
        )
        IconButton(
            onClick = { onNudge(0.01f) },
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = increaseCd }
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = accent
            )
        }
    }
}

private fun modeTitleRes(mode: BinauralMode): Int = when (mode) {
    BinauralMode.RELAKS -> R.string.mode_relax
    BinauralMode.SKUPIENIE -> R.string.mode_focus
    BinauralMode.CZYTANIE -> R.string.mode_reading
    BinauralMode.ENERGIA -> R.string.mode_energy
    BinauralMode.SEN -> R.string.mode_sleep
    BinauralMode.MEDYTACJA -> R.string.mode_meditation
    BinauralMode.NIESTANDARDOWY -> R.string.mode_custom
}

private fun modeDescriptionRes(mode: BinauralMode): Int = when (mode) {
    BinauralMode.RELAKS -> R.string.mode_relax_desc
    BinauralMode.SKUPIENIE -> R.string.mode_focus_desc
    BinauralMode.CZYTANIE -> R.string.mode_reading_desc
    BinauralMode.ENERGIA -> R.string.mode_energy_desc
    BinauralMode.SEN -> R.string.mode_sleep_desc
    BinauralMode.MEDYTACJA -> R.string.mode_meditation_desc
    BinauralMode.NIESTANDARDOWY -> R.string.mode_custom_desc
}

private fun modeAccent(mode: BinauralMode) = when (mode) {
    BinauralMode.RELAKS -> NebulaCyan
    BinauralMode.SKUPIENIE -> SoftAmber
    BinauralMode.CZYTANIE -> SoftSky
    BinauralMode.ENERGIA -> SoftEmber
    BinauralMode.SEN -> SoftLavender
    BinauralMode.MEDYTACJA -> SoftTeal
    BinauralMode.NIESTANDARDOWY -> SoftCoral
}

private fun ambientLabelRes(ambient: AmbientSound): Int = when (ambient) {
    AmbientSound.OFF -> R.string.ambient_off
    AmbientSound.FOREST_NIGHT -> R.string.ambient_forest_night
    AmbientSound.WAVES -> R.string.ambient_waves
    AmbientSound.MORNING_VILLAGE -> R.string.ambient_morning_village
    AmbientSound.RAIN -> R.string.ambient_rain
    AmbientSound.FIREPLACE -> R.string.ambient_fireplace
    AmbientSound.STREAM -> R.string.ambient_stream
    AmbientSound.MOUNTAIN_WIND -> R.string.ambient_mountain_wind
    AmbientSound.CAVE_DRIP -> R.string.ambient_cave_drip
    AmbientSound.SOFT_THUNDER -> R.string.ambient_soft_thunder
}

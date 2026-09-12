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
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
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
import com.miferstlab.binauralbeats.data.BinauralMode
import com.miferstlab.binauralbeats.data.FrequencyMath
import com.miferstlab.binauralbeats.data.PlaybackState
import com.miferstlab.binauralbeats.ui.components.BrainWaveVisual
import com.miferstlab.binauralbeats.ui.theme.ElectricViolet
import com.miferstlab.binauralbeats.ui.theme.GalaxyBackdrop
import com.miferstlab.binauralbeats.ui.theme.NebulaCyan
import com.miferstlab.binauralbeats.ui.theme.NebulaMagenta
import com.miferstlab.binauralbeats.ui.theme.SoftAmber
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
    onCustomCarrier: (Float) -> Unit,
    onCustomBeat: (Float) -> Unit,
    onOpenSettings: () -> Unit
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

                Spacer(Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = glassSurface),
                    border = BorderStroke(1.dp, NebulaCyan.copy(alpha = 0.18f))
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = NebulaCyan
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.volume),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Text(
                                text = "${(state.volume * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = state.volume,
                            onValueChange = onVolumeChange,
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = NebulaCyan,
                                activeTrackColor = NebulaCyan.copy(alpha = 0.85f),
                                inactiveTrackColor = ElectricViolet.copy(alpha = 0.25f)
                            )
                        )
                    }
                }

                if (state.mode == BinauralMode.NIESTANDARDOWY) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = glassSurface),
                        border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.22f))
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
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
    }
}

private fun modeTitleRes(mode: BinauralMode): Int = when (mode) {
    BinauralMode.RELAKS -> R.string.mode_relax
    BinauralMode.SKUPIENIE -> R.string.mode_focus
    BinauralMode.SEN -> R.string.mode_sleep
    BinauralMode.MEDYTACJA -> R.string.mode_meditation
    BinauralMode.NIESTANDARDOWY -> R.string.mode_custom
}

private fun modeDescriptionRes(mode: BinauralMode): Int = when (mode) {
    BinauralMode.RELAKS -> R.string.mode_relax_desc
    BinauralMode.SKUPIENIE -> R.string.mode_focus_desc
    BinauralMode.SEN -> R.string.mode_sleep_desc
    BinauralMode.MEDYTACJA -> R.string.mode_meditation_desc
    BinauralMode.NIESTANDARDOWY -> R.string.mode_custom_desc
}

private fun modeAccent(mode: BinauralMode) = when (mode) {
    BinauralMode.RELAKS -> NebulaCyan
    BinauralMode.SKUPIENIE -> SoftAmber
    BinauralMode.SEN -> SoftLavender
    BinauralMode.MEDYTACJA -> SoftTeal
    BinauralMode.NIESTANDARDOWY -> SoftCoral
}

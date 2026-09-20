package com.miferstlab.binauralbeats.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.miferstlab.binauralbeats.R
import com.miferstlab.binauralbeats.data.AppearanceMode
import com.miferstlab.binauralbeats.data.PlaybackState
import com.miferstlab.binauralbeats.ui.theme.ElectricViolet
import com.miferstlab.binauralbeats.ui.theme.GalaxyBackdrop
import com.miferstlab.binauralbeats.ui.theme.NebulaCyan

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: PlaybackState,
    onMixChanged: (Boolean) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onAppearanceChanged: (AppearanceMode) -> Unit,
    onBack: () -> Unit,
    onUpgradePremium: () -> Unit = {},
    onDebugUnlockChanged: (Boolean) -> Unit = {},
    isDebugUnlockEnabled: Boolean = false,
    onSetPremium: (Boolean) -> Unit = {}
) {
    val glassSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    val showGalaxy = MaterialTheme.colorScheme.background.luminance() < 0.3f

    Box(Modifier.fillMaxSize()) {
        if (showGalaxy) {
            GalaxyBackdrop(Modifier.fillMaxSize())
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_section_appearance),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = glassSurface),
                    border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.2f))
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text = stringResource(R.string.appearance_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppearanceChip(
                                label = stringResource(R.string.appearance_night),
                                selected = state.appearanceMode == AppearanceMode.NIGHT,
                                onClick = { onAppearanceChanged(AppearanceMode.NIGHT) }
                            )
                            AppearanceChip(
                                label = stringResource(R.string.appearance_system),
                                selected = state.appearanceMode == AppearanceMode.SYSTEM,
                                onClick = { onAppearanceChanged(AppearanceMode.SYSTEM) }
                            )
                            AppearanceChip(
                                label = stringResource(R.string.appearance_light),
                                selected = state.appearanceMode == AppearanceMode.LIGHT,
                                onClick = { onAppearanceChanged(AppearanceMode.LIGHT) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.settings_section_playback),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = glassSurface),
                    border = BorderStroke(1.dp, NebulaCyan.copy(alpha = 0.18f))
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.settings_mix_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.settings_mix_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = state.mixWithOtherApps,
                                onCheckedChange = onMixChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NebulaCyan,
                                    checkedTrackColor = NebulaCyan.copy(alpha = 0.45f)
                                )
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.settings_keep_screen_on),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.settings_keep_screen_on_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = state.keepScreenOn,
                                onCheckedChange = onKeepScreenOnChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NebulaCyan,
                                    checkedTrackColor = NebulaCyan.copy(alpha = 0.45f)
                                )
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = stringResource(R.string.settings_section_premium),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = glassSurface),
                    border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.22f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(
                                if (state.isPremium) R.string.settings_premium_status_on
                                else R.string.settings_premium_status_off
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_premium_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        if (state.isPremium) {
                            Text(
                                text = stringResource(R.string.settings_premium_active),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NebulaCyan
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { onSetPremium(false) }) {
                                Text(stringResource(R.string.settings_remove_premium_debug))
                            }
                        } else {
                            Button(onClick = onUpgradePremium) {
                                Text(stringResource(R.string.upgrade_premium))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.settings_debug_unlock),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.settings_debug_unlock_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = isDebugUnlockEnabled,
                                    onCheckedChange = onDebugUnlockChanged,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = NebulaCyan,
                                        checkedTrackColor = NebulaCyan.copy(alpha = 0.45f)
                                    )
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                Text(
                    text = stringResource(R.string.about),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = glassSurface),
                    border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.16f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.about_version, "1.3.0"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.about_disclaimer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AppearanceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                NebulaCyan.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            }
        ),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = NebulaCyan.copy(alpha = 0.28f),
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

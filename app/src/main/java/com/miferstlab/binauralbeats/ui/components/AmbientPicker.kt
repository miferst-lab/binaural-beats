package com.miferstlab.binauralbeats.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.miferstlab.binauralbeats.R
import com.miferstlab.binauralbeats.data.AmbientCategory
import com.miferstlab.binauralbeats.data.AmbientSound
import com.miferstlab.binauralbeats.ui.ambientCategoryRes
import com.miferstlab.binauralbeats.ui.ambientLabelRes

/**
 * Two-level ambient picker: a scrollable row of mood/use categories, then the
 * tracks of the selected category (plus "Off"). Locked premium tracks show a lock;
 * tapping them still calls [onAmbientSelected] so the ViewModel can show the upsell.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmbientPicker(
    selected: AmbientSound,
    hasFullAccess: Boolean,
    accent: Color,
    lockedAccent: Color,
    onAmbientSelected: (AmbientSound) -> Unit,
    modifier: Modifier = Modifier
) {
    var categoryKey by rememberSaveable {
        mutableStateOf((selected.category ?: AmbientCategory.NATURE).prefsValue)
    }
    val category = AmbientCategory.entries.firstOrNull { it.prefsValue == categoryKey }
        ?: AmbientCategory.NATURE

    Column(modifier) {
        Text(
            text = stringResource(
                R.string.ambient_library_summary,
                AmbientSound.tracks.size,
                AmbientSound.freeEntries.size
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            AmbientCategory.entries.forEach { cat ->
                val isCurrent = cat == category
                val hasSelection = selected.category == cat
                FilterChip(
                    selected = isCurrent,
                    onClick = { categoryKey = cat.prefsValue },
                    label = {
                        Text(
                            stringResource(ambientCategoryRes(cat)) + if (hasSelection) " •" else ""
                        )
                    },
                    border = BorderStroke(
                        width = if (isCurrent) 1.5.dp else 1.dp,
                        color = if (isCurrent) accent.copy(alpha = 0.85f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.22f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            (listOf(AmbientSound.OFF) + AmbientSound.inCategory(category)).forEach { ambient ->
                val isSelected = selected == ambient
                val locked = ambient.isPremium && !hasFullAccess
                FilterChip(
                    selected = isSelected,
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
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = when {
                            isSelected -> accent.copy(alpha = 0.85f)
                            locked -> lockedAccent.copy(alpha = 0.55f)
                            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        }
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.28f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        if (selected != AmbientSound.OFF && selected.category != category) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.ambient_now_playing,
                    stringResource(ambientLabelRes(selected))
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

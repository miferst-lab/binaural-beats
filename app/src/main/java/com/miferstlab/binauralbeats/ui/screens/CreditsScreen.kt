package com.miferstlab.binauralbeats.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.miferstlab.binauralbeats.R
import com.miferstlab.binauralbeats.data.AmbientCategory
import com.miferstlab.binauralbeats.data.AmbientSound
import com.miferstlab.binauralbeats.ui.ambientCategoryRes
import com.miferstlab.binauralbeats.ui.ambientLabelRes
import com.miferstlab.binauralbeats.ui.theme.ElectricViolet
import com.miferstlab.binauralbeats.ui.theme.GalaxyBackdrop
import com.miferstlab.binauralbeats.ui.theme.SoftTeal

/** In-app credits for every ambient recording (author, original title, license, source link). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val glassSurface = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    val showGalaxy = MaterialTheme.colorScheme.background.luminance() < 0.3f
    val uriHandler = LocalUriHandler.current

    Box(Modifier.fillMaxSize()) {
        if (showGalaxy) GalaxyBackdrop(Modifier.fillMaxSize())
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.credits_title)) },
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.credits_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp)
                    )
                }
                AmbientCategory.entries.forEach { category ->
                    item(key = "h_${category.prefsValue}") {
                        Text(
                            text = stringResource(ambientCategoryRes(category)),
                            style = MaterialTheme.typography.labelLarge,
                            color = SoftTeal,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp)
                        )
                    }
                    items(AmbientSound.inCategory(category), key = { it.prefsValue }) { sound ->
                        val name = stringResource(ambientLabelRes(sound))
                        val linkCd = stringResource(R.string.credits_open_link_cd, name)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .semantics { contentDescription = linkCd }
                                .clickable { runCatching { uriHandler.openUri(sound.sourceUrl) } },
                            colors = CardDefaults.cardColors(containerColor = glassSurface),
                            border = BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.16f))
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = stringResource(
                                        R.string.credits_item_source,
                                        sound.sourceTitle,
                                        sound.author
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.credits_item_license, sound.license),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

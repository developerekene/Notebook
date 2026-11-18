package com.example.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.NoteItem
import com.example.domain.model.ThemeMode
import com.example.ui.components.DashboardInsightsBanner
import com.example.ui.components.NoteCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    biometricLockEnabled: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectTag: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onOpenNote: (NoteItem) -> Unit,
    onTogglePin: (Long) -> Unit,
    onCreateNote: () -> Unit,
    onCreateVoiceNote: () -> Unit,
    onCycleTheme: () -> Unit,
    onLockVaultNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Notebook",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Offline encrypted workspace",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (biometricLockEnabled) {
                            IconButton(
                                onClick = onLockVaultNow,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("lock_vault_now_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Lock Vault",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = onCycleTheme,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("theme_cycle_btn")
                        ) {
                            Icon(
                                imageVector = if (uiState.themeMode == ThemeMode.LIGHT) {
                                    Icons.Rounded.LightMode
                                } else {
                                    Icons.Rounded.DarkMode
                                },
                                contentDescription = "Switch theme mode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item(key = "insights_banner") {
                DashboardInsightsBanner(insights = uiState.insights)
            }

            item(key = "search_bar") {
                OutlinedTextField(
                    value = uiState.filter.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search encrypted notes, categories, or #tags…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search notes"
                        )
                    },
                    trailingIcon = {
                        if (uiState.filter.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_search_input")
                )
            }

            item(key = "category_chips") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.filter.selectedCategory == null,
                            onClick = { onSelectCategory(null) },
                            label = { Text("All (${uiState.insights.totalNotes})") },
                            modifier = Modifier.testTag("cat_chip_all")
                        )
                    }
                    items(uiState.categories, key = { it.id }) { cat ->
                        val selected = uiState.filter.selectedCategory == cat.name
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectCategory(cat.name) },
                            label = { Text("${cat.name} (${cat.noteCount})") },
                            leadingIcon = {
                                Surface(
                                    color = Color(cat.colorHex),
                                    shape = CircleShape,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("cat_chip_${cat.name}")
                        )
                    }
                }
            }

            if (uiState.allTags.isNotEmpty()) {
                item(key = "tag_chips") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(uiState.allTags, key = { it }) { tag ->
                            val selected = uiState.filter.selectedTag == tag
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectTag(tag) },
                                label = { Text("#$tag") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Tag,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.testTag("tag_chip_$tag")
                            )
                        }
                    }
                }
            }

            val hasActiveFilters = uiState.filter.searchQuery.isNotBlank() ||
                uiState.filter.selectedCategory != null ||
                uiState.filter.selectedTag != null

            if (uiState.isLoaded && uiState.notes.isEmpty()) {
                item(key = "empty_state") {
                    EmptyVaultCard(
                        hasActiveFilters = hasActiveFilters,
                        onClearFilters = onClearFilters,
                        onCreateNote = onCreateNote,
                        onCreateVoiceNote = onCreateVoiceNote
                    )
                }
            } else {
                if (uiState.pinnedNotes.isNotEmpty()) {
                    item(key = "pinned_header") {
                        SectionLabel(title = "PINNED", count = uiState.pinnedNotes.size)
                    }
                    items(uiState.pinnedNotes, key = { "pin_${it.id}" }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { onOpenNote(note) },
                            onTogglePin = { onTogglePin(note.id) }
                        )
                    }
                }

                if (uiState.regularNotes.isNotEmpty()) {
                    item(key = "notes_header") {
                        SectionLabel(
                            title = if (uiState.pinnedNotes.isNotEmpty()) "RECENT NOTES" else "ALL NOTES",
                            count = uiState.regularNotes.size
                        )
                    }
                    items(uiState.regularNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { onOpenNote(note) },
                            onTogglePin = { onTogglePin(note.id) }
                        )
                    }
                }
            }
        }

        // Dual Action Floating Controls (Voice Capture + New Note)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(
                onClick = onCreateVoiceNote,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("fab_voice_note")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "New voice note"
                )
            }

            ExtendedFloatingActionButton(
                onClick = onCreateNote,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New Note") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_new_note")
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun EmptyVaultCard(
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    onCreateNote: () -> Unit,
    onCreateVoiceNote: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_vault),
                contentDescription = "Encrypted notebook empty vault artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp)
                    .clip(MaterialTheme.shapes.medium)
            )

            Text(
                text = if (hasActiveFilters) "No matching notes" else "Your Encrypted Vault is Ready",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasActiveFilters) {
                    "Try clearing your search or category filter to see all notes."
                } else {
                    "Every note is encrypted locally with AES-256-GCM before hitting Room storage. Dictate hands-free or write in Markdown."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            AnimatedVisibility(visible = hasActiveFilters) {
                TextButton(onClick = onClearFilters) {
                    Text("Clear Filters")
                }
            }

            if (!hasActiveFilters) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCreateVoiceNote,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("empty_voice_note_btn")
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Voice Note")
                    }
                    Button(
                        onClick = onCreateNote,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("empty_new_note_btn")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Create Note")
                    }
                }
            }
        }
    }
}

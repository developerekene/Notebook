package com.devekene.notebook.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devekene.notebook.R
import com.devekene.notebook.domain.model.NoteItem
import com.devekene.notebook.ui.components.DashboardInsightsBanner
import com.devekene.notebook.ui.components.NoteCard
import com.devekene.notebook.ui.theme.DarkBlue900

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
    onLockNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .testTag("dashboard_notes_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.EditNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Notebook",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Encrypted offline notes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (biometricLockEnabled) {
                        Surface(
                            onClick = onLockNow,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("lock_vault_now_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Lock App",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Lock",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
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
                    placeholder = { Text("Search notes, categories, or #tags…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search notes",
                            tint = MaterialTheme.colorScheme.primary
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
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, MaterialTheme.shapes.medium, ambientColor = DarkBlue900.copy(alpha = 0.06f))
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
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = uiState.filter.selectedCategory == null,
                                borderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
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
                                    color = if (selected) Color.White else Color(cat.colorHex),
                                    shape = CircleShape,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant
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
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                ),
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
                    EmptyNotesCard(
                        hasActiveFilters = hasActiveFilters,
                        onClearFilters = onClearFilters,
                        onCreateNote = onCreateNote
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

        // Single clean Create Note FAB
        ExtendedFloatingActionButton(
            onClick = onCreateNote,
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("New Note") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 14.dp)
                .testTag("fab_new_note")
        )
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
private fun EmptyNotesCard(
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    onCreateNote: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, MaterialTheme.shapes.large, ambientColor = DarkBlue900.copy(alpha = 0.06f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_notes_light),
                contentDescription = "Minimalist encrypted notebook artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp)
                    .clip(MaterialTheme.shapes.medium)
            )

            Text(
                text = if (hasActiveFilters) "No matching notes" else "Ready for Your First Note",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasActiveFilters) {
                    "Clear your search or category filter to view all notes."
                } else {
                    "Capture thoughts by typing or speaking directly in the editor — encrypted locally with AES-256-GCM."
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
                Button(
                    onClick = onCreateNote,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
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

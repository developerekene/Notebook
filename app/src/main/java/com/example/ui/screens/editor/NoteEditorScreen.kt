package com.example.ui.screens.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.export.NoteExporter
import com.example.core.voice.VoiceDictationController
import com.example.domain.model.Category
import com.example.ui.components.ExportNoteBottomSheet
import com.example.ui.components.VoiceDictationBottomSheet
import com.example.ui.theme.EmeraldVault
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    noteExporter: NoteExporter,
    startVoiceImmediately: Boolean,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showExportSheet by remember { mutableStateOf(false) }
    var showVoiceSheet by remember { mutableStateOf(startVoiceImmediately) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var tagDraft by remember { mutableStateOf("") }

    val voiceController = remember(context) { VoiceDictationController(context) }
    val voiceState by voiceController.state.collectAsStateWithLifecycle()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            voiceController.refreshAvailability()
            voiceController.startListening()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission is needed for voice-to-text.")
            }
        }
    }

    val saveMarkdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) viewModel.exportMarkdownToUri(uri)
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) viewModel.exportPdfToUri(uri)
    }

    DisposableEffect(Unit) {
        onDispose { voiceController.release() }
    }

    LaunchedEffect(startVoiceImmediately) {
        if (startVoiceImmediately && hasAudioPermission) {
            voiceController.startListening()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    fun saveAndExit() {
        if (uiState.title.isNotBlank() || uiState.content.isNotBlank()) {
            viewModel.saveNote { onNavigateBack() }
        } else {
            onNavigateBack()
        }
    }

    BackHandler { saveAndExit() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(
                        onClick = { saveAndExit() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("editor_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Save and go back")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = EmeraldVault,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${uiState.wordCount} words",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.togglePin() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("editor_pin_btn")
                    ) {
                        Icon(
                            imageVector = if (uiState.isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin note",
                            tint = if (uiState.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleLock() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("editor_lock_btn")
                    ) {
                        Icon(
                            imageVector = if (uiState.isLocked) Icons.Rounded.Lock else Icons.Outlined.LockOpen,
                            contentDescription = "Toggle biometric note lock",
                            tint = if (uiState.isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showExportSheet = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("editor_export_btn")
                    ) {
                        Icon(Icons.Rounded.IosShare, contentDescription = "Export note")
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("editor_delete_btn")
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete note")
                    }
                    IconButton(
                        onClick = { viewModel.saveNote() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("editor_save_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Save note",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            voiceController.clearSession()
                            showVoiceSheet = true
                            if (hasAudioPermission) {
                                voiceController.startListening()
                            } else {
                                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier.testTag("editor_voice_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Dictate", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    MarkdownInsertChip(label = "H2", onClick = { viewModel.appendMarkdownSnippet("## ") })
                    MarkdownInsertChip(label = "Bold", onClick = { viewModel.appendMarkdownSnippet("**bold**") })
                    MarkdownInsertChip(label = "List", onClick = { viewModel.appendMarkdownSnippet("- ") })
                    MarkdownInsertChip(label = "Task", onClick = { viewModel.appendMarkdownSnippet("- [ ] ") })
                    MarkdownInsertChip(label = "Code", onClick = { viewModel.appendMarkdownSnippet("`code`") })
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 780.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Category & Tag controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        FilterChip(
                            selected = true,
                            onClick = { showCategoryMenu = true },
                            label = { Text(uiState.categoryName) },
                            modifier = Modifier.testTag("editor_category_chip")
                        )
                        DropdownMenu(
                            expanded = showCategoryMenu,
                            onDismissRequest = { showCategoryMenu = false }
                        ) {
                            val list: List<Category> = categories.ifEmpty {
                                listOf(Category("cat_general", "General", 0xFF3B82F6L, "notes", true))
                            }
                            list.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        viewModel.selectCategory(cat.name)
                                        showCategoryMenu = false
                                    }
                                )
                            }
                        }
                    }

                    if (uiState.isLocked) {
                        Text(
                            text = "Biometric Locked",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Inline Tags
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uiState.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.removeTag(tag) },
                            label = { Text("#$tag") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove tag $tag",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tagDraft,
                        onValueChange = { tagDraft = it },
                        placeholder = { Text("Add #tag (e.g. architecture)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (tagDraft.isNotBlank()) {
                                    viewModel.addTag(tagDraft)
                                    tagDraft = ""
                                }
                            }
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_tag_input")
                    )
                    IconButton(
                        onClick = {
                            if (tagDraft.isNotBlank()) {
                                viewModel.addTag(tagDraft)
                                tagDraft = ""
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("editor_add_tag_btn")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add tag")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Title Input
                BasicTextField(
                    value = uiState.title,
                    onValueChange = viewModel::updateTitle,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (uiState.title.isEmpty()) {
                            Text(
                                text = "Note title",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                        innerTextField()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_title_input")
                )

                // Body Input
                BasicTextField(
                    value = uiState.content,
                    onValueChange = viewModel::updateContent,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (uiState.content.isEmpty()) {
                                Text(
                                    text = "Start writing or tap Dictate for voice-to-text…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 120.dp)
                        .testTag("editor_content_input")
                )
            }
        }
    }

    if (showVoiceSheet) {
        VoiceDictationBottomSheet(
            state = voiceState,
            hasAudioPermission = hasAudioPermission,
            onRequestPermission = { audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onStartListening = { voiceController.startListening() },
            onStopListening = { voiceController.stopListening() },
            onInsertTranscript = { transcript ->
                viewModel.appendVoiceTranscript(transcript, durationSeconds = 5)
            },
            onDismiss = { showVoiceSheet = false }
        )
    }

    if (showExportSheet) {
        val baseFileName = uiState.title.ifBlank { "notebook_note" }
            .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            .take(36)
        ExportNoteBottomSheet(
            noteTitle = uiState.title,
            onDismiss = { showExportSheet = false },
            onSaveMarkdown = { saveMarkdownLauncher.launch("$baseFileName.md") },
            onSavePdf = { savePdfLauncher.launch("$baseFileName.pdf") },
            onShareMarkdown = {
                scope.launch {
                    noteExporter.createShareIntent(viewModel.toSnapshotNoteItem(), isPdf = false)
                        .onSuccess { intent -> context.startActivity(android.content.Intent.createChooser(intent, "Share Markdown")) }
                        .onFailure { err -> snackbarHostState.showSnackbar("Share failed: ${err.localizedMessage}") }
                }
            },
            onSharePdf = {
                scope.launch {
                    noteExporter.createShareIntent(viewModel.toSnapshotNoteItem(), isPdf = true)
                        .onSuccess { intent -> context.startActivity(android.content.Intent.createChooser(intent, "Share PDF")) }
                        .onFailure { err -> snackbarHostState.showSnackbar("Share failed: ${err.localizedMessage}") }
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Note?") },
            text = { Text("This encrypted note will be permanently removed from your local vault.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteCurrentNote(onNavigateBack)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MarkdownInsertChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

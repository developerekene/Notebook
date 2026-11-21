package com.devekene.notebook.ui.screens.editor

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devekene.notebook.core.export.NoteExporter
import com.devekene.notebook.core.voice.VoiceDictationController
import com.devekene.notebook.domain.model.Category
import com.devekene.notebook.ui.components.ExportNoteBottomSheet
import com.devekene.notebook.ui.theme.EmeraldVault
import com.devekene.notebook.ui.theme.RoseAlert
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    noteExporter: NoteExporter,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showExportSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var tagDraft by remember { mutableStateOf("") }
    var lastConsumedChunkId by remember { mutableLongStateOf(0L) }
    var activeVoiceTarget by remember { mutableStateOf(VoiceTargetField.CONTENT) }

    val voiceController = remember(context) { VoiceDictationController(context) }
    val voiceState by voiceController.state.collectAsStateWithLifecycle()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val speechActivityFallbackLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotBlank()) {
                viewModel.insertVoiceTranscript(spoken, durationSeconds = 4, target = activeVoiceTarget)
            }
        }
    }

    fun startVoiceCapture(target: VoiceTargetField) {
        activeVoiceTarget = target
        val started = voiceController.startListening()
        if (!started) {
            try {
                speechActivityFallbackLauncher.launch(voiceController.buildRecognizerIntent())
            } catch (_: Exception) {
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        "No speech recognition service is installed on this device."
                    )
                }
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            voiceController.refreshAvailability()
            startVoiceCapture(activeVoiceTarget)
        } else {
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar("Microphone permission is needed to dictate notes.")
            }
        }
    }

    fun onMicButtonClick(target: VoiceTargetField) {
        if (voiceState.isListening && activeVoiceTarget == target) {
            voiceController.stopListening()
            return
        }
        activeVoiceTarget = target
        if (hasAudioPermission) {
            startVoiceCapture(target)
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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

    LaunchedEffect(voiceState.latestChunkId) {
        if (voiceState.latestChunkId > lastConsumedChunkId && voiceState.latestChunk.isNotBlank()) {
            lastConsumedChunkId = voiceState.latestChunkId
            viewModel.insertVoiceTranscript(
                transcript = voiceState.latestChunk,
                durationSeconds = voiceState.latestDurationSec,
                target = activeVoiceTarget
            )
        }
    }

    LaunchedEffect(voiceState.errorMessage) {
        val err = voiceState.errorMessage
        if (!err.isNullOrBlank()) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(err)
            voiceController.clearError()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    fun saveAndExit() {
        voiceController.stopListening()
        if (uiState.title.isNotBlank() || uiState.description.isNotBlank() || uiState.content.isNotBlank()) {
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Save and back")
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
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("editor_more_btn")
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More note options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (uiState.isLocked) "Remove Biometric Lock" else "Protect with Biometrics")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (uiState.isLocked) Icons.Rounded.Lock else Icons.Outlined.LockOpen,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.toggleLock()
                                },
                                modifier = Modifier.testTag("editor_lock_btn")
                            )
                            DropdownMenuItem(
                                text = { Text("Export Markdown / PDF") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.IosShare, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showExportSheet = true
                                },
                                modifier = Modifier.testTag("editor_export_btn")
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Note", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showDeleteConfirm = true
                                },
                                modifier = Modifier.testTag("editor_delete_btn")
                            )
                        }
                    }
                    Button(
                        onClick = { viewModel.saveNote() },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("editor_save_btn")
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MarkdownInsertChip(label = "H2", onClick = { viewModel.insertAtCursor("\n## ") })
                    MarkdownInsertChip(label = "Bold", onClick = { viewModel.insertAtCursor("**bold**", addSpaceIfNeeded = true) })
                    MarkdownInsertChip(label = "List", onClick = { viewModel.insertAtCursor("\n- ") })
                    MarkdownInsertChip(label = "Checklist", onClick = { viewModel.insertAtCursor("\n- [ ] ") })
                    MarkdownInsertChip(label = "Code", onClick = { viewModel.insertAtCursor("`code`", addSpaceIfNeeded = true) })
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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Category & Lock badge row
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
                                listOf(Category("cat_general", "General", 0xFF2563EBL, "notes", true))
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
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "Biometric Protected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Inline Tags
                if (uiState.tags.isNotEmpty()) {
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
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tagDraft,
                        onValueChange = { tagDraft = it },
                        placeholder = { Text("Add #tag (e.g. ideas)") },
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("editor_tag_input")
                    )
                    Surface(
                        onClick = {
                            if (tagDraft.isNotBlank()) {
                                viewModel.addTag(tagDraft)
                                tagDraft = ""
                            }
                        },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("editor_add_tag_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add tag",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                // Single Unified Writing Card with Mic Icons on Note Title, Description, and Note Body
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Note Title + Inline Mic Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BasicTextField(
                                value = uiState.titleField,
                                onValueChange = viewModel::updateTitleField,
                                textStyle = MaterialTheme.typography.headlineSmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (uiState.title.isEmpty()) {
                                        Text(
                                            text = "Note title",
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("editor_title_input")
                            )

                            InlineFieldMicButton(
                                isListeningHere = voiceState.isListening && activeVoiceTarget == VoiceTargetField.TITLE,
                                fieldLabel = "Note title",
                                testTag = "editor_title_voice_btn",
                                onClick = { onMicButtonClick(VoiceTargetField.TITLE) }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // 2. Description + Inline Mic Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BasicTextField(
                                value = uiState.descriptionField,
                                onValueChange = viewModel::updateDescriptionField,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    if (uiState.description.isEmpty()) {
                                        Text(
                                            text = "Short description or summary",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("editor_description_input")
                            )

                            InlineFieldMicButton(
                                isListeningHere = voiceState.isListening && activeVoiceTarget == VoiceTargetField.DESCRIPTION,
                                fieldLabel = "Description",
                                testTag = "editor_description_voice_btn",
                                onClick = { onMicButtonClick(VoiceTargetField.DESCRIPTION) }
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Inline live listening indicator for the active target field
                        AnimatedVisibility(visible = voiceState.isListening) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf(0.5f, 0.9f, 1f, 0.7f).forEachIndexed { idx, factor ->
                                            val h by animateFloatAsState(
                                                targetValue = 8f + 16f * (voiceState.rmsDb * factor).coerceIn(0.15f, 1f),
                                                animationSpec = tween(100),
                                                label = "inline_wave_$idx"
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(h.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                        }
                                    }
                                    Text(
                                        text = voiceState.partialTranscript.ifBlank {
                                            "Listening for ${activeVoiceTarget.label}… speak naturally"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // 3. Note Body ("the note itself") + Inline Mic Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BasicTextField(
                                value = uiState.contentField,
                                onValueChange = viewModel::updateContentField,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 240.dp)
                                    ) {
                                        if (uiState.content.isEmpty()) {
                                            Text(
                                                text = "Write your note here or tap the mic button to speak…",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("editor_content_input")
                            )

                            InlineFieldMicButton(
                                isListeningHere = voiceState.isListening && activeVoiceTarget == VoiceTargetField.CONTENT,
                                fieldLabel = "Note content",
                                testTag = "editor_voice_btn",
                                onClick = { onMicButtonClick(VoiceTargetField.CONTENT) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
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
            text = { Text("This encrypted note will be permanently removed from your device.") },
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
private fun InlineFieldMicButton(
    isListeningHere: Boolean,
    fieldLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isListeningHere) RoseAlert else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isListeningHere) Color.White else MaterialTheme.colorScheme.primary,
        border = if (isListeningHere) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isListeningHere) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = if (isListeningHere) "Stop voice input for $fieldLabel" else "Dictate $fieldLabel",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MarkdownInsertChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = CircleShape
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
        )
    }
}

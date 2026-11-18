package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.datastore.UserPreferences
import com.example.core.security.BiometricAvailability
import com.example.domain.model.NoteItem
import com.example.ui.navigation.DashboardRoute
import com.example.ui.navigation.InsightsRoute
import com.example.ui.navigation.NoteEditorRoute
import com.example.ui.navigation.VaultSettingsRoute
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.dashboard.DashboardViewModel
import com.example.ui.screens.editor.NoteEditorScreen
import com.example.ui.screens.editor.NoteEditorViewModel
import com.example.ui.screens.insights.InsightsScreen
import com.example.ui.screens.lock.VaultLockScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.settings.VaultSettingsScreen
import com.example.ui.theme.NotebookTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as NotebookApplication).container

        setContent {
            val prefs by container.preferencesRepository.preferencesFlow
                .collectAsStateWithLifecycle(initialValue = UserPreferences())

            var isVaultUnlocked by remember { mutableStateOf(true) }
            var initialLockChecked by remember { mutableStateOf(false) }
            var lockError by remember { mutableStateOf<String?>(null) }
            var pendingLockedNote by remember { mutableStateOf<NoteItem?>(null) }
            var pinPromptNote by remember { mutableStateOf<NoteItem?>(null) }

            val scope = rememberCoroutineScope()
            val lifecycleOwner = LocalLifecycleOwner.current

            LaunchedEffect(prefs.biometricLockEnabled) {
                if (!initialLockChecked) {
                    initialLockChecked = true
                    if (prefs.biometricLockEnabled) {
                        isVaultUnlocked = false
                    }
                }
            }

            DisposableEffect(lifecycleOwner, prefs.biometricLockEnabled, prefs.lockOnBackground) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP &&
                        prefs.biometricLockEnabled &&
                        prefs.lockOnBackground
                    ) {
                        isVaultUnlocked = false
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            NotebookTheme(themeMode = prefs.themeMode) {
                if (!isVaultUnlocked) {
                    val hasBiometrics = container.biometricLockManager.checkAvailability() ==
                        BiometricAvailability.AVAILABLE
                    VaultLockScreen(
                        hasBiometrics = hasBiometrics,
                        hasVaultPin = prefs.hasVaultPin,
                        errorMessage = lockError,
                        onRequestBiometricUnlock = {
                            lockError = null
                            container.biometricLockManager.authenticate(
                                activity = this,
                                onSuccess = {
                                    lockError = null
                                    isVaultUnlocked = true
                                },
                                onError = { err -> lockError = err }
                            )
                        },
                        onVerifyPin = { pin ->
                            scope.launch {
                                if (container.preferencesRepository.verifyVaultPin(pin)) {
                                    lockError = null
                                    isVaultUnlocked = true
                                } else {
                                    lockError = "Incorrect Vault PIN."
                                }
                            }
                        },
                        onBypassIfNoCredentials = { isVaultUnlocked = true }
                    )
                } else {
                    NotebookMainShell(
                        container = container,
                        biometricLockEnabled = prefs.biometricLockEnabled,
                        unlockedNoteId = pendingLockedNote?.id,
                        onConsumeUnlockedNote = { pendingLockedNote = null },
                        onLockVaultNow = {
                            lockError = null
                            isVaultUnlocked = false
                        },
                        onAuthenticateNote = { note, onGranted ->
                            val hasBiometrics = container.biometricLockManager.checkAvailability() ==
                                BiometricAvailability.AVAILABLE
                            if (hasBiometrics) {
                                container.biometricLockManager.authenticate(
                                    activity = this,
                                    title = "Unlock Protected Note",
                                    subtitle = "Verify identity to read '${note.title.ifBlank { "Untitled" }}'",
                                    onSuccess = onGranted,
                                    onError = {
                                        if (prefs.hasVaultPin) {
                                            pinPromptNote = note
                                        }
                                    }
                                )
                            } else if (prefs.hasVaultPin) {
                                pinPromptNote = note
                            } else {
                                onGranted()
                            }
                        }
                    )
                }

                // PIN dialog fallback for individual locked notes
                val lockedTarget = pinPromptNote
                if (lockedTarget != null) {
                    var pinInput by remember { mutableStateOf("") }
                    var pinErr by remember { mutableStateOf<String?>(null) }
                    AlertDialog(
                        onDismissRequest = { pinPromptNote = null },
                        title = { Text("Enter Vault PIN") },
                        text = {
                            androidx.compose.foundation.layout.Column {
                                Text("Authenticate to open '${lockedTarget.title.ifBlank { "Untitled" }}'")
                                OutlinedTextField(
                                    value = pinInput,
                                    onValueChange = { pinInput = it.filter(Char::isDigit) },
                                    label = { Text("PIN") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true
                                )
                                if (pinErr != null) {
                                    Text(pinErr!!, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                scope.launch {
                                    if (container.preferencesRepository.verifyVaultPin(pinInput)) {
                                        pinPromptNote = null
                                        pendingLockedNote = lockedTarget
                                    } else {
                                        pinErr = "Incorrect PIN"
                                    }
                                }
                            }) { Text("Unlock") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pinPromptNote = null }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}

private enum class TopNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    NOTES("Notes", Icons.Rounded.Description, Icons.Outlined.Description, "nav_notes"),
    INSIGHTS("Insights", Icons.Rounded.Analytics, Icons.Outlined.Analytics, "nav_insights"),
    VAULT("Vault", Icons.Rounded.Shield, Icons.Outlined.Shield, "nav_vault")
}

@Composable
private fun NotebookMainShell(
    container: AppContainer,
    biometricLockEnabled: Boolean,
    unlockedNoteId: Long?,
    onConsumeUnlockedNote: () -> Unit,
    onLockVaultNow: () -> Unit,
    onAuthenticateNote: (NoteItem, () -> Unit) -> Unit
) {
    val navController = rememberNavController()

    LaunchedEffect(unlockedNoteId) {
        if (unlockedNoteId != null && unlockedNoteId > 0L) {
            navController.navigate(NoteEditorRoute(noteId = unlockedNoteId, startVoice = false))
            onConsumeUnlockedNote()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val isTopLevelDestination = currentDestination?.let { dest ->
        dest.hasRoute<DashboardRoute>() ||
            dest.hasRoute<InsightsRoute>() ||
            dest.hasRoute<VaultSettingsRoute>()
    } ?: true

    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(container.noteRepository, container.preferencesRepository)
    )
    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()

    val insights14Day by container.noteRepository.observeInsights(daysWindow = 14)
        .collectAsStateWithLifecycle(initialValue = dashboardState.insights)

    fun navigateToTab(tab: TopNavTab) {
        val route: Any = when (tab) {
            TopNavTab.NOTES -> DashboardRoute
            TopNavTab.INSIGHTS -> InsightsRoute
            TopNavTab.VAULT -> VaultSettingsRoute
        }
        navController.navigate(route) {
            popUpTo(DashboardRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (isTopLevelDestination && !useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        TopNavTab.entries.forEach { tab ->
                            val selected = when (tab) {
                                TopNavTab.NOTES -> currentDestination?.hasRoute<DashboardRoute>() == true
                                TopNavTab.INSIGHTS -> currentDestination?.hasRoute<InsightsRoute>() == true
                                TopNavTab.VAULT -> currentDestination?.hasRoute<VaultSettingsRoute>() == true
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevelDestination && useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        TopNavTab.entries.forEach { tab ->
                            val selected = when (tab) {
                                TopNavTab.NOTES -> currentDestination?.hasRoute<DashboardRoute>() == true
                                TopNavTab.INSIGHTS -> currentDestination?.hasRoute<InsightsRoute>() == true
                                TopNavTab.VAULT -> currentDestination?.hasRoute<VaultSettingsRoute>() == true
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("${tab.testTag}_rail")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = DashboardRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<DashboardRoute> {
                        DashboardScreen(
                            uiState = dashboardState,
                            biometricLockEnabled = biometricLockEnabled,
                            onSearchQueryChange = dashboardViewModel::updateSearchQuery,
                            onSelectCategory = dashboardViewModel::selectCategory,
                            onSelectTag = dashboardViewModel::selectTag,
                            onClearFilters = dashboardViewModel::clearFilters,
                            onOpenNote = { note ->
                                if (note.isLocked) {
                                    onAuthenticateNote(note) {
                                        navController.navigate(NoteEditorRoute(noteId = note.id, startVoice = false))
                                    }
                                } else {
                                    navController.navigate(NoteEditorRoute(noteId = note.id, startVoice = false))
                                }
                            },
                            onTogglePin = dashboardViewModel::togglePinned,
                            onCreateNote = {
                                navController.navigate(NoteEditorRoute(noteId = 0L, startVoice = false))
                            },
                            onCreateVoiceNote = {
                                navController.navigate(NoteEditorRoute(noteId = 0L, startVoice = true))
                            },
                            onCycleTheme = dashboardViewModel::cycleThemeMode,
                            onLockVaultNow = onLockVaultNow
                        )
                    }

                    composable<InsightsRoute> {
                        InsightsScreen(
                            insights = insights14Day,
                            keyFingerprint = container.cryptoEngine.getKeyFingerprint(),
                            onTagSelected = { tag ->
                                dashboardViewModel.selectTag(tag)
                                navigateToTab(TopNavTab.NOTES)
                            }
                        )
                    }

                    composable<VaultSettingsRoute> {
                        val settingsViewModel: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.Factory(
                                preferencesRepository = container.preferencesRepository,
                                noteRepository = container.noteRepository,
                                cryptoEngine = container.cryptoEngine,
                                biometricLockManager = container.biometricLockManager,
                                noteExporter = container.noteExporter
                            )
                        )
                        VaultSettingsScreen(viewModel = settingsViewModel)
                    }

                    composable<NoteEditorRoute> { entry ->
                        val args = entry.toRoute<NoteEditorRoute>()
                        val editorViewModel: NoteEditorViewModel = viewModel(
                            key = "editor_${args.noteId}",
                            factory = NoteEditorViewModel.Factory(
                                noteId = args.noteId.takeIf { it > 0L },
                                noteRepository = container.noteRepository,
                                noteExporter = container.noteExporter
                            )
                        )
                        NoteEditorScreen(
                            viewModel = editorViewModel,
                            noteExporter = container.noteExporter,
                            startVoiceImmediately = args.startVoice,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

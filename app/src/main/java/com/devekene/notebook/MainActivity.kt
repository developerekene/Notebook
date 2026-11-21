package com.devekene.notebook

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.devekene.notebook.core.datastore.UserPreferences
import com.devekene.notebook.core.security.BiometricAvailability
import com.devekene.notebook.domain.model.NoteItem
import com.devekene.notebook.ui.navigation.DashboardRoute
import com.devekene.notebook.ui.navigation.InsightsRoute
import com.devekene.notebook.ui.navigation.NoteEditorRoute
import com.devekene.notebook.ui.navigation.SettingsRoute
import com.devekene.notebook.ui.screens.dashboard.DashboardScreen
import com.devekene.notebook.ui.screens.dashboard.DashboardViewModel
import com.devekene.notebook.ui.screens.editor.NoteEditorScreen
import com.devekene.notebook.ui.screens.editor.NoteEditorViewModel
import com.devekene.notebook.ui.screens.insights.InsightsScreen
import com.devekene.notebook.ui.screens.lock.VaultLockScreen
import com.devekene.notebook.ui.screens.settings.SettingsScreen
import com.devekene.notebook.ui.screens.settings.SettingsViewModel
import com.devekene.notebook.ui.theme.DarkBlue900
import com.devekene.notebook.ui.theme.NotebookTheme
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

            NotebookTheme {
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
                                    lockError = "Incorrect Passcode PIN."
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

                val lockedTarget = pinPromptNote
                if (lockedTarget != null) {
                    var pinInput by remember { mutableStateOf("") }
                    var pinErr by remember { mutableStateOf<String?>(null) }
                    AlertDialog(
                        onDismissRequest = { pinPromptNote = null },
                        title = { Text("Enter Passcode PIN") },
                        text = {
                            Column {
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
    INSIGHTS("Insights", Icons.Rounded.AutoGraph, Icons.Outlined.AutoGraph, "nav_insights"),
    SETTINGS("Settings", Icons.Rounded.Tune, Icons.Outlined.Tune, "nav_settings")
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
            navController.navigate(NoteEditorRoute(noteId = unlockedNoteId))
            onConsumeUnlockedNote()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val isTopLevelDestination = currentDestination?.let { dest ->
        dest.hasRoute<DashboardRoute>() ||
            dest.hasRoute<InsightsRoute>() ||
            dest.hasRoute<SettingsRoute>()
    } ?: true

    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(container.noteRepository)
    )
    val dashboardState by dashboardViewModel.uiState.collectAsStateWithLifecycle()

    val insights14Day by container.noteRepository.observeInsights(daysWindow = 14)
        .collectAsStateWithLifecycle(initialValue = dashboardState.insights)

    fun navigateToTab(tab: TopNavTab) {
        val route: Any = when (tab) {
            TopNavTab.NOTES -> DashboardRoute
            TopNavTab.INSIGHTS -> InsightsRoute
            TopNavTab.SETTINGS -> SettingsRoute
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
                    ModernFloatingBottomBar(
                        selectedTab = when {
                            currentDestination?.hasRoute<InsightsRoute>() == true -> TopNavTab.INSIGHTS
                            currentDestination?.hasRoute<SettingsRoute>() == true -> TopNavTab.SETTINGS
                            else -> TopNavTab.NOTES
                        },
                        onSelectTab = ::navigateToTab
                    )
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
                                TopNavTab.SETTINGS -> currentDestination?.hasRoute<SettingsRoute>() == true
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
                                        navController.navigate(NoteEditorRoute(noteId = note.id))
                                    }
                                } else {
                                    navController.navigate(NoteEditorRoute(noteId = note.id))
                                }
                            },
                            onTogglePin = dashboardViewModel::togglePinned,
                            onCreateNote = {
                                navController.navigate(NoteEditorRoute(noteId = 0L))
                            },
                            onLockNow = onLockVaultNow
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

                    composable<SettingsRoute> {
                        val settingsViewModel: SettingsViewModel = viewModel(
                            factory = SettingsViewModel.Factory(
                                preferencesRepository = container.preferencesRepository,
                                noteRepository = container.noteRepository,
                                cryptoEngine = container.cryptoEngine,
                                biometricLockManager = container.biometricLockManager,
                                noteExporter = container.noteExporter
                            )
                        )
                        SettingsScreen(viewModel = settingsViewModel)
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
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernFloatingBottomBar(
    selectedTab: TopNavTab,
    onSelectTab: (TopNavTab) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(30.dp),
                    ambientColor = DarkBlue900.copy(alpha = 0.14f),
                    spotColor = DarkBlue900.copy(alpha = 0.14f)
                ),
            shape = RoundedCornerShape(30.dp),
            color = Color.White,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopNavTab.entries.forEach { tab ->
                    val selected = tab == selectedTab
                    val pillColor by animateColorAsState(
                        targetValue = if (selected) DarkBlue900 else Color.Transparent,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "tab_bg_${tab.name}"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "tab_fg_${tab.name}"
                    )

                    Row(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(pillColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = { onSelectTab(tab) }
                            )
                            .padding(horizontal = if (selected) 18.dp else 14.dp, vertical = 10.dp)
                            .testTag(tab.testTag),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            tint = contentColor,
                            modifier = Modifier.size(21.dp)
                        )
                        AnimatedVisibility(visible = selected) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = contentColor
                            )
                        }
                        if (!selected) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

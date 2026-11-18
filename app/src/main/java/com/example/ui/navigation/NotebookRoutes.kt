package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

@Serializable
data object InsightsRoute

@Serializable
data object VaultSettingsRoute

@Serializable
data class NoteEditorRoute(
    val noteId: Long = 0L,
    val startVoice: Boolean = false
)

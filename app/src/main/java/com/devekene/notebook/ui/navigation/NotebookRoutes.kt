package com.devekene.notebook.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

@Serializable
data object InsightsRoute

@Serializable
data object SettingsRoute

@Serializable
data class NoteEditorRoute(
    val noteId: Long = 0L
)

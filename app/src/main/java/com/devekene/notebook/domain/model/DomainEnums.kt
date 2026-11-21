package com.devekene.notebook.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SyncState(val label: String) {
    LOCAL_ENCRYPTED("Encrypted Offline"),
    QUEUED_FOR_SYNC("Local Vault"),
    SYNCED("Synced"),
    CONFLICT("Needs Review")
}

@Serializable
enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    MARKDOWN("md", "text/markdown", "Markdown (.md)"),
    PDF("pdf", "application/pdf", "PDF Document (.pdf)"),
    ENCRYPTED_VAULT("nbvault", "application/octet-stream", "Encrypted Vault (.nbvault)")
}

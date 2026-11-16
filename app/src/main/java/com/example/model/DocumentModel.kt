package com.example.model

enum class DocumentStatus {
    READY,
    PROCESSING,
    FAILED
}

enum class DocumentSyncStatus {
    LOCAL_ONLY,
    UPLOADING,
    SYNCED,
    FAILED
}

enum class SenderType {
    USER,
    ASSISTANT,
    SYSTEM_NOTICE
}

data class DocumentSection(
    val pageNumber: Int,
    val title: String,
    val summary: String = ""
)

data class DocumentInfo(
    val id: String,
    val name: String,
    val uriString: String? = null,
    val fileSizeBytes: Long = 0L,
    val sizeFormatted: String = "Unknown size",
    val pageCount: Int = 1,
    val uploadedAt: String = "Just now",
    val status: DocumentStatus = DocumentStatus.READY,
    val description: String = "",
    val outlineSections: List<DocumentSection> = emptyList(),
    val syncStatus: DocumentSyncStatus = DocumentSyncStatus.LOCAL_ONLY,
    val syncErrorMessage: String? = null,
    val storagePath: String? = null
)

data class SourceCitation(
    val id: String,
    val pageNumber: Int,
    val sectionTitle: String,
    val snippetText: String,
    val relevanceScore: Int = 95
)

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val sender: SenderType,
    val content: String,
    val timestamp: String,
    val citations: List<SourceCitation> = emptyList(),
    val isPending: Boolean = false,
    val isError: Boolean = false,
    val retryQuery: String? = null
)

data class ChatSession(
    val id: String,
    val title: String,
    val documentName: String,
    val lastActive: String,
    val messageCount: Int
)

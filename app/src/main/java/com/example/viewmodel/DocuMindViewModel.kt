package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DocumentHelper
import com.example.data.SampleDocuments
import com.example.data.supabase.SupabaseApiClient
import com.example.data.supabase.SupabaseAuthManager
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.model.ChatMessageRecord
import com.example.data.supabase.model.ChatSessionRecord
import com.example.data.supabase.model.DocumentRecord
import com.example.data.supabase.model.SupabaseUser
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.DocumentInfo
import com.example.model.DocumentStatus
import com.example.model.DocumentSyncStatus
import com.example.model.SenderType
import com.example.model.SourceCitation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

import com.example.data.gemini.GeminiEmbeddingService
import com.example.data.gemini.GeminiQaService
import com.example.data.supabase.AuthDeepLinkParser
import com.example.data.supabase.DeepLinkParseResult
import com.example.data.supabase.model.CitationRecord
import com.example.data.supabase.model.DocumentChunkRecord
import com.example.ui.components.PasswordRecoveryState
import com.example.util.PdfTextExtractor
import com.example.util.ScannedPdfException
import com.example.util.TextChunker

data class DocuMindUiState(
    val activeDocument: DocumentInfo? = SampleDocuments.samplePdfs.first(),
    val sampleDocuments: List<DocumentInfo> = SampleDocuments.samplePdfs,
    val sessions: List<ChatSession> = emptyList(),
    val activeSessionId: String = "session-1",
    val messages: List<ChatMessage> = emptyList(),
    val isSidebarOpen: Boolean = false,
    val inputQuery: String = "",
    val selectedCitation: SourceCitation? = null,
    val isCitationInspectorOpen: Boolean = false,
    val isDocumentDetailsOpen: Boolean = false,
    val isShowcaseActive: Boolean = false,
    val notificationMessage: String? = null,
    // Supabase Integration State
    val currentUser: SupabaseUser? = null,
    val isAuthLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val isAuthDialogOpen: Boolean = false,
    val isConfigDialogOpen: Boolean = false,
    val isSupabaseConfigured: Boolean = SupabaseConfig.isConfigured(),
    val isRemoteSyncActive: Boolean = false,
    // RAG Indexing & Vector Search State
    val isIndexing: Boolean = false,
    val indexingProgress: String? = null,
    val indexedChunkCount: Int = 0,
    val isGeminiKeyMissing: Boolean = false,
    val isGeminiKeyDialogOpen: Boolean = false,
    // RAG Question-Answering State
    val isGeneratingAnswer: Boolean = false,
    val qaProgressStage: String? = null,
    val qaErrorMessage: String? = null,
    val pendingQuestionAfterKey: String? = null,
    // Password Recovery & Deep-Link Auth State
    val passwordRecoveryState: PasswordRecoveryState = PasswordRecoveryState.Inactive,
    val isForgotPasswordLoading: Boolean = false,
    val forgotPasswordSuccessMessage: String? = null,
    val forgotPasswordErrorMessage: String? = null
)

class DocuMindViewModel @JvmOverloads constructor(
    application: Application,
    private val authManager: SupabaseAuthManager = SupabaseAuthManager(application),
    private val apiClient: SupabaseApiClient = SupabaseApiClient(authManager),
    private val embeddingService: GeminiEmbeddingService = GeminiEmbeddingService(application),
    private val qaService: GeminiQaService = GeminiQaService(application)
) : AndroidViewModel(application) {

    private var activeDocumentBytes: ByteArray? = null
    private var activeDocumentBytesDocId: String? = null
    private var lastHandledDeepLinkUri: String? = null
    private var hadSessionBeforeRecovery: Boolean = false

    private val _uiState = MutableStateFlow(DocuMindUiState())
    val uiState: StateFlow<DocuMindUiState> = _uiState.asStateFlow()

    init {
        initializeDefaultSessions()
        observeAuthState()
    }

    private fun observeAuthState() {
        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                val inRecovery = _uiState.value.passwordRecoveryState !is PasswordRecoveryState.Inactive
                _uiState.update {
                    it.copy(
                        currentUser = user,
                        isRemoteSyncActive = user != null && SupabaseConfig.isConfigured()
                    )
                }
                if (!inRecovery && user != null && SupabaseConfig.isConfigured()) {
                    loadRemoteUserData(user.id)
                }
            }
        }
    }

    private fun loadRemoteUserData(userId: String) {
        viewModelScope.launch {
            // Fetch remote documents
            val docsResult = apiClient.fetchDocuments(userId)
            docsResult.onSuccess { remoteDocs ->
                if (remoteDocs.isNotEmpty()) {
                    val convertedDocs = remoteDocs.map { record ->
                        DocumentInfo(
                            id = record.id ?: UUID.randomUUID().toString(),
                            name = record.name,
                            uriString = record.filePath,
                            fileSizeBytes = record.sizeBytes,
                            sizeFormatted = record.sizeFormatted,
                            pageCount = record.pageCount,
                            uploadedAt = record.createdAt ?: "Recently",
                            status = DocumentStatus.READY,
                            syncStatus = DocumentSyncStatus.SYNCED,
                            storagePath = record.filePath,
                            description = record.description ?: "Synced from Supabase Storage"
                        )
                    }
                    val firstDoc = convertedDocs.firstOrNull()
                    if (firstDoc != null && activeDocumentBytesDocId != firstDoc.id) {
                        activeDocumentBytes = null
                        activeDocumentBytesDocId = null
                    }
                    val chunkCount = firstDoc?.let { apiClient.fetchDocumentChunksCount(it.id).getOrNull() } ?: 0
                    _uiState.update { state ->
                        state.copy(
                            activeDocument = firstDoc ?: state.activeDocument,
                            indexedChunkCount = chunkCount,
                            notificationMessage = "Synced ${convertedDocs.size} documents from Supabase"
                        )
                    }
                }
            }

            // Fetch remote sessions
            val sessionsResult = apiClient.fetchChatSessions(userId)
            sessionsResult.onSuccess { remoteSessions ->
                if (remoteSessions.isNotEmpty()) {
                    val convertedSessions = remoteSessions.map { record ->
                        ChatSession(
                            id = record.id ?: UUID.randomUUID().toString(),
                            title = record.title,
                            documentName = record.documentName ?: "Document",
                            lastActive = record.createdAt ?: "Recently",
                            messageCount = 1
                        )
                    }
                    _uiState.update { state ->
                        state.copy(
                            sessions = convertedSessions,
                            activeSessionId = convertedSessions.first().id
                        )
                    }
                    // Load messages for active session
                    loadRemoteMessages(convertedSessions.first().id)
                }
            }
        }
    }

    private fun loadRemoteMessages(sessionId: String) {
        viewModelScope.launch {
            val result = apiClient.fetchChatMessages(sessionId)
            result.onSuccess { remoteMessages ->
                if (remoteMessages.isNotEmpty()) {
                    val converted = remoteMessages.map { r ->
                        ChatMessage(
                            id = r.id ?: UUID.randomUUID().toString(),
                            sessionId = r.sessionId,
                            sender = when (r.sender) {
                                "USER" -> SenderType.USER
                                "ASSISTANT" -> SenderType.ASSISTANT
                                else -> SenderType.SYSTEM_NOTICE
                            },
                            content = r.content,
                            timestamp = r.createdAt?.takeLast(8) ?: currentTimeString(),
                            citations = r.citations?.map { c ->
                                SourceCitation(
                                    id = c.id,
                                    pageNumber = c.pageNumber,
                                    sectionTitle = c.sectionTitle,
                                    snippetText = c.snippetText,
                                    relevanceScore = c.relevanceScore
                                )
                            } ?: emptyList()
                        )
                    }
                    _uiState.update { it.copy(messages = converted) }
                }
            }
        }
    }

    private fun initializeDefaultSessions() {
        val initialDoc = SampleDocuments.samplePdfs.first()
        val defaultSessions = listOf(
            ChatSession(
                id = "session-1",
                title = "Risk Classification & Sovereign Enclaves",
                documentName = initialDoc.name,
                lastActive = "Just now",
                messageCount = 2
            ),
            ChatSession(
                id = "session-2",
                title = "Q4 Cloud Infrastructure & Margins",
                documentName = "Q4_Enterprise_Financial_Report.pdf",
                lastActive = "2 hours ago",
                messageCount = 4
            ),
            ChatSession(
                id = "session-3",
                title = "Grid Decarbonization Milestones",
                documentName = "Clean_Energy_Transition_Strategy.pdf",
                lastActive = "Oct 28",
                messageCount = 1
            )
        )

        val initialMessages = listOf(
            ChatMessage(
                id = "msg-welcome",
                sessionId = "session-1",
                sender = SenderType.SYSTEM_NOTICE,
                content = "DocuMind Document Workspace initialized. '${initialDoc.name}' (${initialDoc.pageCount} pages, ${initialDoc.sizeFormatted}) is ready.",
                timestamp = "09:15 AM"
            )
        )

        _uiState.update {
            it.copy(
                sessions = defaultSessions,
                activeSessionId = "session-1",
                messages = initialMessages
            )
        }
    }

    fun onDocumentPickCancelled() {
        _uiState.update { it.copy(notificationMessage = "No PDF file was selected.") }
    }

    fun onDocumentPicked(context: Context, uri: Uri) {
        val docInfo = try {
            DocumentHelper.parseUriToDocumentInfo(context, uri)
        } catch (e: Exception) {
            _uiState.update { it.copy(notificationMessage = "Failed to parse document: ${e.message}") }
            return
        }

        // Validate stream readability
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            _uiState.update { it.copy(notificationMessage = "Failed to read file from ContentResolver: ${e.message}") }
            null
        }

        if (bytes == null || bytes.isEmpty()) {
            _uiState.update { it.copy(notificationMessage = "Selected PDF file is empty or cannot be opened.") }
            return
        }

        // Verify standard %PDF header
        val isPdfHeader = bytes.size >= 4 &&
                bytes[0] == 0x25.toByte() && // '%'
                bytes[1] == 0x50.toByte() && // 'P'
                bytes[2] == 0x44.toByte() && // 'D'
                bytes[3] == 0x46.toByte()    // 'F'

        if (!isPdfHeader && !docInfo.name.endsWith(".pdf", ignoreCase = true)) {
            _uiState.update { it.copy(notificationMessage = "Selected file '${docInfo.name}' is not a valid PDF document.") }
            return
        }

        activeDocumentBytes = bytes
        activeDocumentBytesDocId = docInfo.id

        val newSessionId = UUID.randomUUID().toString()
        val newSession = ChatSession(
            id = newSessionId,
            title = "Analysis: ${docInfo.name.take(24)}",
            documentName = docInfo.name,
            lastActive = "Just now",
            messageCount = 1
        )

        val welcomeMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = newSessionId,
            sender = SenderType.SYSTEM_NOTICE,
            content = "Loaded '${docInfo.name}' (${docInfo.pageCount} pages, ${docInfo.sizeFormatted}). Ready for analysis.",
            timestamp = currentTimeString()
        )

        if (authManager.hasValidSession()) {
            val pendingDoc = docInfo.copy(syncStatus = DocumentSyncStatus.UPLOADING)
            _uiState.update { state ->
                state.copy(
                    activeDocument = pendingDoc,
                    indexedChunkCount = 0,
                    qaErrorMessage = null,
                    sessions = listOf(newSession) + state.sessions,
                    activeSessionId = newSessionId,
                    messages = listOf(welcomeMsg),
                    notificationMessage = "Uploading '${docInfo.name}' to Supabase Storage..."
                )
            }
            uploadDocumentInternal(pendingDoc, bytes, newSessionId)
        } else {
            val localDoc = docInfo.copy(syncStatus = DocumentSyncStatus.LOCAL_ONLY)
            _uiState.update { state ->
                state.copy(
                    activeDocument = localDoc,
                    indexedChunkCount = 0,
                    qaErrorMessage = null,
                    sessions = listOf(newSession) + state.sessions,
                    activeSessionId = newSessionId,
                    messages = listOf(welcomeMsg),
                    notificationMessage = "PDF loaded locally. Tap 'Sync to Cloud' or sign in to upload to Supabase Storage."
                )
            }
        }
    }

    fun uploadActiveDocumentToSupabase() {
        val doc = _uiState.value.activeDocument ?: run {
            _uiState.update { it.copy(notificationMessage = "No active document to upload.") }
            return
        }

        if (!authManager.hasValidSession()) {
            _uiState.update {
                it.copy(
                    isAuthDialogOpen = true,
                    authErrorMessage = "Please sign in to your Supabase account to upload documents to private storage."
                )
            }
            return
        }

        val bytes = activeDocumentBytes?.takeIf { activeDocumentBytesDocId == doc.id } ?: try {
            doc.uriString?.let { uriStr ->
                getApplication<Application>().contentResolver.openInputStream(Uri.parse(uriStr))?.use { it.readBytes() }
            }
        } catch (_: Exception) {
            null
        }

        if (bytes == null || bytes.isEmpty()) {
            _uiState.update { state ->
                state.copy(
                    activeDocument = doc.copy(
                        syncStatus = DocumentSyncStatus.FAILED,
                        syncErrorMessage = "PDF content is not in memory. Please select the file again."
                    ),
                    notificationMessage = "Cannot access PDF content. Please re-select the file."
                )
            }
            return
        }

        val uploadingDoc = doc.copy(syncStatus = DocumentSyncStatus.UPLOADING, syncErrorMessage = null)
        _uiState.update { state ->
            state.copy(
                activeDocument = uploadingDoc,
                notificationMessage = "Uploading '${doc.name}' to Supabase Storage..."
            )
        }

        uploadDocumentInternal(uploadingDoc, bytes, _uiState.value.activeSessionId)
    }

    private fun uploadDocumentInternal(doc: DocumentInfo, bytes: ByteArray, sessionId: String) {
        val user = _uiState.value.currentUser ?: run {
            _uiState.update { state ->
                state.copy(
                    activeDocument = doc.copy(
                        syncStatus = DocumentSyncStatus.FAILED,
                        syncErrorMessage = "User authentication is missing."
                    ),
                    notificationMessage = "Upload error: User session not found."
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                val uploadResult = apiClient.uploadPdfFile(user.id, doc.id, bytes)
                uploadResult.onSuccess { storagePath ->
                    val record = DocumentRecord(
                        id = doc.id,
                        userId = user.id,
                        name = doc.name,
                        filePath = storagePath,
                        sizeBytes = doc.fileSizeBytes,
                        sizeFormatted = doc.sizeFormatted,
                        pageCount = doc.pageCount,
                        status = "ready",
                        description = "Uploaded to Supabase private storage"
                    )
                    val insertResult = apiClient.insertDocument(record)
                    insertResult.onSuccess { createdDoc ->
                        // Save session to Supabase
                        apiClient.createChatSession(
                            ChatSessionRecord(
                                id = sessionId,
                                userId = user.id,
                                documentId = createdDoc.id,
                                title = "Analysis: ${doc.name.take(24)}",
                                documentName = doc.name
                            )
                        )
                        _uiState.update { state ->
                            state.copy(
                                activeDocument = doc.copy(
                                    syncStatus = DocumentSyncStatus.SYNCED,
                                    storagePath = storagePath,
                                    syncErrorMessage = null
                                ),
                                notificationMessage = "PDF stored in Supabase Storage. Starting text extraction and RAG indexing..."
                            )
                        }
                        // Trigger real PDF text extraction and RAG indexing pipeline
                        executeDocumentIndexing(doc.copy(storagePath = storagePath), bytes, sessionId)
                    }.onFailure { err ->
                        _uiState.update { state ->
                            state.copy(
                                activeDocument = doc.copy(
                                    syncStatus = DocumentSyncStatus.FAILED,
                                    storagePath = storagePath,
                                    syncErrorMessage = "Metadata error: ${err.message}"
                                ),
                                notificationMessage = "Storage upload succeeded, but database metadata error: ${err.message}"
                            )
                        }
                    }
                }.onFailure { err ->
                    _uiState.update { state ->
                        state.copy(
                            activeDocument = doc.copy(
                                syncStatus = DocumentSyncStatus.FAILED,
                                syncErrorMessage = err.message
                            ),
                            notificationMessage = "Supabase Storage error: ${err.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        activeDocument = doc.copy(
                            syncStatus = DocumentSyncStatus.FAILED,
                            syncErrorMessage = e.message
                        ),
                        notificationMessage = "Upload failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun selectSampleDocument(doc: DocumentInfo) {
        activeDocumentBytes = null
        activeDocumentBytesDocId = null
        val newSessionId = UUID.randomUUID().toString()
        val newSession = ChatSession(
            id = newSessionId,
            title = "Analysis: ${doc.name.take(24)}",
            documentName = doc.name,
            lastActive = "Just now",
            messageCount = 1
        )

        val welcomeMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = newSessionId,
            sender = SenderType.SYSTEM_NOTICE,
            content = "Selected '${doc.name}' (${doc.pageCount} pages, ${doc.sizeFormatted}). Document parsed and ready for questions.",
            timestamp = currentTimeString()
        )

        _uiState.update { state ->
            state.copy(
                activeDocument = doc,
                indexedChunkCount = 0,
                qaErrorMessage = null,
                sessions = listOf(newSession) + state.sessions,
                activeSessionId = newSessionId,
                messages = listOf(welcomeMsg),
                notificationMessage = "Switched to ${doc.name}"
            )
        }
    }

    fun clearActiveDocument() {
        activeDocumentBytes = null
        activeDocumentBytesDocId = null
        val clearMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = _uiState.value.activeSessionId,
            sender = SenderType.SYSTEM_NOTICE,
            content = "Document unloaded. Please upload or select a PDF to begin document Q&A.",
            timestamp = currentTimeString()
        )
        _uiState.update { state ->
            state.copy(
                activeDocument = null,
                indexedChunkCount = 0,
                qaErrorMessage = null,
                messages = state.messages + clearMsg,
                notificationMessage = "Document cleared"
            )
        }
    }

    fun updateInputQuery(query: String) {
        _uiState.update { it.copy(inputQuery = query) }
    }

    fun dismissQaError() {
        _uiState.update { it.copy(qaErrorMessage = null) }
    }

    fun sendQuestion(customText: String? = null) {
        val text = (customText ?: _uiState.value.inputQuery).trim()
        if (text.isEmpty() || _uiState.value.isGeneratingAnswer) return

        val state = _uiState.value
        val activeDoc = state.activeDocument
        val timestamp = currentTimeString()
        val user = state.currentUser
        val context = getApplication<Application>()

        // Ensure activeSessionId is a valid UUID when working with Supabase
        val resolvedSessionId = try {
            UUID.fromString(state.activeSessionId)
            state.activeSessionId
        } catch (_: IllegalArgumentException) {
            UUID.randomUUID().toString()
        }

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = resolvedSessionId,
            sender = SenderType.USER,
            content = text,
            timestamp = timestamp
        )

        // Precondition 1: No document selected
        if (activeDoc == null) {
            val noDocNotice = ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = resolvedSessionId,
                sender = SenderType.SYSTEM_NOTICE,
                content = "Please upload or select a PDF document first so questions can be grounded in indexed pages.",
                timestamp = timestamp
            )
            _uiState.update {
                it.copy(
                    activeSessionId = resolvedSessionId,
                    messages = it.messages + userMessage + noDocNotice,
                    inputQuery = "",
                    qaErrorMessage = null
                )
            }
            return
        }

        // Precondition 2: Offline sample placeholder selected (not stored in Supabase document_chunks)
        if (activeDoc.id.startsWith("doc-sample-")) {
            val sampleNotice = ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = resolvedSessionId,
                sender = SenderType.SYSTEM_NOTICE,
                content = "'${activeDoc.name}' is a built-in preview sample and is not indexed in your Supabase vector database. Upload a real PDF document from your device to run semantic search and get grounded answers with page citations.",
                timestamp = timestamp
            )
            _uiState.update {
                it.copy(
                    activeSessionId = resolvedSessionId,
                    messages = it.messages + userMessage + sampleNotice,
                    inputQuery = "",
                    qaErrorMessage = null
                )
            }
            return
        }

        // Precondition 3: User must be authenticated with Supabase to call protected match_document_chunks RPC
        if (user == null || !authManager.hasValidSession()) {
            val authErrorMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = resolvedSessionId,
                sender = SenderType.ASSISTANT,
                content = "Sign in to your Supabase account to search indexed chunks for '${activeDoc.name}' and generate grounded answers.",
                timestamp = timestamp,
                isError = true,
                retryQuery = text
            )
            _uiState.update {
                it.copy(
                    activeSessionId = resolvedSessionId,
                    messages = it.messages + userMessage + authErrorMsg,
                    inputQuery = "",
                    isAuthDialogOpen = true,
                    authErrorMessage = "Authentication is required to query document chunks."
                )
            }
            return
        }

        // Precondition 4: Gemini API key must be configured for query embedding + grounded synthesis
        if (!GeminiEmbeddingService.isConfigured(context)) {
            _uiState.update {
                it.copy(
                    pendingQuestionAfterKey = text,
                    isGeminiKeyMissing = true,
                    isGeminiKeyDialogOpen = true,
                    notificationMessage = "Gemini API key is required to embed your question and generate an answer."
                )
            }
            return
        }

        // Enter loading state with pending assistant message bubble
        val pendingMsgId = UUID.randomUUID().toString()
        val loadingMsg = ChatMessage(
            id = pendingMsgId,
            sessionId = resolvedSessionId,
            sender = SenderType.ASSISTANT,
            content = "Generating 768-dim query embedding...",
            timestamp = timestamp,
            isPending = true
        )

        _uiState.update { current ->
            val updatedSessions = current.sessions.map { s ->
                if (s.id == state.activeSessionId) s.copy(id = resolvedSessionId, lastActive = "Just now") else s
            }
            current.copy(
                activeSessionId = resolvedSessionId,
                sessions = updatedSessions,
                messages = current.messages + userMessage + loadingMsg,
                inputQuery = "",
                isGeneratingAnswer = true,
                qaProgressStage = "Generating 768-dim query embedding...",
                qaErrorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                // Ensure chat_session row exists in Supabase before inserting messages (satisfies RLS)
                if (SupabaseConfig.isConfigured()) {
                    apiClient.createChatSession(
                        ChatSessionRecord(
                            id = resolvedSessionId,
                            userId = user.id,
                            documentId = activeDoc.id,
                            title = "Analysis: ${activeDoc.name.take(24)}",
                            documentName = activeDoc.name
                        )
                    )
                    apiClient.saveChatMessage(
                        ChatMessageRecord(
                            sessionId = resolvedSessionId,
                            userId = user.id,
                            sender = "USER",
                            content = text
                        )
                    )
                }

                // Step 1: Embed user question into L2-normalized 768-dimensional vector (RETRIEVAL_QUERY)
                val embedResult = embeddingService.generateEmbedding(
                    text = text,
                    taskType = GeminiEmbeddingService.TASK_TYPE_RETRIEVAL_QUERY
                )
                if (embedResult.isFailure) {
                    val err = embedResult.exceptionOrNull()?.message ?: "Unknown embedding error"
                    failPendingQuestion(
                        pendingMsgId = pendingMsgId,
                        sessionId = resolvedSessionId,
                        errorText = "Could not embed your question: $err",
                        retryQuery = text
                    )
                    return@launch
                }

                val queryEmbedding = embedResult.getOrThrow().map { it.toFloat() }

                // Step 2: Retrieve relevant document chunks via Supabase match_document_chunks RPC
                updatePendingProgress(
                    pendingMsgId = pendingMsgId,
                    stageText = "Searching '${activeDoc.name}' via Supabase pgvector..."
                )

                val matchResult = apiClient.matchDocumentChunks(
                    documentId = activeDoc.id,
                    queryEmbedding = queryEmbedding,
                    matchThreshold = 0.30,
                    matchCount = 5
                )

                if (matchResult.isFailure) {
                    val err = matchResult.exceptionOrNull()?.message ?: "Failed to query document_chunks"
                    failPendingQuestion(
                        pendingMsgId = pendingMsgId,
                        sessionId = resolvedSessionId,
                        errorText = "Supabase chunk retrieval failed: $err",
                        retryQuery = text
                    )
                    return@launch
                }

                val matchedChunks = matchResult.getOrThrow()

                // Step 3: Handle empty retrieval results honestly (no fake answers or fake citations)
                if (matchedChunks.isEmpty()) {
                    val totalChunks = apiClient.fetchDocumentChunksCount(activeDoc.id).getOrNull() ?: 0
                    val emptyMessage = if (totalChunks == 0) {
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            sessionId = resolvedSessionId,
                            sender = SenderType.SYSTEM_NOTICE,
                            content = "No indexed text chunks exist yet for '${activeDoc.name}' in Supabase. If indexing hasn't completed or failed, open Document Metadata to run 'Extract Text & Index Chunks'.",
                            timestamp = currentTimeString()
                        )
                    } else {
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            sessionId = resolvedSessionId,
                            sender = SenderType.ASSISTANT,
                            content = "No relevant passages were found in '${activeDoc.name}' (searched $totalChunks indexed chunk(s)) for this question. Try rephrasing or asking about topics covered in the document.",
                            timestamp = currentTimeString(),
                            citations = emptyList()
                        )
                    }
                    _uiState.update { current ->
                        current.copy(
                            indexedChunkCount = totalChunks,
                            messages = current.messages.map { if (it.id == pendingMsgId) emptyMessage else it },
                            isGeneratingAnswer = false,
                            qaProgressStage = null
                        )
                    }
                    return@launch
                }

                // Step 4: Send retrieved PDF chunks strictly as context to Gemini
                updatePendingProgress(
                    pendingMsgId = pendingMsgId,
                    stageText = "Generating grounded answer from ${matchedChunks.size} retrieved chunk(s)..."
                )

                val answerResult = qaService.answerQuestionFromChunks(
                    documentName = activeDoc.name,
                    question = text,
                    matchedChunks = matchedChunks
                )

                if (answerResult.isFailure) {
                    val err = answerResult.exceptionOrNull()?.message ?: "Gemini failed to generate an answer"
                    failPendingQuestion(
                        pendingMsgId = pendingMsgId,
                        sessionId = resolvedSessionId,
                        errorText = "Answer generation failed: $err",
                        retryQuery = text
                    )
                    return@launch
                }

                val generatedAnswer = answerResult.getOrThrow()
                val citations = matchedChunks.map { chunk ->
                    SourceCitation(
                        id = chunk.id,
                        pageNumber = chunk.pageNumber,
                        sectionTitle = chunk.sectionTitle?.takeIf { it.isNotBlank() } ?: "Page ${chunk.pageNumber} Excerpt",
                        snippetText = chunk.chunkText,
                        relevanceScore = (chunk.similarity * 100.0).toInt().coerceIn(0, 100)
                    )
                }

                val assistantMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = resolvedSessionId,
                    sender = SenderType.ASSISTANT,
                    content = generatedAnswer,
                    timestamp = currentTimeString(),
                    citations = citations
                )

                _uiState.update { current ->
                    current.copy(
                        messages = current.messages.map { if (it.id == pendingMsgId) assistantMessage else it },
                        isGeneratingAnswer = false,
                        qaProgressStage = null
                    )
                }

                // Persist assistant message & real citations to Supabase chat_messages
                if (SupabaseConfig.isConfigured()) {
                    val citationRecords = citations.map { c ->
                        CitationRecord(
                            id = c.id,
                            pageNumber = c.pageNumber,
                            sectionTitle = c.sectionTitle,
                            snippetText = c.snippetText,
                            relevanceScore = c.relevanceScore
                        )
                    }
                    apiClient.saveChatMessage(
                        ChatMessageRecord(
                            sessionId = resolvedSessionId,
                            userId = user.id,
                            sender = "ASSISTANT",
                            content = generatedAnswer,
                            citations = citationRecords
                        )
                    )
                }
            } catch (e: Exception) {
                failPendingQuestion(
                    pendingMsgId = pendingMsgId,
                    sessionId = resolvedSessionId,
                    errorText = "Unexpected error during document Q&A: ${e.message}",
                    retryQuery = text
                )
            }
        }
    }

    private fun updatePendingProgress(pendingMsgId: String, stageText: String) {
        _uiState.update { current ->
            current.copy(
                qaProgressStage = stageText,
                messages = current.messages.map { msg ->
                    if (msg.id == pendingMsgId) msg.copy(content = stageText) else msg
                }
            )
        }
    }

    private fun failPendingQuestion(
        pendingMsgId: String,
        sessionId: String,
        errorText: String,
        retryQuery: String
    ) {
        val errorMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            sender = SenderType.ASSISTANT,
            content = errorText,
            timestamp = currentTimeString(),
            isError = true,
            retryQuery = retryQuery
        )
        _uiState.update { current ->
            current.copy(
                messages = current.messages.map { if (it.id == pendingMsgId) errorMsg else it },
                isGeneratingAnswer = false,
                qaProgressStage = null,
                qaErrorMessage = errorText,
                notificationMessage = errorText
            )
        }
    }

    fun toggleShowcaseAnswer() {
        val state = _uiState.value
        if (state.isShowcaseActive) {
            val filtered = state.messages.filter { it.id != "showcase-user" && it.id != "showcase-assistant" }
            _uiState.update {
                it.copy(
                    messages = filtered,
                    isShowcaseActive = false,
                    notificationMessage = "Citation layout preview hidden"
                )
            }
        } else {
            val showcaseUser = ChatMessage(
                id = "showcase-user",
                sessionId = state.activeSessionId,
                sender = SenderType.USER,
                content = SampleDocuments.showcaseQuestion,
                timestamp = "Just now"
            )
            val showcaseAssistant = ChatMessage(
                id = "showcase-assistant",
                sessionId = state.activeSessionId,
                sender = SenderType.ASSISTANT,
                content = SampleDocuments.showcaseAnswer,
                timestamp = "Just now",
                citations = SampleDocuments.showcaseCitations
            )
            _uiState.update {
                it.copy(
                    messages = it.messages + showcaseUser + showcaseAssistant,
                    isShowcaseActive = true,
                    notificationMessage = "Showing AI answer format with 3 verified page citations"
                )
            }
        }
    }

    fun createNewSession() {
        val newId = UUID.randomUUID().toString()
        val docName = _uiState.value.activeDocument?.name ?: "General Workspace"
        val newSession = ChatSession(
            id = newId,
            title = "New Conversation",
            documentName = docName,
            lastActive = "Just now",
            messageCount = 0
        )
        val welcome = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = newId,
            sender = SenderType.SYSTEM_NOTICE,
            content = "New conversation session created for $docName.",
            timestamp = currentTimeString()
        )
        _uiState.update {
            it.copy(
                sessions = listOf(newSession) + it.sessions,
                activeSessionId = newId,
                messages = listOf(welcome),
                isSidebarOpen = false,
                notificationMessage = "Started new conversation"
            )
        }

        val user = _uiState.value.currentUser
        if (user != null && SupabaseConfig.isConfigured()) {
            viewModelScope.launch {
                apiClient.createChatSession(
                    ChatSessionRecord(
                        id = newId,
                        userId = user.id,
                        documentId = _uiState.value.activeDocument?.id,
                        title = newSession.title,
                        documentName = docName
                    )
                )
            }
        }
    }

    fun selectSession(sessionId: String) {
        _uiState.update {
            it.copy(
                activeSessionId = sessionId,
                isSidebarOpen = false,
                notificationMessage = "Switched session"
            )
        }
        if (_uiState.value.isRemoteSyncActive) {
            loadRemoteMessages(sessionId)
        }
    }

    fun deleteSession(sessionId: String) {
        val remaining = _uiState.value.sessions.filter { it.id != sessionId }
        val nextActive = remaining.firstOrNull()?.id ?: "new-session"
        _uiState.update {
            it.copy(
                sessions = remaining,
                activeSessionId = nextActive
            )
        }
        if (_uiState.value.isRemoteSyncActive) {
            viewModelScope.launch {
                apiClient.deleteChatSession(sessionId)
            }
        }
    }

    // Supabase Auth Actions
    fun signInWithSupabase(email: String, pass: String) {
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            val result = authManager.signIn(email, pass)
            result.onSuccess { user ->
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        currentUser = user,
                        isAuthDialogOpen = false,
                        notificationMessage = "Signed in as ${user.email}"
                    )
                }
                // Automatically upload active local document to Supabase Storage if unsynced
                val activeDoc = _uiState.value.activeDocument
                if (activeDoc != null && (activeDoc.syncStatus == DocumentSyncStatus.LOCAL_ONLY || activeDoc.syncStatus == DocumentSyncStatus.FAILED)) {
                    uploadActiveDocumentToSupabase()
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        authErrorMessage = err.message ?: "Authentication failed"
                    )
                }
            }
        }
    }

    fun signUpWithSupabase(email: String, pass: String) {
        _uiState.update { it.copy(isAuthLoading = true, authErrorMessage = null) }
        viewModelScope.launch {
            val result = authManager.signUp(email, pass)
            result.onSuccess { user ->
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        currentUser = user,
                        isAuthDialogOpen = false,
                        notificationMessage = "Account created for ${user.email}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAuthLoading = false,
                        authErrorMessage = err.message ?: "Registration failed"
                    )
                }
            }
        }
    }

    fun signOutFromSupabase() {
        viewModelScope.launch {
            authManager.signOut()
            _uiState.update {
                it.copy(
                    currentUser = null,
                    isRemoteSyncActive = false,
                    isAuthDialogOpen = false,
                    notificationMessage = "Signed out of Supabase"
                )
            }
        }
    }

    fun setAuthDialogOpen(open: Boolean) {
        _uiState.update {
            it.copy(
                isAuthDialogOpen = open,
                authErrorMessage = null,
                forgotPasswordErrorMessage = null,
                forgotPasswordSuccessMessage = null
            )
        }
    }

    fun clearAuthStatusMessages() {
        _uiState.update {
            it.copy(
                authErrorMessage = null,
                forgotPasswordErrorMessage = null,
                forgotPasswordSuccessMessage = null
            )
        }
    }

    fun sendPasswordResetEmail(email: String) {
        val trimmed = email.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(forgotPasswordErrorMessage = "Please enter your email address.") }
            return
        }

        _uiState.update {
            it.copy(
                isForgotPasswordLoading = true,
                forgotPasswordErrorMessage = null,
                forgotPasswordSuccessMessage = null
            )
        }

        viewModelScope.launch {
            val result = authManager.sendPasswordResetEmail(trimmed)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isForgotPasswordLoading = false,
                        forgotPasswordSuccessMessage = "Password recovery email sent to $trimmed. Open the link on this device to reset your password in DocuMind."
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isForgotPasswordLoading = false,
                        forgotPasswordErrorMessage = err.message ?: "Failed to send password reset email."
                    )
                }
            }
        }
    }

    fun handleDeepLinkIntent(intent: Intent?, forceReprocess: Boolean = false) {
        if (intent == null) return
        val dataUri = intent.data
        if (dataUri != null) {
            handleDeepLinkUri(dataUri, forceReprocess = forceReprocess)
            return
        }

        if (intent.action == Intent.ACTION_VIEW) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
            if (!sharedText.isNullOrBlank() && sharedText.contains("documind://", ignoreCase = true)) {
                handleDeepLinkUri(Uri.parse(sharedText), forceReprocess = forceReprocess)
            } else {
                _uiState.update {
                    it.copy(
                        isAuthDialogOpen = false,
                        passwordRecoveryState = PasswordRecoveryState.InvalidOrExpired(
                            message = "The password recovery link opened without a valid URL payload. Please request a new reset link below."
                        )
                    )
                }
            }
        }
    }

    fun handleDeepLinkUri(uri: Uri?, forceReprocess: Boolean = false) {
        if (uri == null) return
        val rawUri = uri.toString()
        if (!forceReprocess && rawUri == lastHandledDeepLinkUri) return

        val parsed = AuthDeepLinkParser.parse(uri)
        handleRecoveryParseResult(parsed, rawUri)
    }

    fun handlePastedRecoveryInput(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(forgotPasswordErrorMessage = "Paste the documind://reset-password link or recovery token first.")
            }
            return
        }

        val parsed = if (
            trimmed.startsWith("documind://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
        ) {
            val direct = AuthDeepLinkParser.parseRawUrl(trimmed)
            if (direct != DeepLinkParseResult.Ignored) {
                direct
            } else {
                val parsedUri = Uri.parse(trimmed)
                val tokenHash = parsedUri.getQueryParameter("token_hash") ?: parsedUri.getQueryParameter("token")
                val accessToken = parsedUri.getQueryParameter("access_token")
                when {
                    !accessToken.isNullOrBlank() -> DeepLinkParseResult.RecoveryTokens(accessToken, parsedUri.getQueryParameter("refresh_token"), null)
                    !tokenHash.isNullOrBlank() -> DeepLinkParseResult.RecoveryTokenHash(tokenHash, parsedUri.getQueryParameter("type") ?: "recovery")
                    else -> DeepLinkParseResult.RecoveryError(
                        errorCode = "missing_token_in_url",
                        message = "That URL did not include a recovery token. Please request a fresh reset link."
                    )
                }
            }
        } else {
            DeepLinkParseResult.RecoveryTokenHash(tokenHash = trimmed, type = "recovery")
        }

        handleRecoveryParseResult(parsed, trimmed)
    }

    private fun handleRecoveryParseResult(parsed: DeepLinkParseResult, rawKey: String?) {
        when (parsed) {
            DeepLinkParseResult.Ignored -> return

            DeepLinkParseResult.ReturnToSignIn -> {
                lastHandledDeepLinkUri = rawKey
                _uiState.update {
                    it.copy(
                        passwordRecoveryState = PasswordRecoveryState.Inactive,
                        isAuthDialogOpen = true,
                        authErrorMessage = null
                    )
                }
            }

            DeepLinkParseResult.CancelledOrEmpty -> {
                lastHandledDeepLinkUri = rawKey
                _uiState.update {
                    it.copy(
                        isAuthDialogOpen = false,
                        passwordRecoveryState = PasswordRecoveryState.InvalidOrExpired(
                            message = "Password recovery was cancelled or the link did not include valid recovery credentials. You can request a new reset link below."
                        )
                    )
                }
            }

            is DeepLinkParseResult.RecoveryError -> {
                lastHandledDeepLinkUri = rawKey
                _uiState.update {
                    it.copy(
                        isAuthDialogOpen = false,
                        passwordRecoveryState = PasswordRecoveryState.InvalidOrExpired(
                            message = parsed.message
                        )
                    )
                }
            }

            is DeepLinkParseResult.RecoveryTokens -> {
                lastHandledDeepLinkUri = rawKey
                hadSessionBeforeRecovery = authManager.hasValidSession()
                _uiState.update {
                    it.copy(
                        isAuthDialogOpen = false,
                        passwordRecoveryState = PasswordRecoveryState.VerifyingLink
                    )
                }
                viewModelScope.launch {
                    val result = authManager.establishSessionFromRecoveryToken(
                        accessToken = parsed.accessToken,
                        refreshToken = parsed.refreshToken
                    )
                    result.onSuccess { user ->
                        _uiState.update {
                            it.copy(
                                passwordRecoveryState = PasswordRecoveryState.ReadyToReset(email = user.email)
                            )
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(
                                passwordRecoveryState = PasswordRecoveryState.InvalidOrExpired(
                                    message = err.message ?: "This password recovery link is invalid or has expired."
                                )
                            )
                        }
                    }
                }
            }

            is DeepLinkParseResult.RecoveryTokenHash -> {
                lastHandledDeepLinkUri = rawKey
                hadSessionBeforeRecovery = authManager.hasValidSession()
                _uiState.update {
                    it.copy(
                        isAuthDialogOpen = false,
                        passwordRecoveryState = PasswordRecoveryState.VerifyingLink
                    )
                }
                viewModelScope.launch {
                    val result = authManager.verifyRecoveryTokenHash(
                        tokenHash = parsed.tokenHash,
                        type = parsed.type
                    )
                    result.onSuccess { user ->
                        _uiState.update {
                            it.copy(
                                passwordRecoveryState = PasswordRecoveryState.ReadyToReset(email = user.email)
                            )
                        }
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(
                                passwordRecoveryState = PasswordRecoveryState.InvalidOrExpired(
                                    message = err.message ?: "This password recovery link is invalid or has expired."
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    fun resendRecoveryEmailFromScreen(email: String) {
        val current = _uiState.value.passwordRecoveryState as? PasswordRecoveryState.InvalidOrExpired ?: return
        val trimmed = email.trim()
        if (trimmed.isBlank()) {
            _uiState.update {
                it.copy(
                    passwordRecoveryState = current.copy(
                        resendErrorMessage = "Please enter your email address.",
                        resendSuccessMessage = null
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                passwordRecoveryState = current.copy(
                    isSendingEmail = true,
                    resendErrorMessage = null,
                    resendSuccessMessage = null
                )
            )
        }

        viewModelScope.launch {
            val result = authManager.sendPasswordResetEmail(trimmed)
            result.onSuccess {
                val latest = _uiState.value.passwordRecoveryState as? PasswordRecoveryState.InvalidOrExpired
                    ?: return@onSuccess
                _uiState.update {
                    it.copy(
                        passwordRecoveryState = latest.copy(
                            isSendingEmail = false,
                            resendSuccessMessage = "New recovery link sent to $trimmed. Open it on this device to continue.",
                            resendErrorMessage = null
                        )
                    )
                }
            }.onFailure { err ->
                val latest = _uiState.value.passwordRecoveryState as? PasswordRecoveryState.InvalidOrExpired
                    ?: return@onFailure
                _uiState.update {
                    it.copy(
                        passwordRecoveryState = latest.copy(
                            isSendingEmail = false,
                            resendErrorMessage = err.message ?: "Could not send recovery email.",
                            resendSuccessMessage = null
                        )
                    )
                }
            }
        }
    }

    fun updatePasswordFromRecovery(newPassword: String) {
        val current = _uiState.value.passwordRecoveryState as? PasswordRecoveryState.ReadyToReset ?: return
        if (newPassword.length < 8) {
            _uiState.update {
                it.copy(
                    passwordRecoveryState = current.copy(
                        errorMessage = "Password must be at least 8 characters long."
                    )
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                passwordRecoveryState = current.copy(
                    isSubmitting = true,
                    errorMessage = null
                )
            )
        }

        viewModelScope.launch {
            val result = authManager.updateUserPassword(newPassword)
            result.onSuccess { updatedUser ->
                hadSessionBeforeRecovery = true
                _uiState.update {
                    it.copy(
                        currentUser = updatedUser,
                        isRemoteSyncActive = SupabaseConfig.isConfigured(),
                        passwordRecoveryState = PasswordRecoveryState.ResetSuccess(email = updatedUser.email),
                        notificationMessage = "Password updated successfully."
                    )
                }
            }.onFailure { err ->
                val latest = _uiState.value.passwordRecoveryState as? PasswordRecoveryState.ReadyToReset
                    ?: return@onFailure
                _uiState.update {
                    it.copy(
                        passwordRecoveryState = latest.copy(
                            isSubmitting = false,
                            errorMessage = err.message ?: "Failed to update password. Please request a new recovery link."
                        )
                    )
                }
            }
        }
    }

    fun cancelPasswordRecovery() {
        val stateBeforeCancel = _uiState.value.passwordRecoveryState
        lastHandledDeepLinkUri = null
        _uiState.update {
            it.copy(
                passwordRecoveryState = PasswordRecoveryState.Inactive,
                notificationMessage = "Password recovery cancelled."
            )
        }
        if (stateBeforeCancel is PasswordRecoveryState.ReadyToReset && !hadSessionBeforeRecovery) {
            viewModelScope.launch {
                authManager.signOut()
            }
        }
    }

    fun openSignInFromRecovery() {
        lastHandledDeepLinkUri = null
        _uiState.update {
            it.copy(
                passwordRecoveryState = PasswordRecoveryState.Inactive,
                isAuthDialogOpen = true,
                authErrorMessage = null
            )
        }
    }

    fun continueAfterPasswordReset() {
        val user = _uiState.value.currentUser
        lastHandledDeepLinkUri = null
        _uiState.update {
            it.copy(
                passwordRecoveryState = PasswordRecoveryState.Inactive,
                notificationMessage = "Signed in as ${user?.email ?: "authenticated user"}"
            )
        }
        if (user != null && SupabaseConfig.isConfigured()) {
            loadRemoteUserData(user.id)
        }
    }

    fun returnToSignInAfterReset() {
        lastHandledDeepLinkUri = null
        _uiState.update {
            it.copy(
                passwordRecoveryState = PasswordRecoveryState.Inactive,
                isAuthDialogOpen = true,
                authErrorMessage = null,
                forgotPasswordSuccessMessage = null,
                forgotPasswordErrorMessage = null,
                notificationMessage = "Password updated! Please sign in with your new password."
            )
        }
        viewModelScope.launch {
            authManager.signOut()
        }
    }

    fun setConfigDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isConfigDialogOpen = open) }
    }

    fun openCitationInspector(citation: SourceCitation) {
        _uiState.update {
            it.copy(
                selectedCitation = citation,
                isCitationInspectorOpen = true
            )
        }
    }

    fun closeCitationInspector() {
        _uiState.update {
            it.copy(
                isCitationInspectorOpen = false,
                selectedCitation = null
            )
        }
    }

    fun setDocumentDetailsOpen(open: Boolean) {
        _uiState.update { it.copy(isDocumentDetailsOpen = open) }
    }

    fun toggleSidebar() {
        _uiState.update { it.copy(isSidebarOpen = !it.isSidebarOpen) }
    }

    fun setSidebarOpen(open: Boolean) {
        _uiState.update { it.copy(isSidebarOpen = open) }
    }

    fun clearNotification() {
        _uiState.update { it.copy(notificationMessage = null) }
    }

    fun setGeminiKeyDialogOpen(open: Boolean) {
        _uiState.update { it.copy(isGeminiKeyDialogOpen = open) }
    }

    fun saveGeminiApiKey(key: String) {
        GeminiEmbeddingService.saveApiKey(getApplication(), key)
        val pendingQuestion = _uiState.value.pendingQuestionAfterKey
        _uiState.update {
            it.copy(
                isGeminiKeyMissing = false,
                isGeminiKeyDialogOpen = false,
                pendingQuestionAfterKey = null,
                notificationMessage = "Gemini API key saved successfully."
            )
        }
        if (!pendingQuestion.isNullOrBlank()) {
            sendQuestion(pendingQuestion)
        } else if (_uiState.value.activeDocument != null && !_uiState.value.activeDocument!!.id.startsWith("doc-sample-")) {
            indexActiveDocument()
        }
    }

    fun indexActiveDocument() {
        val doc = _uiState.value.activeDocument ?: run {
            _uiState.update { it.copy(notificationMessage = "No active document selected for indexing.") }
            return
        }

        if (!authManager.hasValidSession()) {
            _uiState.update {
                it.copy(
                    isAuthDialogOpen = true,
                    authErrorMessage = "Please sign in to Supabase to index document chunks."
                )
            }
            return
        }

        val bytes = activeDocumentBytes?.takeIf { activeDocumentBytesDocId == doc.id } ?: try {
            doc.uriString?.let { uriStr ->
                getApplication<Application>().contentResolver.openInputStream(Uri.parse(uriStr))?.use { it.readBytes() }
            }
        } catch (_: Exception) {
            null
        }

        if (bytes == null || bytes.isEmpty()) {
            // Attempt downloading from Supabase storage if file is synced
            if (!doc.storagePath.isNullOrBlank()) {
                viewModelScope.launch {
                    _uiState.update { it.copy(isIndexing = true, indexingProgress = "Downloading PDF from Supabase Storage...") }
                    val dlResult = apiClient.downloadPdfFile(doc.storagePath)
                    dlResult.onSuccess { downloadedBytes ->
                        activeDocumentBytes = downloadedBytes
                        activeDocumentBytesDocId = doc.id
                        executeDocumentIndexing(doc, downloadedBytes, _uiState.value.activeSessionId)
                    }.onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isIndexing = false,
                                indexingProgress = null,
                                notificationMessage = "Failed to download PDF for indexing: ${err.message}"
                            )
                        }
                    }
                }
                return
            } else {
                _uiState.update { it.copy(notificationMessage = "PDF file content is not available in memory. Please select the file again.") }
                return
            }
        }

        executeDocumentIndexing(doc, bytes, _uiState.value.activeSessionId)
    }

    private fun executeDocumentIndexing(doc: DocumentInfo, bytes: ByteArray, sessionId: String) {
        val context = getApplication<Application>()

        // 1. Verify Gemini API key is configured
        if (!GeminiEmbeddingService.isConfigured(context)) {
            _uiState.update {
                it.copy(
                    isIndexing = false,
                    indexingProgress = null,
                    isGeminiKeyMissing = true,
                    isGeminiKeyDialogOpen = true,
                    notificationMessage = "Gemini API key is required to generate 768-dim embeddings. Configure it to index documents."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isIndexing = true,
                    indexingProgress = "Extracting text from PDF pages (PDFBox)..."
                )
            }

            // Update document status in Supabase
            apiClient.updateDocumentStatus(doc.id, "indexing")

            // 2. Extract actual page-by-page text
            val extractionResult = PdfTextExtractor.extractPagesFromBytes(context, bytes)
            if (extractionResult.isFailure) {
                val ex = extractionResult.exceptionOrNull()
                val isScanned = ex is ScannedPdfException || ex?.message?.contains("scanned", ignoreCase = true) == true
                val errorMsg = if (isScanned) {
                    "No extractable text found in this PDF across ${doc.pageCount} page(s). It appears to be a scanned document requiring OCR."
                } else {
                    "Text extraction failed: ${ex?.message}"
                }

                apiClient.updateDocumentStatus(doc.id, if (isScanned) "ready" else "error")
                _uiState.update {
                    it.copy(
                        isIndexing = false,
                        indexingProgress = null,
                        notificationMessage = errorMsg
                    )
                }

                val notice = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    sender = SenderType.SYSTEM_NOTICE,
                    content = if (isScanned) {
                        "⚠️ Indexing notice for '${doc.name}': No text layer was found in this PDF file. It appears to be an image-only / scanned document requiring OCR before vector embeddings can be generated."
                    } else {
                        "⚠️ Indexing failed for '${doc.name}': ${ex?.message}"
                    },
                    timestamp = currentTimeString()
                )
                _uiState.update { it.copy(messages = it.messages + notice) }
                return@launch
            }

            val pages = extractionResult.getOrThrow()
            _uiState.update {
                it.copy(indexingProgress = "Chunking text from ${pages.size} page(s) with overlap...")
            }

            // 3. Chunk text meaningfully preserving page numbers
            val chunks = TextChunker.chunkPages(pages)
            if (chunks.isEmpty()) {
                apiClient.updateDocumentStatus(doc.id, "ready")
                _uiState.update {
                    it.copy(
                        isIndexing = false,
                        indexingProgress = null,
                        notificationMessage = "No meaningful text chunks could be extracted from this document."
                    )
                }
                return@launch
            }

            // 4. Generate L2-normalized 768-dimensional embeddings using Gemini gemini-embedding-001 (RETRIEVAL_DOCUMENT)
            _uiState.update {
                it.copy(indexingProgress = "Generating 768-dim embeddings for 0 of ${chunks.size} chunks...")
            }

            val texts = chunks.map { it.chunkText }
            val embeddingResult = embeddingService.generateBatchEmbeddings(
                texts = texts,
                taskType = GeminiEmbeddingService.TASK_TYPE_RETRIEVAL_DOCUMENT
            ) { completed, total ->
                _uiState.update {
                    it.copy(indexingProgress = "Generating 768-dim embeddings ($completed / $total chunks)...")
                }
            }

            if (embeddingResult.isFailure) {
                val err = embeddingResult.exceptionOrNull()
                apiClient.updateDocumentStatus(doc.id, "error")
                _uiState.update {
                    it.copy(
                        isIndexing = false,
                        indexingProgress = null,
                        notificationMessage = "Embedding generation failed: ${err?.message}"
                    )
                }
                return@launch
            }

            val embeddings = embeddingResult.getOrThrow()

            // 5. Clear previous chunks for this document only after new embeddings succeed (prevent duplicates on re-indexing)
            _uiState.update {
                it.copy(indexingProgress = "Clearing any previous chunks for '${doc.name}'...")
            }
            apiClient.deleteDocumentChunks(doc.id)

            // 6. Assemble DocumentChunkRecord list with page numbers, chunk text, and 768-dim vectors
            _uiState.update {
                it.copy(indexingProgress = "Saving ${chunks.size} chunks to Supabase document_chunks...")
            }

            val chunkRecords = chunks.mapIndexed { idx, chunk ->
                DocumentChunkRecord(
                    documentId = doc.id,
                    pageNumber = chunk.pageNumber,
                    sectionTitle = chunk.sectionTitle,
                    chunkText = chunk.chunkText,
                    embedding = embeddings[idx]
                )
            }

            // 7. Insert chunks into Supabase document_chunks using authenticated JWT
            val insertResult = apiClient.insertDocumentChunks(chunkRecords)
            insertResult.onSuccess { insertedCount ->
                apiClient.updateDocumentStatus(doc.id, "ready")
                _uiState.update { state ->
                    state.copy(
                        isIndexing = false,
                        indexingProgress = null,
                        indexedChunkCount = insertedCount,
                        activeDocument = doc.copy(
                            status = DocumentStatus.READY,
                            syncStatus = DocumentSyncStatus.SYNCED
                        ),
                        notificationMessage = "Successfully indexed $insertedCount chunks with 768-dim embeddings in Supabase."
                    )
                }

                // Add success message to chat session
                val successNotice = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    sender = SenderType.SYSTEM_NOTICE,
                    content = "✅ Indexing Complete: Successfully extracted text from ${pages.size} page(s) of '${doc.name}', split into $insertedCount semantic chunks, and stored with 768-dimensional Gemini vector embeddings in Supabase 'document_chunks'.",
                    timestamp = currentTimeString()
                )
                _uiState.update { it.copy(messages = it.messages + successNotice) }
            }.onFailure { err ->
                apiClient.updateDocumentStatus(doc.id, "error")
                _uiState.update {
                    it.copy(
                        isIndexing = false,
                        indexingProgress = null,
                        notificationMessage = "Failed to insert chunks into Supabase: ${err.message}"
                    )
                }
            }
        }
    }

    private fun currentTimeString(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
}

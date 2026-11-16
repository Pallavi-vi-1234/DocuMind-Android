package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.DocumentHelper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AuthDialog
import com.example.ui.components.ChatInputBar
import com.example.ui.components.ChatSection
import com.example.ui.components.CitationInspectorDialog
import com.example.ui.components.DocuMindTopBar
import com.example.ui.components.DocumentDetailsDialog
import com.example.ui.components.DocumentUploadArea
import com.example.ui.components.PasswordRecoveryScreen
import com.example.ui.components.PasswordRecoveryState
import com.example.ui.components.SidebarDrawer
import com.example.ui.components.SupabaseConfigDialog
import com.example.viewmodel.DocuMindViewModel
import kotlinx.coroutines.launch

@Composable
fun DocuMindApp(
    deepLinkUri: Uri? = null,
    onDeepLinkConsumed: () -> Unit = {},
    viewModel: DocuMindViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle incoming Supabase password recovery deep links passed via Composable parameter
    LaunchedEffect(deepLinkUri) {
        if (deepLinkUri != null) {
            viewModel.handleDeepLinkUri(deepLinkUri, forceReprocess = true)
            onDeepLinkConsumed()
        }
    }

    // System File Picker for PDF documents (Storage Access Framework)
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Not supported by some providers, continue safely
            }
            viewModel.onDocumentPicked(context, uri)
        } else {
            viewModel.onDocumentPickCancelled()
        }
    }

    // Ensure sample PDF is seeded in Downloads for SAF document picker
    LaunchedEffect(Unit) {
        DocumentHelper.ensureSamplePdfInDownloads(context)
    }

    // Handle notifications
    LaunchedEffect(uiState.notificationMessage) {
        uiState.notificationMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearNotification()
        }
    }

    // Sync drawer state with viewmodel
    LaunchedEffect(uiState.isSidebarOpen) {
        if (uiState.isSidebarOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (!drawerState.isOpen && uiState.isSidebarOpen) {
            viewModel.setSidebarOpen(false)
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch {
            drawerState.close()
            viewModel.setSidebarOpen(false)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .testTag("documind_root")
    ) {
        val isWideScreen = maxWidth >= 768.dp

        if (uiState.passwordRecoveryState !is PasswordRecoveryState.Inactive) {
            // Password Recovery Full-Screen Flow (replaces workspace to prevent drawer/focus overlap or blank frames)
            PasswordRecoveryScreen(
                state = uiState.passwordRecoveryState,
                onUpdatePassword = { newPassword -> viewModel.updatePasswordFromRecovery(newPassword) },
                onRequestNewResetLink = { email -> viewModel.resendRecoveryEmailFromScreen(email) },
                onCancelRecovery = { viewModel.cancelPasswordRecovery() },
                onContinueAfterReset = { viewModel.continueAfterPasswordReset() },
                onOpenSignIn = { viewModel.openSignInFromRecovery() },
                onReturnToSignIn = { viewModel.returnToSignInAfterReset() },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            if (isWideScreen) {
            // Desktop / Tablet Responsive Side-by-Side Layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Persistent Sidebar
                SidebarDrawer(
                    sessions = uiState.sessions,
                    activeSessionId = uiState.activeSessionId,
                    activeDocument = uiState.activeDocument,
                    onSelectSession = { viewModel.selectSession(it) },
                    onDeleteSession = { viewModel.deleteSession(it) },
                    onNewChatClick = { viewModel.createNewSession() },
                    onUploadClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    onSectionClick = { page, title ->
                        viewModel.sendQuestion("Tell me more about Page $page: $title")
                    },
                    onCloseSidebar = { /* In wide screen, stays anchored */ },
                    modifier = Modifier.width(320.dp)
                )

                // Main Content Workspace
                MainWorkspace(
                    uiState = uiState,
                    sessionCount = uiState.sessions.size,
                    isWideScreen = true,
                    onToggleSidebar = { /* wide screen has fixed sidebar */ },
                    onToggleShowcase = { viewModel.toggleShowcaseAnswer() },
                    onOpenDocumentDetails = { viewModel.setDocumentDetailsOpen(true) },
                    onOpenAuth = { viewModel.setAuthDialogOpen(true) },
                    onOpenConfigHelp = { viewModel.setConfigDialogOpen(true) },
                    onPickPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    onUploadToCloudClick = { viewModel.uploadActiveDocumentToSupabase() },
                    onSelectSampleDoc = { viewModel.selectSampleDocument(it) },
                    onClearDocument = { viewModel.clearActiveDocument() },
                    onInputQueryChange = { viewModel.updateInputQuery(it) },
                    onSendMessage = { customText -> viewModel.sendQuestion(customText) },
                    onCitationClick = { citation -> viewModel.openCitationInspector(citation) },
                    onIndexClick = { viewModel.indexActiveDocument() },
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // Mobile / Handheld Layout with ModalNavigationDrawer
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.width(320.dp)
                    ) {
                        SidebarDrawer(
                            sessions = uiState.sessions,
                            activeSessionId = uiState.activeSessionId,
                            activeDocument = uiState.activeDocument,
                            onSelectSession = { sessionId ->
                                viewModel.selectSession(sessionId)
                                coroutineScope.launch { drawerState.close() }
                            },
                            onDeleteSession = { viewModel.deleteSession(it) },
                            onNewChatClick = {
                                viewModel.createNewSession()
                                coroutineScope.launch { drawerState.close() }
                            },
                            onUploadClick = {
                                coroutineScope.launch { drawerState.close() }
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            },
                            onSectionClick = { page, title ->
                                coroutineScope.launch { drawerState.close() }
                                viewModel.sendQuestion("Tell me more about Page $page: $title")
                            },
                            onCloseSidebar = {
                                coroutineScope.launch { drawerState.close() }
                                viewModel.setSidebarOpen(false)
                            }
                        )
                    }
                }
            ) {
                MainWorkspace(
                    uiState = uiState,
                    sessionCount = uiState.sessions.size,
                    isWideScreen = false,
                    onToggleSidebar = {
                        coroutineScope.launch {
                            if (drawerState.isOpen) drawerState.close() else drawerState.open()
                        }
                    },
                    onToggleShowcase = { viewModel.toggleShowcaseAnswer() },
                    onOpenDocumentDetails = { viewModel.setDocumentDetailsOpen(true) },
                    onOpenAuth = { viewModel.setAuthDialogOpen(true) },
                    onOpenConfigHelp = { viewModel.setConfigDialogOpen(true) },
                    onPickPdfClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    onUploadToCloudClick = { viewModel.uploadActiveDocumentToSupabase() },
                    onSelectSampleDoc = { viewModel.selectSampleDocument(it) },
                    onClearDocument = { viewModel.clearActiveDocument() },
                    onInputQueryChange = { viewModel.updateInputQuery(it) },
                    onSendMessage = { customText -> viewModel.sendQuestion(customText) },
                    onCitationClick = { citation -> viewModel.openCitationInspector(citation) },
                    onIndexClick = { viewModel.indexActiveDocument() },
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Citation Inspector Dialog
        if (uiState.isCitationInspectorOpen && uiState.selectedCitation != null) {
            CitationInspectorDialog(
                citation = uiState.selectedCitation!!,
                documentName = uiState.activeDocument?.name,
                onDismiss = { viewModel.closeCitationInspector() }
            )
        }

        // Document Details Dialog
        if (uiState.isDocumentDetailsOpen) {
            DocumentDetailsDialog(
                document = uiState.activeDocument,
                onDismiss = { viewModel.setDocumentDetailsOpen(false) },
                onIndexDocument = { viewModel.indexActiveDocument() }
            )
        }

        // Auth Dialog
        if (uiState.isAuthDialogOpen) {
            AuthDialog(
                currentUser = uiState.currentUser,
                isLoading = uiState.isAuthLoading,
                errorMessage = uiState.authErrorMessage,
                onSignIn = { email, pass -> viewModel.signInWithSupabase(email, pass) },
                onSignUp = { email, pass -> viewModel.signUpWithSupabase(email, pass) },
                onSignOut = { viewModel.signOutFromSupabase() },
                onOpenConfigHelp = {
                    viewModel.setAuthDialogOpen(false)
                    viewModel.setConfigDialogOpen(true)
                },
                onDismiss = { viewModel.setAuthDialogOpen(false) },
                onSendPasswordReset = { email -> viewModel.sendPasswordResetEmail(email) },
                onPasteRecoveryLink = { link -> viewModel.handlePastedRecoveryInput(link) },
                isForgotPasswordLoading = uiState.isForgotPasswordLoading,
                forgotPasswordSuccessMessage = uiState.forgotPasswordSuccessMessage,
                forgotPasswordErrorMessage = uiState.forgotPasswordErrorMessage,
                onClearAuthMessages = { viewModel.clearAuthStatusMessages() }
            )
        }

        // Supabase Config Guide Dialog
        if (uiState.isConfigDialogOpen) {
            SupabaseConfigDialog(
                onDismiss = { viewModel.setConfigDialogOpen(false) }
            )
        }

        // Gemini API Key Required Dialog
        if (uiState.isGeminiKeyDialogOpen) {
            com.example.ui.components.GeminiKeyDialog(
                onSaveKey = { key ->
                    viewModel.saveGeminiApiKey(key)
                },
                onDismiss = { viewModel.setGeminiKeyDialogOpen(false) }
            )
        }
        }
    }
}

@Composable
private fun MainWorkspace(
    uiState: com.example.viewmodel.DocuMindUiState,
    sessionCount: Int,
    isWideScreen: Boolean,
    onToggleSidebar: () -> Unit,
    onToggleShowcase: () -> Unit,
    onOpenDocumentDetails: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenConfigHelp: () -> Unit,
    onPickPdfClick: () -> Unit,
    onUploadToCloudClick: () -> Unit,
    onSelectSampleDoc: (com.example.model.DocumentInfo) -> Unit,
    onClearDocument: () -> Unit,
    onInputQueryChange: (String) -> Unit,
    onSendMessage: (String?) -> Unit,
    onCitationClick: (com.example.model.SourceCitation) -> Unit,
    onIndexClick: () -> Unit = {},
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.testTag("main_workspace_scaffold"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            DocuMindTopBar(
                activeDocument = uiState.activeDocument,
                isSidebarOpen = uiState.isSidebarOpen,
                isShowcaseActive = uiState.isShowcaseActive,
                sessionCount = sessionCount,
                currentUser = uiState.currentUser,
                onToggleSidebar = onToggleSidebar,
                onToggleShowcase = onToggleShowcase,
                onOpenDocumentDetails = onOpenDocumentDetails,
                onOpenAuth = onOpenAuth,
                onOpenConfigHelp = onOpenConfigHelp
            )
        },
        bottomBar = {
            ChatInputBar(
                inputQuery = uiState.inputQuery,
                activeDocument = uiState.activeDocument,
                isShowcaseActive = uiState.isShowcaseActive,
                isGeneratingAnswer = uiState.isGeneratingAnswer,
                onInputQueryChange = onInputQueryChange,
                onSendMessage = { onSendMessage(null) },
                onToggleShowcase = onToggleShowcase
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Upload Area banner
            DocumentUploadArea(
                activeDocument = uiState.activeDocument,
                sampleDocuments = uiState.sampleDocuments,
                onPickPdfClick = onPickPdfClick,
                onUploadToCloudClick = onUploadToCloudClick,
                onSelectSampleDoc = onSelectSampleDoc,
                onClearDocument = onClearDocument,
                onOpenDetails = onOpenDocumentDetails,
                isIndexing = uiState.isIndexing,
                indexingProgress = uiState.indexingProgress,
                indexedChunkCount = uiState.indexedChunkCount,
                onIndexClick = onIndexClick,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )

            // Chat Section (Scrollable messages thread / empty, loading, & error states)
            ChatSection(
                messages = uiState.messages,
                activeDocument = uiState.activeDocument,
                indexedChunkCount = uiState.indexedChunkCount,
                isShowcaseActive = uiState.isShowcaseActive,
                onSendMessage = onSendMessage,
                onRetryQuestion = { query -> onSendMessage(query) },
                onToggleShowcase = onToggleShowcase,
                onCitationClick = onCitationClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatMessage
import com.example.model.DocumentInfo
import com.example.model.SenderType
import com.example.model.SourceCitation
import com.example.ui.theme.DocuMindBlueContainer
import com.example.ui.theme.DocuMindBluePrimary
import com.example.ui.theme.DocuMindCyanContainer
import com.example.ui.theme.DocuMindCyanSecondary
import com.example.ui.theme.DocuMindOnBlueContainer
import com.example.ui.theme.DocuMindOnCyanContainer
import com.example.ui.theme.PdfRed
import com.example.ui.theme.PdfRedContainer
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun ChatSection(
    messages: List<ChatMessage>,
    activeDocument: DocumentInfo?,
    indexedChunkCount: Int,
    isShowcaseActive: Boolean,
    onSendMessage: (String?) -> Unit,
    onRetryQuestion: (String) -> Unit,
    onToggleShowcase: () -> Unit,
    onCitationClick: (SourceCitation) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val showEmptyState = messages.none {
        it.sender == SenderType.USER || it.sender == SenderType.ASSISTANT
    } && !isShowcaseActive && messages.size <= 1

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("chat_section")
    ) {
        if (showEmptyState) {
            ChatEmptyState(
                activeDocument = activeDocument,
                indexedChunkCount = indexedChunkCount,
                onPromptSelected = { prompt -> onSendMessage(prompt) },
                onToggleShowcase = onToggleShowcase,
                isShowcaseActive = isShowcaseActive
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("chat_messages_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatMessageItem(
                        message = message,
                        onCitationClick = onCitationClick,
                        onRetryQuestion = onRetryQuestion
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    onCitationClick: (SourceCitation) -> Unit,
    onRetryQuestion: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (message.sender) {
        SenderType.USER -> {
            // User Message Bubble (Right aligned, DocuMind Blue)
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("user_message_bubble"),
                horizontalArrangement = Arrangement.End
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Surface(
                        color = DocuMindBluePrimary,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 4.dp
                        ),
                        shadowElevation = 1.dp
                    ) {
                        Text(
                            text = message.content,
                            fontSize = 14.sp,
                            color = Color.White,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.timestamp,
                        fontSize = 11.sp,
                        color = Slate400,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }

        SenderType.ASSISTANT -> {
            // Assistant Message Bubble (Loading, Error, Empty, or Grounded Answer with Citations)
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag(
                        when {
                            message.isPending -> "assistant_loading_bubble"
                            message.isError -> "assistant_error_bubble"
                            else -> "assistant_message_bubble"
                        }
                    ),
                horizontalArrangement = Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (message.isError) PdfRed else DocuMindBluePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (message.isError) Icons.Default.ErrorOutline else Icons.Default.AutoAwesome,
                        contentDescription = if (message.isError) "Error" else "AI Assistant",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Card(
                        shape = RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.isError) PdfRedContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (message.isError) PdfRed.copy(alpha = 0.35f) else Slate200
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            if (message.isPending) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = DocuMindBluePrimary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = message.content,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Slate700,
                                        lineHeight = 19.sp
                                    )
                                }
                            } else if (message.isError) {
                                Text(
                                    text = message.content,
                                    fontSize = 13.sp,
                                    color = PdfRed,
                                    lineHeight = 19.sp
                                )
                                if (!message.retryQuery.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = { onRetryQuestion(message.retryQuery) },
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, PdfRed.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("retry_question_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = PdfRed,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Retry Question",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PdfRed
                                        )
                                    }
                                }
                            } else {
                                // Answer Text Body
                                Text(
                                    text = message.content,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 21.sp
                                )

                                // Source Page Numbers Section
                                if (message.citations.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Slate200)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Source,
                                            contentDescription = null,
                                            tint = DocuMindBluePrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Source Page Numbers (${message.citations.size}):",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DocuMindBluePrimary
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    CitationsBadgeList(
                                        citations = message.citations,
                                        onCitationClick = onCitationClick
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (message.isPending) "DocuMind • Searching..." else "DocuMind • ${message.timestamp}",
                        fontSize = 11.sp,
                        color = Slate400,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        SenderType.SYSTEM_NOTICE -> {
            // System Notice Card
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("system_notice_card"),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = DocuMindBlueContainer.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DocuMindBluePrimary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth(0.95f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = DocuMindBluePrimary,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = message.content,
                            fontSize = 12.sp,
                            color = DocuMindOnBlueContainer,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CitationsBadgeList(
    citations: List<SourceCitation>,
    onCitationClick: (SourceCitation) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        citations.forEach { citation ->
            Surface(
                onClick = { onCitationClick(citation) },
                shape = RoundedCornerShape(20.dp),
                color = DocuMindCyanContainer,
                border = BorderStroke(1.dp, DocuMindCyanSecondary.copy(alpha = 0.3f)),
                modifier = Modifier.testTag("citation_badge_page_${citation.pageNumber}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(DocuMindCyanSecondary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "p",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Page ${citation.pageNumber}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DocuMindOnCyanContainer
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "• ${citation.relevanceScore}% match",
                        fontSize = 11.sp,
                        color = DocuMindOnCyanContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatEmptyState(
    activeDocument: DocumentInfo?,
    indexedChunkCount: Int,
    onPromptSelected: (String) -> Unit,
    onToggleShowcase: () -> Unit,
    isShowcaseActive: Boolean
) {
    val suggestedQuestions = listOf(
        "Summarize the key findings and executive takeaways",
        "What are the main risks and mitigation strategies?",
        "Extract critical metrics, dates, and budget figures",
        "List all actionable compliance recommendations"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(DocuMindBlueContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = DocuMindBluePrimary,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "DocuMind Q&A Assistant",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = when {
                activeDocument == null ->
                    "Upload a PDF document above to start asking questions grounded in verifiable page references."
                activeDocument.id.startsWith("doc-sample-") ->
                    "Previewing '${activeDocument.name}'. Upload a real PDF above to index its text and ask questions backed by Supabase pgvector citations."
                indexedChunkCount > 0 ->
                    "Ask questions about '${activeDocument.name}' ($indexedChunkCount indexed chunks ready). Answers are generated solely from retrieved PDF excerpts with page citations."
                else ->
                    "Ask questions about '${activeDocument.name}'. Answers retrieve relevant chunks via Supabase pgvector and cite exact page numbers."
            },
            fontSize = 13.sp,
            color = Slate600,
            lineHeight = 18.sp,
            modifier = Modifier.fillMaxWidth(0.92f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Showcase Answer Button
        Surface(
            onClick = onToggleShowcase,
            shape = RoundedCornerShape(10.dp),
            color = if (isShowcaseActive) DocuMindBluePrimary else Slate100,
            border = BorderStroke(1.dp, if (isShowcaseActive) DocuMindBluePrimary else Slate200),
            modifier = Modifier.testTag("preview_showcase_empty_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Source,
                    contentDescription = null,
                    tint = if (isShowcaseActive) Color.White else DocuMindBluePrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isShowcaseActive) "Hide Citation Layout Preview" else "Preview AI Answer Layout with Page Citations",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isShowcaseActive) Color.White else DocuMindBluePrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Suggested Prompt Chips
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = Slate500,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Suggested Questions:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate700
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestedQuestions.forEach { question ->
                Surface(
                    onClick = { onPromptSelected(question) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.testTag("suggested_prompt_${question.take(10)}")
                ) {
                    Text(
                        text = question,
                        fontSize = 12.sp,
                        color = Slate700,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    inputQuery: String,
    activeDocument: DocumentInfo?,
    isShowcaseActive: Boolean,
    isGeneratingAnswer: Boolean = false,
    onInputQueryChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onToggleShowcase: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chat_input_container"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(color = Slate200, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Document context indicator tag
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (activeDocument != null) PdfRedContainer else Slate200),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (activeDocument != null) "PDF" else "—",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeDocument != null) PdfRed else Slate500
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (activeDocument != null) {
                                "Querying: ${activeDocument.name} (${activeDocument.pageCount}p)"
                            } else {
                                "No PDF selected — upload or pick a document"
                            },
                            fontSize = 11.sp,
                            color = Slate600,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Quick toggle preview badge
                    Surface(
                        onClick = onToggleShowcase,
                        shape = RoundedCornerShape(12.dp),
                        color = if (isShowcaseActive) DocuMindBluePrimary else Slate100,
                        modifier = Modifier.testTag("input_preview_citations_chip")
                    ) {
                        Text(
                            text = if (isShowcaseActive) "Hide Preview" else "Preview Citations",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isShowcaseActive) Color.White else DocuMindBluePrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputQuery,
                        onValueChange = onInputQueryChange,
                        enabled = !isGeneratingAnswer,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_text_field"),
                        placeholder = {
                            Text(
                                text = if (activeDocument != null) {
                                    "Ask a question about this PDF..."
                                } else {
                                    "Upload a PDF to ask questions..."
                                },
                                fontSize = 13.sp,
                                color = Slate400,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DocuMindBluePrimary,
                            unfocusedBorderColor = Slate300,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (inputQuery.isNotBlank() && !isGeneratingAnswer) {
                                    onSendMessage()
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onSendMessage,
                        enabled = inputQuery.isNotBlank() && !isGeneratingAnswer,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = DocuMindBluePrimary,
                            contentColor = Color.White,
                            disabledContainerColor = Slate200,
                            disabledContentColor = Slate400
                        ),
                        modifier = Modifier
                            .testTag("send_message_button")
                            .size(48.dp)
                            .clip(CircleShape)
                    ) {
                        if (isGeneratingAnswer) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = DocuMindBluePrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send Question",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

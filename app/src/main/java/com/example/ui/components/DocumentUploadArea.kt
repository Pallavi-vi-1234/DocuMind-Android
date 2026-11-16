package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.sp
import com.example.model.DocumentInfo
import com.example.model.DocumentSyncStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningContainer
import com.example.ui.theme.DocuMindBlueContainer
import com.example.ui.theme.DocuMindBluePrimary
import com.example.ui.theme.DocuMindCyanSecondary
import com.example.ui.theme.DocuMindOnBlueContainer
import com.example.ui.theme.PdfRed
import com.example.ui.theme.PdfRedContainer
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessEmerald

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentUploadArea(
    activeDocument: DocumentInfo?,
    sampleDocuments: List<DocumentInfo>,
    onPickPdfClick: () -> Unit,
    onSelectSampleDoc: (DocumentInfo) -> Unit,
    onClearDocument: () -> Unit,
    onOpenDetails: () -> Unit,
    onUploadToCloudClick: () -> Unit = {},
    isIndexing: Boolean = false,
    indexingProgress: String? = null,
    indexedChunkCount: Int = 0,
    onIndexClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("document_upload_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, Slate200)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            if (activeDocument != null) {
                // Active Document Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // PDF icon badge
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PdfRedContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = PdfRed,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "PDF",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PdfRed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = activeDocument.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Page count badge
                                Surface(
                                    color = DocuMindBlueContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${activeDocument.pageCount} Pages",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = DocuMindOnBlueContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                Text(
                                    text = activeDocument.sizeFormatted,
                                    fontSize = 12.sp,
                                    color = Slate500
                                )

                                Text(
                                    text = "•",
                                    fontSize = 12.sp,
                                    color = Slate400
                                )

                                if (isIndexing) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = DocuMindBluePrimary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = indexingProgress ?: "Indexing 768-dim vectors...",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = DocuMindBluePrimary
                                        )
                                    }
                                } else {
                                    when (activeDocument.syncStatus) {
                                        DocumentSyncStatus.SYNCED -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = SuccessEmerald,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = if (indexedChunkCount > 0) "$indexedChunkCount Chunks Indexed" else "In Supabase Storage",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = SuccessEmerald
                                                )
                                            }
                                        }
                                    DocumentSyncStatus.UPLOADING -> {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 2.dp,
                                                color = DocuMindBluePrimary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Uploading to Cloud...",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = DocuMindBluePrimary
                                            )
                                        }
                                    }
                                    DocumentSyncStatus.FAILED -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { onUploadToCloudClick() }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = PdfRed,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Upload Failed",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PdfRed
                                            )
                                        }
                                    }
                                    DocumentSyncStatus.LOCAL_ONLY -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { onUploadToCloudClick() }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudUpload,
                                                contentDescription = null,
                                                tint = AmberWarning,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Local Only",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AmberWarning
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                    // Action buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activeDocument.syncStatus == DocumentSyncStatus.SYNCED &&
                            !isIndexing &&
                            indexedChunkCount == 0 &&
                            !activeDocument.id.startsWith("doc-sample-")
                        ) {
                            Surface(
                                onClick = onIndexClick,
                                shape = RoundedCornerShape(8.dp),
                                color = DocuMindBlueContainer,
                                border = BorderStroke(1.dp, DocuMindBluePrimary.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .testTag("reindex_document_button")
                                    .padding(end = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = DocuMindBluePrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Index Chunks",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DocuMindOnBlueContainer
                                    )
                                }
                            }
                        }

                        if (activeDocument.syncStatus == DocumentSyncStatus.LOCAL_ONLY || activeDocument.syncStatus == DocumentSyncStatus.FAILED) {
                            Surface(
                                onClick = onUploadToCloudClick,
                                shape = RoundedCornerShape(8.dp),
                                color = if (activeDocument.syncStatus == DocumentSyncStatus.FAILED) PdfRedContainer else DocuMindBlueContainer,
                                border = BorderStroke(
                                    1.dp,
                                    if (activeDocument.syncStatus == DocumentSyncStatus.FAILED) PdfRed.copy(alpha = 0.5f) else DocuMindBluePrimary.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .testTag("upload_to_supabase_button")
                                    .padding(end = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudUpload,
                                        contentDescription = null,
                                        tint = if (activeDocument.syncStatus == DocumentSyncStatus.FAILED) PdfRed else DocuMindBluePrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (activeDocument.syncStatus == DocumentSyncStatus.FAILED) "Retry Upload" else "Upload to Supabase",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeDocument.syncStatus == DocumentSyncStatus.FAILED) PdfRed else DocuMindOnBlueContainer
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onOpenDetails,
                            modifier = Modifier
                                .testTag("inspect_document_button")
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FindInPage,
                                contentDescription = "Inspect Outline",
                                tint = DocuMindBluePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onPickPdfClick,
                            modifier = Modifier
                                .testTag("replace_document_button")
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Change PDF",
                                tint = Slate600,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onClearDocument,
                            modifier = Modifier
                                .testTag("clear_document_button")
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Document",
                                tint = Slate400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Error detail banner if upload failed
                if (activeDocument.syncStatus == DocumentSyncStatus.FAILED && !activeDocument.syncErrorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = PdfRedContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Storage Error: ${activeDocument.syncErrorMessage}",
                            fontSize = 11.sp,
                            color = PdfRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }

                // Path display if synced
                if (activeDocument.syncStatus == DocumentSyncStatus.SYNCED && !activeDocument.storagePath.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Verified Storage Object: documents/${activeDocument.storagePath}",
                        fontSize = 10.sp,
                        color = Slate500
                    )
                }
            } else {
                // Empty / Upload State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DocuMindBlueContainer.copy(alpha = 0.5f))
                        .border(
                            BorderStroke(1.5.dp, DocuMindBluePrimary.copy(alpha = 0.4f)),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onPickPdfClick() }
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Upload PDF",
                                tint = DocuMindBluePrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Upload PDF Document",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Tap to browse files on your device (up to 50 MB)",
                            fontSize = 12.sp,
                            color = Slate600
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onPickPdfClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DocuMindBluePrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("upload_pdf_browse_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Choose PDF File", fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Storage Access Framework • Sample PDF available in Downloads",
                            fontSize = 11.sp,
                            color = Slate500
                        )
                    }
                }
            }

            // Quick Switch Sample Documents bar
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Samples:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate500
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                ) {
                    sampleDocuments.forEach { sample ->
                        val isSelected = activeDocument?.id == sample.id
                        Surface(
                            onClick = { onSelectSampleDoc(sample) },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) DocuMindBluePrimary else Slate100,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) DocuMindBluePrimary else Slate200
                            ),
                            modifier = Modifier.testTag("sample_doc_${sample.id}")
                        ) {
                            Text(
                                text = when (sample.id) {
                                    "doc-sample-1" -> "AI Framework (28p)"
                                    "doc-sample-2" -> "Q4 Financials (16p)"
                                    else -> "Clean Energy (42p)"
                                },
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Slate700,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

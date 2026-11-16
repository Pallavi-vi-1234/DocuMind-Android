package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.model.SupabaseUser
import com.example.model.DocumentInfo
import com.example.ui.theme.DocuMindBlueContainer
import com.example.ui.theme.DocuMindBluePrimary
import com.example.ui.theme.DocuMindOnBlueContainer
import com.example.ui.theme.PdfRed
import com.example.ui.theme.PdfRedContainer
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessEmerald

@Composable
fun DocuMindTopBar(
    activeDocument: DocumentInfo?,
    isSidebarOpen: Boolean,
    isShowcaseActive: Boolean,
    sessionCount: Int,
    currentUser: SupabaseUser?,
    onToggleSidebar: () -> Unit,
    onToggleShowcase: () -> Unit,
    onOpenDocumentDetails: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenConfigHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("top_app_bar"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Sidebar Toggle + DocuMind Brand
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    IconButton(
                        onClick = onToggleSidebar,
                        modifier = Modifier
                            .testTag("toggle_sidebar_button")
                            .size(44.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                if (sessionCount > 0) {
                                    Badge(
                                        containerColor = DocuMindBluePrimary,
                                        contentColor = Color.White
                                    ) {
                                        Text(text = "$sessionCount", fontSize = 10.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isSidebarOpen) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                                contentDescription = "Toggle Sidebar Navigation",
                                tint = DocuMindBluePrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Brand Icon & Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DocuMindBluePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "DocuMind",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(DocuMindBlueContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "AI DOCS",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DocuMindOnBlueContainer
                                    )
                                }
                            }
                            Text(
                                text = "Document Q&A Assistant",
                                fontSize = 11.sp,
                                color = Slate500,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Right: Active Document Pill & Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    // Document Details Shortcut Pill
                    if (activeDocument != null) {
                        Surface(
                            onClick = onOpenDocumentDetails,
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                            modifier = Modifier
                                .testTag("active_document_pill")
                                .padding(end = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(PdfRedContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "PDF",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PdfRed
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activeDocument.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Slate700,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 110.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "(${activeDocument.pageCount}p)",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }
                        }
                    }

                    // Supabase Storage / Config Status Button
                    IconButton(
                        onClick = onOpenConfigHelp,
                        modifier = Modifier
                            .testTag("supabase_status_button")
                            .size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "Supabase Status & Configuration",
                                tint = if (SupabaseConfig.isConfigured()) SuccessEmerald else Slate500,
                                modifier = Modifier.size(20.dp)
                            )
                            if (SupabaseConfig.isConfigured()) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(SuccessEmerald)
                                )
                            }
                        }
                    }

                    // Toggle Citation Layout Preview Button
                    IconButton(
                        onClick = onToggleShowcase,
                        modifier = Modifier
                            .testTag("toggle_showcase_button")
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isShowcaseActive) DocuMindBluePrimary else Color.Transparent
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Preview AI Answer & Page Citations",
                            tint = if (isShowcaseActive) Color.White else DocuMindBluePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Auth / Account Profile Button
                    IconButton(
                        onClick = onOpenAuth,
                        modifier = Modifier
                            .testTag("auth_profile_button")
                            .size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Account & Authentication",
                                tint = if (currentUser != null) DocuMindBluePrimary else Slate500,
                                modifier = Modifier.size(24.dp)
                            )
                            if (currentUser != null) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(SuccessEmerald)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onOpenDocumentDetails,
                        modifier = Modifier
                            .testTag("info_button")
                            .size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Document and App Information",
                            tint = Slate500,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Subtle divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Slate200)
            )
        }
    }
}

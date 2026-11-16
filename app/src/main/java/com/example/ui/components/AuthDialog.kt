package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.model.SupabaseUser
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningContainer
import com.example.ui.theme.DocuMindBlueContainer
import com.example.ui.theme.DocuMindBluePrimary
import com.example.ui.theme.DocuMindOnBlueContainer
import com.example.ui.theme.PdfRed
import com.example.ui.theme.PdfRedContainer
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.SuccessEmeraldContainer

@Composable
fun AuthDialog(
    currentUser: SupabaseUser?,
    isLoading: Boolean,
    errorMessage: String?,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onOpenConfigHelp: () -> Unit,
    onDismiss: () -> Unit,
    onSendPasswordReset: (String) -> Unit = {},
    onPasteRecoveryLink: (String) -> Unit = {},
    isForgotPasswordLoading: Boolean = false,
    forgotPasswordSuccessMessage: String? = null,
    forgotPasswordErrorMessage: String? = null,
    onClearAuthMessages: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isForgotPasswordMode by remember { mutableStateOf(false) }
    var showManualLinkInput by remember { mutableStateOf(false) }
    var pastedRecoveryLink by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("auth_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, Slate200),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DocuMindBluePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (currentUser != null) "User Profile" else "Supabase Account",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                text = "DocuMind Cloud Sync",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .testTag("close_auth_dialog_button")
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Slate500,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Connection status banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (SupabaseConfig.isConfigured()) SuccessEmeraldContainer else AmberWarningContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (SupabaseConfig.isConfigured()) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (SupabaseConfig.isConfigured()) SuccessEmerald else AmberWarning,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (SupabaseConfig.isConfigured()) "Supabase Connected" else "Credentials Not Set",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (SupabaseConfig.isConfigured()) SuccessEmerald else Slate800
                            )
                        }

                        if (!SupabaseConfig.isConfigured()) {
                            Text(
                                text = "Setup Guide",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DocuMindBluePrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("auth_setup_guide_button")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (currentUser != null) {
                    // Authenticated View
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Slate100,
                        border = BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "SIGNED IN AS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate500,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentUser.email ?: "Authenticated User",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate900
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "User ID: ${currentUser.id.take(12)}...",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PdfRed
                        ),
                        border = BorderStroke(1.dp, PdfRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("sign_out_button")
                    ) {
                        Text(text = "Sign Out of Supabase", fontWeight = FontWeight.SemiBold)
                    }
                } else if (isForgotPasswordMode) {
                    // Forgot Password / Recovery Email Flow
                    Text(
                        text = "Reset Your Password",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enter your account email to receive a secure password recovery link that works in your browser or DocuMind.",
                        fontSize = 12.sp,
                        color = Slate600,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        enabled = !isForgotPasswordLoading,
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (email.isNotBlank() && !isForgotPasswordLoading) {
                                    onSendPasswordReset(email.trim())
                                }
                            }
                        ),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DocuMindBluePrimary,
                            unfocusedBorderColor = Slate200
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_email_field")
                    )

                    if (!forgotPasswordErrorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PdfRedContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("forgot_password_error_banner")
                        ) {
                            Text(
                                text = forgotPasswordErrorMessage,
                                fontSize = 11.sp,
                                color = PdfRed,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    if (!forgotPasswordSuccessMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SuccessEmeraldContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("forgot_password_success_banner")
                        ) {
                            Text(
                                text = forgotPasswordSuccessMessage,
                                fontSize = 11.sp,
                                color = SuccessEmerald,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { onSendPasswordReset(email.trim()) },
                        enabled = !isForgotPasswordLoading && email.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DocuMindBluePrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("send_reset_link_button")
                    ) {
                        if (isForgotPasswordLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Send Reset Link",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            isForgotPasswordMode = false
                            showManualLinkInput = false
                            onClearAuthMessages()
                        },
                        enabled = !isForgotPasswordLoading,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("back_to_sign_in_button")
                    ) {
                        Text(
                            text = "Back to Sign In",
                            fontWeight = FontWeight.SemiBold,
                            color = Slate700
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (showManualLinkInput) "Hide manual recovery link input" else "Paste recovery URL or token in-app",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DocuMindBluePrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showManualLinkInput = !showManualLinkInput }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("toggle_manual_recovery_link")
                    )

                    if (showManualLinkInput) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pastedRecoveryLink,
                            onValueChange = { pastedRecoveryLink = it },
                            label = { Text("Paste https://, documind://, or token") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("paste_recovery_link_field")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onPasteRecoveryLink(pastedRecoveryLink.trim()) },
                            enabled = pastedRecoveryLink.isNotBlank() && !isForgotPasswordLoading,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_pasted_recovery_button")
                        ) {
                            Text(
                                text = "Verify Recovery Link",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    // Sign In / Sign Up Forms
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Slate100,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                selectedTab = 0
                                onClearAuthMessages()
                            },
                            text = { Text("Sign In", fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("tab_sign_in")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                                onClearAuthMessages()
                            },
                            text = { Text("Create Account", fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("tab_sign_up")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DocuMindBluePrimary,
                            unfocusedBorderColor = Slate200
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_field")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Password",
                                    tint = Slate400,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (selectedTab == 0) onSignIn(email, password) else onSignUp(email, password)
                            }
                        ),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DocuMindBluePrimary,
                            unfocusedBorderColor = Slate200
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_field")
                    )

                    if (selectedTab == 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Forgot password?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DocuMindBluePrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        isForgotPasswordMode = true
                                        onClearAuthMessages()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("forgot_password_link")
                            )
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PdfRedContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage,
                                fontSize = 11.sp,
                                color = PdfRed,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            if (selectedTab == 0) onSignIn(email, password) else onSignUp(email, password)
                        },
                        enabled = !isLoading && email.isNotBlank() && password.length >= 6,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DocuMindBluePrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auth_submit_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (selectedTab == 0) "Sign In" else "Create Account",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Credentials info helper link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Need help setting up Supabase keys?",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "View Instructions",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DocuMindBluePrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DocuMindBlueContainer)
                            .clickable { onOpenConfigHelp() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("view_config_instructions_link")
                    )
                }
            }
        }
    }
}

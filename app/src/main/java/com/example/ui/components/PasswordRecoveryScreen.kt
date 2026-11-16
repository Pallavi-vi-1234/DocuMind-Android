package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DocuMindBlueContainer
import com.example.ui.theme.DocuMindBluePrimary
import com.example.ui.theme.DocuMindOnBlueContainer
import com.example.ui.theme.PdfRed
import com.example.ui.theme.PdfRedContainer
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.SuccessEmeraldContainer

sealed interface PasswordRecoveryState {
    data object Inactive : PasswordRecoveryState
    data object VerifyingLink : PasswordRecoveryState
    data class ReadyToReset(
        val email: String?,
        val isSubmitting: Boolean = false,
        val errorMessage: String? = null
    ) : PasswordRecoveryState

    data class InvalidOrExpired(
        val message: String,
        val isSendingEmail: Boolean = false,
        val resendSuccessMessage: String? = null,
        val resendErrorMessage: String? = null
    ) : PasswordRecoveryState

    data class ResetSuccess(
        val email: String?
    ) : PasswordRecoveryState
}

@Composable
fun PasswordRecoveryScreen(
    state: PasswordRecoveryState,
    onUpdatePassword: (String) -> Unit,
    onRequestNewResetLink: (String) -> Unit,
    onCancelRecovery: () -> Unit,
    onContinueAfterReset: () -> Unit,
    onOpenSignIn: () -> Unit,
    onReturnToSignIn: () -> Unit = onOpenSignIn,
    modifier: Modifier = Modifier
) {
    if (state is PasswordRecoveryState.Inactive) return

    BackHandler {
        when (state) {
            is PasswordRecoveryState.ResetSuccess -> onContinueAfterReset()
            else -> onCancelRecovery()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("password_recovery_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Branded DocuMind Recovery Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                border = BorderStroke(1.dp, Slate200),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DocuMindBluePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockReset,
                                contentDescription = "DocuMind",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DocuMind",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                text = "Account Password Recovery",
                                fontSize = 11.sp,
                                color = Slate500
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = DocuMindBlueContainer
                    ) {
                        Text(
                            text = "Secure Reset",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DocuMindOnBlueContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 460.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, Slate200),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (state) {
                            PasswordRecoveryState.Inactive -> Unit

                            PasswordRecoveryState.VerifyingLink -> {
                                VerifyingRecoveryLinkContent(onCancel = onCancelRecovery)
                            }

                            is PasswordRecoveryState.ReadyToReset -> {
                                ReadyToResetContent(
                                    state = state,
                                    onUpdatePassword = onUpdatePassword,
                                    onCancel = onCancelRecovery
                                )
                            }

                            is PasswordRecoveryState.InvalidOrExpired -> {
                                InvalidOrExpiredContent(
                                    state = state,
                                    onRequestNewResetLink = onRequestNewResetLink,
                                    onOpenSignIn = onOpenSignIn,
                                    onCancel = onCancelRecovery
                                )
                            }

                            is PasswordRecoveryState.ResetSuccess -> {
                                ResetSuccessContent(
                                    email = state.email,
                                    onReturnToSignIn = onReturnToSignIn,
                                    onContinue = onContinueAfterReset
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerifyingRecoveryLinkContent(onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(DocuMindBlueContainer),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = DocuMindBluePrimary,
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp
        )
    }

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text = "Verifying Recovery Link",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Slate900,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Confirming your Supabase recovery session before allowing a password update...",
        fontSize = 13.sp,
        color = Slate600,
        textAlign = TextAlign.Center,
        lineHeight = 19.sp
    )

    Spacer(modifier = Modifier.height(22.dp))

    OutlinedButton(
        onClick = onCancel,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("recovery_cancel_button")
    ) {
        Text(text = "Cancel", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ReadyToResetContent(
    state: PasswordRecoveryState.ReadyToReset,
    onUpdatePassword: (String) -> Unit,
    onCancel: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNewPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }

    val meetsMinLength = newPassword.length >= 8
    val passwordsMatch = newPassword == confirmPassword && confirmPassword.isNotEmpty()
    val canSubmit = !state.isSubmitting && meetsMinLength && passwordsMatch

    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(DocuMindBlueContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.LockReset,
            contentDescription = null,
            tint = DocuMindBluePrimary,
            modifier = Modifier.size(30.dp)
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = "Set a New Password",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Slate900,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(6.dp))

    if (!state.email.isNullOrBlank()) {
        Surface(
            color = DocuMindBlueContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "Verified account: ${state.email}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = DocuMindOnBlueContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
    }

    Text(
        text = "Choose a strong new password for your DocuMind Supabase account.",
        fontSize = 13.sp,
        color = Slate600,
        textAlign = TextAlign.Center,
        lineHeight = 18.sp
    )

    Spacer(modifier = Modifier.height(18.dp))

    OutlinedTextField(
        value = newPassword,
        onValueChange = { newPassword = it },
        enabled = !state.isSubmitting,
        label = { Text("New Password") },
        leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            IconButton(
                onClick = { showNewPassword = !showNewPassword },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle New Password Visibility",
                    tint = Slate400,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DocuMindBluePrimary,
            unfocusedBorderColor = Slate200
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recovery_new_password_field")
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedTextField(
        value = confirmPassword,
        onValueChange = { confirmPassword = it },
        enabled = !state.isSubmitting,
        label = { Text("Confirm New Password") },
        leadingIcon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            IconButton(
                onClick = { showConfirmPassword = !showConfirmPassword },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle Confirm Password Visibility",
                    tint = Slate400,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                if (canSubmit) onUpdatePassword(newPassword)
            }
        ),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DocuMindBluePrimary,
            unfocusedBorderColor = Slate200
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recovery_confirm_password_field")
    )

    Spacer(modifier = Modifier.height(10.dp))

    // Password requirements checklist
    val passwordsMismatch = confirmPassword.isNotEmpty() && newPassword != confirmPassword
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PasswordRuleRow(
            satisfied = meetsMinLength,
            text = "At least 8 characters"
        )
        PasswordRuleRow(
            satisfied = passwordsMatch,
            text = if (passwordsMismatch) "Passwords do not match" else "Both password fields match",
            isError = passwordsMismatch,
            modifier = if (passwordsMismatch) Modifier.testTag("recovery_mismatch_error") else Modifier
        )
    }

    if (!state.errorMessage.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = PdfRedContainer,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recovery_error_banner")
        ) {
            Text(
                text = state.errorMessage,
                fontSize = 12.sp,
                color = PdfRed,
                modifier = Modifier.padding(10.dp),
                lineHeight = 17.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    Button(
        onClick = { onUpdatePassword(newPassword) },
        enabled = canSubmit,
        colors = ButtonDefaults.buttonColors(
            containerColor = DocuMindBluePrimary,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("recovery_update_password_button")
    ) {
        if (state.isSubmitting) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(text = "Update Password", fontWeight = FontWeight.SemiBold)
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedButton(
        onClick = onCancel,
        enabled = !state.isSubmitting,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("recovery_cancel_button")
    ) {
        Text(text = "Cancel", fontWeight = FontWeight.SemiBold, color = Slate700)
    }
}

@Composable
private fun InvalidOrExpiredContent(
    state: PasswordRecoveryState.InvalidOrExpired,
    onRequestNewResetLink: (String) -> Unit,
    onOpenSignIn: () -> Unit,
    onCancel: () -> Unit
) {
    var emailInput by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(PdfRedContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = PdfRed,
            modifier = Modifier.size(30.dp)
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = "Recovery Link Unavailable",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Slate900,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = PdfRedContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, PdfRed.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = state.message,
            fontSize = 13.sp,
            color = PdfRed,
            lineHeight = 19.sp,
            modifier = Modifier.padding(12.dp)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Enter your email address to send a fresh recovery link:",
        fontSize = 12.sp,
        color = Slate600,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = emailInput,
        onValueChange = { emailInput = it },
        enabled = !state.isSendingEmail,
        label = { Text("Account Email") },
        leadingIcon = {
            Icon(Icons.Default.Email, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp))
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                if (emailInput.isNotBlank() && !state.isSendingEmail) {
                    onRequestNewResetLink(emailInput.trim())
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
            .testTag("recovery_resend_email_field")
    )

    if (!state.resendSuccessMessage.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = SuccessEmeraldContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = state.resendSuccessMessage,
                fontSize = 12.sp,
                color = SuccessEmerald,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    if (!state.resendErrorMessage.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = PdfRedContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = state.resendErrorMessage,
                fontSize = 12.sp,
                color = PdfRed,
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Button(
        onClick = { onRequestNewResetLink(emailInput.trim()) },
        enabled = emailInput.isNotBlank() && !state.isSendingEmail,
        colors = ButtonDefaults.buttonColors(
            containerColor = DocuMindBluePrimary,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("recovery_resend_button")
    ) {
        if (state.isSendingEmail) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(text = "Send New Recovery Link", fontWeight = FontWeight.SemiBold)
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onOpenSignIn,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("recovery_back_to_signin_button")
        ) {
            Text(text = "Sign In", fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("recovery_cancel_button")
        ) {
            Text(text = "Close", fontWeight = FontWeight.SemiBold, color = Slate700)
        }
    }
}

@Composable
private fun ResetSuccessContent(
    email: String?,
    onReturnToSignIn: () -> Unit,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(SuccessEmeraldContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = SuccessEmerald,
            modifier = Modifier.size(34.dp)
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Password Updated Successfully",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Slate900,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("recovery_success_title")
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = if (!email.isNullOrBlank()) {
            "Your password for $email has been securely updated. You can now sign in to DocuMind with your new password."
        } else {
            "Your password has been securely updated. You can now sign in to DocuMind with your new password."
        },
        fontSize = 13.sp,
        color = Slate600,
        textAlign = TextAlign.Center,
        lineHeight = 19.sp
    )

    Spacer(modifier = Modifier.height(22.dp))

    Button(
        onClick = onReturnToSignIn,
        colors = ButtonDefaults.buttonColors(
            containerColor = DocuMindBluePrimary,
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("recovery_return_to_signin_button")
    ) {
        Text(text = "Return to DocuMind Sign In", fontWeight = FontWeight.SemiBold)
    }

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedButton(
        onClick = onContinue,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("recovery_continue_button")
    ) {
        Text(text = "Continue to DocuMind", fontWeight = FontWeight.SemiBold, color = Slate700)
    }
}

@Composable
private fun PasswordRuleRow(
    satisfied: Boolean,
    text: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tintColor = when {
        isError -> PdfRed
        satisfied -> SuccessEmerald
        else -> Slate400
    }
    val textColor = when {
        isError -> PdfRed
        satisfied -> SuccessEmerald
        else -> Slate500
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = textColor,
            fontWeight = if (satisfied || isError) FontWeight.Medium else FontWeight.Normal
        )
    }
}

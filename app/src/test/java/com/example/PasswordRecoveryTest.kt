package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.supabase.AuthDeepLinkParser
import com.example.data.supabase.DeepLinkParseResult
import com.example.data.supabase.SupabaseAuthManager
import com.example.data.supabase.SupabaseConfig
import com.example.ui.DocuMindApp
import com.example.ui.components.AuthDialog
import com.example.ui.components.PasswordRecoveryScreen
import com.example.ui.components.PasswordRecoveryState
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DocuMindViewModel
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PasswordRecoveryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun mockHttpClient(
        handler: (okhttp3.Request) -> Pair<Int, String>
    ): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val (code, json) = handler(req)
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    private fun readRequestBody(request: okhttp3.Request): String {
        val buffer = Buffer()
        request.body?.writeTo(buffer)
        return buffer.readUtf8()
    }

    @Test
    fun `manifest resolves documind reset-password, sign-in, and https callback to MainActivity`() {
        val ctx = context()
        val deepLinks = listOf(
            SupabaseConfig.CUSTOM_SCHEME_REDIRECT_URL,
            SupabaseConfig.SIGN_IN_DEEP_LINK_URL,
            "https://guileless-dragon-6f6060.netlify.app",
            "https://blmhmgjmjpdrqvewaeym.supabase.co/functions/v1/reset-password"
        )
        deepLinks.forEach { link ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val resolved = ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            assertTrue(
                "Expected MainActivity to resolve $link, got ${resolved?.activityInfo?.name}",
                resolved?.activityInfo?.name == MainActivity::class.java.name
            )
        }
    }

    @Test
    fun `deep link parser handles https callback URLs and sign-in return link`() {
        val netlifyTokens = AuthDeepLinkParser.parse(
            Uri.parse("https://guileless-dragon-6f6060.netlify.app#access_token=https-jwt-1&refresh_token=ref-h1&type=recovery")
        )
        assertTrue(netlifyTokens is DeepLinkParseResult.RecoveryTokens)
        assertEquals("https-jwt-1", (netlifyTokens as DeepLinkParseResult.RecoveryTokens).accessToken)

        val netlifyHash = AuthDeepLinkParser.parse(
            Uri.parse("https://guileless-dragon-6f6060.netlify.app/?token_hash=hash_https_1&type=recovery")
        )
        assertEquals(
            DeepLinkParseResult.RecoveryTokenHash(tokenHash = "hash_https_1", type = "recovery"),
            netlifyHash
        )

        val httpsExpired = AuthDeepLinkParser.parse(
            Uri.parse("https://guileless-dragon-6f6060.netlify.app#error=access_denied&error_code=otp_expired")
        )
        assertTrue(httpsExpired is DeepLinkParseResult.RecoveryError)
        assertTrue((httpsExpired as DeepLinkParseResult.RecoveryError).message.contains("expired", ignoreCase = true))

        val signInLink = AuthDeepLinkParser.parse(Uri.parse("documind://sign-in"))
        assertEquals(DeepLinkParseResult.ReturnToSignIn, signInLink)

        val rawParsed = AuthDeepLinkParser.parseRawUrl(
            "https://guileless-dragon-6f6060.netlify.app?token=raw_tok&type=recovery"
        )
        assertEquals(
            DeepLinkParseResult.RecoveryTokenHash(tokenHash = "raw_tok", type = "recovery"),
            rawParsed
        )
    }

    @Test
    fun `deep link parser handles valid recovery fragment tokens`() {
        val uri = Uri.parse(
            "documind://reset-password#access_token=rec-jwt-123&expires_in=3600&refresh_token=ref-999&token_type=bearer&type=recovery"
        )
        val result = AuthDeepLinkParser.parse(uri)
        assertTrue(result is DeepLinkParseResult.RecoveryTokens)
        val tokens = result as DeepLinkParseResult.RecoveryTokens
        assertEquals("rec-jwt-123", tokens.accessToken)
        assertEquals("ref-999", tokens.refreshToken)
        assertEquals(3600L, tokens.expiresInSeconds)
    }

    @Test
    fun `deep link parser handles token_hash query parameters`() {
        val uri = Uri.parse("documind://reset-password?token_hash=pk_hash_abc&type=recovery")
        val result = AuthDeepLinkParser.parse(uri)
        assertEquals(
            DeepLinkParseResult.RecoveryTokenHash(tokenHash = "pk_hash_abc", type = "recovery"),
            result
        )
    }

    @Test
    fun `deep link parser handles expired and cancelled recovery links gracefully`() {
        val expiredUri = Uri.parse(
            "documind://reset-password#error=access_denied&error_code=otp_expired&error_description=Email+link+is+invalid+or+has+expired"
        )
        val expired = AuthDeepLinkParser.parse(expiredUri)
        assertTrue(expired is DeepLinkParseResult.RecoveryError)
        assertTrue((expired as DeepLinkParseResult.RecoveryError).message.contains("expired", ignoreCase = true))

        val cancelledUri = Uri.parse("documind://reset-password#error=access_denied&error_code=user_cancelled")
        val cancelled = AuthDeepLinkParser.parse(cancelledUri)
        assertTrue(cancelled is DeepLinkParseResult.RecoveryError)
        assertTrue((cancelled as DeepLinkParseResult.RecoveryError).message.contains("cancelled", ignoreCase = true))

        val emptyUri = Uri.parse("documind://reset-password")
        assertEquals(DeepLinkParseResult.CancelledOrEmpty, AuthDeepLinkParser.parse(emptyUri))

        val wrongTypeUri = Uri.parse("documind://reset-password#access_token=abc&type=signup")
        val wrongType = AuthDeepLinkParser.parse(wrongTypeUri)
        assertTrue(wrongType is DeepLinkParseResult.RecoveryError)

        val unrelatedUri = Uri.parse("https://example.com/other")
        assertEquals(DeepLinkParseResult.Ignored, AuthDeepLinkParser.parse(unrelatedUri))
    }

    @Test
    fun `sendPasswordResetEmail sends recover request with https callback redirect_to`() = runTest {
        var capturedUrl = ""
        var capturedMethod = ""
        var capturedBody = ""
        var capturedApiKey = ""

        val client = mockHttpClient { req ->
            capturedUrl = req.url.toString()
            capturedMethod = req.method
            capturedBody = readRequestBody(req)
            capturedApiKey = req.header("apikey").orEmpty()
            200 to "{}"
        }

        val manager = SupabaseAuthManager(
            context = context(),
            client = client,
            isConfiguredProvider = { true },
            baseUrlProvider = { "https://example.supabase.co/" },
            anonKeyProvider = { "anon-public-key" }
        )

        assertEquals("https://guileless-dragon-6f6060.netlify.app", SupabaseConfig.getPasswordResetRedirectUrl())
        val result = manager.sendPasswordResetEmail("alice@example.com")
        assertTrue(result.isSuccess)
        assertEquals("POST", capturedMethod)
        assertEquals("anon-public-key", capturedApiKey)
        assertTrue(
            "Expected Netlify redirect_to in URL $capturedUrl",
            capturedUrl == "https://example.supabase.co/auth/v1/recover?redirect_to=https%3A%2F%2Fguileless-dragon-6f6060.netlify.app"
        )
        assertTrue(capturedBody.contains("alice@example.com"))
    }

    @Test
    fun `sendPasswordResetEmail supports custom scheme fallback redirect_to`() = runTest {
        var capturedUrl = ""

        val client = mockHttpClient { req ->
            capturedUrl = req.url.toString()
            200 to "{}"
        }

        val manager = SupabaseAuthManager(
            context = context(),
            client = client,
            isConfiguredProvider = { true },
            baseUrlProvider = { "https://example.supabase.co/" },
            anonKeyProvider = { "anon-public-key" },
            passwordResetRedirectUrlProvider = { SupabaseConfig.CUSTOM_SCHEME_REDIRECT_URL }
        )

        val result = manager.sendPasswordResetEmail("alice@example.com")
        assertTrue(result.isSuccess)
        assertEquals(
            "https://example.supabase.co/auth/v1/recover?redirect_to=documind%3A%2F%2Freset-password",
            capturedUrl
        )
    }

    @Test
    fun `establishSessionFromRecoveryToken and updateUserPassword call Supabase endpoints`() = runTest {
        val requests = mutableListOf<Triple<String, String, String>>()
        val client = mockHttpClient { req ->
            val path = req.url.encodedPath
            requests.add(Triple(req.method, path, req.header("Authorization").orEmpty()))
            when {
                req.method == "GET" && path == "/auth/v1/user" ->
                    200 to """{"id":"user-uuid-1","email":"alice@example.com"}"""
                req.method == "PUT" && path == "/auth/v1/user" ->
                    200 to """{"id":"user-uuid-1","email":"alice@example.com"}"""
                else ->
                    400 to """{"msg":"Unexpected endpoint $path"}"""
            }
        }

        val manager = SupabaseAuthManager(
            context = context(),
            client = client,
            isConfiguredProvider = { true },
            baseUrlProvider = { "https://example.supabase.co/" },
            anonKeyProvider = { "anon-public-key" }
        )

        val sessionRes = manager.establishSessionFromRecoveryToken("rec-token-xyz", "ref-1")
        assertTrue(sessionRes.isSuccess)
        assertEquals("alice@example.com", sessionRes.getOrNull()?.email)
        assertTrue(manager.hasValidSession())

        val updateRes = manager.updateUserPassword("NewSecurePass123")
        assertTrue(updateRes.isSuccess)
        assertEquals("user-uuid-1", updateRes.getOrNull()?.id)

        assertEquals(2, requests.size)
        assertEquals(Triple("GET", "/auth/v1/user", "Bearer rec-token-xyz"), requests[0])
        assertEquals(Triple("PUT", "/auth/v1/user", "Bearer rec-token-xyz"), requests[1])
    }

    @Test
    fun `establishSessionFromRecoveryToken rejects expired 401 token`() = runTest {
        val client = mockHttpClient { _ ->
            401 to """{"msg":"invalid JWT: token is expired"}"""
        }

        val manager = SupabaseAuthManager(
            context = context(),
            client = client,
            isConfiguredProvider = { true },
            baseUrlProvider = { "https://example.supabase.co/" },
            anonKeyProvider = { "anon-public-key" }
        )
        manager.signOut()

        val result = manager.establishSessionFromRecoveryToken("expired-token")
        assertTrue(result.isFailure)
        assertFalse(manager.hasValidSession())
        assertTrue(result.exceptionOrNull()?.message?.contains("expired", ignoreCase = true) == true)
    }

    @Test
    fun `verifyRecoveryTokenHash exchanges hash via POST auth v1 verify`() = runTest {
        var capturedPath = ""
        var capturedBody = ""
        val client = mockHttpClient { req ->
            capturedPath = req.url.encodedPath
            capturedBody = readRequestBody(req)
            200 to """{"access_token":"verified-jwt","refresh_token":"ref-2","user":{"id":"u-2","email":"bob@example.com"}}"""
        }

        val manager = SupabaseAuthManager(
            context = context(),
            client = client,
            isConfiguredProvider = { true },
            baseUrlProvider = { "https://example.supabase.co/" },
            anonKeyProvider = { "anon-public-key" }
        )

        val result = manager.verifyRecoveryTokenHash("hash_987", "recovery")
        assertTrue(result.isSuccess)
        assertEquals("/auth/v1/verify", capturedPath)
        assertTrue(capturedBody.contains("hash_987"))
        assertTrue(capturedBody.contains("recovery"))
        assertEquals("verified-jwt", manager.getAccessToken())
    }

    @Test
    fun `verifyRecoveryTokenHash rejects invalid or expired token_hash`() = runTest {
        val client = mockHttpClient { _ ->
            403 to """{"msg":"Email link is invalid or has expired","error_code":"otp_expired"}"""
        }

        val manager = SupabaseAuthManager(
            context = context(),
            client = client,
            isConfiguredProvider = { true },
            baseUrlProvider = { "https://example.supabase.co/" },
            anonKeyProvider = { "anon-public-key" }
        )
        manager.signOut()

        val result = manager.verifyRecoveryTokenHash("bad_hash_000", "recovery")
        assertTrue(result.isFailure)
        assertFalse(manager.hasValidSession())
        assertTrue(result.exceptionOrNull()?.message?.contains("invalid or has expired", ignoreCase = true) == true)
    }

    @Test
    fun `password recovery screen validates password rules and submits update`() {
        var submittedPassword: String? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                PasswordRecoveryScreen(
                    state = PasswordRecoveryState.ReadyToReset(email = "alice@example.com"),
                    onUpdatePassword = { submittedPassword = it },
                    onRequestNewResetLink = {},
                    onCancelRecovery = {},
                    onContinueAfterReset = {},
                    onOpenSignIn = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("recovery_update_password_button").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("recovery_new_password_field").performTextInput("Short1")
        composeTestRule.onNodeWithTag("recovery_confirm_password_field").performTextInput("Short1")
        composeTestRule.onNodeWithTag("recovery_update_password_button").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("recovery_new_password_field").performTextInput("2345")
        composeTestRule.onNodeWithTag("recovery_confirm_password_field").performTextInput("9999")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recovery_mismatch_error")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("recovery_update_password_button").assertIsNotEnabled()

        // Replace both fields with valid matching 8+ character password
        composeTestRule.onNodeWithTag("recovery_new_password_field")
            .performScrollTo()
            .performTextReplacement("StrongPass123")
        composeTestRule.onNodeWithTag("recovery_confirm_password_field")
            .performScrollTo()
            .performTextReplacement("StrongPass123")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recovery_update_password_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals("StrongPass123", submittedPassword)
    }

    @Test
    fun `password recovery screen handles expired link resend`() {
        var resentEmail: String? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                PasswordRecoveryScreen(
                    state = PasswordRecoveryState.InvalidOrExpired(
                        message = "This password recovery link has expired."
                    ),
                    onUpdatePassword = {},
                    onRequestNewResetLink = { resentEmail = it },
                    onCancelRecovery = {},
                    onContinueAfterReset = {},
                    onOpenSignIn = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("This password recovery link has expired.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("recovery_resend_button")
            .performScrollTo()
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag("recovery_resend_email_field")
            .performScrollTo()
            .performTextInput("alice@example.com")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recovery_resend_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals("alice@example.com", resentEmail)
    }

    @Test
    fun `password recovery screen shows Password Updated Successfully and Return to DocuMind Sign In`() {
        var continued = false
        var returnedToSignIn = false

        composeTestRule.setContent {
            MyApplicationTheme {
                PasswordRecoveryScreen(
                    state = PasswordRecoveryState.ResetSuccess(email = "alice@example.com"),
                    onUpdatePassword = {},
                    onRequestNewResetLink = {},
                    onCancelRecovery = {},
                    onContinueAfterReset = { continued = true },
                    onOpenSignIn = {},
                    onReturnToSignIn = { returnedToSignIn = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("recovery_success_title")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Password Updated Successfully")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("recovery_return_to_signin_button")
            .performScrollTo()
            .performClick()
        assertTrue(returnedToSignIn)

        composeTestRule.onNodeWithTag("recovery_continue_button")
            .performScrollTo()
            .performClick()
        assertTrue(continued)
    }

    @Test
    fun `auth dialog forgot password flow triggers reset callback`() {
        var resetRequestedFor: String? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                AuthDialog(
                    currentUser = null,
                    isLoading = false,
                    errorMessage = null,
                    onSignIn = { _, _ -> },
                    onSignUp = { _, _ -> },
                    onSignOut = {},
                    onOpenConfigHelp = {},
                    onDismiss = {},
                    onSendPasswordReset = { resetRequestedFor = it }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("forgot_password_link").performClick()
        composeTestRule.onNodeWithTag("reset_email_field").performTextInput("alice@example.com")
        composeTestRule.onNodeWithTag("send_reset_link_button").performClick()

        assertEquals("alice@example.com", resetRequestedFor)
    }

    @Test
    fun `app_name is DocuMind and never Untitled`() {
        val appName = context().getString(R.string.app_name)
        assertEquals("DocuMind", appName)
        assertFalse(appName.equals("Untitled", ignoreCase = true))

        val activityInfo = context().packageManager.getActivityInfo(
            android.content.ComponentName(context(), MainActivity::class.java),
            0
        )
        assertEquals("DocuMind", activityInfo.loadLabel(context().packageManager).toString())
    }

    @Test
    fun `deep link parser handles triple slash token param and unknown documind host`() {
        val tripleSlash = Uri.parse("documind:///reset-password#access_token=triple-jwt&type=recovery")
        val tripleResult = AuthDeepLinkParser.parse(tripleSlash)
        assertTrue(tripleResult is DeepLinkParseResult.RecoveryTokens)
        assertEquals("triple-jwt", (tripleResult as DeepLinkParseResult.RecoveryTokens).accessToken)

        val tokenQuery = Uri.parse("documind://reset-password?token=legacy_tok_123&type=recovery")
        assertEquals(
            DeepLinkParseResult.RecoveryTokenHash(tokenHash = "legacy_tok_123", type = "recovery"),
            AuthDeepLinkParser.parse(tokenQuery)
        )

        val unknownHost = Uri.parse("documind://unknown-route")
        val unknownResult = AuthDeepLinkParser.parse(unknownHost)
        assertTrue(unknownResult is DeepLinkParseResult.RecoveryError)
    }

    @Test
    fun `cold start deep link renders PasswordRecoveryScreen via DocuMindViewModel instead of blank screen`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = mockHttpClient { req ->
            when {
                req.method == "GET" && req.url.encodedPath == "/auth/v1/user" ->
                    200 to """{"id":"user-cold-1","email":"coldstart@example.com"}"""
                else ->
                    404 to """{"msg":"not found"}"""
            }
        }
        val authManager = SupabaseAuthManager(
            context = app,
            client = client,
            isConfiguredProvider = { true },
            baseUrlProvider = { "https://example.supabase.co/" },
            anonKeyProvider = { "anon-public-key" }
        )
        val viewModel = DocuMindViewModel(app, authManager = authManager)

        val coldIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("documind://reset-password#access_token=cold-jwt-token&refresh_token=cold-ref&type=recovery")
        )
        viewModel.handleDeepLinkIntent(coldIntent)

        composeTestRule.setContent {
            MyApplicationTheme {
                DocuMindApp(viewModel = viewModel)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("recovery_new_password_field").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("password_recovery_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Set a New Password").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Verified account: coldstart@example.com").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("recovery_update_password_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `warm start onNewIntent and repeated empty link show PasswordRecoveryScreen instead of blank screen`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val authManager = SupabaseAuthManager(
            context = app,
            client = mockHttpClient { _ -> 401 to """{"msg":"expired"}""" },
            isConfiguredProvider = { true },
            baseUrlProvider = { "https://example.supabase.co/" },
            anonKeyProvider = { "anon-public-key" }
        )
        val viewModel = DocuMindViewModel(app, authManager = authManager)

        composeTestRule.setContent {
            MyApplicationTheme {
                DocuMindApp(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("documind_root").assertIsDisplayed()

        // Warm start: deliver expired recovery link via Intent
        val expiredIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("documind://reset-password#error=access_denied&error_code=otp_expired")
        )
        viewModel.handleDeepLinkIntent(expiredIntent, forceReprocess = true)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("password_recovery_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recovery Link Unavailable").assertIsDisplayed()

        // Cancel back to workspace, then deliver same URI again (simulating clicking email link a second time)
        viewModel.cancelPasswordRecovery()
        composeTestRule.waitForIdle()

        viewModel.handleDeepLinkIntent(expiredIntent, forceReprocess = true)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("password_recovery_screen").assertIsDisplayed()

        // Empty ACTION_VIEW intent with null data must still display clear error screen, never blank
        viewModel.cancelPasswordRecovery()
        val emptyViewIntent = Intent(Intent.ACTION_VIEW)
        viewModel.handleDeepLinkIntent(emptyViewIntent, forceReprocess = true)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("password_recovery_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recovery Link Unavailable").assertIsDisplayed()
    }

    @Test
    fun `MainActivity cold start and onNewIntent route deep link without blank screen`() {
        val coldIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("documind://reset-password#error=access_denied&error_code=otp_expired")
        )
        val controller = Robolectric.buildActivity(MainActivity::class.java, coldIntent).setup()
        ShadowLooper.idleMainLooper()

        val activity = controller.get()
        assertEquals("DocuMind", activity.packageManager.getActivityInfo(activity.componentName, 0).loadLabel(activity.packageManager).toString())

        val vm = androidx.lifecycle.ViewModelProvider(activity)[DocuMindViewModel::class.java]
        assertTrue(vm.uiState.value.passwordRecoveryState is PasswordRecoveryState.InvalidOrExpired)

        vm.cancelPasswordRecovery()
        assertTrue(vm.uiState.value.passwordRecoveryState is PasswordRecoveryState.Inactive)

        // Simulate warm start onNewIntent
        val warmIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("documind://reset-password")
        )
        controller.newIntent(warmIntent)
        ShadowLooper.idleMainLooper()
        assertTrue(vm.uiState.value.passwordRecoveryState is PasswordRecoveryState.InvalidOrExpired)
    }

    @Test
    fun `end to end https recovery flow updates password and returns to DocuMind Sign In`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = mockHttpClient { req ->
            when {
                req.method == "GET" && req.url.encodedPath == "/auth/v1/user" ->
                    200 to """{"id":"user-https-1","email":"browser@example.com"}"""
                req.method == "PUT" && req.url.encodedPath == "/auth/v1/user" ->
                    200 to """{"id":"user-https-1","email":"browser@example.com"}"""
                req.method == "POST" && req.url.encodedPath == "/auth/v1/logout" ->
                    204 to ""
                else ->
                    404 to """{"msg":"not found"}"""
            }
        }
        val authManager = SupabaseAuthManager(
            context = app,
            client = client,
            isConfiguredProvider = { true },
            baseUrlProvider = { "https://blmhmgjmjpdrqvewaeym.supabase.co/" },
            anonKeyProvider = { "anon-public-key" }
        )
        val viewModel = DocuMindViewModel(app, authManager = authManager)

        viewModel.handlePastedRecoveryInput(
            "https://guileless-dragon-6f6060.netlify.app#access_token=https-valid-jwt&refresh_token=ref-h&type=recovery"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                DocuMindApp(viewModel = viewModel)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("recovery_new_password_field").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("recovery_new_password_field")
            .performScrollTo()
            .performTextInput("NewValidPass123!")
        composeTestRule.onNodeWithTag("recovery_confirm_password_field")
            .performScrollTo()
            .performTextInput("NewValidPass123!")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("recovery_update_password_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("recovery_success_title").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Password Updated Successfully").assertIsDisplayed()

        composeTestRule.onNodeWithTag("recovery_return_to_signin_button")
            .performScrollTo()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("auth_submit_button").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(viewModel.uiState.value.isAuthDialogOpen)
        assertTrue(viewModel.uiState.value.passwordRecoveryState is PasswordRecoveryState.Inactive)
    }

    @Test
    fun `invalid recovery token_hash via ViewModel shows clear error and Return to Sign In link opens AuthDialog`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val client = mockHttpClient { req ->
            when {
                req.method == "POST" && req.url.encodedPath == "/auth/v1/verify" ->
                    400 to """{"msg":"Token has expired or is invalid"}"""
                else ->
                    404 to """{"msg":"not found"}"""
            }
        }
        val authManager = SupabaseAuthManager(
            context = app,
            client = client,
            isConfiguredProvider = { true },
            baseUrlProvider = { "https://blmhmgjmjpdrqvewaeym.supabase.co/" },
            anonKeyProvider = { "anon-public-key" }
        )
        val viewModel = DocuMindViewModel(app, authManager = authManager)

        viewModel.handlePastedRecoveryInput(
            "https://guileless-dragon-6f6060.netlify.app?token_hash=invalid_hash&type=recovery"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                DocuMindApp(viewModel = viewModel)
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("recovery_resend_email_field").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Token has expired or is invalid").performScrollTo().assertIsDisplayed()

        // Clicking Return to DocuMind Sign In deep link returns user to AuthDialog
        viewModel.handleDeepLinkIntent(
            Intent(Intent.ACTION_VIEW, Uri.parse("documind://sign-in")),
            forceReprocess = true
        )
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("auth_submit_button").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(viewModel.uiState.value.isAuthDialogOpen)
    }

    @Test
    fun `browser callback artifacts contain no service_role key or token logging`() {
        val rootDir = generateSequence(java.io.File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .firstOrNull { java.io.File(it, "supabase/functions/reset-password/index.ts").exists() }
        assertTrue("Expected workspace root containing supabase/functions/reset-password/index.ts", rootDir != null)

        val edgeFn = java.io.File(rootDir!!, "supabase/functions/reset-password/index.ts").readText()
        val staticHtml = java.io.File(rootDir, "web/reset-password.html").readText()
        val configToml = java.io.File(rootDir, "supabase/config.toml").readText()

        assertTrue(configToml.contains("verify_jwt = false"))
        listOf(edgeFn, staticHtml).forEach { content ->
            val codeWithoutComments = content.lines()
                .filterNot { it.trimStart().startsWith("//") }
                .joinToString("\n")
            assertFalse(
                "Callback code must not reference service_role key",
                codeWithoutComments.contains("service_role", ignoreCase = true)
            )
            assertFalse("Callback must not call console.log", codeWithoutComments.contains("console.log("))
            assertTrue("Callback must show Password Updated Successfully", content.contains("Password Updated Successfully"))
            assertTrue("Callback must have Return to DocuMind Sign In action", content.contains("Return to DocuMind Sign In"))
        }
    }
}

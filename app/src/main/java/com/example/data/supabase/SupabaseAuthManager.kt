package com.example.data.supabase

import android.content.Context
import android.content.SharedPreferences
import com.example.data.supabase.model.AuthSessionResponse
import com.example.data.supabase.model.SupabaseUser
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

class SupabaseAuthManager(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
    private val isConfiguredProvider: () -> Boolean = { SupabaseConfig.isConfigured() },
    private val baseUrlProvider: () -> String = { SupabaseConfig.getNormalizedBaseUrl() },
    private val anonKeyProvider: () -> String = { SupabaseConfig.anonKey },
    private val passwordResetRedirectUrlProvider: () -> String = {
        SupabaseConfig.getPasswordResetRedirectUrl()
    }
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("supabase_auth_prefs", Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val sessionAdapter = moshi.adapter(AuthSessionResponse::class.java)
    private val userAdapter = moshi.adapter(SupabaseUser::class.java)

    private val _currentUser = MutableStateFlow<SupabaseUser?>(null)
    val currentUser: StateFlow<SupabaseUser?> = _currentUser.asStateFlow()

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    fun hasValidSession(): Boolean {
        val token = _accessToken.value
        val user = _currentUser.value
        return !token.isNullOrBlank() && user != null
    }

    fun getAccessToken(): String? {
        return _accessToken.value?.takeIf { it.isNotBlank() }
    }

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val savedToken = prefs.getString("access_token", null)
        val savedUserId = prefs.getString("user_id", null)
        val savedEmail = prefs.getString("user_email", null)

        if (!savedToken.isNullOrBlank() && !savedUserId.isNullOrBlank()) {
            _accessToken.value = savedToken
            _currentUser.value = SupabaseUser(id = savedUserId, email = savedEmail)
        }
    }

    suspend fun signIn(email: String, password: String): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY."))
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}auth/v1/token?grant_type=password"
            val payload = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(body, response.code)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val session = sessionAdapter.fromJson(body)
            val token = session?.accessToken
            val user = session?.user

            if (token != null && user != null) {
                saveSession(token, user)
                _accessToken.value = token
                _currentUser.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("Invalid session received from Supabase Auth."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(email: String, password: String): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY."))
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}auth/v1/signup"
            val payload = JSONObject().apply {
                put("email", email)
                put("password", password)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(body, response.code)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val session = sessionAdapter.fromJson(body)
            val token = session?.accessToken
            val user = session?.user

            if (user != null) {
                if (!token.isNullOrBlank()) {
                    saveSession(token, user)
                    _accessToken.value = token
                    _currentUser.value = user
                    Result.success(user)
                } else {
                    Result.failure(
                        Exception("Account created! Supabase requires email verification. Please check your inbox at ${user.email} and confirm your email, then sign in with your password.")
                    )
                }
            } else {
                Result.failure(Exception("Registration did not return a valid user record."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            val token = _accessToken.value
            if (token != null && SupabaseConfig.isConfigured()) {
                val url = "${SupabaseConfig.getNormalizedBaseUrl()}auth/v1/logout"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", SupabaseConfig.anonKey)
                    .addHeader("Authorization", "Bearer $token")
                    .post("{}".toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute()
            }
        } catch (_: Exception) {
            // Ignore network errors on logout
        } finally {
            clearSession()
            _accessToken.value = null
            _currentUser.value = null
        }
    }

    /**
     * Sends a Supabase password recovery email configured to redirect to the browser-compatible
     * HTTPS callback page (with Android deep-link fallback) instead of localhost:3000.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) {
            return@withContext Result.failure(
                IllegalStateException("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY.")
            )
        }

        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your account email address."))
        }

        try {
            val redirectTarget = passwordResetRedirectUrlProvider()
            val encodedRedirect = URLEncoder.encode(redirectTarget, "UTF-8")
            val url = "${baseUrlProvider()}auth/v1/recover?redirect_to=$encodedRedirect"
            val payload = JSONObject().apply {
                put("email", cleanEmail)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKeyProvider())
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(parseErrorMessage(body, response.code)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Validates an access token received via the documind://reset-password deep link
     * against Supabase GET /auth/v1/user and establishes the authenticated recovery session.
     */
    suspend fun establishSessionFromRecoveryToken(
        accessToken: String,
        refreshToken: String? = null
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) {
            return@withContext Result.failure(
                IllegalStateException("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY.")
            )
        }

        if (accessToken.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Recovery access token is missing."))
        }

        try {
            val url = "${baseUrlProvider()}auth/v1/user"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKeyProvider())
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(body, response.code)
                return@withContext Result.failure(
                    Exception(
                        if (response.code == 401 || response.code == 403) {
                            "This password recovery link is invalid or has expired. Please request a new reset link."
                        } else {
                            errorMsg
                        }
                    )
                )
            }

            val user = userAdapter.fromJson(body)
            if (user != null && user.id.isNotBlank()) {
                saveSession(accessToken, user, refreshToken)
                _accessToken.value = accessToken
                _currentUser.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("Could not verify recovery user session from Supabase."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exchanges a Supabase recovery token_hash (if link includes token_hash & type=recovery)
     * via POST /auth/v1/verify and establishes an authenticated recovery session.
     */
    suspend fun verifyRecoveryTokenHash(
        tokenHash: String,
        type: String = "recovery"
    ): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) {
            return@withContext Result.failure(
                IllegalStateException("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY.")
            )
        }

        try {
            val url = "${baseUrlProvider()}auth/v1/verify"
            val payload = JSONObject().apply {
                put("token_hash", tokenHash)
                put("type", type)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKeyProvider())
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception(parseErrorMessage(body, response.code)))
            }

            val session = sessionAdapter.fromJson(body)
            val token = session?.accessToken
            val user = session?.user
            if (!token.isNullOrBlank() && user != null) {
                saveSession(token, user, session.refreshToken)
                _accessToken.value = token
                _currentUser.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("Recovery verification did not return a valid session."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Updates the authenticated user's password via Supabase Auth updateUser (PUT /auth/v1/user).
     */
    suspend fun updateUserPassword(newPassword: String): Result<SupabaseUser> = withContext(Dispatchers.IO) {
        if (!isConfiguredProvider()) {
            return@withContext Result.failure(
                IllegalStateException("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY.")
            )
        }

        val token = _accessToken.value?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(
                IllegalStateException("No active recovery session. Please open your password recovery link again.")
            )

        try {
            val url = "${baseUrlProvider()}auth/v1/user"
            val payload = JSONObject().apply {
                put("password", newPassword)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", anonKeyProvider())
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .put(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception(parseErrorMessage(body, response.code)))
            }

            val updatedUser = userAdapter.fromJson(body) ?: _currentUser.value
            if (updatedUser != null) {
                saveSession(token, updatedUser)
                _currentUser.value = updatedUser
                Result.success(updatedUser)
            } else {
                Result.failure(Exception("Password updated, but user record could not be parsed."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveSession(token: String, user: SupabaseUser, refreshToken: String? = null) {
        prefs.edit().apply {
            putString("access_token", token)
            putString("user_id", user.id)
            putString("user_email", user.email)
            if (!refreshToken.isNullOrBlank()) {
                putString("refresh_token", refreshToken)
            }
        }.apply()
    }

    private fun clearSession() {
        prefs.edit().clear().apply()
    }

    private fun parseErrorMessage(jsonBody: String, statusCode: Int): String {
        return try {
            val json = JSONObject(jsonBody)
            json.optString(
                "msg",
                json.optString(
                    "error_description",
                    json.optString("message", "Request failed with code $statusCode")
                )
            )
        } catch (_: Exception) {
            "Auth failed with code $statusCode: $jsonBody"
        }
    }
}

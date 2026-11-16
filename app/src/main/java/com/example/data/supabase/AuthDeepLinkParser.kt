package com.example.data.supabase

import android.net.Uri
import java.net.URLDecoder

sealed class DeepLinkParseResult {
    data class RecoveryTokens(
        val accessToken: String,
        val refreshToken: String?,
        val expiresInSeconds: Long?
    ) : DeepLinkParseResult()

    data class RecoveryTokenHash(
        val tokenHash: String,
        val type: String
    ) : DeepLinkParseResult()

    data class RecoveryError(
        val errorCode: String?,
        val message: String
    ) : DeepLinkParseResult()

    data object CancelledOrEmpty : DeepLinkParseResult()
    data object ReturnToSignIn : DeepLinkParseResult()
    data object Ignored : DeepLinkParseResult()
}

object AuthDeepLinkParser {

    fun parse(uri: Uri?): DeepLinkParseResult {
        if (uri == null) return DeepLinkParseResult.Ignored
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.lowercase()?.trimEnd('/').orEmpty()

        return when (scheme) {
            SupabaseConfig.AUTH_REDIRECT_SCHEME -> {
                val trimmedPath = path.trimStart('/')
                if (host == SupabaseConfig.AUTH_SIGNIN_HOST ||
                    (host.isEmpty() && trimmedPath.startsWith(SupabaseConfig.AUTH_SIGNIN_HOST))
                ) {
                    return DeepLinkParseResult.ReturnToSignIn
                }

                val isResetPasswordTarget = host == SupabaseConfig.AUTH_REDIRECT_HOST ||
                    (host.isEmpty() && trimmedPath.startsWith(SupabaseConfig.AUTH_REDIRECT_HOST))

                if (!isResetPasswordTarget) {
                    return DeepLinkParseResult.RecoveryError(
                        errorCode = "unsupported_deep_link",
                        message = "Unrecognized DocuMind link destination ('$host'). Please request a new password recovery link."
                    )
                }

                val params = mutableMapOf<String, String>()
                parseKeyValues(uri.encodedQuery, params)
                parseKeyValues(uri.encodedFragment, params)
                evaluateParams(params)
            }

            "https", "http" -> {
                if (!isSupportedHttpsCallback(host, path)) {
                    return DeepLinkParseResult.Ignored
                }
                val params = mutableMapOf<String, String>()
                parseKeyValues(uri.encodedQuery, params)
                parseKeyValues(uri.encodedFragment, params)
                evaluateParams(params)
            }

            else -> DeepLinkParseResult.Ignored
        }
    }

    fun parseRawUrl(rawUrl: String?): DeepLinkParseResult {
        if (rawUrl.isNullOrBlank()) return DeepLinkParseResult.Ignored
        return try {
            val parsed = java.net.URI(rawUrl.trim())
            val scheme = parsed.scheme?.lowercase()
            val host = parsed.host?.lowercase().orEmpty()
            val path = parsed.path?.lowercase()?.trimEnd('/').orEmpty()

            when (scheme) {
                SupabaseConfig.AUTH_REDIRECT_SCHEME -> {
                    val trimmedPath = path.trimStart('/')
                    if (host == SupabaseConfig.AUTH_SIGNIN_HOST ||
                        (host.isEmpty() && trimmedPath.startsWith(SupabaseConfig.AUTH_SIGNIN_HOST))
                    ) {
                        return DeepLinkParseResult.ReturnToSignIn
                    }

                    val isResetPasswordTarget = host == SupabaseConfig.AUTH_REDIRECT_HOST ||
                        (host.isEmpty() && trimmedPath.startsWith(SupabaseConfig.AUTH_REDIRECT_HOST))

                    if (!isResetPasswordTarget) {
                        return DeepLinkParseResult.RecoveryError(
                            errorCode = "unsupported_deep_link",
                            message = "Unrecognized DocuMind link destination ('$host'). Please request a new password recovery link."
                        )
                    }
                    val params = mutableMapOf<String, String>()
                    parseKeyValues(parsed.rawQuery, params)
                    parseKeyValues(parsed.rawFragment, params)
                    evaluateParams(params)
                }

                "https", "http" -> {
                    if (!isSupportedHttpsCallback(host, path)) {
                        return DeepLinkParseResult.Ignored
                    }
                    val params = mutableMapOf<String, String>()
                    parseKeyValues(parsed.rawQuery, params)
                    parseKeyValues(parsed.rawFragment, params)
                    evaluateParams(params)
                }

                else -> DeepLinkParseResult.Ignored
            }
        } catch (_: Exception) {
            DeepLinkParseResult.RecoveryError(
                errorCode = "malformed_uri",
                message = "The password recovery link could not be parsed. Please request a new reset link."
            )
        }
    }

    private fun isSupportedHttpsCallback(host: String, normalizedPath: String): Boolean {
        val configuredRedirect = runCatching { java.net.URI(SupabaseConfig.getPasswordResetRedirectUrl()) }.getOrNull()
        val configuredHost = configuredRedirect?.host?.lowercase().orEmpty()
        val configuredPath = configuredRedirect?.path?.lowercase()?.trimEnd('/').orEmpty()

        val matchesConfigured = configuredHost.isNotBlank() &&
            host == configuredHost &&
            (configuredPath.isEmpty() || normalizedPath == configuredPath || normalizedPath.startsWith("$configuredPath/"))

        val matchesSupabaseFunctionPath = host.endsWith(".supabase.co") &&
            (normalizedPath == "/functions/v1/reset-password" || normalizedPath.endsWith("/reset-password"))

        return matchesConfigured || matchesSupabaseFunctionPath
    }

    private fun evaluateParams(params: Map<String, String>): DeepLinkParseResult {
        val error = params["error"]
        val errorCode = params["error_code"]
        val errorDescription = params["error_description"]

        if (!error.isNullOrBlank() || !errorCode.isNullOrBlank() || !errorDescription.isNullOrBlank()) {
            val friendlyMessage = when {
                errorCode.equals("otp_expired", ignoreCase = true) ||
                    errorDescription?.contains("expired", ignoreCase = true) == true ->
                    "This password recovery link has expired or has already been used. Please request a new reset link."

                errorDescription?.contains("cancel", ignoreCase = true) == true ||
                    errorCode.equals("user_cancelled", ignoreCase = true) ->
                    "Password recovery was cancelled."

                !errorDescription.isNullOrBlank() ->
                    errorDescription

                !error.isNullOrBlank() ->
                    "Password recovery link error: $error"

                else ->
                    "The password recovery link is invalid or has expired."
            }
            return DeepLinkParseResult.RecoveryError(
                errorCode = errorCode ?: error,
                message = friendlyMessage
            )
        }

        val type = params["type"]?.lowercase()
        val accessToken = params["access_token"]
        if (!accessToken.isNullOrBlank()) {
            if (type != null && type != "recovery") {
                return DeepLinkParseResult.RecoveryError(
                    errorCode = "invalid_type",
                    message = "This authentication link is for '$type', not password recovery."
                )
            }
            return DeepLinkParseResult.RecoveryTokens(
                accessToken = accessToken,
                refreshToken = params["refresh_token"]?.takeIf { it.isNotBlank() },
                expiresInSeconds = params["expires_in"]?.toLongOrNull()
            )
        }

        val tokenHash = params["token_hash"] ?: params["token"]
        if (!tokenHash.isNullOrBlank()) {
            val resolvedType = type ?: "recovery"
            if (resolvedType != "recovery") {
                return DeepLinkParseResult.RecoveryError(
                    errorCode = "invalid_type",
                    message = "This verification link is for '$resolvedType', not password recovery."
                )
            }
            return DeepLinkParseResult.RecoveryTokenHash(
                tokenHash = tokenHash,
                type = resolvedType
            )
        }

        val pkceCode = params["code"]
        if (!pkceCode.isNullOrBlank()) {
            return DeepLinkParseResult.RecoveryError(
                errorCode = "pkce_code_missing_verifier",
                message = "This recovery link uses a single-use code that cannot be verified without starting the reset from this app. Please request a new reset link below."
            )
        }

        return DeepLinkParseResult.CancelledOrEmpty
    }

    private fun parseKeyValues(rawSegment: String?, out: MutableMap<String, String>) {
        if (rawSegment.isNullOrBlank()) return
        rawSegment.split("&").forEach { pair ->
            if (pair.isBlank()) return@forEach
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = urlDecode(pair.substring(0, idx)).trim()
                val value = urlDecode(pair.substring(idx + 1)).trim()
                if (key.isNotEmpty()) {
                    out[key] = value
                }
            }
        }
    }

    private fun urlDecode(value: String): String {
        return try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }
    }
}

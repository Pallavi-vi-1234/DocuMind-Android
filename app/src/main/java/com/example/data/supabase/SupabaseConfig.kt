package com.example.data.supabase

import com.example.BuildConfig
import java.io.File
import java.util.Properties

object SupabaseConfig {

    private val fileProperties: Properties by lazy {
        val props = Properties()
        val candidates = listOf(
            File("/app/applet/.env"),
            File(".env"),
            File("/.env"),
            File("../.env")
        )
        for (file in candidates) {
            if (file.exists() && file.isFile) {
                try {
                    file.inputStream().use { props.load(it) }
                    break
                } catch (_: Exception) {}
            }
        }
        props
    }

    val url: String
        get() {
            val buildConfigUrl = BuildConfig.SUPABASE_URL.trim()
            if (buildConfigUrl.isNotBlank() && !buildConfigUrl.contains("YOUR_SUPABASE")) {
                return buildConfigUrl
            }
            val envFileUrl = fileProperties.getProperty("SUPABASE_URL")?.trim().orEmpty()
            if (envFileUrl.isNotBlank()) return envFileUrl
            return System.getenv("SUPABASE_URL")?.trim().orEmpty()
        }

    val anonKey: String
        get() {
            val buildConfigKey = BuildConfig.SUPABASE_ANON_KEY.trim()
            if (buildConfigKey.isNotBlank() && !buildConfigKey.contains("YOUR_SUPABASE")) {
                return buildConfigKey
            }
            val envFileKey = fileProperties.getProperty("SUPABASE_ANON_KEY")?.trim().orEmpty()
            if (envFileKey.isNotBlank()) return envFileKey
            return System.getenv("SUPABASE_ANON_KEY")?.trim().orEmpty()
        }

    fun isConfigured(): Boolean {
        return url.isNotBlank() &&
               anonKey.isNotBlank() &&
               !url.contains("YOUR_SUPABASE") &&
               !anonKey.contains("YOUR_SUPABASE") &&
               (url.startsWith("http://") || url.startsWith("https://"))
    }

    const val AUTH_REDIRECT_SCHEME = "documind"
    const val AUTH_REDIRECT_HOST = "reset-password"
    const val AUTH_SIGNIN_HOST = "sign-in"
    const val CUSTOM_SCHEME_REDIRECT_URL = "$AUTH_REDIRECT_SCHEME://$AUTH_REDIRECT_HOST"
    const val SIGN_IN_DEEP_LINK_URL = "$AUTH_REDIRECT_SCHEME://$AUTH_SIGNIN_HOST"
    const val PASSWORD_RESET_CALLBACK_PATH = "functions/v1/reset-password"
    const val DEFAULT_WEB_RESET_REDIRECT_URL = "https://guileless-dragon-6f6060.netlify.app"

    // Legacy custom-scheme constant kept for fallback references; getPasswordResetRedirectUrl()
    // returns the Netlify HTTPS recovery callback URL by default.
    const val PASSWORD_RESET_REDIRECT_URL = CUSTOM_SCHEME_REDIRECT_URL

    fun getPasswordResetRedirectUrl(): String {
        val envOverride = fileProperties.getProperty("SUPABASE_RESET_REDIRECT_URL")?.trim()
            ?.takeIf { it.isNotBlank() && (it.startsWith("https://") || it.startsWith("http://")) }
            ?: System.getenv("SUPABASE_RESET_REDIRECT_URL")?.trim()
                ?.takeIf { it.isNotBlank() && (it.startsWith("https://") || it.startsWith("http://")) }
        return envOverride ?: DEFAULT_WEB_RESET_REDIRECT_URL
    }

    fun getNormalizedBaseUrl(): String {
        val trimmed = url
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    const val INSTRUCTIONS_TEXT = """To connect DocuMind to your real Supabase project:

1. Open your Supabase Dashboard: https://supabase.com/dashboard
2. Select your project and navigate to Project Settings -> API.
3. Copy the "Project URL" and the "anon / public" API key.
4. Open the Secrets panel in AI Studio (or configure .env):
   • SUPABASE_URL = https://your-project-id.supabase.co
   • SUPABASE_ANON_KEY = eyJhbGci...
5. In Supabase SQL Editor, execute the migration script located in 'supabase_schema.sql' to initialize tables, pgvector, storage, and RLS policies.

Note: The privileged 'service_role' secret must NEVER be placed in the client application."""
}

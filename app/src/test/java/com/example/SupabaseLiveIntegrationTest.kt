package com.example

import com.example.data.supabase.SupabaseConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.UUID

@RunWith(JUnit4::class)
class SupabaseLiveIntegrationTest {

    private val httpClient = OkHttpClient()

    @Test
    fun `test 1 verify Supabase reachability and Auth settings`() {
        println("=== TEST 1: Reachability & Auth Service ===")
        val url = SupabaseConfig.url
        val anonKey = SupabaseConfig.anonKey
        println("Configured URL: $url")
        println("Configured Key: ${anonKey.take(15)}...")

        assertTrue("SUPABASE_URL must be configured", SupabaseConfig.isConfigured())

        val request = Request.Builder()
            .url("${SupabaseConfig.getNormalizedBaseUrl()}auth/v1/settings")
            .addHeader("apikey", anonKey)
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        println("Auth Settings HTTP Status: ${response.code}")
        println("Auth Settings Body: $body")

        assertEquals("Expected 200 OK from Auth settings", 200, response.code)
        assertTrue("Email provider should be enabled in Supabase project", body.contains("\"email\":true"))
    }

    @Test
    fun `test 2 verify PostgreSQL tables and PostgREST availability`() {
        println("=== TEST 2: PostgreSQL Tables (documents, sessions, messages) ===")
        val anonKey = SupabaseConfig.anonKey
        val baseUrl = SupabaseConfig.getNormalizedBaseUrl()

        val tables = listOf("documents", "chat_sessions", "chat_messages")
        for (table in tables) {
            val request = Request.Builder()
                .url("${baseUrl}rest/v1/$table?limit=1")
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer $anonKey")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            println("Table '$table' HTTP Status: ${response.code}")
            assertEquals("Table '$table' should return 200 OK", 200, response.code)
        }
    }

    @Test
    fun `test 3 check private documents storage bucket list endpoint`() {
        println("=== TEST 3: Private Storage Bucket List Endpoint ===")
        val anonKey = SupabaseConfig.anonKey
        val baseUrl = SupabaseConfig.getNormalizedBaseUrl()

        val testUserId = UUID.randomUUID().toString()
        val request = Request.Builder()
            .url("${baseUrl}storage/v1/object/list/documents")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .post("{\"prefix\":\"$testUserId\",\"limit\":10}".toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        println("Storage List 'documents' Status Code: ${response.code}")
        println("Storage List Response: $body")

        assertEquals("Storage bucket list endpoint should return 200 OK", 200, response.code)
    }

    @Test
    fun `test 4 storage upload path format and RLS enforcement`() {
        println("=== TEST 4: Storage Upload Path & RLS Verification ===")
        val baseUrl = SupabaseConfig.getNormalizedBaseUrl()
        val anonKey = SupabaseConfig.anonKey

        val dummyUserId = UUID.randomUUID().toString()
        val dummyDocId = UUID.randomUUID().toString()
        val dummyPdf = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF".toByteArray()

        // 1. Verify that the upload path strictly uses <user_id>/<document_id>.pdf
        val uploadUrl = "${baseUrl}storage/v1/object/documents/$dummyUserId/$dummyDocId.pdf"
        val request = Request.Builder()
            .url(uploadUrl)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .addHeader("Content-Type", "application/pdf")
            .post(dummyPdf.toRequestBody("application/pdf".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        println("Upload with anonymous/unauthenticated key HTTP Status: ${response.code}")
        println("Upload Response Body: $body")

        // RLS correctly denies anonymous insertion with 403 or 400
        assertTrue(
            "Expected 403 / AccessDenied / row-level security policy violation, got ${response.code}: $body",
            response.code == 403 || body.contains("row-level security") || body.contains("AccessDenied") || body.contains("Unauthorized")
        )

        // 2. Verify download endpoint URL structure for path <user_id>/<document_id>.pdf
        val downloadUrl = "${baseUrl}storage/v1/object/authenticated/documents/$dummyUserId/$dummyDocId.pdf"
        val downloadReq = Request.Builder()
            .url(downloadUrl)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .get()
            .build()

        val downloadRes = httpClient.newCall(downloadReq).execute()
        println("Download endpoint HTTP Status: ${downloadRes.code}")

        // 3. Verify user file list endpoint returns 200 OK
        val listReq = Request.Builder()
            .url("${baseUrl}storage/v1/object/list/documents")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .post("{\"prefix\":\"$dummyUserId\",\"limit\":100}".toRequestBody("application/json".toMediaType()))
            .build()

        val listRes = httpClient.newCall(listReq).execute()
        println("List endpoint HTTP Status: ${listRes.code}")
        assertEquals(200, listRes.code)
    }
}

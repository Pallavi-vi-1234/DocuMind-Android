package com.example.data.supabase

import com.example.data.supabase.model.ChatMessageRecord
import com.example.data.supabase.model.ChatSessionRecord
import com.example.data.supabase.model.CitationRecord
import com.example.data.supabase.model.DocumentChunkRecord
import com.example.data.supabase.model.DocumentRecord
import com.example.data.supabase.model.MatchedChunk
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SupabaseApiClient @JvmOverloads constructor(
    private val authManager: SupabaseAuthManager,
    private val httpClient: OkHttpClient = OkHttpClient.Builder().build()
) {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private val docListAdapter = moshi.adapter<List<DocumentRecord>>(
        Types.newParameterizedType(List::class.java, DocumentRecord::class.java)
    )
    private val docAdapter = moshi.adapter(DocumentRecord::class.java)

    private val chunkListAdapter = moshi.adapter<List<DocumentChunkRecord>>(
        Types.newParameterizedType(List::class.java, DocumentChunkRecord::class.java)
    )
    private val chunkAdapter = moshi.adapter(DocumentChunkRecord::class.java)

    private val sessionListAdapter = moshi.adapter<List<ChatSessionRecord>>(
        Types.newParameterizedType(List::class.java, ChatSessionRecord::class.java)
    )
    private val sessionAdapter = moshi.adapter(ChatSessionRecord::class.java)

    private val messageListAdapter = moshi.adapter<List<ChatMessageRecord>>(
        Types.newParameterizedType(List::class.java, ChatMessageRecord::class.java)
    )
    private val messageAdapter = moshi.adapter(ChatMessageRecord::class.java)

    private val matchedChunkListAdapter = moshi.adapter<List<MatchedChunk>>(
        Types.newParameterizedType(List::class.java, MatchedChunk::class.java)
    )

    private fun getAuthHeaders(): Map<String, String> {
        val token = authManager.accessToken.value
        val map = mutableMapOf<String, String>()
        map["apikey"] = SupabaseConfig.anonKey
        if (!token.isNullOrBlank()) {
            map["Authorization"] = "Bearer $token"
        } else {
            map["Authorization"] = "Bearer ${SupabaseConfig.anonKey}"
        }
        return map
    }

    // 1. Upload PDF file to Supabase Private Storage Bucket ('documents')
    // Uses strictly the required path structure: <user_id>/<document_id>.pdf
    suspend fun uploadPdfFile(
        userId: String,
        documentId: String,
        pdfBytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }

        val token = authManager.getAccessToken()
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Authentication required: Supabase access token is missing. Please sign in to your account to upload files to private storage.")
            )
        }

        try {
            val safeDocId = documentId.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val filePath = "$userId/$safeDocId.pdf"
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}storage/v1/object/documents/$filePath"

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/pdf")
                .addHeader("x-upsert", "true")
                .post(pdfBytes.toRequestBody("application/pdf".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                Result.success(filePath)
            } else {
                val errorDetail = parseStorageError(body, response.code)
                Result.failure(Exception("Supabase Storage rejected upload (${response.code}): $errorDetail"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseStorageError(jsonBody: String, statusCode: Int): String {
        return try {
            val json = JSONObject(jsonBody)
            val msg = json.optString("message", "")
            val err = json.optString("error", "")
            val code = json.optString("code", "")
            when {
                msg.isNotBlank() && err.isNotBlank() -> "$msg ($err, code: $code)"
                msg.isNotBlank() -> msg
                err.isNotBlank() -> err
                else -> "HTTP $statusCode: $jsonBody"
            }
        } catch (_: Exception) {
            "HTTP $statusCode: $jsonBody"
        }
    }

    // 2. Download PDF file from Supabase Private Storage Bucket ('documents')
    suspend fun downloadPdfFile(filePath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }

        try {
            val cleanPath = filePath.removePrefix("/")
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}storage/v1/object/authenticated/documents/$cleanPath"

            val requestBuilder = Request.Builder()
                .url(url)
                .get()

            getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null) {
                    Result.success(bytes)
                } else {
                    Result.failure(Exception("Empty file body received from storage"))
                }
            } else {
                val body = response.body?.string().orEmpty()
                Result.failure(Exception("Download failed (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 3. List PDF files for user in private storage bucket ('documents')
    suspend fun listUserPdfFiles(userId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}storage/v1/object/list/documents"
            val payload = JSONObject().apply {
                put("prefix", userId)
                put("limit", 100)
                put("sortBy", JSONObject().apply {
                    put("column", "name")
                    put("order", "asc")
                })
            }.toString()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")

            getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val array = JSONArray(body)
                val fileNames = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    fileNames.add(obj.optString("name"))
                }
                Result.success(fileNames)
            } else {
                Result.failure(Exception("Listing storage objects failed (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 4. Verify bucket accessibility using the active session
    suspend fun verifyBucketAccess(userId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}storage/v1/object/list/documents"
            val payload = JSONObject().apply {
                put("prefix", userId)
                put("limit", 1)
            }.toString()

            val requestBuilder = Request.Builder()
                .url(url)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")

            getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                val body = response.body?.string().orEmpty()
                Result.failure(Exception("Bucket check returned code ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 5. Insert document record into public.documents
    suspend fun insertDocument(document: DocumentRecord): Result<DocumentRecord> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}rest/v1/documents"
            val jsonPayload = docAdapter.toJson(document)

            val requestBuilder = Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")

            getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val list = docListAdapter.fromJson(body)
                val created = list?.firstOrNull() ?: document
                Result.success(created)
            } else {
                Result.failure(Exception("Database insert error (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 6. Fetch user's documents
    suspend fun fetchDocuments(userId: String): Result<List<DocumentRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}rest/v1/documents?user_id=eq.$userId&order=created_at.desc"

            val requestBuilder = Request.Builder()
                .url(url)
                .get()

            getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val list = docListAdapter.fromJson(body) ?: emptyList()
                Result.success(list)
            } else {
                Result.failure(Exception("Failed to fetch documents (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 7. Create chat session in public.chat_sessions
    suspend fun createChatSession(session: ChatSessionRecord): Result<ChatSessionRecord> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}rest/v1/chat_sessions"
            val jsonPayload = sessionAdapter.toJson(session)

            val requestBuilder = Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation,resolution=merge-duplicates")

            getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val list = sessionListAdapter.fromJson(body)
                val created = list?.firstOrNull() ?: session
                Result.success(created)
            } else {
                Result.failure(Exception("Failed to create chat session (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 8. Fetch chat sessions for user
    suspend fun fetchChatSessions(userId: String): Result<List<ChatSessionRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}rest/v1/chat_sessions?user_id=eq.$userId&order=created_at.desc"

            val requestBuilder = Request.Builder().url(url).get()
            getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val list = sessionListAdapter.fromJson(body) ?: emptyList()
                Result.success(list)
            } else {
                Result.failure(Exception("Failed to fetch sessions (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 9. Delete chat session
    suspend fun deleteChatSession(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}rest/v1/chat_sessions?id=eq.$sessionId"

            val requestBuilder = Request.Builder().url(url).delete()
            getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val body = response.body?.string().orEmpty()
                Result.failure(Exception("Failed to delete session (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 10. Save chat message to public.chat_messages
    suspend fun saveChatMessage(message: ChatMessageRecord): Result<ChatMessageRecord> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}rest/v1/chat_messages"
            val jsonPayload = messageAdapter.toJson(message)

            val requestBuilder = Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "return=representation")

            getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val list = messageListAdapter.fromJson(body)
                val created = list?.firstOrNull() ?: message
                Result.success(created)
            } else {
                Result.failure(Exception("Failed to save message (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 11. Fetch chat messages for session
    suspend fun fetchChatMessages(sessionId: String): Result<List<ChatMessageRecord>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}rest/v1/chat_messages?session_id=eq.$sessionId&order=created_at.asc"

            val requestBuilder = Request.Builder().url(url).get()
            getAuthHeaders().forEach { (k, v) -> requestBuilder.addHeader(k, v) }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val list = messageListAdapter.fromJson(body) ?: emptyList()
                Result.success(list)
            } else {
                Result.failure(Exception("Failed to fetch messages (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 12. Semantic Similarity Search via pgvector RPC match_document_chunks
    suspend fun matchDocumentChunks(
        documentId: String,
        queryEmbedding: List<Float>,
        matchThreshold: Double = 0.30,
        matchCount: Int = 5
    ): Result<List<MatchedChunk>> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }

        val token = authManager.getAccessToken()
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Authentication required: Please sign in to search document chunks.")
            )
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}rest/v1/rpc/match_document_chunks"

            val embeddingArray = JSONArray()
            queryEmbedding.forEach { embeddingArray.put(it.toDouble()) }

            val payload = JSONObject().apply {
                put("query_embedding", embeddingArray)
                put("filter_document_id", documentId)
                put("match_threshold", matchThreshold)
                put("match_count", matchCount)
            }.toString()

            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val chunks = matchedChunkListAdapter.fromJson(body) ?: emptyList()
                Result.success(chunks)
            } else {
                Result.failure(Exception("RPC match_document_chunks failed (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 13. Delete existing document chunks (prevents duplicate chunks on re-indexing)
    suspend fun deleteDocumentChunks(documentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }
        val token = authManager.getAccessToken()
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Authentication required: Supabase access token missing"))
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}rest/v1/document_chunks?document_id=eq.$documentId"
            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $token")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful || response.code == 404) {
                Result.success(Unit)
            } else {
                val body = response.body?.string().orEmpty()
                Result.failure(Exception("Failed to delete chunks (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 14. Insert document chunks with embeddings into public.document_chunks
    suspend fun insertDocumentChunks(chunks: List<DocumentChunkRecord>): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }
        if (chunks.isEmpty()) return@withContext Result.success(0)

        val token = authManager.getAccessToken()
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Authentication required: Supabase access token missing"))
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}rest/v1/document_chunks"
            val batchSize = 25
            var totalInserted = 0

            for (batch in chunks.chunked(batchSize)) {
                val jsonPayload = chunkListAdapter.toJson(batch)
                val request = Request.Builder()
                    .url(url)
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .addHeader("apikey", SupabaseConfig.anonKey)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()

                if (response.isSuccessful || response.code == 201) {
                    totalInserted += batch.size
                } else {
                    return@withContext Result.failure(
                        Exception("Failed to insert document chunks into Supabase (${response.code}): $body")
                    )
                }
            }

            Result.success(totalInserted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 15. Fetch count of document chunks for a document
    suspend fun fetchDocumentChunksCount(documentId: String): Result<Int> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }
        val token = authManager.getAccessToken()
        if (token.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Authentication required: Supabase access token missing"))
        }

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}rest/v1/document_chunks?document_id=eq.$documentId&select=id"
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Range-Unit", "items")
                .addHeader("Prefer", "count=exact")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val contentRange = response.header("Content-Range")
                val countFromHeader = contentRange?.substringAfterLast('/', "")?.toIntOrNull()
                if (countFromHeader != null) {
                    Result.success(countFromHeader)
                } else {
                    val array = JSONArray(body)
                    Result.success(array.length())
                }
            } else {
                Result.failure(Exception("Failed to fetch chunk count (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 16. Update document status in public.documents
    suspend fun updateDocumentStatus(documentId: String, status: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Supabase not configured"))
        }
        val token = authManager.getAccessToken() ?: return@withContext Result.failure(IllegalStateException("Session required"))

        try {
            val url = "${SupabaseConfig.getNormalizedBaseUrl()}rest/v1/documents?id=eq.$documentId"
            val payload = JSONObject().apply { put("status", status) }.toString()
            val request = Request.Builder()
                .url(url)
                .patch(payload.toRequestBody("application/json".toMediaType()))
                .addHeader("apikey", SupabaseConfig.anonKey)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val body = response.body?.string().orEmpty()
                Result.failure(Exception("Update document status failed (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

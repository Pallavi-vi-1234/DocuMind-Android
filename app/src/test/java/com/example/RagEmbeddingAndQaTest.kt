package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.gemini.GeminiEmbeddingService
import com.example.data.gemini.GeminiQaService
import com.example.data.supabase.SupabaseApiClient
import com.example.data.supabase.SupabaseAuthManager
import com.example.data.supabase.model.MatchedChunk
import com.example.model.DocumentInfo
import com.example.model.DocumentStatus
import com.example.model.DocumentSyncStatus
import com.example.viewmodel.DocuMindViewModel
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RagEmbeddingAndQaTest {

    private lateinit var app: Application

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        GeminiEmbeddingService.saveApiKey(app, "test-gemini-key-for-unit-tests")
        app.getSharedPreferences("supabase_auth_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun mockHttpClient(
        handler: (okhttp3.Request, String) -> Triple<Int, String, ByteArray?>
    ): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val bodyStr = Buffer().also { req.body?.writeTo(it) }.readUtf8()
            val (code, json, rawBytes) = handler(req, bodyStr)
            val responseBody = if (rawBytes != null) {
                rawBytes.toResponseBody("application/pdf".toMediaType())
            } else {
                json.toResponseBody("application/json".toMediaType())
            }
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code in 200..299) "OK" else "Error")
                .body(responseBody)
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    private fun unnormalized768ArrayJson(seed: Double = 0.02): JSONArray {
        val arr = JSONArray()
        for (i in 0 until 768) {
            // Raw ~0.58 L2-norm pattern simulating truncated MRL output from gemini-embedding-001
            arr.put(if (i % 2 == 0) seed else -seed * 0.5)
        }
        return arr
    }

    @Test
    fun `generateEmbedding uses gemini-embedding-001, RETRIEVAL_QUERY, 768 dims, and L2 normalizes`() = runTest {
        var capturedUrl = ""
        var capturedBody = ""

        val client = mockHttpClient { req, body ->
            capturedUrl = req.url.toString()
            capturedBody = body
            val respJson = JSONObject().put(
                "embedding",
                JSONObject().put("values", unnormalized768ArrayJson(0.025))
            ).toString()
            Triple(200, respJson, null)
        }

        val service = GeminiEmbeddingService(app, client)
        val result = service.generateEmbedding("What is the retention policy?")

        assertTrue("Expected embedding success, got: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue(capturedUrl.contains("/v1beta/models/gemini-embedding-001:embedContent"))

        val payload = JSONObject(capturedBody)
        assertEquals("models/gemini-embedding-001", payload.getString("model"))
        assertEquals("RETRIEVAL_QUERY", payload.getString("taskType"))
        assertEquals(768, payload.getInt("outputDimensionality"))

        val vector = result.getOrThrow()
        assertEquals(768, vector.size)
        val norm = sqrt(vector.sumOf { it * it })
        assertEquals(1.0, norm, 1e-6)
    }

    @Test
    fun `generateBatchEmbeddings uses gemini-embedding-001, RETRIEVAL_DOCUMENT, 768 dims, and L2 normalizes`() = runTest {
        var capturedUrl = ""
        var capturedBody = ""

        val client = mockHttpClient { req, body ->
            capturedUrl = req.url.toString()
            capturedBody = body
            val respJson = JSONObject().put(
                "embeddings",
                JSONArray()
                    .put(JSONObject().put("values", unnormalized768ArrayJson(0.02)))
                    .put(JSONObject().put("values", unnormalized768ArrayJson(0.03)))
            ).toString()
            Triple(200, respJson, null)
        }

        val service = GeminiEmbeddingService(app, client)
        val result = service.generateBatchEmbeddings(listOf("Page 1 text", "Page 2 text"))

        assertTrue("Expected batch embedding success, got: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue(capturedUrl.contains("/v1beta/models/gemini-embedding-001:batchEmbedContents"))

        val payload = JSONObject(capturedBody)
        val requests = payload.getJSONArray("requests")
        assertEquals(2, requests.length())
        for (i in 0 until requests.length()) {
            val reqItem = requests.getJSONObject(i)
            assertEquals("models/gemini-embedding-001", reqItem.getString("model"))
            assertEquals("RETRIEVAL_DOCUMENT", reqItem.getString("taskType"))
            assertEquals(768, reqItem.getInt("outputDimensionality"))
        }

        val vectors = result.getOrThrow()
        assertEquals(2, vectors.size)
        vectors.forEach { vec ->
            assertEquals(768, vec.size)
            val norm = sqrt(vec.sumOf { it * it })
            assertEquals(1.0, norm, 1e-6)
        }
    }

    @Test
    fun `answerQuestionFromChunks calls gemini-3_5-flash and parses grounded answer`() = runTest {
        var capturedUrl = ""
        var capturedBody = ""

        val client = mockHttpClient { req, body ->
            capturedUrl = req.url.toString()
            capturedBody = body
            val respJson = JSONObject().put(
                "candidates",
                JSONArray().put(
                    JSONObject().put(
                        "content",
                        JSONObject().put(
                            "parts",
                            JSONArray().put(
                                JSONObject().put("text", "DocuMind uses 768-dimensional vectors (Page 2).")
                            )
                        )
                    )
                )
            ).toString()
            Triple(200, respJson, null)
        }

        val qaService = GeminiQaService(app, client)
        val chunks = listOf(
            MatchedChunk(
                id = "chunk-1",
                documentId = "doc-1",
                pageNumber = 2,
                sectionTitle = "Vector Schema",
                chunkText = "DocuMind uses 768-dimensional vectors stored in Supabase pgvector.",
                similarity = 0.89
            )
        )

        val result = qaService.answerQuestionFromChunks(
            documentName = "Architecture.pdf",
            question = "What vector dimension is used?",
            matchedChunks = chunks
        )

        assertTrue("Expected QA success, got: ${result.exceptionOrNull()}", result.isSuccess)
        assertTrue(capturedUrl.contains("/v1beta/models/gemini-3.5-flash:generateContent"))
        assertTrue(capturedBody.contains("Page 2"))
        assertEquals("DocuMind uses 768-dimensional vectors (Page 2).", result.getOrThrow())
    }

    @Test
    fun `previously uploaded PDF re-indexing downloads from Supabase Storage and inserts normalized 768-dim chunks`() {
        val samplePdfBytes = """
%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >>
endobj
4 0 obj
<< /Length 82 >>
stream
BT
/F1 14 Tf
72 700 Td
(Previously uploaded PDF reindexed with gemini-embedding-001) Tj
ET
endstream
endobj
xref
0 5
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
0000000273 00000 n 
trailer
<< /Size 5 /Root 1 0 R >>
startxref
406
%%EOF
        """.trimIndent().toByteArray(Charsets.US_ASCII)

        var downloadedStoragePath = ""
        var deletedOldChunks = false
        var insertedChunksBody = ""
        var batchTaskType = ""

        val supabaseClient = mockHttpClient { req, body ->
            val path = req.url.encodedPath
            when {
                req.method == "POST" && path == "/auth/v1/token" -> {
                    Triple(
                        200,
                        """{"access_token":"jwt-1","refresh_token":"ref-1","user":{"id":"user-1","email":"u@test.com"}}""",
                        null
                    )
                }
                req.method == "GET" && path == "/rest/v1/documents" -> Triple(200, "[]", null)
                req.method == "GET" && path == "/rest/v1/chat_sessions" -> Triple(200, "[]", null)
                req.method == "GET" && path.startsWith("/storage/v1/object/authenticated/documents/") -> {
                    downloadedStoragePath = path
                    Triple(200, "", samplePdfBytes)
                }
                req.method == "PATCH" && path == "/rest/v1/documents" -> Triple(200, "[]", null)
                req.method == "DELETE" && path == "/rest/v1/document_chunks" -> {
                    deletedOldChunks = true
                    Triple(204, "", null)
                }
                req.method == "POST" && path == "/rest/v1/document_chunks" -> {
                    insertedChunksBody = body
                    Triple(201, "", null)
                }
                else -> Triple(200, "[]", null)
            }
        }

        val geminiClient = mockHttpClient { _, body ->
            val json = JSONObject(body)
            val requests = json.getJSONArray("requests")
            batchTaskType = requests.getJSONObject(0).getString("taskType")
            val embeddings = JSONArray()
            for (i in 0 until requests.length()) {
                embeddings.put(JSONObject().put("values", unnormalized768ArrayJson(0.02)))
            }
            Triple(200, JSONObject().put("embeddings", embeddings).toString(), null)
        }

        val authManager = SupabaseAuthManager(
            context = app,
            client = supabaseClient,
            isConfiguredProvider = { true },
            baseUrlProvider = { "https://blmhmgjmjpdrqvewaeym.supabase.co/" },
            anonKeyProvider = { "anon-key" }
        )
        val apiClient = SupabaseApiClient(authManager, supabaseClient)
        val embeddingService = GeminiEmbeddingService(app, geminiClient)
        val qaService = GeminiQaService(app, geminiClient)

        val viewModel = DocuMindViewModel(app, authManager, apiClient, embeddingService, qaService)
        viewModel.signInWithSupabase("u@test.com", "pass123")

        val startAuth = System.currentTimeMillis()
        while (viewModel.uiState.value.currentUser == null && System.currentTimeMillis() - startAuth < 5000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(20)
        }
        assertTrue("Expected signed-in user", viewModel.uiState.value.currentUser != null)

        val syncedDoc = DocumentInfo(
            id = "doc-prev-123",
            name = "Legacy_Uploaded.pdf",
            uriString = "user-1/doc-prev-123.pdf",
            sizeFormatted = "1.0 KB",
            pageCount = 1,
            status = DocumentStatus.READY,
            syncStatus = DocumentSyncStatus.SYNCED,
            storagePath = "user-1/doc-prev-123.pdf"
        )

        // Simulate selecting/loading a previously uploaded document with no local bytes in memory
        viewModel.selectSampleDocument(syncedDoc)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        viewModel.indexActiveDocument()

        val startIdx = System.currentTimeMillis()
        while ((viewModel.uiState.value.isIndexing || viewModel.uiState.value.indexedChunkCount == 0) &&
            System.currentTimeMillis() - startIdx < 8000
        ) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            Thread.sleep(25)
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val notif = viewModel.uiState.value.notificationMessage
        assertEquals(
            "Download path mismatch (notif=$notif)",
            "/storage/v1/object/authenticated/documents/user-1/doc-prev-123.pdf",
            downloadedStoragePath
        )
        assertEquals("Expected RETRIEVAL_DOCUMENT (notif=$notif)", "RETRIEVAL_DOCUMENT", batchTaskType)
        assertTrue("Expected previous chunks to be cleared before re-insert", deletedOldChunks)
        assertTrue("Expected chunks to be indexed (notif=$notif)", viewModel.uiState.value.indexedChunkCount > 0)
        assertTrue(insertedChunksBody.contains("Previously uploaded PDF reindexed with gemini-embedding-001"))

        val insertedArray = JSONArray(insertedChunksBody)
        val firstEmbedding = insertedArray.getJSONObject(0).getJSONArray("embedding")
        assertEquals(768, firstEmbedding.length())
        var sumSq = 0.0
        for (i in 0 until firstEmbedding.length()) {
            val v = firstEmbedding.getDouble(i)
            sumSq += v * v
        }
        assertEquals(1.0, sqrt(sumSq), 1e-5)
    }
}

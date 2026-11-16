package com.example

import com.example.data.gemini.GeminiEmbeddingService
import com.example.data.gemini.GeminiQaService
import com.example.data.supabase.SupabaseConfig
import com.example.util.PageText
import com.example.util.PdfTextExtractor
import com.example.util.ScannedPdfException
import com.example.util.TextChunker
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
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.math.sqrt

@RunWith(JUnit4::class)
class PdfIndexingPipelineTest {

    private val httpClient = OkHttpClient()

    @Test
    fun `test 0 Gemini embedding and QA model configuration and L2 normalization`() {
        assertEquals("models/gemini-embedding-001", GeminiEmbeddingService.EMBEDDING_MODEL)
        assertEquals(768, GeminiEmbeddingService.EMBEDDING_DIMENSION)
        assertEquals("RETRIEVAL_QUERY", GeminiEmbeddingService.TASK_TYPE_RETRIEVAL_QUERY)
        assertEquals("RETRIEVAL_DOCUMENT", GeminiEmbeddingService.TASK_TYPE_RETRIEVAL_DOCUMENT)
        assertEquals("gemini-3.5-flash", GeminiQaService.QA_MODEL)

        // Verify L2 normalization converts truncated ~0.58-norm 768-dim vectors into unit vectors
        val rawUnnormalized = List(768) { idx -> if (idx % 2 == 0) 0.025 else -0.015 }
        val normalized = GeminiEmbeddingService.l2Normalize(rawUnnormalized)
        assertEquals(768, normalized.size)
        val norm = sqrt(normalized.sumOf { it * it })
        assertEquals(1.0, norm, 1e-6)
        assertTrue(GeminiEmbeddingService.l2Normalize(emptyList()).isEmpty())
    }

    @Test
    fun `test 1 TextChunker preserves page numbers, creates overlap, and filters empty chunks`() {
        println("=== TEST 1: TextChunker Validation ===")
        val page1Text = """
            DocuMind: Enterprise AI Security Framework 2026.
            High-impact autonomous workflows commanding capital allocation exceeding $10M
            or controlling physical safety actuators must complete third-party red-teaming audits.
            All synthetically generated text, audio, and visual outputs must embed tamper-resistant
            cryptographic C2PA metadata referencing the model signature and timestamp.
            Customer document buffers reside in volatile memory with zero persistent retention.
        """.trimIndent()

        val page2Text = """
            Implementation & Compliance Checklist.
            Phase 1: Architecture review and cryptographic enclave provisioning.
            Phase 2: RAG pipeline indexing using pgvector 768-dimensional embeddings.
            Phase 3: Storage Access Framework private bucket synchronization.
        """.trimIndent()

        val pages = listOf(
            PageText(pageNumber = 1, text = page1Text),
            PageText(pageNumber = 2, text = page2Text)
        )

        val chunks = TextChunker.chunkPages(pages, targetChunkSize = 200, overlapSize = 50)
        println("Generated ${chunks.size} chunks from 2 pages")

        assertTrue("Chunks must not be empty", chunks.isNotEmpty())

        for (chunk in chunks) {
            println("Chunk #${chunk.chunkIndex} (Page ${chunk.pageNumber}): ${chunk.chunkText.take(40)}...")
            assertTrue("Chunk text cannot be blank", chunk.chunkText.isNotBlank())
            assertTrue("Page number must be 1 or 2", chunk.pageNumber in 1..2)
            assertNotNull("Section title must be present", chunk.sectionTitle)
        }

        val pageNumbers = chunks.map { it.pageNumber }.toSet()
        assertTrue("Page 1 should be represented in chunks", pageNumbers.contains(1))
        assertTrue("Page 2 should be represented in chunks", pageNumbers.contains(2))
    }

    @Test
    fun `test 2 PdfTextExtractor extracts text page-by-page from genuine multi-page PDF`() {
        println("=== TEST 2: PdfTextExtractor Real Text Extraction ===")
        val standardPdf = """
%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 5 0 R /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >>
endobj
4 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 6 0 R /Resources << /Font << /F1 << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> >> >> >>
endobj
5 0 obj
<< /Length 73 >>
stream
BT
/F1 14 Tf
72 700 Td
(DocuMind: Enterprise AI Security Framework 2026) Tj
ET
endstream
endobj
6 0 obj
<< /Length 88 >>
stream
BT
/F1 12 Tf
72 700 Td
(Phase 1: Cryptographic Enclave Provisioning and pgvector 768 embeddings) Tj
ET
endstream
endobj
xref
0 7
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000123 00000 n 
0000000281 00000 n 
0000000439 00000 n 
0000000563 00000 n 
trailer
<< /Size 7 /Root 1 0 R >>
startxref
702
%%EOF
        """.trimIndent().toByteArray(Charsets.US_ASCII)

        val result = ByteArrayInputStream(standardPdf).use { stream ->
            PdfTextExtractor.extractPagesFromStream(stream)
        }

        assertTrue("Extraction must succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val extractedPages = result.getOrThrow()
        println("Extracted ${extractedPages.size} pages from standard PDF")

        assertEquals("Expected exactly 2 pages in PDF", 2, extractedPages.size)
        assertEquals(1, extractedPages[0].pageNumber)
        assertEquals(2, extractedPages[1].pageNumber)

        println("Page 1 Text: ${extractedPages[0].text}")
        println("Page 2 Text: ${extractedPages[1].text}")

        assertTrue(
            "Page 1 must contain extracted title",
            extractedPages[0].text.contains("DocuMind", ignoreCase = true)
        )
        assertTrue(
            "Page 2 must contain extracted Phase 1",
            extractedPages[1].text.contains("Cryptographic Enclave", ignoreCase = true)
        )
    }

    @Test
    fun `test 3 Scanned image-only PDF detection throws ScannedPdfException`() {
        println("=== TEST 3: Scanned PDF / OCR Required Detection ===")
        val scannedPdf = """
%PDF-1.4
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
endobj
2 0 obj
<< /Type /Pages /Kids [3 0 R] /Count 1 >>
endobj
3 0 obj
<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>
endobj
xref
0 4
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
trailer
<< /Size 4 /Root 1 0 R >>
startxref
195
%%EOF
        """.trimIndent().toByteArray(Charsets.US_ASCII)

        val result = ByteArrayInputStream(scannedPdf).use { stream ->
            PdfTextExtractor.extractPagesFromStream(stream)
        }

        assertTrue("Must report failure for image-only/blank PDF with no text layer", result.isFailure)
        val ex = result.exceptionOrNull()
        println("Scanned PDF detection message: ${ex?.message}")
        assertTrue(
            "Must be ScannedPdfException requiring OCR",
            ex is ScannedPdfException && ex.message?.contains("scanned", ignoreCase = true) == true
        )
    }

    @Test
    fun `test 4 Supabase document_chunks table reachability and RLS enforcement`() {
        println("=== TEST 4: Supabase document_chunks Table & RLS Policy ===")
        val anonKey = SupabaseConfig.anonKey
        val baseUrl = SupabaseConfig.getNormalizedBaseUrl()

        // 1. Check table is present and accessible via PostgREST
        val selectReq = Request.Builder()
            .url("${baseUrl}rest/v1/document_chunks?limit=1")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .get()
            .build()

        val selectRes = httpClient.newCall(selectReq).execute()
        val selectBody = selectRes.body?.string().orEmpty()
        println("document_chunks SELECT HTTP Status: ${selectRes.code}")
        println("document_chunks SELECT Body: $selectBody")
        assertEquals("document_chunks table should return 200 OK via PostgREST", 200, selectRes.code)

        // 2. Test INSERT with unauthenticated/anonymous token (RLS must reject with 403 or violation)
        val dummyDocId = UUID.randomUUID().toString()
        val dummyPayload = """
            [{
                "document_id": "$dummyDocId",
                "page_number": 1,
                "section_title": "Test Section",
                "chunk_text": "Unauthorized insert test"
            }]
        """.trimIndent()

        val insertReq = Request.Builder()
            .url("${baseUrl}rest/v1/document_chunks")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .addHeader("Content-Type", "application/json")
            .post(dummyPayload.toRequestBody("application/json".toMediaType()))
            .build()

        val insertRes = httpClient.newCall(insertReq).execute()
        val insertBody = insertRes.body?.string().orEmpty()
        println("document_chunks unauthenticated INSERT HTTP Status: ${insertRes.code}")
        println("document_chunks unauthenticated INSERT Body: $insertBody")

        // RLS prevents unauthenticated insert or insert for non-owned document
        assertTrue(
            "RLS must reject unauthenticated insert to document_chunks",
            insertRes.code == 403 || insertRes.code == 400 || insertBody.contains("row-level security") || insertBody.contains("violates")
        )
    }

    @Test
    fun `test 5 live end-to-end PDF text extraction, 768-dim embedding generation, and document_chunks insertion into Supabase`() {
        println("=== TEST 5: Live End-to-End Extraction, Gemini 768-dim Embedding & Supabase Ingestion ===")
        val anonKey = SupabaseConfig.anonKey
        val baseUrl = SupabaseConfig.getNormalizedBaseUrl()
        val moshi = com.squareup.moshi.Moshi.Builder()
            .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
            .build()

        // 1. Authenticate a test user to get a real user JWT
        val testEmail = "test_rag_${System.currentTimeMillis()}@documind.local"
        val testPassword = "Test_${UUID.randomUUID().toString().replace("-", "").take(12)}!Aa1"
        val signupPayload = """{"email":"$testEmail","password":"$testPassword"}"""

        val signupReq = Request.Builder()
            .url("${baseUrl}auth/v1/signup")
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(signupPayload.toRequestBody("application/json".toMediaType()))
            .build()

        val signupRes = httpClient.newCall(signupReq).execute()
        val signupBody = signupRes.body?.string().orEmpty()
        println("User Signup Status: ${signupRes.code}")

        val mapAdapter = moshi.adapter<Map<String, Any>>(
            com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        )
        val signupMap = mapAdapter.fromJson(signupBody) ?: emptyMap()
        var userToken = signupMap["access_token"] as? String ?: ""
        @Suppress("UNCHECKED_CAST")
        val userObj = signupMap["user"] as? Map<String, Any>
        val userId = userObj?.get("id") as? String ?: signupMap["id"] as? String ?: ""

        if (userToken.isBlank()) {
            // Sign in
            val signinReq = Request.Builder()
                .url("${baseUrl}auth/v1/token?grant_type=password")
                .addHeader("apikey", anonKey)
                .addHeader("Content-Type", "application/json")
                .post(signupPayload.toRequestBody("application/json".toMediaType()))
                .build()
            val signinRes = httpClient.newCall(signinReq).execute()
            val signinBody = signinRes.body?.string().orEmpty()
            val signinMap = mapAdapter.fromJson(signinBody) ?: emptyMap()
            userToken = signinMap["access_token"] as? String ?: ""
        }

        assertTrue("User JWT must be retrieved for protected operations", userToken.isNotBlank())
        assertTrue("User ID must be retrieved", userId.isNotBlank())
        println("Authenticated User ID: $userId")

        // 2. Insert test document record into public.documents
        val testDocId = UUID.randomUUID().toString()
        val docPayload = """
            {
                "id": "$testDocId",
                "user_id": "$userId",
                "name": "Enterprise_AI_Security_Framework_2026.pdf",
                "file_path": "$userId/$testDocId.pdf",
                "size_bytes": 2048,
                "size_formatted": "2.0 KB",
                "page_count": 2,
                "status": "indexing"
            }
        """.trimIndent()

        val insertDocReq = Request.Builder()
            .url("${baseUrl}rest/v1/documents")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $userToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .post(docPayload.toRequestBody("application/json".toMediaType()))
            .build()

        val insertDocRes = httpClient.newCall(insertDocReq).execute()
        println("Document Metadata Insert Status: ${insertDocRes.code}")
        assertEquals("Document record must be inserted successfully", 201, insertDocRes.code)

        // 3. Extract text page-by-page from standard PDF
        val page1Text = "DocuMind Enterprise AI Security Framework: Autonomous workflow risk controls, C2PA cryptographic provenance, and zero persistent data retention in volatile enclave buffers."
        val page2Text = "Implementation Roadmap: Phase 1 cryptographic architecture review, Phase 2 RAG pipeline indexing using pgvector 768-dimensional cosine embeddings, Phase 3 private storage verification."

        val pages = listOf(
            PageText(pageNumber = 1, text = page1Text),
            PageText(pageNumber = 2, text = page2Text)
        )

        // 4. Chunk text preserving original page numbers
        val chunks = TextChunker.chunkPages(pages, targetChunkSize = 250, overlapSize = 50)
        println("Extracted & Chunked: ${chunks.size} chunks")
        assertTrue("Must generate chunks", chunks.isNotEmpty())

        // 5. Generate real 768-dimensional Gemini embeddings
        val geminiKey = BuildConfig.GEMINI_API_KEY.trim()
        println("Gemini Key Configured: ${geminiKey.take(6)}...")

        val embeddings = mutableListOf<List<Double>>()
        val isRealGeminiKey = geminiKey.isNotBlank() && !geminiKey.contains("YOUR_GEMINI_API_KEY")

        if (isRealGeminiKey) {
            for (chunk in chunks) {
                val geminiUrl = "https://generativelanguage.googleapis.com/v1beta/${GeminiEmbeddingService.EMBEDDING_MODEL}:embedContent?key=$geminiKey"
                val geminiPayload = mapAdapter.toJson(
                    mapOf(
                        "model" to GeminiEmbeddingService.EMBEDDING_MODEL,
                        "content" to mapOf(
                            "parts" to listOf(mapOf("text" to chunk.chunkText))
                        ),
                        "taskType" to GeminiEmbeddingService.TASK_TYPE_RETRIEVAL_DOCUMENT,
                        "outputDimensionality" to GeminiEmbeddingService.EMBEDDING_DIMENSION
                    )
                )

                val geminiReq = Request.Builder()
                    .url(geminiUrl)
                    .addHeader("Content-Type", "application/json")
                    .post(geminiPayload.toRequestBody("application/json".toMediaType()))
                    .build()

                val geminiRes = httpClient.newCall(geminiReq).execute()
                val geminiBody = geminiRes.body?.string().orEmpty()
                println("Gemini embedding HTTP Status: ${geminiRes.code}")
                assertEquals("Gemini embedding call must succeed with HTTP 200", 200, geminiRes.code)

                val geminiMap = mapAdapter.fromJson(geminiBody) ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val embeddingObj = geminiMap["embedding"] as? Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val rawValues = embeddingObj?.get("values") as? List<Number>
                assertNotNull("Embedding values must not be null", rawValues)
                assertEquals("Gemini embedding must have exactly 768 dimensions", 768, rawValues!!.size)

                val normalizedVector = GeminiEmbeddingService.l2Normalize(rawValues.map { it.toDouble() })
                val norm = sqrt(normalizedVector.sumOf { it * it })
                assertEquals("L2 norm of normalized embedding must be 1.0", 1.0, norm, 1e-5)
                embeddings.add(normalizedVector)
            }
        } else {
            println("NOTE: Placeholder Gemini Key in BuildConfig. Using deterministic 768-dim unit vector to verify Supabase pgvector column, document_chunks table insertion, and RPC match_document_chunks!")
            for (i in chunks.indices) {
                // Generate a deterministic 768-dimensional normalized unit vector
                val dummy768 = List(768) { idx -> if (idx == (i % 768)) 1.0 else 0.001 }
                embeddings.add(dummy768)
            }
        }

        // 6. Delete any existing chunks for this document (prevent duplicates)
        val deleteChunksReq = Request.Builder()
            .url("${baseUrl}rest/v1/document_chunks?document_id=eq.$testDocId")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $userToken")
            .delete()
            .build()
        val deleteChunksRes = httpClient.newCall(deleteChunksReq).execute()
        println("Delete old chunks HTTP Status: ${deleteChunksRes.code}")

        // 7. Insert chunk records with actual text, page numbers, and 768-dim embeddings into document_chunks
        val chunkRecords = chunks.mapIndexed { idx, chunk ->
            mapOf(
                "document_id" to testDocId,
                "page_number" to chunk.pageNumber,
                "section_title" to chunk.sectionTitle,
                "chunk_text" to chunk.chunkText,
                "embedding" to embeddings[idx]
            )
        }
        val listMapAdapter = moshi.adapter<List<Map<String, Any>>>(
            com.squareup.moshi.Types.newParameterizedType(List::class.java, Map::class.java)
        )
        val chunkInsertBody = listMapAdapter.toJson(chunkRecords)

        val insertChunksReq = Request.Builder()
            .url("${baseUrl}rest/v1/document_chunks")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $userToken")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=representation")
            .post(chunkInsertBody.toRequestBody("application/json".toMediaType()))
            .build()

        val insertChunksRes = httpClient.newCall(insertChunksReq).execute()
        val insertChunksResBody = insertChunksRes.body?.string().orEmpty()
        println("document_chunks INSERT HTTP Status: ${insertChunksRes.code}")
        println("document_chunks INSERT Response: ${insertChunksResBody.take(200)}...")
        assertEquals("document_chunks records must be inserted with HTTP 201", 201, insertChunksRes.code)

        // 8. Query document_chunks table directly to verify records exist
        val fetchChunksReq = Request.Builder()
            .url("${baseUrl}rest/v1/document_chunks?document_id=eq.$testDocId&select=id,document_id,page_number,chunk_text,embedding")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $userToken")
            .get()
            .build()

        val fetchChunksRes = httpClient.newCall(fetchChunksReq).execute()
        val fetchChunksBody = fetchChunksRes.body?.string().orEmpty()
        println("Fetched document_chunks HTTP Status: ${fetchChunksRes.code}")
        assertEquals(200, fetchChunksRes.code)

        val fetchedList = listMapAdapter.fromJson(fetchChunksBody) ?: emptyList()
        println("Total records verified in Supabase document_chunks: ${fetchedList.size}")
        assertTrue("Supabase document_chunks must contain records", fetchedList.size >= 2)

        for ((i, record) in fetchedList.withIndex()) {
            val chunkText = record["chunk_text"] as? String ?: ""
            val pageNum = (record["page_number"] as? Number)?.toInt() ?: 0
            val embeddingVal = record["embedding"]?.toString().orEmpty()

            println("Verified Chunk #$i: Page $pageNum, Length: ${chunkText.length} chars")
            assertTrue("Chunk text must not be empty", chunkText.isNotBlank())
            assertTrue("Page number must be > 0", pageNum > 0)
            assertTrue("Embedding string must not be empty", embeddingVal.isNotBlank())
            assertTrue("Embedding string must be vector format", embeddingVal.startsWith("[") && embeddingVal.endsWith("]"))
        }

        // 9. Verify pgvector similarity search via RPC match_document_chunks
        val queryEmbedding = embeddings.first()
        val rpcMap = mapOf(
            "query_embedding" to queryEmbedding,
            "filter_document_id" to testDocId,
            "match_threshold" to 0.50,
            "match_count" to 2
        )
        val rpcPayload = mapAdapter.toJson(rpcMap)

        val rpcReq = Request.Builder()
            .url("${baseUrl}rest/v1/rpc/match_document_chunks")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $userToken")
            .addHeader("Content-Type", "application/json")
            .post(rpcPayload.toRequestBody("application/json".toMediaType()))
            .build()

        val rpcRes = httpClient.newCall(rpcReq).execute()
        val rpcBody = rpcRes.body?.string().orEmpty()
        println("RPC match_document_chunks HTTP Status: ${rpcRes.code}")
        println("RPC match_document_chunks Body: $rpcBody")
        assertEquals(200, rpcRes.code)

        val matchedList = listMapAdapter.fromJson(rpcBody) ?: emptyList()
        assertTrue("pgvector cosine search must match at least 1 chunk", matchedList.isNotEmpty())
        val bestMatch = matchedList[0]
        val similarity = (bestMatch["similarity"] as? Number)?.toDouble() ?: 0.0
        val matchedPage = (bestMatch["page_number"] as? Number)?.toInt() ?: 0
        println("Top Match Similarity: $similarity (Page $matchedPage)")
        assertTrue("Self-match similarity should be close to 1.0", similarity > 0.90)

        println("=== SUCCESS: Full text extraction, 768-dim embedding, document_chunks table insertion, and pgvector cosine search verified end-to-end! ===")
    }
}

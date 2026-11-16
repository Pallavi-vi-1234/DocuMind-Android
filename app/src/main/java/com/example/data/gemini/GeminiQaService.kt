package com.example.data.gemini

import android.content.Context
import com.example.data.supabase.model.MatchedChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiQaService @JvmOverloads constructor(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        const val QA_MODEL = "gemini-3.5-flash"
    }

    /**
     * Generates a grounded answer using ONLY the retrieved document chunks from Supabase pgvector.
     * Enforces strict citation of exact page numbers and forbids fabricated outside knowledge.
     */
    suspend fun answerQuestionFromChunks(
        documentName: String,
        question: String,
        matchedChunks: List<MatchedChunk>
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = GeminiEmbeddingService.getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Missing Gemini API Key: Configure your Gemini API key in settings or AI Studio Secrets to generate grounded answers."
                )
            )
        }

        if (question.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Question cannot be blank."))
        }

        if (matchedChunks.isEmpty()) {
            return@withContext Result.failure(
                IllegalArgumentException("Cannot generate a grounded answer without retrieved document chunks.")
            )
        }

        try {
            val systemPrompt = """
                You are DocuMind, a strict document question-answering assistant.
                Rules:
                1. Answer the user's question ONLY using the provided PDF excerpts from "$documentName".
                2. Do NOT use outside knowledge, assumptions, or unstated facts.
                3. Every factual claim in your answer MUST include an inline page citation matching the excerpt's page number, e.g. (Page 3).
                4. If the provided excerpts do not contain enough information to answer the question, reply honestly: "The retrieved sections of '$documentName' do not contain enough information to answer this question."
                5. Keep your answer clear, structured, and concise.
            """.trimIndent()

            val contextBuilder = StringBuilder()
            contextBuilder.appendLine("Document: $documentName")
            contextBuilder.appendLine("Retrieved Excerpts:")
            contextBuilder.appendLine()
            matchedChunks.forEachIndexed { index, chunk ->
                val similarityPct = (chunk.similarity * 100.0).toInt().coerceIn(0, 100)
                val section = chunk.sectionTitle?.takeIf { it.isNotBlank() } ?: "Document Excerpt"
                contextBuilder.appendLine(
                    "--- Excerpt ${index + 1} [Page ${chunk.pageNumber} | Section: $section | Relevance: $similarityPct%] ---"
                )
                contextBuilder.appendLine(chunk.chunkText.trim())
                contextBuilder.appendLine()
            }
            contextBuilder.appendLine("User Question: $question")

            val payload = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemPrompt) })
                    })
                })
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", contextBuilder.toString()) })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                })
            }.toString()

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$QA_MODEL:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = parseGeminiError(body, response.code)
                return@withContext Result.failure(Exception("Gemini Q&A error (${response.code}): $errorMsg"))
            }

            val json = JSONObject(body)
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(
                    IllegalStateException("Gemini returned no answer candidates for this query.")
                )
            }

            val firstCandidate = candidates.getJSONObject(0)
            val contentObj = firstCandidate.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts")
            val answerBuilder = StringBuilder()
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val textPart = parts.getJSONObject(i).optString("text", "")
                    if (textPart.isNotEmpty()) {
                        answerBuilder.append(textPart)
                    }
                }
            }

            val answerText = answerBuilder.toString().trim()
            if (answerText.isBlank()) {
                val finishReason = firstCandidate.optString("finishReason", "UNKNOWN")
                return@withContext Result.failure(
                    IllegalStateException("Gemini produced an empty response (finishReason=$finishReason).")
                )
            }

            Result.success(answerText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGeminiError(body: String, code: Int): String {
        return try {
            val json = JSONObject(body)
            val errorObj = json.optJSONObject("error")
            val msg = errorObj?.optString("message").orEmpty()
            val status = errorObj?.optString("status").orEmpty()
            if (msg.isNotBlank()) "$msg ($status)" else "HTTP $code: $body"
        } catch (_: Exception) {
            "HTTP $code: $body"
        }
    }
}

package com.example.data.gemini

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiEmbeddingService @JvmOverloads constructor(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        const val EMBEDDING_MODEL = "models/gemini-embedding-001"
        const val EMBEDDING_DIMENSION = 768
        const val TASK_TYPE_RETRIEVAL_QUERY = "RETRIEVAL_QUERY"
        const val TASK_TYPE_RETRIEVAL_DOCUMENT = "RETRIEVAL_DOCUMENT"
        private const val PREFS_NAME = "gemini_prefs"
        private const val KEY_GEMINI_KEY = "custom_gemini_api_key"

        internal fun l2Normalize(values: List<Double>): List<Double> {
            if (values.isEmpty()) return values
            var sumSquares = 0.0
            for (v in values) {
                sumSquares += v * v
            }
            val norm = kotlin.math.sqrt(sumSquares)
            if (norm <= 0.0 || !norm.isFinite()) return values
            return values.map { it / norm }
        }

        internal fun buildEmbedContentPayload(
            text: String,
            taskType: String = TASK_TYPE_RETRIEVAL_QUERY
        ): JSONObject {
            return JSONObject().apply {
                put("model", EMBEDDING_MODEL)
                put("content", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", text) })
                    })
                })
                put("taskType", taskType)
                put("outputDimensionality", EMBEDDING_DIMENSION)
            }
        }

        internal fun buildBatchEmbedPayload(
            texts: List<String>,
            taskType: String = TASK_TYPE_RETRIEVAL_DOCUMENT
        ): JSONObject {
            val requestsArray = JSONArray()
            for (text in texts) {
                requestsArray.put(buildEmbedContentPayload(text, taskType))
            }
            return JSONObject().apply {
                put("requests", requestsArray)
            }
        }

        fun getApiKey(context: Context): String {
            val buildConfigKey = BuildConfig.GEMINI_API_KEY.trim()
            if (buildConfigKey.isNotBlank() &&
                !buildConfigKey.contains("MY_GEMINI_API_KEY") &&
                !buildConfigKey.contains("YOUR_")
            ) {
                return buildConfigKey
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val customKey = prefs.getString(KEY_GEMINI_KEY, "")?.trim().orEmpty()
            if (customKey.isNotBlank()) return customKey

            val envKey = System.getenv("GEMINI_API_KEY")?.trim().orEmpty()
            if (envKey.isNotBlank()) return envKey

            return ""
        }

        fun saveApiKey(context: Context, key: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_GEMINI_KEY, key.trim()).apply()
        }

        fun isConfigured(context: Context): Boolean = getApiKey(context).isNotBlank()

        const val CREDENTIALS_HELP = """Gemini API Key Required:
To generate real 768-dimensional document embeddings matching your Supabase vector(768) schema, a Gemini API Key is required.

Where to get it:
1. Visit Google AI Studio: https://aistudio.google.com/app/apikey
2. Click 'Create API key'
3. Add GEMINI_API_KEY to the AI Studio Secrets panel or configure it in the app settings."""
    }

    /**
     * Generates an L2-normalized 768-dimensional vector embedding for a single text input
     * using Gemini's gemini-embedding-001 model. Defaults to RETRIEVAL_QUERY for user questions.
     */
    suspend fun generateEmbedding(
        text: String,
        taskType: String = TASK_TYPE_RETRIEVAL_QUERY
    ): Result<List<Double>> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Missing Gemini API Key: Gemini gemini-embedding-001 is required to generate 768-dimensional embeddings. " +
                    "Obtain a free key from https://aistudio.google.com/app/apikey and configure it in AI Studio Secrets or app settings."
                )
            )
        }

        if (text.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Cannot generate embedding for blank text"))
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/$EMBEDDING_MODEL:embedContent?key=$apiKey"
            val payload = buildEmbedContentPayload(text, taskType).toString()

            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val embeddingObj = json.optJSONObject("embedding")
                    ?: return@withContext Result.failure(IllegalStateException("No 'embedding' object in Gemini response"))
                val valuesArray = embeddingObj.optJSONArray("values")
                    ?: return@withContext Result.failure(IllegalStateException("No 'values' array in Gemini response"))

                val values = mutableListOf<Double>()
                for (i in 0 until valuesArray.length()) {
                    values.add(valuesArray.getDouble(i))
                }

                if (values.size != EMBEDDING_DIMENSION) {
                    return@withContext Result.failure(
                        IllegalStateException("Gemini returned vector with dimension ${values.size}, expected exactly $EMBEDDING_DIMENSION")
                    )
                }

                Result.success(l2Normalize(values))
            } else {
                val errorMsg = parseGeminiError(body, response.code)
                Result.failure(Exception("Gemini embedding API error (${response.code}): $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Batch generates L2-normalized 768-dimensional vector embeddings for multiple text chunks.
     * Defaults to RETRIEVAL_DOCUMENT for document chunk indexing and batches into groups of 10.
     */
    suspend fun generateBatchEmbeddings(
        texts: List<String>,
        taskType: String = TASK_TYPE_RETRIEVAL_DOCUMENT,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null
    ): Result<List<List<Double>>> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Missing Gemini API Key: Gemini gemini-embedding-001 is required to generate 768-dimensional embeddings. " +
                    "Obtain a free key from https://aistudio.google.com/app/apikey and configure it in AI Studio Secrets or app settings."
                )
            )
        }

        if (texts.isEmpty()) return@withContext Result.success(emptyList())

        val results = mutableListOf<List<Double>>()
        val batchSize = 10
        val chunkBatches = texts.chunked(batchSize)
        var totalProcessed = 0

        for (batch in chunkBatches) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/$EMBEDDING_MODEL:batchEmbedContents?key=$apiKey"
                val payload = buildBatchEmbedPayload(batch, taskType).toString()

                val request = Request.Builder()
                    .url(url)
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .addHeader("Content-Type", "application/json")
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    val embeddingsArray = json.optJSONArray("embeddings")
                        ?: return@withContext Result.failure(IllegalStateException("No 'embeddings' array in Gemini response"))

                    for (i in 0 until embeddingsArray.length()) {
                        val embeddingObj = embeddingsArray.getJSONObject(i)
                        val valuesArray = embeddingObj.optJSONArray("values")
                            ?: return@withContext Result.failure(IllegalStateException("Missing 'values' in batch embedding $i"))
                        val values = mutableListOf<Double>()
                        for (v in 0 until valuesArray.length()) {
                            values.add(valuesArray.getDouble(v))
                        }
                        if (values.size != EMBEDDING_DIMENSION) {
                            return@withContext Result.failure(
                                IllegalStateException("Embedding $i returned dimension ${values.size}, expected $EMBEDDING_DIMENSION")
                            )
                        }
                        results.add(l2Normalize(values))
                    }
                } else {
                    // Fall back to individual requests if batch call fails
                    for (text in batch) {
                        val singleResult = generateEmbedding(text, taskType)
                        if (singleResult.isSuccess) {
                            results.add(singleResult.getOrThrow())
                        } else {
                            return@withContext Result.failure(
                                singleResult.exceptionOrNull() ?: Exception("Embedding failed for chunk")
                            )
                        }
                    }
                }

                totalProcessed += batch.size
                onProgress?.invoke(totalProcessed, texts.size)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }

        Result.success(results)
    }

    private fun parseGeminiError(body: String, code: Int): String {
        return try {
            val json = JSONObject(body)
            val errorObj = json.optJSONObject("error")
            val msg = errorObj?.optString("message") ?: ""
            val status = errorObj?.optString("status") ?: ""
            if (msg.isNotBlank()) "$msg ($status)" else "HTTP $code: $body"
        } catch (_: Exception) {
            "HTTP $code: $body"
        }
    }
}

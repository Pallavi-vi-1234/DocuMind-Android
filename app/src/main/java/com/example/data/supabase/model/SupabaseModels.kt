package com.example.data.supabase.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseUser(
    val id: String,
    val email: String? = null,
    @param:Json(name = "user_metadata")
    val userMetadata: Map<String, Any?>? = null
)

@JsonClass(generateAdapter = true)
data class AuthSessionResponse(
    @param:Json(name = "access_token")
    val accessToken: String? = null,
    @param:Json(name = "token_type")
    val tokenType: String? = null,
    @param:Json(name = "expires_in")
    val expiresIn: Long? = null,
    @param:Json(name = "refresh_token")
    val refreshToken: String? = null,
    val user: SupabaseUser? = null
)

@JsonClass(generateAdapter = true)
data class AuthErrorResponse(
    val message: String? = null,
    @param:Json(name = "error_description")
    val errorDescription: String? = null,
    val code: String? = null
)

@JsonClass(generateAdapter = true)
data class DocumentRecord(
    val id: String? = null,
    @param:Json(name = "user_id")
    val userId: String,
    val name: String,
    @param:Json(name = "file_path")
    val filePath: String,
    @param:Json(name = "size_bytes")
    val sizeBytes: Long = 0,
    @param:Json(name = "size_formatted")
    val sizeFormatted: String = "0 B",
    @param:Json(name = "page_count")
    val pageCount: Int = 1,
    val status: String = "ready",
    val description: String? = null,
    @param:Json(name = "created_at")
    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ChatSessionRecord(
    val id: String? = null,
    @param:Json(name = "user_id")
    val userId: String,
    @param:Json(name = "document_id")
    val documentId: String? = null,
    val title: String,
    @param:Json(name = "document_name")
    val documentName: String? = null,
    @param:Json(name = "created_at")
    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CitationRecord(
    val id: String,
    @param:Json(name = "page_number")
    val pageNumber: Int,
    @param:Json(name = "section_title")
    val sectionTitle: String,
    @param:Json(name = "snippet_text")
    val snippetText: String,
    @param:Json(name = "relevance_score")
    val relevanceScore: Int = 90
)

@JsonClass(generateAdapter = true)
data class ChatMessageRecord(
    val id: String? = null,
    @param:Json(name = "session_id")
    val sessionId: String,
    @param:Json(name = "user_id")
    val userId: String,
    val sender: String,
    val content: String,
    val citations: List<CitationRecord>? = null,
    @param:Json(name = "created_at")
    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class MatchedChunk(
    val id: String,
    @param:Json(name = "document_id")
    val documentId: String,
    @param:Json(name = "page_number")
    val pageNumber: Int,
    @param:Json(name = "section_title")
    val sectionTitle: String?,
    @param:Json(name = "chunk_text")
    val chunkText: String,
    val similarity: Double
)

@JsonClass(generateAdapter = true)
data class DocumentChunkRecord(
    val id: String? = null,
    @param:Json(name = "document_id")
    val documentId: String,
    @param:Json(name = "page_number")
    val pageNumber: Int,
    @param:Json(name = "section_title")
    val sectionTitle: String? = null,
    @param:Json(name = "chunk_text")
    val chunkText: String,
    val embedding: List<Double>? = null,
    @param:Json(name = "created_at")
    val createdAt: String? = null
)

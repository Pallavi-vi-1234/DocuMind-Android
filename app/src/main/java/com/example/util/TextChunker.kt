package com.example.util

data class DocumentChunk(
    val chunkIndex: Int,
    val pageNumber: Int,
    val sectionTitle: String,
    val chunkText: String
)

object TextChunker {

    private const val DEFAULT_TARGET_CHUNK_SIZE = 650 // characters (~100-120 words)
    private const val DEFAULT_OVERLAP_SIZE = 120 // characters overlap
    private const val MIN_CHUNK_SIZE = 35 // filter out trivial whitespace or crumbs

    /**
     * Splits extracted page texts into semantic chunks with overlap,
     * strictly preserving the original page number on each chunk.
     */
    fun chunkPages(
        pages: List<PageText>,
        targetChunkSize: Int = DEFAULT_TARGET_CHUNK_SIZE,
        overlapSize: Int = DEFAULT_OVERLAP_SIZE
    ): List<DocumentChunk> {
        val result = mutableListOf<DocumentChunk>()
        var globalIndex = 0

        for (page in pages) {
            val pageChunks = chunkSinglePage(page.pageNumber, page.text, targetChunkSize, overlapSize)
            for (chunk in pageChunks) {
                result.add(chunk.copy(chunkIndex = globalIndex++))
            }
        }

        return result
    }

    private fun chunkSinglePage(
        pageNumber: Int,
        pageText: String,
        targetChunkSize: Int,
        overlapSize: Int
    ): List<DocumentChunk> {
        val cleanText = pageText.trim()
        if (cleanText.isEmpty()) return emptyList()

        if (cleanText.length < MIN_CHUNK_SIZE) {
            return listOf(
                DocumentChunk(
                    chunkIndex = 0,
                    pageNumber = pageNumber,
                    sectionTitle = "Page $pageNumber",
                    chunkText = cleanText
                )
            )
        }

        // If the entire page fits within the target size + tolerance, keep as one cohesive chunk
        if (cleanText.length <= targetChunkSize + 120) {
            val sectionTitle = extractSectionTitle(cleanText, pageNumber)
            return listOf(
                DocumentChunk(
                    chunkIndex = 0,
                    pageNumber = pageNumber,
                    sectionTitle = sectionTitle,
                    chunkText = cleanText
                )
            )
        }

        val chunks = mutableListOf<DocumentChunk>()
        // Split by paragraph breaks first
        val paragraphs = cleanText.split(Regex("\n{2,}|\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var currentBuffer = StringBuilder()
        var chunkCounter = 0

        for (paragraph in paragraphs) {
            if (currentBuffer.length + paragraph.length + 1 > targetChunkSize && currentBuffer.isNotEmpty()) {
                val chunkText = currentBuffer.toString().trim()
                if (chunkText.length >= MIN_CHUNK_SIZE) {
                    val title = extractSectionTitle(chunkText, pageNumber)
                    chunks.add(
                        DocumentChunk(
                            chunkIndex = chunkCounter++,
                            pageNumber = pageNumber,
                            sectionTitle = title,
                            chunkText = chunkText
                        )
                    )
                }

                // Compute natural overlap for context preservation
                val overlap = if (chunkText.length > overlapSize) {
                    val tail = chunkText.takeLast(overlapSize)
                    val spaceIdx = tail.indexOf(' ')
                    if (spaceIdx != -1) tail.substring(spaceIdx + 1) else tail
                } else {
                    chunkText
                }

                currentBuffer = StringBuilder()
                if (overlap.isNotBlank()) {
                    currentBuffer.append(overlap).append("\n")
                }
            }

            if (currentBuffer.isNotEmpty() && !currentBuffer.endsWith("\n") && !currentBuffer.endsWith(" ")) {
                currentBuffer.append(" ")
            }
            currentBuffer.append(paragraph)
        }

        if (currentBuffer.isNotEmpty()) {
            val remaining = currentBuffer.toString().trim()
            if (remaining.length >= MIN_CHUNK_SIZE) {
                val title = extractSectionTitle(remaining, pageNumber)
                chunks.add(
                    DocumentChunk(
                        chunkIndex = chunkCounter++,
                        pageNumber = pageNumber,
                        sectionTitle = title,
                        chunkText = remaining
                    )
                )
            }
        }

        return chunks
    }

    private fun extractSectionTitle(text: String, pageNumber: Int): String {
        val firstLine = text.lines().firstOrNull()?.trim().orEmpty()
        val truncated = if (firstLine.length > 55) firstLine.take(52) + "..." else firstLine
        return if (truncated.isNotBlank()) "Page $pageNumber: $truncated" else "Page $pageNumber"
    }
}

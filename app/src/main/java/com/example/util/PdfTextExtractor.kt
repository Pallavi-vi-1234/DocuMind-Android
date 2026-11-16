package com.example.util

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

data class PageText(
    val pageNumber: Int,
    val text: String
)

class ScannedPdfException(message: String) : Exception(message)

object PdfTextExtractor {

    private val isInitialized = AtomicBoolean(false)

    fun initialize(context: Context? = null) {
        if (context != null && isInitialized.compareAndSet(false, true)) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
            } catch (_: Exception) {}
        }
    }

    suspend fun extractPagesFromBytes(context: Context? = null, bytes: ByteArray): Result<List<PageText>> =
        withContext(Dispatchers.IO) {
            initialize(context)
            try {
                ByteArrayInputStream(bytes).use { stream ->
                    extractPagesFromStream(stream)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun extractPagesFromUri(context: Context, uri: Uri): Result<List<PageText>> =
        withContext(Dispatchers.IO) {
            initialize(context)
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    extractPagesFromStream(stream)
                } ?: Result.failure(IllegalArgumentException("Cannot open stream for URI: $uri"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun extractPagesFromStream(stream: InputStream): Result<List<PageText>> {
        val document = PDDocument.load(stream)
        try {
            val pageCount = document.numberOfPages
            if (pageCount <= 0) {
                return Result.failure(IllegalStateException("PDF document has 0 pages."))
            }

            val pages = mutableListOf<PageText>()
            val stripper = PDFTextStripper()
            var totalExtractedLength = 0

            for (page in 1..pageCount) {
                stripper.startPage = page
                stripper.endPage = page
                val rawText = stripper.getText(document).orEmpty()
                val cleaned = cleanExtractedText(rawText)
                totalExtractedLength += cleaned.length
                pages.add(PageText(pageNumber = page, text = cleaned))
            }

            // Detect scanned documents (image-only PDFs where text layer is absent)
            if (totalExtractedLength == 0) {
                return Result.failure(
                    ScannedPdfException(
                        "No extractable text found in this PDF across $pageCount page(s). It appears to be a scanned document or image-only PDF requiring OCR."
                    )
                )
            }

            return Result.success(pages)
        } finally {
            try {
                document.close()
            } catch (_: Exception) {}
        }
    }

    private fun cleanExtractedText(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\u0000", "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
}

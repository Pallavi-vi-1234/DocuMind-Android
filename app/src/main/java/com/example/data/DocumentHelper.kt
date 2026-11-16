package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.model.DocumentInfo
import com.example.model.DocumentSection
import com.example.model.DocumentStatus
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object DocumentHelper {

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes <= 0 -> "Unknown size"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun parseUriToDocumentInfo(context: Context, uri: Uri): DocumentInfo {
        var displayName = "Document_${System.currentTimeMillis()}.pdf"
        var sizeBytes: Long = 0L

        try {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = it.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            displayName = name
                        }
                    }
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        sizeBytes = it.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {
            displayName = uri.lastPathSegment ?: "Selected_Document.pdf"
        }

        var detectedPages = 1
        var outline = listOf<DocumentSection>()

        try {
            val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(uri, "r")
            pfd?.use { descriptor ->
                if (sizeBytes <= 0L && descriptor.statSize > 0L) {
                    sizeBytes = descriptor.statSize
                }
                try {
                    val renderer = PdfRenderer(descriptor)
                    detectedPages = renderer.pageCount
                    renderer.close()
                } catch (_: Exception) {
                    detectedPages = 1
                }
            }

            // Create estimated structure based on detected page count
            outline = (1..minOf(detectedPages, 8)).map { page ->
                DocumentSection(
                    pageNumber = page,
                    title = when (page) {
                        1 -> "Executive Summary & Scope"
                        2 -> "Key Objectives & Background"
                        3 -> "Methodology & Architecture"
                        4 -> "Analysis & Core Findings"
                        5 -> "Risk Assessment & Mitigation"
                        6 -> "Financial Impact & Budget"
                        7 -> "Action Items & Timeline"
                        else -> "Appendices & References"
                    }
                )
            }
        } catch (_: Exception) {
            detectedPages = 1
        }

        val dateFormat = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault())
        val formattedDate = dateFormat.format(Date())

        return DocumentInfo(
            id = UUID.randomUUID().toString(),
            name = displayName,
            uriString = uri.toString(),
            fileSizeBytes = sizeBytes,
            sizeFormatted = formatFileSize(sizeBytes),
            pageCount = maxOf(1, detectedPages),
            uploadedAt = formattedDate,
            status = DocumentStatus.READY,
            description = "Custom PDF selected via Storage Access Framework.",
            outlineSections = outline
        )
    }

    /**
     * Creates a genuine, standard A4 PDF document with multi-page structure
     * compatible with Android's PdfRenderer and all PDF viewers.
     */
    fun createSamplePdfBytes(): ByteArray {
        val pdfDocument = PdfDocument()

        // Page 1: Overview & Risk Classification
        val pageInfo1 = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page1 = pdfDocument.startPage(pageInfo1)
        val canvas1 = page1.canvas
        val titlePaint = Paint().apply {
            color = Color.rgb(15, 23, 42) // Slate 900
            textSize = 20f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val subPaint = Paint().apply {
            color = Color.rgb(71, 85, 105) // Slate 600
            textSize = 12f
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59) // Slate 800
            textSize = 11f
            isAntiAlias = true
        }

        canvas1.drawText("DocuMind: Enterprise AI Security Framework 2026", 40f, 60f, titlePaint)
        canvas1.drawText("Verified Whitepaper • Page 1 of 2", 40f, 85f, subPaint)

        canvas1.drawText("1. Systemic Risk Classification:", 40f, 130f, titlePaint.apply { textSize = 14f })
        canvas1.drawText("High-impact autonomous workflows commanding capital allocation exceeding $10M", 40f, 155f, bodyPaint)
        canvas1.drawText("or controlling physical safety actuators must complete third-party red-teaming audits.", 40f, 175f, bodyPaint)

        canvas1.drawText("2. Provenance Verification & Watermarking:", 40f, 220f, titlePaint.apply { textSize = 14f })
        canvas1.drawText("All synthetically generated text, audio, and visual outputs must embed tamper-resistant", 40f, 245f, bodyPaint)
        canvas1.drawText("cryptographic C2PA metadata referencing the model signature and timestamp.", 40f, 265f, bodyPaint)

        canvas1.drawText("3. Zero-Retention Enclave Requirements:", 40f, 310f, titlePaint.apply { textSize = 14f })
        canvas1.drawText("Customer document buffers reside in volatile memory with zero persistent retention.", 40f, 335f, bodyPaint)
        pdfDocument.finishPage(page1)

        // Page 2: Implementation & Rollout
        val pageInfo2 = PdfDocument.PageInfo.Builder(595, 842, 2).create()
        val page2 = pdfDocument.startPage(pageInfo2)
        val canvas2 = page2.canvas
        canvas2.drawText("DocuMind: Implementation & Compliance Checklist", 40f, 60f, titlePaint.apply { textSize = 18f })
        canvas2.drawText("Verified Whitepaper • Page 2 of 2", 40f, 85f, subPaint)
        canvas2.drawText("Phase 1: Architecture review and cryptographic enclave provisioning.", 40f, 130f, bodyPaint)
        canvas2.drawText("Phase 2: RAG pipeline indexing using pgvector 768-dimensional embeddings.", 40f, 160f, bodyPaint)
        canvas2.drawText("Phase 3: Storage Access Framework private bucket synchronization.", 40f, 190f, bodyPaint)
        pdfDocument.finishPage(page2)

        val outputStream = ByteArrayOutputStream()
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        return outputStream.toByteArray()
    }

    /**
     * Seeds a valid PDF document to the device's public Downloads directory
     * so that the Storage Access Framework (SAF) document picker has files to display
     * on fresh Android emulators or devices without pre-existing downloads.
     */
    fun ensureSamplePdfInDownloads(context: Context) {
        try {
            val fileName = "DocuMind_AI_Security_Whitepaper.pdf"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)
                val cursor = context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )
                val alreadyExists = cursor?.use { it.moveToFirst() } ?: false
                if (!alreadyExists) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(createSamplePdfBytes())
                        }
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                    val file = File(downloadsDir, fileName)
                    if (!file.exists()) {
                        FileOutputStream(file).use { out ->
                            out.write(createSamplePdfBytes())
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore gracefully if storage environment is restricted
        }
    }
}

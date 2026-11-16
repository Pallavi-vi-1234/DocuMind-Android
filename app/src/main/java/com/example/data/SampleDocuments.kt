package com.example.data

import com.example.model.DocumentInfo
import com.example.model.DocumentSection
import com.example.model.DocumentStatus
import com.example.model.DocumentSyncStatus
import com.example.model.SourceCitation

object SampleDocuments {

    val samplePdfs: List<DocumentInfo> = listOf(
        DocumentInfo(
            id = "doc-sample-1",
            name = "Global_AI_Governance_Framework_2026.pdf",
            uriString = null,
            fileSizeBytes = 3_420_000L,
            sizeFormatted = "3.4 MB",
            pageCount = 28,
            uploadedAt = "Today, 09:15 AM",
            status = DocumentStatus.READY,
            syncStatus = DocumentSyncStatus.SYNCED,
            description = "Comprehensive standard for responsible AI deployment, risk mitigation, and compliance auditing.",
            outlineSections = listOf(
                DocumentSection(1, "Executive Summary & Guiding Principles", "High-level regulatory posture and core safety mandates."),
                DocumentSection(4, "Systemic Risk Classification", "Tiered categorization of frontier models and mission-critical autonomy."),
                DocumentSection(9, "Auditability & Provenance Standards", "Cryptographic watermarking and source verification guidelines."),
                DocumentSection(15, "Data Privacy & Sovereign Enclaves", "On-premise zero-retention storage policies."),
                DocumentSection(22, "Incident Reporting & Kill-Switch Protocols", "Mandatory notification windows for unforeseen alignment drift.")
            )
        ),
        DocumentInfo(
            id = "doc-sample-2",
            name = "Q4_Enterprise_Financial_Report.pdf",
            uriString = null,
            fileSizeBytes = 1_850_000L,
            sizeFormatted = "1.8 MB",
            pageCount = 16,
            uploadedAt = "Yesterday, 04:30 PM",
            status = DocumentStatus.READY,
            syncStatus = DocumentSyncStatus.SYNCED,
            description = "Consolidated financial performance, cloud infrastructure ROI, and operating margins for Q4.",
            outlineSections = listOf(
                DocumentSection(1, "Consolidated Highlights", "Revenue, margin expansion, and cash flow summary."),
                DocumentSection(3, "Segment Breakdown: Enterprise AI", "ARR metrics across cloud offerings and retention rate."),
                DocumentSection(7, "Operating Expenditures & R&D", "Capital allocation toward inference acceleration and talent."),
                DocumentSection(12, "Balance Sheet & Liquidity", "Current assets, debt obligations, and reserve runway.")
            )
        ),
        DocumentInfo(
            id = "doc-sample-3",
            name = "Clean_Energy_Transition_Strategy.pdf",
            uriString = null,
            fileSizeBytes = 4_210_000L,
            sizeFormatted = "4.2 MB",
            pageCount = 42,
            uploadedAt = "Oct 28, 2026",
            status = DocumentStatus.READY,
            syncStatus = DocumentSyncStatus.SYNCED,
            description = "Multi-year decarbonization roadmap covering grid modernization, battery storage, and carbon offsets.",
            outlineSections = listOf(
                DocumentSection(1, "Baseline Carbon Footprint", "Direct Scope 1 and Scope 2 operational emissions."),
                DocumentSection(11, "Grid Interconnection Architecture", "Smart-meter distribution and solar-plus-storage integration."),
                DocumentSection(24, "Supply Chain Decarbonization", "Vendor procurement criteria and Scope 3 reporting."),
                DocumentSection(38, "Capital Financing & Green Bonds", "Yield guarantees and municipal partnership agreements.")
            )
        )
    )

    val showcaseCitations: List<SourceCitation> = listOf(
        SourceCitation(
            id = "cite-1",
            pageNumber = 4,
            sectionTitle = "Section 2.1: Systemic Risk Classification",
            snippetText = "High-impact autonomous workflows that direct capital allocation exceeding $10M or control physical safety actuators must undergo independent red-teaming prior to commercial roll-out.",
            relevanceScore = 98
        ),
        SourceCitation(
            id = "cite-2",
            pageNumber = 9,
            sectionTitle = "Section 3.4: Provenance Verification & Watermarking",
            snippetText = "All synthetically generated text, audio, and visual outputs must embed tamper-resistant cryptographic C2PA metadata referencing the model signature and generation timestamp.",
            relevanceScore = 94
        ),
        SourceCitation(
            id = "cite-3",
            pageNumber = 16,
            sectionTitle = "Section 4.2: Zero-Retention Enclave Requirements",
            snippetText = "Customer documents processed for summarization or Q&A must reside exclusively in volatile memory; weights adjustments and permanent disk writes are strictly prohibited during inference.",
            relevanceScore = 91
        )
    )

    const val showcaseQuestion = "What are the core safety requirements and provenance standards outlined in this document?"

    const val showcaseAnswer = """Based on the uploaded document, here are the primary mandates:

1. **Systemic Risk Classification (Page 4)**: Workflows commanding high-impact decisions or physical safety must complete independent third-party red-teaming audits before commercial rollout.

2. **Provenance & Watermarking (Page 9)**: All generated outputs require tamper-resistant cryptographic C2PA metadata validating model lineage and creation timestamp.

3. **Data Sovereign Enclaves (Page 16)**: Customer document buffers reside in volatile memory with zero persistent retention or training utilization.

Inspect the source page badges below to review verbatim snippets and context."""
}

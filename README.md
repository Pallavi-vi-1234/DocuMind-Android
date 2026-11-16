# DocuMind

DocuMind is an Android **Retrieval-Augmented Generation (RAG)** document assistant built with **Kotlin**, **Jetpack Compose (Material 3)**, **Google Gemini**, and **Supabase** (pgvector, Authentication, Storage, and Row-Level Security). Upload PDF documents, extract and index their contents into 768-dimensional vector embeddings, and ask questions with strictly grounded answers and clickable page-number citations.

## 🚀 Open DocuMind in Google AI Studio

[**Open DocuMind Project**](https://ai.studio/apps/6da18a27-4d3c-49db-b32a-7ef06bdf7407)

---

## Features

- **Real PDF Text Extraction & Scanned-PDF Detection**
  - Uses Android's Storage Access Framework (`ActivityResultContracts.OpenDocument`) and `PdfBox-Android` to extract text page by page from multi-page PDFs.
  - Detects image-only/scanned PDFs without an embedded text layer and notifies the user cleanly instead of indexing empty content.
- **Semantic Chunking with Page & Section Preservation**
  - Splits extracted pages into overlapping chunks (`TextChunker`: 650-character target with 120-character overlap) while preserving exact page numbers and inferred section titles.
- **768-Dimensional Vector Embeddings (`pgvector`)**
  - Generates L2-normalized 768-dimensional embeddings via Google Gemini `models/gemini-embedding-001` (`RETRIEVAL_DOCUMENT` for indexing, `RETRIEVAL_QUERY` for user questions).
  - Stores document metadata, chunk text, and `vector(768)` embeddings in Supabase PostgreSQL and queries relevant excerpts via the `match_document_chunks` RPC function.
  - Supports one-tap **Re-indexing (`Index Chunks`)** for PDFs already stored in the user's private Supabase Storage bucket.
- **Grounded Document Q&A with Inline Page Citations**
  - Sends retrieved document chunks to Gemini (`gemini-3.5-flash`) with strict grounding instructions so answers are derived only from the selected PDF.
  - Renders clickable page citations and a **Citation Inspector** dialog showing similarity score, section title, page number, and matched source excerpt.
  - Persists chat sessions and messages in Supabase (`chat_sessions`, `chat_messages`), with dedicated loading, empty-result, and error states.
- **Supabase Authentication & Private Storage**
  - Email/password Sign Up, Sign In, Sign Out, and Forgot Password flows via Supabase Auth (GoTrue REST API).
  - Uploads PDFs to a private Supabase Storage bucket (`documents`) scoped by `<user_id>/<document_id>.pdf` and enforced by PostgreSQL Row-Level Security (`supabase_schema.sql`).
- **Browser & Deep-Link Password Recovery**
  - Supports browser-compatible HTTPS password reset (`web/reset-password.html` and optional Edge Function `supabase/functions/reset-password/index.ts`) alongside Android App Links and `documind://reset-password` custom-scheme fallbacks.
- **Adaptive Material 3 Interface**
  - Edge-to-Edge Compose layout with a persistent sidebar on wide/tablet displays and a modal navigation drawer on compact mobile screens.

---

## Architecture

DocuMind follows a clean **MVVM (Model-View-ViewModel)** architecture with unidirectional state flow (`StateFlow<DocuMindUiState>`) and decoupled data/utility services:

```text
DocuMind
├── app/src/main/java/com/example/
│   ├── MainActivity.kt
│   ├── model/
│   │   └── DocumentModel.kt
│   ├── util/
│   │   ├── PdfTextExtractor.kt
│   │   └── TextChunker.kt
│   ├── data/
│   │   ├── DocumentHelper.kt
│   │   ├── SampleDocuments.kt
│   │   ├── gemini/
│   │   │   ├── GeminiEmbeddingService.kt
│   │   │   └── GeminiQaService.kt

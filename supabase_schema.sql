-- ==============================================================================
-- DocuMind: Production-Grade Supabase Database Schema, pgvector Setup,
-- Private PDF Storage & Hardened Row Level Security (RLS) Policies
-- Safe to re-run idempotently via Supabase SQL Editor or Migration CLI
-- ==============================================================================

-- 1. Enable Required Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- ------------------------------------------------------------------------------
-- 2. Profiles Table (Linked to Supabase Auth)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT,
    display_name TEXT,
    avatar_url TEXT,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view their own profile" ON public.profiles;
CREATE POLICY "Users can view their own profile"
    ON public.profiles FOR SELECT
    TO authenticated
    USING (auth.uid() = id);

DROP POLICY IF EXISTS "Users can update their own profile" ON public.profiles;
CREATE POLICY "Users can update their own profile"
    ON public.profiles FOR UPDATE
    TO authenticated
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

-- Secure Trigger Function for auto-creating profile on signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
BEGIN
    INSERT INTO public.profiles (id, email, display_name)
    VALUES (
        new.id,
        new.email,
        COALESCE(new.raw_user_meta_data->>'full_name', split_part(new.email, '@', 1))
    )
    ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        display_name = COALESCE(public.profiles.display_name, EXCLUDED.display_name),
        updated_at = timezone('utc'::text, now());
    RETURN new;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();


-- ------------------------------------------------------------------------------
-- 3. Documents Metadata Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    file_path TEXT NOT NULL,
    size_bytes BIGINT DEFAULT 0,
    size_formatted TEXT,
    page_count INT DEFAULT 1,
    status TEXT DEFAULT 'ready' CHECK (status IN ('uploading', 'parsing', 'indexing', 'ready', 'error')),
    description TEXT,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS documents_user_id_created_at_idx 
    ON public.documents (user_id, created_at DESC);

ALTER TABLE public.documents ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can select their own documents" ON public.documents;
CREATE POLICY "Users can select their own documents"
    ON public.documents FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert their own documents" ON public.documents;
CREATE POLICY "Users can insert their own documents"
    ON public.documents FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can update their own documents" ON public.documents;
CREATE POLICY "Users can update their own documents"
    ON public.documents FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can delete their own documents" ON public.documents;
CREATE POLICY "Users can delete their own documents"
    ON public.documents FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);


-- ------------------------------------------------------------------------------
-- 4. Document Chunks & Vector Embeddings Table (pgvector)
-- Using 768-dimension embeddings (standard for Gemini text-embedding-004)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES public.documents(id) ON DELETE CASCADE,
    page_number INT NOT NULL,
    section_title TEXT,
    chunk_text TEXT NOT NULL,
    embedding vector(768),
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Fast HNSW cosine index for approximate nearest neighbor retrieval
CREATE INDEX IF NOT EXISTS document_chunks_embedding_hnsw_idx
    ON public.document_chunks
    USING hnsw (embedding vector_cosine_ops);

CREATE INDEX IF NOT EXISTS document_chunks_doc_id_page_idx
    ON public.document_chunks (document_id, page_number);

ALTER TABLE public.document_chunks ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view chunks for their own documents" ON public.document_chunks;
CREATE POLICY "Users can view chunks for their own documents"
    ON public.document_chunks FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.documents
            WHERE documents.id = document_chunks.document_id
              AND documents.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "Users can insert chunks for their own documents" ON public.document_chunks;
CREATE POLICY "Users can insert chunks for their own documents"
    ON public.document_chunks FOR INSERT
    TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.documents
            WHERE documents.id = document_chunks.document_id
              AND documents.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "Users can update chunks for their own documents" ON public.document_chunks;
CREATE POLICY "Users can update chunks for their own documents"
    ON public.document_chunks FOR UPDATE
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.documents
            WHERE documents.id = document_chunks.document_id
              AND documents.user_id = auth.uid()
        )
    )
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.documents
            WHERE documents.id = document_chunks.document_id
              AND documents.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "Users can delete chunks for their own documents" ON public.document_chunks;
CREATE POLICY "Users can delete chunks for their own documents"
    ON public.document_chunks FOR DELETE
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.documents
            WHERE documents.id = document_chunks.document_id
              AND documents.user_id = auth.uid()
        )
    );


-- ------------------------------------------------------------------------------
-- 5. Chat Sessions Table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.chat_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    document_id UUID REFERENCES public.documents(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    document_name TEXT,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS chat_sessions_user_id_updated_idx 
    ON public.chat_sessions (user_id, updated_at DESC);

ALTER TABLE public.chat_sessions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view their own chat sessions" ON public.chat_sessions;
CREATE POLICY "Users can view their own chat sessions"
    ON public.chat_sessions FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert their own chat sessions" ON public.chat_sessions;
CREATE POLICY "Users can insert their own chat sessions"
    ON public.chat_sessions FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can update their own chat sessions" ON public.chat_sessions;
CREATE POLICY "Users can update their own chat sessions"
    ON public.chat_sessions FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can delete their own chat sessions" ON public.chat_sessions;
CREATE POLICY "Users can delete their own chat sessions"
    ON public.chat_sessions FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);


-- ------------------------------------------------------------------------------
-- 6. Chat Messages Table (Strengthened Foreign Key & Session Verification)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES public.chat_sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    sender TEXT NOT NULL CHECK (sender IN ('USER', 'ASSISTANT', 'SYSTEM_NOTICE')),
    content TEXT NOT NULL,
    citations JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS chat_messages_session_created_idx 
    ON public.chat_messages (session_id, created_at ASC);

ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view messages in their sessions" ON public.chat_messages;
CREATE POLICY "Users can view messages in their sessions"
    ON public.chat_messages FOR SELECT
    TO authenticated
    USING (
        auth.uid() = user_id AND
        EXISTS (
            SELECT 1 FROM public.chat_sessions
            WHERE chat_sessions.id = chat_messages.session_id
              AND chat_sessions.user_id = auth.uid()
        )
    );

-- Hardened INSERT policy: guarantees message author is authenticated user
-- AND the targeted session belongs strictly to the same authenticated user
DROP POLICY IF EXISTS "Users can insert messages into their sessions" ON public.chat_messages;
CREATE POLICY "Users can insert messages into their sessions"
    ON public.chat_messages FOR INSERT
    TO authenticated
    WITH CHECK (
        auth.uid() = user_id AND
        EXISTS (
            SELECT 1 FROM public.chat_sessions
            WHERE chat_sessions.id = chat_messages.session_id
              AND chat_sessions.user_id = auth.uid()
        )
    );

DROP POLICY IF EXISTS "Users can delete messages in their sessions" ON public.chat_messages;
CREATE POLICY "Users can delete messages in their sessions"
    ON public.chat_messages FOR DELETE
    TO authenticated
    USING (
        auth.uid() = user_id AND
        EXISTS (
            SELECT 1 FROM public.chat_sessions
            WHERE chat_sessions.id = chat_messages.session_id
              AND chat_sessions.user_id = auth.uid()
        )
    );


-- ------------------------------------------------------------------------------
-- 7. Private Supabase Storage Bucket for PDFs
-- ------------------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'documents',
    'documents',
    false,
    52428800, -- 50 MB limit
    ARRAY['application/pdf']
)
ON CONFLICT (id) DO UPDATE SET
    public = false,
    file_size_limit = 52428800,
    allowed_mime_types = ARRAY['application/pdf'];

-- Storage RLS: Strict user isolation by folder prefix: <auth.uid()>/<filename>.pdf
DROP POLICY IF EXISTS "Users can upload their own PDFs to private storage" ON storage.objects;
CREATE POLICY "Users can upload their own PDFs to private storage"
    ON storage.objects FOR INSERT
    TO authenticated
    WITH CHECK (
        bucket_id = 'documents' AND
        ((storage.foldername(name))[1] = auth.uid()::text OR name LIKE (auth.uid()::text || '/%'))
    );

DROP POLICY IF EXISTS "Users can read their own PDFs from private storage" ON storage.objects;
CREATE POLICY "Users can read their own PDFs from private storage"
    ON storage.objects FOR SELECT
    TO authenticated
    USING (
        bucket_id = 'documents' AND
        ((storage.foldername(name))[1] = auth.uid()::text OR name LIKE (auth.uid()::text || '/%'))
    );

DROP POLICY IF EXISTS "Users can update their own PDFs in private storage" ON storage.objects;
CREATE POLICY "Users can update their own PDFs in private storage"
    ON storage.objects FOR UPDATE
    TO authenticated
    USING (
        bucket_id = 'documents' AND
        ((storage.foldername(name))[1] = auth.uid()::text OR name LIKE (auth.uid()::text || '/%'))
    )
    WITH CHECK (
        bucket_id = 'documents' AND
        ((storage.foldername(name))[1] = auth.uid()::text OR name LIKE (auth.uid()::text || '/%'))
    );

DROP POLICY IF EXISTS "Users can delete their own PDFs from private storage" ON storage.objects;
CREATE POLICY "Users can delete their own PDFs from private storage"
    ON storage.objects FOR DELETE
    TO authenticated
    USING (
        bucket_id = 'documents' AND
        ((storage.foldername(name))[1] = auth.uid()::text OR name LIKE (auth.uid()::text || '/%'))
    );


-- ------------------------------------------------------------------------------
-- 8. Hardened PostgreSQL RPC Function for pgvector Semantic Similarity Search
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.match_document_chunks(
    query_embedding vector(768),
    filter_document_id UUID,
    match_threshold double precision DEFAULT 0.65,
    match_count int DEFAULT 4
)
RETURNS TABLE (
    id UUID,
    document_id UUID,
    page_number INT,
    section_title TEXT,
    chunk_text TEXT,
    similarity double precision
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions, pg_temp
AS $$
BEGIN
    -- Defense-in-depth: Verify requesting user owns the document being queried
    IF NOT EXISTS (
        SELECT 1 FROM public.documents
        WHERE documents.id = filter_document_id
          AND documents.user_id = auth.uid()
    ) THEN
        RAISE EXCEPTION 'Access denied: You do not own this document or document does not exist.';
    END IF;

    RETURN QUERY
    SELECT
        dc.id,
        dc.document_id,
        dc.page_number,
        dc.section_title,
        dc.chunk_text,
        (1 - (dc.embedding <=> query_embedding)) AS similarity
    FROM public.document_chunks dc
    WHERE dc.document_id = filter_document_id
      AND dc.embedding IS NOT NULL
      AND (1 - (dc.embedding <=> query_embedding)) > match_threshold
    ORDER BY dc.embedding <=> query_embedding ASC
    LIMIT match_count;
END;
$$;

-- Restrict function execution strictly to authenticated users (block anonymous callers)
REVOKE ALL ON FUNCTION public.match_document_chunks(vector, UUID, double precision, int) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.match_document_chunks(vector, UUID, double precision, int) TO authenticated;

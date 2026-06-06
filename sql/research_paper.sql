CREATE TABLE IF NOT EXISTS research_paper (
    id UUID PRIMARY KEY,
    openalex_id VARCHAR(64) UNIQUE,
    doi VARCHAR(255),
    title TEXT NOT NULL,
    abstract_text TEXT,
    publication_year INTEGER,
    source_name VARCHAR(255),
    source_type VARCHAR(64),
    authors TEXT,
    landing_page_url TEXT,
    pdf_url TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

COMMENT ON TABLE research_paper IS '学术论文元数据缓存表';
COMMENT ON COLUMN research_paper.openalex_id IS 'OpenAlex work ID，例如 W123 或 https://openalex.org/W123 规整后的 ID';
COMMENT ON COLUMN research_paper.doi IS '论文 DOI';
COMMENT ON COLUMN research_paper.abstract_text IS 'OpenAlex 返回的摘要文本，当前用于摘要级 RAG';
COMMENT ON COLUMN research_paper.source_name IS '期刊、会议或论文集名称';
COMMENT ON COLUMN research_paper.source_type IS 'source 类型，例如 journal / conference';
COMMENT ON COLUMN research_paper.pdf_url IS 'OpenAlex 最佳开放获取 PDF 候选链接，可能为空';
COMMENT ON COLUMN research_paper.metadata IS '扩展元数据，例如引用数、OpenAlex 原始链接、venue 识别信息';

CREATE INDEX IF NOT EXISTS idx_research_paper_year
    ON research_paper (publication_year);

CREATE INDEX IF NOT EXISTS idx_research_paper_source_name
    ON research_paper (source_name);

CREATE INDEX IF NOT EXISTS idx_research_paper_doi
    ON research_paper (doi);

CREATE TABLE IF NOT EXISTS research_paper_collection (
    id UUID PRIMARY KEY,
    paper_id UUID NOT NULL,
    collection_name VARCHAR(100) NOT NULL DEFAULT 'default',
    note TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'saved',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_research_paper_collection_paper
        FOREIGN KEY (paper_id) REFERENCES research_paper (id),
    CONSTRAINT uk_research_paper_collection_paper
        UNIQUE (paper_id, collection_name)
);

COMMENT ON TABLE research_paper_collection IS '论文收藏与后续入库状态表';
COMMENT ON COLUMN research_paper_collection.collection_name IS '收藏集合名称，MVP 默认 default';
COMMENT ON COLUMN research_paper_collection.note IS '人工阅读备注或后续自动摘要';
COMMENT ON COLUMN research_paper_collection.status IS 'saved / indexed / synced 等流程状态';

CREATE INDEX IF NOT EXISTS idx_research_paper_collection_name
    ON research_paper_collection (collection_name);

CREATE TABLE IF NOT EXISTS research_paper_chunk_bge_m3 (
    id UUID PRIMARY KEY,
    paper_id UUID NOT NULL,
    chunk_type VARCHAR(32) NOT NULL,
    chunk_index INTEGER NOT NULL DEFAULT 0,
    content TEXT NOT NULL,
    embedding_text TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding VECTOR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_research_paper_chunk_paper
        FOREIGN KEY (paper_id) REFERENCES research_paper (id) ON DELETE CASCADE,
    CONSTRAINT uk_research_paper_chunk
        UNIQUE (paper_id, chunk_type, chunk_index)
);

COMMENT ON TABLE research_paper_chunk_bge_m3 IS '论文摘要级分块与 BGE-M3 向量索引表';
COMMENT ON COLUMN research_paper_chunk_bge_m3.paper_id IS '关联 research_paper.id';
COMMENT ON COLUMN research_paper_chunk_bge_m3.chunk_type IS 'abstract / pdf_page / section 等分块类型，当前先实现 abstract';
COMMENT ON COLUMN research_paper_chunk_bge_m3.content IS '可回显给用户的原文片段';
COMMENT ON COLUMN research_paper_chunk_bge_m3.embedding_text IS '实际送入 embedding 模型的检索文本';
COMMENT ON COLUMN research_paper_chunk_bge_m3.metadata IS '扩展引用信息，例如 title、year、doi、source_name';
COMMENT ON COLUMN research_paper_chunk_bge_m3.embedding IS 'BGE-M3 生成的 1024 维向量';

CREATE INDEX IF NOT EXISTS idx_research_paper_chunk_paper_id
    ON research_paper_chunk_bge_m3 (paper_id);

CREATE INDEX IF NOT EXISTS idx_research_paper_chunk_type
    ON research_paper_chunk_bge_m3 (chunk_type);

CREATE INDEX IF NOT EXISTS idx_research_paper_chunk_embedding
    ON research_paper_chunk_bge_m3
    USING ivfflat (embedding vector_l2_ops)
    WITH (lists = 100);

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS knowledge_base (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

COMMENT ON TABLE knowledge_base IS '知识库定义表';
COMMENT ON COLUMN knowledge_base.name IS '知识库名称';
COMMENT ON COLUMN knowledge_base.description IS '知识库简介';
COMMENT ON COLUMN knowledge_base.metadata IS '知识库扩展信息，例如标签、用途说明';

CREATE TABLE IF NOT EXISTS document (
    id UUID PRIMARY KEY,
    kb_id UUID NOT NULL,
    filename VARCHAR(255) NOT NULL,
    filetype VARCHAR(32),
    size BIGINT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_document_kb
        FOREIGN KEY (kb_id) REFERENCES knowledge_base (id)
);

COMMENT ON TABLE document IS '知识库原始文档表';
COMMENT ON COLUMN document.kb_id IS '所属知识库 ID';
COMMENT ON COLUMN document.filename IS '原始文件名';
COMMENT ON COLUMN document.filetype IS '文件类型，例如 md / txt';
COMMENT ON COLUMN document.metadata IS '文档扩展信息，例如存储路径、解析参数';

CREATE TABLE IF NOT EXISTS chunk_bge_m3 (
    id UUID PRIMARY KEY,
    kb_id UUID NOT NULL,
    doc_id UUID NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB,
    embedding VECTOR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_chunk_kb
        FOREIGN KEY (kb_id) REFERENCES knowledge_base (id),
    CONSTRAINT fk_chunk_doc
        FOREIGN KEY (doc_id) REFERENCES document (id)
);

COMMENT ON TABLE chunk_bge_m3 IS '知识库分块与向量表（bge-m3）';
COMMENT ON COLUMN chunk_bge_m3.content IS '分块后的正文内容';
COMMENT ON COLUMN chunk_bge_m3.metadata IS 'chunk 扩展信息，例如标题、chunk 序号';
COMMENT ON COLUMN chunk_bge_m3.embedding IS '标题或检索文本对应的 1024 维向量';

CREATE INDEX IF NOT EXISTS idx_chunk_bge_m3_kb_id
    ON chunk_bge_m3 (kb_id);

CREATE INDEX IF NOT EXISTS idx_chunk_bge_m3_doc_id
    ON chunk_bge_m3 (doc_id);

CREATE INDEX IF NOT EXISTS idx_chunk_bge_m3_embedding
    ON chunk_bge_m3
    USING ivfflat (embedding vector_l2_ops)
    WITH (lists = 100);

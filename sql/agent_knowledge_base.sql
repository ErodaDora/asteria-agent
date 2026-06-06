CREATE TABLE IF NOT EXISTS agent_knowledge_base (
    agent_id UUID NOT NULL,
    kb_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (agent_id, kb_id),
    CONSTRAINT fk_agent_knowledge_base_agent
        FOREIGN KEY (agent_id) REFERENCES jagent_agent (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_agent_knowledge_base_kb
        FOREIGN KEY (kb_id) REFERENCES knowledge_base (id)
        ON DELETE CASCADE
);

COMMENT ON TABLE agent_knowledge_base IS '智能体与知识库的访问权限关系表';
COMMENT ON COLUMN agent_knowledge_base.agent_id IS '智能体 ID';
COMMENT ON COLUMN agent_knowledge_base.kb_id IS '知识库 ID';

INSERT INTO agent_knowledge_base (agent_id, kb_id, created_at)
SELECT
    a.id,
    value::uuid,
    NOW()
FROM jagent_agent a
CROSS JOIN LATERAL jsonb_array_elements_text(COALESCE(a.allowed_kbs, '[]'::jsonb)) AS value
WHERE EXISTS (
    SELECT 1
    FROM knowledge_base kb
    WHERE kb.id = value::uuid
)
ON CONFLICT (agent_id, kb_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_agent_knowledge_base_kb_id
    ON agent_knowledge_base (kb_id);

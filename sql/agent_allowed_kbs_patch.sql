ALTER TABLE jagent_agent
    ADD COLUMN IF NOT EXISTS allowed_kbs JSONB;

COMMENT ON COLUMN jagent_agent.allowed_kbs IS '该智能体允许访问的知识库 id 列表';

UPDATE jagent_agent
SET allowed_kbs = '[]'::jsonb
WHERE allowed_kbs IS NULL;

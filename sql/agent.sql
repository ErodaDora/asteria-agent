CREATE TABLE IF NOT EXISTS jagent_agent (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    system_prompt TEXT NOT NULL,
    default_model_key VARCHAR(100) NOT NULL,
    allowed_kbs JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

COMMENT ON TABLE jagent_agent IS 'JAgent 智能体定义表';
COMMENT ON COLUMN jagent_agent.name IS '智能体名称';
COMMENT ON COLUMN jagent_agent.description IS '智能体简介';
COMMENT ON COLUMN jagent_agent.system_prompt IS '该智能体默认系统提示词';
COMMENT ON COLUMN jagent_agent.default_model_key IS '默认模型 key，例如 deepseek-chat / glm-4.6';
COMMENT ON COLUMN jagent_agent.allowed_kbs IS '该智能体允许访问的知识库 id 列表';

INSERT INTO jagent_agent (
    id,
    name,
    description,
    system_prompt,
    default_model_key,
    allowed_kbs,
    created_at,
    updated_at
)
VALUES (
    '10000000-0000-0000-0000-000000000001',
    'Basic Agent',
    '最小智能体入口，用于承接后续工具调用与任务编排能力。',
    '你是 JAgent 的基础智能体，请先给出简洁、清楚、面向执行的回答。',
    'deepseek-chat',
    '[]'::jsonb,
    NOW(),
    NOW()
)
ON CONFLICT (id) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    system_prompt = EXCLUDED.system_prompt,
    default_model_key = EXCLUDED.default_model_key,
    allowed_kbs = EXCLUDED.allowed_kbs,
    updated_at = NOW();

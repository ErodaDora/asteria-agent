ALTER TABLE chat_session
ADD COLUMN IF NOT EXISTS agent_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_chat_session_agent'
    ) THEN
        ALTER TABLE chat_session
        ADD CONSTRAINT fk_chat_session_agent
        FOREIGN KEY (agent_id) REFERENCES jagent_agent (id);
    END IF;
END $$;

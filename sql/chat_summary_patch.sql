ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS summary TEXT;

ALTER TABLE chat_session
    ADD COLUMN IF NOT EXISTS summary_updated_at TIMESTAMP;

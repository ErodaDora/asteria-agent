CREATE TABLE IF NOT EXISTS chat_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    agent_id UUID,
    title VARCHAR(255) NOT NULL,
    summary TEXT,
    summary_updated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_chat_session_user
        FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_chat_session_agent
        FOREIGN KEY (agent_id) REFERENCES jagent_agent (id)
);

CREATE TABLE IF NOT EXISTS chat_message (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_chat_message_session
        FOREIGN KEY (session_id) REFERENCES chat_session (id)
);

ALTER TABLE chat_sessions ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX idx_chat_sessions_user_pinned_updated ON chat_sessions(user_id, pinned, updated_at);

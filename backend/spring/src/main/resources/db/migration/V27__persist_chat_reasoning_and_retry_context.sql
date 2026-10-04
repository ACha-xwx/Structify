ALTER TABLE chat_messages ADD COLUMN reasoning_content LONGTEXT;
ALTER TABLE chat_messages ADD COLUMN chapter_id VARCHAR(64);
ALTER TABLE chat_messages ADD COLUMN thinking_enabled BOOLEAN;
ALTER TABLE chat_messages ADD COLUMN reasoning_effort VARCHAR(16);

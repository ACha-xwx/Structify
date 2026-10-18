ALTER TABLE chat_messages ADD COLUMN generation_status VARCHAR(16) NOT NULL DEFAULT 'COMPLETED';
ALTER TABLE chat_messages ADD COLUMN failure_code VARCHAR(64);

CREATE TABLE chat_attachments (
    id VARCHAR(36) PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    attachment_type VARCHAR(16) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    encoding VARCHAR(32),
    byte_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_attachment_message FOREIGN KEY (message_id) REFERENCES chat_messages(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_attachment_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_chat_attachments_message ON chat_attachments(message_id);
CREATE INDEX idx_chat_attachments_user ON chat_attachments(user_id, id);

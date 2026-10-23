-- Retrieval tuning that an administrator can change without a redeploy.
-- A single row (id = 1): when it is absent the app.knowledge.* configuration properties remain the defaults,
-- so an untouched installation behaves exactly as before.
CREATE TABLE knowledge_settings (
    id BIGINT PRIMARY KEY,
    minimum_score DOUBLE NOT NULL,
    search_limit INT NOT NULL,
    title_weight_long DOUBLE NOT NULL,
    title_weight_short DOUBLE NOT NULL,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_knowledge_settings_single CHECK (id = 1),
    CONSTRAINT fk_knowledge_settings_editor FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

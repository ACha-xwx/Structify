CREATE TABLE studyroom_event_streams (
 stream_id VARCHAR(96) PRIMARY KEY, user_id BIGINT NOT NULL,
 room_id VARCHAR(36), entity_kind VARCHAR(16) NOT NULL, entity_id VARCHAR(36) NOT NULL,
 operation_id VARCHAR(36) NOT NULL, attempt INT NOT NULL, run_id VARCHAR(36), revision INT,
 last_sequence BIGINT NOT NULL DEFAULT 0, floor_sequence BIGINT NOT NULL DEFAULT 0,
 state_version BIGINT NOT NULL DEFAULT 0, snapshot_json LONGTEXT NOT NULL,
 terminal BOOLEAN NOT NULL DEFAULT FALSE, terminal_at TIMESTAMP,
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE studyroom_events (
 stream_id VARCHAR(96) NOT NULL, sequence BIGINT NOT NULL, kind VARCHAR(32) NOT NULL,
 operation_id VARCHAR(36) NOT NULL, attempt INT NOT NULL, state_version BIGINT NOT NULL,
 run_id VARCHAR(36), revision INT, payload_json LONGTEXT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(stream_id,sequence),
 FOREIGN KEY(stream_id) REFERENCES studyroom_event_streams(stream_id) ON DELETE CASCADE
);
CREATE INDEX idx_studyroom_stream_retention ON studyroom_event_streams(terminal,terminal_at);

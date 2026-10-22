CREATE TABLE studyroom_questions (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, room_id VARCHAR(36) NOT NULL,
 run_id VARCHAR(36) NOT NULL, fragment_id VARCHAR(36) NOT NULL, content_revision INT NOT NULL,
 question VARCHAR(4000) NOT NULL, context_json LONGTEXT NOT NULL, audience VARCHAR(16) NOT NULL,
 status VARCHAR(16) NOT NULL, answer_text LONGTEXT NOT NULL, sources_json LONGTEXT NOT NULL,
 actions_json LONGTEXT NOT NULL, job_id VARCHAR(36), failure_code VARCHAR(96), failure_message VARCHAR(1000),
 assistant_message_id VARCHAR(36) NOT NULL, user_message_id VARCHAR(36) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
 FOREIGN KEY(room_id) REFERENCES study_rooms(id) ON DELETE CASCADE,
 FOREIGN KEY(run_id) REFERENCES studyroom_runs(id) ON DELETE CASCADE
);
CREATE INDEX idx_studyroom_questions_active ON studyroom_questions(user_id,run_id,status);

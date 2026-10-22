CREATE TABLE studyroom_check_sets (
 fragment_id VARCHAR(36) NOT NULL, content_revision INT NOT NULL, generation_id VARCHAR(36),
 status VARCHAR(16) NOT NULL, failure_code VARCHAR(96), pending_job_id VARCHAR(36),
 PRIMARY KEY(fragment_id,content_revision),
 FOREIGN KEY(fragment_id) REFERENCES studyroom_fragments(id) ON DELETE CASCADE
);
CREATE TABLE studyroom_checks (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, run_id VARCHAR(36) NOT NULL,
 unit_id VARCHAR(36) NOT NULL, fragment_id VARCHAR(36) NOT NULL, content_revision INT NOT NULL,
 generation_id VARCHAR(36) NOT NULL, question_order INT NOT NULL, prompt VARCHAR(800) NOT NULL,
 options_json LONGTEXT NOT NULL, answer_option VARCHAR(32) NOT NULL, analysis VARCHAR(1600) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
 FOREIGN KEY(run_id) REFERENCES studyroom_runs(id) ON DELETE CASCADE,
 FOREIGN KEY(fragment_id) REFERENCES studyroom_fragments(id) ON DELETE CASCADE
);
CREATE INDEX idx_studyroom_checks_current ON studyroom_checks(fragment_id,content_revision,generation_id);
CREATE TABLE studyroom_check_attempts (
 id VARCHAR(36) PRIMARY KEY, check_id VARCHAR(36) NOT NULL, user_id BIGINT NOT NULL,
 selected_option VARCHAR(32) NOT NULL, correct BOOLEAN NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(check_id) REFERENCES studyroom_checks(id) ON DELETE CASCADE,
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

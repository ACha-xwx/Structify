ALTER TABLE studyroom_runs ADD COLUMN state_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE studyroom_generation_jobs ADD COLUMN fragment_id VARCHAR(36);
ALTER TABLE studyroom_generation_jobs ADD COLUMN priority INT NOT NULL DEFAULT 0;
ALTER TABLE studyroom_generation_calls ADD COLUMN request_context_json LONGTEXT;
ALTER TABLE studyroom_generation_calls ADD COLUMN usage_json LONGTEXT;
ALTER TABLE studyroom_generation_calls ADD COLUMN elapsed_ms BIGINT;
ALTER TABLE studyroom_generation_calls ADD COLUMN issues_json LONGTEXT;

CREATE TABLE studyroom_fragments (
 id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36) NOT NULL, unit_id VARCHAR(36) NOT NULL,
 fragment_key VARCHAR(80) NOT NULL, fragment_order INT NOT NULL, plan_json LONGTEXT NOT NULL,
 plan_hash VARCHAR(64) NOT NULL, resource_revision VARCHAR(64), status VARCHAR(24) NOT NULL,
 content_revision INT NOT NULL DEFAULT 0, pending_revision INT, step_id VARCHAR(36),
 content_json LONGTEXT, error_code VARCHAR(96), issues_json LONGTEXT,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(run_id,unit_id,fragment_key), UNIQUE(unit_id,fragment_order),
 FOREIGN KEY(run_id) REFERENCES studyroom_runs(id) ON DELETE CASCADE,
 FOREIGN KEY(unit_id) REFERENCES studyroom_units(id) ON DELETE CASCADE);

CREATE TABLE studyroom_fragment_versions (
 fragment_id VARCHAR(36) NOT NULL, revision INT NOT NULL, status VARCHAR(24) NOT NULL,
 content_json LONGTEXT, resource_revision VARCHAR(64), issues_json LONGTEXT,
 consumed_calls INT NOT NULL DEFAULT 0, consumed_tokens BIGINT NOT NULL DEFAULT 0,
 active_duration_ms BIGINT NOT NULL DEFAULT 0,
 PRIMARY KEY(fragment_id,revision),
 FOREIGN KEY(fragment_id) REFERENCES studyroom_fragments(id) ON DELETE CASCADE);

CREATE TABLE studyroom_learning_progress (
 user_id BIGINT NOT NULL, run_id VARCHAR(36) NOT NULL, unit_id VARCHAR(36) NOT NULL,
 fragment_id VARCHAR(36), completed BOOLEAN NOT NULL DEFAULT FALSE,
 focus_sequence BIGINT NOT NULL DEFAULT 0, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(user_id,run_id), FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
 FOREIGN KEY(run_id) REFERENCES studyroom_runs(id) ON DELETE CASCADE);

CREATE INDEX idx_study_fragments_order ON studyroom_fragments(run_id,unit_id,fragment_order);
CREATE INDEX idx_study_jobs_priority ON studyroom_generation_jobs(status,priority,next_attempt_at);

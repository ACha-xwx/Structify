CREATE TABLE study_outline_drafts (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, question VARCHAR(4000) NOT NULL,
 chapter_id VARCHAR(64), depth VARCHAR(16) NOT NULL, status VARCHAR(24) NOT NULL,
 latest_revision INT NOT NULL DEFAULT 0, pending_job_id VARCHAR(36), confirmed_run_id VARCHAR(36), source_room_id VARCHAR(36), source_run_id VARCHAR(36),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE);
CREATE TABLE study_outline_revisions (
 id VARCHAR(36) PRIMARY KEY, draft_id VARCHAR(36) NOT NULL, revision INT NOT NULL, base_revision INT NOT NULL,
 outline_json LONGTEXT NOT NULL, issues_json LONGTEXT NOT NULL, change_summary_json LONGTEXT NOT NULL,
 feedback VARCHAR(4000), curriculum_version VARCHAR(64), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(draft_id,revision), FOREIGN KEY(draft_id) REFERENCES study_outline_drafts(id) ON DELETE CASCADE);
CREATE TABLE study_outline_messages (
 id VARCHAR(36) PRIMARY KEY, draft_id VARCHAR(36) NOT NULL, message_order INT NOT NULL, role VARCHAR(24) NOT NULL,
 content LONGTEXT NOT NULL, revision INT, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(draft_id,message_order), FOREIGN KEY(draft_id) REFERENCES study_outline_drafts(id) ON DELETE CASCADE);
CREATE TABLE studyroom_runs (
 id VARCHAR(36) PRIMARY KEY, room_id VARCHAR(36) NOT NULL, user_id BIGINT NOT NULL, draft_id VARCHAR(36) NOT NULL,
 outline_revision INT NOT NULL, run_revision INT NOT NULL, status VARCHAR(24) NOT NULL,
 approved_outline_json LONGTEXT NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(room_id,run_revision), FOREIGN KEY(room_id) REFERENCES study_rooms(id) ON DELETE CASCADE,
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE);
CREATE TABLE studyroom_units (
 id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36) NOT NULL, unit_key VARCHAR(80) NOT NULL, unit_order INT NOT NULL,
 status VARCHAR(24) NOT NULL, unit_spec_json LONGTEXT NOT NULL, content_json LONGTEXT,
 content_hash VARCHAR(64) NOT NULL, source_unit_id VARCHAR(36), error_code VARCHAR(96), error_message VARCHAR(1000),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(run_id,unit_key), FOREIGN KEY(run_id) REFERENCES studyroom_runs(id) ON DELETE CASCADE);
CREATE TABLE studyroom_generation_jobs (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, draft_id VARCHAR(36), run_id VARCHAR(36), unit_id VARCHAR(36),
 job_type VARCHAR(24) NOT NULL, job_key VARCHAR(200) NOT NULL UNIQUE, status VARCHAR(24) NOT NULL,
 payload_json LONGTEXT NOT NULL, attempt INT NOT NULL DEFAULT 1, lease_token VARCHAR(36), lease_until TIMESTAMP,
 heartbeat_at TIMESTAMP, next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 failure_code VARCHAR(96), failure_message VARCHAR(1000), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE);
CREATE INDEX idx_study_jobs_queue ON studyroom_generation_jobs(status,next_attempt_at,lease_until);
CREATE TABLE studyroom_generation_calls (
 id VARCHAR(36) PRIMARY KEY, job_id VARCHAR(36) NOT NULL, attempt INT NOT NULL, phase VARCHAR(80) NOT NULL,
 status VARCHAR(24) NOT NULL, quota_request_id VARCHAR(128) NOT NULL UNIQUE, response_json LONGTEXT,
 failure_code VARCHAR(96), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(job_id,attempt,phase), FOREIGN KEY(job_id) REFERENCES studyroom_generation_jobs(id) ON DELETE CASCADE);
CREATE TABLE studyroom_request_keys (
 user_id BIGINT NOT NULL, operation VARCHAR(32) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL, result_id VARCHAR(36) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(user_id,operation,idempotency_key), FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE);
ALTER TABLE study_rooms ADD COLUMN current_run_id VARCHAR(36);
ALTER TABLE study_room_steps ADD COLUMN unit_id VARCHAR(36);
ALTER TABLE study_room_steps ADD COLUMN run_id VARCHAR(36);
ALTER TABLE study_room_steps ADD COLUMN run_scope VARCHAR(36) NOT NULL DEFAULT 'legacy';
ALTER TABLE study_room_steps ADD COLUMN step_key_in_unit VARCHAR(80);
ALTER TABLE study_room_steps ADD COLUMN generation_status VARCHAR(24);
CREATE UNIQUE INDEX uq_study_unit_step ON study_room_steps(unit_id,step_key_in_unit);
ALTER TABLE study_room_messages ADD COLUMN run_id VARCHAR(36);

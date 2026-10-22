CREATE TABLE studyroom_completed_knowledge (
 user_id BIGINT NOT NULL, run_id VARCHAR(36) NOT NULL, unit_id VARCHAR(36) NOT NULL,
 completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(user_id,run_id,unit_id),
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
 FOREIGN KEY(run_id) REFERENCES studyroom_runs(id) ON DELETE CASCADE,
 FOREIGN KEY(unit_id) REFERENCES studyroom_units(id) ON DELETE CASCADE);

CREATE TABLE studyroom_lecture_regions (
 id VARCHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, run_id VARCHAR(36) NOT NULL,
 fragment_id VARCHAR(36) NOT NULL, content_revision INT NOT NULL,
 scene_id VARCHAR(160) NOT NULL, resource_id VARCHAR(240) NOT NULL,
 region_json LONGTEXT NOT NULL, deleted BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
 FOREIGN KEY(run_id) REFERENCES studyroom_runs(id) ON DELETE CASCADE,
 FOREIGN KEY(fragment_id) REFERENCES studyroom_fragments(id) ON DELETE CASCADE
);
CREATE INDEX idx_lecture_regions_fragment ON studyroom_lecture_regions(fragment_id,content_revision);

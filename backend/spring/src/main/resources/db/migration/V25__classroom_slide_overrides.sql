-- A teacher-facing correction for one step of a prepared lesson: the courseware page that should be
-- on screen while that step is taught. It outranks both the model's slideRefs and the automatic
-- alignment, because a human decided it. Keyed by script + step so it applies to every learner of the
-- same lesson instead of only the session that recorded it.
CREATE TABLE classroom_slide_overrides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    script_id VARCHAR(96) NOT NULL,
    step_index INT NOT NULL,
    slide_id VARCHAR(160) NOT NULL,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_classroom_slide_override UNIQUE (script_id, step_index),
    CONSTRAINT fk_classroom_slide_override_session FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_classroom_slide_overrides_script ON classroom_slide_overrides(script_id, step_index);

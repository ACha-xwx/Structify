-- One step of a lesson a learner wants to come back to. The labels are copied at the moment of saving, so
-- the list reads without replaying the lesson, and the page is the one that step actually shows, resolved
-- by the same alignment the classroom uses. Keyed by session + step: saving the same step twice is the
-- same bookmark, not a second row.
CREATE TABLE classroom_bookmarks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    lesson_id VARCHAR(96),
    lesson_title VARCHAR(300),
    chapter_id VARCHAR(64),
    step_index INT NOT NULL,
    slide_id VARCHAR(160),
    title VARCHAR(300) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_classroom_bookmark_step UNIQUE (user_id, session_id, step_index),
    CONSTRAINT fk_classroom_bookmark_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_classroom_bookmarks_user ON classroom_bookmarks(user_id, created_at);

-- Every question a learner asks in the middle of a lesson, and what the teacher answered.
--
-- The session keeps only the latest response (the pane shows one answer at a time), so asking a second
-- question used to erase the first: the next answer had no idea what had already been said, and the class
-- saw the same example explained twice. Keeping the exchanges per step is also what makes "what did I ask
-- during this lesson" answerable without replaying the session.
CREATE TABLE classroom_ask_exchanges (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    step_index INT NOT NULL,
    question LONGTEXT NOT NULL,
    answer LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_classroom_ask_session FOREIGN KEY (session_id) REFERENCES classroom_sessions(id) ON DELETE CASCADE
);

CREATE INDEX idx_classroom_ask_step ON classroom_ask_exchanges(session_id, step_index, id);

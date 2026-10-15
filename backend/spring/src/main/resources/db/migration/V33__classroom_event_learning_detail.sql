-- What the learning map needs from a classroom action: which step it happened on, which question it
-- answered, how many attempts that step has taken, and whether a hint came first.
--
-- Without these, a question answered on the third try is indistinguishable from one answered straight
-- away, and an event cannot be attributed to the lesson it happened in - which is why the per-lesson
-- nodes of the learning map have nothing to colour them.
ALTER TABLE classroom_events ADD COLUMN step_index INT;
ALTER TABLE classroom_events ADD COLUMN question_id VARCHAR(64);
ALTER TABLE classroom_events ADD COLUMN attempt_no INT;
ALTER TABLE classroom_events ADD COLUMN hint_used BOOLEAN;
CREATE INDEX idx_classroom_events_session_step ON classroom_events(session_id, step_index);

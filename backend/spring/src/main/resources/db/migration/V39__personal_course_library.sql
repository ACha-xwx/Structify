ALTER TABLE study_outline_drafts ADD COLUMN display_title VARCHAR(160);
ALTER TABLE study_outline_drafts ADD COLUMN activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE study_outline_drafts ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE study_rooms ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE study_rooms ADD COLUMN last_learning_at TIMESTAMP;
UPDATE study_rooms SET last_learning_at=(SELECT MAX(p.updated_at) FROM studyroom_learning_progress p WHERE p.run_id=study_rooms.current_run_id AND p.fragment_id IS NOT NULL);
UPDATE study_outline_drafts SET activity_at=updated_at;
CREATE INDEX idx_personal_courses ON study_outline_drafts(user_id,deleted_at,activity_at);

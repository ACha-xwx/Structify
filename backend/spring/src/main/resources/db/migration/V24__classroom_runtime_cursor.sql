ALTER TABLE classroom_sessions ADD COLUMN runtime_index INT NOT NULL DEFAULT -1;
ALTER TABLE classroom_sessions ADD COLUMN runtime_revision INT NOT NULL DEFAULT 0;
ALTER TABLE classroom_sessions ADD COLUMN runtime_response LONGTEXT;

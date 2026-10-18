CREATE TABLE study_rooms (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chapter_id VARCHAR(64),
    title VARCHAR(160) NOT NULL,
    initial_question VARCHAR(4000) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    current_step_id VARCHAR(36),
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_study_rooms_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_study_rooms_chapter FOREIGN KEY (chapter_id) REFERENCES chapters(id) ON DELETE SET NULL
);

CREATE INDEX idx_study_rooms_user_updated ON study_rooms(user_id, updated_at, id);

CREATE TABLE study_room_steps (
    id VARCHAR(36) PRIMARY KEY,
    study_room_id VARCHAR(36) NOT NULL,
    step_key VARCHAR(80) NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    title VARCHAR(160) NOT NULL,
    explanation LONGTEXT NOT NULL,
    evidence_json LONGTEXT NOT NULL,
    resources_json LONGTEXT NOT NULL,
    step_order INT NOT NULL,
    supplemental BOOLEAN NOT NULL DEFAULT FALSE,
    parent_step_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_study_room_step_order UNIQUE (study_room_id, step_order),
    CONSTRAINT fk_study_room_steps_room FOREIGN KEY (study_room_id) REFERENCES study_rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_study_room_steps_parent FOREIGN KEY (parent_step_id) REFERENCES study_room_steps(id) ON DELETE SET NULL
);

CREATE INDEX idx_study_room_steps_parent ON study_room_steps(study_room_id, parent_step_id, step_order);

CREATE TABLE study_room_messages (
    id VARCHAR(36) PRIMARY KEY,
    study_room_id VARCHAR(36) NOT NULL,
    message_order INT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content LONGTEXT NOT NULL,
    sources_json LONGTEXT NOT NULL,
    step_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_study_room_message_order UNIQUE (study_room_id, message_order),
    CONSTRAINT fk_study_room_messages_room FOREIGN KEY (study_room_id) REFERENCES study_rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_study_room_messages_step FOREIGN KEY (step_id) REFERENCES study_room_steps(id) ON DELETE SET NULL
);

CREATE INDEX idx_study_room_messages_history ON study_room_messages(study_room_id, message_order);

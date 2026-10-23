-- A class the school actually has: who teaches it, and which students are in it.
-- Until now nothing in the product knew that a teacher and a student belong together, so a teacher could not be
-- shown anything about "their" students. Two tables, no denormalised copies: a class, and one row per person in it.
CREATE TABLE classes (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    term VARCHAR(40) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE class_enrollments (
    class_id VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    role_in_class VARCHAR(16) NOT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (class_id, user_id),
    CONSTRAINT fk_class_enrollments_class FOREIGN KEY (class_id) REFERENCES classes (id) ON DELETE CASCADE,
    CONSTRAINT fk_class_enrollments_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_class_enrollments_user ON class_enrollments (user_id, role_in_class);

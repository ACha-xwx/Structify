CREATE TABLE sandbox_configurations (
    id BIGINT PRIMARY KEY,
    provider VARCHAR(16) NOT NULL,
    base_url VARCHAR(2048) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    last_test_status VARCHAR(64) NULL,
    last_tested_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sandbox_provider CHECK (provider IN ('PISTON', 'JUDGE0'))
);

CREATE INDEX idx_sandbox_config_updated ON sandbox_configurations(updated_at);

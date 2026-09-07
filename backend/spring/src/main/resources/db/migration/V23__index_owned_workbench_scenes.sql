CREATE INDEX idx_animation_records_user_updated
    ON animation_records(user_id, updated_at);

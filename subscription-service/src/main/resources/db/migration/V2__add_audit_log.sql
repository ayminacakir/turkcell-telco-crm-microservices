CREATE TABLE IF NOT EXISTS audit_log (
    id          UUID        PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id   UUID,
    action      VARCHAR(50) NOT NULL,
    details     TEXT,
    created_at  TIMESTAMP   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log(entity_type, entity_id);

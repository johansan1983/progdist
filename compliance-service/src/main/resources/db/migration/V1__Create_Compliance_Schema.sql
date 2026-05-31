CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(60) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    target_id VARCHAR(36),
    org_id UUID,
    payload JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_actor_id ON audit_log(actor_id);
CREATE INDEX idx_audit_log_org_id ON audit_log(org_id);
CREATE INDEX idx_audit_log_event_type ON audit_log(event_type);
CREATE INDEX idx_audit_log_created_at ON audit_log(created_at DESC);

CREATE TABLE consent_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(36) NOT NULL,
    org_id UUID NOT NULL,
    consent_version INTEGER NOT NULL DEFAULT 1,
    accepted_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    UNIQUE (user_id, org_id, consent_version)
);

CREATE INDEX idx_consent_records_user_org ON consent_records(user_id, org_id);

CREATE TABLE erasure_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(36) NOT NULL,
    org_id UUID,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'  -- PENDING, IN_PROGRESS, COMPLETED
);

CREATE INDEX idx_erasure_requests_user_id ON erasure_requests(user_id);
CREATE INDEX idx_erasure_requests_status ON erasure_requests(status);

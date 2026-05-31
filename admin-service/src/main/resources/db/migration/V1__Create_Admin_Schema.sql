CREATE TABLE business_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id UUID NOT NULL,
    rule_key VARCHAR(80) NOT NULL,
    rule_value TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (org_id, rule_key)
);

CREATE INDEX idx_business_rules_org_id ON business_rules(org_id);

-- Seed default rules comment: populated per-org on first admin login

ALTER TABLE user_profiles
    ADD COLUMN org_id UUID REFERENCES organizations(id) ON DELETE SET NULL,
    ADD COLUMN dept_id UUID REFERENCES departments(id) ON DELETE SET NULL,
    ADD COLUMN system_role VARCHAR(20) NOT NULL DEFAULT 'USER';

CREATE INDEX idx_user_profiles_org_id ON user_profiles(org_id);
CREATE INDEX idx_user_profiles_dept_id ON user_profiles(dept_id);

ALTER TABLE rooms
    ADD COLUMN org_id UUID REFERENCES organizations(id) ON DELETE CASCADE,
    ADD COLUMN dept_id UUID REFERENCES departments(id) ON DELETE SET NULL,
    ADD COLUMN channel_type VARCHAR(20) NOT NULL DEFAULT 'GENERAL';

CREATE INDEX idx_rooms_org_id ON rooms(org_id);

-- Default organization for the team-chats model: users without an explicit org fall here,
-- and chat-service treats an absent org_id claim as this org. Fixed UUID so a Keycloak
-- user-attribute / JWT claim can reference it deterministically.

INSERT INTO organizations (id, name, slug, plan)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default', 'default', 'BASIC')
ON CONFLICT (id) DO NOTHING;

-- Backfill existing profiles that have no organization.
UPDATE user_profiles
SET org_id = '00000000-0000-0000-0000-000000000001'
WHERE org_id IS NULL;

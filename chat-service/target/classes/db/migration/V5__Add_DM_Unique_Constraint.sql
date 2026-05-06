-- Prevent duplicate DM conversations between the same pair of users
CREATE UNIQUE INDEX IF NOT EXISTS uq_dm_pair
    ON conversation (dm_participant_a, dm_participant_b)
    WHERE type = 'DIRECT';

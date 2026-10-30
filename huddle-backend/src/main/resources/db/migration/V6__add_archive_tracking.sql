-- Add archive tracking to collaborations
-- Track when a collaboration was archived
ALTER TABLE collaborations ADD COLUMN archived_at TIMESTAMP;

-- Add archive eligibility to collaboration members
-- Track which members were active at the time of archive closure
ALTER TABLE collaboration_members ADD COLUMN archive_eligible BOOLEAN DEFAULT FALSE;

-- Add index for archive eligibility queries
CREATE INDEX idx_collaboration_members_archive_eligible ON collaboration_members(collaboration_id, archive_eligible);

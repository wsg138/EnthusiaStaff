-- Owner-mandated: track whether a punishment draft uses a custom duration,
-- so Admin custom-duration requests can be routed to Founder approval.
ALTER TABLE punishment_drafts
    ADD COLUMN custom_duration BOOLEAN NOT NULL DEFAULT FALSE;

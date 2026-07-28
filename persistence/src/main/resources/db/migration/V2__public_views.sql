CREATE OR REPLACE VIEW public_player_names AS
SELECT player_id, current_username, lowercase_username
FROM players
WHERE current_username IS NOT NULL;

CREATE OR REPLACE VIEW public_cases AS
SELECT
    c.case_id,
    c.target_id,
    c.public_reason,
    c.exact_reason_id,
    c.issued_at,
    c.state,
    c.revision
FROM cases c
WHERE c.visibility = 'PUBLIC'
  AND c.state <> 'FULLY_OVERTURNED';

CREATE OR REPLACE VIEW public_sanctions AS
SELECT
    s.sanction_id,
    s.case_id,
    s.target_id,
    s.sanction_type,
    s.status,
    s.issued_at,
    s.expiration_at,
    s.ended_at
FROM sanctions s
JOIN public_cases c ON c.case_id = s.case_id;

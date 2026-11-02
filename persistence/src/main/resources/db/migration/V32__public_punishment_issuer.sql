-- Publish only a bounded player-style issuer name for cases already marked public.
-- Internal notes, actor IDs, ranks, and private or overturned cases remain excluded.
CREATE OR REPLACE VIEW public_cases AS
SELECT c.case_id, c.target_id, c.public_reason, c.sanction_family, c.issued_at,
       c.state, c.revision,
       CASE WHEN c.actor_name REGEXP '^[A-Za-z0-9_]{3,16}$' THEN c.actor_name ELSE NULL END AS issued_by
FROM cases c
WHERE c.visibility = 'PUBLIC'
  AND c.state <> 'FULLY_OVERTURNED';

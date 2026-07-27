# ADR 0004: LiteBans remains authoritative during a mandatory shadow window

**Status:** Accepted

## Decision

Import LiteBans read-only and compare login, mute, and IP-ban decisions for at least 168 continuous hours with durable daily coverage. Time in maintenance does not count. Ordinary cutover is blocked by time, unexplained comparison mismatches, failed count/checksum/UUID/expiration checks, or incomplete recovery. A Founder override is explicit and audited and may waive only the time/cadence requirement; it cannot waive a mismatch, recovery, write-fence, or final-import blocker.

## Consequences

Legacy jars stay installed through shadow. EnthusiaStaff never double-enforces during that mode. Rollback returns authority to LiteBans without deleting imported evidence.

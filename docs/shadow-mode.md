# Shadow mode

`SHADOW_MIGRATION` is a non-authoritative observation period. LiteBans continues to enforce bans, mutes, and IP bans. EnthusiaStaff mirrors source records and calculates expected outcomes, but its Paper and Velocity enforcement paths do not apply those outcomes.

## Required window

Ordinary cutover requires at least 168 hours of actual shadow observation. Time spent in `MAINTENANCE` does not extend the window. Aborting maintenance resets continuity so the maintenance interval cannot be counted as shadow time.

The gate also requires:

- at least seven successful summary runs;
- no gap greater than 26 hours from shadow start through the last summary and shadow end;
- zero mismatch or failed shadow run after the uninterrupted window begins;
- complete comparison rows for every accepted run.

A failed or mismatching run resets the uninterrupted window. Repeated runs in a short period cannot substitute for daily coverage.

Velocity schedules a pass immediately and then at `litebans.shadow-interval-hours` while the mode is `SHADOW_MIGRATION`. The default interval is 24 hours and the maximum accepted configuration is 24. If `litebans.shadow-schedule-enabled=false`, operators must run `/estaff migration shadow` often enough to satisfy the same durable cadence gate.

## Compared evidence

Every shadow pass records aggregate results for:

- source and mapped counts;
- per-record and whole-source checksums;
- active sanction state;
- UUID mapping;
- expiration timestamps;
- login-ban decisions;
- mute decisions;
- IP-ban decisions, including a matching protected identity token.

New LiteBans punishments are imported on the next pass. LiteBans unbans, unmute operations, and expiration edits are reconciled transactionally. Immutable source changes are rejected rather than silently rewriting audit provenance.

Use `/estaff cutover status` to see observed hours, summary count, recovery count, comparison categories, and named blockers. Any unexplained mismatch blocks cutover. The Founder override can bypass only the shadow duration and cadence blockers; it cannot bypass mismatches, unresolved recovery operations, an in-progress migration, missing maintenance, or a missing final import.

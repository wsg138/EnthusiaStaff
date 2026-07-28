# ADR 0005: Separate moderation authority from website workflow state

**Status:** Accepted

## Decision

MariaDB owns punishments and exposes sanitized read-only views. Cloudflare D1 owns accounts, sessions, code claims, appeals, rate limits, and security events. Private appeal media lives in R2. Hyperdrive or a restricted internal API is read-only for public moderation data.

## Consequences

A website compromise cannot directly alter sanctions. Appeal acceptance submits an authenticated idempotent removal request to the normal moderation service and remains pending until its durable result is recorded.

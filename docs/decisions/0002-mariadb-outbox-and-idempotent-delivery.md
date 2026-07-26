# ADR 0002: MariaDB outbox with idempotent at-least-once delivery

**Status:** Accepted

## Decision

Commit domain changes, audit, and outbox messages together in MariaDB. Deliver at least once and deduplicate at the consumer with a durable inbox unique key. Use explicit acknowledgement and bounded leases.

## Consequences

No network transport is described as exactly once. Duplicate delivery is normal and tested. MariaDB availability is required for new destructive work; cached state can support only explicitly safe reads.

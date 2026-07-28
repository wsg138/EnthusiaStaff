# ADR 0003: Authenticated persistent Paper–Velocity channel

**Status:** Accepted

## Decision

Use a persistent framed connection with server allowlisting, protocol negotiation, HMAC-SHA-256 envelopes, timestamp and nonce replay checks, message IDs, acknowledgements, bounded queues, reconnect backoff, and health reporting. MariaDB outbox remains the recovery path.

## Consequences

The low-latency channel can fail without losing accepted work. Secrets are per server and rotatable. Invalid or unnegotiated frames are discarded before domain dispatch. Bukkit plugin messaging may report compatibility health but is not authoritative transport.

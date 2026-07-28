# ADR 0003: Authenticated persistent Paper–Velocity channel

**Status:** Accepted

## Decision

Use a persistent TLS 1.3 framed connection with Velocity
certificate and host verification, server allowlisting, protocol negotiation,
HMAC-SHA-256 envelopes, timestamp and nonce replay checks, message IDs,
acknowledgements, bounded queues, reconnect backoff, and health reporting.
There is no cleartext fallback. MariaDB outbox remains the recovery path.

## Consequences

The low-latency channel can fail without losing accepted work. TLS key and trust
stores are explicit, restart-loaded configuration and certificate rotation uses
an overlapping trust window. HMAC secrets are per server and independently
rotatable. Invalid or unnegotiated frames are discarded before domain dispatch.
Bukkit plugin messaging may report compatibility health but is not
authoritative transport.

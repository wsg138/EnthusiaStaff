# ADR 0007: Separate Discord moderation runtime and scoped sanctions

Status: accepted design, not implemented.

## Context

EnthusiaStaff currently has Paper and Velocity Minecraft runtimes plus a durable webhook-delivery subsystem. The planned Discord moderation system requires persistent Gateway connectivity, Discord-native enforcement, account linking, AutoMod, staff interactions, and cross-platform moderation without making Discord or Velocity a single point of failure for the other platform.

## Decision

Create a separate Java 21 staff Discord runtime/process rather than embedding the interactive bot into Paper or Velocity.

MariaDB and EnthusiaStaff domain services remain authoritative. Discord and Minecraft enforcement remain separate scopes. A cross-platform moderation action creates independently enforceable scoped sanctions under one case/history context rather than one ambiguous combined platform sanction.

Discord native guild bans remain the enforcement mechanism for Discord bans, while EnthusiaStaff owns policy/history. Normal Discord mute behavior uses managed role/permission enforcement so ticket/support access can remain available.

The existing webhook outbox/Velocity delivery system remains a separate notification subsystem.

A separate public Discord application exposes only sanitized public information and receives no privileged moderation credentials.

## Consequences

- Discord outages/restarts do not restart Velocity or disable Minecraft moderation.
- Minecraft outages do not inherently disable safe Discord-only moderation.
- Partial cross-platform enforcement is representable and recoverable instead of being reported as false global success.
- Staff-bot deployment adds a third non-Minecraft runtime and requires its own lifecycle, health, secrets, Gateway/rate-limit handling, tests, and staging.
- Existing documentation that says there are exactly two deployable artifacts continues to mean exactly two deployable **Minecraft jars**; it does not prohibit this separate service runtime.

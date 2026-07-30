# Integrations

Optional integrations are isolated behind adapters. An unavailable or
incompatible provider disables only its dependent capability and must produce
precise health output. EnthusiaStaff does not guess destructive behavior
through commands or undocumented reflection.

## Current integration boundaries

| Integration | Intended boundary | Current status |
| --- | --- | --- |
| EnthusiaCurrency | Exact snapshot, plan, idempotent removal, verification, and compensation | Root adapter and contract exist; provider API reconstruction and live staging remain |
| EnthusiaMarket | Stall snapshot, ownership/lock transitions, blacklist, and recovery | Root adapter exists; provider compatibility branch and staging remain |
| EnthusiaCommend | Persistent reputation blacklist enforced at every provider write entry point | Root adapter and contract exist; provider API reconstruction remains |
| EnthusiaAutoClicker | Versioned, bounded client evidence with validation timestamps | Root adapter and contract exist; provider API reconstruction remains |
| RoseChat | Pre-broadcast moderation, channels, PM evidence, mute/freeze handling, and recipient filtering | Root adapter exists; intended repository is currently unavailable |
| Simple Voice Chat | Staff/vanish-aware recipient and mute behavior | Adapter coverage and live staging remain |
| ViaVersion, Floodgate, Geyser | Java/Bedrock protocol and platform evidence | Optional adapters exist; multi-platform staging remains |
| CombatLogX | Block staff-mode entry in combat and prevent vanished/staff combat tagging | Optional adapter exists; live staging remains |
| Polar 1.7.11-beta | Violation evidence and an independent punishment ladder | Disabled because the available loader exposes no supported violation event API |
| Discord/DiscordSRV | Durable categorized notifications and optional compatibility | Durable webhook outbox is tested; live webhook and DiscordSRV staging remain |

This table is conservative. The presence of a class or soft dependency does not
mean a provider API is production-ready.

## Degraded behavior

- Missing optional plugins must not disable unrelated moderation features.
- Version mismatch disables the affected adapter.
- Provider calls use bounded workers and must not block the Paper or Velocity
  event thread.
- Destructive work persists intent before calling the provider.
- Provider results are verified against the durable plan.
- Retry is bounded and idempotent; ambiguity enters recovery quarantine.
- Health output states what is disabled, why, and whether reload or restart is
  required.

## Classloader and packaging

Provider-owned APIs should be compile-only or exposed through an agreed SPI.
Runtime jars must not shade duplicate provider API classes that create
classloader-identity conflicts. Private jars, local server files, and
uncommitted provider builds are not source dependencies.

The final packaging decision and real multi-plugin loading tests remain open
requirements.

## Security

Provider credentials come from environment variables or platform secret
stores. Logs and health output must not expose webhook URLs, tokens, raw
network addresses, private evidence, provider protocol secrets, or player asset
contents.

See the
[requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)
for current implementation and blocker status.

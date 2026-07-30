# Integrations

Optional integrations must degrade independently. A missing provider should
disable only the feature that requires it, produce precise verification output,
and leave unrelated staff work available.

## Integration matrix

| Provider | Purpose | Failure behavior |
| --- | --- | --- |
| MariaDB | Durable authority | Block new punishment and destructive edits |
| Velocity channel | Network sanctions and coordination | Block new network sanctions |
| RoseChat | Staff/global channels, mute, vanish recipients, PM evidence, automod | Disable affected chat features |
| Simple Voice Chat | Voice mute and vanish recipient handling | Text mute may remain; voice enforcement unavailable |
| ViaVersion | Protocol/version evidence | Mark version evidence unavailable |
| Floodgate/Geyser | Bedrock identity and GUI compatibility | Mark platform evidence/compatibility unavailable |
| CombatLogX | Staff-mode combat gate | Block staff-mode entry when status cannot be proven safely |
| Polar | Anticheat evidence/automation | Disable Polar automation |
| ProtocolLib | Packet-level supported features | Disable only dependent features |
| DiscordSRV/webhooks | Staff notifications | Queue durably; do not lose case action |
| LuckPerms | Rank/permission integration | Fail authority safely |
| EnthusiaCurrency | Economy confiscation | Hide/block economy action |
| EnthusiaMarket | Market compliance/restriction | Block confirmation |
| EnthusiaCommend | Reputation blacklist | Disable provider action |
| EnthusiaTeleport | Visibility/teleport compatibility | Disable dependent integration |
| PlayTimePlugin | Vanish-aware playtime | Mark integration unavailable |
| InventoryRollbackPlus | Supporting history/recovery | Do not present as EnthusiaStaff rollback |
| EnthusiaAutoClicker | Versioned client evidence | Show unknown/unavailable safely |

## RoseChat

Required stable bridge behavior:

- Current/set channel
- Staff and global channels
- Public/private classification
- Pre-broadcast moderation
- Private-message report capture
- Join/quit rendering
- Vanish-aware recipients
- Mute enforcement
- Staff-only frozen chat
- Staff-chat toggle
- Reload-safe registration

The current provider repository/API is unavailable according to the
requirements matrix, so complete RoseChat integration is blocked.

## Polar

Target version: 1.7.11-beta, command namespace `/enthusia`.

The integration may use a private compile-only loader but must not commit,
shade, publish, or decompile unrelated private internals. It needs a supported
violation event API. Until that exists, automatic Polar punishment remains
disabled.

## Provider API safety

Cross-plugin destructive actions must use stable provider contracts and
idempotency keys. Do not bypass Currency, Market, Reputation, or chat storage
with raw database writes or reflection into private implementation state.

## Packaging

Inspect shaded jars before release. Provider-owned API classes must not appear
twice across plugin classloaders. Prefer provider API artifacts as
`compileOnly` or use Enthusia-owned SPI contracts with explicit adapters.

## Verification

`/estaff verify full` should report each provider as:

- `PASS`
- `WARNING`
- `DISABLED`
- `RESTART REQUIRED`
- `CRITICAL`

“Plugin present” is not enough. Verification must check compatible API,
registration, event reception where possible, and feature readiness without
running destructive tests.

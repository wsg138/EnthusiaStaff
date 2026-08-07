# Integrations

Optional integrations must degrade independently. A missing or incompatible
provider should disable only the feature that requires it, produce actionable
verification output and leave unrelated moderation work available.

- Completion, source files and remaining work:
  [[Integrations, Migration, and Release Readiness]]
- Core health/degraded behavior: [[Core Platform and Infrastructure]]
- Runtime configuration: [[Configuration]]
- Commands and verification: [[Commands and Permissions]]

## Integration matrix

| Provider | Purpose | Required failure behavior |
| --- | --- | --- |
| MariaDB | Durable authority for moderation/recovery | Block new punishments and destructive edits; preserve safe reads/status |
| Paper–Velocity channel | Network sanctions and coordination | Block new network sanctions; expose reconnect/backlog state |
| RoseChat | Staff/global channels, mute, vanish recipients, PM evidence, automod | Disable only affected chat features |
| Simple Voice Chat | Voice mute and vanish-aware recipients | Text mute may remain; report voice enforcement unavailable |
| ViaVersion/ViaBackwards | Protocol/version evidence | Mark version evidence unknown/unavailable |
| Floodgate/Geyser | Bedrock identity and compatibility | Mark platform/alias/GUI compatibility unavailable |
| CombatLogX | Staff-mode combat gate | Block staff-mode entry when safe combat state cannot be proven |
| Polar | Anticheat evidence/future automation | Disable Polar automation only |
| ProtocolLib | Player-info/entity packet handling | Fail closed for dependent spectator/vanish presentation |
| DiscordSRV/webhooks | Staff notifications | Queue durably; never undo a valid case because Discord is down |
| LuckPerms | Command discovery and rank permissions | Fail authority safely; central policy still rechecks writes |
| EnthusiaCurrency | Economy confiscation/restoration | Hide or block economy actions |
| EnthusiaMarket | Market restriction/removal/restoration | Block confirmation when provider authority is unavailable |
| EnthusiaCommend | Reputation blacklist | Disable provider action only |
| EnthusiaTeleport | Visibility/teleport compatibility | Disable dependent integration behavior |
| PlayTimePlugin | Vanish-aware playtime | Mark integration unavailable without exposing vanished staff |
| InventoryRollbackPlus | Supporting history/recovery context | Never present it as EnthusiaStaff whole-server rollback |
| EnthusiaAutoClicker | Versioned client evidence | Show unknown/unavailable safely |

## Where integration code lives

| Location | Responsibility |
| --- | --- |
| [Integration contracts](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-contracts/src/main/java) | Stable compile-time contracts for Enthusia-owned providers |
| [Paper integration adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/integration) | Bukkit-side provider discovery and behavior |
| [Paper client adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/client) | ViaVersion, Floodgate/Geyser, AutoClicker and client evidence |
| [Paper economy adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/economy) | EnthusiaCurrency moderation gateway |
| [Visibility API](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/api/StaffVisibilityService.java) | Shared vanish decision boundary for other plugins |
| [Paper integration manager](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperIntegrationManager.java) | Integration lifecycle and shutdown ownership |
| [Velocity configuration](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java) | Proxy/Discord/site integration settings |

## Enthusia-owned providers

### EnthusiaCurrency

Used for exact economy snapshots, validated removal plans, idempotent replay,
conflict handling and restoration. EnthusiaStaff must never bypass Currency with
raw balance SQL.

Current state: the contract/gateway and moderation journal foundations exist; the
complete provider API and cross-plugin staging remain incomplete.

### EnthusiaCommend

Used for persistent reputation blacklists. Enforcement must cover GUI, command
and API write paths while preserving existing score/viewing behavior.

Current state: the required contract is defined; provider implementation and
cross-surface enforcement remain incomplete.

### EnthusiaAutoClicker

Used for versioned bounded client handshake/evidence. Unknown, missing or
unsupported evidence must display safely rather than being treated as cheating.

Current state: the contract and consumer foundations exist; provider storage and
full integration remain incomplete.

### Enthusia-RoseChat

Required capabilities:

- current/set channel;
- staff and global channels;
- public/private classification;
- pre-broadcast moderation;
- private-message report capture;
- join/quit rendering;
- vanish-aware recipients;
- mute enforcement;
- staff-only frozen chat;
- staff-chat toggle;
- reload-safe registration.

The supported provider repository/API required for the complete bridge is
currently unavailable/incomplete. Report PM evidence, strict pre-broadcast
automod and several staff/visibility features remain blocked.

### EnthusiaMarket

Used for supported stall review, restriction, removal and restoration. The
provider's own transaction model remains authoritative.

Current state: contracts/expected boundaries exist; the complete provider
implementation and staging remain incomplete.

## Third-party integrations

### ProtocolLib

Supports packet-level player-info/entity behavior where Paper APIs are
insufficient. If unavailable or incompatible, spectator staff presentation must
fail closed rather than exposing actual spectator state.

See [[Vanish Internals]].

### Floodgate and Geyser

Used for Bedrock identity, `*` aliases and client compatibility.

Platform persistence has an explicit evidence boundary:

- Paper resolves platform from the supported Floodgate API and the observed
  Geyser/Floodgate availability state for that player UUID.
- A working Floodgate observation may persist `BEDROCK` or `JAVA`.
- Geyser with missing, unavailable or incompatible Floodgate persists `UNKNOWN`;
  provider failure is never treated as proof of Java.
- Velocity presence observations are intentionally unverified and therefore
  persist `UNKNOWN` until a Paper backend supplies verified provider evidence.
  They may update UUID, name history and presence, but cannot downgrade a known
  platform.
- A username beginning with `*` is a supported Bedrock alias shape, not proof of
  platform. Platform is never inferred from username text.
- Verified Bedrock evidence repairs legacy Java/unknown rows and is not
  overwritten by later unverified, duplicate or out-of-order proxy observations.
- Current and historical `*` aliases remain case-insensitively resolvable and
  searchable through the player directory.

Full representative Java/Bedrock, reconnect and multi-backend acceptance remains
owned by private validation package `ES-V02`; this source package does not claim
that staging evidence.

### ViaVersion and ViaBackwards

Used for protocol/version evidence. Version data is context, not proof of a rule
violation.

### CombatLogX

Used to block unsafe staff-mode entry and prevent staff-mode/vanished players from
creating or receiving combat tags.

### Polar

Target integration version: `1.7.11-beta`, command namespace `/enthusia`.
Automatic punishment remains disabled until Polar exposes a supported
violation-event contract. Private internals must not be shaded, decompiled or
presented as a supported API.

### Simple Voice Chat

Used for voice-mute and vanish-aware recipients. Voice integration failure should
not corrupt text punishment state.

## Provider API safety

For every destructive provider action:

- use a supported stable contract;
- supply an idempotency key;
- record durable intent before external effects;
- verify the returned result;
- distinguish unavailable, conflict, retryable and terminal failure;
- enter visible recovery/quarantine when outcome is ambiguous;
- never use raw provider SQL, reflection into private state or command dispatch as
  a transaction mechanism.

## Packaging and classloaders

Provider APIs should normally be `compileOnly` or explicit Enthusia-owned SPI
contracts. Runtime-jar inspection rejects provider-owned API duplication, but
release acceptance must still install all providers together and verify service
discovery/classloader behavior.

## Verification output

`/estaff verify full` should classify each provider as:

- `PASS`
- `WARNING`
- `DISABLED`
- `RESTART REQUIRED`
- `CRITICAL`

“Plugin present” is not sufficient. Verification should check compatible API,
service registration, required callbacks/events where safely possible and the
exact dependent features. It must not run destructive provider actions as a test.

## Current completion

Provider implementation/classloader compatibility is one of the largest remaining
release areas. The detailed percentages, provider source links, website/Discord
status and migration dependencies are maintained in
[[Integrations, Migration, and Release Readiness]].

## Related pages

- [[Integrations, Migration, and Release Readiness]]
- [[Core Platform and Infrastructure]]
- [[Configuration]]
- [[Commands and Permissions]]
- [[Vanish Internals]]
- [[Build and Testing]]

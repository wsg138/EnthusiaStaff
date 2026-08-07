# Integrations

Optional integrations must degrade independently. A missing or incompatible provider should disable only the behavior that requires it, produce actionable verification output, and leave unrelated moderation features available when that is safe.

- Current integration/release status: [[Integrations, Migration, and Release Readiness]]
- Core health/degradation: [[Core Platform and Infrastructure]]
- Runtime configuration: [[Configuration]]
- Commands/verification: [[Commands and Permissions]]
- Review guidance: [[Code Review Guide]]

## Integration matrix

| Provider | Purpose | Required failure behavior |
| --- | --- | --- |
| MariaDB | Durable moderation/recovery authority | Block unsafe writes/destructive edits; preserve safe reads/status where possible |
| Paper-Velocity channel | Network sanctions and coordination | Block unsafe network writes; expose reconnect/backlog state |
| RoseChat | Staff/global channels, mute, vanish recipients, PM evidence, automod | Disable only affected chat features |
| Simple Voice Chat | Voice mute and vanish-aware recipients | Text moderation may remain; report voice enforcement unavailable |
| ViaVersion/ViaBackwards | Protocol/version evidence | Mark version evidence unknown/unavailable |
| Floodgate/Geyser | Verified Bedrock platform evidence and client compatibility | Keep platform `UNKNOWN` when provider evidence is unavailable/incompatible; retain safe lookup behavior |
| CombatLogX | Staff-mode combat gating | Block unsafe staff-mode transition when combat safety cannot be established |
| Polar | Anticheat evidence/supported automation | Disable unsupported automation only |
| ProtocolLib | Player-info/packet visibility support | Fail conservatively for dependent vanish/spectator presentation |
| Discord delivery/webhooks | Staff notifications | Queue durably where configured; never undo a valid case because Discord is down |
| LuckPerms/permission provider | Command discovery/rank permissions | Fail authority safely; central application policy still rechecks writes |
| EnthusiaCurrency | Economy confiscation/restoration | Hide/block economy actions when provider authority is unavailable |
| EnthusiaMarket | Market moderation/restoration | Block confirmation when provider authority cannot be proved |
| EnthusiaCommend | Reputation blacklist | Disable provider-specific action only |
| EnthusiaTeleport | Visibility/teleport compatibility | Disable dependent integration behavior |
| PlayTimePlugin | Vanish-aware external behavior | Degrade without exposing hidden staff |
| InventoryRollbackPlus | Supporting history/recovery context | Never present it as EnthusiaStaff whole-server rollback |
| EnthusiaAutoClicker | Versioned client evidence | Show unknown/unavailable safely |

## Where integration code lives

| Location | Responsibility |
| --- | --- |
| [Integration contracts](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-contracts/src/main/java) | Stable compile-time contracts for Enthusia-owned providers |
| [Paper integration adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/integration) | Bukkit-side provider discovery/behavior |
| [Paper client adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/client) | ViaVersion, Floodgate/Geyser, AutoClicker and client evidence |
| [Paper economy adapters](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/economy) | EnthusiaCurrency moderation gateway |
| [Visibility API](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/api/StaffVisibilityService.java) | Shared visibility decision boundary |
| [Paper integration manager](https://github.com/wsg138/EnthusiaStaff/blob/main/paper/src/main/java/net/enthusia/staff/paper/PaperIntegrationManager.java) | Provider lifecycle/shutdown ownership |
| [Velocity configuration](https://github.com/wsg138/EnthusiaStaff/blob/main/velocity/src/main/java/net/enthusia/staff/velocity/VelocityConfiguration.java) | Proxy/network/Discord/site integration settings |

## Enthusia-owned providers

### EnthusiaCurrency

Used for exact balance snapshots/plans, idempotent removal, verification, conflict handling and restoration. EnthusiaStaff owns moderation intent/journaling; Currency remains balance authority. Raw provider balance SQL is outside this contract.

Current state: contract/gateway/journal foundations exist; complete provider-side moderation behavior and representative cross-plugin recovery staging remain incomplete.

### EnthusiaCommend

Used for a persistent moderation blacklist that must be enforced at every provider write entry point rather than one GUI surface.

Current state: required boundary is defined; complete provider implementation and cross-surface staging remain incomplete.

### EnthusiaAutoClicker

Used for versioned bounded client handshake/evidence. Unknown, missing, unsupported or stale evidence is context—not automatic proof of cheating.

Current state: contract/consumer foundations exist; provider/runtime integration remains incomplete.

### Enthusia-RoseChat

Intended capabilities include staff/global channels, current/set channel, public/private classification, pre-broadcast moderation, private-message report capture, vanish-aware recipients, mute enforcement, frozen staff-only chat, staff-chat toggle and reload-safe registration.

The supported provider API required for all of those paths is still incomplete/unavailable. Do not invent a provider contract from assumptions, reflection or command behavior. Dependent PM-evidence/strict automod/chat-visibility work remains limited until a supported contract exists.

### EnthusiaMarket

Used for supported stall moderation, review, ownership/restriction changes and restoration while preserving the market plugin's own transaction/rent semantics.

Current state: contract boundaries exist; complete provider implementation and end-to-end staging remain incomplete.

## Floodgate and Geyser

Platform identity uses supported provider evidence, not username shape.

- UUID is authoritative.
- A successful supported Floodgate observation may persist verified `BEDROCK` or `JAVA` platform evidence.
- Geyser with missing/unavailable/incompatible Floodgate remains `UNKNOWN`; provider failure is not proof of Java.
- Velocity presence is intentionally unverified for platform. It may update UUID/name/presence but cannot downgrade a verified platform record.
- A username beginning with `*` is a supported lookup alias shape, not platform proof.
- Verified Bedrock evidence can repair legacy Java/unknown rows and should not be overwritten by later duplicate/out-of-order unverified proxy observations.
- Current and historical `*` aliases remain case-insensitively resolvable through the player directory.

Representative Java/Bedrock/Geyser/Floodgate reconnect, server-switch, provider-failure, GUI/text-fallback and packet/visibility acceptance is still required before making staging claims.

## ProtocolLib

Used for narrowly scoped packet-level visibility behavior where Paper APIs alone are insufficient. Missing/incompatible ProtocolLib must not expose a state that the visibility contract considers unsafe.

Deep dive: [[Vanish Internals]].

## ViaVersion and ViaBackwards

Used for protocol/version context. Version information is evidence/context only and should degrade to unknown/unavailable when the supported provider is missing.

## CombatLogX

Used to prevent unsafe staff-mode transitions around combat and avoid staff-mode behavior silently bypassing combat policy. Provider absence or incompatible behavior should surface explicitly rather than be guessed.

## Polar

The project has targeted Polar `1.7.11-beta` for supported integration discovery. Automatic punishment must stay disabled unless the supported provider API exposes the required reliable violation-event contract. Private internals must not be decompiled, shaded or presented as a public API.

## Simple Voice Chat

Used for voice-mute and vanish-aware voice recipients. Voice integration failure must not corrupt text sanction state or cause a text moderation action to be reported as fully voice-enforced.

## Provider API safety

For a destructive provider action:

- call a supported stable contract;
- carry an idempotency/external operation identity;
- record durable moderation intent before external effects where required;
- validate/recheck authority at the appropriate service boundary;
- verify the returned/external result;
- distinguish unavailable, conflict, retryable, terminal and ambiguous outcomes;
- quarantine/retain recovery evidence when the external result cannot be proved;
- bound retries/timeouts;
- never use raw provider SQL, reflection into private internals, or command dispatch as a transaction protocol.

## Packaging and classloaders

Provider APIs should normally remain `compileOnly` or use explicit Enthusia-owned SPI/contracts. Runtime-JAR inspection can detect copied provider classes, but it does not prove live service discovery or classloader compatibility.

Representative release staging should install supported providers together and test present/missing/incompatible/reload/restart cases.

## Verification output

`/estaff verify full` should distinguish states such as:

- `PASS`
- `WARNING`
- `DISABLED`
- `RESTART REQUIRED`
- `CRITICAL`

“Plugin present” is insufficient. Verification should identify whether the compatible API/service needed by the dependent capability is actually available. Verification must not perform destructive provider operations merely to prove discovery.

## Current state

Integration/provider work remains one of the largest release-readiness areas. Several useful adapters and contracts are merged, including the current verified Floodgate identity boundary, but multiple provider-side implementations and representative all-provider/classloader/runtime acceptance are still incomplete.

See [[Integrations, Migration, and Release Readiness]] for the current qualitative status rather than transient package identifiers or exact completion percentages.

## Related pages

- [[Integrations, Migration, and Release Readiness]]
- [[Core Platform and Infrastructure]]
- [[Configuration]]
- [[Commands and Permissions]]
- [[Vanish Internals]]
- [[Code Review Guide]]
- [[Build and Testing]]
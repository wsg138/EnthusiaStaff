# Integrations

Optional integrations must degrade independently. A missing or incompatible provider should disable only behavior that actually depends on it, surface an actionable health state, and leave unrelated moderation available when that is safe.

- Overall release/integration status: [[Integrations, Migration, and Release Readiness]]
- Discord/StaffBot: [[Discord Moderation Platform]]
- StaffBot operations: [[Staff Bot Runtime and Operations]]
- Public site/web APIs: [[Website and Web API]]
- Core health/config: [[Core Platform and Infrastructure]], [[Configuration]]
- Review guidance: [[Code Review Guide]]

## Integration matrix

| Provider / boundary | Purpose | Required failure behavior |
| --- | --- | --- |
| MariaDB | Durable moderation/recovery authority | Block unsafe writes; preserve safe status/reads where possible |
| Paper–Velocity channel | Network sanctions/coordination | Block unsafe network writes; expose reconnect/backlog state |
| StaffBot / Discord Gateway | Interactive Discord staff UX and native Discord effects | Fail closed on identity/authority; preserve durable reconciliation; do not widen authority |
| `discord-platform-api` | Provider-neutral managed-role contract | Consumer/provider absence must be explicit; no direct JDA fallback from consumers |
| Legacy Discord webhooks | One-way staff notifications | Queue/retry durably; never undo a valid moderation action because Discord is down |
| Velocity website API | Public projections + authenticated appeal workflow | Reject invalid auth/replay/bounds; never expose privileged records directly |
| Public `enthusia-site` | Website/UI and Pages Functions | Public projection only; server-side functions mediate privileged backend access |
| `moderation-web` | Staging staff browser workspace | Read/simulation only; signed/session/replay boundaries fail closed |
| RoseChat | Staff/global channels, mute, vanish recipients, PM evidence, automod | Disable only affected chat features |
| Simple Voice Chat | Voice mute / vanish-aware recipients | Text moderation may remain; report voice enforcement unavailable |
| ViaVersion/ViaBackwards | Protocol/version evidence | Mark evidence unknown/unavailable |
| Floodgate/Geyser | Verified Bedrock platform evidence/client compatibility | Keep platform `UNKNOWN` when evidence cannot be established |
| CombatLogX | Staff-mode combat gating | Block unsafe Staff Mode transition if combat safety is unknown |
| Polar | Anticheat evidence/supported automation | Disable unsupported automation only |
| ProtocolLib | Packet/player-info visibility behavior | Fail conservatively for dependent vanish/spectator presentation |
| LuckPerms | Staff identity/discovery/duty context | Fail authority safely; central policy still rechecks writes |
| EnthusiaCurrency | Economy confiscation/restoration | Hide/block economy actions if provider authority unavailable |
| EnthusiaMarket | Market moderation/restoration | Block confirmation if provider result cannot be established |
| EnthusiaCommend | Reputation restrictions | Disable provider-specific action only |
| EnthusiaTeleport | Visibility/teleport compatibility | Disable dependent integration only |
| PlayTimePlugin | Playtime/main-account and external behavior inputs | Degrade without guessing; do not expose hidden staff |
| InventoryRollbackPlus | Supporting recovery/history context | Never present as EnthusiaStaff whole-server rollback |
| EnthusiaAutoClicker | Versioned client evidence | Show unknown/unavailable safely |

## Where integration code lives

| Location | Responsibility |
| --- | --- |
| [`integration-contracts/`](https://github.com/wsg138/EnthusiaStaff/tree/main/integration-contracts) | Stable contracts for Enthusia-owned providers |
| [`discord-platform-api/`](https://github.com/wsg138/EnthusiaStaff/tree/main/discord-platform-api) | Provider-neutral managed Discord role contract |
| [`paper/.../integration/`](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/integration) | Bukkit-side provider adapters/discovery |
| [`paper/.../client/`](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/client) | Floodgate/Geyser, ViaVersion and client evidence |
| [`paper/.../economy/`](https://github.com/wsg138/EnthusiaStaff/tree/main/paper/src/main/java/net/enthusia/staff/paper/economy) | Currency moderation adapter |
| [`staff-bot/`](https://github.com/wsg138/EnthusiaStaff/tree/main/staff-bot) | JDA/Discord runtime and Discord external effects |
| [`velocity/`](https://github.com/wsg138/EnthusiaStaff/tree/main/velocity/src/main/java/net/enthusia/staff/velocity) | Network/webhook/website API boundaries |
| [`components/enthusia-site/`](https://github.com/wsg138/EnthusiaStaff/tree/main/components/enthusia-site) | Public site + Pages Functions |
| [`moderation-web/`](https://github.com/wsg138/EnthusiaStaff/tree/main/moderation-web) | Staging browser moderation workspace |

## Discord integration boundaries

### StaffBot

StaffBot owns the privileged Discord Gateway/JDA session. Other Minecraft plugins should not open their own JDA connections for Enthusia-managed role or moderation behavior.

StaffBot integrates with:

- Discord application/guild hierarchy and rate limits;
- MariaDB Discord moderation/linking/reconciliation state;
- current Enthusia staff authority through the approved private authority boundary;
- the private moderation-read service used by the staging web workspace.

A Discord role or visible command is not sufficient authority. See [[Discord Moderation Platform]].

### Managed-role contract

`discord-platform-api` lets consumers express managed role claims without depending on JDA, Discord snowflake handling, or StaffBot persistence internals.

Review the distinction carefully:

- **contract merged** does not mean every provider/consumer migration is complete;
- consumers should not fall back to DiscordSRV/JDA implementation details silently;
- role ownership/reconciliation must not remove unrelated externally managed roles;
- desired membership must account for the approved multi-Minecraft-to-one-Discord linking semantics.

Role-sync replacement/parity remains active development until its provider/consumer migration is merged and accepted.

### Legacy webhook subsystem

Velocity’s webhook outbox remains a separate one-way notification integration. It has at-least-once delivery/retry/privacy semantics and does not own interactive moderation.

See [[Discord Delivery]].

## Website integration boundaries

### Velocity website API

Velocity owns the authoritative EnthusiaStaff-side API. Public routes use sanitized projections; authenticated routes mediate punishment-code/appeal/reviewer workflows.

A site/browser request must not directly mutate MariaDB or bypass central sanction authority.

### Public site

`components/enthusia-site/` is the synchronized static site/Cloudflare Pages Functions component. Keep privileged credentials server-side and preserve aggregate/standalone parity rules.

### Moderation web

`moderation-web/` is a staging-only Worker/static-assets staff workspace. It uses signed one-time launches, secure sessions/CSRF and independently signed/replay-protected StaffBot reads.

It is intentionally not a punishment writer or production authority. See [[Website and Web API]].

## Enthusia-owned providers

### EnthusiaCurrency

Currency remains balance authority. EnthusiaStaff owns moderation intent/journaling and should use supported plan/apply/verify/restore behavior rather than provider SQL.

### EnthusiaCommend

Reputation restrictions must be enforced through the provider’s supported contract at every relevant write surface, not just one GUI.

### EnthusiaAutoClicker

Versioned bounded client evidence is context, not automatic proof of cheating. Missing/unsupported/stale evidence should remain unknown/unavailable rather than guessed.

### Enthusia-RoseChat

Chat/staff-channel/mute/freeze/PM-evidence/visibility/automod integration must use supported RoseChat APIs. Do not invent behavior from reflection, private internals or command dispatch. Any still-missing provider path should degrade explicitly.

### EnthusiaMarket

Market moderation must preserve the provider’s ownership/rent/transaction semantics and use supported review/restriction/restoration contracts.

## Floodgate / Geyser

Platform identity is provider-evidence based:

- UUID is authoritative;
- supported verified Floodgate evidence may establish `JAVA` or `BEDROCK`;
- missing/incompatible evidence remains `UNKNOWN`;
- unverified Velocity observations cannot downgrade verified platform state;
- `*` aliases are lookup compatibility, not platform proof.

Representative Java/Bedrock/Geyser/Floodgate staging is still required for client/runtime claims.

## ProtocolLib / ViaVersion / CombatLogX / Polar / Voice

These integrations remain narrowly scoped:

- ProtocolLib supports packet-level visibility behavior where necessary;
- ViaVersion/ViaBackwards provide protocol/version context;
- CombatLogX informs safe Staff Mode transitions;
- Polar automation stays off unless a supported reliable event/API exists;
- Simple Voice Chat failure must not corrupt text sanction state.

Missing/incompatible providers should not silently widen behavior.

## Provider API safety

For a destructive external/provider action:

1. use a supported contract;
2. carry idempotency/external operation identity where required;
3. persist durable intent/recovery state before effects where required;
4. reauthorize current actor/target state;
5. apply the effect through the owning adapter;
6. verify/reconcile the result;
7. distinguish unavailable, conflict, retryable, terminal and ambiguous outcomes;
8. bound retries/timeouts;
9. preserve quarantine/recovery evidence when truth cannot be established.

Never use raw provider SQL, reflection into private internals, or command dispatch as a transaction protocol.

## Packaging and classloaders

Provider APIs should remain compile-only/service contracts where appropriate. Runtime-JAR scans detect accidental shading but do not prove live service discovery/classloader compatibility.

For `discord-platform-api`, the same rule applies in reverse: consumer plugins should not accidentally pull JDA or StaffBot implementation classes into their runtime.

## Verification

`/estaff verify full` and runtime-specific health surfaces provide non-destructive observations. “Provider present” is not the same as “capability verified.” Resolve warnings through the corresponding staging test rather than invoking destructive provider behavior simply to prove discovery.

## See also

- [[Integrations, Migration, and Release Readiness]]
- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Website and Web API]]
- [[Discord Delivery]]
- [[Core Platform and Infrastructure]]
- [[Configuration]]
- [[Commands and Permissions]]
- [[Code Review Guide]]
- [[Build and Testing]]
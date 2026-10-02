# Discord Moderation Platform

EnthusiaStaff now includes a real standalone Discord moderation runtime, durable Discord/Minecraft account linking, Discord punishment execution/reconciliation, staff read/moderation UI, and a provider-neutral managed-role API foundation. This page is the product/status hub for those features.

For building, deploying, configuring, or recovering the Java/JDA runtime, use [[Staff Bot Runtime and Operations]]. For the browser moderation workspace and public website APIs, use [[Website and Web API]]. Existing one-way webhook notifications remain documented separately in [[Discord Delivery]].

## Quick status

| Area | Current merged-main state | Important limitation |
| --- | --- | --- |
| StaffBot Java/JDA runtime | **Implemented** | Production authority is a separate cutover decision; destructive enforcement is disabled by default. |
| Staff moderation/read UI | **Implemented** | Reads/actions still require authoritative linked-staff identity and live authorization; a Discord role alone is not authority. |
| Discord punishment execution | **Implemented, not production-accepted** | Durable warn/mute/kick/ban/restriction and end/revoke/overturn flows exist, but production Discord authority is not implied by the merge. |
| Discord/Minecraft account linking | **Implemented** | Migration/cutover from DiscordSRV and role-sync parity are separate concerns. |
| Discord moderation persistence | **Implemented** | V19 owns moderation/reconciliation foundations; V20 owns account linking. |
| Provider-neutral managed-role API | **Implemented contract foundation** | `discord-platform-api` defines the contract; final StaffBot provider/consumer migrations remain separate work. |
| Staging browser moderation workspace | **Available for accepted staging read/simulation scope** | It is intentionally not a production/destructive moderation authority. See [[Website and Web API]]. |
| Legacy webhook notifications | **Available with limitations** | Separate outbound subsystem; it is not the interactive StaffBot runtime. |
| DiscordSRV console replacement | **In development** | The authenticated Discord-to-Minecraft command bridge is not merged on current `main`. |
| Evidence/case/note/linked-alt alert expansion | **In development** | Active work must not be documented as merged until it lands. |
| Cross-platform moderation expansion | **In development** | Existing merged punishment paths are authoritative only for their implemented scopes. |
| Role-sync replacement/parity | **In development / not complete** | Do not claim DiscordSRV role-sync retirement is finished from the managed-role contract alone. |

## StaffBot owns the Discord gateway

The `staff-bot` Gradle module is a separate Java 25 application using JDA. It is the intended privileged Discord Gateway owner for EnthusiaStaff.

Primary entry points:

- [`StaffBotApplication.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotApplication.java)
- [`StaffBotRuntime.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffBotRuntime.java)
- [`JdaDiscordGateway.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaDiscordGateway.java)

The runtime includes identity fencing, bounded workers, reconnect/rate-limit handling, replay protection, health/readiness support, privacy-safe logging, and graceful/forced shutdown behavior.

Other plugins should not create their own JDA sessions to implement managed role behavior. The provider-neutral contract for that direction lives in `discord-platform-api`.

Deep operational documentation: [[Staff Bot Runtime and Operations]].

## Staff moderation UI and read workflows

Merged StaffBot supports Discord-side moderation discovery/read flows rather than only outbound notifications. Current source includes:

- staff/player moderation panels;
- Discord user/message context workflows;
- Minecraft-target lookup;
- linked-account and punishment/history views;
- cases/notes views exposed by the current read model;
- signed, expiring interaction components;
- replay protection;
- authoritative linked-staff actor resolution;
- permission-aware command discovery plus action-time reauthorization.

Important paths:

- [`StaffModerationRuntime.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffModerationRuntime.java)
- [`StaffModerationController.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/StaffModerationController.java)
- [`JdaStaffModerationListener.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaStaffModerationListener.java)
- [`LinkedStaffActorResolver.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/LinkedStaffActorResolver.java)

A visible slash command, button, Discord role, or previously valid confirmation is never final authority by itself.

## Authority model

Discord actions must resolve to current Enthusia staff authority and the current target state.

The important invariants are:

- Discord roles do not independently grant punishment authority.
- A Discord actor must resolve to the linked authoritative staff identity.
- Rank/permission/self-target/higher-rank rules are evaluated through EnthusiaStaff policy.
- Cross-platform effects require explicit authorization for the intended scope.
- confirmations reauthorize before side effects rather than trusting stale snapshots;
- external hierarchy/precondition checks fail closed when they cannot be established;
- Paper-local Staff Mode requirements do not silently replace the independent global/Discord/website authority model.

See [[Rank Authority]] and [[Code Review Guide]].

## Discord punishment execution and reconciliation

Merged StaffBot includes Discord-only punishment execution infrastructure for configured warn, mute, kick, ban, and restriction flows plus end/revoke/overturn behavior.

Primary paths include:

- [`DiscordPunishmentRuntime.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentRuntime.java)
- [`DiscordPunishmentService.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentService.java)
- [`DiscordPunishmentWorker.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentWorker.java)
- [`DiscordPunishmentAuthorization.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/DiscordPunishmentAuthorization.java)
- [`JdaDiscordPunishmentGateway.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaDiscordPunishmentGateway.java)
- [`JdaKickEnforcer.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaKickEnforcer.java)
- [`JdaNativeBanEnforcer.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaNativeBanEnforcer.java)
- [`JdaMuteRoleOwnership.java`](https://github.com/wsg138/EnthusiaStaff/blob/main/staff-bot/src/main/java/net/enthusia/staff/discordbot/JdaMuteRoleOwnership.java)

### Failure safety

External Discord effects are not ordinary local database writes. The merged implementation uses durable intent/reconciliation/retry/expiry concepts so crashes, rate limits, ambiguous responses, and external changes can be resolved deliberately.

Examples of important review rules:

- do not blindly retry an ambiguous kick after the remote effect may already have happened;
- verify/reconcile native ban state rather than assuming one API response establishes durable truth;
- distinguish bot-owned mute/restriction state from unrelated human/external role changes;
- audit enough history to find the newest relevant ownership change rather than assuming a small first page is complete;
- persist/audit terminal ambiguity instead of reporting false success.

## Discord/Minecraft account linking

Merged `main` includes the account-linking runtime and V20 persistence.

The flow supports one-use short-lived link codes initiated from either side of the Minecraft/Discord boundary. Only hashes of link codes are persisted, and replacement, expiry, replay, restart, ownership history, unlink, reassignment, and main-account behavior are handled through durable state.

Minecraft-facing commands include the merged `/link` and `/unlink` paths. Authorized staff flows can correct/reassign links through the central linking services.

One Discord identity may represent multiple Minecraft identities while current ownership and historical link state remain explicit. Main-account selection supports the approved playtime-based behavior and staff override semantics.

Primary persistence migration:

[`V20__discord_account_linking.sql`](https://github.com/wsg138/EnthusiaStaff/blob/main/persistence/src/main/resources/db/migration/V20__discord_account_linking.sql)

DiscordSRV migration/mirroring compatibility exists as a transition concern; do not treat that compatibility as proof that every DiscordSRV-dependent role/console/chat workflow has already been retired.

Linked-account information is private unless a specifically approved projection says otherwise.

## Persistence

Current merged Flyway history reaches **V20**.

Discord-specific milestones:

- `V19__discord_moderation_persistence.sql` — Discord moderation subject/enforcement/reconciliation/security-state foundation;
- `V20__discord_account_linking.sql` — durable Discord/Minecraft linking runtime state.

Current open branches may contain later migration numbers. They are not part of merged `main` and must not be documented as current schema until merged.

## Provider-neutral managed-role API

`discord-platform-api` is a first-class Gradle module that defines the provider-neutral role-management contract for other Enthusia plugins.

The contract includes concepts such as:

- managed-role namespaces/keys;
- role claims;
- client/provider result states;
- unioned desired membership across multiple Minecraft identities;
- service discovery without exposing JDA, Discord snowflake plumbing, or StaffBot persistence internals to consumers.

Source:

[`discord-platform-api/`](https://github.com/wsg138/EnthusiaStaff/tree/main/discord-platform-api)

This is a **contract foundation**, not proof that every old DiscordSRV role-sync consumer has been migrated. Role-sync parity remains a separate workstream.

## Browser moderation workspace

StaffBot also supports the signed private read/launch boundary used by the staging Cloudflare moderation workspace. That surface is intentionally read/simulation-oriented and is documented in [[Website and Web API]].

Do not confuse the browser workspace with StaffBot itself or with the public Enthusia website.

## Legacy webhook delivery

The older Velocity Discord outbox/webhook subsystem still exists for bounded notification delivery. It is an at-least-once outbound integration and has different failure/privacy semantics from the interactive StaffBot runtime.

Use [[Discord Delivery]] for that subsystem.

## Current in-progress work

These areas exist in active development but are **not merged current behavior**:

- authenticated Discord-to-Minecraft console command bridge replacing DiscordSRV console;
- expanded Discord evidence/case/note/linked-alt/evasion alert workflows;
- broader cross-platform moderation integration;
- final managed-role provider/consumer migrations and DiscordSRV role-sync parity;
- expanded website appeal lifecycle beyond the routes documented in [[Website and Web API]].

The Wiki should update these entries after they merge rather than copying implementation claims from their draft branches.

## Developer source map

| Concern | Primary path |
| --- | --- |
| StaffBot runtime/JDA | `staff-bot/src/main/java/net/enthusia/staff/discordbot/` |
| Discord platform contract | `discord-platform-api/` |
| Discord moderation domain | `domain/src/main/java/net/enthusia/staff/domain/` Discord/moderation/auth packages |
| Discord JDBC state | `persistence/src/main/java/net/enthusia/staff/persistence/` Discord stores |
| V19/V20 schema | `persistence/src/main/resources/db/migration/` |
| Minecraft linking commands/adapters | `paper/` linking command/runtime paths |
| StaffBot tests | `staff-bot/src/test/java/` |
| Cross-module/MariaDB tests | `integration-tests/src/test/java/` |
| Existing webhook delivery | Velocity/domain Discord outbox paths and [[Discord Delivery]] |

## Review checklist

For Discord changes verify, at minimum:

- exactly one intended Gateway/JDA owner;
- current linked staff identity is re-established before privileged actions;
- Discord roles are not treated as standalone authority;
- target/platform/scope cannot be silently widened;
- replay and signed-component expiry are enforced;
- worker queues and retries are bounded;
- ambiguous external outcomes reconcile safely;
- reconnect/shutdown cannot leave stale workers mutating new runtime state;
- database intent/audit and external Discord effect ordering is crash-safe;
- bot-owned role/ban state is distinguished from unrelated external changes;
- account-link codes are one-use, short-lived, hashed at rest, and ownership-safe;
- logs/errors do not expose tokens, private IDs/evidence, signing material, or raw network identity;
- tests are not misrepresented as live Discord/staging/production acceptance.

## See also

- [[Staff Bot Runtime and Operations]]
- [[Website and Web API]]
- [[Discord Delivery]]
- [[Rank Authority]]
- [[Architecture]]
- [[Developer Guide Index]]
- [[Developer Code Guide]]
- [[Code Review Guide]]
- [[Build and Testing]]
- [[Implementation Status]]
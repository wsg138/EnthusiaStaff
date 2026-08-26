# Discord Moderation Platform

This page explains the Discord moderation expansion, what is already present on merged `main`, and what is still future/runtime work.

> **Current status: partial foundation, not an operational staff bot.** Merged `main` contains Discord/Minecraft moderation-subject and scope contracts, V19 persistence, and central Discord-origin authorization policy. It does **not** yet provide the finished account-linking runtime, interactive staff bot, Discord punishment enforcement, AutoMod, native-ban cutover, role sync, or public information bot.

For existing webhook notification delivery, use [[Discord Delivery]]. For the full product contract, see [`docs/discord-moderation-platform.md`](../../../docs/discord-moderation-platform.md). Developers should also read [`docs/discord-authorization.md`](../../../docs/discord-authorization.md), [[Developer Code Guide]], and [[Code Review Guide]].

## Quick status

| Area | Merged-main state | What that means |
| --- | --- | --- |
| Moderation subject / platform identity model | **Implemented foundation** | Discord-only, Minecraft-only and linked subjects can be represented without collapsing the two enforcement scopes. |
| Discord/Minecraft link history model | **Implemented foundation** | Domain and persistence can represent current/historical links and main-account state; the user-facing five-minute code workflow is not merged runtime behavior. |
| Discord moderation persistence | **Implemented foundation** | V19 adds subject/link, enforcement-target, evidence-metadata, security-lock, reconciliation and maintenance state plus JDBC adapters/tests. |
| Discord-origin authorization | **Implemented foundation** | Central domain policy models role/rank limits, target protection, consequence limits, cross-platform preconditions and reauthorization. |
| Existing webhook notification delivery | **Available with limitations** | The current Velocity outbox worker can deliver sanitized staff notifications. It is separate from the future interactive bot. |
| Account-link command/runtime and DiscordSRV migration | **Not available on merged main** | Do not train users on a linking command or migration flow until the runtime work is merged and validated. |
| Interactive staff Discord bot | **Not available on merged main** | No staff-bot module is part of the current merged runtime artifacts. |
| Discord punishments / restrictions / reconciliation | **Planned beyond the foundation** | The data and authorization model do not themselves call Discord or enforce a sanction. |
| AutoMod / security-lock automation | **Planned beyond the foundation** | Storage concepts exist; live detection/enforcement and false-positive acceptance are separate work. |
| Public information bot / role sync / final ban migration and cutover | **Planned** | These remain separate trust boundaries and release gates. |

A merged schema or passing domain test is not staging evidence for a Discord bot or live Discord enforcement.

## What the merged foundation establishes

### Moderation subjects and scopes

The domain can represent a moderation subject that has Minecraft identities, a Discord identity, or both. Enforcement targets retain an explicit platform and explicit scope instead of using a magic cross-platform `BOTH` state.

This matters because a staff action started on Discord should not silently become a Minecraft punishment, and vice versa. Cross-platform effects require an explicit requested consequence and an independently authorized target/scope.

Primary source areas:

- `domain/src/main/java/net/enthusia/staff/domain/moderation/`
- `domain/src/main/java/net/enthusia/staff/domain/auth/`
- `domain/src/main/java/net/enthusia/staff/domain/ports/DiscordModerationPersistenceStore.java`

### Persistence and migration V19

Current merged Flyway history ends at:

```text
V19__discord_moderation_persistence.sql
```

V19 adds durable structures for moderation subjects, Minecraft and Discord identities, link history, main-account selection, platform-specific enforcement targets, Discord evidence metadata, security locks, reconciliation state and bounded maintenance work.

Important persistence entry points include:

- `JdbcDiscordModerationPersistenceStore.java`
- `JdbcDiscordIdentityRepository.java`
- `JdbcDiscordLinkRepository.java`
- `JdbcDiscordMainAccountRepository.java`
- `JdbcDiscordOperationalRepository.java`
- `JdbcDiscordReplayGuard.java`

Relevant MariaDB coverage includes `DiscordPersistenceSafetyIntegrationTest` and migration/upgrade tests. Those tests prove the exercised schema and persistence properties; they do not prove a live Discord API flow.

### Authorization policy

`DiscordModerationAuthorizationService` is the central Discord-origin policy boundary. Related policy objects model authorization requests/snapshots, runtime limits, operation/consequence policy, external preconditions, target protection and cross-platform revalidation.

The important rule is that Discord command visibility or a Discord role is never final authority. A caller must resolve current Enthusia staff identity/rank and target state, authorize the exact consequence/scope, satisfy any external hierarchy preconditions, and reauthorize stale confirmation flows before a side effect.

Current design deliberately allows Developer to have Mod-equivalent **Discord-only temporary moderation authority** while retaining the existing independent Minecraft authorization rules. That distinction is represented in the merged domain policy, but there is no merged interactive Discord command runtime using it yet.

## Linking design

The finished design allows a player to start linking from either Minecraft or Discord with a one-use five-minute code completed on the other platform.

One Discord account may have multiple current Minecraft identities, while one Minecraft UUID may have only one current Discord owner. Historical unlink records remain auditable. The first Minecraft link becomes the main account; automatic selection uses PlayTimePlugin active playtime with the approved stability threshold, while authorized staff may override/lock the main selection.

**Do not confuse the model with availability.** The merged domain/persistence foundation can represent these facts, but the complete command/code flow, provider adapter, migration of existing DiscordSRV links and production data migration remain separate runtime work.

Linked-account details are private. Public output must never expose another player's Discord link, linked alts, or historical links.

## Staff authority design

The target Discord authority model is:

- **Helper:** investigation/read access, warnings and configured short temporary mutes only.
- **Mod:** configured temporary Discord punishments and custom temporary durations within policy.
- **Developer:** the same Discord-only temporary authority as Mod, without gaining Minecraft punishment authority merely from Discord.
- **Admin/Founder:** permanent Discord ban, permanent mute and permanent channel restriction plus elevated correction/overturn authority as configured.

Self-targeting, protected target hierarchy abuse, stale actor/target state and unauthorized cross-platform consequences must fail closed.

See [[Roles and Permissions|Rank-Authority]] for the overall authority model and [[Code Review Guide]] for review checks.

## Finished enforcement design

When later runtime work is merged, Discord enforcement is intended to support warnings, managed-role mutes, kicks, temporary/permanent native bans, and temporary/permanent channel/category restrictions.

Normal Enthusia Discord mute is designed around bot-managed role/permission enforcement rather than Discord Timeout so approved private support/ticket areas can remain usable. Channel restrictions may be read-only or no-access and may be temporary or permanent.

The merged V19 enforcement/reconciliation state is preparation for safe durable execution. It is **not** proof that these effects are currently applied to Discord.

## AutoMod and security locks

The finished AutoMod design combines local server-specific rules with an AI moderation signal where configured. Uncertain or AI-only results should be review input rather than guessed severe punishment. Ticket/support exemptions, message edits, invite/link policy, repeated cross-channel link behavior and staff handling require explicit runtime policy.

An Account Security Lock is a safety state, not a punishment. V19 can persist security-lock state, but live compromised-account detection, deletion/DM behavior and unlock commands are not currently merged operational behavior.

## Evidence and cases

The finished Discord workflow is intended to capture bounded message context, attachments/metadata and edit history for authorized staff review while preserving retention and privacy boundaries. V19 currently provides evidence **metadata** persistence foundations; do not imply that the full Discord capture pipeline exists merely because the table exists.

Formal appeals remain website-only in the approved design. Discord support may answer questions but should not become a second appeal authority.

## Ban migration and authority cutover

Discord's native guild ban remains the mechanism that prevents a banned account from joining. The planned migration imports current native bans into EnthusiaStaff without unbanning/rebanning users, preserves available audit facts, marks unavailable legacy facts as unknown, and reconciles the imported set before any authority transition.

No production Discord ban migration or cutover is implied by the merged domain/schema work. Final authority requires exact-candidate runtime validation, reconciliation, outage/retry testing and explicit operational approval.

## Public information bot

The planned public bot is a separate application and trust boundary. It may expose sanitized public commands such as player/guild/leaderboard/server links, but never linked accounts/alts, historical links, private cases, notes, evidence or privileged staff data.

It is not part of the current merged runtime artifacts.

## Developer and reviewer map

Start here for Discord changes:

| Concern | Primary source / proof |
| --- | --- |
| Subject identity and scope semantics | `domain/.../moderation/` and their unit tests |
| Discord-origin authority | `domain/.../auth/Discord*` plus Discord authorization tests and `docs/discord-authorization.md` |
| Durable Discord moderation state | `persistence/.../JdbcDiscord*`, V19, MariaDB integration tests |
| Legacy webhook notifications | `domain/.../discord/`, `JdbcDiscordOutboxStore`, `velocity/.../DiscordOutboxWorker.java`, [[Discord Delivery]] |
| Full approved product behavior | `docs/discord-moderation-platform.md` |
| Source navigation | [[Developer Code Guide]] |
| Review invariants | [[Code Review Guide]] |
| Evidence interpretation | [[Build and Testing]] |

Review especially for platform/scope mismatches, stale authorization snapshots, role/rank confusion, replay/idempotency, link-history corruption, Discord snowflake bounds, privacy leaks, partial external failure and any code that treats a future Discord runtime as already authoritative.

## Go deeper

- [[Discord Delivery]] — current webhook notification subsystem.
- [[Roles and Permissions|Rank-Authority]] — current and Discord-specific authority semantics.
- [[Developer Code Guide]] — detailed code/source map.
- [[Code Review Guide]] — cross-cutting review checklist.
- [[Architecture]] — module/runtime boundaries.
- [[Build and Testing]] — what automated, MariaDB and runtime evidence prove.
- [[Implementation Status]] — overall merged-main product status.
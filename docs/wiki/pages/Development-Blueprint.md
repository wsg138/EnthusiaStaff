# Remaining Development Map

Use this page to understand **what kinds of product work remain and which authoritative source answers the next question**. It is intentionally not a branch, package, or worker dashboard.

## Quick answer

EnthusiaStaff has substantial merged foundations, but production authority is still gated by unfinished product areas and representative validation.

The durable release path remains:

1. finish correctness/safety gaps in merged product areas;
2. complete required provider and external-component contracts;
3. run representative distributed Java/Bedrock/provider validation;
4. run destructive/load/process-recovery acceptance;
5. complete private LiteBans migration/shadow evidence and owner cutover acceptance;
6. perform the final release/no-fix audit on one pinned candidate.

The Discord expansion is no longer specification-only: merged `main` now includes moderation-subject/scope contracts, V19 persistence foundations and central Discord-origin authorization policy. The account-linking runtime, interactive staff bot, Discord enforcement/reconciliation, AutoMod, role sync, public bot and final Discord migration/cutover remain separate product work. See [[Discord Moderation Platform]].

Use [[Implementation Status]] for the readable merged-main picture. Use the [requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md) plus current legitimate review/runtime evidence for requirement-level proof.

## What remains by product area

| Area | Main remaining themes | Start here |
| --- | --- | --- |
| Core/runtime | full configuration/reload, lifecycle recovery, distributed topology, provider/classloader and Folia evidence | [[Core Platform and Infrastructure]] |
| Moderation | remaining escalation/provider/report-notification details and representative staff/runtime acceptance | [[Moderation, Punishments, and Reports]] |
| Discord moderation | account-link runtime/migration, staff-bot runtime, scoped Discord effects/reconciliation, cross-platform UX, evidence capture, AutoMod, role sync, public bot and Discord cutover | [[Discord Moderation Platform]] |
| Player-state tools | inventory/offline/recovery safety, freeze coverage, vanish integrations, alts and representative Cheat Tester/runtime acceptance | [[Staff Tools, Investigations, and Player-State Safety]] |
| Integrations/release | provider implementations, private site/runtime acceptance, LiteBans shadow/cutover, Java/Bedrock/Folia/load/process-kill evidence | [[Integrations, Migration, and Release Readiness]] |

These rows are product categories, not work assignments.

## Where should I go next?

- **Understand a feature before changing it:** [[Developer Guide Index]] → feature hub → [[Developer Code Guide]].
- **Review a change:** [[Code Review Guide]].
- **Understand the current Discord boundary:** [[Discord Moderation Platform]].
- **Find what proof remains:** [[Build and Testing]] plus the requirements/evidence sources.
- **Plan release/cutover work:** [[Integrations, Migration, and Release Readiness]], [[LiteBans Migration]], and [[Shadow Mode and Cutover]].
- **Work under a separately assigned orchestration/package contract:** use that contract and live GitHub directly; do not infer its state from this Wiki.

## Durable dependency principles

Regardless of current development ordering:

- domain/persistence correctness precedes production authority;
- Discord identity/scope/authorization correctness precedes destructive bot execution;
- user-facing account linking requires durable ownership/history/replay semantics before migration/cutover;
- AutoMod shadow/quality evidence precedes automated enforcement/cutover;
- provider behavior must be implemented through the owning provider's supported contract rather than invented in EnthusiaStaff;
- private/runtime validation follows the exact code it is intended to validate;
- Java/Bedrock/provider acceptance must use the exact candidate being evaluated;
- destructive/load/process-kill acceptance comes before production cutover;
- LiteBans remains authoritative throughout shadow until an explicit accepted authority transition;
- existing native Discord moderation remains authoritative for Discord enforcement until the separate Discord migration/cutover is accepted;
- code/config changes after an acceptance run invalidate affected evidence.

## Current foundation versus finished Discord platform

Merged foundations now provide:

- moderation-subject and explicit Minecraft/Discord enforcement-scope domain concepts;
- durable V19 identity/link/main-account/enforcement/evidence-metadata/security-lock/reconciliation/maintenance schema and JDBC support;
- central Discord-origin authorization with rank/platform consequences, target protection, runtime limits, external preconditions and stale-flow reauthorization.

Those layers are prerequisites. They do **not** by themselves provide:

- a player-facing five-minute code linking flow;
- existing DiscordSRV link migration runtime;
- an interactive Discord staff bot;
- native Discord mute/ban/restriction execution and expiry/reconciliation;
- complete Discord evidence capture;
- AutoMod enforcement;
- role synchronization/public information bot;
- native-ban migration/cutover acceptance.

That distinction should remain visible in design, code review and Wiki status pages.

## Repository and component model

`wsg138/EnthusiaStaff:main` is the aggregate repository for the current platform source and component copies. External components may also retain standalone repositories. When a component exists in both places, release confidence requires deliberate revision/content reconciliation rather than assuming one side represents the other.

The planned staff Discord bot is a separate service/runtime boundary while sharing approved domain/persistence contracts. The public bot is a separate application/trust boundary with sanitized public data only.

General Wiki pages should explain durable product state rather than duplicate transient branch/worker history.

## Release gates

Broad release gates include:

- hosted clean build/test/static-analysis/runtime-artifact checks;
- private exact-candidate runtime checks;
- representative Velocity + multiple Paper backends + providers;
- Java and Bedrock/Geyser/Floodgate behavior;
- Folia-compatible scheduler/ownership behavior where supported;
- destructive workflow interruption/recovery and load/saturation;
- private representative LiteBans migration and shadow comparison;
- owner-authorized cutover and rollback acceptance;
- final release audit.

The Discord expansion adds acceptance gates for linking/migration parity, staff authorization, managed/native enforcement, temporary expiry/restart, Discord outage/rate-limit recovery, cross-platform partial failure, evidence/privacy, AutoMod shadow quality, public-bot isolation and final Discord cutover.

See [[Build and Testing]] for what each evidence layer proves. Passing an earlier layer does not imply a later layer passed.

## Authoritative references

- [Finished behavior](https://github.com/wsg138/EnthusiaStaff/blob/main/ENTHUSIASTAFF-GOALS.md)
- [Discord moderation platform specification](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/discord-moderation-platform.md)
- [Discord authorization design](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/discord-authorization.md)
- [[Implementation Status]]
- [Requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)
- [[Developer Guide Index]]
- [[Code Review Guide]]
- [[Build and Testing]]
# Remaining Development Map

Use this page to understand **what product work remains** without turning the Wiki into a package/worker dashboard.

## Quick answer

EnthusiaStaff now has substantial merged Minecraft, Discord, linking, website/API and staging-web implementation. The remaining work is increasingly about **finishing migrations/integrations and proving the complete system**, not building the original Discord runtime from scratch.

The broad release path remains:

1. finish correctness/safety gaps in merged product areas;
2. complete outstanding provider/DiscordSRV replacement work;
3. finish active Discord evidence/cross-platform/console/role-sync work;
4. finish the expanded website appeal lifecycle that is still unmerged;
5. run representative distributed Java/Bedrock/provider/Discord/web validation;
6. run destructive/load/process-recovery acceptance;
7. complete LiteBans and Discord-specific migration/cutover evidence;
8. perform final release/no-fix audit on one pinned candidate.

Use [[Implementation Status]] for the merged-main picture. Active PRs are development context, not current behavior.

## What remains by product area

| Area | Main remaining themes | Start here |
| --- | --- | --- |
| Core/runtime | complete configuration/reload/lifecycle acceptance, full topology/provider/classloader/Folia evidence | [[Core Platform and Infrastructure]] |
| Moderation | remaining provider/report/evidence details and representative staff/runtime acceptance | [[Moderation, Punishments, and Reports]] |
| Discord/StaffBot | console replacement, role-sync parity/provider migration, expanded evidence/cases/alerts, cross-platform moderation, final production authority/cutover | [[Discord Moderation Platform]] |
| Website/API | expanded appeal lifecycle currently in development plus production deployment/security/operations acceptance | [[Website and Web API]] |
| Player-state tools | inventory/offline/recovery safety, freeze coverage, vanish integrations, alts and representative Cheat Tester/runtime acceptance | [[Staff Tools, Investigations, and Player-State Safety]] |
| Integrations/release | provider implementations, LiteBans shadow/cutover, Java/Bedrock/Folia/load/process-kill and complete release evidence | [[Integrations, Migration, and Release Readiness]] |

These are product categories, not work assignments.

## Discord: merged versus remaining

### Merged now

Current `main` already includes:

- standalone Java 25/JDA StaffBot runtime;
- Discord moderation/read commands and signed interaction flow;
- linked-staff actor resolution and central authorization;
- Discord punishment execution/reconciliation for warn/mute/kick/ban/restriction scopes;
- Discord/Minecraft account-linking runtime with V20 persistence and `/link`/`/unlink` paths;
- DiscordSRV link import/mirroring transition support;
- StaffBot health/deployment/recovery support;
- provider-neutral `discord-platform-api` managed-role contracts;
- staging browser moderation workspace and private StaffBot read bridge.

Do not list those as future foundations anymore.

### Still separate/in development

Current active work includes, but is not limited to:

- authenticated Discord-to-Minecraft command bridge replacing DiscordSRV console;
- expanded Discord evidence/case/note/linked-alt/evasion alert workflows;
- broader cross-platform Discord/Minecraft moderation integration;
- managed-role provider/consumer migrations and DiscordSRV role-sync parity;
- final migration/cutover/production-authority acceptance.

See [[Discord Moderation Platform]].

## Website/web: merged versus remaining

Merged `main` includes:

- synchronized `components/enthusia-site/` public site component;
- Velocity public punishment/search/case API projections;
- punishment-code claim/revalidation;
- appeal eligibility/submission/reviewer list/decision/accept paths;
- V17 appeal workflow persistence;
- staging Cloudflare moderation web workspace;
- signed one-time launches, secure browser sessions and private StaffBot read API.

Active draft work expands the website appeal lifecycle further. Those draft endpoints/workflows are not current `main` and must stay labeled development-only until merged.

Production public-site/API acceptance and the staging moderation workspace's production-hardening/cutover questions are separate from source presence.

See [[Website and Web API]].

## Durable dependency principles

Regardless of current development ordering:

- domain/persistence correctness precedes production authority;
- StaffBot command visibility/Discord roles never replace authoritative linked-staff checks;
- account linking must preserve one-use/replay/ownership/history guarantees across restart/migration;
- external Discord effects need ambiguity-aware reconciliation rather than blind retries;
- managed-role consumers should use the provider-neutral contract rather than direct JDA;
- provider behavior must use supported provider contracts, not invented APIs or raw SQL;
- public/browser APIs expose only approved projections and never become implicit moderation writers;
- exact-candidate validation follows the code/config/artifacts being accepted;
- Java/Bedrock/provider/Discord/web acceptance must use the exact candidate;
- destructive/load/process-kill acceptance comes before production cutover;
- LiteBans remains authoritative until its accepted transition;
- Discord production authority remains separately gated even though enforcement code exists;
- changes after acceptance invalidate affected evidence.

## Repository/component model

`wsg138/EnthusiaStaff:main` is the aggregate platform repository. It contains the Java runtime modules plus synchronized/external-style components such as `components/enthusia-site/`.

Current deployment boundaries include:

- Paper plugin;
- transition authority bridge plugin;
- Velocity plugin;
- StaffBot standalone application;
- public site/Pages Functions;
- staging moderation-web Worker/static assets.

When a component also exists as a standalone repository, release confidence requires deliberate parity/revision reconciliation.

## Broad release gates

Release confidence still requires the applicable combination of:

- clean build/test/static/runtime-artifact checks;
- MariaDB migration/recovery tests;
- exact-candidate private runtime checks;
- representative Velocity + multiple Paper backends + providers;
- Java/Bedrock/Geyser/Floodgate behavior;
- Folia scheduler/ownership behavior where supported;
- StaffBot Discord identity/hierarchy/reconnect/rate-limit/reconciliation tests;
- website authentication/replay/privacy/session checks;
- destructive workflow interruption/recovery and load/saturation;
- representative LiteBans migration/shadow comparison;
- subsystem-specific DiscordSRV/Discord authority transition evidence;
- explicit owner-authorized cutover/rollback acceptance;
- final release audit.

See [[Build and Testing]] for what each evidence layer proves.

## Where to continue

- Current state: [[Implementation Status]]
- Developer routing: [[Developer Guide Index]]
- Source map: [[Developer Code Guide]]
- Review: [[Code Review Guide]]
- Discord: [[Discord Moderation Platform]]
- StaffBot ops: [[Staff Bot Runtime and Operations]]
- Website/API: [[Website and Web API]]
- Release/cutover: [[Integrations, Migration, and Release Readiness]]
- LiteBans: [[LiteBans Migration]] and [[Shadow Mode and Cutover]]

General Wiki pages should explain durable product state, not copy transient package-worker history.
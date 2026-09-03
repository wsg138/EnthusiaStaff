# Implementation Status

> **Overall verdict: not approved for production authority.** LiteBans and the currently approved production staff stack remain authoritative until the separate migration, shadow, acceptance, and cutover gates are completed.

This page answers a product question: **what is present on merged `main`, and what kind of proof exists?** It deliberately does not mirror package-worker state or assign exact completion percentages that become misleading after every merge.

## Status language

- **Available** — implemented and verified in the environment relevant to the claim.
- **Available with limitations** — usable for the stated scope, with important limitations listed.
- **Implemented, not staging-verified** — merged code and relevant automated evidence exist, but representative runtime staging has not established the full claim.
- **Partial** — meaningful foundations exist but the described workflow is incomplete.
- **Blocked** — a required dependency, provider, environment, or authority gate is unavailable.
- **Planned** — required by the goals/specification but not implemented.
- **Deprecated** — retained only for migration or compatibility.

A passing unit/integration suite does not automatically make a feature **Available**. See [[Build and Testing]] for the evidence ladder.

## Current merged-main picture

| Area | Current state | What is established on merged `main` | Important remaining proof/work |
| --- | --- | --- | --- |
| Runtime artifacts and module architecture | **Implemented, not staging-verified** | Java 21 multi-module build produces the intended Paper and Velocity runtime artifacts; clean domain/platform boundaries and runtime-JAR leak checks have automated coverage. | Real combined provider/classloader, distributed topology, supported server-version, and release-candidate staging. |
| MariaDB persistence and Flyway | **Implemented, not staging-verified** | Transactional JDBC stores, leases/revisions/outboxes/recovery foundations and migrations through **V17** are present; MariaDB/Testcontainers covers substantial persistence behavior. | Production-like volume/latency, process-kill, long outage, multi-server contention, and release-candidate upgrade rehearsal. |
| Paper–Velocity protocol | **Implemented, not staging-verified** | Persistent authenticated transport, replay protection, acknowledgements, durable inbox/outbox behavior and bounded retry foundations exist. | Multi-backend reconnect/outage/backpressure, real certificate/allowlist, and no-online-player runtime acceptance. |
| Configuration and reload | **Partial** | Validated reason-policy compatibility, report configuration/GUI snapshots, selected Paper settings and immutable publication paths exist. | Full modular configuration tree, complete cross-file atomic reload, restart-required reporting, and representative Paper/Velocity reload staging. |
| Player identity and Java/Bedrock persistence | **Implemented, not staging-verified** | UUID authority, verified Floodgate-based platform persistence, `UNKNOWN` fallback, alias/history resolution, and protection against unverified proxy observations downgrading verified platform state are merged. | Representative Java/Bedrock/Geyser/Floodgate reconnect, multi-backend, provider-failure and presentation staging. |
| Punishment creation, drafts and approval requests | **Implemented, not staging-verified** | Central punishment policy, durable drafts, request/approval boundaries, rank rules, MariaDB persistence and GUI/text foundations have automated evidence. | Representative multi-server/Bedrock staff use, remaining modular UI/config work, and final production authority acceptance. |
| Punishment history and exact sanction lifecycle | **Implemented, not staging-verified** | Bounded history/case views and exact sanction reduce/end/revoke/overturn paths with locked transaction checks, audit, idempotency and V14 persistence are merged. | Representative staff usability, website/provider end-to-end enforcement, and production authority acceptance. |
| Escalation policy | **Partial** | Stable reason IDs, aliases/removed metadata, recommendation snapshots, explicit decay eligibility, ordinals and core engine behavior exist. | Broader family/combined-sanction rules, modular policy configuration and representative runtime acceptance. |
| Reports and retained evidence | **Available with limitations** | `/report`, report queues/detail/action GUI, text/Bedrock fallbacks, revision fencing, bounded evidence retention/cleanup and configurable report policy are implemented. | Supported RoseChat private-message bridge, remaining notification/Discord presentation, and distributed provider/runtime staging. |
| Website punishment/appeal workflow | **Implemented, not staging-verified** | Current aggregate source includes the scoped private-site/website appeal workflow, V17 persistence, authenticated bridge foundations, exact-sanction appeal isolation, and synchronized website component work. | Private deployment/security/runtime acceptance, real authentication/provider integration, operational monitoring, and public/production launch approval. Formal appeals are planned to remain website-only as Discord moderation expands. |
| Staff mode and operational hotbar tools | **Implemented, not staging-verified** | Durable staff-session recovery plus the operational hotbar dispatcher, random teleport, inspect/freeze/reports/follow-spectate/vanish/staff-chat/menu routes, stale-tool rejection and Bedrock text fallbacks are merged. | Representative Java/Bedrock/Folia/distributed staging and unfinished advanced tools such as cheat testers/fake systems. |
| Vanish | **Available with limitations** | Durable intent, rank-aware visibility service, incremental audience reconciliation, session fencing and current ProtocolLib player-info handling exist. | Complete cross-plugin visibility coverage, visual/packet compatibility, Java/Bedrock/Folia/multi-backend staging, and provider integration. |
| Freeze | **Partial** | Durable freeze state and important restriction/recovery foundations exist. | Exhaustive movement/inventory/teleport/backend/chat bypass coverage and representative restart/client/Folia staging. |
| Inventory/Ender editing and confiscation | **Partial** | Revision/journal/lease/confiscation/restoration foundations and substantial automated persistence/domain coverage exist. | Complete concurrent-viewer, nested-container, offline ownership/save races, login patches, crash recovery, quarantine and multi-server runtime proof. |
| Economy/market/reputation moderation | **Partial / blocked by providers where applicable** | EnthusiaStaff-side contracts, journals and adapter boundaries exist for some operations. | Supported provider APIs/implementations, idempotent end-to-end behavior and cross-plugin staging. Raw provider SQL is not an acceptable substitute. |
| Alt/network identity workflows | **Partial** | Protected network-identity and relationship foundations exist. | Confidence lifecycle, exclusions/households, inheritance, alerts/UI, key rotation and production-like private-data validation. |
| Discord webhook delivery | **Partial** | Durable outbox/Velocity delivery worker and bounded retry foundations exist. | Complete event routing/privacy review, outage/dead-letter/operator behavior and live integration acceptance. |
| Discord moderation/linking/AutoMod staff bot | **Planned** | Product/architecture specification and phased worker plan define a separate Java 21 staff bot, linked identity, scoped Discord sanctions, native-ban reconciliation, managed mutes/restrictions, evidence, AutoMod and cross-platform workflows. | No runtime/schema/commands/enforcement have been implemented yet; identity/scope/persistence/authorization must be built first. See [[Discord Moderation Platform]]. |
| Public Discord information bot | **Planned** | Sanitized public command scope and trust boundary are specified. | No public bot/runtime/API implementation yet; it must remain isolated from privileged moderation data/credentials. See [[Discord Moderation Platform]]. |
| LiteBans migration/shadow/cutover | **Partial; production acceptance blocked** | Schema inspection/import, mappings, comparison dimensions and cutover/recovery foundations exist. | Private representative data, exact 168-hour accepted shadow evidence, final reconciliation, owner acceptance and single-authority production cutover. |
| Full release acceptance | **Blocked / not yet completed** | Hosted build/test/static-analysis checkpoints and limited private Paper boot evidence exist for historical exact SHAs. | One pinned release candidate still needs coherent Velocity, multi-backend, providers, Java/Bedrock, Folia, load, process-kill, destructive recovery, migration/shadow and production acceptance evidence. |

## Important merged facts readers commonly miss

### Flyway is through V17

Current `main` contains `V17__website_appeal_workflow.sql`. V1-V17 are forward-only history and must not be edited in place. Add a new migration for future schema changes.

### Java/Bedrock identity is provider-evidence based

A `*` username shape is not proof of Bedrock. Supported Floodgate evidence establishes platform; unavailable/incompatible evidence remains `UNKNOWN`. Unverified Velocity presence may update identity/presence metadata but must not downgrade a verified platform record. See [[Integrations]].

### Staff operational tools are merged but not production-accepted

The staff-mode hotbar dispatcher and its text/Bedrock fallbacks are merged. That establishes repository behavior and automated evidence, not representative Java/Bedrock/Folia/distributed acceptance. See [[Staff Mode, Vanish, and Freeze|Staff-Mode-Vanish-and-Freeze]].

### Website source exists; deployment acceptance is separate

The scoped website/appeal implementation and V17 support are present in the aggregate repository. The restricted bridge and site still require the relevant private deployment, security, provider and production acceptance before they should be treated as a live public service.

### Discord moderation expansion is specification-only

The interactive staff bot, new linking authority, Discord punishment enforcement, AutoMod replacement, role-sync replacement, ban migration and public bot are planned in [[Discord Moderation Platform]]. Existing webhook delivery does not mean those features already exist.

## How to inspect one feature deeply

1. Open the matching feature hub or focused page for purpose, current limitations, important source paths, and focused staff/operator pages.
2. Open [[Developer Code Guide]] for the end-to-end source trace of implemented areas.
3. Open [[Code Review Guide]] to see the invariants and failure modes a change must preserve.
4. Use the [requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md) for conservative requirement-level evidence, but reconcile it with current merged code and live GitHub when a recent merge has not yet been reflected there.
5. Use exact PR/workflow evidence only for the SHA it actually tested.

## Feature hubs

- [[Core Platform and Infrastructure]]
- [[Moderation, Punishments, and Reports]]
- [[Staff Tools, Investigations, and Player-State Safety]]
- [[Integrations, Migration, and Release Readiness]]
- [[Discord Moderation Platform]]

## Release boundary

No source merge, automated test, Wiki update, or successful standalone Paper boot by itself authorizes:

- production moderation authority;
- disabling or removing LiteBans;
- replacing Discord moderation/AutoMod before its own migration/shadow/cutover acceptance;
- skipping the shadow/acceptance gates;
- destructive provider testing on live data;
- publishing private evidence or credentials;
- claiming Java/Bedrock/Folia/provider compatibility beyond the environment actually exercised.
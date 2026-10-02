# Implementation Status

> **Overall release boundary:** merged implementation is not the same thing as production authority. LiteBans and the currently approved production staff/Discord stack remain authoritative where their replacement has not completed its specific migration, acceptance, and cutover gates.

This page answers: **what is present on merged `main`, and what kind of proof exists?** It deliberately avoids package-worker percentages and does not describe draft PR behavior as available.

## Status language

- **Available** — implemented and verified in the environment relevant to the claim.
- **Available with limitations** — usable for the stated scope, with important limitations listed.
- **Implemented, not staging-verified** — merged code and automated evidence exist, but representative runtime staging has not established the full claim.
- **Implemented, not production-accepted** — merged/runtime evidence exists for some scope, but production authority/cutover has not been accepted.
- **Partial** — meaningful foundations exist but the described workflow is incomplete.
- **In development** — active unmerged work exists; it is not current `main` behavior.
- **Blocked** — a required dependency, environment, provider, or authority gate is unavailable.
- **Planned** — required/approved but not implemented.

See [[Build and Testing]] for the evidence ladder.

## Current merged-main picture

| Area | Current state | What is established on merged `main` | Important remaining proof/work |
| --- | --- | --- | --- |
| Runtime/module architecture | **Implemented, not fully production-accepted** | Java 25 multi-module system includes Paper, Velocity, standalone StaffBot, `discord-platform-api`, `paper-authority-bridge`, persistence/protocol/domain modules and integration tests. | One coherent release-candidate topology with the intended providers, private services, Java/Bedrock/Folia coverage and production acceptance. |
| MariaDB persistence and Flyway | **Implemented, not fully production-accepted** | Transactional stores, leases/revisions/outboxes/recovery foundations and Flyway history through **V20** are merged. V19 adds Discord moderation persistence; V20 adds account linking. | Production-like volume/latency/process-kill/multi-runtime contention and exact upgrade rehearsal for final candidates. |
| Paper–Velocity protocol | **Implemented, not fully staging-verified** | Authenticated persistent transport, replay protection, ACKs, durable inbox/outbox, retry/backpressure foundations. | Representative multi-backend outage/reconnect/no-player/security acceptance. |
| Configuration and reload | **Partial** | Validated policy/config snapshots and safe publication paths exist across Paper/Velocity/StaffBot scopes. | Complete modular tree/cross-file immutable reload and representative reload/restart evidence. |
| Player identity and Java/Bedrock persistence | **Implemented, not fully staging-verified** | UUID authority, verified Floodgate platform evidence, `UNKNOWN` fallback, alias/history handling and downgrade protection. | Representative Java/Bedrock/Geyser/Floodgate/provider-failure/multi-backend acceptance. |
| Punishment creation/history/sanction lifecycle | **Implemented, not production-accepted** | Central punishment policy, durable drafts/requests, exact sanction reduce/end/revoke/overturn, history/cases/audit/idempotency are merged. | Final production authority/cutover and representative multi-surface enforcement validation. |
| Reports and retained evidence | **Available with limitations** | Submission, queues/detail/action UI, bounded retained evidence, revision fencing and policy are implemented. | Ongoing provider/Discord presentation/evidence expansion and full distributed acceptance. |
| Public site + Velocity website API | **Implemented, not production-accepted as a complete public service** | Aggregate `enthusia-site` component, public punishment/search/case projections, punishment-code and appeal/reviewer workflow endpoints, V17 persistence and exact-sanction appeal authority are merged. | Production deployment/security/operations/provider acceptance; draft expanded appeal lifecycle is not yet merged. See [[Website and Web API]]. |
| Staging moderation web workspace | **Available for accepted staging read/simulation scope** | Cloudflare Worker/static-assets workspace, one-time signed launches, secure browser sessions, signed/replay-protected StaffBot reads and read-only moderation UX are merged and have protected staging evidence. | It intentionally has no destructive/production moderation authority; production design would require separate acceptance and hardened dedicated signing secrets. |
| Staff mode and operational tools | **Implemented, not fully staging-verified** | Durable staff sessions, operational dispatcher/tools, aliases/recovery, Staff Mode mutation authority and fallbacks are merged. | Representative Java/Bedrock/Folia/distributed acceptance and remaining advanced-tool work. |
| Vanish | **Available with limitations** | Durable intent, rank-aware visibility, reconciliation/session fencing and current packet/tab support are merged. | Full client/provider/multi-backend coverage. |
| Freeze | **Partial** | Durable state and important restriction/recovery foundations exist. | Exhaustive bypass/restart/client/Folia/provider coverage. |
| Inventory/Ender editing and confiscation | **Partial** | Journals/leases/revision/confiscation/restoration foundations and automated persistence/domain coverage exist. | Concurrent-viewer/nested-container/offline-save/login-patch/crash/quarantine runtime proof. |
| Economy/market/reputation moderation | **Partial / provider-dependent** | EnthusiaStaff-side contracts/journals/adapters exist for supported portions. | Provider-side APIs/implementations and cross-plugin acceptance where still incomplete. |
| Alt/network identity workflows | **Partial** | Protected network identity and relationship foundations exist. | Full confidence/exclusion/inheritance/alert/private-data acceptance. |
| Legacy Discord webhook delivery | **Available with limitations** | Durable outbox/Velocity worker, bounded renderer/retries and privacy projection exist. | Live route/outage/dead-letter/operator acceptance; this is separate from StaffBot. |
| StaffBot Discord runtime | **Implemented** | Standalone Java/JDA runtime, gateway ownership, identity fences, worker pool, health/readiness, moderation/read UI, signed components, linked-staff resolution, private read bridge and deployment/runbook are merged. | Production authority remains separately gated; destructive enforcement defaults off. See [[Staff Bot Runtime and Operations]]. |
| Discord punishment enforcement | **Implemented, not production-accepted** | Durable warn/mute/kick/ban/restriction execution, end/revoke/overturn, confirmations, reauthorization, DMs/outcomes, retry/reconciliation/expiry and native-effect ownership logic are merged. | Exact production cutover/authority acceptance; do not equate implementation with live production enforcement. |
| Discord/Minecraft account linking | **Implemented** | Bidirectional one-use short-lived codes, hashed-at-rest codes, ownership/history/main-account state, unlink/reassignment, playtime-based main selection and DiscordSRV import/mirroring compatibility are merged under V20. | Final retirement of every DiscordSRV-dependent role/console/chat path is separate work. |
| Provider-neutral Discord managed-role API | **Implemented contract foundation** | `discord-platform-api` defines provider-neutral managed-role claims/namespaces/keys/results without leaking JDA/provider internals to consumers. | StaffBot provider and all consumer migrations/role-sync parity are separate workstreams. |
| Discord evidence/cases/alerts expansion | **In development** | Active work exists beyond the current merged read/punishment surfaces. | Not current `main`; document after merge and validation. |
| DiscordSRV console replacement | **In development** | Authenticated command-bridge work exists on a separate draft workstream. | Not merged; current Wiki must not claim console retirement is complete. |
| Cross-platform Discord/Minecraft moderation expansion | **In development** | Existing merged scope/authorization foundations support expansion. | Active work remains unmerged; platform scope must not be widened by assumption. |
| Discord role-sync replacement | **In development / incomplete** | Managed-role contract foundation is merged. | Provider implementation/consumer migrations/parity and final DiscordSRV retirement are not complete. |
| LiteBans migration/shadow/cutover | **Partial; production acceptance blocked** | Schema import/comparison/cutover/recovery foundations exist. | Representative private data, required accepted shadow evidence, final reconciliation, owner acceptance and single-authority cutover. |
| Full release acceptance | **Blocked / incomplete** | Strong hosted automated checkpoints and scoped staging evidence exist for many subsystems. | One pinned complete candidate still needs coherent distributed/provider/client/load/recovery/migration/cutover acceptance for the exact intended production topology. |

## Important merged facts

### Flyway is through V20

Current `main` contains migrations through:

```text
V20__discord_account_linking.sql
```

V19 owns Discord moderation persistence; V20 owns account linking. Later migration numbers visible only on open branches are not current schema and must not be documented as merged.

### StaffBot is a standalone runtime

Current merged code no longer consists only of Paper and Velocity. `staff-bot` builds a standalone Java/JDA executable with its own lifecycle, health, private service boundaries and release/update procedure; the separate AuthorityBridge artifact remains a narrow transition runtime. See [[Staff Bot Runtime and Operations]] and [[Architecture]].

### Discord enforcement exists, but safe defaults/cutover still matter

Discord punishment services and native/role effects are merged. That does not mean production Discord authority has moved. The runtime keeps destructive enforcement explicitly gated and defaults it off unless the accepted environment enables it.

### Account linking exists

The Wiki should no longer describe Discord/Minecraft linking as schema-only or future work. The merged runtime includes link-code, unlink/reassignment, ownership/history and main-account behavior under V20. DiscordSRV migration compatibility does not mean every DiscordSRV function has been retired.

### There are multiple web surfaces

The public site, Velocity website API, staging moderation web workspace, and StaffBot private read API are distinct trust boundaries. See [[Website and Web API]].

### Staging moderation web is intentionally read-only/simulation-oriented

Its successful staging evidence supports the recorded browser/read workflows. It does not grant production punishment authority or make Cloudflare/the browser a trusted moderation writer.

### Java/Bedrock identity remains provider-evidence based

A `*` username is not platform proof. UUID plus supported Floodgate evidence remains authoritative; unavailable/incompatible evidence remains `UNKNOWN`.

## How to inspect one feature deeply

1. Open the matching feature hub/focused page for purpose, limitations and important source paths.
2. Open [[Developer Code Guide]] for end-to-end source traces.
3. Open [[Code Review Guide]] for invariants/failure modes.
4. Use current code/migrations/tests and legitimate exact-SHA workflow/runtime evidence for proof.
5. Treat requirements/package records as evidence/history, not as a substitute for reconciling current merged `main`.

## Feature/deep-dive entry points

- [[Core Platform and Infrastructure]]
- [[Moderation, Punishments, and Reports]]
- [[Staff Tools, Investigations, and Player-State Safety]]
- [[Integrations, Migration, and Release Readiness]]
- [[Discord Moderation Platform]]
- [[Staff Bot Runtime and Operations]]
- [[Website and Web API]]

## Release boundary

No source merge, automated test, Wiki update, scoped staging success, or standalone runtime boot by itself authorizes:

- production moderation authority;
- disabling/removing LiteBans before its accepted cutover;
- enabling destructive Discord enforcement in production before Discord-specific acceptance;
- treating an in-progress console/role-sync/cross-platform feature as merged;
- exposing private evidence/credentials/network details;
- claiming Java/Bedrock/Folia/provider compatibility beyond the environment actually exercised.
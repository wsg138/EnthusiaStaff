# Remaining Development Map

This page answers one question: **which feature group should development work on next?**

Detailed feature percentages, descriptions, source files and remaining tasks live on the feature-group pages. This page keeps the cross-group order, current branch state and release dependencies.

- Completion overview: [[Feature Completion Status|Implementation-Status]]
- Exact evidence: [requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)
- Code ownership: [[Developer Code Guide]]
- Validation: [[Build and Testing]]
- Migration operations: [[LiteBans Migration]] and [[Shadow Mode and Cutover]]
- Production cutover acceptance: [cutover acceptance](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/cutover-acceptance.md)

## Repository checkpoint

- PR #27 and PR #37 are merged implementation checkpoints.
- PR #46 is the current punishment-history and exact sanction-lifecycle pull request.
- V11, V12 and V13 retain their checksum-locked bytes; V14 adds history/linkage indexes and exact mutation metadata.
- LiteBans remains authoritative; this feature does not deploy a JAR, activate EnthusiaStaff authority or begin issue #43 acceptance.
- Issue #43 remains the separate production-cutover acceptance gate.

## Four development groups

| Group | Completion | Why it matters now | Detailed work |
| --- | ---: | --- | --- |
| [[Core Platform and Infrastructure]] | **About 72%** | Modes, configuration, lifecycle, identity and failure handling are shared prerequisites for many other features. | Open the group page for files, descriptions and remaining categories. |
| [[Moderation, Punishments, and Reports]] | **About 63%** | History, sanction changes, request notifications, reports and evidence form the main moderation workflow. | Open the group page for files, commands and remaining categories. |
| [[Staff Tools, Investigations, and Player-State Safety]] | **About 44%** | Staff mode, vanish, freeze, inventories and confiscation can lose or duplicate player state if recovery is incomplete. | Open the group page for safety boundaries, files and remaining categories. |
| [[Integrations, Migration, and Release Readiness]] | **About 36%** | Providers, the site, migration, topology and acceptance evidence determine whether the platform can replace LiteBans safely. | Open the group page for providers, migration and release gates. |

## Current development order

### 1. Finish and merge PR #46 as a normal feature

Before merge:

- review the complete history, mutation, command, configuration and V14 diff;
- pass exact-head Java 21 build, unit and MariaDB/Testcontainers tests, migration checksum checks, runtime-JAR inspection, static analysis, documentation validation and configured coverage upload;
- resolve all valid automated and human review findings;
- keep history database-paginated, mutation audit append-only and operational-mode fencing unchanged;
- use a normal merge commit and do not deploy the merged JAR.

### 2. Continue normal feature development

After PR #46 is merged, choose one coherent unfinished moderation or staff feature.
Do not start it from this PR. Prioritize correctness and recovery gaps before
broader feature expansion.

### 3. Complete issue #43 when the plugin is closer to release

Pin one exact release-candidate SHA, Paper and Velocity JAR hashes, sanitized configuration revision and isolated staging environment. Any runtime-source, JAR, migration, schema, comparison or relevant configuration change invalidates the record.

The production cutover record must cover:

- representative sanitized-backup migration, rerun and checksum comparison;
- interrupted migration and restart recovery;
- an uninterrupted 168-hour shadow window with seven complete daily summaries;
- maintenance fencing and final incremental migration;
- ambiguous activation retry and duplicate safety;
- emergency freeze and restart persistence;
- rollback and idempotent reconciliation;
- Velocity/HUB/SMP, Java and Bedrock/Geyser acceptance;
- provider-present/provider-missing behavior;
- database, queue, dead-letter, process-kill, saturation and latency scenarios.

Only after that record is complete may the release candidate be deployed for production cutover, EnthusiaStaff authority be activated, LiteBans be disabled or removed, the final production migration be performed, or a live cutover be authorized.

### 4. Finish shared foundation gaps

Prioritize the core gaps that block several feature groups:

- complete dependency-specific feature gates and actionable runtime verification;
- finish modular configuration and atomic reload;
- prove startup, shutdown, long-outage and process-kill recovery;
- finish historical-name, Bedrock alias and bounded completion behavior.

See [[Core Platform and Infrastructure]].

### 5. Complete the remaining feature groups

Continue moderation history and appeals, report UX, player-state safety, provider integrations, staff tools, the private site and the final release manifest in coherent, independently reviewable changes.

## How to choose the next task

Choose work in this order:

1. correctness or security defects that can corrupt state;
2. recovery and idempotency gaps in destructive workflows;
3. core moderation workflows already close to usable;
4. shared foundation work that blocks multiple features;
5. one coherent feature category from a group page;
6. documentation and evidence updates for the exact tested revision.

Do not choose work merely because it is easy to demonstrate. A section is done only when its behavior, authority, persistence, failure handling, recovery, tests, staging and documentation agree.

## Documentation ownership

| Information | Primary page |
| --- | --- |
| Feature percentages and group directory | [[Feature Completion Status|Implementation-Status]] |
| Core feature descriptions and files | [[Core Platform and Infrastructure]] |
| Moderation feature descriptions and files | [[Moderation, Punishments, and Reports]] |
| Staff-tool feature descriptions and files | [[Staff Tools, Investigations, and Player-State Safety]] |
| Integration/release descriptions and files | [[Integrations, Migration, and Release Readiness]] |
| Exact evidence and blockers | `reports/REQUIREMENTS-MATRIX.md` |
| Complete code map and feature traces | [[Developer Code Guide]] |
| Validation procedure | [[Build and Testing]] |
| Migration operator procedure | [[LiteBans Migration]] and [[Shadow Mode and Cutover]] |
| Production cutover acceptance | `docs/cutover-acceptance.md` and issue #43 |

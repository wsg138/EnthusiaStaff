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

- PR #27 is merged and remains the previous major implementation checkpoint.
- PR #37 is the current cutover-coordination implementation pull request.
- PR #37 contains dormant infrastructure for authority fencing, migration coordination, transactional activation, restart recovery, duplicate safety, emergency freeze persistence, and protected authoritative writers.
- V11, V12 and V13 must retain the deployed checksum-locked bytes without scanner-only comments; analysis suppression belongs in `.codacy.yml`.
- Automated shadow scheduling is disabled by default and requires explicit configuration.
- LiteBans remains authoritative outside an explicitly validated and authorized `ACTIVE` transition.
- Issue #43 is the remaining **production cutover acceptance** gate, not an implementation merge blocker.
- Merging PR #37 does not deploy a JAR, start a production shadow window, disable LiteBans, or authorize a live cutover.

## Four development groups

| Group | Completion | Why it matters now | Detailed work |
| --- | ---: | --- | --- |
| [[Core Platform and Infrastructure]] | **About 72%** | Modes, configuration, lifecycle, identity and failure handling are shared prerequisites for many other features. | Open the group page for files, descriptions and remaining categories. |
| [[Moderation, Punishments, and Reports]] | **About 56%** | History, sanction changes, request notifications, reports and evidence form the main moderation workflow. | Open the group page for files, commands and remaining categories. |
| [[Staff Tools, Investigations, and Player-State Safety]] | **About 44%** | Staff mode, vanish, freeze, inventories and confiscation can lose or duplicate player state if recovery is incomplete. | Open the group page for safety boundaries, files and remaining categories. |
| [[Integrations, Migration, and Release Readiness]] | **About 36%** | Providers, the site, migration, topology and acceptance evidence determine whether the platform can replace LiteBans safely. | Open the group page for providers, migration and release gates. |

## Current development order

### 1. Finish and merge PR #37 as dormant infrastructure

Before merge:

- complete code review and resolve material review findings;
- pass exact-head Java 21 build, unit and MariaDB/Testcontainers tests, migration checksum checks, runtime-JAR inspection, provider API leak checks, static analysis, and configured coverage upload;
- keep V11, V12 and V13 byte-compatible with their locked deployed checksums;
- verify startup remains non-`ACTIVE`, missing or invalid activation evidence fails closed, and automatic shadow scheduling is disabled unless explicitly configured;
- use a normal merge commit and do not deploy the merged JAR.

Issue #43 does not block this dormant implementation merge.

### 2. Continue normal feature development

After PR #37 is merged, choose the next actual moderation or staff feature from the unfinished feature groups. Do not hold unrelated development work behind the later production cutover rehearsal.

Prioritize work in this order:

1. correctness or security defects that can corrupt state;
2. recovery and idempotency gaps in destructive workflows;
3. core moderation workflows that are already partially implemented;
4. shared foundation work that blocks multiple user-facing features;
5. one coherent feature category from a group page;
6. documentation and evidence updates for the exact tested revision.

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

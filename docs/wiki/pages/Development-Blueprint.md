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

- PR #51 was merged by normal merge commit `4f7165adced48d98bce86730e89b92944afba063` after exact-head validation and zero unresolved review threads.
- PR #52 is the current reason-policy compatibility work; verify its live head, validation, review and merge state before acting.
- PR #49 remains the latest merged report-system product checkpoint; PR #50 remains the RoseChat blocker coordination checkpoint.
- V14 remains the live highest Flyway migration; V1–V14 are immutable.
- LiteBans remains authoritative; no merged development work deploys a JAR or activates EnthusiaStaff authority.
- Issue #43 remains the separate production-cutover acceptance gate.

## Four development groups

| Group | Why it matters now | Detailed work |
| --- | --- | --- |
| [[Core Platform and Infrastructure]] | Modes, configuration, lifecycle, identity and failure handling are shared prerequisites for many other features. | Open the group page for files, descriptions and remaining categories. |
| [[Moderation, Punishments, and Reports]] | History, sanction changes, escalation, request notifications, reports and evidence form the main moderation workflow. | Open the group page for files, commands and remaining categories. |
| [[Staff Tools, Investigations, and Player-State Safety]] | Staff mode, vanish, freeze, inventories and confiscation can lose or duplicate player state if recovery is incomplete. | Open the group page for safety boundaries, files and remaining categories. |
| [[Integrations, Migration, and Release Readiness]] | Providers, the site, migration, topology and acceptance evidence determine whether the platform can replace LiteBans safely. | Open the group page for providers, migration and release gates. |

## Current development order

### 1. Verify PR #52 closure

Before beginning implementation:

- verify PR #52's exact live head, base and ahead/behind relation;
- read its exact-head build, test, migration, coverage, analyzer, runtime-JAR and review evidence from GitHub;
- confirm the normal merge result, resulting `main`, feature-head containment and branch cleanup;
- reconcile any newer pull request, branch, migration or requirement change.

PR #52 adds explicit aliases for renamed reason IDs, readable presentation-only metadata for removed IDs, atomic reload/rollback of the complete compatibility model, and selection boundaries that keep removed IDs inactive. Do not repeat it in a competing PR.

### 2. Resume RoseChat only when the provider contract exists

The supported private-message callback and privacy presentation boundary remains the preferred report-system item, but it is externally blocked.

Implementation may begin only after an accessible supported provider repository or artifact defines:

- the callback/event type and supported version;
- before-cancel, accepted-delivery and failed-delivery timing;
- sender and recipient identity semantics;
- cancellation, filtering, ignore, spy and duplicate behavior;
- threading and Paper/Folia scheduling guarantees;
- privacy-safe evidence fields and retention boundaries.

Do not use reflection against unknown implementation classes, copy provider-owned API classes, scrape logs as a fake callback, or store messages before delivery semantics are known.

### 3. Select an implementable item when the blocker remains

If the RoseChat contract is still unavailable after live reconciliation, choose one coherent prerequisite-complete feature using this order:

1. correctness or security defects that can corrupt state;
2. recovery and idempotency gaps in destructive workflows;
3. core moderation workflows already close to usable;
4. shared foundation work that blocks multiple features;
5. one coherent category from a detailed feature-group page;
6. documentation and evidence for the exact tested revision.

After PR #52, the likely next prerequisite-ready escalation slice is explicit policy-snapshot behavior across ladder edits. That work must preserve historical interpretation and stored ordinals, remain separate from serious-offense decay metadata and broader modular configuration, and be selected only after fresh live reconciliation.

Do not treat the blocked RoseChat feature or the broader escalation requirement as complete, and do not silently combine them in one PR.

### 4. Complete issue #43 when the plugin is closer to release

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

## Documentation ownership

| Information | Primary page |
| --- | --- |
| Feature percentages and group directory | [[Feature Completion Status|Implementation-Status]] |
| Core feature descriptions and files | [[Core Platform and Infrastructure]] |
| Moderation feature descriptions and files | [[Moderation, Punishments, and Reports]] |
| Staff-tool feature descriptions and files | [[Staff Tools, Investigations, and Player-State Safety]] |
| Integration/release descriptions and files | [[Integrations, Migration, and Release Readiness]] |
| Exact evidence and blockers | `reports/REQUIREMENTS-MATRIX.md` and the current agent handoff |
| Complete code map and feature traces | [[Developer Code Guide]] |
| Validation procedure | [[Build and Testing]] |
| Migration operator procedure | [[LiteBans Migration]] and [[Shadow Mode and Cutover]] |
| Production cutover acceptance | `docs/cutover-acceptance.md` and issue #43 |

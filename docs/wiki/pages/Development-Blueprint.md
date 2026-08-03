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

- PR #53 merged by normal merge commit `fc1e94bd7317d59a33d297a049a94fd2eb3f1c5e` from exact feature head `d766dfcd849c25df37df47962a0aab9bc6975304` after successful exact-head Coverage and Validate Wiki workflows and zero unresolved review threads.
- PR #54 is the serious-offense decay metadata work requiring live validation and merge verification.
- PR #49 remains the latest merged report-system product checkpoint; PR #50 records historical RoseChat blocker coordination.
- PR #54 adds V16; V1–V15 remain immutable.
- LiteBans remains authoritative; no merged development work deploys a JAR or activates EnthusiaStaff authority.
- Issue #43 is specifically the LiteBans production-cutover acceptance gate and remains open; it is not the general blocker queue.

## Four development groups

| Group | Why it matters now | Detailed work |
| --- | --- | --- |
| [[Core Platform and Infrastructure]] | Modes, configuration, lifecycle, identity and failure handling are shared prerequisites for many other features. | Open the group page for files, descriptions and remaining categories. |
| [[Moderation, Punishments, and Reports]] | History, sanction changes, escalation, request notifications, reports and evidence form the main moderation workflow. | Open the group page for files, commands and remaining categories. |
| [[Staff Tools, Investigations, and Player-State Safety]] | Staff mode, vanish, freeze, inventories and confiscation can lose or duplicate player state if recovery is incomplete. | Open the group page for safety boundaries, files and remaining categories. |
| [[Integrations, Migration, and Release Readiness]] | Providers, the site, migration, topology and acceptance evidence determine whether the platform can replace LiteBans safely. | Open the group page for providers, migration and release gates. |

## Current development order

### 1. Verify PR #54 closure

Before beginning implementation:

- verify PR #54's exact live head, base and ahead/behind relation;
- read its exact-head Coverage, Wiki, Pi, Codacy, build, test, migration, artifact, runtime-JAR and review evidence from GitHub;
- confirm the normal merge result, resulting `main`, feature-head containment and automatic branch cleanup;
- reconcile any newer pull request, branch, migration or requirement change.

PR #54 stores each new punishment step's configured decay eligibility as immutable history. The shared clean-period clock still resets from the latest contributing related offense, but each interval reduces only prior contributions explicitly stored as decay-eligible. Explicitly non-decaying serious history remains effective under later minor policies, eligible minor history can still decay under a later non-decaying policy, and pre-V16 rows remain `UNKNOWN` without invented backfill. Do not repeat it in a competing PR.

### 2. Prioritize staff mode, vanish, and freeze

After PR #54 is verified, the current owner priority is staff/player-visible safety work:

1. staff mode;
2. vanish;
3. freeze.

Select exactly one bounded prerequisite-ready item after live reconciliation. Prefer correctness, recovery, idempotency, state-restoration, restriction-enforcement, visibility, lifecycle or multi-runtime defects that make the selected feature unsafe or unusable. Do not combine all three systems in one oversized PR unless one coherent defect genuinely crosses their shared boundary.

### 3. Complete report notifications

Report notification completion is the second owner priority. Resume the supported RoseChat private-message callback and privacy presentation boundary only after an accessible provider contract defines:

- the callback/event type and supported version;
- before-cancel, accepted-delivery and failed-delivery timing;
- sender and recipient identity semantics;
- cancellation, filtering, ignore, spy and duplicate behavior;
- threading and Paper/Folia scheduling guarantees;
- privacy-safe evidence fields and retention boundaries.

Do not use reflection against unknown implementation classes, copy provider-owned API classes, scrape logs as a fake callback, or store messages before delivery semantics are known. Track unavailable provider APIs in focused blocker issues and the normal handoff. Do not open a standalone documentation PR solely to record a blocker unless routing would otherwise become materially incorrect or unsafe.

### 4. Continue escalation policy only after higher priorities

Escalation-policy completion is the third owner priority. The remaining work includes wider combined-recommendation coverage, explicit family-relationship expansion, broader modular punishment/escalation configuration, decayed-history GUI presentation and representative multi-runtime staff acceptance.

Do not begin another escalation-policy or internal infrastructure PR immediately after PR #54 unless it fixes a confirmed correctness, security, concurrency, migration or data-integrity defect; directly unblocks a higher-priority feature; or the owner explicitly approves it.

### 5. Complete issue #43 when the plugin is closer to release

Issue #43 is specifically the LiteBans production-cutover acceptance issue. It is not the general defect or blocker queue.

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

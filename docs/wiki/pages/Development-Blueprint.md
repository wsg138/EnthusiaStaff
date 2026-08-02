# Remaining Development Map

This page answers one question: **which feature group should development work on
next?**

Detailed feature percentages, descriptions, source files and remaining tasks live
on the feature-group pages. This page keeps the cross-group order, current branch
state and release dependencies.

- Completion overview: [[Feature Completion Status|Implementation-Status]]
- Exact evidence: [requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)
- Code ownership: [[Developer Code Guide]]
- Validation: [[Build and Testing]]
- Migration operations: [[LiteBans Migration]] and [[Shadow Mode and Cutover]]
- Acceptance gate: [cutover acceptance](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/cutover-acceptance.md)

## Repository checkpoint

- PR #27 is merged at `main` commit
  `14666c5b065571c227373ec9e13e82e978b689ca`.
- PR #27's final reviewed source head was
  `28dcd90f96b7a0c772acc378f73b18d9af62fe0b`.
- PR #37 is the only current implementation pull request.
- PR #37 remains an open draft on `section/plugin` at
  `aa2d737a5f33f0337010932723f46ce1e356c867`.
- PR #37 is 76 commits ahead and 0 behind the recorded `main` checkpoint.
- Exact-head Coverage run `30734750010` passed with runtime packaging, migration
  checksum tests, aggregate coverage and Codacy upload.
- Pi source run `30731471656` and staging run `30731479127` passed on
  runtime-equivalent source; later changes are tests, docs, analysis configuration
  and temporary workflow cleanup, not production runtime source or migration bytes.
- V11, V12 and V13 must retain the deployed checksum-locked bytes without
  scanner-only comments; analysis suppression belongs in `.codacy.yml`.
- Issue #43 is the remaining production-like acceptance blocker.
- LiteBans remains authoritative.
- Green CI does not by itself permit PR #37 to be marked ready or merged.

## Four development groups

| Group | Completion | Why it matters now | Detailed work |
| --- | ---: | --- | --- |
| [[Core Platform and Infrastructure]] | **About 72%** | Modes, configuration, lifecycle, identity and failure handling are shared prerequisites for many other features. | Open the group page for files, descriptions and remaining categories. |
| [[Moderation, Punishments, and Reports]] | **About 56%** | History, sanction changes, request notifications, reports and evidence form the main moderation workflow. | Open the group page for files, commands and remaining categories. |
| [[Staff Tools, Investigations, and Player-State Safety]] | **About 44%** | Staff mode, vanish, freeze, inventories and confiscation can lose or duplicate player state if recovery is incomplete. | Open the group page for safety boundaries, files and remaining categories. |
| [[Integrations, Migration, and Release Readiness]] | **About 36%** | Providers, the site, migration, topology and acceptance evidence determine whether the platform can replace LiteBans safely. | Open the group page for providers, migration and release gates. |

## Current development order

### 1. Preserve the post-PR-27 checkpoint

- Keep `main`, the final PR #27 source head and the post-merge workflow evidence
  recorded exactly.
- Do not reopen PR #27 or recreate its former branch unless a new verified defect
  requires a separate correction.
- Keep documentation evidence boundaries explicit: exact-main runtime evidence,
  merge-ref Wiki evidence and staging evidence are different claims.

### 2. Keep PR #37 internally ready

- Review operational-state persistence, maintenance entry/abort, exact final-run
  activation linkage, duplicate activation, emergency freeze and writer fencing.
- Preserve cross-server database coordination, local transaction serialization,
  restart recovery, migration-run abandonment handling, activation replay and
  cutover audit persistence.
- Keep the deployed V11, V12 and V13 bytes unchanged. Do not add scanner comments;
  use analysis configuration for exclusions and new migration files for future SQL.
- Merge current `main` into `section/plugin` only if the branch actually becomes
  behind; use a normal merge commit, never a rebase or force-push.

### 3. Complete issue #43 against one release candidate

Pin one exact PR #37 source SHA, Paper and Velocity JAR hashes, sanitized
configuration revision and isolated staging environment. Any runtime-source, JAR,
migration, schema, comparison or relevant configuration change invalidates the
record.

The record must cover:

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

### 4. Decide PR #37 readiness

PR #37 may be marked ready only when one exact acceptance record satisfies
`docs/cutover-acceptance.md`, issue #43 is complete, automated validation is clean,
no major review thread remains and the final exact-head diff has been reviewed.

Merging PR #37 still does not authorize a production LiteBans cutover.

### 5. Finish shared foundation gaps

Prioritize the core gaps that block several feature groups:

- complete operational modes and dependency-specific feature gates;
- finish modular configuration and atomic reload;
- finish runtime health and actionable verification;
- prove startup, shutdown, long-outage and process-kill recovery;
- finish historical-name, Bedrock alias and bounded completion behavior.

See [[Core Platform and Infrastructure]].

### 6. Complete the remaining feature groups

Continue moderation history and appeals, player-state safety, provider integrations,
the private site and the final release manifest only after the cutover branch and
its acceptance evidence are stable.

## How to choose the next task

Choose work in this order:

1. correctness or security defects that can corrupt state;
2. recovery and idempotency gaps in destructive workflows;
3. acceptance evidence required by issue #43;
4. shared foundation work that blocks multiple features;
5. one coherent feature category from a group page;
6. documentation and evidence updates for the exact tested revision.

Do not choose work merely because it is easy to demonstrate. A section is done
only when its behavior, authority, persistence, failure handling, recovery, tests,
staging and documentation agree.

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
| PR #37 production-like acceptance | `docs/cutover-acceptance.md` and issue #43 |

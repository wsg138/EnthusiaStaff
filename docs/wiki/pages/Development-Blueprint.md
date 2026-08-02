# Remaining Development Map

This page answers one question: **which feature group should development work on
next?**

Detailed feature percentages, descriptions, source files and remaining tasks now
live on the four feature-group pages. This page keeps only the cross-group order
and dependencies.

- Completion overview: [[Feature Completion Status|Implementation-Status]]
- Exact evidence: [requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)
- Code ownership: [[Developer Code Guide]]
- Validation: [[Build and Testing]]
- Migration operations: [[LiteBans Migration]] and [[Shadow Mode and Cutover]]

## Four development groups

| Group | Completion | Why it matters now | Detailed work |
| --- | ---: | --- | --- |
| [[Core Platform and Infrastructure]] | **About 72%** | Modes, configuration, lifecycle, identity and failure handling are shared prerequisites for many other features. | Open the group page for files, descriptions and remaining categories. |
| [[Moderation, Punishments, and Reports]] | **About 56%** | History, sanction changes, request notifications, reports and evidence form the main moderation workflow. | Open the group page for files, commands and remaining categories. |
| [[Staff Tools, Investigations, and Player-State Safety]] | **About 44%** | Staff mode, vanish, freeze, inventories and confiscation can lose or duplicate player state if recovery is incomplete. | Open the group page for safety boundaries, files and remaining categories. |
| [[Integrations, Migration, and Release Readiness]] | **About 36%** | Providers, the site, migration, topology and acceptance evidence determine whether the platform can ever replace LiteBans safely. | Open the group page for providers, migration and release gates. |

## Current development order

### 1. Converge active branches

- Complete and review [PR #37](https://github.com/wsg138/EnthusiaStaff/pull/37).
- Reconcile and review [PR #27](https://github.com/wsg138/EnthusiaStaff/pull/27).
- Preserve current composition, persistence and scheduling boundaries.
- Establish one clean `main` checkpoint before starting overlapping lifecycle work.

### 2. Finish shared foundation gaps

Prioritize the core gaps that block several feature groups:

- complete operational modes and dependency-specific feature gates;
- finish modular configuration and atomic reload;
- finish runtime health and actionable verification;
- prove startup, shutdown, long-outage and process-kill recovery;
- finish historical-name, Bedrock alias and bounded completion behavior.

See [[Core Platform and Infrastructure]].

### 3. Complete the moderation lifecycle

- punishment history and full case timelines;
- precise sanction reduction, ending, revocation and overturn;
- appeal-linked decisions;
- durable request lifecycle notifications;
- complete escalation/version compatibility;
- report GUI, RoseChat evidence and strict automod.

See [[Moderation, Punishments, and Reports]].

### 4. Complete player-state and investigation safety

- staff-mode entry/restore/recovery;
- complete freeze and vanish coverage;
- online/offline inventory ownership and concurrency;
- item/economy confiscation and restoration;
- alt confidence, exceptions and inheritance;
- inspector, staff tools, cheat testers and fake systems.

See [[Staff Tools, Investigations, and Player-State Safety]].

### 5. Complete external systems

- Currency, Commend, AutoClicker, RoseChat and Market providers;
- complete Discord routing and recovery;
- private punishment/appeal site;
- provider classloader and isolated degraded-mode staging.

See [[Integrations, Migration, and Release Readiness]].

### 6. Build one release candidate and prove it

- finish LiteBans cutover coordination and migration recovery;
- run real-data migration rehearsal;
- run full Velocity/HUB/SMP topology;
- run Java, Bedrock/Geyser and Folia acceptance;
- run load, saturation and process-kill scenarios;
- create one release manifest with every repository revision and artifact hash.

### 7. Run shadow and rollback rehearsal

- complete seven daily summaries across at least 168 continuous hours;
- explain every mismatch;
- run final incremental import;
- rehearse emergency freeze, backup restore and authority rollback;
- record explicit authorization before production authority changes.

## How to choose the next task

Choose work in this order:

1. correctness or security defects that can corrupt state;
2. recovery and idempotency gaps in destructive workflows;
3. shared foundation work that blocks multiple features;
4. one coherent feature category from a group page;
5. real staging that automated tests cannot prove;
6. documentation and evidence updates for the exact tested revision.

Do not choose work merely because it is easy to demonstrate. A section is done
only when its behavior, authority, persistence, failure handling, recovery,
tests, staging and documentation agree.

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

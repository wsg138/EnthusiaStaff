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

- PR #54 merged normally as `717d716d34f3e4e524d9b7c744cb5ece3cacaf04` from exact feature head `b0b5bef5807da7d60d64ad7c59319ec15c53955f` after successful exact-head Coverage and Validate Wiki workflows and zero unresolved review threads.
- PR #55 is the active bounded staff-mode work item: Admin Ender chest access is being corrected from full mutation access to view-only while Founder owner access remains available.
- PR #49 remains the latest merged report-system product checkpoint; PR #50 records historical RoseChat blocker coordination.
- V16 is the live highest Flyway migration; PR #55 adds no migration and V1–V16 remain immutable.
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

### 1. Complete PR #55

PR #55 fixes one confirmed staff-mode asset-safety defect:

- Helper, Mod, and Developer remain unable to open Ender chests during staff mode;
- Admin may open an Ender chest but clicks and drags in that view are cancelled;
- Founder retains configured owner-level Ender access;
- Admin creative inventory interaction outside the Ender view remains available;
- no vanish, freeze, general inventory-editing, confiscation, provider, migration or production scope is included.

Before merge:

- complete the one canonical PR handoff and expected post-merge routing records;
- harsh-review the full diff separately and fix every confirmed defect or merge blocker;
- synchronize with current `main`;
- freeze tracked content;
- verify one unchanged exact head through build/tests, migration immutability, runtime-JAR/provider-leak checks, static analysis, wiki validation, applicable Pi staging and all review gates;
- merge normally only with zero unresolved valid threads;
- verify resulting `main`, feature-head containment and branch cleanup;
- stop without starting another item.

### 2. Continue bounded staff-mode safety work

After PR #55 is complete, staff mode remains the first owner-priority feature. Select exactly one prerequisite-ready lifecycle or restriction-enforcement gap after fresh live reconciliation. Prefer correctness, recovery, idempotency, state restoration, reload/disable safety, rank enforcement or leak prevention. Do not combine a separate staff-mode slice with vanish or freeze unless one confirmed defect genuinely crosses the shared boundary.

### 3. Continue vanish, then freeze

After prerequisite-ready staff-mode work, continue owner priority one with bounded vanish and freeze items:

- vanish still needs complete entity/tracker suppression, integration exposure coverage, live Folia owner-scheduling verification and Java/Bedrock staging;
- freeze still needs complete interaction, command and backend-switch restriction coverage, reconnect/offline semantics and supported RoseChat staff-only chat behavior.

Do not invent unavailable provider APIs or represent hosted tests as full multi-runtime acceptance.

### 4. Complete report notifications

Report notification completion is the second owner priority. Resume the supported RoseChat private-message callback and privacy presentation boundary only after an accessible provider contract defines:

- the callback/event type and supported version;
- before-cancel, accepted-delivery and failed-delivery timing;
- sender and recipient identity semantics;
- cancellation, filtering, ignore, spy and duplicate behavior;
- threading and Paper/Folia scheduling guarantees;
- privacy-safe evidence fields and retention boundaries.

Do not use reflection against unknown implementation classes, copy provider-owned API classes, scrape logs as a fake callback, or store messages before delivery semantics are known. Track unavailable provider APIs in focused blocker issues and the normal handoff. Do not use issue #43 as a general blocker issue.

### 5. Continue escalation policy only after higher priorities

Escalation-policy completion is the third owner priority. Remaining work includes wider combined-recommendation coverage, explicit family-relationship expansion, broader modular punishment/escalation configuration, decayed-history GUI presentation and representative multi-runtime staff acceptance.

Do not begin another escalation-policy or internal infrastructure PR while higher-priority prerequisite-ready staff/player-visible work exists unless it fixes a confirmed correctness, security, concurrency, migration or data-integrity defect; directly unblocks a higher-priority feature; or the owner explicitly approves it.

### 6. Complete issue #43 when the plugin is closer to release

Issue #43 is specifically the LiteBans production-cutover acceptance issue. It is not the general defect or blocker queue.

Pin one exact release-candidate SHA, Paper and Velocity JAR hashes, sanitized configuration revision and isolated staging environment. Any runtime-source, JAR, migration, schema, comparison or relevant configuration change invalidates the record.

The production cutover record must cover representative migration, interrupted recovery, an uninterrupted 168-hour shadow window, maintenance fencing, final incremental migration, ambiguous activation retries, emergency freeze persistence, rollback, reconciliation, Java/Bedrock topology and provider/dependency failures.

Only after that record is complete may a release candidate be deployed for production cutover, EnthusiaStaff authority be activated, LiteBans be disabled or removed, the final production migration be performed, or a live cutover be authorized.

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

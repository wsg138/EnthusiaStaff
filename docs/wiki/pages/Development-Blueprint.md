# Remaining Development Map

This page answers one question: **which feature group should development work on next?**

Detailed feature percentages, descriptions, source files and remaining tasks live on the feature-group pages. This page keeps the cross-group order, current repository state and release dependencies.

- Completion overview: [[Feature Completion Status|Implementation-Status]]
- Exact evidence: [requirements matrix](https://github.com/wsg138/EnthusiaStaff/blob/main/reports/REQUIREMENTS-MATRIX.md)
- Code ownership: [[Developer Code Guide]]
- Validation: [[Build and Testing]]
- Migration operations: [[LiteBans Migration]] and [[Shadow Mode and Cutover]]
- Production cutover acceptance: [cutover acceptance](https://github.com/wsg138/EnthusiaStaff/blob/main/docs/cutover-acceptance.md)

## Repository checkpoint

- PR #64 is the latest merged product PR. It merged normally from exact feature head `63e5d450fad73ffed5900edc160548cb7f165b85` as `9d84f8d50a024b04530335c622d63b573343242b`.
- PR #65 is documentation-only reconciliation. Read live GitHub to determine whether it is still open or already merged before acting.
- No implementation feature is active or preselected by the tracked routing state.
- V16 is the live highest Flyway migration; PR #65 adds no migration and V1–V16 remain immutable.
- LiteBans remains authoritative; merged development work does not deploy a JAR or activate EnthusiaStaff authority.
- Issue #43 is specifically the later LiteBans production-cutover acceptance gate and is not the general blocker queue.

## Four development groups

| Group | Why it matters now | Detailed work |
| --- | --- | --- |
| [[Core Platform and Infrastructure]] | Modes, configuration, lifecycle, identity and failure handling are shared prerequisites for many other features. | Open the group page for files, descriptions and remaining categories. |
| [[Moderation, Punishments, and Reports]] | History, sanction changes, escalation, request notifications, reports and evidence form the main moderation workflow. | Open the group page for files, commands and remaining categories. |
| [[Staff Tools, Investigations, and Player-State Safety]] | Staff mode, vanish, freeze, inventories and confiscation can lose or duplicate player state if recovery is incomplete. | Open the group page for safety boundaries, files and remaining categories. |
| [[Integrations, Migration, and Release Readiness]] | Providers, the site, migration, topology and acceptance evidence determine whether the platform can replace LiteBans safely. | Open the group page for providers, migration and release gates. |

## Current development order

### 1. Finish routing reconciliation when still open

If PR #65 remains open, finish its documentation-only scope, validate the exact documentation head, resolve valid review findings, merge normally, verify resulting `main` and branch deletion, and stop. Do not start a product feature in the same session.

### 2. Freshly select one priority-one gap

When no unfinished PR exists, inspect live source, tests, goals, this blueprint and the requirements matrix. Select exactly one bounded prerequisite-ready incomplete item from staff mode, vanish or freeze.

Do not assume an older handoff candidate is still incomplete. Do not combine unrelated staff-mode, vanish and freeze slices merely because they share the same owner-priority tier.

Current known remaining categories include, subject to live verification:

- staff-mode snapshot/revision and production-like recovery coverage, location/server restoration, CombatLogX behavior, remaining transition restrictions and command coverage;
- vanish entity/tracker suppression, integration exposure coverage, live Folia owner-scheduling verification and Java/Bedrock staging;
- freeze remaining command and backend-switch completeness, supported staff-only chat/provider behavior, offline duration controls and representative Paper/Velocity staging.

These are gap categories, not a preselected next feature.

### 3. Complete report notifications

Report notification completion is the second owner priority. Provider-independent work may proceed when it is the highest prerequisite-ready bounded gap. RoseChat private-message and privacy presentation work remains blocked until an accessible supported provider contract defines:

- callback/event type and supported version;
- before-cancel, accepted-delivery and failed-delivery timing;
- sender and recipient identity semantics;
- cancellation, filtering, ignore, spy and duplicate behavior;
- threading and Paper/Folia scheduling guarantees;
- privacy-safe evidence fields and retention boundaries.

Do not use reflection against unknown implementation classes, copy provider-owned API classes, scrape logs as a fake callback, or use issue #43 as a general blocker issue.

### 4. Continue escalation policy after higher priorities

Escalation-policy completion is the third owner priority. Remaining work includes wider combined-recommendation coverage, explicit family-relationship expansion, broader modular punishment/escalation configuration, decayed-history GUI presentation and representative multi-runtime staff acceptance.

Do not begin another escalation-policy or internal-infrastructure PR while higher-priority prerequisite-ready staff/player-visible work exists unless it fixes a confirmed correctness, security, concurrency, migration or data-integrity defect, directly unblocks a higher-priority feature, or the owner explicitly approves it.

### 5. Complete issue #43 when the plugin is closer to release

Issue #43 is specifically the LiteBans production-cutover acceptance issue. It is not the general defect or blocker queue.

Pin one exact release-candidate SHA, Paper and Velocity JAR hashes, sanitized configuration revision and isolated staging environment. Complete representative migration, interrupted recovery, an uninterrupted 168-hour shadow window, maintenance fencing, final incremental migration, ambiguous activation retries, emergency freeze persistence, rollback, reconciliation, Java/Bedrock topology and provider/dependency failure evidence.

Only after that record is complete and separately approved may a release candidate be deployed for production cutover, EnthusiaStaff authority be activated, LiteBans be disabled or removed, the final production migration be performed, or a live cutover be authorized.

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

# EnthusiaStaff workspace state

Last updated: 2026-08-05

This is a routing record, not a substitute for live GitHub reconciliation.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current legitimate `main` at reconciliation start | `9d84f8d50a024b04530335c622d63b573343242b` |
| Latest merged product work | PR #64 — Block mounted movement while frozen |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `IDLE — PR #65 is documentation-only reconciliation pending live merge verification; no implementation item is active` |
| Pull request | `#65 — Reconcile repository routing after PR #64` |
| Feature branch | `agent/reconcile-post-pr64-routing` |
| Starting main | `9d84f8d50a024b04530335c622d63b573343242b` |
| Work item | Reconcile routing, workspace-state, requirements and Wiki status documentation after recent merges |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-05-post-pr64-reconciliation.md` |
| Migration boundary | V16 is highest; this documentation PR adds no migration; V1–V16 remain immutable |
| Production authority | LiteBans remains authoritative; no deployment or cutover authority is granted |

## Live reconciliation

- PR #64 merged by normal merge commit from exact feature head `63e5d450fad73ffed5900edc160548cb7f165b85`.
- PR #64 merge commit and resulting `main` at reconciliation start are `9d84f8d50a024b04530335c622d63b573343242b`.
- The PR #64 feature head is the merge base of current `main`; `main` is one merge commit ahead and zero behind.
- The PR #64 remote branch is absent. Before this reconciliation branch, only `main` existed.
- No open or draft pull request existed before PR #65.
- Recent merged work also includes PR #58 staff-session disable recovery, PR #59 live-rank vanish reconciliation, PR #60 freeze recovery fencing, PR #61 database-dump protections, PR #62 staff-mode world-interaction restrictions and PR #63 precise freeze-interaction restrictions.
- V16 remains the highest Flyway migration. No recent PR changed migration bytes.
- The durable agent process in `ai-agents/UNIVERSAL-AGENT-PROMPT.md` remains correct and is not changed by this reconciliation.

Exact final-head validation and merge evidence for PR #65 belongs in the pull-request description or consolidated comments rather than in this tracked state file.

## Owner priorities and blockers

When prerequisites are comparable, use this order:

1. staff mode, vanish and freeze;
2. report notification completion;
3. escalation-policy completion.

No open blocker prevents fresh selection within priority one. The supported RoseChat private-message provider contract remains unavailable and blocks provider-dependent report-notification and private-message evidence work. Issue #43 remains open only as the later LiteBans production-cutover acceptance gate; it is not a general blocker queue and does not block ordinary dormant development merges.

## Next route

1. Reconcile live GitHub before acting. If PR #65 remains open, finish this documentation-only PR and do not start implementation.
2. After PR #65 is merged and no unfinished PR exists, inspect current code, tests, goals, the development blueprint and the requirements matrix for actual remaining gaps.
3. Select exactly one bounded prerequisite-ready incomplete staff-mode, vanish or freeze item. No specific feature is preselected by this record.
4. Do not route RoseChat-dependent report work until a supported provider contract exists. Provider-independent report work remains second priority when prerequisite-ready.
5. Treat escalation-policy completion as third priority.
6. Keep issue #43 reserved for later production-cutover acceptance when a release candidate and representative isolated environment exist.

## Permanent boundaries

- No deployment, service restart, production database, player data, credentials, Discord route or hosting access.
- No EnthusiaStaff authority activation.
- No LiteBans disablement, removal or authority change.
- No issue #43 acceptance, 168-hour shadow window, production migration, cutover, backup or restore.
- No Flyway repair, migration edit or history rewrite.
- A merged development PR remains dormant until separately authorized production work occurs.

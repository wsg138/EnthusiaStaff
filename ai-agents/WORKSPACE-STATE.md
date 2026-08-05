# EnthusiaStaff workspace state

Last updated: 2026-08-05

This is a routing record, not a substitute for live GitHub reconciliation.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Audited legitimate `main` | `dddc8352aed5aac1eeead3a670680cd647b1b9c2` |
| Latest merged product work | PR #64 — Block mounted movement while frozen |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `PLANNING — repository-wide completion audit is merged; no implementation package is active or preselected` |
| Canonical audit | `reports/PROJECT-COMPLETION-AUDIT.md` |
| Audit pull request | `#66 — Audit repository-wide project completion state` |
| Audited main | `dddc8352aed5aac1eeead3a670680cd647b1b9c2` |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-05-repository-completion-audit.md` |
| Migration boundary | V16 is highest; the audit changes no migration; V1–V16 remain immutable |
| Production authority | LiteBans remains authoritative; no deployment or cutover authority is granted |

## Audit result

- The repository is structurally established and feature-incomplete.
- The canonical 99-item ledger records 19 `COMPLETE_GOOD`, 47 `COMPLETE_WITH_ISSUES`, 12 `PARTIAL`, 6 `NOT_STARTED`, 3 `BLOCKED`, 10 `DEFERRED_ACCEPTANCE`, and 2 `OUTSIDE_THIS_REPOSITORY` items.
- The highest-risk confirmed defect is `AUD-APPEAL-003`: one punishment appeal can end all active sanctions in the same combined case.
- Major incomplete areas include functional staff tools, cheat/fake tooling, provider-backed asset workflows, RoseChat-dependent communication/evidence, Velocity reload, Bedrock identity correctness, distributed staging, and LiteBans acceptance/cutover.
- Exact-main Java 21 build/tests, MariaDB/Testcontainers integration, runtime JAR inspection, provider-leak inspection, aggregate coverage generation, and Wiki validation succeeded.
- Aggregate coverage is 46.89% line and 37.83% branch; major runtime paths remain weakly proved.
- Current-SHA Pi staging did not execute because GitHub reported account billing/spending-limit status before a runner started.
- No product code, migration, workflow, runtime configuration, deployment control, production authority, LiteBans authority, or issue #43 acceptance changed.

## Next route

1. Reconcile live GitHub before planning.
2. Use `reports/PROJECT-COMPLETION-AUDIT.md` as the authority for defining bounded implementation packages.
3. Do not preselect work from older priority lists or handoffs.
4. Resolve dependency and code-overlap relationships before allowing parallel work.
5. Keep provider-dependent, private-data, distributed, Bedrock, and production-acceptance work in the environments identified by the audit.
6. Keep issue #43 reserved for the later 168-hour production-like LiteBans acceptance gate.

## Permanent boundaries

- No deployment, service restart, production database, player data, credentials, Discord route or hosting access.
- No EnthusiaStaff authority activation.
- No LiteBans disablement, removal or authority change.
- No issue #43 acceptance, 168-hour shadow window, production migration, cutover, backup or restore.
- No Flyway repair, migration edit or history rewrite.
- A merged development PR remains dormant until separately authorized production work occurs.

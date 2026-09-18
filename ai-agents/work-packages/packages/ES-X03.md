# `ES-X03` — EnthusiaMarket destructive provider

## 1. Package identity
`ES-X03`; external/multi-repository; primary `COMP-STAFF`; other `COMP-MARKET`; priority 120; conditional parallelism only without shared destructive-state overlap.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED` after the 2026-09-18 continuation. The former D04 migration/shared-file blocker is resolved and the existing Staff implementation has been reconciled to current `main`, but the exact frozen implementation head cannot clear its required static-analysis or canonical Pi gates. The registry is the canonical routing index.

## 3. Objective
Implement durable market restriction, reservation, confiscation, rollback, and exact restoration across EnthusiaStaff and EnthusiaMarket.

## 4. Included behavior
Supported versioned provider contract; listing/reservation ownership; durable snapshots/operation IDs; restriction/confiscation; idempotent rollback/restoration; retry/restart/race handling; provider missing/version mismatch; matching aggregate copy and parity.

## 5. Explicit exclusions
Production listings; whole-market rollback; currency/reputation work; unverified reflection against provider internals; representative destructive/load/process-kill acceptance assigned to `ES-V03`.

## 6. Dependencies
`ES-P08` and `ES-X02` are `COMPLETE`.

## 7. Repository and privacy boundaries
Use the existing Staff [PR #139](https://github.com/wsg138/EnthusiaStaff/pull/139) on `package/es-x03-market-provider`; do not replace, rebase, force-push, squash, or close it while blocked. EnthusiaMarket `main` already contains the merged product lineage; standalone [PR #6](https://github.com/wsg138/EnthusiaMarket/pull/6) is an unpaired hardening proposal and must not be absorbed or merged as X03. Market may use only ordinary public repository CI. No private Pi/staging runner config, labels, bridge/dispatch implementation, staging secrets/topology/credentials, private artifact-transfer mechanism, or Sentinel infrastructure may enter Market or BadgersMC repositories.

## 8. Current live heads and collision state

- EnthusiaStaff `main` reconciliation head: `6201765f0e07b08f9b22ed7ce96838c01fa94007`.
- Staff PR #139 exact head: `879eae12df35253cce6cd12179d5cef1afe95dd9`; open, non-draft, and mergeable.
- EnthusiaMarket `main`: `cc19fa966dcb155fa1743f5076fb5152e74bdf8f`; the aggregate Market component matches this standalone tree before Staff merge. Its metadata remains `SYNC_PENDING` until post-merge parity.
- Staff `main` owns V20; X03 owns `V21__market_compliance_journal.sql`; active D09 reserves V22. X03 and D09 both touch `PaperCommandRegistrar.java`, but their hunks are disjoint.
- No X03 exact-path overlap exists with the user-owned dirty root worktree. No force-push, rebase, squash, or destructive reset is authorized or used.

## 9. Historical continuation and reconciliation
The old thermal/readiness blocker changed on 2026-08-26 and Market's standalone product lineage merged normally. A real migration-ceiling test failure at historical Staff head `22ea8395caa421dc9161c84acd58b5b16ca05fc8` was repaired without product behavior changes. D04 later serialized V20 to `main`; the current PR #139 head reconciles that work and carries X03's forward-only V21 migration.

Historical pass/fail evidence remains historical only. It does not make the current head green, and historical thermal/readiness failures are not rewritten as passes.

## 10. Manual review and exact-head validation

Focused manual review on `879eae12df35253cce6cd12179d5cef1afe95dd9` covered the journal migration, JDBC store, reconciliation coordinator, provider boundary, command surface, lifecycle maintenance, and focused unit/integration coverage. It found durable intent before provider mutation, idempotency and optimistic-revision handling, fail-closed provider writes, explicit approval before confiscation, and recovery that never auto-approves. No new source defect was found, so the implementation head remains frozen.

| Gate | Result | Evidence |
| --- | --- | --- |
| Diff hygiene | PASS | `git diff --check origin/main...879eae12df35253cce6cd12179d5cef1afe95dd9` produced no output. |
| Coverage/full build | PASS | GitHub Actions run `35168774609`, job `105035696843`, on the exact head. |
| Sentinel restart artifact | PASS | Run `35168774608`, job `105035696425`, on the exact head; the required artifact manifest is present. |
| PR review | No live inline threads | CodeRabbit's successful status is skipped/manual review status, not an automated full-review approval. |
| Codacy static analysis | NOT PASS | Check `105036637444` is `action_required` with 2,085 new issues. |
| Canonical Pi supersession | NOT PASS | Run `35168772060` failed before private dispatch because the public workflow-history lookup returned HTTP 401 `Bad credentials`. No private Pi/Paper/MariaDB runtime ran. |
| Durable Sentinel restart | NOT RUN | A status request at [PR #139 comment 5730170958](https://github.com/wsg138/EnthusiaStaff/pull/139#issuecomment-5730170958) had no observed response at publication; no restart was requested. |

## 11. Static-analysis boundary
The static result is not dismissed wholesale. Of 2,085 reports, 1,989 are under the aggregate Market component and 96 are Staff/agent Markdown. Some high findings are demonstrable analyzer mismatches: a Staff-specific RAC-table pattern applied to Market table names, SQLint applied to component SQLite migrations, and Gradle dependency coordinates mistaken for keys. The same report also contains component-owned complexity, Markdown, and dependency debt, including a Trivy medium finding.

Do not broaden root `.codacy.yml` to hide `components/enthusia-market/**`. This frozen Staff integration package must not silently take ownership of a new paired provider static-remediation package or suppress valid analyzer findings.

## 12. Current synchronization evidence
The aggregate Market component and standalone Market `main` are equal before Staff merge. Final canonical standalone↔aggregate parity remains a required post-Staff-merge gate; `COMPONENT-METADATA.md` correctly remains `SYNC_PENDING`. Standalone PR #6 is separate and its inclusion would break this exact parity, so preserve it unchanged.

## 13. Exact unblock condition
`BLOCKED` / `PARKED_BLOCKED`. Keep PR #139 and its existing branch; do not create a replacement X03 product branch.

Before a fresh exact-head static check, the owner must choose and record one bounded path:

1. authorize a paired standalone-Market and aggregate remediation scope for valid provider static debt; or
2. make a reviewed analyzer-boundary/configuration decision that preserves standalone Market validation and does not globally suppress valid component findings.

The Codacy decision must be made at the supported repository/analyzer scope, including any required supported tool-configuration enablement; it cannot be guessed through a broad aggregate exclusion. Independently, restore the public Pi workflow-history authentication/effective permission used by the supersession bridge. Only after both conditions change should a worker freeze the resulting exact head and rerun all required hosted/static/manual-review/Sentinel/Pi gates. Merge #139 normally only if every required gate is terminal and green, then prove post-merge standalone↔aggregate parity and update component metadata.

## 14. Completion definition
The standalone Market product lineage is merged, but ES-X03 is not complete until Staff PR #139 is fully exact-head validated, normally merged and contained, and post-merge provider parity is exact. Representative destructive/load/process-kill acceptance remains `ES-V03`.

## 15. Handoffs and production boundary
Historical handoffs: `ai-agents/reports/package-handoffs/2026-08-14-es-x03-market-provider-blocked.md` and `ai-agents/reports/package-handoffs/2026-08-26-es-x03-discord-serialization-blocked.md`.

Current handoff: `ai-agents/reports/package-handoffs/2026-09-18-es-x03-parked-static-and-pi.md`.

No production listing, balance, item, private player row, database, deployment, migration/import execution, Discord configuration, authority, LiteBans, cutover, or issue #43 acceptance changed.

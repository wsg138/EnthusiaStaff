from pathlib import Path

registry = Path('ai-agents/work-packages/PACKAGE-REGISTRY.md')
text = registry.read_text(encoding='utf-8')
old = "`ES-X02 — EnthusiaCurrency destructive provider` is now dependency-complete and `READY` at priority 110. `ES-X01` remains `BLOCKED` / `PARKED_BLOCKED` because the supported RoseChat standalone repository, default branch, source, and AGENTS contract remain unresolved. `ES-X03`, `ES-X04`, `ES-V02`, `ES-V03`, `ES-A01`, and `ES-QA01` remain parked on their documented dependencies/external conditions."
new = "`ES-X02 — EnthusiaCurrency destructive provider` is `BLOCKED` / `PARKED_BLOCKED` after actionable implementation and exact-head hosted validation reached standalone Currency PR #11 at `5d9dfc7f03d33ee2147141fef4c777ba0e67d939`. The configured Java 21 Maven suite passed on that exact head (run `31657088614`), and a manual rollback review defect was fixed, but Codacy still reports 29 unresolved new findings (2 critical, 1 high, 26 medium) without exposing the individual findings through the available GitHub evidence path. The package cannot be frozen or merged until those findings are individually inspected and every valid finding is fixed or explicitly invalidated. `ES-X01` remains independently `BLOCKED` / `PARKED_BLOCKED`. `ES-X03`, `ES-X04`, `ES-V02`, `ES-V03`, `ES-A01`, and `ES-QA01` remain parked on their documented dependencies/external conditions."
if text.count(old) != 1:
    raise SystemExit('registry canonical paragraph anchor changed')
text = text.replace(old, new)
old_row = "| `ES-X02` | EnthusiaCurrency destructive provider | `READY` | `READY` | 110 | `ES-P08` | dependency complete; exact next normal package absent a higher-precedence actionable continuation |"
new_row = "| `ES-X02` | EnthusiaCurrency destructive provider | `BLOCKED` | `PARKED_BLOCKED` | 110 | `ES-P08` | Currency PR #11 head `5d9dfc7...`; Java 21 Maven run `31657088614` passed; Codacy reports 29 unresolved findings whose individual details are not available through current GitHub evidence; aggregate branch reserved but intentionally not imported/opened until standalone merge |"
if text.count(old_row) != 1:
    raise SystemExit('registry ES-X02 row anchor changed')
text = text.replace(old_row, new_row)
insert = """
## ES-X02 active blocked record

- Package start: Staff `main` `4831b1442e572914c86fd8e202e7de6f546868e2`; Currency `main` `922223cfff8c325e36f58b6af6adf6d74e4a5417`.
- Required package branch reserved in both repositories: `package/es-x02-currency-provider`.
- Standalone Currency implementation PR #11 is open and non-draft at exact current head `5d9dfc7f03d33ee2147141fef4c777ba0e67d939`.
- Exact-head configured hosted validation passed: Currency CI run `31657088614`, Java 21, `mvn -B -ntp verify`.
- Manual harsh review found a valid rollback-status defect: compensation could report `FAILED_ROLLED_BACK` without verifying rollback. It was repaired so unverifiable compensation returns `QUARANTINE_REQUIRED`; the exact-head CI rerun passed after the fix.
- CodeRabbit could not perform a final automated review because its service reported a temporary review limit. That unavailable review is not called a pass; manual review remains required and no CodeRabbit finding is being suppressed.
- Codacy currently reports 29 new unresolved findings: 2 critical security, 1 high performance, and 26 medium. The available GitHub comment exposes aggregate counts and an external link but not the individual findings needed for a valid/invalid disposition. Under `VALIDATION-POLICY.md`, the package cannot merge around unresolved static-analysis findings.
- Aggregate Staff branch is reserved at the package-start Staff head but has no imported Currency product delta and no aggregate implementation PR. This is intentional: the standalone repository must merge first, then its exact merged state is imported and parity-proved.
- Canonical Pi, aggregate hosted validation, standalone merge, aggregate import/PR, final parity, and temporary-branch cleanup have not run and are not claimed. Representative live destructive balances remain assigned to `ES-V03` by the original package contract.
- Exact unblock condition: individual Codacy PR #11 findings become accessible through a usable evidence path; inspect every finding, fix every valid finding (or record a concrete invalid disposition), rerun static analysis to zero valid unresolved findings on the same frozen head, then resume remaining ES-X02 review/Pi/merge/import/parity gates.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-12-es-x02-currency-provider-blocked.md`.

"""
marker = "## ES-P08 terminal record\n"
if text.count(marker) != 1:
    raise SystemExit('registry terminal marker changed')
text = text.replace(marker, insert + marker)
registry.write_text(text, encoding='utf-8')

package = Path('ai-agents/work-packages/packages/ES-X02.md')
package.write_text('''# `ES-X02` — EnthusiaCurrency destructive provider

## 1. Package identity
`ES-X02`; External/multi-repository; primary `COMP-STAFF`; other `COMP-CURRENCY`; priority 110; sequential around shared destructive journals.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED`; standalone implementation exists but static-analysis findings cannot yet be individually dispositioned.

## 3. Objective
Implement transactional currency removal and exact restoration across EnthusiaStaff and EnthusiaCurrency.

## 4. Why the package exists
The audit marks currency destruction critical and provider-blocked: runtime paths require a compatible first-party contract plus private acceptance later.

## 5. Included audit IDs
`AUD-ASSET-002` and currency portion of `AUD-ASSET-005`.

## 6. Included behavior
Versioned supported API; reservation/operation IDs; exact bank/inventory/Ender Chest snapshots; source-ordered removal; persistent bank revisions; stale rejection; idempotent apply/restore; verified rollback or quarantine; provider present/missing/version mismatch; case/audit linkage; matching aggregate copy/parity.

## 7. Explicit exclusions
Production balances; representative live destructive testing (`ES-V03`); market/reputation; economy redesign outside required contract.

## 8. Dependencies
`ES-P08` is `COMPLETE`.

## 9. Component and repository boundaries
EnthusiaStaff integration/root plus `components/enthusia-currency/`, and `wsg138/EnthusiaCurrency`; no unrelated components, permanent branches, or isolated product PR.

## 10. Required branches
`package/es-x02-currency-provider` exists in both repos. Preserve both while blocked; delete only after verified package completion.

## 11. Required PRs
Standalone Currency PR #11 exists. The matching aggregate Staff product PR is intentionally not opened yet because policy requires standalone merge before importing its exact merged state.

## 12. Implementation checklist
- [x] Reconcile both repositories, AGENTS, live heads, and package registry.
- [x] Reserve same-ID package branches in both repositories.
- [x] Define/import versioned moderation contract into standalone Currency.
- [x] Implement operation-owned expiring movement leases.
- [x] Implement exact bank/inventory/Ender Chest snapshot/checksum and source-ordered planning.
- [x] Persist bank revision and use compare-and-set stale-state protection.
- [x] Implement idempotent apply and exact restore with monotonic revision.
- [x] Register provider through Bukkit ServicesManager and fail closed from Staff gateway when missing/version-mismatched.
- [x] Add lock/allocation/persistent-revision regression tests and documentation.
- [x] Fix manual-review rollback defect so unverifiable compensation quarantines instead of claiming rollback.
- [x] Exact current Currency head passes configured Java 21 Maven verification.
- [ ] Inspect/disposition every Codacy finding and reach zero valid unresolved static findings.
- [ ] Freeze final standalone head and finish zero-finding review gate.
- [ ] Execute required canonical Pi validation for the frozen executable scope.
- [ ] Merge standalone PR normally.
- [ ] Import exact merged standalone state into aggregate mirror and update component metadata.
- [ ] Open/cross-link aggregate Staff product PR, run aggregate exact-head gates, and merge normally.
- [ ] Prove post-merge standalone/aggregate parity and clean package branches.

## 13. Acceptance criteria
No balance loss/duplication under success, rejection, timeout, duplicate, crash, or restoration; snapshots/audit are exact; missing/incompatible provider fails safe; both PRs merge and aggregate copy matches standalone.

## 14. Test requirements
Both repos' suites plus reservation/idempotency, concurrent changes, partial failure/rollback, restart/retry, stale restoration, provider absent/version mismatch, authorization/audit, and bounded work tests. Exact current standalone hosted suite passed; remaining runtime/aggregate proof has not run.

## 15. Static-analysis requirements
All configured checks in both repos; zero valid human/CodeRabbit/Codacy findings. Current blocker: Codacy PR #11 summary reports 29 new findings (2 critical, 1 high, 26 medium) but the current GitHub evidence path does not expose individual findings for disposition.

## 16. Documentation requirements
Contract/version, commands/permissions, operation states, recovery/restoration, provider setup/missing behavior, component metadata, package handoff, PR cross-links.

## 17. Security and privacy requirements
Financial-grade authorization/audit; no real balances/player rows in artifacts; fail closed; bounded/redacted logging.

## 18. Migration impact
No new migration was added. Existing SQLite balances schema is upgraded in owning-repo initialization with a persistent revision column; history is not rewritten.

## 19. Bedrock considerations
Staff controls retain text fallback and identity correctness; private client/destructive acceptance remains later.

## 20. Distributed-runtime considerations
Operation ownership, concurrent balance changes, duplicate calls, reconnect, process death, and DB latency are handled through lease ownership, checksums, persistent revisions, idempotency, and quarantine outcomes; private acceptance remains later.

## 21. External-provider considerations
Uses the verified `wsg138/EnthusiaCurrency` API; no reflection/invented runtime contract. Standalone AGENTS/CI remain mandatory.

## 22. Completion definition
Both exact-head PRs merge normally; all required checks/reviews pass; parity true; metadata/merges/hashes recorded; temporary branches cleaned. Private representative destructive acceptance remains `ES-V03`.

## 23. Resume state
Resume standalone Currency PR #11 at exact current package head `5d9dfc7f03d33ee2147141fef4c777ba0e67d939`. Do not import/merge the aggregate mirror yet. First obtain individual Codacy finding details, disposition each, fix all valid findings, and rerun static/review gates. The package is parked while that evidence remains inaccessible.

## 24. Last completed checkpoint
Standalone provider implementation plus compensation repair is durable on PR #11. Exact-head Currency CI run `31657088614` passed Java 21 `mvn -B -ntp verify` on `5d9dfc7...`.

## 25. Remaining checklist
Codacy dispositions; final harsh review/freeze; canonical Pi; standalone normal merge; exact aggregate import/metadata/PR; aggregate hosted/Pi gates as applicable; aggregate normal merge; post-merge parity; containment and branch cleanup.

## 26. Known blockers
Codacy reports 29 unresolved new PR findings, including 2 critical and 1 high, while the available GitHub evidence does not provide individual finding details. Exact unblock: make those finding details accessible, resolve every valid issue or record concrete invalid dispositions, and rerun static analysis on the frozen current head. CodeRabbit also reported a temporary review-rate limit; it is not called a pass and is not used to waive manual review.

## 27. Current evidence
- Staff package start `main`: `4831b1442e572914c86fd8e202e7de6f546868e2`.
- Currency package start `main`: `922223cfff8c325e36f58b6af6adf6d74e4a5417`.
- Currency implementation branch: `package/es-x02-currency-provider`.
- Staff reserved package branch: `package/es-x02-currency-provider` (no aggregate product import yet).
- Currency PR: #11, open/non-draft.
- Current Currency head: `5d9dfc7f03d33ee2147141fef4c777ba0e67d939`.
- Hosted exact-head run: `31657088614`, success, Java 21 `mvn -B -ntp verify`.
- Manual review repair: unverifiable compensation now returns `QUARANTINE_REQUIRED`; exact-head CI passed after repair.
- Codacy summary: 29 unresolved new findings (2 critical, 1 high, 26 medium); not passed.
- CodeRabbit: final automated review unavailable due temporary rate limit; not counted as a pass.
- Canonical Pi/staging: not executed; no pass claimed. Representative live destructive balances remain deferred to `ES-V03`.
- Handoff: `ai-agents/reports/package-handoffs/2026-08-12-es-x02-currency-provider-blocked.md`.

## 28. Merge and synchronization record
No product merge has occurred. One-sided merge is intentionally avoided while standalone static review is blocked. Completion still requires both normal merges, exact post-merge parity, metadata, containment, and temporary branch cleanup.
''', encoding='utf-8')

workspace = Path('ai-agents/WORKSPACE-STATE.md')
text = workspace.read_text(encoding='utf-8')
text = text.replace('| Active package | None. ES-P08 implementation PR #128 merged normally and its implementation branch is deleted. |', '| Active package | `ES-X02 — EnthusiaCurrency destructive provider` is `BLOCKED` / `PARKED_BLOCKED`. Standalone Currency PR #11 is open at exact head `5d9dfc7f03d33ee2147141fef4c777ba0e67d939`; the Staff same-ID branch is reserved but has no aggregate product import yet. |')
text = text.replace('| Ready package | `ES-X02 — EnthusiaCurrency destructive provider` is dependency-complete and `READY` at priority 110. It is not activated by this finalization. |', '| ES-X02 validation state | Currency Java 21 `mvn -B -ntp verify` run `31657088614` passed on exact head `5d9dfc7...`. Codacy still reports 29 unresolved new findings (2 critical, 1 high, 26 medium), and individual details are not available through the current GitHub evidence path; no static pass, Pi pass, merge, or parity pass is claimed. |')
text = text.replace('| Exact next action | A new sequential worker must reconcile live GitHub. Absent a newly discovered higher-precedence `ACTIONABLE_CONTINUATION`, select `ES-X02 — EnthusiaCurrency destructive provider`, work exactly that package, publish durable state, and stop. |', '| Exact next action | Treat ES-X02 as `PARKED_BLOCKED` while the Codacy finding-detail condition is unchanged. Resume it before new work when individual PR #11 findings become accessible: inspect/disposition all findings, fix every valid issue, rerun static/review gates, then continue Pi → standalone merge → exact aggregate import/PR → aggregate gates/merge → parity/cleanup. While unchanged, normal routing may skip this parked package per worker protocol. |')
workspace.write_text(text, encoding='utf-8')

handoff = Path('ai-agents/reports/package-handoffs/2026-08-12-es-x02-currency-provider-blocked.md')
handoff.write_text('''# ES-X02 — EnthusiaCurrency destructive provider — blocked handoff

Date: 2026-08-12 America/Indiana/Indianapolis

## Routing

- Package: `ES-X02` only.
- Status: `BLOCKED` / `PARKED_BLOCKED`.
- Dependency `ES-P08`: complete.
- Staff start: `4831b1442e572914c86fd8e202e7de6f546868e2`.
- Currency start: `922223cfff8c325e36f58b6af6adf6d74e4a5417`.
- Same-ID branches: `package/es-x02-currency-provider` in both repositories.
- Currency PR #11: open, non-draft, current head `5d9dfc7f03d33ee2147141fef4c777ba0e67d939`.
- Staff aggregate product PR: not opened; aggregate branch remains at the package-start Staff state because standalone must merge before exact import.

## Durable implementation

The standalone provider now publishes API v1 through Bukkit ServicesManager, owns expiring operation leases, snapshots exact bank/inventory/Ender Chest assets with SHA-256 state checksums and persistent bank revision, plans source-ordered exact debits, rejects stale state, supports idempotent apply/restore, advances restore revisions monotonically, blocks normal inventory movement while leased, and returns quarantine outcomes when durable flush or exact compensation cannot be proven. SQLite balance persistence now carries revisions, and regression tests cover lease ownership/expiry, exact denomination allocation, and revision persistence/restart upgrade.

A manual harsh review found one valid defect after the first green build: physical compensation could report `FAILED_ROLLED_BACK` even if restoring the physical state failed. The branch was repaired so compensation re-observes the exact account state; only a verified exact rollback returns `FAILED_ROLLED_BACK`, otherwise the operation returns `QUARANTINE_REQUIRED`.

## Exact-head validation

- Frozen candidate/current executable head: `5d9dfc7f03d33ee2147141fef4c777ba0e67d939`.
- Currency configured hosted suite: run `31657088614` passed on that exact head with Temurin Java 21 and `mvn -B -ntp verify`.
- The prior bot-authored compensation-fix head produced `action_required` rather than product evidence; a content-identical user-authored commit retriggered the exact-head suite. No skipped/action-required run is called a pass.
- CodeRabbit final automated review could not execute because the service reported a temporary review-rate limit. This is not labeled passing and does not waive manual review.
- Codacy PR summary currently reports 29 new findings: 2 critical security, 1 high performance, and 26 medium. The GitHub-visible summary exposes aggregate counts and an external link, not individual finding details. Those findings therefore cannot honestly be classified valid/invalid from the available evidence.
- Canonical Pi has not run. Aggregate hosted validation has not run. No staging or runtime pass is claimed.
- Representative destructive balances/private destructive acceptance remain excluded here and assigned to `ES-V03` by the original package contract.

## Blocker and exact resume action

`VALIDATION-POLICY.md` requires every valid Codacy/static finding resolved before merge. The current tool-visible GitHub evidence is insufficient to inspect the 29 findings individually, so ES-X02 is parked rather than merged around unknown critical/high findings.

Exact unblock condition: the individual Codacy findings for Currency PR #11 become accessible through a usable evidence path. Resume ES-X02 immediately when that changes; inspect every finding, fix every valid issue or record a concrete invalid disposition, rerun static analysis and harsh review on the unchanged/final head, then perform canonical Pi and the remaining standalone merge → exact aggregate import → aggregate PR/gates/merge → parity/cleanup sequence.

## Systems not to disturb

Preserve historical Currency PRs #1–#9 and unrelated Staff work. Do not alter LiteBans authority, issue #43, production routes/data, migration history, or other packages. Do not merge either ES-X02 product side one-sided, squash/rebase/force-push, or claim missing Pi/static/staging evidence as passing.
''', encoding='utf-8')

latest = Path('ai-agents/reports/agent-handoffs/latest.md')
latest.write_text('''# Latest package-worker handoff

Current package: `ES-X02 — EnthusiaCurrency destructive provider`

Status: `BLOCKED` / `PARKED_BLOCKED`.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-12-es-x02-currency-provider-blocked.md`.

Standalone Currency PR #11 is open at exact head `5d9dfc7f03d33ee2147141fef4c777ba0e67d939`. Its configured Java 21 Maven suite passed in run `31657088614` after a manual-review compensation defect was repaired. Codacy still reports 29 unresolved findings (2 critical, 1 high, 26 medium), while the current GitHub evidence path exposes only aggregate counts rather than individual findings. No static-analysis pass, canonical Pi pass, product merge, aggregate import, or parity pass is claimed.

Resume ES-X02 when individual Codacy PR #11 findings become accessible; disposition/fix all valid findings first, then continue the remaining exact-head review/Pi/merge/import/parity sequence. While that external condition is unchanged, treat ES-X02 as parked per `WORKER-PROTOCOL.md` and do not repeatedly rerun the same unavailable review path.
''', encoding='utf-8')

from pathlib import Path
import re

WORKSPACE = '''# Workspace state

Last updated: 2026-08-13

Live GitHub overrides stale records. Detailed package evidence remains in the registry, package records, canonical handoffs, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active package | `ES-X02 — EnthusiaCurrency destructive provider` remains `BLOCKED` / `PARKED_BLOCKED` only because canonical private Pi staging has not received a trusted runner. |
| Frozen Staff product head | `fbba02d10301b6bc6d80ada4ad7113f80ff95514` on aggregate PR #133; non-draft, mergeable, preserved unmerged. |
| Final standalone Currency main | `b922c5af30860a6c205f9ee16b817349a7677cd0`, reached through normal merges of Currency PRs #11, #12, and #13. |
| Standalone validation | Final Currency head `a968f04b09c11dc1816f2b802626adbcef0f73c8` passed exact branch-head Java 21 `mvn -B -ntp verify` (7 tests + shaded JAR), Codacy, review, and merged normally. |
| Aggregate hosted validation | Staff Coverage run `31692612391` passed full Java 21 multi-module build/tests, runtime-JAR/provider-leak inspection, JaCoCo, artifact upload, and Codacy coverage; Staff Codacy check `94423669170` has zero issues; zero valid unresolved review threads; Sentinel artifact run `31692612386` passed. |
| Canonical Pi state | Public run `31692610056` built and transferred the exact runtime and dispatched private run `31693194558`. Private job `94424932390` remains queued with `runner_id: 0`, empty runner name, and zero executed steps for `self-hosted/Linux/ARM64/enthusia-staging`. No Pi pass or product failure is claimed. |
| Infrastructure exception | None. ES-X02 has no explicit owner approval for `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED`; the queued zero-execution job cannot be relabeled as completion. |
| Mirror/parity state | Pre-merge Git-object verification proves the Staff mirror is byte-identical to Currency `b922c5af...` for every standalone root object; component metadata remains `SYNC_PENDING` until Staff normal merge plus required post-merge `component_sync.py` parity. |
| Production boundary | Representative live destructive balances remain deliberately deferred to `ES-V03`; issue #43 remains open/deferred and LiteBans remains authoritative. |
| Exact next action | Resume ES-X02 as `ACTIONABLE_CONTINUATION` when the trusted Pi runner can allocate. First reconcile private run `31693194558` and public bridge `31692610056`; if they later completed, inspect exact private/public evidence and cleanup. Otherwise run one fresh exact-head canonical Pi only after the infrastructure condition changes. Require actual private execution before merging Staff PR #133. |

## Package boundary

Do not start ES-X03, ES-X04, ES-V03, or another package from this worker. Preserve Staff PR #133 and both repositories' legitimate unrelated work. No production data, deployment, shadow window, cutover, authority change, or private-data acceptance is authorized by ES-X02.
'''

PACKAGE = '''# `ES-X02` — EnthusiaCurrency destructive provider

## 1. Package identity
`ES-X02`; External/multi-repository; primary `COMP-STAFF`; other `COMP-CURRENCY`; priority 110; sequential around shared destructive journals.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED`; implementation and every non-Pi gate are complete, but canonical private Pi staging has not received a trusted runner.

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
`package/es-x02-currency-provider` is preserved for open Staff PR #133. Standalone Currency work is already merged to `main`; branch cleanup waits for verified package completion. The blocker publication uses docs-only `status/es-x02-pi-runner-blocked-20260813`.

## 11. Required PRs
Standalone Currency PRs #11, #12, and #13 merged normally. Aggregate Staff product PR #133 is open, non-draft, mergeable, frozen at `fbba02d10301b6bc6d80ada4ad7113f80ff95514`, and intentionally unmerged while canonical Pi is unavailable.

## 12. Implementation checklist
- [x] Reconcile both repositories, AGENTS, live heads, and package registry.
- [x] Define and implement versioned moderation provider in standalone Currency.
- [x] Implement expiring operation-owned locks, exact snapshots/checksums, source-ordered removal, persistent revisions, stale/CAS protection, idempotent apply/restore, verified compensation/quarantine, and provider registration.
- [x] Fix all valid standalone and aggregate static/review findings, including async durability exactness, lease timing/overflow, movement-lock gaps, failed-CAS phantom state, aggregate PMD debt, and Vault-missing startup continuation.
- [x] Merge standalone Currency work normally through PRs #11/#12/#13; final `main` `b922c5af30860a6c205f9ee16b817349a7677cd0`.
- [x] Import exact merged standalone state into Staff and prove pre-merge Git-object identity.
- [x] Open aggregate Staff PR #133 and freeze exact product head `fbba02d10301b6bc6d80ada4ad7113f80ff95514`.
- [x] Pass exact aggregate hosted build/tests/coverage, runtime-JAR/provider-leak, static analysis, review, and Sentinel artifact gates.
- [ ] Execute canonical private Pi runtime/persistence/restart/cleanup validation. Current private job has no runner allocation.
- [ ] Merge Staff PR #133 normally.
- [ ] Prove post-merge standalone/aggregate parity with `component_sync.py`, update metadata to `IN_SYNC`, verify containment, and clean temporary branches.

## 13. Acceptance criteria
No balance loss/duplication under success, rejection, timeout, duplicate, crash, or restoration; snapshots/audit are exact; missing/incompatible provider fails safe; both repositories merge normally and aggregate copy matches standalone. Canonical Pi must actually execute successfully before aggregate merge.

## 14. Test requirements
Both repos' suites plus reservation/idempotency, concurrent changes, partial failure/rollback, restart/retry, stale restoration, provider absent/version mismatch, authorization/audit, and bounded work tests. Representative live destructive balance acceptance remains deferred to `ES-V03`.

## 15. Static-analysis requirements
Satisfied on current product heads: final standalone Codacy suite `85973637978` succeeded; final Staff Codacy check `94423669170` reports zero issues. No valid unresolved CodeRabbit/human review thread remains.

## 16. Documentation requirements
Contract/version, provider setup/missing behavior, operation states, recovery/restoration, component metadata, package handoff, and PR cross-links are present; final completion/parity metadata remains pending.

## 17. Security and privacy requirements
Financial-grade authorization/audit; no real balances/player rows in artifacts; fail closed; bounded/redacted logging. No private databases, production rows, secrets, or reconstructable private evidence were committed.

## 18. Migration impact
No new Staff migration. Existing Currency SQLite balance schema is upgraded by the owning repository with a persistent revision column; history is not rewritten.

## 19. Bedrock considerations
Staff controls retain text fallback and identity correctness; representative client/destructive acceptance remains later.

## 20. Distributed-runtime considerations
Operation ownership, concurrent balance changes, duplicate calls, reconnect, process death, and DB latency are handled through lease ownership, checksums, persistent revisions, idempotency, and quarantine outcomes; private representative acceptance remains `ES-V03`.

## 21. External-provider considerations
Uses the verified first-party `wsg138/EnthusiaCurrency` API; no reflection or invented runtime contract. Final standalone main is `b922c5af30860a6c205f9ee16b817349a7677cd0`.

## 22. Completion definition
Standalone and aggregate product PRs merge normally; all required checks/reviews/Pi pass; post-merge parity is true; metadata/merges/hashes recorded; temporary branches cleaned. Private representative destructive acceptance remains `ES-V03`.

## 23. Resume state
Resume Staff PR #133 at exact frozen product head `fbba02d10301b6bc6d80ada4ad7113f80ff95514`. First reconcile public Pi run `31692610056` and private run `31693194558` / job `94424932390`. If the private job has actually allocated and completed since this publication, inspect exact logs/evidence and require every applicable runtime, persistence/restart, cleanup, and public-transfer assertion. If it remains unallocated, do not rerun the same path until the trusted runner condition changes.

## 24. Last completed checkpoint
Every non-Pi gate is green. Coverage run `31692612391` passed the full aggregate Java 21 suite and runtime-JAR/provider-leak inspection; Staff Codacy `94423669170` is zero-issue; review debt is zero; Sentinel artifact run `31692612386` passed. The canonical public Pi build/bridge dispatched the exact private run, but no private runner has allocated.

## 25. Remaining checklist
Actual private Pi execution and public bridge cleanup/final result; normal Staff merge; post-merge `component_sync.py` parity; metadata `IN_SYNC`; containment and branch cleanup; canonical `COMPLETE` publication.

## 26. Known blockers
Private staging job `94424932390` in run `31693194558` is queued with `runner_id: 0`, empty runner name, and zero steps for required labels `self-hosted/Linux/ARM64/enthusia-staging`. No owner-approved infrastructure exception exists for ES-X02. Do not call this a Pi pass or product failure.

## 27. Current evidence
- Staff package start: `4831b1442e572914c86fd8e202e7de6f546868e2`.
- Currency package start: `922223cfff8c325e36f58b6af6adf6d74e4a5417`.
- Currency normal merges: #11 `6fd8947d3b2d2c470548f77f4fbf253fcc86b7e2`; #12 `7a9f67ed57de3d4eb7529c91a625efd017bfa88e`; #13 / final main `b922c5af30860a6c205f9ee16b817349a7677cd0`.
- Final standalone validation head: `a968f04b09c11dc1816f2b802626adbcef0f73c8`; exact branch-head CI run `31692395919` / job `94422400756`; 7 tests + shaded JAR; Codacy suite `85973637978`; CodeRabbit success; zero review threads.
- Staff PR #133 frozen head: `fbba02d10301b6bc6d80ada4ad7113f80ff95514`; mergeable, non-draft.
- Staff Coverage: run `31692612391` / job `94423135991`; 48.98% lines / 40.05% branches / 51.52% instructions; Paper SHA-256 `a142d0c30cbe4d085dea0901287f1d1bf9d84cb2143a0322091afb908342c6a6`; Velocity SHA-256 `c891d4744ed142edffa0352b4c20f39428fbc379c46313dbbe234878345ec1c7`; validation artifact `9178197820`.
- Staff static/review: Codacy `94423669170` zero issues; zero valid unresolved review threads; final CodeRabbit status success/rate-limited with no new finding.
- Sentinel artifact: run `31692612386` / job `94423077006`, artifact `9178016407`, success.
- Canonical Pi public run `31692610056`: exact public build success and private dispatch complete. Private run `31693194558` / job `94424932390`: queued, `runner_id: 0`, empty runner, zero steps; no Pi pass claimed.
- Handoff: `ai-agents/reports/package-handoffs/2026-08-13-es-x02-currency-provider-pi-blocked.md`.

## 28. Merge and synchronization record
Standalone is merged. Aggregate remains intentionally unmerged at `fbba02d...` pending actual canonical Pi. Pre-merge object identity is proven; post-merge parity and final metadata are pending and must not be predeclared.
'''

HANDOFF = '''# ES-X02 — EnthusiaCurrency destructive provider — Pi runner blocked handoff

Date: 2026-08-13
Status: `BLOCKED` / `PARKED_BLOCKED`
Classification: external infrastructure unavailable; no product failure; no exception approved

## Durable checkpoint

ES-X02 has completed standalone implementation/fixes and all non-Pi aggregate validation. Do not reopen old Codacy work or repeat superseded heads.

Final standalone Currency `main`: `b922c5af30860a6c205f9ee16b817349a7677cd0`, reached by normal merge commits from PRs #11, #12, and #13. Final standalone validation head `a968f04b09c11dc1816f2b802626adbcef0f73c8` passed exact branch-head Java 21 `mvn -B -ntp verify` (run `31692395919`, job `94422400756`, 7 tests, shaded JAR), Codacy suite `85973637978`, review, and mergeability.

Aggregate Staff product PR #133 is open/non-draft/mergeable at exact frozen head `fbba02d10301b6bc6d80ada4ad7113f80ff95514`. The mirrored Currency tree is Git-object-identical to standalone main for all standalone root objects; only aggregate-only `COMPONENT-METADATA.md` is extra. Do not modify or merge PR #133 until canonical Pi succeeds.

## Green final aggregate evidence

- Coverage run `31692612391`, job `94423135991`: full Java 21 clean multi-module build/tests, runtime JARs/provider-leak checks, JaCoCo, validation artifact, and Codacy coverage upload all passed. Coverage: 48.98% lines / 40.05% branches / 51.52% instructions. Validation artifact `9178197820`.
- Runtime JARs: Paper SHA-256 `a142d0c30cbe4d085dea0901287f1d1bf9d84cb2143a0322091afb908342c6a6`; Velocity SHA-256 `c891d4744ed142edffa0352b4c20f39428fbc379c46313dbbe234878345ec1c7`; provider leaks 0.
- Staff Codacy check `94423669170`: success, zero issues/annotations.
- Review: all valid findings fixed in standalone and re-imported; zero valid unresolved GitHub review threads. Final CodeRabbit status is success/rate-limited with no new finding.
- Sentinel artifact run `31692612386`, job `94423077006`: success; artifact `9178016407`.

## Canonical Pi blocker

Public canonical Pi run `31692610056` successfully built/verified the exact Staff runtime, uploaded the runtime package, published the bounded transient transfer, dispatched the private self-hosted workflow, and located private run `31693194558`.

Private job `94424932390` is named `Verify bridge and boot/restart runtime on Lincoln-PI-4` and requires labels `self-hosted`, `Linux`, `ARM64`, `enthusia-staging`. At publication it remains queued with:

- `runner_id: 0`
- `runner_name: ""`
- `runner_group_id: 0`
- `steps: []`

Therefore no private Paper boot/restart, migration, MariaDB/Flyway, persistence, shutdown scan, cleanup, process-reap, or evidence-upload step has executed. This is infrastructure-unavailable evidence only. It is not `PASS`, not a product failure, and cannot be substituted by the successful public build.

No explicit owner approval exists for `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED` on ES-X02. Do not self-approve or infer one from the package request.

## Exact resume procedure

1. Reconcile live Staff/Currency heads, PR #133, public run `31692610056`, and private run `31693194558` before doing anything else.
2. If private job `94424932390` has since allocated/completed, inspect the job/run logs and sanitized evidence. Require trusted runner identity plus all applicable runtime, database/migration, restart, persistence, guarded cleanup/process-reap, evidence sanitization, and public transfer-cleanup assertions. Do not infer success from a green public build or job title.
3. If the existing run timed out/cancelled while still `runner_id: 0`, retain that as non-passing zero-execution history. Only issue one fresh exact-head canonical Pi after concrete evidence that the trusted runner condition changed; do not repeatedly probe an unchanged unavailable runner.
4. On real Pi success, re-check PR #133 head remains exactly `fbba02d10301b6bc6d80ada4ad7113f80ff95514`, mergeable, Codacy-clean, review-clean, and that all required exact-head evidence still applies.
5. Merge PR #133 normally with expected head `fbba02d...`; no squash/rebase/force/auto-merge.
6. Run post-merge `tools/component-sync/component_sync.py compare` against exact Currency main `b922c5af30860a6c205f9ee16b817349a7677cd0`. Require no added/removed/modified product file and record hashes/manifests/merge SHAs.
7. Update component metadata to `IN_SYNC`, publish ES-X02 `COMPLETE` in registry/package/workspace/latest handoff, verify containment and safe branch cleanup, then stop. Do not start ES-X03/ES-X04 in the same worker.

Representative live destructive balances remain explicitly deferred to `ES-V03`. No production balances, private evidence rows, production authority, cutover, or issue #43 change belongs in ES-X02.
'''

LATEST = '''# Latest package-worker handoff

Current package: `ES-X02 — EnthusiaCurrency destructive provider`

Status: `BLOCKED` / `PARKED_BLOCKED`.

Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-13-es-x02-currency-provider-pi-blocked.md`.

All standalone work is merged normally; final Currency `main` is `b922c5af30860a6c205f9ee16b817349a7677cd0`. Aggregate Staff PR #133 is frozen at `fbba02d10301b6bc6d80ada4ad7113f80ff95514`, mergeable, and has passed all non-Pi hosted/static/review/Sentinel artifact gates. Its exact mirror is pre-merge object-identical to Currency main.

The only remaining package gate is canonical private Pi staging. Public run `31692610056` dispatched private run `31693194558`, but job `94424932390` remains queued with `runner_id: 0`, empty runner name, and zero executed steps for the trusted `Lincoln-PI-4` labels. No Pi pass or product failure is claimed, and no owner-approved infrastructure exception exists.

Resume ES-X02 before new dependent work when the trusted Pi runner condition changes. Reconcile the existing run first; require actual private execution plus public transfer cleanup before merging PR #133, then finish normal aggregate merge, post-merge `component_sync.py` parity, metadata/containment/branch cleanup, and canonical `COMPLETE` publication. Representative destructive balances remain deferred to `ES-V03`.
'''

Path('ai-agents/WORKSPACE-STATE.md').write_text(WORKSPACE, encoding='utf-8')
Path('ai-agents/work-packages/packages/ES-X02.md').write_text(PACKAGE, encoding='utf-8')
Path('ai-agents/reports/package-handoffs/2026-08-13-es-x02-currency-provider-pi-blocked.md').write_text(HANDOFF, encoding='utf-8')
Path('ai-agents/reports/agent-handoffs/latest.md').write_text(LATEST, encoding='utf-8')

registry_path = Path('ai-agents/work-packages/PACKAGE-REGISTRY.md')
registry = registry_path.read_text(encoding='utf-8')
registry = registry.replace('Last updated: 2026-08-12', 'Last updated: 2026-08-13', 1)

state_pattern = re.compile(
    r'`ES-X02 — EnthusiaCurrency destructive provider` is `BLOCKED` / `PARKED_BLOCKED`.*?'
    r'`ES-X01` remains independently `BLOCKED` / `PARKED_BLOCKED`\.',
    re.DOTALL,
)
state_replacement = (
    '`ES-X02 — EnthusiaCurrency destructive provider` is `BLOCKED` / `PARKED_BLOCKED` after completing '
    'standalone implementation/fixes and every non-Pi aggregate gate. Final Currency main is '
    '`b922c5af30860a6c205f9ee16b817349a7677cd0`; Staff product PR #133 is frozen at '
    '`fbba02d10301b6bc6d80ada4ad7113f80ff95514`, mergeable, static-clean, review-clean, and hosted-build-clean. '
    'Canonical Pi public run `31692610056` dispatched private run `31693194558`, but private job `94424932390` '
    'has no allocated runner (`runner_id: 0`, empty runner name, zero steps). No owner-approved infrastructure '
    'exception exists, so the package cannot merge or complete until actual private staging executes successfully. '
    '`ES-X01` remains independently `BLOCKED` / `PARKED_BLOCKED`.'
)
registry, count = state_pattern.subn(state_replacement, registry, count=1)
if count != 1:
    raise SystemExit(f'canonical-state ES-X02 paragraph matched {count} times')

row_pattern = re.compile(r'^\| `ES-X02` \| EnthusiaCurrency destructive provider \|.*$', re.MULTILINE)
row_replacement = '| `ES-X02` | EnthusiaCurrency destructive provider | `BLOCKED` | `PARKED_BLOCKED` | 110 | `ES-P08` | Staff PR #133 frozen `fbba02d...`; all non-Pi gates green; private Pi run `31693194558` / job `94424932390` queued with `runner_id: 0`, zero steps; no exception approved |'
registry, count = row_pattern.subn(row_replacement, registry, count=1)
if count != 1:
    raise SystemExit(f'ES-X02 registry row matched {count} times')

section_pattern = re.compile(
    r'## ES-X02 active blocked record\n\n.*?(?=\n## ES-P08 terminal record)',
    re.DOTALL,
)
section_replacement = '''## ES-X02 active blocked record

- Package start: Staff `main` `4831b1442e572914c86fd8e202e7de6f546868e2`; Currency `main` `922223cfff8c325e36f58b6af6adf6d74e4a5417`.
- Standalone Currency work is merged normally: PR #11 -> `6fd8947d3b2d2c470548f77f4fbf253fcc86b7e2`; PR #12 -> `7a9f67ed57de3d4eb7529c91a625efd017bfa88e`; PR #13 -> final main `b922c5af30860a6c205f9ee16b817349a7677cd0`.
- Final standalone validation head `a968f04b09c11dc1816f2b802626adbcef0f73c8` passed true branch-head Java 21 `mvn -B -ntp verify` in run `31692395919` / job `94422400756` with 7 tests and shaded runtime JAR; Codacy suite `85973637978` succeeded; CodeRabbit status succeeded; valid unresolved thread count was zero.
- Aggregate Staff PR #133 is open, non-draft, mergeable, and frozen at exact product head `fbba02d10301b6bc6d80ada4ad7113f80ff95514`. The exact mirror is Git-object-identical to Currency main `b922c5af...` for `.github`, `.gitignore`, `AGENTS.md`, `README.md`, `pom.xml`, and the complete `src` subtree; `COMPONENT-METADATA.md` is the sole aggregate-only file.
- Exact aggregate Coverage run `31692612391` / job `94423135991` passed the full Java 21 clean build/tests, runtime-JAR/provider-leak inspection, JaCoCo, validation artifact, and Codacy coverage upload. Coverage measured 48.98% lines, 40.05% branches, and 51.52% instructions. Staff Codacy check `94423669170` passed with zero issues. Final valid unresolved review-thread count is zero. Sentinel artifact run `31692612386` / job `94423077006` passed.
- Canonical Pi public run `31692610056` built/verified the exact Staff runtime, uploaded it, published the bounded transient transfer, dispatched private staging, and located private run `31693194558`.
- Private staging job `94424932390` (`Verify bridge and boot/restart runtime on Lincoln-PI-4`) is queued for labels `self-hosted`, `Linux`, `ARM64`, `enthusia-staging` with `runner_id: 0`, empty runner name, and zero executed steps. No Paper boot, restart, MariaDB/Flyway, persistence, cleanup, or product-validation step has executed in that private run. This is infrastructure-unavailable evidence, not a pass and not a product failure.
- No ES-X02 owner approval exists for `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED`; no exception is claimed or self-approved.
- Exact unblock condition: the trusted Pi runner becomes allocatable. Reconcile the existing public/private run first; if it has since completed, inspect and require all private runtime/persistence/restart/cleanup assertions plus public transfer cleanup. If it has not, run one fresh exact-head canonical Pi only after the infrastructure condition changes. Do not merge PR #133 without actual successful private staging or a separately explicit valid owner-approved exception.
- After Pi passes: merge Staff PR #133 normally at expected head `fbba02d...`, run required post-merge `tools/component-sync/component_sync.py` parity against Currency main `b922c5af...`, update component metadata to `IN_SYNC`, publish ES-X02 `COMPLETE`, verify containment/cleanup, and stop. Representative live destructive balances remain deferred to `ES-V03`.
- Canonical handoff: `ai-agents/reports/package-handoffs/2026-08-13-es-x02-currency-provider-pi-blocked.md`.
'''
registry, count = section_pattern.subn(section_replacement, registry, count=1)
if count != 1:
    raise SystemExit(f'ES-X02 active section matched {count} times')

registry_path.write_text(registry, encoding='utf-8')

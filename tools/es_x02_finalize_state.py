from pathlib import Path
import re

MERGE = "a3b6f2f7c1e9f6b7fe1667974aa0d050533605a9"
CURRENCY = "b922c5af30860a6c205f9ee16b817349a7677cd0"
HASH = "d6797acbd50bb6547ce724bff946974872795e9f2343c664c2c9e8bde28e5e2c"

workspace = f'''# Workspace state

Last updated: 2026-08-13

Live GitHub overrides stale records. Detailed package evidence remains in the registry, package records, canonical handoffs, and PR verification ledgers.

## Current routing

| Field | Value |
| --- | --- |
| Completed packages | `ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X02`, `ES-X05`, `ES-R01`, `ES-R02`, `ES-V01` |
| Active package | None. This worker completed exactly `ES-X02 — EnthusiaCurrency destructive provider` and stops. |
| ES-X02 final standalone | Currency `main` `{CURRENCY}` after normal PR merges #11/#12/#13. |
| ES-X02 final aggregate | Staff PR #133 merged normally as `{MERGE}` after exact-head build/static/review/Sentinel/canonical-Pi validation. |
| ES-X02 parity | `tools/component-sync/component_sync.py compare` returned `parity: true`; standalone/aggregate hash `{HASH}`; no added, missing, or modified product files. Component metadata is `IN_SYNC`. |
| Canonical Pi | Public run `31692610056` and private run `31693194558` / job `94424932390` passed on trusted `Lincoln-PI-4`; two Paper/storage-ready cycles, clean shutdown/failure scans, guarded disposable DB reset, sanitized evidence artifact `9178996362`, and public transfer cleanup all passed. |
| Next dependency-safe routing | `ES-X03 — EnthusiaMarket destructive provider` is now `READY` at priority 120. `ES-X04 — EnthusiaCommend reputation provider` is also dependency-complete and `READY` at priority 125. A new worker must reconcile live GitHub before selecting either; this worker does not start them. |
| Parked provider package | `ES-X01 — RoseChat provider and communication integration` remains `BLOCKED` / `PARKED_BLOCKED` on its unresolved supported repository/source/AGENTS contract and does not block unrelated ready work. |
| Production boundary | Representative destructive/latency/load acceptance remains `ES-V03`; issue #43 remains open/deferred and LiteBans remains authoritative. No production data, deployment, cutover, authority change, or representative destructive production test occurred in ES-X02. |
| Canonical handoff | `ai-agents/reports/package-handoffs/2026-08-13-es-x02-currency-provider-complete.md` |

## ES-X02 terminal boundary

ES-X02 is `COMPLETE`. Standalone and aggregate histories remain normal merge history, final component parity is exact, and the package does not authorize production balance changes or replace the later `ES-V03` acceptance package.
'''
Path('ai-agents/WORKSPACE-STATE.md').write_text(workspace, encoding='utf-8')

handoff = f'''# ES-X02 — EnthusiaCurrency destructive provider — complete handoff

Date: 2026-08-13
Status: `COMPLETE`

## Final repository state

- Standalone Currency package start: `922223cfff8c325e36f58b6af6adf6d74e4a5417`.
- Standalone normal merges: PR #11 -> `6fd8947d3b2d2c470548f77f4fbf253fcc86b7e2`; PR #12 -> `7a9f67ed57de3d4eb7529c91a625efd017bfa88e`; PR #13 -> final `main` `{CURRENCY}`.
- Final standalone validated head: `a968f04b09c11dc1816f2b802626adbcef0f73c8`; exact branch-head Java 21 run `31692395919` / job `94422400756`, 7 tests plus shaded JAR; Codacy suite `85973637978`; zero unresolved review threads.
- Aggregate Staff product head: `fbba02d10301b6bc6d80ada4ad7113f80ff95514`.
- Staff PR #133 merged normally as `{MERGE}`.

## Final aggregate validation

- Coverage/full build run `31692612391`, job `94423135991`: exact-head Java 21 full multi-module build/tests, runtime-JAR/provider-leak inspection, JaCoCo, validation artifact, and Codacy coverage upload passed. Coverage: 48.98% lines / 40.05% branches / 51.52% instructions. Paper JAR SHA-256 `a142d0c30cbe4d085dea0901287f1d1bf9d84cb2143a0322091afb908342c6a6`; Velocity JAR SHA-256 `c891d4744ed142edffa0352b4c20f39428fbc379c46313dbbe234878345ec1c7`; provider leaks 0; validation artifact `9178197820`.
- Staff Codacy `94423669170`: success, zero issues/annotations. All valid CodeRabbit findings were fixed; final unresolved review-thread count zero.
- Sentinel artifact run `31692612386`, job `94423077006`: success; artifact `9178016407`.
- Canonical Pi public run `31692610056` and bridge job `94424878487`: success. Private run `31693194558`, job `94424932390`, trusted runner `Lincoln-PI-4` / `runner_id: 2`: success. Sanitized summary recorded `result=PASS`, exact source `fbba02d...`, two starts, two storage-ready `SHADOW_MIGRATION` cycles, `failure_count=0`; both shutdowns exited 0; both failure scans were clean; disposable database identity/reset passed and removed 69 objects after test; unrelated host services remained active. Evidence artifact `9178996362`, digest `sha256:3bdf2a97d47678ffd9a2f5875268f451bc08a237b2b30b434add1c918dab4b72`. Public transient release/tag cleanup passed.

## Post-merge parity

`tools/component-sync/component_sync.py compare` was executed after aggregate merge against exact Staff merge `{MERGE}` and exact Currency main `{CURRENCY}`.

- `parity: true`
- aggregate hash: `{HASH}`
- standalone hash: `{HASH}`
- added to aggregate: none
- missing from aggregate: none
- modified: none

Evidence: `ai-agents/reports/package-handoffs/2026-08-13-es-x02-component-parity.json`.
Component metadata is `IN_SYNC` and records both final SHAs/hash.

## Review defects resolved during package

The package fixed valid findings including async durability/snapshot races, unverifiable rollback status, movement-lock mutation gaps, armor-stand transfers, lease timing/overflow, failed-CAS phantom state, revision overflow, dynamic schema SQL shape, aggregate PMD debt, and the critical Vault-missing startup continuation. A negative persisted financial revision deliberately remains fail-closed rather than silently normalized.

## Boundaries

No production/private balances, production data, credentials, cutover, deployment, issue #43 authority change, or representative destructive production acceptance occurred. Representative destructive/latency/load acceptance remains assigned to `ES-V03`.

## Next routing

This worker stops after ES-X02. `ES-X03` is dependency-complete and `READY` at priority 120; `ES-X04` is dependency-complete and `READY` at priority 125. `ES-X01` remains independently parked. The next worker must reconcile live GitHub and the registry before selection.
'''
Path('ai-agents/reports/package-handoffs/2026-08-13-es-x02-currency-provider-complete.md').write_text(handoff, encoding='utf-8')

latest = '''# Latest package-worker handoff

Current package: none; the previous worker completed `ES-X02 — EnthusiaCurrency destructive provider` and stopped.

Status: `ES-X02` is `COMPLETE`.

Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-13-es-x02-currency-provider-complete.md`.

Final Currency `main` is `b922c5af30860a6c205f9ee16b817349a7677cd0`; Staff product PR #133 merged normally as `a3b6f2f7c1e9f6b7fe1667974aa0d050533605a9`; canonical Pi passed; post-merge component parity is true with identical hash `d6797acbd50bb6547ce724bff946974872795e9f2343c664c2c9e8bde28e5e2c`; component metadata is `IN_SYNC`.

For the next sequential worker, reconcile live GitHub first. Absent a newly discovered higher-precedence actionable continuation, `ES-X03 — EnthusiaMarket destructive provider` is dependency-complete and `READY` at priority 120; `ES-X04` is also `READY` at priority 125. `ES-X01` remains parked on its unresolved supported repository/source contract. Do not treat ES-X02 completion as production destructive acceptance; that remains `ES-V03`.
'''
Path('ai-agents/reports/agent-handoffs/latest.md').write_text(latest, encoding='utf-8')

registry_path = Path('ai-agents/work-packages/PACKAGE-REGISTRY.md')
registry = registry_path.read_text(encoding='utf-8')
registry = registry.replace('Last updated: 2026-08-12', 'Last updated: 2026-08-13', 1)
registry = registry.replace(
    '`ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, and `ES-V01` are `COMPLETE`.',
    '`ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X02`, `ES-X05`, `ES-R01`, `ES-R02`, and `ES-V01` are `COMPLETE`.',
    1,
)
state_pattern = re.compile(
    r'`ES-X02 — EnthusiaCurrency destructive provider` is `BLOCKED` / `PARKED_BLOCKED`.*?'
    r'`ES-X01` remains independently `BLOCKED` / `PARKED_BLOCKED`\. `ES-X03`, `ES-X04`, `ES-V02`, `ES-V03`, `ES-A01`, and `ES-QA01` remain parked on their documented dependencies/external conditions\.',
    re.DOTALL,
)
state_replacement = (
    f'`ES-X02 — EnthusiaCurrency destructive provider` is `COMPLETE`. Standalone Currency final `main` is `{CURRENCY}`; '
    f'aggregate Staff PR #133 merged normally as `{MERGE}` after all required exact-head hosted/static/review/Sentinel/Pi gates passed; '
    f'post-merge `component_sync.py` parity is true with identical hash `{HASH}` and component metadata `IN_SYNC`. '
    '`ES-X01` remains independently `BLOCKED` / `PARKED_BLOCKED`. `ES-X03` and `ES-X04` are now dependency-complete and `READY`; '
    '`ES-V02`, `ES-V03`, `ES-A01`, and `ES-QA01` remain parked on their remaining documented dependencies/authorization conditions.'
)
registry, count = state_pattern.subn(state_replacement, registry, count=1)
if count != 1:
    raise SystemExit(f'canonical ES-X02 state paragraph matched {count} times')

replacements = {
    r'^\| `ES-X02` \| EnthusiaCurrency destructive provider \|.*$': f'| `ES-X02` | EnthusiaCurrency destructive provider | `COMPLETE` | — | 110 | `ES-P08` | Currency main `{CURRENCY[:8]}...`; Staff PR #133 merge `{MERGE[:8]}...`; canonical Pi passed; post-merge parity `{HASH[:8]}...`; `IN_SYNC` |',
    r'^\| `ES-X03` \| EnthusiaMarket destructive provider \|.*$': '| `ES-X03` | EnthusiaMarket destructive provider | `PLANNED` | `READY` | 120 | `ES-P08`, `ES-X02` | dependencies complete; unassigned; no branch/PR/handoff yet |',
    r'^\| `ES-X04` \| EnthusiaCommend reputation provider \|.*$': '| `ES-X04` | EnthusiaCommend reputation provider | `PLANNED` | `READY` | 125 | `ES-P08`, `ES-X02` | dependencies complete; unassigned; no branch/PR/handoff yet |',
}
for pattern, replacement in replacements.items():
    registry, count = re.subn(pattern, replacement, registry, count=1, flags=re.MULTILINE)
    if count != 1:
        raise SystemExit(f'registry row pattern matched {count} times: {pattern}')

section_pattern = re.compile(r'## ES-X02 active blocked record\n\n.*?(?=\n## ES-P08 terminal record)', re.DOTALL)
section_replacement = f'''## ES-X02 terminal record

- Package start: Staff `4831b1442e572914c86fd8e202e7de6f546868e2`; Currency `922223cfff8c325e36f58b6af6adf6d74e4a5417`.
- Currency PRs #11/#12/#13 merged normally; final standalone main `{CURRENCY}`. Final standalone validated head `a968f04b09c11dc1816f2b802626adbcef0f73c8` passed exact branch-head Java 21 run `31692395919` / job `94422400756`, 7 tests + shaded JAR, Codacy suite `85973637978`, and zero unresolved review threads.
- Staff frozen product head `fbba02d10301b6bc6d80ada4ad7113f80ff95514` passed Coverage/full build `31692612391` / job `94423135991`, Staff Codacy `94423669170`, zero unresolved review threads, Sentinel artifact `31692612386` / job `94423077006`, and canonical Pi public run `31692610056` correlated with private run `31693194558` / job `94424932390` on trusted `Lincoln-PI-4`.
- Private Pi sanitized evidence: `result=PASS`, exact source, two Paper starts, two storage-ready `SHADOW_MIGRATION` cycles, clean shutdown/failure scans, disposable DB reset, unrelated host-service preservation, artifact `9178996362` digest `sha256:3bdf2a97d47678ffd9a2f5875268f451bc08a237b2b30b434add1c918dab4b72`. Public bridge cleanup/result passed.
- Staff PR #133 merged normally as `{MERGE}`.
- Required post-merge `tools/component-sync/component_sync.py compare` against Currency `{CURRENCY}` returned `parity: true`, aggregate hash = standalone hash = `{HASH}`, with no added, missing, or modified product files. Component metadata is `IN_SYNC`.
- Representative live destructive balances remain assigned to `ES-V03`; ES-X02 changed no production authority/data/cutover state.
- Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-13-es-x02-currency-provider-complete.md`.
'''
registry, count = section_pattern.subn(section_replacement, registry, count=1)
if count != 1:
    raise SystemExit(f'ES-X02 active section matched {count} times')

next_pattern = re.compile(r'## Next sequential action\n\n.*\Z', re.DOTALL)
next_replacement = '''## Next sequential action

No package is active. A new sequential worker must reconcile live GitHub and classify every incomplete package. Absent a newly discovered higher-precedence `ACTIONABLE_CONTINUATION`, select `ES-X03 — EnthusiaMarket destructive provider`, now dependency-complete and `READY` at priority 120. `ES-X04 — EnthusiaCommend reputation provider` is also `READY` at priority 125. `ES-X01` remains parked on the unresolved supported RoseChat repository/source contract. Work exactly one package, publish durable state, and stop.
'''
registry, count = next_pattern.subn(next_replacement, registry, count=1)
if count != 1:
    raise SystemExit(f'next sequential action matched {count} times')
registry_path.write_text(registry, encoding='utf-8')

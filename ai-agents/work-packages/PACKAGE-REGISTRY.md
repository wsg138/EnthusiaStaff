# Package registry

Last updated: 2026-08-13

Live GitHub overrides stale text. Detailed historical evidence remains in package files and canonical handoffs; this registry is the current routing authority.

## Rules

- Classify every incomplete package before selection and work exactly one package per worker.
- Existing `ACTIONABLE_CONTINUATION` work takes priority over newly `READY` work.
- Use normal merge commits only; never relabel missing, skipped, cancelled, superseded, queued, timed-out, wrong-revision, resource-gated, merge-ref-only, or failed validation as passing evidence.
- Do not weaken staging, provenance, migration, review, static-analysis, privacy, or production-authority boundaries.
- Issue #43 remains open/deferred and LiteBans remains authoritative until separately approved.
- A parked package does not block selection of an unrelated dependency-complete `READY` package.
- Required package gates come from the authoritative package contract at selection, current validation policy, actually applicable configured checks, and explicit owner direction. A worker cannot create a new blocker merely by adding an optional/diagnostic gate to later tracking text.

## Canonical current state

`ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P08`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, and `ES-V01` are `COMPLETE`.

`ES-P08 — Item confiscation and restoration` completed through implementation PR #128. Frozen executable-validation head `27b20bb56e540161f695e624916f91620261457d` passed the package's required executable gates. Final synchronized head `f398fd5bd8bbf4ec62f7f05313dd082948c2561b` differed from the frozen product head only in eight `ai-agents` Markdown process/state/handoff files, passed the applicable documentation/static/review gates, merged normally, is exactly contained with zero file delta, and its temporary implementation branch is deleted.

The later live Sentinel restart attempts remain explicit non-passing diagnostic history. The canonical ES-P08 contract at package start did not require that independent restart and explicitly deferred representative destructive/load acceptance to `ES-V03`; the worker-added blocker was corrected under `VALIDATION-POLICY.md` without relabeling any failed diagnostic as a pass.

`ES-X02 — EnthusiaCurrency destructive provider` is `BLOCKED` / `PARKED_BLOCKED` after completing standalone implementation/fixes and every non-Pi aggregate gate. Final Currency main is `b922c5af30860a6c205f9ee16b817349a7677cd0`; Staff product PR #133 is frozen at `fbba02d10301b6bc6d80ada4ad7113f80ff95514`, mergeable, static-clean, review-clean, and hosted-build-clean. Canonical Pi public run `31692610056` dispatched private run `31693194558`, but private job `94424932390` has no allocated runner (`runner_id: 0`, empty runner name, zero steps). No owner-approved infrastructure exception exists, so the package cannot merge or complete until actual private staging executes successfully. `ES-X01` remains independently `BLOCKED` / `PARKED_BLOCKED`. `ES-X03`, `ES-X04`, `ES-V02`, `ES-V03`, `ES-A01`, and `ES-QA01` remain parked on their documented dependencies/external conditions.

## Canonical package index

| ID | Title | Status | Classification | Priority | Dependencies | Assignment / live work |
| --- | --- | --- | --- | ---: | --- | --- |
| `ES-P01` | Exact-sanction appeal isolation | `COMPLETE` | — | 10 | — | merged PR #68 |
| `ES-R01` | Billing-independent staging bridge recovery | `COMPLETE` | — | 15 | — | canonical public→private staging route proven |
| `ES-R02` | Report integration fixture clock recovery | `COMPLETE` | — | 16 | — | merged PR #103 |
| `ES-P02` | Runtime database recovery and Velocity reload | `COMPLETE` | — | 20 | `ES-P01` | merged PR #70 |
| `ES-P03` | Bedrock identity correctness | `COMPLETE` | — | 30 | `ES-P02` | merged PR #75 |
| `ES-X05` | Website UX, authentication, and appeals | `COMPLETE` | — | 35 | `ES-P01` | merged PR #74 |
| `ES-P04` | Staff-mode operational tools | `COMPLETE` | — | 40 | `ES-P03` | merged PR #79 |
| `ES-P07` | Inventory and Ender editing runtime completion | `COMPLETE` | — | 45 | `ES-P02` | frozen `70b279998bbcc9a3ddd68b5f6e060d5a60662323`; PR #112 merged normally as `c96b0a2047e2e720bb4f18d32cf8c254d0302508`; contained; branch cleaned |
| `ES-P05` | Report evidence and staff workflow completion | `COMPLETE` | — | 50 | `ES-P03`, `ES-P04` | merged PR #81 |
| `ES-P09` | Alt and network-identity completion | `COMPLETE` | — | 55 | `ES-P03` | merged PR #84 |
| `ES-P06` | Discord notification delivery completion | `COMPLETE` | — | 60 | `ES-P05` | frozen `7e21edb1d32a75727dc65df826f9de964adcfff3`; PR #115 merged normally as `d78a5165493f810dbb3fd4d11e5e9d4b80ffed71`; contained; branch cleaned |
| `ES-P08` | Item confiscation and restoration | `COMPLETE` | — | 70 | `ES-P07` | frozen executable head `27b20bb56e540161f695e624916f91620261457d`; final synchronized head `f398fd5bd8bbf4ec62f7f05313dd082948c2561b`; PR #128 merged normally; contained; branch cleaned |
| `ES-P10` | Cheat tester and fake-entity system | `COMPLETE` | — | 80 | `ES-P04` | merged PR #86 |
| `ES-P11` | Fake-base generation and cleanup | `COMPLETE` | — | 90 | `ES-P10` | merged PR #88 |
| `ES-X01` | RoseChat provider and communication integration | `BLOCKED` | `PARKED_BLOCKED` | 100 | `ES-P03`, `ES-P04`, `ES-P05` | supported integration repository/default branch/source/AGENTS contract unresolved |
| `ES-X02` | EnthusiaCurrency destructive provider | `BLOCKED` | `PARKED_BLOCKED` | 110 | `ES-P08` | Staff PR #133 frozen `fbba02d...`; all non-Pi gates green; private Pi run `31693194558` / job `94424932390` queued with `runner_id: 0`, zero steps; no exception approved |
| `ES-X03` | EnthusiaMarket destructive provider | `PLANNED` | `PARKED_BLOCKED` | 120 | `ES-P08`, `ES-X02` | ES-X02 incomplete |
| `ES-X04` | EnthusiaCommend reputation provider | `PLANNED` | `PARKED_BLOCKED` | 125 | `ES-P08`, `ES-X02` | ES-X02 incomplete |
| `ES-V01` | Private LiteBans representative-data verification | `COMPLETE` | — | 200 | — | merged PR #110; terminal evidence retained |
| `ES-V02` | Distributed and Java/Bedrock staging | `DEFERRED` | `PARKED_BLOCKED` | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | ES-X01, ES-X03 and ES-X04 remain incomplete |
| `ES-V03` | Destructive, latency, and load acceptance | `DEFERRED` | `PARKED_BLOCKED` | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | provider dependencies remain incomplete; representative destructive/load acceptance lives here |
| `ES-A01` | LiteBans cutover acceptance | `DEFERRED` | `PARKED_BLOCKED` | 300 | `ES-V01`, `ES-V02`, `ES-V03` | ES-V02/ES-V03 plus owner authorization and issue #43 required |
| `ES-QA01` | Final repository and workflow audit | `PLANNED` | `PARKED_BLOCKED` | 400 | `ES-A01` | dependency blocked |


## ES-X02 active blocked record

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

## ES-P08 terminal record

- Package start: `main` `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`.
- Frozen executable-validation head: `27b20bb56e540161f695e624916f91620261457d`.
- Final synchronized head: `f398fd5bd8bbf4ec62f7f05313dd082948c2561b`.
- Implementation PR: #128, merged normally. GitHub verification proves the synchronized head is one merge commit behind resulting `main`, zero behind in containment terms, with zero file differences; the temporary implementation branch is deleted.
- Documentation-only blocker publication PR #129 remains historical evidence of the temporary Sentinel-blocked classification; it changed no product code.
- Hosted frozen-head proof: Wiki run `31555952998` passed; Coverage run `31555953013` / job `93988340387` passed Java 21 full build/tests with MariaDB/Testcontainers, warnings-as-errors, runtime JAR/provider-leak checks, aggregate JaCoCo, and Codacy coverage upload. Aggregate coverage was 48.99% lines, 40.07% branches, 51.52% instructions.
- Codacy static check `93988413158` passed with zero issues; coverage variation +0.2% against the -1.0% target; diff coverage 74.5% with no configured diff gate.
- Frozen-head review: all valid CodeRabbit findings are addressed and all threads resolved. Exact-head manual review found no additional valid release blocker; valid unresolved review-thread count was zero. The docstring-coverage UI warning is advisory and is not counted as a repository-gate pass.
- Exact Sentinel artifact run `31555953004` passed.
- Canonical Pi: public run `31555950970` attempt 1 and correlated private run `31556350997` / job `93989465759` passed on trusted `Lincoln-PI-4` with exact provenance, V1–V18 fresh migration, V18 restart no-op, two storage-ready `SHADOW_MIGRATION` cycles, clean shutdown/failure scans, sanitized evidence, guarded disposable-database cleanup, and public transfer cleanup.
- State-only synchronization: exact compare `27b20bb...` → `f398fd5...` contained only eight `ai-agents` Markdown process/state/handoff files. The final synchronization head passed Wiki, Codacy static, CodeRabbit review, remained mergeable, and had zero valid unresolved review threads.
- Live Sentinel diagnostics are not passed: job `150` ended at `RESTART_CYCLE_1_RESOURCE_GATE_FAILED` (80.3 C >= 80.0 C); job `151` timed out; job `153` completed restart cycle 1 then ended `RESTART_CYCLE_2_RESOURCE_GATE_FAILED` (81.8 C >= 80.0 C). None is relabeled. The correction was gate applicability: the original ES-P08 contract did not require this independent live restart, while destructive representative acceptance was deferred to ES-V03.
- V18 remains immutable; ES-P08 added no migration. Issue #43 remains open/deferred and LiteBans remains authoritative. No production data, deployment, shadow window, cutover, authority change, source rewrite, or second package implementation occurred.
- Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-11-es-p08-item-confiscation-complete.md`.

## ES-P06 terminal record

- Original/pre-merge `main`: `449461b410c0b06d27bfd98a2940023aa0d9913f`.
- Final frozen implementation head: `7e21edb1d32a75727dc65df826f9de964adcfff3`.
- Hosted exact-head validation: Validate Wiki run `31450684263` passed; Coverage run `31450684287` attempt 2 / job `93657195445` passed Java 21 full build/tests with MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, aggregate JaCoCo, validation artifact, and Codacy coverage upload. Aggregate coverage was 47.56% lines, 38.74% branches, and 50.23% instructions.
- Hosted validation artifact `9086657350`, digest `sha256:329ed42f108776e19713bac57dc36b47020f74dd78571296fc9a28cfde0be248`.
- Runtime JARs: Paper SHA-256 `74fbc2f1ac487a4191ccc5d83b6d7c68ba857dd4c2fd8b060c13fff138c0fe33`; Velocity SHA-256 `e3705f7729d3e1e48797635d4c88345aea68e9e3845bce47547c6879ab9920e2`; provider API leaks 0.
- Exact-head Codacy static check `93654428681` passed with zero findings. Diff coverage was 72.52%; coverage variation +0.43% against the -1.0% target.
- Review: three substantive CodeRabbit findings were fixed with regression coverage and all threads resolved. The final incremental CodeRabbit rerun was rate-limited and is not counted as a pass. An exact-head manual review found no additional valid defect; final valid unresolved thread count is zero.
- Non-passing hosted history: Coverage attempt 1 / job `93654716868` failed in an untouched punishment-request alert concurrency test on a transient MariaDB race; that attempt remains non-passing. The unchanged exact SHA passed the full rerun in attempt 2.
- Canonical Pi: public run `31450682744` attempt 1, build job `93654251245`, and bridge job `93655372240` succeeded. Correlated private run `31451077909` / job `93655393387` succeeded on trusted `Lincoln-PI-4` with exact source/provenance verification, two Paper/storage-ready `SHADOW_MIGRATION` cycles, V1–V18 applied on cycle 1, schema v18 current/no-op on restart, clean shutdown/failure scans, and guarded disposal of 69 database objects. Sanitized evidence artifact `9086623670`, digest `sha256:98627335ce81a862a2d77287548a03d2ef85e238c8d14e5b4e932d471b230ce7`.
- The configured Sentinel PR artifact-build check passed on the exact head; no live Sentinel restart is claimed or substituted for canonical staging because ES-P06's changed runtime is Velocity-side.
- No production Discord route was contacted; isolated fake/in-memory delivery was used for route/body behavior. ES-P06 added no migration; V18 remains immutable.
- PR #115 merged by normal merge commit `d78a5165493f810dbb3fd4d11e5e9d4b80ffed71`. Its parents are pre-merge `main` and the frozen feature head; both feature head and merge commit share tree `8f7b7dae841779af573012df3e30fb6302580654`. `package/es-p06-discord-delivery` is deleted/404.
- Issue #43 remains open/deferred and LiteBans remains authoritative. No production data, deployment, shadow window, cutover, punishment-authority change, source rewrite, or unrelated package implementation occurred.
- Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-10-es-p06-discord-delivery-complete.md`.

## ES-P07 terminal record

- Original package start: `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`.
- Final frozen implementation head: `70b279998bbcc9a3ddd68b5f6e060d5a60662323`.
- Hosted exact-head validation: Java 21 full build/tests with MariaDB/Testcontainers, runtime-JAR/provider-leak inspection, Wiki, aggregate JaCoCo, and Codacy upload all passed. Aggregate coverage was 47.14% lines, 38.24% branches, 49.80% instructions; validation artifact `9080140711`, digest `sha256:7c8a1df6bd5deaa2719febd588ab0c39925728291b475088f5e601e5b1e3624a`.
- Exact-head Codacy: zero static annotations; diff coverage 31.58%; coverage variation -0.04% against the -1.0% target.
- Review: CodeRabbit success and zero valid unresolved threads. A late request to place the final commit's own SHA inside files belonging to that commit was dispositioned as self-referential and invalid; PR metadata already held the literal frozen SHA, and the thread was resolved without changing the validated tree.
- Sentinel: exact artifact `9079917694`, digest `sha256:5e67feb5a4461cc468289a6ef063ad66b3b22c08f70dede750a32f501ab72132`; restart job 85 reached terminal `PAPER_RESTART_OK` with two clean readiness/start-stop cycles.
- Canonical Pi: fresh public run `31437103701` attempt 1 succeeded end-to-end and removed its transient public transfer. Correlated private run `31437719313` / job `93615505782` succeeded on trusted `Lincoln-PI-4` runner ID 2 with exact source/provenance verification, two Paper/storage-ready `SHADOW_MIGRATION` cycles, V1–V18 applied on the first cycle, schema v18 current/no-op on restart, clean shutdown/failure scans, and guarded post-test database cleanup. Sanitized evidence artifact `9082068813`, digest `sha256:db97da3300f462a091986dd8f752bd5e7fb374983bc6a2da8eaec94a96a28ea2`.
- Earlier final-head private attempts remain non-passing evidence: one failed before Paper on transient bridge HTTP 404; another correctly rejected a rerun-attempt manifest mismatch caused by reuse of the attempt-1 build artifact. Provenance was not weakened.
- PR #112 merged by normal merge commit `c96b0a2047e2e720bb4f18d32cf8c254d0302508`. Containment is exact: one merge commit ahead, zero behind, zero file differences. `package/es-p07-inventory-runtime` is deleted/404.
- V18 remains the immutable migration ceiling; ES-P07 added no migration. Issue #43 remains open/deferred; LiteBans remains authoritative. No production data, deployment, shadow window, cutover, punishment-authority change, source rewrite, or unrelated package work occurred.
- Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-10-es-p07-inventory-runtime-complete.md`.

## Next sequential action

No package is active. A new sequential worker must reconcile live GitHub and classify every incomplete package. Absent a newly discovered higher-precedence `ACTIONABLE_CONTINUATION`, select `ES-X02 — EnthusiaCurrency destructive provider`, which is dependency-complete and `READY` at priority 110. Work exactly ES-X02, publish durable state, and stop. `ES-X01` remains parked on the unresolved supported RoseChat repository/source contract and must not block ES-X02.

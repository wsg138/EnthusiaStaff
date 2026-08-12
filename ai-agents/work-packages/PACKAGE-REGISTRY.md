# Package registry

Last updated: 2026-08-11

Live GitHub overrides stale text. Detailed historical evidence remains in package files and canonical handoffs; this registry is the current routing authority.

## Rules

- Classify every incomplete package before selection and work exactly one package per worker.
- Existing `ACTIONABLE_CONTINUATION` work takes priority over newly `READY` work.
- Use normal merge commits only; never relabel missing, skipped, cancelled, superseded, wrong-revision, or failed validation as passing evidence.
- Do not weaken staging, provenance, migration, review, static-analysis, privacy, or production-authority boundaries.
- Issue #43 remains open/deferred and LiteBans remains authoritative until separately approved.

## Canonical current state

`ES-P01`, `ES-P02`, `ES-P03`, `ES-P04`, `ES-P05`, `ES-P06`, `ES-P07`, `ES-P09`, `ES-P10`, `ES-P11`, `ES-X05`, `ES-R01`, `ES-R02`, and `ES-V01` are `COMPLETE`.

`ES-P08 — Item confiscation and restoration` is `ACTIVE` / `ACTIONABLE_CONTINUATION` on `package/es-p08-item-confiscation`. It was selected only after live reconciliation showed no higher-priority incomplete-package continuation and confirmed ES-P07 complete. Exact package start is `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`; draft implementation PR #128 is the canonical live work. Current implementation adds Founder-authorized, audited, idempotent retry for one coherent case-linked quarantined item operation while preserving normal checksum/revision recovery as the only path that may apply inventory changes. Final exact-head validation is not yet claimed.

`ES-X01` remains `BLOCKED` / `PARKED_BLOCKED` because the supported RoseChat standalone repository, default branch, source, and AGENTS files required for integration remain unresolved. `ES-X02`, `ES-X03`, `ES-X04`, `ES-V02`, `ES-V03`, `ES-A01`, and `ES-QA01` remain parked on their documented dependencies/external conditions. This worker must complete exactly ES-P08 and must not activate a downstream package.

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
| `ES-P08` | Item confiscation and restoration | `ACTIVE` | `ACTIONABLE_CONTINUATION` | 70 | `ES-P07` | branch `package/es-p08-item-confiscation`; draft PR #128; start `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`; final validation pending |
| `ES-P10` | Cheat tester and fake-entity system | `COMPLETE` | — | 80 | `ES-P04` | merged PR #86 |
| `ES-P11` | Fake-base generation and cleanup | `COMPLETE` | — | 90 | `ES-P10` | merged PR #88 |
| `ES-X01` | RoseChat provider and communication integration | `BLOCKED` | `PARKED_BLOCKED` | 100 | `ES-P03`, `ES-P04`, `ES-P05` | supported integration repository/default branch/source/AGENTS contract unresolved |
| `ES-X02` | EnthusiaCurrency destructive provider | `PLANNED` | `PARKED_BLOCKED` | 110 | `ES-P08` | dependency ES-P08 is active/incomplete |
| `ES-X03` | EnthusiaMarket destructive provider | `PLANNED` | `PARKED_BLOCKED` | 120 | `ES-P08`, `ES-X02` | dependencies incomplete |
| `ES-X04` | EnthusiaCommend reputation provider | `PLANNED` | `PARKED_BLOCKED` | 125 | `ES-P08`, `ES-X02` | dependencies incomplete |
| `ES-V01` | Private LiteBans representative-data verification | `COMPLETE` | — | 200 | — | merged PR #110; terminal evidence retained |
| `ES-V02` | Distributed and Java/Bedrock staging | `DEFERRED` | `PARKED_BLOCKED` | 250 | `ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, `ES-X05` | ES-X01, ES-X03 and ES-X04 remain incomplete |
| `ES-V03` | Destructive, latency, and load acceptance | `DEFERRED` | `PARKED_BLOCKED` | 260 | `ES-P08`, `ES-X02`, `ES-X03`, `ES-X04` | ES-P08 active; provider dependencies incomplete |
| `ES-A01` | LiteBans cutover acceptance | `DEFERRED` | `PARKED_BLOCKED` | 300 | `ES-V01`, `ES-V02`, `ES-V03` | ES-V02/ES-V03 plus owner authorization and issue #43 required |
| `ES-QA01` | Final repository and workflow audit | `PLANNED` | `PARKED_BLOCKED` | 400 | `ES-A01` | dependency blocked |

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

## ES-P08 active record

- Exact package start: `7c032c6af32f7281f518a01ed6dc3b0252cabb5b`.
- Branch: `package/es-p08-item-confiscation`; draft PR #128.
- Live migration ceiling at selection: V18; no migration has been added because `recovery_quarantine` already contains `resolved_at`, `resolved_by`, and `resolution_json`.
- Existing item foundations retained: durable profiles/operations/patches, network leases/fencing, nested paths/fingerprints, before snapshots, confiscated-asset snapshots, restoration reservation/finalization, bounded work, and restart/login patch recovery.
- New bounded recovery authority: Founder-only `/case recoveritems <case-id>` uses `enthusiastaff.owner.recovery` plus service-level `RESTORE_ASSETS` policy; persistence rechecks item operation type, case-target/profile binding, pair state/profile/fence coherence and live leases before requeue. The command itself never applies inventory.
- Retry resolution is durable and idempotent: the quarantine stores resolver/time/metadata, an append-only audit uses operation+fence idempotency, and a failed retry reopens the quarantine resolution fields while preserving prior authorizations in audit.
- New tests cover authorization, generic-operation exclusion, duplicate retry, competing lease, paired-state divergence rollback, no inventory revision change on authorization, and re-quarantine/re-recovery. Existing restoration integrity tests cover exact case/player/profile/scope reservation binding; existing item code retains nested path/fingerprint handling.
- Diagnostic-only evidence: pre-doc head `3c27bcc7e1d0ec67295bb3a5c1225defb4803e02` passed Sentinel's Java 21 Paper artifact build in run `31553184745`; superseded Coverage was cancelled. Neither is final ES-P08 evidence.
- Final exact-head hosted/static/review/Sentinel/Pi evidence, merge containment, branch cleanup and terminal publication remain pending.

## Next sequential action

Resume and complete ES-P08 on PR #128. Do not select or activate ES-X02 or any other package while ES-P08 is active. After ES-P08 becomes terminally complete, this worker must publish that state and stop; a later worker must reconcile live GitHub before selecting the next package.
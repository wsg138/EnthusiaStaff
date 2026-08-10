# `ES-P07` — Inventory and Ender editing runtime completion

## 1. Package identity
`ES-P07`; Internal; primary `COMP-STAFF`; priority 45.

## 2. Status
`COMPLETE`.

Final frozen implementation head: `70b279998bbcc9a3ddd68b5f6e060d5a60662323`.
Implementation PR: `#112`.
Normal implementation merge: `c96b0a2047e2e720bb4f18d32cf8c254d0302508`.
Original starting `main`: `17fb50d02fdc35cffd1cbdc63e28f72cffd88315`.
Pre-merge target `main`: `2d8fcf27b0bac980211149ae8f7f4e7798998ee5`.
Canonical terminal handoff: `ai-agents/reports/package-handoffs/2026-08-10-es-p07-inventory-runtime-complete.md`.

## 3. Objective
Complete safe online/offline inventory and Ender viewing/editing, revisions, locks, queued patches, server scopes, and runtime recovery.

## 4. Completed behavior
- Exact logical dirty-slot writes replace stale whole-image player/Ender replacement.
- The complete deduplicated slot set validates before player inventory is obtained or mutated.
- Complete serialized inventory snapshots are bounded at 32 MiB; the per-item bound remains 16 MiB.
- Same-operation `APPLYING` lease replay is idempotent with the same fence while competing operations remain excluded.
- Login/recovery locks guard damage/resurrection, consume/durability/mending, entity interaction, and Paper pick/equipment-swap paths.
- Existing authoritative target lookup, entity-thread scheduling, offline queued patches, revision/checksum fencing, Velocity backend-switch locks, nested item serialization, and lifecycle ownership remain intact.
- Direct tests cover aggregate bounds, mixed valid/invalid slots, MariaDB same-owner lease replay, command permission separation, `invsee`/`endersee` workflow wiring, entity scheduling, GUI edit gating, and explicit non-null default-false permission metadata.
- Inventory safety/Wiki documentation is current.

## 5. Exact-head validation
Frozen head `70b279998bbcc9a3ddd68b5f6e060d5a60662323` passed all required development gates:

- Wiki: run `31433081524`, job `93600876249` — success.
- Hosted build/coverage: run `31433081255`, successful exact-head job `93601334744` — Java 21 full build/tests, MariaDB/Testcontainers, runtime-JAR integrity/provider-leak inspection, aggregate JaCoCo, validation artifact, and Codacy coverage upload all succeeded.
- Aggregate JaCoCo: 47.14% lines, 38.24% branches, 49.80% instructions.
- Paper JAR: 9,148,983 bytes, SHA-256 `814697276458912c1bbb8fe5aaf98952fa7f1eb6c625b3195deaff598538aa8e`, provider API leaks 0.
- Velocity JAR: 7,891,242 bytes, SHA-256 `12ba1199024cfc9cd228307742dc3cc4c8d25b017bb0d117bb8b97c3e99b8c44`, provider API leaks 0.
- Validation artifact `9080140711`, digest `sha256:7c8a1df6bd5deaa2719febd588ab0c39925728291b475088f5e601e5b1e3624a`.
- Codacy static: success with zero annotations; diff coverage 31.58%; coverage variation -0.04% against the -1.0% target.
- CodeRabbit: success; zero valid unresolved review threads after dispositioning the late self-referential SHA-record request without changing the frozen tree.
- Sentinel artifact `9079917694`, digest `sha256:5e67feb5a4461cc468289a6ef063ad66b3b22c08f70dede750a32f501ab72132`; exact restart job 85 reached terminal `PAPER_RESTART_OK` after two clean readiness/start-stop cycles.
- Canonical fresh Pi public run `31437103701`, attempt 1, succeeded end-to-end. Correlated private run `31437719313` / job `93615505782` succeeded on trusted `Lincoln-PI-4` runner ID 2. Exact bridge provenance/checksum passed; two Paper/storage-ready cycles entered `SHADOW_MIGRATION`; cycle 1 applied V1–V18; cycle 2 verified schema v18 current/no-op; both shutdowns and failure scans passed; guarded post-test cleanup passed. Sanitized evidence artifact `9082068813`, digest `sha256:db97da3300f462a091986dd8f752bd5e7fb374983bc6a2da8eaec94a96a28ea2`.

## 6. Non-passing/superseded evidence
All validation tied to earlier candidate heads is non-final. Two final-head private attempts also remain explicitly non-passing: one failed before Paper on a transient bridge HTTP 404; a rerun then correctly rejected an attempt-number manifest mismatch because GitHub reused the successful attempt-1 build artifact. The provenance check was not weakened. A new run ID/attempt-1 event produced the passing canonical evidence above.

## 7. Review disposition
CodeRabbit's late request to place `70b279998bbcc9a3ddd68b5f6e060d5a60662323` inside tracked files belonging to that same commit was dispositioned as invalid/self-referential: changing those files would create a different commit SHA and immediately stale the inserted value. PR metadata and GitHub HEAD already recorded the literal frozen SHA. The review thread was resolved with this rationale and no tracked change was made after freeze.

## 8. Merge, containment, and cleanup
PR #112 merged using the required normal merge method as `c96b0a2047e2e720bb4f18d32cf8c254d0302508`.

The merge commit has exactly two parents:
1. `2d8fcf27b0bac980211149ae8f7f4e7798998ee5` — pre-merge `main`.
2. `70b279998bbcc9a3ddd68b5f6e060d5a60662323` — frozen validated feature head.

Comparison from the frozen feature head to the merge commit is one commit ahead, zero behind, with zero file differences. GitHub auto-deleted `package/es-p07-inventory-runtime`; the ref returns 404. External component parity is not applicable to internal `COMP-STAFF` scope.

## 9. Boundaries
- Item confiscation/restoration remains `ES-P08`.
- External destructive providers remain `ES-X02`, `ES-X03`, and `ES-X04`.
- Broader representative multi-backend, large/private-inventory and Java/Bedrock acceptance remains `ES-V02`.
- V18 remains the immutable migration ceiling; ES-P07 added no migration or migration repair.
- Issue #43 remains open/deferred and LiteBans authoritative.
- No production data, deployment, shadow window, cutover, punishment-authority change, source rewrite, or unrelated package implementation occurred.

## 10. Dependency result and stop state
ES-P07 completion makes `ES-P08` dependency-complete and `READY` at priority 70. `ES-P06` remains `READY` at priority 60. `ES-X01` remains `BLOCKED` / `PARKED_BLOCKED` on the unresolved supported RoseChat repository/source contract. Downstream packages remain dependency-blocked as recorded in the canonical registry.

This worker does not activate another package. A new sequential worker must reconcile live GitHub and route normally; absent a new actionable continuation, current priority places ES-P06 before ES-P08.

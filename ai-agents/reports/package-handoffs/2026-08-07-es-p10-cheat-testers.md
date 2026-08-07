# ES-P10 cheat tester and fake-entity handoff

Date: 2026-08-07
Package: `ES-P10 — Cheat tester and fake-entity system`
Worker: `ChatGPT sequential package worker`
Status: `COMPLETE`
Implementation PR: `#86`
Frozen implementation head: `1997caa864847049d51bfc58402f019e0a0d65c6`
Product merge: `e605d8ad6094b2ae6842044d209875e13c38906d`
Implementation branch: `package/es-p10-cheat-testers` — deleted after merge

## Selection and routing

- Legitimate aggregate `main` at selection: `83302749b3247f7a05157f1625fc99da6aa43736` (merge PR #85).
- ES-P04 was `COMPLETE`, satisfying ES-P10's dependency.
- ES-P02 PR #70 and ES-P05 PR #81 were freshly rechecked and remained `BLOCKED` / `PARKED_BLOCKED` on the unchanged private Actions Billing & plans condition. Neither was resumed.
- Live reconciliation found PR #86 and `package/es-p10-cheat-testers` already advancing ES-P10. Its live head still had exact-head static/coverage failures and unresolved review findings, so ES-P10 was selected as the highest-priority `ACTIONABLE_CONTINUATION`, not as a new claim.
- Exactly one package was worked. ES-P11 was not activated or modified.
- Issue #43 remains open and deferred; LiteBans remains authoritative.

## Completed package behavior

ES-P10 owns `AUD-TESTER-001` and `AUD-TESTER-002` only and completed:

- the real Cheat Tester staff hotbar action plus `/cheattester config|select|run|cancel|status` text fallback;
- release probes for Totem refill, No-fall, Velocity/anti-knockback, and Auto-armor;
- strict staff-mode/permission authorization, same-target duplicate rejection, and bounded global/per-staff concurrency;
- evidence-only operation with no automatic sanction, punishment request, or authority mutation;
- exact temporary player-state snapshot/restoration on the owning entity scheduler;
- immutable forward migration `V18__cheat_tester_session_journal.sql` for crash/restart recovery, revisioned evidence, terminal lifecycle, and a globally unique active-target fence;
- integration of ACTIVE tester rows into the existing durable inventory-lock contract so disconnect/restart recovery cannot race offline inventory mutation;
- global target-row lookup on login/respawn so a player can recover a durable tester row created on a backend that no longer returns;
- bounded/restart-only tester configuration with `/estaff reload` rejecting unapplied changes;
- optional ProtocolLib 5.4.0 fake-entity adapter with no direct NMS dependency;
- target/staff-only synthetic entity visibility, target `USE_ENTITY` cancellation/capture, attack/interaction latency and aim-angle evidence, and scheduler-safe viewer cleanup;
- fail-closed packet-provider behavior, including propagating failed fake-entity destruction after fail-closing so unverifiable cleanup cannot be falsely terminalized.

Fake bases / `AUD-TESTER-003` remain exclusively `ES-P11`.

## Continuation review and fixes

Every live PR review thread was reconciled and every still-valid finding was corrected. Zero valid unresolved review threads remained at merge.

1. **Static size and weak direct coverage.** `CheatTesterManager` was decomposed into focused fake-entity state/support and durable-completion collaborators. Direct tests cover fake-entity state, control-state policy, journal revision retry, strict configuration parsing, and settings boundaries/non-finite values.
2. **Strict configuration.** Unknown keys are rejected at the Cheat Tester root and nested `velocity` / `no-fall` mappings; cross-field capacity limits are validated.
3. **Recovery load and backend ownership.** The recurring recovery sweep was reduced from every 5 seconds to every 5 minutes while event-driven recovery remains primary. Login/respawn recovery uses the globally active target row so exact restoration is not stranded by a failed previous backend.
4. **Staff-tool safety and privacy.** Any tagged staff tool cancels player damage before tool validity dispatch. `/cheattester status` requires the tester permission and active staff mode. Inventory click/drag/open mutations are blocked while an exact-restoration probe owns target state.
5. **Snapshot and settings validation.** Held-slot and item payload decoding complete before inventory writes. Probe doubles reject NaN/infinity at construction time.
6. **ProtocolLib failure semantics.** Listener, show, and destroy paths handle runtime/linkage incompatibilities and fail closed. Delayed staff-viewer fake spawns recheck active session state. Failed cleanup remains durably recoverable rather than being incorrectly terminalized.
7. **Persistence correctness.** V18 payload columns are sized for bounded utf8mb4 character contracts. Tester types persist stable `id()` values. Audit JSON uses the configured Jackson mapper instead of manual escaping.
8. **Concurrency/evidence correctness.** The pre-mutation cancellation/journal race remains atomically coordinated, global/per-staff limits are rechecked safely, and controlling-staff fake-entity interactions are excluded from suspect evidence.

## Persistence and privacy boundary

- V1–V17 are untouched; V18 is the package migration and is now present on aggregate `main`.
- Tester recovery snapshots are bounded opaque restoration material, not general staff evidence.
- V18 does not add raw addresses, chat logs, private messages, or unrelated sensitive data.
- Evidence is bounded and tied to the tester session; every tester remains human-review-only.

## Exact frozen-head validation

Frozen head: `1997caa864847049d51bfc58402f019e0a0d65c6`.

- Wiki/metadata check `93015435354`: **SUCCESS**.
- Java 21 workflow run `31224336640`, successful unchanged-head rerun job `93016497496`: **SUCCESS** for build, tests, MariaDB/Testcontainers integration, migration checks, aggregate JaCoCo, runtime-JAR inspection, validation artifact upload, and Codacy coverage upload.
- Artifact `9011777818`, SHA-256 `4557877edff2589065483f4d2d81f0c231511001ad04ea82d5b36cfbba1c762a`.
- Aggregate JaCoCo: 2,576 / 5,427 lines covered = 47.47%; 796 / 1,887 branches covered = 42.18%.
- Codacy static `93015840928`: **SUCCESS**, zero issues/annotations.
- Codacy coverage variation `93017546035`: **SUCCESS**, `-0.98%` against allowed `-1.0%`.
- Codacy diff coverage `93017545807`: **SUCCESS**, `29.91%`, no configured gate.
- Review threads: zero valid unresolved threads at merge.

The first attempt of workflow run `31224336640` failed in the already-merged punishment-request-alert integration test because MariaDB reported a concurrent-update/deadlock-style failure. No ES-P10 code was changed in response. Failed jobs were rerun on the exact unchanged SHA and the complete job succeeded; only that clean unchanged-head rerun is counted as passing evidence.

## Private staging disposition

Representative distributed Java/Bedrock acceptance remains assigned to `ES-V02` by the ES-P10 contract.

Exact frozen-head private staging run `31224339373` did not execute product validation:

- Ubuntu job `93015447468`: runner ID `0`, empty runner name, `steps: []`;
- Pi job `93015487565`: skipped.

This is **NOT A PASS**. It is not relabeled through an infrastructure exception and is not treated as an executed ES-P10 product failure. No ES-P10 owner-approved staging exception was claimed; representative acceptance remains deferred to ES-V02 exactly as the package contract specifies.

## Merge, containment, and cleanup

- Immediately before merge, live `main` remained exactly the selected starting head `83302749b3247f7a05157f1625fc99da6aa43736`; frozen ES-P10 was 128 commits ahead and 0 behind.
- PR #86 merged normally with expected head `1997caa864847049d51bfc58402f019e0a0d65c6`.
- Product merge commit: `e605d8ad6094b2ae6842044d209875e13c38906d`.
- Post-merge compare from frozen head to merged `main`: one commit ahead, zero behind, zero file differences. Exact containment is verified.
- `package/es-p10-cheat-testers` was automatically removed by GitHub and now returns 404. Implementation-branch cleanup is complete.

## Terminal routing

ES-P10 is terminal `COMPLETE`. Dependency recomputation makes `ES-P11 — Fake-base generation and cleanup` `READY`, but this worker did not activate or modify ES-P11. ES-P02 and ES-P05 remain parked under their unchanged external blockers. A future worker must start with fresh live GitHub reconciliation and canonical selection rules.

## Systems not disturbed

The worker did not modify parked ES-P02/ES-P05 product branches, begin ES-P11 fake bases, deploy, access production/private player data, run issue #43 acceptance, activate EnthusiaStaff punishment authority, disable LiteBans, rewrite Flyway history, execute ES-V02, or claim unavailable private staging passed.

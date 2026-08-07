# ES-P10 cheat tester and fake-entity handoff

Date: 2026-08-07
Package: `ES-P10 — Cheat tester and fake-entity system`
Worker: `ChatGPT sequential package worker`
Status: `MERGE_PENDING`
PR: `#86`
Branch: `package/es-p10-cheat-testers`

## Selection and routing

- Legitimate aggregate `main` at selection: `83302749b3247f7a05157f1625fc99da6aa43736` (merge PR #85).
- ES-P04 is `COMPLETE`, satisfying ES-P10's dependency.
- ES-P02 PR #70 and ES-P05 PR #81 were freshly rechecked and remain `BLOCKED` / `PARKED_BLOCKED` on the unchanged private Actions Billing & plans condition. A private staging run created at 2026-08-07 16:10 UTC for current EnthusiaStaff source had required Ubuntu job `92925059857` with runner ID `0`, empty runner name, and `steps: []`; Pi job `92925074453` was skipped. No product validation step executed. The blocker therefore did not change and those packages were not resumed.
- Live reconciliation found PR #86 and `package/es-p10-cheat-testers` already advancing ES-P10. Its then-current head had exact-head Codacy size and coverage-variation failures plus unresolved CodeRabbit findings, so ES-P10 was selected as the highest-priority `ACTIONABLE_CONTINUATION`, not as a new claim.
- Issue #43 remains open and deferred; LiteBans remains authoritative.

## Implemented package behavior

ES-P10 owns `AUD-TESTER-001` and `AUD-TESTER-002` only and now implements:

- the real Cheat Tester staff hotbar action plus `/cheattester config|select|run|cancel|status` text fallback;
- release probes for Totem refill, No-fall, Velocity/anti-knockback, and Auto-armor;
- strict staff-mode/permission authorization, same-target duplicate rejection, and bounded global/per-staff concurrency;
- evidence-only operation with no automatic sanction, punishment request, or authority mutation;
- exact temporary player-state snapshot/restoration on the owning entity scheduler;
- immutable forward migration `V18__cheat_tester_session_journal.sql` for crash/restart recovery, revisioned evidence, terminal lifecycle, and a globally unique active-target fence;
- integration of ACTIVE tester rows into the existing durable inventory-lock contract so disconnected/restarting recovery cannot race offline inventory mutation;
- global target-row lookup on login/respawn so a player can recover a durable tester row created on a backend that no longer returns;
- bounded/restart-only tester configuration with `/estaff reload` rejecting unapplied changes;
- optional ProtocolLib 5.4.0 fake-entity adapter with no direct NMS dependency;
- target/staff-only synthetic entity visibility, target `USE_ENTITY` cancellation/capture, attack/interaction latency and aim-angle evidence, and scheduler-safe viewer cleanup;
- fail-closed packet-provider behavior: unhealthy/unavailable ProtocolLib disables fake-entity testing without disabling non-packet probes, and a failed destroy now propagates after fail-closing so an unverifiable cleanup cannot be falsely terminalized.

Fake bases / `AUD-TESTER-003` remain exclusively `ES-P11`.

## Continuation review and fixes

The continuation worker reconciled every live PR review thread and corrected all still-valid findings. Zero valid unresolved review threads remained at the state checkpoint.

1. **Static size and weak direct coverage.** `CheatTesterManager` had a 783 non-comment-line Codacy finding and newly added policy/runtime glue depressed coverage variation. Fake-entity bookkeeping and durable optimistic completion were extracted into focused collaborators; direct tests now cover fake-entity state, control-state policy, journal revision retry, strict configuration parsing, and settings boundaries/non-finite values.
2. **Strict configuration.** Unknown keys are rejected at the Cheat Tester root and nested `velocity` / `no-fall` mappings. Cross-field capacity limits are validated.
3. **Recovery load and backend ownership.** The recurring recovery sweep was reduced from every 5 seconds to every 5 minutes while event-driven recovery remains primary. Login/respawn recovery uses the globally active target row so exact restoration is not stranded by a crashed previous backend. Expired mutating rows are not blindly released because that could discard the only authoritative restoration snapshot.
4. **Staff-tool safety and privacy.** Any tagged staff tool cancels player damage before tool validity dispatch. `/cheattester status` requires the tester permission and active staff mode. Inventory click/drag/open mutations are blocked while an exact-restoration probe owns target state.
5. **Snapshot and settings validation.** Held-slot and item payload decoding complete before inventory writes. Probe doubles reject NaN/infinity at construction time.
6. **ProtocolLib failure semantics.** Listener, show, and destroy paths catch runtime/linkage incompatibilities, fail the adapter closed, and preserve durable recovery when synthetic-entity cleanup cannot be verified. Delayed staff-viewer fake spawns recheck active session state to prevent spawn-after-destroy races.
7. **Persistence correctness.** V18 payload columns are `MEDIUMTEXT` for the existing bounded character contracts. Tester types persist stable `id()` values. Audit JSON uses the configured Jackson mapper instead of manual escaping.
8. **Earlier harsh-review fixes retained.** The pre-mutation cancellation/journal race remains atomically coordinated, and controlling-staff fake-entity interactions remain excluded from suspect evidence.

## Persistence and privacy boundary

- V1–V17 are untouched; V18 is the package migration.
- Tester recovery snapshots are bounded opaque restoration material, not general staff evidence.
- V18 does not add raw addresses, chat logs, private messages, or unrelated sensitive data.
- Evidence is bounded and tied to the tester session; every tester remains human-review-only.

## Direct tests and evidence

Primary direct evidence is summarized in `reports/ES-P10-CHEAT-TESTER-IMPLEMENTATION.md`.

Direct tests cover tester type/settings, command filtering, strict configuration/reload behavior, control-state authorization/capacity, staff-tool authorization, lifecycle journal/cancellation phase ordering, evidence formatting/bounds, fake-entity target-only bookkeeping, packet-provider unavailable behavior, durable completion revision retry, V18 migration, MariaDB restart persistence, global active-target fencing across backend IDs, evidence revision checkpointing, audit emission, durable inventory-lock participation, and terminal release/reuse.

## Private staging disposition

Representative distributed Java/Bedrock acceptance remains assigned to `ES-V02` by the ES-P10 contract. Package Pi/distributed attempts continue to encounter the known private Actions account restriction: the private required Ubuntu job receives runner ID `0`, an empty runner name, and no steps; the Pi job is then skipped. This is **NOT A PASS**, but it is also not an executed product failure. No ES-P10 owner-approved staging exception is being claimed; representative acceptance remains explicitly deferred to ES-V02 by this package's contract.

## Current validation state

Product code was last changed at `ee49d35b2a84eb62e01dbef16d5991ae89ff10f8`; the following package-state commits are documentation-only checkpoints. The final tracked PR head after this handoff update is the head that must satisfy every applicable exact-head hosted Java/build/test/MariaDB/Testcontainers/migration/runtime-JAR/Wiki/static/coverage/review gate before PR #86 may merge. Skipped, superseded, different-head, queued, or zero-runner evidence is not counted as a pass.

## Terminal procedure still required

1. Treat the head containing this handoff as frozen unless a valid gate/review defect forces another change.
2. Require exact-head hosted build/tests/MariaDB/Testcontainers/migration/runtime-JAR/Wiki/static/coverage gates and zero valid review threads.
3. Record exact-head private zero-runner/Pi-skipped evidence as **NOT A PASS**, with ES-V02 retaining representative acceptance.
4. Merge PR #86 normally with the exact validated head.
5. Verify resulting `main` contains the frozen head and no unique package work remains.
6. Delete `package/es-p10-cheat-testers`.
7. Publish terminal `COMPLETE` package/registry/workspace/handoff state in a documentation-only follow-up, recompute dependency-derived readiness without activating a second package, merge that publication normally, delete its branch, and stop.

## Systems not to disturb

Do not modify parked ES-P02/ES-P05 product branches, begin ES-P11 fake bases, deploy, access production/private player data, run issue #43 acceptance, activate EnthusiaStaff punishment authority, disable LiteBans, rewrite Flyway history, or claim unavailable private staging passed.

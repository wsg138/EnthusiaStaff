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
- ES-P10 had no pre-existing branch, PR, or package handoff before selection and was the dependency-complete eligible `READY` package with the lowest numerical priority.
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
- bounded/restart-only tester configuration with `/estaff reload` rejecting unapplied changes;
- optional ProtocolLib 5.4.0 fake-entity adapter with no direct NMS dependency;
- target/staff-only synthetic entity visibility, target `USE_ENTITY` cancellation/capture, attack/interaction latency and aim-angle evidence, and scheduler-safe viewer cleanup;
- fail-closed packet-provider behavior: unhealthy/unavailable ProtocolLib disables fake-entity testing without disabling non-packet probes, and an unverifiable cleanup never produces a false terminal success.

Fake bases / `AUD-TESTER-003` remain exclusively `ES-P11`.

## Harsh-review fixes

Pre-freeze review and static analysis found and corrected several real issues rather than waiving them:

1. **Cancellation versus journal-start race.** A cancellation could previously arrive between local session registration and durable journal commit. `CheatTesterSession` now coordinates journal-submission and finish phases atomically. Cancellation before submission creates no row; cancellation while a write is in flight causes a successfully committed row to terminalize before probe mutation; cancellation after commit uses the durable finish path. Direct session tests cover all three phases plus submission failure.
2. **Observer contamination of evidence.** The controlling staff viewer can see the synthetic entity, but staff interactions no longer increment suspect interaction/attack evidence. Only the target contributes suspect evidence.
3. **Cross-backend duplicate and asset race.** V18 uses a nullable globally unique active-target UUID and ACTIVE tester rows participate in the inventory lock, including after the in-memory session disappears.
4. **Scheduler/viewer safety.** Fake show/hide is scheduled separately for each viewer's entity scheduler; target mutation/restoration stays on the target scheduler.
5. **Static-analysis complexity.** The original large runtime was decomposed into `CheatTesterSession`, `CheatTesterProbeEngine`, `CheatTesterEvidence`, `CheatTesterMutationGuard`, `CheatTesterSnapshotCodec`, configuration parsing, command parsing, and the journal store. Narrow compatibility suppression remains only on the legacy restart-configuration constructor that predates ES-P10.
6. **Compile/API drift.** Hosted validation exposed and corrected ProtocolLib listener-plugin typing, Paper inventory type usage, and an integration-test checked-exception declaration. Superseded failing heads remain failed evidence and are not counted as final validation.

## Persistence and privacy boundary

- V1–V17 are untouched; V18 is the package migration.
- Tester recovery snapshots are bounded opaque restoration material, not general staff evidence.
- V18 does not add raw addresses, chat logs, private messages, or unrelated sensitive data.
- Evidence is bounded and tied to the tester session; every tester remains human-review-only.

## Direct tests and evidence

Primary direct evidence is summarized in `reports/ES-P10-CHEAT-TESTER-IMPLEMENTATION.md`.

Direct tests cover tester type/settings, command filtering, configuration bounds/reload behavior, staff-tool authorization, lifecycle journal/cancellation phase ordering, evidence formatting/bounds, packet-provider unavailable behavior, V18 migration, MariaDB restart persistence, global active-target fencing across backend IDs, evidence revision checkpointing, audit emission, durable inventory-lock participation, and terminal release/reuse.

## Private staging disposition

Representative distributed Java/Bedrock acceptance remains assigned to `ES-V02` by the ES-P10 contract. Package Pi/distributed attempts continue to encounter the known private Actions account restriction: the private required Ubuntu job receives runner ID `0`, an empty runner name, and no steps; the Pi job is then skipped. This is **NOT A PASS**, but it is also not an executed product failure. No ES-P10 owner-approved staging exception is being claimed or needed for the representative acceptance that the package contract explicitly defers to ES-V02.

## Current validation state

Implementation and package-state publication are complete enough to freeze a final product head. The final exact head must still satisfy every applicable hosted Java/build/test/MariaDB/Testcontainers/migration/runtime-JAR/Wiki/static/coverage/review gate, with zero valid unresolved review threads, before PR #86 may merge. Skipped, superseded, different-head, or zero-runner evidence is not counted as a pass.

## Terminal procedure still required

1. Freeze the final PR #86 head after this package-state publication.
2. Require exact-head hosted build/tests/MariaDB/Testcontainers/migration/runtime-JAR/Wiki/static/coverage gates and zero valid review threads.
3. Record exact-head private zero-runner/Pi-skipped evidence as **NOT A PASS**, with ES-V02 retaining representative acceptance.
4. Mark PR #86 ready and merge it normally with the exact frozen head.
5. Verify resulting `main` contains the frozen head exactly and has no file differences beyond the merge commit.
6. Delete `package/es-p10-cheat-testers`.
7. Publish terminal `COMPLETE` package/registry/workspace/handoff state in a documentation-only follow-up, recompute dependency-derived readiness without activating a second package, merge that publication normally, delete its branch, and stop.

## Systems not to disturb

Do not modify parked ES-P02/ES-P05 product branches, begin ES-P11 fake bases, deploy, access production/private player data, run issue #43 acceptance, activate EnthusiaStaff punishment authority, disable LiteBans, rewrite Flyway history, or claim unavailable private staging passed.

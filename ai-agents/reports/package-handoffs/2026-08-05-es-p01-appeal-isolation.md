# ES-P01 exact-sanction appeal isolation handoff

- Date: 2026-08-05
- Package: `ES-P01 — Exact-sanction appeal isolation`
- Status at this checkpoint: `ACTIVE`
- Starting `main`: `e434b3dedc003d1d5b3def64f38cc7465752b0e5`
- Branch: `package/es-p01-appeal-isolation`
- Pull request: `#68 — ES-P01: isolate appeals to the exact sanction`
- Passing implementation head before evidence-only checkpoint commits: `9acbbe0cb792deb69bd5758b364d9609da9ace58`
- Highest Flyway migration: `V16`; no migration changed or added
- Production authority: LiteBans remains authoritative

## Live reconciliation

At package start, the repository had only `main`, no open or draft PRs, no active package branches, and no existing ES-P01 handoff. The registry marked ES-P01 `READY` and unassigned. PR #68 and the documented temporary branch were therefore created rather than replacing existing work.

## Completed implementation

- Replaced `WebsiteAppealEndpoint` case-wide `END_EARLY` mutation with `ExactSanctionChangeRequest` targeting the submitted `punishmentId`.
- Uses exact `FULL_OVERTURN`, expected revision, and linked appeal ID; the response reports exactly one affected sanction.
- Preserves website reviewer authorization through `ACCEPT_APPEAL`; linked appeals do not grant ordinary full-overturn permission.
- Constrains hierarchy bypass at the request boundary to Founder or a linked MOD/ADMIN appeal reviewer. Developer/System actors and SYSTEM-issued sanctions remain non-mutable.
- Writes durable `APPLIED/MUTATION_PENDING_R<revision>` before mutation and `APPLIED/APPLIED` after mutation.
- Persists the original expected revision in the pending outcome so a retry after restart cannot silently accept an intervening sanction mutation.
- Supports retry after Velocity restart when mutation has not begun, rolled back, or committed before finalization.
- Keeps finalized durable outcome `APPLIED`; replay remains response metadata and does not conflict with stored state.
- Preserves the existing 10–1,000-character website reason contract. Paper configuration remains capped at 512.
- Adds no migration because the pending marker fits the existing `outcome_code VARCHAR(64)` column while appeal `state` remains `APPLIED`.

## Regression coverage

Focused Velocity/domain tests prove:

- exact punishment ID, original expected revision, linked appeal, full overturn, and one affected sanction;
- no case-wide mutation fallback;
- MOD appeal authorization without ordinary full-overturn authority;
- constrained hierarchy bypass and immutable SYSTEM sanctions;
- repeated acceptance and stable durable replay outcome;
- pending retry reuses the persisted revision even when the live revision is newer;
- read-only reviewer rejection, authority-mode fencing, stale decision persistence, missing exact target/capability, and the 1,000-character reason bound.

MariaDB/Testcontainers coverage proves:

- a combined case with two active sanctions overturns only the appealed sanction;
- the sibling sanction remains active and the case remains open;
- linked sanction event and exact overturn audit are written once;
- finalized replay and pending-revision recovery survive MariaDB runtime restart;
- an intervening revision change after pending preparation is rejected as stale after restart;
- a forced audit-insert failure rolls back sanction/event/outbox work and leaves the appeal pending for recovery;
- concurrent identical retries produce one mutation and one replay.

## Passing hosted evidence

Exact tested head: `9acbbe0cb792deb69bd5758b364d9609da9ace58`.

- Coverage run: `31059266809`
- Job: `92483396625`
- Java: Temurin `21.0.11+10`
- Command: `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`
- Result: `BUILD SUCCESSFUL`; every module test and MariaDB/Testcontainers integration test passed.
- Runtime JAR integrity/provider-leak inspection passed for 24 provider API source types.
- Paper JAR: 8,895,602 bytes; SHA-256 `51df452638f8efeb31f52583684603cef5b6c70470529fe90ad20cfb6c90cb1b`; 4,747 entries; zero provider leaks.
- Velocity JAR: 7,788,490 bytes; SHA-256 `743aac566ee14a856d011f9d22e882b28f1312538c538d8de6ea141c184d58b2`; 4,120 entries; zero provider leaks.
- Aggregate JaCoCo: 47.04% lines, 38.10% branches, 49.78% instructions.
- Artifact: `8951540150`; 18,257,819 bytes; SHA-256 `0abac9547c2b59cf6d9c5d112bd90d90e60ebd21c74a2a64457aad520d919b22`.
- Codacy coverage upload succeeded; current PR summary reports zero new issues.

This is passing pre-review evidence. Evidence-only checkpoint commits moved the branch afterward, so the final reviewed head must repeat all applicable gates.

## Earlier failed runs and fixes

1. Run `31057348404`, job `92477622946`: production code compiled; four new integration methods failed because a synthetic case ID contained a disallowed Crockford character. Fixtures were corrected to production-valid IDs.
2. Run `31058553169`, job `92481345478`: three integration paths were rejected by the existing Founder-only hierarchy bypass. The bypass was moved behind a constrained request invariant for linked MOD/ADMIN appeal decisions; ordinary mutation authority remains unchanged.
3. Full-diff review found a restart-staleness gap where a pending retry could read a newer revision. The original revision is now persisted in the pending outcome and reused after restart.

## Pi staging evidence

The configured Pi workflow dispatches to `wsg138/EnthusiaStaff-Staging`, but the staging build job receives no runner (`runner_id: 0`, no runner name, zero steps). It never checks out or executes product code; the Pi boot/restart job is skipped as a consequence. This is infrastructure-unavailable evidence, not a passing check and not evidence of a product boot failure.

Representative recorded attempt:

- Parent run `31057348145`, job `92477622119`
- Staging run `31057358391`
- Staging build job `92477654523`: `runner_id: 0`, zero steps
- Pi boot job `92477660726`: skipped
- Diagnostics artifact `8950755524`; SHA-256 `7f4473dd32b89f1ad69c1e0a26379ae76fe92686e92fe83ead67762a7c04dcfb`

## Harsh review findings fixed

1. Unsafe case-wide appeal mutation.
2. Durable replay outcome conflict (`APPLIED` versus `REPLAYED`).
3. Crash window between exact mutation and appeal finalization.
4. Appeal authorization versus ordinary full-overturn authorization mismatch.
5. Issuer hierarchy mismatch and over-broad bypass risk.
6. Restart retry using a new live revision rather than the original accepted revision.
7. Website reason-contract regression and duplicated audit text.
8. Misleading integration audit counts.
9. Invalid synthetic case IDs.

## Current review state

- First-party full-diff review is complete for implementation, transaction, authorization, hierarchy, idempotency, restart, concurrency, rollback, contract, privacy, and scope.
- Codacy currently reports zero new issues.
- CodeRabbit skipped while PR #68 was draft. The next action is to mark the PR ready and obtain a current review.
- No valid unresolved review thread is currently known, but this must be rechecked after CodeRabbit runs.

## Incomplete work

1. Mark PR #68 ready and obtain CodeRabbit/human review evidence.
2. Resolve every valid finding and all valid unresolved threads.
3. Harshly review the complete resulting diff again.
4. Freeze the exact reviewed head and rerun Java 21 build/tests, MariaDB/Testcontainers, runtime-JAR inspection, coverage, Codacy, and review gates.
5. Merge PR #68 normally with the expected frozen head.
6. Verify resulting `main`, feature-head containment, and deletion of `package/es-p01-appeal-isolation`.
7. Finalize the registry, package file, workspace state, latest routing, and this handoff with exact merge/cleanup evidence.
8. Derive `ES-P02` and `ES-X05` to `READY` only after ES-P01 is truly complete; do not begin them.

## Exact next action

Mark PR #68 ready for review. Inspect CodeRabbit, Codacy, human reviews, and all review threads. Fix every valid finding. When tracked content is stable and no valid finding remains, record the exact head, rerun every applicable exact-head gate, and merge normally only if that exact head passes.

## Systems not to disturb

- Website UX and `enthusia-site`
- Production authority or deployment
- LiteBans migration/cutover and issue #43
- V1–V16 migrations
- External providers or aggregate component copies
- Private databases, player rows, logs, messages, credentials, addresses, and production routes
- Any package other than ES-P01

# ES-P01 exact-sanction appeal isolation handoff

- Date: 2026-08-05
- Package: `ES-P01 — Exact-sanction appeal isolation`
- Status at this checkpoint: `ACTIVE`
- Starting `main`: `e434b3dedc003d1d5b3def64f38cc7465752b0e5`
- Branch: `package/es-p01-appeal-isolation`
- Pull request: `#68 — ES-P01: isolate appeals to the exact sanction`
- Latest implementation head at checkpoint: `5e3f07ee546c4a569f7d27cf2b4e09e1b0c97adf`
- Highest Flyway migration: `V16`; no migration changed or added
- Production authority: LiteBans remains authoritative

## Live reconciliation

At package start, the repository had only `main`, no open or draft PRs, no active package branches, and no existing ES-P01 handoff. The registry marked ES-P01 `READY` and unassigned. PR #68 and the documented temporary branch were therefore created rather than resuming replacement work.

## Completed implementation

- Replaced `WebsiteAppealEndpoint` case-wide `END_EARLY` mutation with `ExactSanctionChangeRequest` targeting the submitted `punishmentId`.
- Uses `FULL_OVERTURN` with exact expected revision and linked appeal ID; response reports exactly one affected sanction.
- Preserves website reviewer authorization through `ACCEPT_APPEAL`; linked appeals do not grant general full-overturn permission.
- Uses hierarchy bypass only for the already-authorized linked appeal decision, preserving the former website-reviewer ability to reverse sanctions regardless of issuer rank without widening ordinary command authority.
- Adds durable `APPLIED/MUTATION_PENDING` preparation before mutation and `APPLIED/APPLIED` finalization after mutation.
- Supports retry after Velocity restart when mutation has not begun, rolled back, or committed before finalization.
- Keeps finalized durable outcome `APPLIED`; `replayed` remains response metadata and no longer conflicts with stored appeal state.
- Preserves the existing website input contract of 10–1,000 reason characters. Paper configuration remains capped at 512; only the domain value object can represent the existing website bound.
- Adds no schema migration because the pending marker is an existing `outcome_code VARCHAR(64)` value while appeal `state` remains `APPLIED`.

## Regression coverage

Focused Velocity/domain tests cover:

- exact punishment ID, expected revision, linked appeal, full overturn, and one affected sanction;
- no case-wide mutation fallback;
- moderator appeal authorization without ordinary full-overturn authority;
- repeated acceptance and stable durable replay outcome;
- read-only reviewer rejection before preparation;
- authority mode fail-closed behavior;
- stale exact decision persistence;
- missing exact target and missing exact capability fail closed;
- the original 1,000-character reason limit.

MariaDB/Testcontainers coverage includes:

- a combined case with two active sanctions where only the appealed sanction overturns;
- sibling sanction remains active and case remains open;
- linked sanction event and exact overturn audit are written once;
- restart replay and concurrent duplicate requests create one mutation and one replay;
- stale revision changes neither sanction;
- forced audit-insert failure rolls back sanction/event/outbox transaction and leaves the appeal pending for restart recovery.

## Validation evidence and corrections

### Hosted run on `c1ce380a2128c928f66d28651de858d4c6cf6b49`

- Coverage run: `31057348404`
- Job: `92477622946`
- Java: Temurin 21.0.11+10
- Build reached all modules and compiled production code and tests.
- Result: failed only because all four new integration methods used a synthetic case ID containing a disallowed Crockford character.
- Failure artifact: `8950858297`, SHA-256 `8fa3aaeb2e2569a975388107be05287d8d57edcd970eef9c0497ccd3740a42c0`.
- Fix: changed fixtures to valid 16-digit case IDs and corrected audit assertions to count only `SANCTION_OVERTURNED`, excluding the expected punishment-code claim audit.

### Pi staging on `c1ce380a2128c928f66d28651de858d4c6cf6b49`

- Parent run: `31057348145`
- Parent job: `92477622119`
- Dispatched staging run: `wsg138/EnthusiaStaff-Staging` run `31057358391`
- Staging build job: `92477654523`
- Staging build job had `runner_id: 0`, no runner name, and zero steps; it never checked out or executed product code.
- Pi boot/restart job `92477660726` was skipped as a consequence.
- Parent diagnostics artifact: `8950755524`, SHA-256 `7f4473dd32b89f1ad69c1e0a26379ae76fe92686e92fe83ead67762a7c04dcfb`.
- Classification: infrastructure unavailable; not a passing check and not evidence of a product boot failure.

## Harsh review findings already fixed

1. **Unsafe case-wide mutation:** replaced with exact sanction mutation.
2. **Replay completion conflict:** durable outcome no longer changes from `APPLIED` to `REPLAYED`.
3. **Crash window between mutation and appeal completion:** explicit pending/final state allows idempotent recovery.
4. **Authorization mismatch:** linked appeal requests use `ACCEPT_APPEAL`, while unlinked overturns retain `FULL_OVERTURN` requirements.
5. **Issuer hierarchy mismatch:** bypass is limited to a linked, service-authorized appeal request.
6. **Website reason contract regression:** retained the prior 1,000-character bound and removed duplicated audit prefix text.
7. **Misleading integration audit counts:** scoped to the exact overturn event rather than all website audits.
8. **Invalid synthetic case IDs:** corrected to production-valid Crockford identifiers.

## Incomplete work

- Obtain a passing exact-head hosted build/test/runtime-JAR/coverage/static-analysis run after the fixture fixes.
- Update registry and workspace routing to current package state.
- Complete final documentation/state batch.
- Harshly review the complete final PR diff, including all state and test corrections.
- Mark PR #68 ready and obtain CodeRabbit/Codacy review evidence.
- Resolve all valid findings and require zero unresolved valid threads.
- Freeze the reviewed head and validate that exact revision.
- Merge normally, verify containment, and delete `package/es-p01-appeal-isolation`.
- Finalize package state with the exact merge commit and dependency-derived READY packages.

## Exact next action

Wait for the hosted run associated with the current or later branch head. Fix any real build, test, runtime-JAR, or static-analysis failure. Then complete state/docs updates, review the entire PR #68 diff, mark it ready, resolve automated review findings, freeze the exact reviewed head, and rerun all applicable gates before normal merge.

## Systems not to disturb

- Website UX and `enthusia-site`
- Production authority or deployment
- LiteBans migration/cutover and issue #43
- V1–V16 migrations
- External providers or aggregate component copies
- Private databases, player rows, logs, messages, credentials, addresses, and production routes
- Any package other than ES-P01

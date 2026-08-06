# ES-P01 exact-sanction appeal isolation handoff

- Date: 2026-08-05
- Package: `ES-P01 — Exact-sanction appeal isolation`
- Starting status: `ACTIVE`
- Current status: `BLOCKED`
- Starting `main`: `e434b3dedc003d1d5b3def64f38cc7465752b0e5`
- Preserved branch: `package/es-p01-appeal-isolation`
- Preserved pull request: `#68 — ES-P01: isolate appeals to the exact sanction`
- Frozen reviewed product head: `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be`
- Highest Flyway migration: `V16`; no migration changed or added
- Production authority: LiteBans remains authoritative

## Why this package was selected

Live reconciliation found PR #68 and its active package branch as the only unfinished package work. The universal worker rules require resuming an existing package PR before selecting a new `READY` package, so ES-P01 was resumed and no second package began.

## Completed implementation

- Website appeal acceptance now targets only the submitted punishment ID through the exact-sanction mutation contract.
- A combined case can overturn the appealed sanction while leaving sibling sanctions active and the case open.
- Linked appeal authorization uses `ACCEPT_APPEAL` without granting ordinary full-overturn authority.
- Hierarchy bypass is constrained at the request boundary to Founder or a linked MOD/ADMIN appeal reviewer; SYSTEM sanctions remain immutable.
- Mutation idempotency is bound to the website key, appeal ID, and punishment ID.
- Durable `APPLIED/MUTATION_PENDING_R<revision>` state records the original accepted revision before mutation and finalizes to `APPLIED/APPLIED` afterward.
- Pending state survives restart, retains stale-state protection, and permits recovery after rollback or a post-commit/pre-finalization crash.
- The shared pending-revision codec rejects malformed, negative, explicitly signed, and leading-zero revisions.
- Existing API fields and the 10–1,000-character website reason contract are preserved.
- No migration was required because the marker fits the existing `outcome_code VARCHAR(64)` column.

## Tests added or strengthened

Domain and Velocity tests cover:

- exact punishment targeting, original expected revision, linked appeal, full overturn, and one affected sanction;
- no case-wide mutation fallback;
- appeal authorization versus general overturn authorization;
- hierarchy bypass construction and SYSTEM immutability;
- finalized replay without live capability or target lookup;
- pending retry using the persisted revision rather than a newer live revision;
- read-only reviewer denial, authority fencing, stale-state persistence, missing exact target/capability, and reason bounds;
- canonical pending-revision encoding and rejection of malformed, negative, signed, and leading-zero suffixes.

MariaDB/Testcontainers tests cover:

- exact isolation in a combined case;
- finalized replay after restart;
- persisted-revision stale rejection after restart;
- transaction rollback with pending-state recovery;
- concurrent identical retries producing one mutation and one replay;
- sibling-sanction and audit/event isolation.

## Review findings and repairs

The run resumed at head `dbcef45397f0d694744816776cc076800ab253d6`, where hosted validation failed because two Velocity tests still expected appeal preparation to occur after capability/target checks. The durable replay design intentionally prepares first, so the tests were corrected and finalized-replay coverage was added.

Further valid review repairs included:

- centralizing the pending-revision wire format;
- rejecting noncanonical stored revisions;
- reducing transition-state complexity;
- retaining parse causes and making fail-closed behavior explicit;
- replacing an unchecked sealed-interface cast with an exhaustive switch;
- binding idempotency to appeal and punishment identity;
- deriving test expectations from shared constants;
- recording tracked-content freeze and the precise Pi owner/unblock condition.

One review suggestion incorrectly expected low-level prevalidated `HELPER` hierarchy bypass to be false. Current policy intentionally admits that branch, while `ExactSanctionChangeRequest` prevents a HELPER from constructing the appeal bypass. The test was corrected to pin the actual policy while retaining SYSTEM denials.

First-party harsh review rechecked authorization through persistence linkage, transaction boundaries, replay, rollback, stale revisions, idempotency, concurrency, exact-only mutation, privacy, scope, and migration boundaries. No remaining product defect was found. CodeRabbit reports success and every current review thread is resolved.

## Exact-head hosted validation

Frozen product head: `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be`.

- Coverage run `31064171443`; job `92498280092`; success.
- Java: Temurin `21.0.11+10`.
- Command: `./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`.
- Result: `BUILD SUCCESSFUL` in 5m28s; 49 tasks, 40 executed and 9 up-to-date.
- Every module test and MariaDB/Testcontainers integration test passed.
- Runtime inspection checked 24 provider API source types and found zero leaks.
- Paper JAR: 8,897,023 bytes; SHA-256 `ce0e19ae07af278d55db7a56ae65df74aa050aa21a9c010018e5283703c628b9`; 4,748 entries.
- Velocity JAR: 7,790,210 bytes; SHA-256 `9b79c0e215d59a711a778414237789676b42cc083cd973f8c3f505d22e39612e`; 4,121 entries.
- Aggregate JaCoCo: 47.07% lines, 38.18% branches, 49.81% instructions.
- Artifact `8953318443`: 18,264,471 bytes; SHA-256 `19a71478d9e05d1b08e2153d80442f81cc1a9208014adfabf186e9d969bb6e7f`.
- Codacy static analysis and coverage upload passed with zero annotations.

## Pi staging evidence and blocker

The configured workflow dispatches to private repository `wsg138/EnthusiaStaff-Staging`, owned by `wsg138`.

- Parent run `31057348145`, job `92477622119`.
- Staging run `31057358391`.
- Staging build job `92477654523`: `runner_id: 0`, no runner name, zero executed steps.
- Pi boot/restart job `92477660726`: skipped because the build dependency never ran.
- Diagnostics artifact `8950755524`: SHA-256 `7f4473dd32b89f1ad69c1e0a26379ae76fe92686e92fe83ead67762a7c04dcfb`.

This is infrastructure-unavailable evidence. It is not a passing gate and does not indicate a product boot failure.

The only remaining required action depends on external staging infrastructure. ES-P01 is therefore `BLOCKED`, with PR #68 and its branch preserved.

Unblock condition: the `wsg138/EnthusiaStaff-Staging` owner must allocate a compatible runner and obtain a successful exact-head staging build plus safe Pi boot/restart, or explicitly accept and record a verified validation exception permitted by repository policy.

## Explicit exclusions preserved

- Website UX and `enthusia-site`
- Production authority or deployment
- LiteBans migration/cutover and issue #43
- V1–V16 migrations
- External providers or aggregate component copies
- Private databases, player rows, logs, messages, credentials, addresses, and production routes
- Every package other than ES-P01

## Merge, containment, and cleanup state

- PR #68 was not merged because the Pi gate is unresolved.
- No merge commit exists.
- `main` remains `e434b3dedc003d1d5b3def64f38cc7465752b0e5`.
- Reviewed-head containment and post-merge divergence verification are not yet applicable.
- `package/es-p01-appeal-isolation` remains preserved and was not deleted.
- No external parity gate applies.
- No dependent package became `READY`; `ES-P02` and `ES-X05` remain `PLANNED`.

## Exact next action

Resolve the Pi gate without changing frozen product head `5a668d5fecd2bb809a31fdb7ddcb7e27b536a7be`: configure a compatible staging runner and rerun the exact-head staging build plus safe Pi boot/restart, or record an explicit verified owner exception. Then reconcile that PR #68 still points to the reviewed head with all checks and threads clear, merge normally, verify containment and divergence, delete the branch when safe, and complete a documentation-only post-merge finalization PR. Any real product commit requires another harsh review and every applicable exact-head gate.

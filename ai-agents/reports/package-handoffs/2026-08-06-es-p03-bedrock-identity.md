# ES-P03 Bedrock identity correctness handoff

Date: 2026-08-06
Package: `ES-P03 — Bedrock identity correctness`
Worker: `ChatGPT sequential package worker`
Status: `ACTIVE — implementation complete, exact-head validation pending`

## Selection and routing

- Legitimate aggregate `main` at selection: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Temporary package branch: `package/es-p03-bedrock-identity`.
- Aggregate PR: #75.
- Ordinary dependency: ES-P02 complete.
- Owner instruction: continue another productive package while ES-P02 and ES-X05 remain parked until GitHub-hosted runners recover.
- Recorded disposition: narrow owner-directed routing exception selecting ES-P03, the lowest-priority next implementation package. This does not mark ES-P02 complete, import PR #70, waive ES-P03 gates, or activate another package.

## Live reconciliation

- ES-P01: complete.
- ES-P02: `BLOCKED` / `PARKED_BLOCKED`; branch `package/es-p02-runtime-db-recovery`; PR #70; package-record head `80d4ea840f34017c09afb618f623581b31c6223d`; untouched.
- ES-X05: implementation merged in aggregate and standalone; finalization remains `BLOCKED` / `PARKED_BLOCKED`; branch `package/es-x05-finalization`; PR #74; head `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`; untouched.
- Open package PRs at start: #70 and #74 only. PR #75 was created for ES-P03.
- Highest Flyway migration on current `main`: immutable V17.
- Issue #43: open and deferred; no production or cutover authority.
- No competing active implementation worker was visible.

## Confirmed defects at package start

1. Paper mute join persisted every player as Java.
2. Velocity login/backend observations supplied Java without Floodgate proof.
3. The directory rejected the configured single-`*` Bedrock prefix and prefix search.
4. Platform upserts could overwrite stronger known evidence.
5. Current-name and presence writes were not ordered by observation time, and a stale disconnect could clear a newer connection.

## Implemented behavior

- Added `PlayerPlatformDetection` as the shared policy for converting Floodgate/Geyser availability and per-player evidence into `JAVA`, `BEDROCK`, or `UNKNOWN`.
- Added `PaperPlayerPlatformResolver`, reusing the supported Floodgate API shape already present in the repository.
- Paper join persistence now calls the proof-bearing `recordSeenVerified` path.
- The legacy `recordSeen` path is explicitly unverified. Authoritative JDBC persistence stores its platform as `UNKNOWN`, so existing Velocity observations can update UUID, name history, and presence without corrupting platform.
- Verified Bedrock evidence upgrades legacy Java/unknown records and cannot be downgraded by later unverified Java/unknown observations.
- Verified Java upgrades only unknown records; it cannot overwrite Bedrock.
- A single configured `*` prefix is accepted for current and historical aliases and prefix search. Username text is never used as platform proof.
- Current name, lowercase name, current/last server, first seen, and last seen are ordered by observation time.
- Stale disconnects cannot clear a newer same-server connection.
- Broken or incompatible Floodgate remains `UNKNOWN`, including when no local Geyser plugin is visible; only verified Floodgate evidence or explicit absence of both providers can prove Java.
- No migration was required; V1–V17 remain unchanged.

## Regression coverage

- Domain tests cover available Floodgate Bedrock/Java, both providers absent, Geyser with incompatible Floodgate, broken Floodgate without local Geyser, and contradictory provider observations.
- MariaDB/Testcontainers tests cover:
  - `*` current and historical aliases;
  - case-insensitive exact and prefix lookup;
  - unverified proxy Java hints persisting `UNKNOWN`;
  - verified Bedrock repair and non-downgrade;
  - out-of-order current-name/presence writes;
  - stale disconnect protection; and
  - invalid double-prefix alias rejection.
- Integration documentation records the evidence boundary and the ES-P09/ES-V02 ownership handoff.

## Harsh review record

- Retrieved exact Codacy annotations rather than relying on the summary count.
- Removed duplicated test literals.
- Split the ordered JDBC observation method into validation, transaction, player upsert, name upsert, and rollback helpers to remove the complexity findings.
- Identified and corrected an additional source defect not reported by the analyzer: unavailable/incompatible Floodgate plus absent local Geyser had been treated as Java, which is unsafe for proxy-hosted Geyser/Floodgate layouts.
- Remaining external review and exact-head analysis must still be green on the final frozen SHA.

## Current gate observations

- PR #75 was marked ready for review after the implementation/documentation checkpoint.
- An explicit CodeRabbit review request selected the full 14-file diff but was refused because the repository/developer review limit was reached. The bot reported that no review started; this is not a pass.
- The first empty freeze commit did not produce a check suite, so it is not validation evidence.
- PR #75 was closed and reopened on the unchanged head to invoke the repository's configured `pull_request` `reopened` trigger. Exact-head workflow creation/execution still must be observed directly.
- This handoff update is a non-empty synchronization commit recording the live gate state; the resulting head is the new candidate and must receive fresh exact-head checks.

## Validation requirements

- Java 21 warnings-as-errors.
- Clean build and all unit/integration tests.
- Flyway clean-install, upgrade, checksum, and V1–V17 immutability checks.
- Aggregate coverage and runtime-JAR integrity/provider-leak checks.
- Wiki/package validation and configured static analysis.
- Zero valid unresolved review threads.
- Missing, queued, cancelled, skipped, merge-ref-only, rate-limited, or zero-runner ordinary hosted checks are not passes.

## Systems not disturbed

- ES-P02 PR #70 and branch.
- ES-X05 PR #74 and branch.
- Existing migrations V1–V17.
- LiteBans authority and issue #43.
- Production credentials, data, routes, accounts, or environments.
- ES-P09 alt-graph and inheritance scope.

## Exact next action

Inspect the new exact-head Codacy/GitHub Actions results. If all configured source gates execute successfully, resolve any valid findings and re-request external review only when the rate limit has materially recovered. Otherwise publish the precise blocker and park PR #75 without beginning another package.

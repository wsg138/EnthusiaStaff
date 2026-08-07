# ES-P03 Bedrock identity correctness handoff

Date: 2026-08-06
Package: `ES-P03 — Bedrock identity correctness`
Worker: `ChatGPT sequential package worker`
Status: `ACTIVE — implementation and review repairs complete, exact-head validation pending`

## Selection and routing

- Legitimate aggregate `main` at selection: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Temporary package branch: `package/es-p03-bedrock-identity`.
- Aggregate PR: ready PR #75.
- Ordinary dependency: ES-P02 must be `COMPLETE`, but it remains `BLOCKED` / `PARKED_BLOCKED`.
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
- Only an available Floodgate per-player observation can prove Java or Bedrock. Missing, unavailable, incompatible, or absent local provider evidence remains `UNKNOWN`, including proxy-hosted layouts.
- Unequal timestamps order identity and presence changes by event time.
- Equal timestamps use a stable binary key built from normalized username, display username, and server ID, so database arrival order cannot choose the current identity or server.
- Historical display-name updates use a stable binary tie-breaker at equal timestamps.
- Disconnects must be strictly later than the matching connection; stale or equal-time disconnects cannot clear it.
- Valid prefix searches escape SQL `LIKE` wildcards, so `_` remains literal for Java and Bedrock aliases.
- No migration was required; V1–V17 remain unchanged.

## Regression coverage

- Domain tests cover available Floodgate Bedrock/Java, both local providers absent in a proxy-hosted layout, Geyser with incompatible Floodgate, broken Floodgate without local Geyser, and contradictory provider observations.
- MariaDB/Testcontainers tests cover:
  - `*` current and historical aliases;
  - case-insensitive exact and prefix lookup;
  - literal underscore prefix matching;
  - unverified proxy Java hints persisting `UNKNOWN`;
  - verified Bedrock repair and non-downgrade;
  - out-of-order current-name/presence writes;
  - arrival-order-independent equal-time identity/presence ties;
  - stale and equal-time disconnect protection; and
  - invalid double-prefix alias rejection.
- Integration documentation records the evidence boundary and the ES-P09/ES-V02 ownership handoff.

## Harsh review record

- Retrieved exact Codacy annotations rather than relying on the summary count.
- Removed duplicated test literals.
- Split the ordered JDBC observation method into validation, transaction, player upsert, name upsert, and rollback helpers to remove the complexity findings.
- Corrected unavailable/incompatible and both-local-providers-absent Java fallbacks for proxy-hosted Geyser/Floodgate layouts.
- CodeRabbit identified inconsistent ES-P02 dependency wording, stale draft/ready state, missing intended terminal/next-owner state, nondeterministic equal-time persistence, and unescaped SQL `LIKE` underscores.
- The dependency/readiness/terminal records were reconciled; equal-time ordering now uses a stable data-derived tie-breaker; disconnects require strictly later event time; prefix patterns escape SQL wildcard characters.
- The generic CodeRabbit docstring-coverage warning is not a functional or repository-configured source defect; first-party Java build policy remains `-Xlint:all -Werror`, configured static analysis, and zero valid unresolved review findings.

## Current gate observations

- PR #75 is ready for review.
- Exact-head runs on earlier candidates are superseded and are historical evidence only.
- The synchronized head containing all valid review repairs must receive fresh Coverage, Wiki, Codacy, and CodeRabbit results.
- Zero valid unresolved review threads are required before merge.

## Validation requirements

- Java 21 warnings-as-errors.
- Clean build and all unit/integration tests.
- Flyway clean-install, upgrade, checksum, and V1–V17 immutability checks.
- Aggregate coverage and runtime-JAR integrity/provider-leak checks.
- Wiki/package validation and configured static analysis.
- Zero valid unresolved review threads.
- Missing, queued, cancelled, skipped, merge-ref-only, rate-limited, superseded, or zero-runner ordinary hosted checks are not passes.

## Intended terminal state and next owner action

- Intended ES-P03 terminal state: `COMPLETE` after exact-head gates, normal merge, containment, safe branch cleanup, and persistent final publication.
- Responsible next actor: repository owner `wsg138` directs or permits the next sequential package selection after live reclassification.
- This worker will not activate that next package, and this package's routing exception does not automatically apply to later dependency edges.

## Systems not disturbed

- ES-P02 PR #70 and branch.
- ES-X05 PR #74 and branch.
- Existing migrations V1–V17.
- LiteBans authority and issue #43.
- Production credentials, data, routes, accounts, or environments.
- ES-P09 alt-graph and inheritance scope.

## Exact next action

Freeze the synchronized PR #75 head, require successful exact-head Coverage, Wiki, Codacy, and CodeRabbit review with zero valid unresolved findings, then merge normally or publish the precise blocker. Do not begin another package.

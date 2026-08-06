# ES-P03 Bedrock identity correctness handoff

Date: 2026-08-06
Package: `ES-P03 — Bedrock identity correctness`
Worker: `ChatGPT sequential package worker`
Status: `ACTIVE`

## Selection and routing

- Current legitimate aggregate `main` at selection: `345b7bbcec6facb45c7f96b0e6e181ac7a38e1da`.
- Temporary package branch: `package/es-p03-bedrock-identity`.
- Required aggregate PR: pending first coherent checkpoint.
- Ordinary dependency: ES-P02 complete.
- Current owner instruction: continue another productive package while ES-P02 and ES-X05 remain parked until GitHub-hosted runners recover.
- Recorded disposition: narrow owner-directed routing exception selecting ES-P03, the lowest-priority next implementation package. This does not mark ES-P02 complete, import PR #70, waive ES-P03 gates, or activate another package.

## Live reconciliation

- ES-P01: complete.
- ES-P02: `BLOCKED` / `PARKED_BLOCKED`; branch `package/es-p02-runtime-db-recovery`; PR #70; package-record head `80d4ea840f34017c09afb618f623581b31c6223d`; untouched.
- ES-X05: implementation merged in aggregate and standalone; finalization remains `BLOCKED` / `PARKED_BLOCKED`; branch `package/es-x05-finalization`; PR #74; head `96bf9ab21b114a4523582a5ca267e6c1d1370cb1`; untouched.
- Open package PRs at start: #70 and #74 only.
- Open package branches at start: the two parked branches only; ES-P03 branch was then created from exact `main`.
- Highest Flyway migration on current `main`: immutable V17.
- Issue #43: open and deferred; no production or cutover authority.
- No competing active implementation worker was visible.

## Confirmed defects at package start

1. `MuteEnforcementListener.onJoin` records every player as `PlayerPlatform.JAVA`.
2. `EnthusiaStaffVelocityPlugin.onServerPostConnect` records every player as `PlayerPlatform.JAVA`.
3. `JdbcPlayerDirectory.recordSeen` rejects the configured `*` Bedrock prefix even though the authoritative goals require it.
4. `JdbcPlayerDirectory.search` rejects `*`-prefixed lookup prefixes.
5. Directory upserts unconditionally replace the stored platform, allowing a weaker or incorrect later observation to corrupt a known Bedrock identity.

## Intended implementation boundary

- Reuse the supported Floodgate API shape already present in Paper integration.
- Derive each platform write from verified Floodgate evidence, explicit absence evidence, or `UNKNOWN`; never infer Bedrock solely from username text.
- Preserve and resolve `*`-prefixed Bedrock current and historical names.
- Make persistence safe under duplicate and out-of-order Paper/Velocity observations, including repairing legacy unconditional-Java rows when verified Bedrock evidence arrives.
- Keep alt graph, confidence, ambiguity policies, relationship management, protected network identity, and sanction inheritance in ES-P09.
- Keep representative Java/Bedrock staging acceptance in ES-V02.

## Validation requirements

- Java 21 warnings-as-errors.
- Focused Paper and Velocity tests for platform-resolution availability states and write wiring.
- MariaDB/Testcontainers tests for Java/Bedrock names, current/history resolution, prefix search, duplicate/out-of-order updates, and non-downgrade behavior.
- Clean build, all tests, migrations/checksums, aggregate coverage, runtime JAR integrity, provider-leak checks, Wiki/package validation, static analysis, and zero valid unresolved review threads on one exact frozen head.
- Missing, queued, cancelled, skipped, merge-ref-only, or zero-runner ordinary hosted checks are not passes.

## Systems not to disturb

- ES-P02 PR #70 and branch.
- ES-X05 PR #74 and branch.
- Existing migrations V1–V17.
- LiteBans authority and issue #43.
- Production credentials, data, routes, accounts, or environments.
- ES-P09 alt-graph and inheritance scope.

## Exact next action

Complete the identity-write inventory, implement the shared verified platform observation and persistence policy, add focused tests and documentation, update this handoff and package records, open the draft PR, then review and exact-head validate. Stop after ES-P03 completes or is truthfully blocked; do not begin another package.

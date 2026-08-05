# Repository-wide completion audit handoff

Date: 2026-08-05
Repository: `wsg138/EnthusiaStaff`
Audit PR: #66
Audit branch: `audit/full-project-completion-state`
Audited legitimate `main`: `dddc8352aed5aac1eeead3a670680cd647b1b9c2`
Highest Flyway migration: V16

## Result

The canonical audit is `reports/PROJECT-COMPLETION-AUDIT.md`.

The repository is **structurally established and feature-incomplete**. It is not release-candidate ready and not production-ready as a LiteBans replacement.

The 99-item ledger contains:

- 19 `COMPLETE_GOOD`;
- 47 `COMPLETE_WITH_ISSUES`;
- 12 `PARTIAL`;
- 6 `NOT_STARTED`;
- 3 `BLOCKED`;
- 10 `DEFERRED_ACCEPTANCE`;
- 2 `OUTSIDE_THIS_REPOSITORY`.

Proof levels are 65 `TESTED`, 23 `IMPLEMENTED_UNVERIFIED`, 6 `NOT_STARTED`, 3 `BLOCKED`, 2 `PARTIAL`, and 0 `STAGING_VERIFIED` for the audited SHA.

## Highest-risk evidence

- `AUD-APPEAL-003`: the website accepts a punishment-specific appeal but invokes case-wide `END_EARLY`; all active sanctions in a combined case can be ended.
- `AUD-ID-004`: Velocity and a Paper join path persist Bedrock players as `PlayerPlatform.JAVA`.
- `AUD-STAFF-004`: staff-mode hotbar items are created and transfer-protected but have no interaction dispatcher.
- `AUD-RUNTIME-001` and `AUD-RUNTIME-002`: initial MariaDB bootstrap is one-shot and requires restart after a transient startup failure.
- `AUD-ARCH-004`: aggregate coverage is 46.89% line and 37.83% branch, with major runtime composition, command, worker and destructive coordinator paths at 0%.
- `AUD-ASSET-001` through `AUD-ASSET-005`: destructive asset workflows are unproved, provider-blocked or absent and share high-risk journals/rollback code.

## Strong foundations to preserve

- Java 21 modular Gradle architecture, warnings-as-errors and isolated Paper/Velocity shaded artifacts;
- service-boundary authorization and hierarchy;
- authenticated/replay-protected persistent-channel protocol;
- stable escalation policy IDs, finite ladders, recommendation and decay snapshots;
- durable moderation/report/inventory journals, revisions, idempotency and outboxes;
- loopback HMAC website request boundary;
- dormant LiteBans reader, reconciliation, restart recovery, shadow comparison and authority fencing.

## Validation evidence

Exact-main GitHub Actions used Temurin 21.0.11+10 and ran:

`./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`

The build, all module tests, MariaDB/Testcontainers integration tests, runtime JAR creation/integrity, provider API leak inspection, aggregate coverage generation, Codacy coverage upload and Wiki validation succeeded. The current-SHA Pi workflow did not execute because GitHub reported account billing/spending-limit status before a runner started; this is not a product boot failure.

## Planning route

No implementation package is active or preselected. The owner/planner must use the audit ledger, dependency relationships and code-overlap notes to define bounded packages. Do not resume the older priority list without reconciling it against the audit.

Keep appeal/exact-sanction mutation, staff tool dispatch, asset journals/provider rollback, distributed authority/cutover, and policy/migration/history changes sequential around their shared code.

Private LiteBans data, destructive provider testing, multi-process staging and Java/Bedrock/provider acceptance require Codex or a private local environment. Website UX and provider work require the external repositories identified in the audit.

## Boundaries preserved

No product code, migration, workflow, runtime configuration, deployment, production access, authority activation, LiteBans change, or issue #43 acceptance occurred. LiteBans remains authoritative and V1–V16 remain immutable.

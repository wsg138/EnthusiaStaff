# PR #54 validation-final handoff — serious-offense decay eligibility metadata

Date: 2026-08-03 (America/Indiana/Indianapolis)

Repository: `wsg138/EnthusiaStaff`

Pull request: [#54 — Preserve serious-offense decay eligibility in escalation history](https://github.com/wsg138/EnthusiaStaff/pull/54)

Branch: `feature/serious-offense-decay-metadata`

Starting `main`: `fc1e94bd7317d59a33d297a049a94fd2eb3f1c5e`

This immutable report is the final tracked handoff for PR #54. Verify the live feature SHA, workflow results, review state, merge result, resulting `main` and branch cleanup directly on GitHub.

## Start-state reconciliation

- PR #53 had already merged normally as `fc1e94bd7317d59a33d297a049a94fd2eb3f1c5e` from exact feature head `d766dfcd849c25df37df47962a0aab9bc6975304`.
- PR #53 exact-head Coverage `30783188447` and Validate Wiki `30783188443` succeeded and all review threads were resolved.
- No open or draft pull request existed.
- Every pre-existing non-main branch was `ahead_by: 0` relative to `main`.
- V15 was the live highest Flyway migration.
- The supported RoseChat private-message provider contract remained unavailable.

## Work item and implementation

PR #54 preserves each new punishment step's configured decay eligibility and evaluates future escalation from that immutable historical value.

- Added `DecayEligibility` with explicit `ELIGIBLE`, `INELIGIBLE`, and legacy `UNKNOWN` states.
- `PriorOffense` carries the stored eligibility used for later escalation.
- `EscalationDecision` captures the creating reason policy's explicit eligibility for the newly accepted punishment.
- V16 adds nullable `punishment_steps.decay_eligible`; V1–V15 remain byte-identical.
- `JdbcModerationStore` writes the metadata in the same transaction as the case, recommendation snapshot, actual sanctions, audit and outboxes.
- Related-history reads map `TRUE` to eligible, `FALSE` to ineligible, and legacy `NULL` to unknown.
- The shared clean-period clock remains based on the latest contributing, non-overturned related offense.
- Each complete 90-day clean interval reduces only contributions stored as eligible.
- Explicitly non-decaying serious history remains effective under later eligible/minor policies.
- Eligible minor history may still decay when the later selected reason is non-decaying.
- Legacy rows do not decay because reconstructing an old policy from current configuration would invent historical evidence; no legacy row is backfilled or rewritten.
- Existing recommendation snapshots, applied sanctions, expiration/lifecycle state, aliases, removed IDs and visibility behavior remain unchanged.

## Configuration and schema changes

- Added Flyway migration `V16__punishment_decay_eligibility_snapshots.sql`.
- Added no runtime configuration keys, permission nodes, environment variables, provider dependencies or operational-mode changes.
- Did not edit `reason-policies.yml`; tests verify representative explicit eligible and non-decaying default policies.
- Did not edit V1–V15 or use Flyway repair.

## Validation commands and environment

The repository Coverage workflow uses Temurin Java 21 and runs the exact checked-out SHA with:

```text
chmod +x gradlew
./gradlew clean build jacocoAggregateReport runtimeJars \
  --no-daemon \
  --no-build-cache \
  --no-configuration-cache \
  --console=plain
```

The workflow validates the Gradle wrapper, runs the complete unit and MariaDB/Testcontainers suites, generates aggregate JaCoCo coverage, builds and inspects the Paper and Velocity runtime JARs, checks provider-source boundaries, uploads configured validation artifacts and coverage/static-analysis evidence, and records the tested SHA. Wiki validation runs separately on the exact PR head. Exact successful run/job IDs and results belong in the live PR evidence comment and are not embedded here to avoid changing the validated feature SHA.

## Focused tests

- `EscalationEngineTest`: 89/90/180-day boundaries, latest-related reset, mixed eligibility, serious history under a later eligible policy, minor history under a later non-decaying policy, and legacy unknown behavior.
- `PunishmentDecayMetadataServiceTest`: the authoritative service path carries both eligible and ineligible creating-policy metadata into the committed plan.
- `PunishmentDecayEligibilityIntegrationTest`: restart persistence, later-policy changes, legacy null loading and database range enforcement.
- `PunishmentDecayV16MigrationIntegrationTest`: populated V15-to-V16 upgrade preservation with historical eligibility remaining null.
- `ReasonPolicyConfigurationLoaderTest`: representative minor eligible and serious non-decaying default policy values.
- Existing recommendation-snapshot integration coverage remains explicit about its decay metadata fixture.

## Separate harsh review findings

One confirmed defect was fixed:

1. Initial persistence tests constructed `PunishmentPlan` directly and did not prove that `PunishmentService` copied the creating policy's decay setting into the accepted plan. `PunishmentDecayMetadataServiceTest` now verifies both values through the authoritative service path.

The full PR review also rechecked clean-period boundaries, latest-offense reset semantics, mixed eligible/ineligible histories, legacy behavior, transaction placement, migration immutability, database constraints, configuration values and preserved production boundaries. All review threads and valid review-body findings must be resolved against the final exact head. Any later tracked change invalidates successful exact-head evidence and requires a fresh run.

## Preserved boundaries

This work did not deploy, access production, activate EnthusiaStaff authority, alter/disable/remove LiteBans, edit V1–V15, use Flyway repair, push directly to `main`, rebase, squash, force-push, enable automatic merge, invent RoseChat APIs, implement wider combined recommendations, add family-relationship graphs, complete modular configuration, finish decayed-history GUI presentation, or satisfy issue #43 production acceptance.

## Merge gate

Merge only when one unchanged exact head is synchronized with current `main` and has successful Java 21 clean build, all unit and MariaDB/Testcontainers tests, clean-install and V15-to-V16 migration coverage, migration checksum protection, runtime-JAR inspection, aggregate coverage, configured static analysis, wiki validation, zero unresolved valid review threads and resolved external-review findings. Record exact evidence in PR metadata without changing the feature SHA. Use a normal merge commit, verify resulting `main`, and delete the feature branch only if a safe available tool supports deletion.

## Next work

After PR #54 is fully merged and verified, stop. Reconcile live state again. Resume RoseChat only if a supported contract exists; otherwise select exactly one bounded follow-up. Wider combined-recommendation coverage, explicit family relationships, broader modular punishment/escalation configuration, decayed-history GUI presentation and representative multi-runtime acceptance remain separate.

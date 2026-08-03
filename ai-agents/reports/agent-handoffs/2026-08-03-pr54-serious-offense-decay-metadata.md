# PR #54 handoff — serious-offense decay eligibility metadata

Date: 2026-08-03 (America/Indiana/Indianapolis)

Repository: `wsg138/EnthusiaStaff`

Pull request: [#54 — Preserve serious-offense decay eligibility in escalation history](https://github.com/wsg138/EnthusiaStaff/pull/54)

Branch: `feature/serious-offense-decay-metadata`

Starting `main`: `fc1e94bd7317d59a33d297a049a94fd2eb3f1c5e`

This is the one canonical handoff for PR #54. Verify the live feature SHA, terminal workflow and Pi results, artifacts, review state, merge result, resulting `main` and branch cleanup directly on GitHub.

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
- V16 adds nullable decay eligibility to both `punishment_steps` and durable `punishment_requests`; V1–V15 remain byte-identical.
- `JdbcModerationStore` writes punishment-step metadata in the same transaction as the case, recommendation snapshot, actual sanctions, audit and outboxes.
- Durable punishment requests store and decode the creating policy's eligibility so a request approved after restart preserves the original value.
- Related-history and request reads map `TRUE` to eligible, `FALSE` to ineligible, and legacy `NULL` to unknown.
- The shared clean-period clock remains based on the latest contributing, non-overturned related offense.
- Each complete 90-day clean interval reduces only contributions stored as eligible.
- Explicitly non-decaying serious history remains effective under later eligible/minor policies.
- Eligible minor history may still decay when the later selected reason is non-decaying.
- Legacy rows do not decay because reconstructing an old policy from current configuration would invent historical evidence; no legacy row is backfilled or rewritten.
- Existing recommendation snapshots, applied sanctions, expiration/lifecycle state, aliases, removed IDs and visibility behavior remain unchanged.

## Configuration and schema changes

- Added Flyway migration `V16__punishment_decay_eligibility_snapshots.sql` for punishment steps and pending punishment requests.
- Added no runtime configuration keys, permission nodes, environment variables, provider dependencies or operational-mode changes.
- Did not edit `reason-policies.yml`; tests verify representative explicit eligible and non-decaying default policies.
- Did not edit V1–V15 or use Flyway repair.

## Focused tests

- `EscalationEngineTest`: 89/90/180-day boundaries, latest-related reset, mixed eligibility, serious history under a later eligible policy, minor history under a later non-decaying policy, and legacy unknown behavior.
- `PunishmentDecayMetadataServiceTest`: separate directly asserted tests verify the authoritative service path carries eligible and ineligible creating-policy metadata into the committed plan.
- `PunishmentDecayEligibilityIntegrationTest`: punishment restart persistence, durable-request restart and approval persistence, later-policy changes, legacy null loading and database range enforcement for both snapshot tables.
- `PunishmentDecayV16MigrationIntegrationTest`: populated V15-to-V16 upgrade preservation and both V16 columns.
- `ReasonPolicyConfigurationLoaderTest`: representative minor eligible and serious non-decaying default policy values.
- Existing recommendation-snapshot integration coverage remains explicit about its decay metadata fixture.

## Separate harsh review findings

Eight confirmed defects, workflow defects, or analyzer findings were fixed:

1. Initial persistence tests constructed `PunishmentPlan` directly and did not prove that `PunishmentService` copied the creating policy's decay setting into the accepted plan. `PunishmentDecayMetadataServiceTest` now verifies both values through the authoritative service path.
2. The requirements matrix still routed the next agent to RoseChat or another escalation slice after the owner changed priorities. It now routes staff mode/vanish/freeze first, report notifications second, and escalation third.
3. The agent workflow could treat a configured but untriggered Pi workflow as absent. It now requires trigger/re-run or a verified documented exception; a missing trigger is not non-applicability.
4. The universal prompt could direct a review-only work item into implementation validation and merge. Review-only work now ends after reporting findings unless the user separately requests implementation.
5. Durable punishment requests decoded through the compatibility constructor and lost the creating policy's decay eligibility before approval. V16, the JDBC request repository/codec, restart tests, legacy-null handling and approval persistence now preserve it explicitly.
6. Static analysis rejected the eligibility persistence switches. Both database write paths now use explicit `UNKNOWN` handling followed by a boolean write for known values.
7. Static analysis rejected the side-effecting `index++` argument in the punishment-request insert binding. The parameter is now written first and incremented on the next statement.
8. Static analysis required direct assertions in each JUnit test. Eligible and ineligible policy behavior are now separate test methods with direct assertions.

The complete diff must be reviewed once more at the resulting exact head before merge. All valid review threads must be resolved and any later tracked change requires a fresh exact-head validation cycle.

## Workflow documentation and routing

The tracked workflow documentation:

- keeps this one canonical PR #54 handoff;
- records the expected post-merge state as `IDLE — PR #54 requires live merge verification`;
- records owner priorities: staff mode/vanish/freeze, then report notification completion, then escalation-policy completion;
- records the two-consecutive-internal-PR guardrail and the allowed correctness/data-integrity exception for PR #54;
- states that issue #43 is specifically the LiteBans production-cutover acceptance issue, not a general blocker queue;
- routes unavailable provider APIs to focused blocker issues and the normal handoff rather than standalone documentation PRs;
- documents Coverage/Pi timing, supersession, final-head freezing, trigger-or-block handling, terminal Pi requirements for implementation PRs and the docs-only distinction;
- separates implementation workflows from explicitly requested review-only work.

## Validation contract

The repository Coverage workflow uses Temurin Java 21 and runs the exact checked-out SHA with:

```text
chmod +x gradlew
./gradlew clean build jacocoAggregateReport runtimeJars \
  --no-daemon \
  --no-build-cache \
  --no-configuration-cache \
  --console=plain
```

Coverage and Pi may take roughly ten minutes. A newer commit can cancel or supersede an earlier run; cancelled and superseded runs are neither failures nor validation evidence. After the final analyzer-fix commit, make no further commits unless final validation exposes another real defect.

Final exact-head evidence must be recorded in PR #54, not in this tracked file. It must include the final feature SHA, Coverage and Wiki run/job IDs, Java/build/test/migration results, coverage, runtime-JAR hashes and artifact identity, provider-leak checks, Codacy state, review-thread count, and the terminal exact-head Pi result or a verified documented exception proving an applicable workflow cannot be triggered or applied.

## Blocked-work and production boundaries

- Issue #43 remains open specifically for LiteBans production-cutover acceptance; it is not the general defect or blocker queue.
- The unavailable RoseChat provider contract is an external blocker and should normally be tracked in a focused issue plus the normal handoff.
- Do not open a documentation-only PR solely to record an external blocker unless routing would otherwise become materially incorrect or unsafe.
- This work did not deploy, access production, activate EnthusiaStaff authority, alter/disable/remove LiteBans, edit V1–V15, use Flyway repair, push directly to `main`, rebase, squash, force-push, enable automatic merge, invent RoseChat APIs, implement wider combined recommendations, add family-relationship graphs, complete modular configuration, finish decayed-history GUI presentation, or satisfy issue #43 production acceptance.

## Merge gate

Merge only when one unchanged exact head is synchronized with current `main` and has successful Java 21 clean build, all unit and MariaDB/Testcontainers tests, clean-install and V15-to-V16 migration coverage, migration checksum protection, runtime-JAR inspection, provider-leak checks, aggregate coverage, configured static analysis, wiki validation, successful terminal exact-head Pi when configured and applicable or a verified documented exception, zero unresolved valid review threads and resolved external-review findings. Record exact evidence in PR metadata without changing the feature SHA. Use a normal merge commit, verify resulting `main`, verify feature-head containment, and verify automatic branch deletion.

## Next work

After PR #54 is fully merged and verified, stop. The next owner-priority workstream is staff mode, vanish, and freeze. Report notification completion is second. Escalation-policy completion is third, and another escalation-policy slice must not start immediately after PR #54 without a qualifying correctness/security/concurrency/migration/data-integrity reason, a direct higher-priority unblock, or explicit owner approval.

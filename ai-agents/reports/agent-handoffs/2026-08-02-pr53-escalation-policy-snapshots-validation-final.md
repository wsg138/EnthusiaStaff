# PR #53 validation-final handoff — escalation recommendation snapshots

Date: 2026-08-02 (America/Indiana/Indianapolis)

Repository: `wsg138/EnthusiaStaff`

Pull request: [#53 — Preserve escalation recommendation snapshots across ladder edits](https://github.com/wsg138/EnthusiaStaff/pull/53)

Branch: `feature/escalation-policy-snapshots`

Starting `main`: `49ee42c142ccd9e66b7b5fed2c30fc5b4094a052`

This immutable report supersedes all earlier PR #53 handoffs. Earlier reports remain unedited historical checkpoints. Verify the live feature SHA, workflow results, review state, merge result, resulting `main` and branch cleanup directly on GitHub.

## Work item and implementation

PR #53 preserves the exact configured escalation recommendation and selected ladder ordinal for new policy-created cases without rewriting legacy history.

- V15 adds nullable `selected_ordinal` and `recommended_sanctions_json` to `punishment_steps`; V1–V14 remain byte-identical.
- A MariaDB check constraint requires both snapshot fields to be null together for legacy rows or populated together for new rows.
- New cases persist raw, effective and selected ordinals, configuration version, selected label, contribution details and exact recommended sanctions in the same transaction as actual sanctions, audit and outboxes.
- Recommendation snapshots use the established strict sanction codec.
- Applied sanctions remain separate and authoritative for type, issue time, expiration and lifecycle.
- Legacy rows remain explicitly snapshot-unavailable; no recommendation is inferred from applied sanctions.
- Domain and JDBC review paths reject malformed or incomplete snapshots.
- `/case` displays the frozen policy snapshot before the actual sanctions and renders temporary recommendation durations as staff-readable days, hours, minutes and seconds instead of ISO-8601 duration text.
- Current policy calculations continue using the current ladder and clamp out-of-range ordinals to the current final step.

## Configuration and schema changes

- Added Flyway migration `V15__punishment_recommendation_snapshots.sql`.
- Added no runtime configuration keys, permission nodes, environment variables, provider dependencies or operational-mode changes.
- Did not edit `reason-policies.yml`; existing policy publication and reload behavior is unchanged.
- Did not edit V1–V14 or use Flyway repair.

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

The workflow also validates the Gradle wrapper, runs the complete unit and MariaDB/Testcontainers suites, generates aggregate JaCoCo coverage, builds and inspects the runtime JARs, checks provider-source boundaries, uploads configured validation artifacts and coverage/static-analysis evidence, and records the tested SHA. Wiki validation runs separately on the exact PR head. Exact successful run/job IDs and results belong in the live PR evidence comment and are not embedded here to avoid changing the validated feature SHA.

## Focused tests

- `ReasonPolicyLadderEditTest`: edited-ladder interpretation and final-step clamping.
- `PunishmentStepReviewTest`: valid-pair construction, empty recommendation rejection and both incomplete-pair rejection paths.
- `PunishmentRecommendationSnapshotIntegrationTest`: restart persistence, raw/effective ordinal eight with selected ordinal two, seven-day recommendation versus thirty-day applied override, legacy null behavior, database pair enforcement and corrupt snapshot rejection.
- `PunishmentRecommendationV15MigrationIntegrationTest`: populated V14-to-V15 upgrade preservation with both legacy snapshot fields remaining null.
- `CaseCommandDurationTest`: whole-day, mixed-part, zero and negative-duration staff presentation behavior.

## Separate harsh review, CI and external review findings

Ten confirmed or valid review findings were fixed:

1. Effective ordinal alone left finite-ladder clamping ambiguous; selected ordinal is stored separately.
2. Generic Jackson serialization did not follow the established sanction snapshot schema; the strict existing codec is reused.
3. Independently nullable snapshot fields allowed one-sided rows; database, domain and JDBC invariants enforce a complete pair.
4. An intermediate requirements-matrix rewrite omitted its final rows and execution order; the original tail was restored.
5. Exact-head Coverage run `30782286201` on `7a01745d747aa52778d6ee723a2401de0ab9967d` found four invalid Crockford test IDs containing `O`; fixtures now use valid 16-digit identifiers. That failed run is failure evidence only.
6. External review found the restart test expected raw ordinal `2` although the constructed decision persists raw ordinal `8`; the assertion now verifies `8`, while selected ordinal remains `2`.
7. External review found stale active PR #37 instructions in the requirements matrix; PR #37 is now historical evidence only and active validation routes solely to PR #53.
8. External review found earlier immutable handoffs omitted the starting SHA, exact validation command and configuration-change section; superseding reports supply them and leave older reports unedited as historical exceptions.
9. CodeRabbit's review-body finding identified ISO `Duration.toString()` output such as `PT168H` in staff-facing case history; temporary recommendations now use a tested human-readable formatter.
10. CodeRabbit's review-body finding identified incomplete constructor-test coverage; valid snapshot pairs and empty recommendation lists are now explicitly tested.

All review threads and valid review-body findings must be resolved against the final exact head. Any later tracked change invalidates successful exact-head evidence and requires a fresh run.

## Failed historical validation

Coverage run `30782286201`, job `91589254050`, checked out exact SHA `7a01745d747aa52778d6ee723a2401de0ab9967d` under Temurin `21.0.11+10`. Domain and compilation steps progressed, but `PunishmentRecommendationSnapshotIntegrationTest` recorded four fixture-validation failures before database behavior ran. The job reported 143 integration tests with four failures and skipped later success-only packaging/coverage steps. It must never be cited as passing evidence.

## Preserved boundaries

This work did not deploy, access production, activate EnthusiaStaff authority, alter/disable/remove LiteBans, edit V1–V14, use Flyway repair, push directly to `main`, rebase, squash, force-push, enable automatic merge, invent RoseChat APIs, implement serious-offense decay, widen combined recommendations, complete modular configuration or satisfy issue #43 production acceptance.

## Merge gate

Merge only when one unchanged exact head is synchronized with current `main` and has successful Java 21 clean build, all unit and MariaDB/Testcontainers tests, clean-install and V14-to-V15 migration coverage, migration checksum protection, runtime-JAR inspection, aggregate coverage, configured static analysis, wiki validation, zero unresolved valid review threads and resolved external-review findings. Record exact evidence in PR metadata without changing the feature SHA. Use a normal merge commit, verify resulting `main`, and delete the feature branch only if a safe available tool supports deletion.

## Next work

After PR #53 is fully merged and verified, stop. Reconcile live state again. Resume RoseChat only if a supported contract exists; otherwise serious-offense decay metadata is the current likely bounded escalation follow-up. Wider combined recommendations and broader modular configuration remain separate.

# ES-X03 — EnthusiaMarket destructive provider

## 1. Package identity

**ES-X03**; external/multi-repository; primary **COMP-STAFF**; other
**COMP-MARKET**; priority 120. Keep destructive-state work serialized with
other packages that touch the same Market/Staff persistence or authority paths.

## 2. Status

**PARTIAL / FINAL_VALIDATION_PENDING.** The implementation has been rebuilt on
current Staff `main`, synchronized to the authoritative current-upstream Market
candidate, and passed the full clean aggregate build. Hosted final-head gates
remain mandatory before merge.

## 3. Objective

Provide durable market restriction, reservation, confiscation, rollback, and
exact restoration across EnthusiaStaff and EnthusiaMarket, with typed provider
contracts and restart-safe Staff orchestration.

## 4. Current source checkpoint

- Staff `main`: `e535a425fa351a78f8b4248853f1b57c3ccecd1e`.
- Staff package PR: `wsg138/EnthusiaStaff#139`, branch
  `package/es-x03-market-provider`.
- Market upstream baseline: `BadgersMC/EnthusiaMarket@14351db4dc416138341a11d0e4e27602f207221b`
  after Staff integration PR #194 and ownership-integrity PR #195.
- Authoritative current-upstream Market cleanup:
  `wsg138/EnthusiaMarket:integration/badgers-staff-market@755d81042a9a53aee7184cce8eb2bf256fe7165c`.
- Historical Market PR #7 is closed/superseded and must not be replayed.
- Preserved unpaired Market PR #6 remains separate and is not folded into X03.

## 5. Aggregate/provider parity

Reconciliation run `36744990024` rebuilt X03 from exact current Staff
`main`, replayed only the prior scoped X03 Staff delta, and mirrored exact
Market `755d81042a9a53aee7184cce8eb2bf256fe7165c`. `component_sync.py compare` reported:

- parity: `true`;
- aggregate hash: `999fc8b40c7a808d417aab33b46f66f01598664f205c13854c145c72c7ba7f03`;
- standalone hash: `999fc8b40c7a808d417aab33b46f66f01598664f205c13854c145c72c7ba7f03`;
- added/missing/modified product paths: none.

`COMPONENT-METADATA.md` remains aggregate-only metadata and is excluded from the
product hash by design.

## 6. Staff-owned behavior

The Staff leg includes the typed Market moderation contract, durable compliance
journal and V21 migration, provider discovery/version checks, recovery and
reconciliation orchestration, `/marketcase`, and the minimal Paper/storage
wiring needed to expose those surfaces. Public response construction remains
separate from persistence/provider mutation paths.

The package preserves the existing migration boundary: Staff owns V20, X03
owns forward-only V21, and D09 retains V22.

## 7. Validation completed before promotion

Exact reconstruction run `36744990024` succeeded on Java 21 with caches
disabled for the build command:

`./gradlew clean build jacocoAggregateReport runtimeJars --no-daemon --no-build-cache --no-configuration-cache --console=plain`

The run executed the aggregate unit/integration suites, including Paper,
persistence, protocol, staff-bot, Velocity, authority bridge, MariaDB-backed
integration tests, JaCoCo aggregation, and runtime JAR inspection. It completed
`BUILD SUCCESSFUL` with 71 actionable tasks (60 executed, 11 up-to-date).

Staff-specific coverage includes intent-before-provider mutation, provider
outage recovery, authorization-required confiscation approval, fail-closed
non-active mode, concurrent idempotency, restart persistence, backward-state
conflict, and one-shot review alerts. The mirrored Market suite supplies the
provider-side rejection, rollback, conflict, and persistence coverage.

Staff-owned diff hygiene passed. The mirrored Market product tree is preserved
byte-for-byte; intentional Markdown hard-break whitespace inside that external
component is validated by standalone parity rather than rewritten in Staff.

## 8. Remaining acceptance gates

Do **not** merge until the promoted exact PR head is unchanged and all applicable
hosted checks are terminal green. In particular:

- Coverage/full build and runtime artifact checks;
- hosted Codacy static analysis with **zero new valid findings**;
- review/Sentinel artifact and restart checks where configured;
- canonical private staging/Pi evidence where the package requires it;
- no unresolved valid review finding.

Old Codacy or staging results from superseded heads are not final evidence.
False positives must be documented individually; broad exclusions or weakened
quality gates are not acceptable substitutes.

## 9. Completion definition

After normal merges, verify exact standalone-to-aggregate Market parity again,
update component metadata to the merged SHAs, and only then mark ES-X03
complete. Representative destructive/load acceptance remains owned by ES-V03.

## 10. Production boundary

This package preparation does not mutate production listings, balances, items,
player data, databases, deployments, Discord configuration, LiteBans authority,
website state, or cutover state.

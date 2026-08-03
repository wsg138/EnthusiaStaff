# PR #53 handoff — escalation recommendation snapshots

Date: 2026-08-02 (America/Indiana/Indianapolis)

Repository: `wsg138/EnthusiaStaff`

Pull request: [#53 — Preserve escalation recommendation snapshots across ladder edits](https://github.com/wsg138/EnthusiaStaff/pull/53)

Branch: `feature/escalation-policy-snapshots`

This report is an immutable development handoff. Verify every live SHA, workflow, review, merge and branch-cleanup fact directly on GitHub before acting.

## Work-item decision

PR #52 was already merged when this agent began. Its exact feature head `ac08bcce7281caf6425393213c5ef4d48cd99b3e` was contained in normal merge commit `49ee42c142ccd9e66b7b5fed2c30fc5b4094a052`, its exact-head Coverage and Validate Wiki workflows succeeded, and it had zero unresolved review threads.

No pull request was open or in draft. Every pre-existing non-main branch was verified `ahead_by: 0` relative to `main`, so no unfinished work was displaced. The live highest Flyway migration was V14. The goals, workspace manifest, development blueprint, requirements matrix and implementation all identified explicit policy-snapshot behavior across ladder edits as the next prerequisite-ready bounded item while the RoseChat provider contract remained unavailable.

## Confirmed gap

A committed case stored the configuration version, raw/effective ordinal, selected label and sanctions actually applied. It did not store the exact configured recommendation. An authorized override could therefore replace the only durable sanction representation, and a later ladder edit made the original expectation impossible to reconstruct truthfully.

The effective ordinal was also insufficient by itself for finite ladders. An out-of-range effective ordinal may clamp to the final configured step, whose selected ordinal is lower.

## Complete implementation

PR #53 adds `V15__punishment_recommendation_snapshots.sql` with two nullable fields on `punishment_steps`:

- `selected_ordinal` stores the actual configured step ordinal chosen by the ladder;
- `recommended_sanctions_json` stores the exact selected step sanction specifications using the established strict sanction snapshot format.

For every new policy-created case, `JdbcModerationStore` writes configuration version, raw ordinal, effective ordinal, selected ordinal, step label, contribution details and recommended sanctions in the same transaction as the case, actual sanctions, audit and outbox records. Approved punishment requests reuse that same transaction path.

`JdbcCaseReviewStore` exposes the stored selected ordinal and recommendation through `PunishmentStepReview`. `MariaDbRuntime` wires the strict decoder. `/case` history shows the policy version, raw/effective ordinal, selected ordinal, label and frozen recommendation before listing the actual sanctions.

The exact recommendation remains separate from the actual applied result. An authorized override can change type or duration without changing historical expectation, while the actual sanction row remains authoritative for issue time, expiration and lifecycle state.

V14 and older rows remain null for both new fields. Review presentation labels those snapshots unavailable and does not infer them from applied sanctions. Malformed non-null snapshots fail closed.

Current policies continue using the current ladder for future calculations. Focused domain coverage proves a stored ordinal maps to current edited step content and that an ordinal beyond a shortened finite ladder selects the current final step.

## Tests added

- `ReasonPolicyLadderEditTest`
  - current policy interprets a stored ordinal with current step content;
  - out-of-range ordinals clamp to the current final step.
- `PunishmentRecommendationSnapshotIntegrationTest`
  - persists raw/effective ordinal eight and selected ordinal two;
  - preserves a seven-day configured recommendation while the applied override expires after thirty days;
  - survives runtime close/reopen;
  - retains legacy null semantics;
  - rejects a corrupt empty snapshot.
- `PunishmentRecommendationV15MigrationIntegrationTest`
  - migrates a populated V14 schema to V15;
  - preserves historical raw/effective ordinal, label, contributions and escalation contribution state;
  - leaves selected ordinal and recommendation null instead of inventing history.

The existing clean-install integration path exercises migration through the latest schema. The deployed V11–V13 checksum lock remains unchanged, and V1–V14 were not edited.

## Separate harsh review

The entire PR was reviewed independently for migration safety, append-only behavior, transaction atomicity, approved-request reuse, historical truthfulness, override separation, finite-ladder clamping, malformed JSON, restart behavior, staff-only presentation, provider boundaries, test adequacy and documentation accuracy.

Two confirmed defects were fixed:

1. **Selected-step ambiguity:** the first implementation stored only effective ordinal and recommendation JSON. Because a finite ladder can clamp effective ordinal eight to selected ordinal two, the exact historical ladder position remained ambiguous. V15, persistence, review presentation and tests now store `selected_ordinal` separately.
2. **Unstable snapshot encoding:** the first implementation used generic Jackson record serialization. That did not follow the repository's established sanction snapshot schema and risked incompatible `Optional<Duration>` handling. The final implementation reuses `PunishmentDraftSanctionCodec` for strict type/kind/duration encoding and decoding.

No valid review finding may remain unresolved before merge. Any tracked change after exact-head validation invalidates that validation and requires a new exact-head run.

## Documentation and routing updated

- `docs/database.md` documents V15 and the no-inference legacy boundary.
- `reports/REQUIREMENTS-MATRIX.md` records this slice while retaining conservative `PARTIAL` escalation status.
- `docs/wiki/pages/Development-Blueprint.md` routes live closure to PR #53 and keeps the next slice separate.
- `WORKSPACE-MANIFEST.md` records implementation, harsh-review and production boundaries.
- `ai-agents/WORKSPACE-STATE.md` routes the next agent to live PR #53 evidence.
- `ai-agents/reports/agent-handoffs/latest.md` points to this report.

## Preserved boundaries

This work did not:

- deploy a plugin or access production systems;
- activate EnthusiaStaff authority;
- disable, modify or remove LiteBans;
- access production data, credentials, private evidence or backups;
- edit V1–V14 or use Flyway repair;
- push directly to `main`, rebase, squash, force-push or enable automatic merge;
- invent or reflect against an unavailable RoseChat API;
- implement serious-offense decay metadata, wider combined-recommendation behavior or broader modular configuration;
- complete issue #43 or claim production acceptance.

## Exact validation and merge contract

Read PR #53 live for the final feature SHA and evidence. Before merge, require all of the following on one unchanged exact head:

- branch synchronized with current `main`;
- Java 21 clean build and complete unit/integration test suite;
- MariaDB/Testcontainers clean-install and V14-to-V15 upgrade tests;
- migration checksum protection;
- aggregate coverage and configured static analysis;
- exactly one valid Paper and one valid Velocity runtime JAR with no provider-source leak;
- documentation/wiki validation as triggered;
- zero unresolved valid review threads;
- every external analyzer/reviewer finding resolved or explicitly disproved with evidence;
- exact evidence recorded in the PR without changing the feature SHA.

Use a normal merge commit only. After merge, verify the resulting `main`, feature-head containment and post-merge checks. Delete the feature branch only if the available tool exposes safe branch deletion; otherwise record that cleanup blocker accurately.

## Next legitimate work

After PR #53 is fully merged and verified, do not continue implementation in this PR. Reconcile live GitHub and repository state again. Resume RoseChat only if a supported contract becomes available. Otherwise, serious-offense decay metadata is the current likely bounded escalation follow-up, while wider combined recommendations and broader modular configuration remain separate.

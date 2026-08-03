# PR #53 final handoff — escalation recommendation snapshots

Date: 2026-08-02 (America/Indiana/Indianapolis)

Repository: `wsg138/EnthusiaStaff`

Pull request: [#53 — Preserve escalation recommendation snapshots across ladder edits](https://github.com/wsg138/EnthusiaStaff/pull/53)

Branch: `feature/escalation-policy-snapshots`

This is the superseding immutable handoff for PR #53. The earlier same-day report records the pre-final-review checkpoint and must not be edited. Verify every live SHA, workflow, review, merge and branch-cleanup fact directly on GitHub before acting.

## Work-item selection

PR #52 was already merged when work began. Its exact feature head `ac08bcce7281caf6425393213c5ef4d48cd99b3e` was contained in normal merge commit `49ee42c142ccd9e66b7b5fed2c30fc5b4094a052`; exact-head Coverage and Validate Wiki succeeded and no review thread remained unresolved.

No pull request was open or in draft. Every pre-existing non-main branch was `ahead_by: 0` relative to `main`, so no unfinished work was displaced. V14 was the live highest migration. The goals, workspace records, development blueprint, requirements matrix and current code all identified explicit recommendation snapshots across ladder edits as the next bounded prerequisite-ready escalation item while the supported RoseChat provider contract remained unavailable.

## Confirmed gap

Committed cases preserved configuration version, raw/effective ordinal, selected label and the sanctions actually applied, but not the exact configured recommendation. An authorized override could therefore replace the only durable sanction representation, and later policy edits made the original expectation impossible to reconstruct truthfully.

Effective ordinal alone was not enough for finite ladders because an out-of-range value can clamp to a lower final-step ordinal.

## Final implementation

`V15__punishment_recommendation_snapshots.sql` adds nullable `selected_ordinal` and `recommended_sanctions_json` columns to `punishment_steps`. A check constraint requires both fields to be null together for legacy rows or populated together for new rows.

Every new policy-created case writes the selected configured step ordinal and exact recommended sanction specifications in the same transaction as the case, applied sanctions, audit and outbox records. Approved punishment requests reuse the same `JdbcModerationStore.createPunishment` path.

The recommendation is encoded with the repository's existing strict sanction snapshot schema. `JdbcCaseReviewStore` decodes it and rejects malformed or one-sided snapshots. `PunishmentStepReview` preserves immutable optional selected ordinal and recommendation values and enforces the same pair invariant.

`/case` history presents configuration version, raw/effective ordinal, selected ordinal, selected label and frozen recommendation before the sanctions actually applied. An authorized override remains visible only in the actual sanction list; it does not mutate the historical recommendation. Actual sanction type, issue time, expiration and lifecycle state remain authoritative.

V14 and older rows keep both new fields null. They remain readable and are explicitly presented as snapshot unavailable. The implementation never infers a recommendation from applied sanctions.

Current policies still calculate future recommendations against the current ladder. Tests establish that an ordinal maps to current edited step content and that an ordinal beyond a shortened finite ladder selects the current final step.

## Final test additions

- `ReasonPolicyLadderEditTest`
  - current policy interprets a prior ordinal using current ladder content;
  - an out-of-range ordinal uses the current final step.
- `PunishmentStepReviewTest`
  - selected ordinal without recommendation is rejected;
  - recommendation without selected ordinal is rejected.
- `PunishmentRecommendationSnapshotIntegrationTest`
  - raw/effective ordinal eight and selected ordinal two survive restart;
  - a seven-day configured recommendation remains distinct from a thirty-day applied override;
  - legacy both-null snapshots remain explicitly unavailable;
  - MariaDB rejects an incomplete snapshot pair;
  - corrupt non-null snapshot JSON fails closed.
- `PunishmentRecommendationV15MigrationIntegrationTest`
  - populated V14 schema upgrades to V15;
  - raw/effective ordinal, label, contributions and escalation state remain unchanged;
  - legacy selected ordinal and recommendation remain null rather than being invented.

The existing clean-install suite migrates through the latest schema. The V11–V13 deployed checksum lock remains unchanged. V1–V14 were not edited.

## Separate harsh review

The complete PR was reviewed independently for migration safety, append-only behavior, database invariants, transaction atomicity, request-approval reuse, historical truthfulness, override separation, finite-ladder clamping, strict serialization, malformed and partial snapshots, restart behavior, staff-only presentation, provider boundaries, test adequacy and documentation integrity.

Four confirmed defects were fixed:

1. **Selected-step ambiguity:** storing only effective ordinal left clamped finite-ladder history ambiguous. V15, persistence, review presentation and tests now preserve `selected_ordinal` separately.
2. **Unstable snapshot encoding:** generic Jackson record serialization did not use the established sanction schema and risked incompatible optional-duration handling. The implementation now reuses `PunishmentDraftSanctionCodec`.
3. **Incomplete snapshot pairs:** independently nullable fields could expose one side without the other. V15 now enforces a check constraint, the domain record enforces the invariant, the JDBC reader fails closed, and focused tests cover both layers.
4. **Requirements-matrix truncation:** the first targeted rewrite omitted the matrix's final three rows and immediate execution order. The original tail was restored; the final matrix diff contains only the intended seven line replacements.

No unresolved valid finding may remain before merge. Any tracked change after exact-head validation invalidates that evidence and requires a fresh run.

## Documentation and routing

- `docs/database.md` documents V15, legacy null behavior and no-inference semantics.
- `reports/REQUIREMENTS-MATRIX.md` records the implemented slice while retaining conservative `PARTIAL` status.
- `docs/wiki/pages/Development-Blueprint.md` routes closure to PR #53 and keeps future escalation slices separate.
- `WORKSPACE-MANIFEST.md` records the final implementation, four harsh-review fixes and production boundary.
- `ai-agents/WORKSPACE-STATE.md` routes the next agent to live PR #53 evidence.
- `ai-agents/reports/agent-handoffs/latest.md` points to this superseding report.

## Preserved boundaries

This work did not:

- deploy a plugin, access production systems or inspect production data;
- activate EnthusiaStaff authority;
- disable, modify or remove LiteBans;
- edit V1–V14 or use Flyway repair;
- push directly to `main`, rebase, squash, force-push or enable automatic merge;
- invent or reflect against an unavailable RoseChat API;
- implement serious-offense decay metadata, wider combined recommendations or broader modular configuration;
- complete issue #43 or claim staging/production acceptance.

## Exact-head validation and merge contract

Read PR #53 live for the final feature SHA and evidence. Merge only after one unchanged exact head has:

- zero commits behind current `main`;
- successful Java 21 clean build and complete unit/integration suites;
- successful MariaDB/Testcontainers clean-install and V14-to-V15 upgrade coverage;
- successful migration checksum protection;
- successful aggregate coverage and configured static analysis;
- exactly one valid Paper and one valid Velocity runtime JAR with no provider-source leak;
- successful documentation/wiki validation as triggered;
- zero unresolved valid review threads;
- all analyzer and reviewer findings resolved or disproved with direct evidence;
- exact evidence recorded in the PR without changing the feature SHA.

Use a normal merge commit only. After merge, verify the resulting `main`, feature-head containment and post-merge workflows. Delete the feature branch only when the available tool safely supports branch deletion; otherwise record the cleanup limitation accurately.

## Next legitimate work

After PR #53 is merged and verified, stop. Reconcile live GitHub and repository state again before selecting another item. Resume RoseChat only if a supported contract becomes available. Otherwise serious-offense decay metadata is the current likely bounded escalation follow-up; wider combined recommendations and broader modular configuration remain separate.

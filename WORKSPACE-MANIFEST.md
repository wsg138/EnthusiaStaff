# EnthusiaStaff workspace manifest

Last updated: 2026-08-02 (America/Indiana/Indianapolis)

This manifest records development coordination and authority boundaries. It does not authorize deployment, production-data access, LiteBans cutover or a change in punishment authority.

## Repository checkpoint

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| `main` at PR #53 start | `49ee42c142ccd9e66b7b5fed2c30fc5b4094a052` |
| Latest merged product PR before current work | PR #52 — reason aliases and removed-ID presentation |
| Active work | PR #53 — escalation recommendation snapshots across ladder edits |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-02-pr53-escalation-policy-snapshots-ci-final.md` |
| Migration boundary | PR #53 adds V15; V1–V14 remain immutable |
| Dormant default | Startup remains non-`ACTIVE` |
| Production authority | **LiteBans remains authoritative** |

At PR #53 start there were no open pull requests and every pre-existing non-main branch was `ahead_by: 0` relative to `main`.

## Implementation checkpoint

PR #53 implements one bounded escalation-policy compatibility slice:

- new policy-created cases persist the exact configured recommendation independently from sanctions actually applied;
- raw, effective and selected ladder ordinals remain separate so finite-ladder clamping is historically unambiguous;
- V15 requires selected ordinal and recommendation JSON to be both null for legacy rows or both populated for new rows;
- recommendation snapshots use the established strict sanction codec;
- configuration version, selected label and recommendation survive restart and later ladder edits;
- authorized overrides do not replace historical recommendations, while actual sanction rows remain authoritative;
- legacy V14 and older cases remain explicitly snapshot-unavailable rather than reconstructed;
- malformed or incomplete snapshots fail closed;
- `/case` displays the frozen policy snapshot before actual sanctions;
- current policies still use the current ladder and clamp out-of-range ordinals to the current final step;
- V1–V14 and existing case, sanction, request, appeal, expiration and audit history are not rewritten.

Exact final-head validation, review and merge evidence belong in PR #53 live metadata.

## Harsh-review and CI checkpoint

Five confirmed defects were fixed:

1. effective ordinal alone could not identify a clamped selected step, so `selected_ordinal` is stored separately;
2. generic Jackson serialization did not use the established sanction schema, so `PunishmentDraftSanctionCodec` is reused;
3. nullable snapshot fields allowed one-sided rows, so database, domain and JDBC invariants enforce a complete pair;
4. an intermediate requirements-matrix rewrite omitted its final rows and execution order, which were restored;
5. failed exact-head Coverage run `30782286201` on `7a01745d747aa52778d6ee723a2401de0ab9967d` found four invalid Crockford test IDs containing `O`; the fixtures now use valid 16-digit identifiers. That run is failure evidence only.

Regression coverage targets ladder edits, out-of-range clamping, restart persistence, recommendation-versus-applied override separation, legacy null behavior, pair integrity, corrupt snapshots and V14-to-V15 upgrade preservation.

## Prior verified evidence

PR #52 exact head `ac08bcce7281caf6425393213c5ef4d48cd99b3e` passed Coverage `30780118437` and Validate Wiki `30780118455`, had zero unresolved review threads, and merged normally as `49ee42c142ccd9e66b7b5fed2c30fc5b4094a052`. Do not attribute prior-head evidence to PR #53.

## Provider blocker

The supported RoseChat private-message callback and privacy presentation boundary remains blocked because no accessible supported provider repository/API defines callback timing, identity, cancellation/delivery semantics, threading, duplicate behavior, versions and privacy-safe evidence fields. Do not invent an API, reflect against unknown classes, copy provider-owned classes or scrape logs as a substitute callback.

## Development merge gate

Merge PR #53 only after one unchanged exact head is synchronized with `main` and passes Java 21 build/tests, MariaDB/Testcontainers clean-install and V14-to-V15 upgrade checks, migration checksums, runtime-JAR inspection, aggregate coverage, configured static analysis, wiki validation and all review gates. Zero unresolved valid threads must remain. Record exact evidence in the PR without changing the feature SHA and use a normal merge commit.

## Production cutover gate

Issue #43 remains open. Before it is complete, do not deploy a production cutover candidate, begin a real shadow window, activate EnthusiaStaff authority, disable/remove LiteBans, perform final production migration or authorize live cutover.

## Related repositories

Provider and website repositories remain independent. Their histories must not be flattened into EnthusiaStaff, and provider API classes must not leak into Paper or Velocity runtime JARs. The intended RoseChat provider repository/API remains unavailable.

## Current route

1. Verify PR #53's exact live head, checks, reviews, normal merge result, resulting `main` and branch cleanup.
2. Resume RoseChat only after a supported contract exists.
3. Otherwise select exactly one prerequisite-ready escalation slice after fresh reconciliation; serious-offense decay metadata is the current likely candidate.
4. Stop after PR #53 and do not combine the next slice with it.

## Release boundaries

- Never combine evidence from different revisions.
- Keep credentials, private JARs, databases, logs and evidence out of Git.
- Never repair Flyway history or edit deployed migration bytes.
- Do not represent hosted tests or isolated staging as production acceptance.
- A merged pull request is a development checkpoint, not deployment authorization.

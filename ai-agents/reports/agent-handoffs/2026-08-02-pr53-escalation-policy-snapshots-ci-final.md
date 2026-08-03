# PR #53 CI-final handoff — escalation recommendation snapshots

Date: 2026-08-02 (America/Indiana/Indianapolis)

Repository: `wsg138/EnthusiaStaff`

Pull request: [#53 — Preserve escalation recommendation snapshots across ladder edits](https://github.com/wsg138/EnthusiaStaff/pull/53)

Branch: `feature/escalation-policy-snapshots`

This immutable report supersedes both earlier PR #53 handoffs. Verify all live SHA, workflow, review, merge, resulting-main and branch-cleanup evidence directly on GitHub before acting.

## Work item

PR #53 preserves the exact configured escalation recommendation and selected ladder ordinal for new policy-created cases without rewriting legacy history. It remains one bounded escalation-policy compatibility slice.

## Implementation

- V15 adds nullable `selected_ordinal` and `recommended_sanctions_json` to `punishment_steps` while leaving V1–V14 byte-identical.
- A database check constraint requires both snapshot fields to be null together for legacy rows or populated together for new rows.
- New cases persist raw ordinal, effective ordinal, selected ordinal, configuration version, selected label, contributions and exact recommended sanctions in the same transaction as the actual sanctions, audit and outboxes.
- Recommendation snapshots use the existing strict sanction codec.
- Actual applied sanctions remain separate and authoritative for type, issue time, expiration and lifecycle.
- Legacy rows remain explicitly snapshot-unavailable; no recommendation is inferred from applied sanctions.
- Domain and JDBC review models reject malformed or incomplete snapshots.
- `/case` history shows the frozen policy snapshot before the actual sanction list.
- Current policy calculations continue using the current ladder and clamp out-of-range ordinals to the current final step.

## Focused coverage

- edited-ladder interpretation and final-step clamping;
- selected-ordinal/recommendation pair invariants in domain and MariaDB;
- restart persistence;
- seven-day recommendation versus thirty-day applied override;
- legacy null behavior;
- corrupt snapshot rejection;
- populated V14-to-V15 upgrade preservation.

## Separate harsh review and CI findings

Five confirmed defects were fixed before the final tracked-content freeze:

1. effective ordinal alone left clamped finite-ladder history ambiguous, so selected ordinal is stored separately;
2. generic Jackson serialization did not follow the established sanction snapshot schema, so the strict existing codec is reused;
3. independently nullable snapshot fields allowed one-sided rows, so V15, the domain model and JDBC now enforce a complete pair;
4. an intermediate requirements-matrix rewrite omitted its final three rows and execution order, so the original tail was restored;
5. the first exact-head Coverage run `30782286201` on `7a01745d747aa52778d6ee723a2401de0ab9967d` found that four new test fixture IDs contained forbidden Crockford character `O`; all four tests failed before exercising database behavior. The fixtures now use valid 16-digit Crockford identifiers. The failed run is historical failure evidence only and must not be cited as validation success.

Any tracked change after the next successful exact-head validation invalidates that evidence and requires a fresh run.

## Preserved boundaries

This work did not deploy, access production, activate EnthusiaStaff authority, alter LiteBans, edit V1–V14, use Flyway repair, push to main, rebase, squash, force-push, enable automatic merge, invent RoseChat APIs, implement serious-offense decay, expand combined recommendations, complete modular configuration or satisfy issue #43 production acceptance.

## Merge gate

Read PR #53 live. Merge only when one unchanged exact head is synchronized with `main` and has successful Java 21 clean build, all unit and MariaDB/Testcontainers tests, clean-install and V14-to-V15 migration coverage, migration checksum protection, runtime-JAR inspection, aggregate coverage, configured static analysis, wiki validation, zero unresolved valid review threads and resolved external-review findings. Record exact evidence in PR metadata without changing the feature SHA, use a normal merge commit, verify resulting `main`, and delete the branch only if a safe tool is available.

## Next work

After PR #53 is fully merged and verified, stop. Reconcile live state again. Resume RoseChat only if a supported provider contract exists; otherwise serious-offense decay metadata is the current likely bounded escalation follow-up. Wider combined recommendations and broader modular configuration remain separate.

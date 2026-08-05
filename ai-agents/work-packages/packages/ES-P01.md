# `ES-P01` — Exact-sanction appeal isolation

## 1. Package identity

| Field | Value |
| --- | --- |
| Package ID | `ES-P01` |
| Type | Internal |
| Primary component | `COMP-STAFF` |
| Other components | — |
| Priority | `10` |
| Parallel safe | No |

## 2. Status

Canonical current status: `ACTIVE`. `PACKAGE-REGISTRY.md` is authoritative.

## 3. Objective

Ensure an appeal decision can mutate only the exact appealed sanction and cannot end unrelated sanctions in a combined case.

## 4. Why the package exists

The 2026-08-05 completion audit groups these requirements into one coherent implementation, provider, validation, acceptance, or audit boundary. Splitting shared correctness work risks inconsistent behavior; broadening it would mix unrelated work.

## 5. Included audit IDs

- `AUD-APPEAL-002`
- `AUD-APPEAL-003`
- `AUD-PUNISH-003`
- `AUD-SEC-005`
- `AUD-WEB-002`

## 6. Included behavior

- Replace case-wide appeal mutation with exact-sanction targeting.
- Preserve reviewer authorization, idempotency, concurrency control, audit linkage, and website contract semantics.
- Add regression coverage for combined cases, repeated acceptance, stale decisions, and rollback.

## 7. Explicit exclusions

- Website UX changes
- Production authority activation
- Issue #43 acceptance

## 8. Dependencies

- None.

Dependencies must be `COMPLETE` before this package becomes `READY`, except where the registry and owner explicitly accept a `DEFERRED` validation dependency for a later audit stage.

## 9. Component and repository boundaries

- Primary: `COMP-STAFF`.
- Additional: —.
- Change only paths required by included behavior, tests, contracts, aggregate component copies, package state, and the canonical handoff.
- Do not import or mutate unrelated components.
- No permanent component branch or isolated-component PR is part of this package.

## 10. Required branches

- EnthusiaStaff temporary branch: `package/es-p01-appeal-isolation`.

All branches are temporary and are deleted after merge when containment and absence of unique work are verified.

## 11. Required PRs

- One implementation PR targeting `wsg138/EnthusiaStaff:main`: `#68`.

A post-merge state-only finalization PR may be required because the exact merge commit, resulting `main`, containment, and implementation-branch deletion cannot be truthfully committed before PR #68 merges. It must contain no product changes and remains part of ES-P01 bookkeeping only.

## 12. Implementation checklist

- [x] Reconcile live GitHub and exact starting SHAs across every required repository.
- [x] Verify registry status, assignment, and absence of a conflicting worker.
- [x] Confirm included audit gaps still exist and exclusions remain correct.
- [x] Replace the website case-wide mutation path with the existing exact-sanction mutation contract.
- [x] Preserve appeal-review authorization without granting general full-overturn authority.
- [x] Add durable pending/final appeal transitions for restart recovery and stable replay outcomes.
- [x] Preserve the existing 1,000-character website reason contract.
- [x] Add focused unit tests for targeting, authorization, replay, stale state, authority fencing, missing capability, and reason bounds.
- [x] Add MariaDB regressions for combined cases, repeated acceptance/restart, stale decisions, rollback, and concurrent retries.
- [ ] Complete passing hosted exact-head build, tests, runtime-JAR inspection, coverage, and static-analysis evidence.
- [ ] Complete harsh final-diff review and resolve every valid finding/thread.
- [ ] Freeze tracked content and validate the exact reviewed head.
- [ ] Merge PR #68 normally.
- [ ] Verify resulting `main`, feature-head containment, and deletion of `package/es-p01-appeal-isolation`.
- [ ] Finalize registry, workspace state, this package file, and canonical handoff with exact post-merge evidence.

## 13. Acceptance criteria

- Every included requirement is implemented or conclusively exercised for validation/audit packages.
- Authorization, idempotency, concurrency, rollback, restart, bounds, privacy, and audit behavior are proved where applicable.
- No explicit exclusion was implemented accidentally.
- Package-specific PR and synchronization gates are satisfied.

## 14. Test requirements

Follow `VALIDATION-POLICY.md`. Add focused unit/integration/runtime tests for each included behavior and regression. Never claim staging or production acceptance from hosted unit tests.

## 15. Static-analysis requirements

Run repository-configured warnings-as-errors and static analysis for every changed repository. Resolve every valid first-party finding; use only narrow justified suppressions. Verify CodeRabbit/Codacy where available.

## 16. Documentation requirements

Update directly affected user/operator/developer documentation, commands, permissions, configuration, runbooks, component metadata, package status, and evidence. Keep claims conservative and revision-specific.

## 17. Security and privacy requirements

No secrets, credentials, raw addresses, private messages, databases, player rows, production routes, or reconstructable derived data in commits, PR comments, logs, or artifacts. Enforce service-boundary authorization and fail closed.

## 18. Migration impact

No migration is required. V16 remains highest and V1–V16 remain immutable. `MUTATION_PENDING` is an outcome code inside the existing `VARCHAR(64)` column; the existing appeal state remains `APPLIED`.

## 19. Bedrock considerations

This package changes a private website/Velocity mutation path and does not add a GUI or Java-only interaction. Existing website contract fields remain unchanged. Representative Bedrock acceptance remains outside this package.

## 20. Distributed-runtime considerations

The exact store's transaction, revision, idempotency, appeal linkage, and outbox behavior are reused. A durable `APPLIED/MUTATION_PENDING` appeal state makes both pre-mutation and post-commit/pre-finalization crash windows retryable across Velocity restarts. Hosted MariaDB tests do not replace distributed staging.

## 21. External-provider considerations

No external provider API is added or changed. LiteBans remains authoritative until the separate accepted cutover process.

## 22. Completion definition

`COMPLETE` requires all included criteria, tests, static analysis, documentation, exact reviewed heads, zero valid unresolved threads, normal merge of PR #68, verified containment, and safe deletion of the required implementation branch.

## 23. Resume state

- Assigned worker: `ChatGPT assigned-package implementation worker`.
- Starting `main`: `e434b3dedc003d1d5b3def64f38cc7465752b0e5`.
- Current branch: `package/es-p01-appeal-isolation`.
- Current PR: `#68 — ES-P01: isolate appeals to the exact sanction`.
- Latest implementation head at this checkpoint: `5e3f07ee546c4a569f7d27cf2b4e09e1b0c97adf`.
- Latest handoff: [`../../reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md`](../../reports/package-handoffs/2026-08-05-es-p01-appeal-isolation.md).
- Current action: finish hosted validation, full-diff review, exact-head freeze, merge, containment, cleanup, and final state synchronization.

## 24. Last completed checkpoint

Implementation and regression coverage are committed. The first hosted run exposed only an invalid synthetic Crockford case ID; the fixture and audit-count assumptions were corrected. Pi staging dispatch reached `EnthusiaStaff-Staging`, but its build job received no runner (`runner_id: 0`, zero steps), so no product code or Pi boot executed.

## 25. Remaining checklist

1. Obtain a passing hosted coverage/build run for the current or later exact head.
2. Review the complete final diff for transaction, authorization, concurrency, restart, rollback, contract, privacy, and scope defects.
3. Resolve CodeRabbit/Codacy/human findings and all valid review threads.
4. Freeze the reviewed head and rerun every applicable exact-head gate.
5. Record the Pi infrastructure result conservatively; it is neither a pass nor a product failure.
6. Merge PR #68 normally with the expected reviewed head.
7. Verify containment and delete `package/es-p01-appeal-isolation`.
8. Commit exact final package state and dependency-derived READY statuses without beginning another package.

## 26. Known blockers

No product blocker is known. The configured Pi staging workflow is currently infrastructure-blocked before execution: staging run `31057358391`, build job `92477654523`, `runner_id: 0`, no steps, boot job skipped. Hosted build and MariaDB validation remain available.

## 27. Final evidence

Pending exact-head freeze. Current evidence and failure corrections are recorded in the canonical handoff and PR #68.

## 28. Merge and synchronization record

Pending. No external component parity gate applies.

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

Canonical initial status: `READY`. `PACKAGE-REGISTRY.md` is authoritative.

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

- One PR targeting `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist

- [ ] Reconcile live GitHub and exact starting SHAs across every required repository.
- [ ] Verify registry status, assignment, and absence of a conflicting worker.
- [ ] Confirm included audit gaps still exist and exclusions remain correct.
- [ ] Complete every included behavior without placeholders or invented provider APIs.
- [ ] Add meaningful success, failure, concurrency, restart, and regression tests as applicable.
- [ ] Update the package file, registry, workspace state, component metadata when applicable, and one canonical package handoff.
- [ ] Harshly review every required final PR diff.
- [ ] Freeze tracked content and validate every exact final head.
- [ ] Merge every required PR normally.
- [ ] Verify post-merge heads, containment, branch cleanup, and external parity when applicable.

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

No migration is assumed. If a schema change is genuinely required, verify V16 is still highest, add a new immutable migration, and test clean install, upgrade, and checksum behavior. Never edit V1–V16, use Flyway repair, or rewrite history.

## 19. Bedrock considerations

Provide text/command fallback for GUI-dependent behavior, preserve Floodgate identity, avoid Java-only assumptions, and defer acceptance claims until representative Bedrock staging is recorded.

## 20. Distributed-runtime considerations

Account for multiple Paper/Velocity processes, server switching, duplicate delivery, reconnect, restart, ownership, fences, bounded queues, and database latency. Hosted tests do not replace distributed staging.

## 21. External-provider considerations

Use only verified supported repositories/contracts. Provider missing or incompatible behavior must be explicit and safe. Never reflect against unknown implementations, invent APIs, or scrape logs as a substitute contract.

## 22. Completion definition

`COMPLETE` requires all included criteria, tests, static analysis, documentation, exact reviewed heads, zero valid unresolved threads, and that one required EnthusiaStaff PR is merged normally. One merged PR is sufficient only for packages whose required-PR section explicitly defines one PR.

## 23. Resume state

- Assigned worker: `UNASSIGNED`.
- Current branches: `NONE`.
- Current PRs: `NONE`.
- Latest handoff: `NONE`.
- Current action: Start only when explicitly assigned; dependencies are already satisfied.

## 24. Last completed checkpoint

Package definition created by the orchestration setup. No product implementation began.

## 25. Remaining checklist

All implementation, validation, review, merge, synchronization, and evidence items remain.

## 26. Known blockers

None beyond dependencies and live reconciliation.

## 27. Final evidence

`UNSET`. Record final reviewed heads, workflow/job IDs, test/static-analysis results, review disposition, parity hashes when applicable, and links in live PR evidence and the canonical handoff.

## 28. Merge and synchronization record

`UNSET`. Record the EnthusiaStaff feature head, merge commit, resulting `main`, containment, and temporary branch deletion. No component parity gate applies unless the package is later amended to include an external component.

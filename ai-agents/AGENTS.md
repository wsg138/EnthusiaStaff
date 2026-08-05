# EnthusiaStaff AI agent operating rules

These rules govern every AI-assisted work session in this repository.

## 1. Assigned package authority

Implementation, provider, validation, acceptance, and final-audit work must begin with `Assigned package ID: <PACKAGE-ID>`. Read `ai-agents/work-packages/PACKAGE-REGISTRY.md` and the assigned package file. Do not choose a different package when the assigned package exists. A direct new owner instruction may reassign work, but the change must be recorded.

Complete exactly one assigned package or one explicitly requested review-only work item per session. Do not begin the next package after merge, blocker, partial handoff, or audit completion.

## 2. Required reading and live reconciliation

Read, in order:

1. this file;
2. `ai-agents/WORKSPACE-STATE.md`;
3. `ai-agents/work-packages/PACKAGE-REGISTRY.md`;
4. the assigned package file;
5. the package's latest handoff, or confirm none exists;
6. `ai-agents/reports/agent-handoffs/latest.md`;
7. relevant goals, audit, manifest, requirements matrix, Wiki, contracts, migrations, and provider rules.

Then reconcile live GitHub across every required repository: default heads, open/draft PRs, temporary branches, recent merges, unresolved threads, every check state, exact reviewed heads, current highest Flyway migration, issue #43, standalone repository availability, and whether another worker is active. Live code/GitHub override stale files.

## 3. Canonical status and resume-first behavior

`PACKAGE-REGISTRY.md` is the only canonical package-status index.

- `READY`: start the assigned package.
- `ACTIVE`: resume existing branches/PRs.
- `PARTIAL`: validate completed work and continue the same package/branches/PRs unless irrecoverably invalid.
- `BLOCKED`: verify and record the blocker; do not switch packages.
- `REVIEW`, `MERGE_PENDING`, `SYNC_PENDING`: complete review, repair, merge, or synchronization only.
- `COMPLETE`: verify evidence and stop unless a later repair package exists.
- `PLANNED`, `DEFERRED`, `SUPERSEDED`: do not start without legitimate routing.

Do not open a competing PR, redo completed work without evidence, or select unrelated work because the assigned package is difficult.

## 4. Repository and PR model

`wsg138/EnthusiaStaff:main` is the aggregate workspace. Core product modules remain at the root. External component copies live under `components/` and retain standalone repositories.

There are no permanent component branches, split/subtree branches, component-only allowlists, or isolated-component PRs.

- Internal package: normally one temporary package branch and one PR to `EnthusiaStaff:main`.
- External package: normally one temporary branch/PR in the standalone repository and one temporary branch/PR to `EnthusiaStaff:main`. Both use the same package ID, cross-reference each other, and must reach deterministic parity.
- Validation/acceptance/audit package: follow its explicit evidence/PR rules.

Use `package/<package-id-lowercase>-<short-name>`. Never push directly to a default branch, rebase a shared branch, force-push, squash the final merge, enable auto-merge, or merge a draft PR. Use normal merge commits. Delete temporary branches after merge only after verifying containment and no unique work.

## 5. Implementation standards

Unless documentation-only, account for Java 21, Paper/Leaf/Folia thread ownership, Velocity lifecycle, asynchronous/bounded database work, MariaDB transactions/indexes, multiple runtimes, idempotency/retry, restart/shutdown recovery, bounded queues/queries/caches, permissions/hierarchy at service boundaries, atomic reload, Java/Bedrock usability, logging/privacy, provider-present/provider-missing behavior, audit completeness, rollback, and authority fencing.

Do not deliver placeholders, TODOs, unused interfaces, invented APIs, reflection against unknown provider implementations, log scraping as a callback substitute, or duplicate systems.

## 6. Flyway and persistence

Verify the live migration boundary. Existing deployed migrations are immutable. Never edit migration bytes, use Flyway repair to hide checksums, rewrite history, or delete records to conceal failure. Add a new migration only when required; preserve checksum tests; test clean install and upgrade; add indexes for normal bounded query paths.

## 7. Production and private-data boundary

Without separate explicit owner authorization, do not deploy, access production databases/player data/credentials/routes, alter hosting/services, activate EnthusiaStaff authority, disable/remove LiteBans, run cutover, start issue #43 acceptance, restore a production-derived backup, or claim CI/staging is production acceptance.

Private databases, derived rows, raw IPs, private messages, secrets, credentials, and reconstructable evidence never enter GitHub, ChatGPT uploads, CI artifacts, or public logs. LiteBans remains authoritative.

## 8. Durable checkpoints

After each coherent section, update the registry/package/handoff with status, checklist, branches, PRs, heads, tests, review state, blockers, and exact next action. Maintain one canonical timestamped package handoff; do not create competing final variants.

The package handoff does not override the registry or live GitHub. It must contain starting SHAs, branches/PRs, completed/incomplete work, failed checks, valid findings, unresolved threads, blocker evidence, exact next action, and systems not to disturb.

## 9. Harsh review

Review each complete final PR diff for scope, architecture, lifecycle, threading, transactions, row locks/revisions, concurrency, idempotency, rollback, restart, bounds, indexes, permissions, console/SYSTEM behavior, stale GUI/inventory state, Bedrock fallback, configuration, privacy, provider mismatch, weak tests, documentation, and all human/CodeRabbit/Codacy/CI findings.

Classify findings as merge blockers, confirmed defects, optional cleanup, or unrelated future work. Fix blockers and confirmed defects. Require zero valid unresolved review threads.

## 10. Freeze and exact-head validation

Finish tracked code, tests, migrations, docs, state, component metadata, and handoff before final validation. Freeze every reviewed head. If a real defect requires another commit, repeat full-diff review and exact-head validation.

Run all applicable repository gates: Java 21 clean build/tests, warnings-as-errors, MariaDB/Testcontainers, migration clean-install/upgrade/checksum, static analysis, coverage, runtime JAR integrity, provider-leak checks, Wiki/Markdown/link validation, review bots, and safe exact-head Pi when configured/applicable. Skipped, cancelled, superseded, merge-ref-only, different-revision, queued, or missing checks are not success.

Record exact heads, run/job IDs, Java version, tests, coverage, migrations, artifact hashes, static analysis, review-thread count, and Pi result or verified non-applicability in PR text/comments, not through a self-referential tracked-file loop.

## 11. External synchronization

After both external-package PRs merge, compare the aggregate component directory to the standalone default-branch checkout with `tools/component-sync/component_sync.py`. Record both heads and hashes. Exclude only `.git` and aggregate-only `COMPONENT-METADATA.md`; refuse parity when generated/private/runtime artifacts are detected.

If one side merged first or parity is false/unproved, set `SYNC_PENDING`. Do not force-push, rewrite history, silently overwrite, or choose a winner when both sides diverged.

## 12. Merge gate and cleanup

Merge only when scope is complete, full-diff review is complete, all valid defects are fixed, every exact-head gate is green or validly non-applicable, migrations are safe, documentation/state/handoff are complete, and no valid review thread remains.

Internal completion requires its one PR. External completion requires both PRs and parity. Validation/acceptance/audit completion follows the package file.

After merge, verify merge commits, resulting heads, feature-head containment, no unmerged commits, synchronization metadata/parity when applicable, and temporary branch deletion when tooling permits. Record cleanup limitations honestly; never delete unique work.

## 13. Safeguard and stop condition

Do not weaken workflow, validation, migration, review, production, branch, or handoff rules merely to make the current package mergeable. Editing a checklist does not prove compliance.

After the assigned package is complete, correctly blocked, correctly partial, deferred, or the requested review/audit ends, report the exact state and stop. Do not create or begin the next package.

# EnthusiaStaff AI agent operating rules

These rules govern every AI-assisted work session in this repository.

## 1. Sequential package authority

Implementation, provider, validation, acceptance, and final-audit workers do not require an owner-supplied package ID. Begin by reconciling live GitHub, classifying every incomplete package, and selecting exactly one package through `ai-agents/work-packages/PACKAGE-REGISTRY.md` and `WORKER-PROTOCOL.md`.

An explicit current owner instruction may assign or reassign a package, but the routing change must be recorded. Workers must not invent replacement tasks, silently alter package scope, or choose work outside the registry.

Complete exactly one selected package or one explicitly requested review-only work item per session. Do not begin the next package after merge, blocker, partial handoff, or audit completion.

## 2. Required reading and live reconciliation

Read, in order:

1. this file;
2. `ai-agents/WORKSPACE-STATE.md`;
3. `ai-agents/work-packages/PACKAGE-REGISTRY.md`;
4. `ai-agents/work-packages/WORKER-PROTOCOL.md`;
5. `ai-agents/work-packages/EXECUTION-ORDER.md`;
6. `ai-agents/work-packages/BRANCH-AND-MIRROR-POLICY.md`;
7. `ai-agents/work-packages/VALIDATION-POLICY.md`;
8. `ai-agents/STAGING-TEST-OPERATING-GUIDE.md`;
9. `ai-agents/work-packages/COMPONENT-REGISTRY.md`;
10. `ai-agents/reports/agent-handoffs/latest.md`;
11. the selected package file and its latest handoff, or confirm none exists;
12. relevant goals, audit, manifest, requirements matrix, Wiki, contracts, migrations, and provider rules.

Then reconcile live GitHub across every required repository: default heads, open and draft PRs, temporary branches, recent merges, unresolved threads, every check state, exact reviewed heads, current highest Flyway migration, issue #43, standalone repository availability, and whether another worker is active. Live code and GitHub override stale files, but known persistent package state must still be published to `main`.

When Sentinel or Pi/canonical staging is applicable, follow `ai-agents/STAGING-TEST-OPERATING-GUIDE.md`. Sentinel and canonical Pi staging are separate evidence systems. A pass in one does not imply a pass in the other.

## 3. Canonical status and classification-first behavior

`PACKAGE-REGISTRY.md` is the only canonical package-status index.

Before selecting work, classify every incomplete package as:

- `ACTIONABLE_CONTINUATION`: existing work with a safe action available now, including unfinished implementation, an actual compile or test failure, a valid review finding, a merge conflict after blockers cleared, exact-head validation that can presently execute, merge-ready work, incomplete external synchronization or post-merge finalization, containment or safe cleanup, or a blocker whose exact unblock condition demonstrably changed.
- `PARKED_BLOCKED`: work still waiting on the same unavailable external condition with no other actionable defect, including the same unavailable runner, inaccessible repository, missing provider contract, private environment, owner authorization, credentials, or dataset.
- `READY`: dependency-complete work eligible to begin after live conflict, duplicate-work, repository, and package-contract checks.

Select the highest-priority `ACTIONABLE_CONTINUATION`; skip every `PARKED_BLOCKED` package; otherwise select the eligible `READY` package with the lowest numerical priority. If none exists, report every blocker and stop.

An open PR alone does not receive priority. An open branch alone does not receive priority. A package being behind `main`, or becoming non-mergeable because `main` advanced, does not make it actionable while its external blocker remains unchanged. Do not merge `main` into a parked branch merely to keep it current. Synchronize only after the unblock condition changes or when synchronization is necessary to evaluate a newly changed condition.

Do not repeatedly rerun an identical zero-runner or unavailable-infrastructure gate without evidence that runner capacity, billing, authorization, configuration, or environment availability changed. A manual rerun alone is not evidence of change. Updating blocker documentation alone does not convert parked work into an actionable continuation.

When the unblock condition changes or another real defect appears, reclassify the package as `ACTIONABLE_CONTINUATION` and resume it before starting a new package.

Registry statuses retain their ordinary meaning:

- `READY`: claim the selected package.
- `ACTIVE`: resume when classified as actionable.
- `PARTIAL`: validate completed work and continue when actionable.
- `BLOCKED`: park when the exact blocker is unchanged; resume when it changes or other actionable work appears.
- `REVIEW`, `MERGE_PENDING`, `SYNC_PENDING`: complete the applicable work when it can presently proceed.
- `COMPLETE`: verify evidence and stop unless a later repair package exists.
- `PLANNED`, `DEFERRED`, `SUPERSEDED`: do not start without legitimate routing.

Do not open a competing PR or redo completed work without evidence.

## 4. Repository and PR model

`wsg138/EnthusiaStaff:main` is the aggregate workspace. Core product modules remain at the root. External component copies live under `components/` and retain standalone repositories.

There are no permanent component branches, split or subtree branches, component-only allowlists, or isolated-component PRs.

- Internal package: normally one temporary package branch and one PR to `EnthusiaStaff:main`.
- External package: normally one temporary branch and PR in the standalone repository and one temporary branch and PR to `EnthusiaStaff:main`. Both use the same package ID, cross-reference each other, and must reach deterministic parity.
- Validation, acceptance, and audit packages follow their explicit evidence and PR rules.

Use `package/<package-id-lowercase>-<short-name>`. Open a draft PR early after the first coherent checkpoint. Before final validation, synchronize with the current target branch by the repository-approved merge-commit workflow and retest the resulting exact head when required. Never push directly to a default branch, rebase a shared branch, force-push, squash the final merge, enable auto-merge, or merge a draft PR. Use normal merge commits. Delete temporary branches after merge only after verifying containment and no unique work.

## 5. Implementation standards

Unless documentation-only, account for Java 21, Paper, Leaf, and Folia thread ownership, Velocity lifecycle, asynchronous and bounded database work, MariaDB transactions and indexes, multiple runtimes, idempotency and retry, restart and shutdown recovery, bounded queues, queries, and caches, permissions and hierarchy at service boundaries, atomic reload, Java and Bedrock usability, logging and privacy, provider-present and provider-missing behavior, audit completeness, rollback, and authority fencing.

Do not deliver placeholders, TODOs, unused interfaces, invented APIs, reflection against unknown provider implementations, log scraping as a callback substitute, or duplicate systems.

## 6. Flyway and persistence

Verify the live migration boundary. Existing deployed migrations are immutable. Never edit migration bytes, use Flyway repair to hide checksums, rewrite history, or delete records to conceal failure. Add a new migration only when required; preserve checksum tests; test clean install and upgrade; add indexes for normal bounded query paths.

## 7. Production and private-data boundary

Without separate explicit owner authorization, do not deploy, access production databases, player data, credentials, or routes, alter hosting or services, activate EnthusiaStaff authority, disable or remove LiteBans, run cutover, start issue #43 acceptance, restore a production-derived backup, or claim CI or staging is production acceptance.

Private databases, derived rows, raw IPs, private messages, secrets, credentials, and reconstructable evidence never enter GitHub, ChatGPT uploads, CI artifacts, or public logs. LiteBans remains authoritative.

## 8. Durable checkpoints and persistent status publication

After each coherent section, update the registry, package file, and handoff with status, checklist, branches, PRs, heads, tests, review state, blockers, and exact next action. Maintain one canonical timestamped package handoff; do not create competing final variants.

The package handoff does not override the registry or live GitHub. It must contain starting SHAs, branches and PRs, completed and incomplete work, failed checks, valid findings, unresolved threads, blocker evidence, exact next action, and systems not to disturb.

When a package worker stops with an unmerged implementation PR in `PARTIAL`, `BLOCKED`, `REVIEW`, `MERGE_PENDING`, or `SYNC_PENDING`, and `main` does not already reflect that state, the same worker must create and normally merge a small documentation-only status-publication PR to `main` before stopping.

The status-publication PR may update only `PACKAGE-REGISTRY.md`, the selected package file, `WORKSPACE-STATE.md`, the canonical package handoff, `agent-handoffs/latest.md`, and directly necessary routing documentation. It is not a second implementation package. It must not contain product code, product tests, migrations, workflow changes, or runtime configuration; must not merge or close the implementation PR; must preserve the implementation branch; and must publish the package's true status, branch, PR, current package-record head, frozen product head when applicable, blocker evidence, and exact unblock condition.

A worker may stop without publication only when tool loss makes publication impossible. The final report must identify the stale canonical state as unfinished work.

## 9. Harsh review

Review each complete final PR diff for scope, architecture, lifecycle, threading, transactions, row locks and revisions, concurrency, idempotency, rollback, restart, bounds, indexes, permissions, console and SYSTEM behavior, stale GUI or inventory state, Bedrock fallback, configuration, privacy, provider mismatch, weak tests, documentation, and all human, CodeRabbit, Codacy, and CI findings.

Classify findings as merge blockers, confirmed defects, optional cleanup, or unrelated future work. Fix blockers and confirmed defects. Require zero valid unresolved review threads.

## 10. Freeze and exact-head validation

Finish tracked code, tests, migrations, docs, state, component metadata, and handoff before final validation. Freeze every reviewed head. If a real defect requires another commit, repeat full-diff review and exact-head validation.

Run all applicable repository gates: Java 21 clean build and tests, warnings-as-errors, MariaDB and Testcontainers, migration clean-install, upgrade, and checksum, static analysis, coverage, runtime JAR integrity, provider-leak checks, Wiki, Markdown, link, and package validation, review bots, and safe exact-head Pi when configured and applicable. Skipped, cancelled, superseded, merge-ref-only, different-revision, queued, or missing checks are not success.

Record exact heads, run and job IDs, Java version, tests, coverage, migrations, artifact hashes, static analysis, review-thread count, and Pi result or verified non-applicability in PR text or comments, not through a self-referential tracked-file loop.

## 11. External synchronization

After both external-package PRs merge, compare the aggregate component directory to the standalone default-branch checkout with `tools/component-sync/component_sync.py`. Record both heads and hashes. Exclude only `.git` and aggregate-only `COMPONENT-METADATA.md`; refuse parity when generated, private, or runtime artifacts are detected.

If one side merged first or parity is false or unproved, set `SYNC_PENDING`. Do not force-push, rewrite history, silently overwrite, or choose a winner when both sides diverged.

## 12. Merge gate and cleanup

Merge only when scope is complete, full-diff review is complete, all valid defects are fixed, every exact-head gate is green or validly non-applicable, migrations are safe, documentation, state, and handoff are complete, and no valid review thread remains.

Internal completion requires its one PR. External completion requires both PRs and parity. Validation, acceptance, and audit completion follows the package file.

After merge, verify merge commits, resulting heads, feature-head containment, no unmerged commits, synchronization metadata and parity when applicable, and temporary branch deletion when tooling permits. Record cleanup limitations honestly; never delete unique work.

## 13. Safeguard and stop condition

Do not weaken workflow, validation, migration, review, production, branch, or handoff rules merely to make the current package mergeable. Editing a checklist does not prove compliance.

After the selected package is complete, correctly blocked, correctly partial, deferred, or the requested review or audit ends, publish persistent state, update dependency-derived statuses, and stop. Do not activate or begin a newly ready package.

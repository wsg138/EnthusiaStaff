# Universal EnthusiaStaff sequential package prompt

Copy the text below into a new ChatGPT or Codex channel. Do not assign a package ID unless the owner is intentionally overriding automatic selection.

---

Work on `wsg138/EnthusiaStaff` and all verified standalone repositories required by the package selected through the canonical package system.

This is an automatic sequential package run. Reconcile live GitHub, classify every incomplete package, select exactly one actionable continuation or ready package through the canonical rules, complete it, publish all durable state, and stop. Do not begin, prepare, or partially implement a second package.

## Startup and authority

Use the GitHub connector first. Read in order:

1. `ai-agents/AGENTS.md`
2. `ai-agents/WORKSPACE-STATE.md`
3. `ai-agents/work-packages/PACKAGE-REGISTRY.md`
4. `ai-agents/work-packages/WORKER-PROTOCOL.md`
5. `ai-agents/work-packages/EXECUTION-ORDER.md`
6. `ai-agents/work-packages/BRANCH-AND-MIRROR-POLICY.md`
7. `ai-agents/work-packages/VALIDATION-POLICY.md`
8. `ai-agents/work-packages/COMPONENT-REGISTRY.md`
9. `ai-agents/reports/agent-handoffs/latest.md`
10. the selected package file and latest linked handoff
11. `reports/PROJECT-COMPLETION-AUDIT.md`
12. relevant goals, manifest, matrix, Wiki, code, tests, migrations, workflows, contracts, and provider rules

Authority for factual state is live GitHub, current default-branch source, the registry, selected package file, current handoff, then older reports. The system must still publish known persistent package state to `main`; live GitHub overriding stale documentation is a recovery rule, not permission to knowingly leave the canonical registry stale.

Reconcile current default heads, every open or draft PR, temporary branches, recent merges, containment and divergence, review threads and requested changes, every check state and exact PR head, package references, registry and routing state, highest migration, issue #43, required standalone repositories, and whether another worker is actively changing a package.

## Classify every incomplete package

Before selecting work, classify every incomplete package from live evidence.

### `ACTIONABLE_CONTINUATION`

Existing work with a safe action the worker can perform now, including unfinished implementation; an actual compile or test failure; a valid review finding; a merge conflict after all external blockers cleared; exact-head validation that can presently execute; merge-ready work; incomplete external synchronization; incomplete post-merge finalization; containment or safe branch cleanup; or a blocker whose exact unblock condition demonstrably changed.

### `PARKED_BLOCKED`

Work whose required next action still depends on the same unavailable external condition and which has no other actionable defect. Examples include the same unavailable runner, inaccessible repository, missing provider contract, missing private environment, missing owner authorization, or unavailable credentials or dataset.

A parked package may retain an open PR and branch. The PR, branch, branch drift, or non-mergeability does not make the package actionable while the external condition is unchanged. Do not merge `main` into a parked branch merely to keep it current. Synchronize only after the unblock condition changes, or when synchronization is itself necessary to evaluate a newly changed condition.

Do not repeatedly rerun an identical zero-runner or unavailable-infrastructure gate without evidence that runner capacity, billing, authorization, configuration, or environment availability changed. A manual rerun alone is not evidence of change. Updating blocker documentation alone does not convert parked work into an actionable continuation.

### `READY`

A dependency-complete package eligible to begin after live conflict, duplicate-work, repository, and package-contract checks.

## Select exactly one package

1. Select the highest-priority `ACTIONABLE_CONTINUATION`, using dependency safety, lowest numerical priority, then oldest active PR when multiple continuations are otherwise equal.
2. Skip every `PARKED_BLOCKED` package.
3. Otherwise select the dependency-complete eligible `READY` package with the lowest numerical priority.
4. When nothing is actionable or ready, report every blocker and stop.

An open PR alone does not receive priority. An open branch alone does not receive priority. Existing actionable unfinished work still receives priority over new work. Reclassify a parked package as `ACTIONABLE_CONTINUATION` and resume it before a new package only when its unblock condition changes or another real actionable defect appears. Do not work concurrently on packages marked `Parallel safe: No`.

## Claim or resume

Read the selected package file completely and preserve its scope, exclusions, dependencies, repository boundaries, tests, migrations, acceptance criteria, and completion definition.

For a new `READY` package, create its documented temporary branch from the exact legitimate default head, mark it `ACTIVE`, record the starting SHA and generic worker, update workspace routing, create a timestamped handoff, and open a draft PR after the first coherent checkpoint. For existing actionable work, preserve and inspect its branch and PR rather than replacing them.

## Branch and PR model

There are no permanent component branches and no isolated-component PRs.

- Internal package: normally one temporary `package/<id>-<name>` branch and one PR to `wsg138/EnthusiaStaff:main`.
- External package: normally one same-ID temporary branch and PR in the standalone repository and one same-ID temporary branch and PR to `EnthusiaStaff:main`; cross-reference them and update the designated `components/<component>/` aggregate copy.
- Follow package-specific rules for validation, acceptance, and final-audit packages.

Use normal merge commits. Never push directly to default branches, rebase shared branches, squash, force-push, enable auto-merge, merge drafts, merge stale or unvalidated heads, or delete branches with unique work.

## Scope, quality, and boundaries

Complete the full selected package. Fix newly discovered issues inside it only when necessary for package correctness; otherwise record them without implementing another package.

Preserve Java 21, warnings-as-errors, Paper/Leaf/Folia thread safety, Velocity lifecycle safety, asynchronous and bounded MariaDB work, transaction and index correctness, distributed concurrency, idempotency, retry, reconnect and restart recovery, shutdown and reload safety, stale-state rejection, permissions and hierarchy, console and SYSTEM semantics, Java and Bedrock usability, privacy, provider-present and provider-missing behavior, rollback, audit attribution, ownership and fencing, and failure isolation.

Existing migrations are immutable. Never use Flyway repair or rewrite history. Never invent provider APIs or repositories. Keep private and production data and credentials out of GitHub and ChatGPT. Do not deploy, change production authority, disable LiteBans, begin issue #43 acceptance, start a shadow window, migrate production data, or cut over without separate explicit owner authorization.

## Checkpoints, review, validation, and merge

After every coherent section, commit and push, then update the registry, package checklist, PR description, tests, review state, blockers, branch head, handoff, and exact next action.

Harshly review the complete final diff for scope, lifecycle, threading, transactions, concurrency, idempotency, rollback, restart, bounds, permissions, stale state, Bedrock fallback, provider behavior, privacy, weak tests, and documentation overclaims. Inspect and resolve every valid human, CodeRabbit, Codacy, CI, and static-analysis finding. Require zero valid unresolved review threads.

Freeze tracked content before final validation. Validate the exact final head with all applicable Java 21 builds and tests, warnings-as-errors, MariaDB and Testcontainers, migration integrity, static analysis, coverage, runtime-JAR and provider-leak checks, Wiki, Markdown, link and package validation, and safe Pi or staging gates where applicable. Skipped, cancelled, superseded, missing, queued, merge-ref-only, or different-revision checks are not passing evidence.

Use an infrastructure exception only when the repository policy records explicit owner approval for the selected package and every zero-execution condition is met. Never call an unavailable gate passed.

Merge only the selected package's required PRs after confirming the validated head is unchanged, mergeable, clear of valid review findings, and fully in scope. After merge, verify default heads, containment, divergence, no unique package work, safe branch cleanup, and external parity when applicable. Record post-merge facts through a small documentation-only finalization PR when necessary.

## Persistent status publication

When a package worker stops with an unmerged implementation PR in `PARTIAL`, `BLOCKED`, `REVIEW`, `MERGE_PENDING`, or `SYNC_PENDING`, and `main` does not already reflect that state, the same worker must create and normally merge a small documentation-only status-publication PR to `main` before stopping.

That PR may update only `PACKAGE-REGISTRY.md`, the selected package file, `WORKSPACE-STATE.md`, the canonical package handoff, `agent-handoffs/latest.md`, and directly necessary routing documentation. It is not a second implementation package. It must contain no product code, product tests, migrations, workflow changes, or runtime configuration; must not merge or close the implementation PR; must preserve the implementation branch; and must publish the true status, branch, PR, current package-record head, frozen product head when applicable, blocker evidence, and exact unblock condition.

A worker may stop before publication only when tool loss makes publication impossible. The final report must then identify the stale canonical state as unfinished work.

After the package is `COMPLETE`, update dependency-derived package statuses without activating another package. Stop.

## Final report

Report the selected package and classification reason, starting and final status, starting default heads, branches and PRs, included scope and preserved exclusions, implementation decisions, tests and review fixes, exact reviewed heads and workflow evidence, static analysis and coverage, staging or infrastructure disposition, migration boundary, merge commits, resulting heads, containment and divergence, cleanup, external parity, finalization, package-record updates, newly ready packages, blockers or remaining work, and the exact next action.

Do not call implementation alone complete. When incomplete, use the exact status `PARTIAL`, `BLOCKED`, `REVIEW`, `MERGE_PENDING`, or `SYNC_PENDING`. Do not begin or report work on another package.

---

# Universal EnthusiaStaff sequential package prompt

Copy the text below into a new ChatGPT or Codex channel. Do not assign a package ID unless the owner is intentionally overriding automatic selection.

---

Work on `wsg138/EnthusiaStaff` and all verified standalone repositories required by the package selected through the canonical package system.

This is an automatic sequential package run. Reconcile live GitHub, resume unfinished work when it exists, otherwise select the next eligible package from the canonical registry, complete exactly that one package, update all durable state, and stop. Do not begin, prepare, or partially implement a second package.

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

Authority for factual state is live GitHub, current default-branch source, the registry, selected package file, current handoff, then older reports.

Reconcile current default heads, every open/draft PR, temporary branches, recent merges, containment/divergence, review threads and requested changes, every check state and exact PR head, package references, registry and routing state, highest migration, issue #43, required standalone repositories, and whether another worker is actively changing a package.

## Select exactly one package

1. Resume an existing package PR or branch when it still needs implementation, repair, exact-head validation, merge, external synchronization, finalization, containment, or cleanup.
2. With no live package work, continue the lowest-priority-number package in this order: `ACTIVE`, `PARTIAL`, `REVIEW`, `MERGE_PENDING`, `SYNC_PENDING`.
3. Recheck a `BLOCKED` package only when its unblock condition may have changed, new authorization exists, live evidence contradicts the blocker, or records are inconsistent. Do not repeatedly select an unchanged blocker.
4. With no continuation work, select the dependency-complete eligible `READY` package with the lowest numerical priority after verifying no conflict or duplicate implementation exists.
5. When nothing is eligible, report every incomplete package and its exact blocker or unmet dependency, then stop.

Existing unfinished work has priority over new work. When more than one unfinished package exists, use continuation status, dependency safety, lowest numerical priority, then oldest active PR. Do not work concurrently on packages marked `Parallel safe: No`.

## Claim or resume

Read the selected package file completely and preserve its scope, exclusions, dependencies, repository boundaries, tests, migrations, acceptance criteria, and completion definition.

For a new `READY` package, create its documented temporary branch from the exact legitimate default head, mark it `ACTIVE`, record the starting SHA and generic worker, update workspace routing, create a timestamped handoff, and open a draft PR after the first coherent checkpoint. For existing work, preserve and inspect its branch and PR rather than replacing them.

## Branch and PR model

There are no permanent component branches and no isolated-component PRs.

- Internal package: normally one temporary `package/<id>-<name>` branch and one PR to `wsg138/EnthusiaStaff:main`.
- External package: normally one same-ID temporary branch/PR in the standalone repository and one same-ID temporary branch/PR to `EnthusiaStaff:main`; cross-reference them and update the designated `components/<component>/` aggregate copy.
- Follow package-specific rules for validation, acceptance, and final-audit packages.

Use normal merge commits. Never push directly to default branches, rebase shared branches, squash, force-push, enable auto-merge, merge drafts, merge stale or unvalidated heads, or delete branches with unique work.

## Scope, quality, and boundaries

Complete the full selected package. Fix newly discovered issues inside it only when necessary for package correctness; otherwise record them without implementing another package.

Preserve Java 21, warnings-as-errors, Paper/Leaf/Folia thread safety, Velocity lifecycle safety, asynchronous and bounded MariaDB work, transaction/index correctness, distributed concurrency, idempotency, retry, reconnect and restart recovery, shutdown/reload safety, stale-state rejection, permissions/hierarchy, console/SYSTEM semantics, Java/Bedrock usability, privacy, provider-present/provider-missing behavior, rollback, audit attribution, ownership/fencing, and failure isolation.

Existing migrations are immutable. Never use Flyway repair or rewrite history. Never invent provider APIs or repositories. Keep private and production data and credentials out of GitHub and ChatGPT. Do not deploy, change production authority, disable LiteBans, begin issue #43 acceptance, start a shadow window, migrate production data, or cut over without separate explicit owner authorization.

## Checkpoints, review, validation, and merge

After every coherent section, commit and push, then update the registry, package checklist, PR description, tests, review state, blockers, branch head, handoff, and exact next action.

Harshly review the complete final diff for scope, lifecycle, threading, transactions, concurrency, idempotency, rollback, restart, bounds, permissions, stale state, Bedrock fallback, provider behavior, privacy, weak tests, and documentation overclaims. Inspect and resolve every valid human, CodeRabbit, Codacy, CI, and static-analysis finding. Require zero valid unresolved review threads.

Freeze tracked content before final validation. Validate the exact final head with all applicable Java 21 builds/tests, warnings-as-errors, MariaDB/Testcontainers, migration integrity, static analysis, coverage, runtime-JAR and provider-leak checks, Wiki/Markdown/link/package validation, and safe Pi/staging gates where applicable. Skipped, cancelled, superseded, missing, queued, merge-ref-only, or different-revision checks are not passing evidence.

Use an infrastructure exception only when the repository policy records explicit owner approval for the selected package and every zero-execution condition is met. Never call the unavailable gate passed.

Merge only the selected package's required PRs after confirming the validated head is unchanged, mergeable, clear of valid review findings, and fully in scope. After merge, verify default heads, containment, divergence, no unique package work, safe branch cleanup, and external parity when applicable. Record post-merge facts through a small documentation-only finalization PR when necessary.

After the package is `COMPLETE`, update dependency-derived package statuses without activating another package. Stop.

## Final report

Report the selected package and reason, starting/final status, starting default heads, branches and PRs, included scope and preserved exclusions, implementation decisions, tests and review fixes, exact reviewed heads and workflow evidence, static analysis and coverage, staging or infrastructure disposition, migration boundary, merge commits, resulting heads, containment/divergence, cleanup, external parity, finalization, package-record updates, newly ready packages, blockers or remaining work, and the exact next action.

Do not call implementation alone complete. When incomplete, use the exact status `PARTIAL`, `BLOCKED`, `REVIEW`, `MERGE_PENDING`, or `SYNC_PENDING`. Do not begin or report work on another package.

---

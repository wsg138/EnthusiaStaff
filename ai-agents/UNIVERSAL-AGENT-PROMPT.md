# Universal EnthusiaStaff assigned-package prompt

Copy the text below into a new ChatGPT or Codex channel and replace the placeholder.

---

Work on `wsg138/EnthusiaStaff` and all standalone repositories required by the assigned package.

Assigned package ID: `<PACKAGE-ID>`

Complete only this assigned package. Do not choose or begin another package when the assigned package exists. Resume its existing branches and PRs when status is `ACTIVE` or `PARTIAL`. Stop only when this same package is `COMPLETE`, correctly `BLOCKED`, correctly `PARTIAL`, intentionally `DEFERRED`, or the assigned review/audit is complete.

## Startup

Use the GitHub connector first. Read:

1. `ai-agents/AGENTS.md`
2. `ai-agents/WORKSPACE-STATE.md`
3. `ai-agents/work-packages/PACKAGE-REGISTRY.md`
4. `ai-agents/work-packages/packages/<PACKAGE-ID>.md`
5. the latest package handoff linked by the package/registry
6. `ai-agents/reports/agent-handoffs/latest.md`
7. relevant goals, audit, manifest, matrix, Wiki, code, migrations, and provider rules

Reconcile live default heads, PRs, temporary branches, recent merges, review threads, checks in every state, highest migration, issue #43, required standalone repositories, and active workers. Record exact starting SHAs and determine whether you are starting, resuming, reviewing, merging, synchronizing, validating, or stopping.

## Status action

Follow `WORKER-PROTOCOL.md`. Do not replace an existing package branch merely because this is a new channel. Do not redo completed checkpoints without evidence they are wrong. If blocked, verify and record the blocker; do not switch packages.

## Branch and PR model

There are no permanent component branches and no isolated-component PRs.

- Internal package: normally one temporary `package/<id>-<name>` branch and one PR to `wsg138/EnthusiaStaff:main`.
- External package: normally one same-ID temporary branch/PR in the standalone repository and one same-ID temporary branch/PR to `EnthusiaStaff:main`; cross-reference them and update the designated `components/<component>/` aggregate copy.
- Follow package-specific rules for validation, acceptance, and final-audit packages.

Use normal merge commits. Never push directly to default branches, rebase shared branches, squash, force-push, enable auto-merge, or merge drafts. Delete temporary branches after merge when containment and no unique work are verified.

## Quality and boundaries

Finish all included scope and respect exclusions. Preserve Java 21, Paper/Leaf/Folia thread safety, Velocity lifecycle, asynchronous/bounded MariaDB work, transactions/indexes, distributed concurrency, idempotency, retry, restart/shutdown recovery, permissions/hierarchy, atomic reload, Java/Bedrock usability, privacy, provider-present/provider-missing behavior, rollback, audit, and authority fencing.

Existing migrations are immutable. Never use Flyway repair or rewrite history. Never invent provider APIs or repositories. Keep private/production data and credentials out of GitHub and ChatGPT. Do not deploy, change production authority, disable LiteBans, or begin issue #43 acceptance without separate explicit owner authorization.

## Checkpoint, review, validation, and synchronization

After each coherent section, update package state/checklist, branches/PRs/heads, tests, review, blockers, exact next action, and one canonical package handoff.

Harshly review every complete final diff. Fix all valid blockers/defects and require zero valid unresolved threads. Freeze tracked content, then validate every exact reviewed head with all applicable repository checks. Never treat skipped/cancelled/superseded/different-head/merge-ref-only/missing checks as success.

For external packages, merge both PRs and run deterministic aggregate-versus-standalone comparison. Set `SYNC_PENDING` if one side is unmerged or parity is false/unproved. Never force-push, rewrite history, or silently overwrite divergence.

## Final report

Report assigned package, starting heads, branches/PRs, completed scope, review findings/fixes, exact-head validation, merge and parity evidence or precise blocker/partial state, branch cleanup, boundaries preserved, and the exact next action. Do not start another package.

---

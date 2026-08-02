# Handoff — AI agent workspace setup

## Identity

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Date | 2026-08-02 |
| Work item | Add a shared AI-agent workflow, state file, universal prompt, and handoff-report location |
| Starting `main` | `f53143132db29b9cd75e7caa6589f979d99af8c4` |
| Branch | `agent/add-ai-agent-workflow` |
| PR | [`#47 — Add shared AI agent workflow`](https://github.com/wsg138/EnthusiaStaff/pull/47) |
| Intended merge method | Normal merge commit |
| Exact-head and merge evidence | Read PR #47 description and final comments live |

## Baseline

Repository state before this work:

- no shared AI-agent directory;
- no concise current-state routing file;
- no reusable universal prompt;
- no standardized repository handoff location;
- prior work evidence existed across PR descriptions, issue comments, repository documents, and chat reports.

The existing `WORKSPACE-MANIFEST.md`, `ENTHUSIASTAFF-GOALS.md`, development blueprint, and requirements matrix remain authoritative project references.

## Implementation

Added `ai-agents/` containing:

- `README.md` — workspace purpose, precedence, and normal flow;
- `AGENTS.md` — permanent resume-first, implementation, harsh-review, validation, merge, cleanup, migration, production-boundary, blocked-work, and handoff rules;
- `WORKSPACE-STATE.md` — current repository checkpoint, active work, latest repository-process update, last completed product feature, next planned work, migration boundary, and production boundary;
- `UNIVERSAL-AGENT-PROMPT.md` — reusable opening prompt for future AI channels;
- `reports/README.md` — handoff format, exact-evidence location, and privacy rules;
- `reports/agent-handoffs/latest.md` — latest-report pointer;
- timestamped handoffs for PR #46 and this workspace setup.

## Workflow decisions

- Agents must inspect live GitHub before trusting recorded state.
- Relevant unfinished PRs are resumed before new work begins.
- Each session completes one logical work item and then stops.
- Implementation and harsh review are separate phases.
- Exact final-head evidence is required before merge.
- Normal merge commits remain mandatory.
- Auto-merge, rebase, squash, force-push, and direct `main` pushes remain prohibited.
- State and handoff files land through the reviewed feature PR.
- Tracked content is frozen before exact-head validation.
- Exact final-head evidence is stored in the PR description or a final pre-merge comment, which does not alter the feature SHA.
- The actual merge commit is verified live afterward and recorded in the PR, avoiding a direct documentation commit to `main`.
- The next agent always reconciles the committed handoff against live GitHub.
- Branch deletion occurs only after the branch is proven fully merged and tooling permits it.

## Harsh review

The documentation review found one merge-blocking workflow defect:

A handoff file committed inside a PR cannot reliably contain that same PR's final SHA and final CI run IDs without changing the SHA again and invalidating the evidence it just recorded.

This was corrected by separating evidence locations:

- repository handoffs store stable summaries, boundaries, review context, next steps, and the PR link;
- exact final-head evidence is recorded in the PR after tracked files are frozen;
- exact merge evidence is recorded in the PR after merge;
- the next agent reads both the committed handoff and live GitHub state.

This avoids a circular commit-and-revalidate sequence without allowing direct pushes to `main`.

The final review must also confirm:

- no contradiction with existing repository policy;
- no weakening of production or migration boundaries;
- no unsafe automatic merge behavior;
- no stale assumption treated as authoritative;
- no private-data leakage;
- valid repository paths and links;
- clear one-work-item stopping behavior.

## Validation location

Exact final-head validation evidence will be recorded in PR #47 after tracked content is frozen.

Required validation for this documentation-only work:

- compare branch against current `main`;
- confirm only intended `ai-agents/` files changed;
- verify every referenced repository path exists;
- inspect Markdown structure and relative links;
- inspect PR review threads and configured checks;
- confirm no code, migration, configuration, workflow, or production boundary changed.

## Intended merge state

When the review and exact-head checks are clean:

- mark PR #47 ready;
- merge using a normal merge commit;
- verify the merge commit and resulting `main` live;
- verify the branch is fully contained in `main`;
- delete the branch when available tooling permits;
- record exact merge and cleanup evidence in PR #47;
- stop without starting the report workflow.

## Boundaries

- No plugin code changed.
- No migration changed.
- No build or runtime configuration changed.
- No GitHub Actions workflow changed.
- No JAR was deployed.
- No production system, database, player data, credential, Discord route, or webhook was accessed.
- EnthusiaStaff authority was not activated.
- LiteBans remains authoritative.
- Issue #43 remains open.

## Next work

After this workflow is merged, the next agent should use `ai-agents/UNIVERSAL-AGENT-PROMPT.md`.

The currently recorded next product feature is the staff report workflow. The next agent must verify live open PRs and repository state before creating that branch.
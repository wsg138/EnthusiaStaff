# Handoff — AI agent workspace setup

## Identity

| Field | Value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Date | 2026-08-02 |
| Work item | Add a shared AI-agent workflow, state file, universal prompt, and handoff-report location |
| Starting `main` | `f53143132db29b9cd75e7caa6589f979d99af8c4` |
| Branch | `agent/add-ai-agent-workflow` |
| PR | To be assigned after opening |
| Final feature head | To be verified before merge |
| Intended merge method | Normal merge commit |

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
- `WORKSPACE-STATE.md` — current repository checkpoint, active work, latest completed PR, next planned work, migration boundary, and production boundary;
- `UNIVERSAL-AGENT-PROMPT.md` — reusable opening prompt for future AI channels;
- `reports/README.md` — handoff format and privacy rules;
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
- The feature PR updates state and handoff files before merge so the records land through the reviewed PR.
- The actual merge commit is verified live afterward and recorded in the PR description or final PR comment, avoiding a direct documentation commit to `main`.
- The next agent always reconciles the committed handoff against live GitHub.
- Branch deletion occurs only after the branch is proven fully merged and tooling permits it.

## Review focus

Review this documentation for:

- contradictions with existing repository policy;
- accidental weakening of production or migration boundaries;
- impossible handoff requirements;
- circular state updates;
- unsafe automatic merge behavior;
- stale assumptions being treated as authoritative;
- private-data leakage;
- broken repository links;
- ambiguity about one-work-item stopping behavior.

## Validation

Required for this documentation-only work:

- compare branch against current `main`;
- confirm only intended `ai-agents/` files changed;
- verify every referenced repository path exists;
- inspect Markdown structure and internal relative links;
- inspect PR review threads and configured checks;
- confirm no code, migration, configuration, workflow, or production boundary changed.

## Boundaries

- No plugin code changed.
- No migration changed.
- No build or runtime configuration changed.
- No JAR was built or deployed as part of this documentation task.
- No production system, database, player data, credential, Discord route, or webhook was accessed.
- EnthusiaStaff authority was not activated.
- LiteBans remains authoritative.
- Issue #43 remains open.

## Next work

After this workflow is merged, the next agent should use `ai-agents/UNIVERSAL-AGENT-PROMPT.md`.

The currently recorded next product feature is the staff report workflow. The next agent must verify live open PRs and repository state before creating that branch.
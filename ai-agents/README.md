# EnthusiaStaff AI agent workspace

This directory is the shared operating location for AI-assisted repository work.

It exists so a new ChatGPT or coding-agent channel can determine the current repository state, resume unfinished work, complete one logical step, review it rigorously, validate the exact final revision, merge it safely, and leave a durable handoff for the next agent.

## Files

- [`AGENTS.md`](AGENTS.md) — permanent operating, review, validation, merge, safety, and handoff rules.
- [`WORKSPACE-STATE.md`](WORKSPACE-STATE.md) — concise current state, active work, last completed work, next planned work, migration boundary, and release boundary.
- [`UNIVERSAL-AGENT-PROMPT.md`](UNIVERSAL-AGENT-PROMPT.md) — reusable opening prompt for a new AI channel.
- [`reports/`](reports/) — durable handoff reports and the report format.

## Other authoritative repository documents

The AI workspace coordinates work; it does not replace the project requirements.

Agents must also read the relevant portions of:

- [`/ENTHUSIASTAFF-GOALS.md`](../ENTHUSIASTAFF-GOALS.md)
- [`/WORKSPACE-MANIFEST.md`](../WORKSPACE-MANIFEST.md)
- [`/docs/wiki/pages/Development-Blueprint.md`](../docs/wiki/pages/Development-Blueprint.md)
- [`/reports/REQUIREMENTS-MATRIX.md`](../reports/REQUIREMENTS-MATRIX.md)

When documents disagree:

1. Live GitHub and repository state determines what actually exists.
2. `AGENTS.md` controls the agent workflow and safety process.
3. `ENTHUSIASTAFF-GOALS.md` controls product requirements.
4. `WORKSPACE-STATE.md` identifies the intended current work item but may be stale and must be reconciled against live state.
5. The latest handoff explains the previous agent's work but is evidence, not authority to ignore current code or CI.

An agent may not weaken review, migration, production, or merge safeguards merely by editing this directory.

## Normal session flow

1. Read this directory and the linked project documents.
2. Inspect the live default branch, open PRs, branches, reviews, and CI.
3. Resume the active or unfinished PR before starting another feature.
4. Complete exactly one logical work item.
5. Perform a separate harsh-review phase over the full diff.
6. Fix confirmed defects and rerun exact-head validation.
7. Merge with a normal merge commit only when every merge gate passes.
8. Delete the merged feature branch when it is fully contained in `main` and tooling permits deletion.
9. Update `WORKSPACE-STATE.md` and add a handoff report before merging so the records land with the work.
10. Verify the merge live, update the PR description or final PR comment with the exact merge result, and stop.

The next agent must always verify live GitHub state rather than blindly trusting a recorded SHA.
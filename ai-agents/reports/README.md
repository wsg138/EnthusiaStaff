# AI agent reports

This directory contains durable, sanitized handoff reports for completed or blocked AI-assisted work.

## Layout

```text
ai-agents/reports/
├── README.md
└── agent-handoffs/
    ├── latest.md
    └── YYYY-MM-DD-prNN-short-name.md
```

Use the PR number when one exists. For blocked work without a PR, use a clear work identifier.

Examples:

- `2026-08-02-pr46-punishment-history.md`
- `2026-08-03-pr47-report-workflow.md`
- `2026-08-04-blocked-rosechat-provider.md`

## Avoid self-referential evidence

A report committed inside a PR cannot reliably contain that same PR's final SHA, final CI run IDs, or merge commit without changing the revision again.

Therefore:

- the tracked handoff contains the stable work summary, review summary, boundaries, next step, and PR link;
- exact final-head validation evidence is recorded in the PR description or a final pre-merge PR comment after tracked files are frozen;
- the exact merge commit and resulting `main` are recorded in the PR after merge;
- the next agent must read both the handoff and the live PR evidence.

Do not create a circular sequence where every attempt to record the final SHA creates another SHA requiring another validation run.

## Required handoff sections

Every handoff should include:

1. **Identity**
   - repository;
   - date/time and timezone;
   - work item;
   - starting `main`;
   - branch;
   - PR and URL.
2. **Baseline**
   - existing behavior;
   - confirmed gaps;
   - relevant prerequisites.
3. **Implementation**
   - completed behavior;
   - material architecture and persistence changes;
   - commands, permissions, configuration, migrations, and documentation.
4. **Harsh review**
   - merge blockers found;
   - confirmed defects fixed;
   - optional or deferred findings;
   - unresolved-review status at the time tracked content was frozen.
5. **Validation location**
   - link to the PR description or comment where exact final-head evidence will be recorded;
   - checks that must pass;
   - known evidence limitations.
6. **Intended merge or blocker state**
   - intended normal merge state;
   - branch-cleanup expectation;
   - or exact blocker and required input.
7. **Boundaries**
   - production, authority, migration, credential, and data boundaries preserved.
8. **Remaining work**
   - known limitations;
   - next recommended work item;
   - no implementation of that next item.

A historical report may include exact final-head and merge evidence when those values were already known before the AI workspace was introduced.

## Latest pointer

`agent-handoffs/latest.md` must point to the newest handoff and summarize:

- work item;
- PR;
- state expected after merge or blocker state;
- validation-evidence location;
- next work item.

The next agent must inspect live GitHub because the latest file is normally committed before final validation and merge.

## Privacy

Never store:

- credentials;
- secret names that reveal private infrastructure unnecessarily;
- raw production data;
- player IP addresses;
- private evidence;
- unredacted appeal content;
- database dumps;
- private JARs;
- access tokens;
- production hostnames when they are sensitive.

Use sanitized aggregate evidence and GitHub run, job, artifact, commit, issue, and PR identifiers.
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

## Required handoff sections

Every handoff should include:

1. **Identity**
   - repository;
   - date/time and timezone;
   - work item;
   - starting `main`;
   - branch;
   - PR;
   - original and final feature heads.
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
   - unresolved review status.
5. **Validation**
   - exact final feature head;
   - workflow run and job IDs;
   - Java version;
   - build and tests;
   - migration checks;
   - coverage;
   - JAR and artifact hashes where relevant;
   - static analysis;
   - Pi evidence or an honest statement that it did not run.
6. **Merge or blocker**
   - intended or actual merge state;
   - normal merge confirmation;
   - resulting `main` when known live;
   - branch cleanup result;
   - or exact blocker and required input.
7. **Boundaries**
   - production, authority, migration, credential, and data boundaries preserved.
8. **Remaining work**
   - known limitations;
   - next recommended work item;
   - no implementation of that next item.

## Latest pointer

`agent-handoffs/latest.md` must point to the newest handoff and summarize:

- work item;
- PR;
- final feature head;
- state;
- next work item.

The next agent must still inspect live GitHub because the latest file may have been committed immediately before the PR merge and therefore may not contain the final merge commit.

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
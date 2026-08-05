# EnthusiaStaff workspace manifest

Last updated: 2026-08-05 (`America/Indiana/Indianapolis`)

This manifest records development coordination and authority boundaries. It does not authorize deployment, production-data access, LiteBans cutover or a change in punishment authority.

## Repository checkpoint

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| `main` at reconciliation start | `9d84f8d50a024b04530335c622d63b573343242b` |
| Latest merged product PR | PR #64 — Block mounted movement while frozen |
| Current documentation work | PR #65 — post-PR #64 routing reconciliation; verify live merge state |
| Expected committed state | `IDLE — no implementation item active or preselected` |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-05-post-pr64-reconciliation.md` |
| Migration boundary | V16 is highest; V1–V16 remain immutable |
| Dormant default | Startup remains non-`ACTIVE` |
| Production authority | **LiteBans remains authoritative** |

At PR #65 start there were no open or draft pull requests and no non-`main` branch. PR #64 had merged normally, its exact feature head was contained in `main`, and its feature branch had been removed.

## Recent merged checkpoints

The recent priority-one sequence is merged:

- PR #56 closed staff-tool number-key and offhand transfer bypasses.
- PR #57 reconciled staff-mode profiles with live rank changes.
- PR #58 added durable fail-closed recovery across Paper disable or reload.
- PR #59 reconciled vanish state and visibility with live rank changes.
- PR #60 fenced freeze recovery across reconnects and manual changes.
- PR #61 added database-dump and private migration-input protections.
- PR #62 blocked ordinary world interactions during active staff mode.
- PR #63 blocked precise world interactions while frozen.
- PR #64 ejected frozen players from mounts and rejected new mount attempts.

These are development checkpoints, not deployment or production-acceptance evidence.

## Owner priority checkpoint

When prerequisites are comparable:

1. staff mode, vanish and freeze;
2. report notification completion;
3. escalation-policy completion.

No specific next feature is selected by this manifest. After PR #65 is complete and no unfinished PR exists, the next agent must inspect live code, tests, goals and requirements and select one bounded prerequisite-ready remaining priority-one gap.

## Blocked-work routing

The supported RoseChat private-message callback and privacy presentation contract remains unavailable. Provider-dependent report notification, private-message evidence and related staff-chat work must not invent APIs, reflect against unknown implementation classes, copy provider-owned classes or scrape logs as a substitute callback.

Issue #43 is specifically the LiteBans production-cutover acceptance issue. It is not the general bug-report or blocker queue and does not prevent ordinary dormant development work. LiteBans remains authoritative until a separately approved production cutover is completed.

## Development merge gate

For the next implementation PR, use the exact-head and merge rules in `ai-agents/AGENTS.md` and `ai-agents/UNIVERSAL-AGENT-PROMPT.md`. Require one reviewed unchanged head, every applicable configured check, zero valid unresolved review threads, a normal merge commit, exact resulting `main`, feature-head containment and safe branch cleanup.

Documentation-only PR #65 does not require Pi solely because it changes documentation unless the repository workflow triggers and requires Pi for its exact head. Record actual workflow behavior accurately; do not claim skipped or absent checks passed.

## Production cutover gate

Issue #43 remains open specifically for production-cutover acceptance. Before it is separately completed and approved, do not deploy a production cutover candidate, begin a real shadow window, activate EnthusiaStaff authority, disable or remove LiteBans, perform a final production migration or authorize live cutover.

## Related repositories

Provider and website repositories remain independent. Their histories must not be flattened into EnthusiaStaff, and provider API classes must not leak into Paper or Velocity runtime JARs. The intended RoseChat provider repository/API remains unavailable.

## Current route

1. Finish PR #65 only if it is still open; do not begin implementation in the same session.
2. After PR #65 merges, reconcile live GitHub and inspect current implementation gaps.
3. Select one bounded prerequisite-ready staff-mode, vanish or freeze item; no candidate is preselected.
4. Continue report notifications second when their prerequisites are available.
5. Treat escalation-policy completion as third priority.
6. Complete issue #43 only later, when the plugin is closer to release and a pinned release candidate and representative isolated environment exist.

## Release boundaries

- Never combine evidence from different revisions.
- Keep credentials, private JARs, databases, logs and evidence out of Git.
- Never repair Flyway history or edit deployed migration bytes.
- Do not represent hosted tests or isolated staging as production acceptance.
- A merged pull request is a development checkpoint, not deployment authorization.

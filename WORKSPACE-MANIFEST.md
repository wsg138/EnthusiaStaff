# EnthusiaStaff workspace manifest

Last updated: 2026-08-03 (America/Indiana/Indianapolis)

This manifest records development coordination and authority boundaries. It does not authorize deployment, production-data access, LiteBans cutover or a change in punishment authority.

## Repository checkpoint

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| `main` at PR #56 start | `d71759aa4f121c82f984e57d6fd0968a80c502ba` |
| Latest merged product PR | PR #55 — Admin staff-mode Ender view-only enforcement |
| Work requiring live verification | PR #56 — staff-tool hotbar and offhand transfer bypass closure |
| Expected committed state | `IDLE — PR #56 requires live merge verification` |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-03-staffmode-tool-transfer-bypasses.md` |
| Migration boundary | V16 is highest; PR #56 adds no migration; V1–V16 remain immutable |
| Dormant default | Startup remains non-`ACTIVE` |
| Production authority | **LiteBans remains authoritative** |

At PR #56 start there were no open pull requests and no non-main branch remained active. PR #55 had merged normally, its feature head was contained in `main`, and its feature branch had been removed.

## Implementation checkpoint

PR #56 is one bounded staff-mode item-leak prevention correction:

- the previous active-session click guard inspected only the clicked item and cursor;
- number-key and inventory offhand clicks can move a different source item;
- a focused listener now identifies current, cursor, exact referenced hotbar and offhand sources;
- one directly tested policy decides whether the transfer contains a protected staff tool;
- number-key checks only the selected hotbar source;
- offhand swap checks only the offhand source;
- unrelated click types do not become over-restrictive;
- transition and rank/Ender mutation rules remain enforced by `StaffModeManager`;
- Helper restrictions, Admin Ender view-only behavior, Founder owner access, drag/drop/pickup/swap-hand protection and staff-tool cleanup remain unchanged.

No database, protocol, provider, command, permission, configuration, vanish, freeze, confiscation or production-authority behavior is part of this PR.

## Harsh-review checkpoint

The complete diff received a separate harsh review. It found and fixed one confirmed architecture defect: the first implementation left a second current-item/cursor guard in `StaffModeManager` beside the new listener. The manager now owns transition/rank restrictions while the dedicated listener owns active-session staff-tool click transfer protection.

No tracked merge blocker remains before exact-head validation. Full Paper event-object staging is useful optional runtime confidence beyond the thin listener and directly tested decision policy.

Exact final-head validation, review and merge evidence belongs in PR #56 live metadata.

## Owner priority checkpoint

When prerequisites are comparable, use this order:

1. staff mode, vanish, and freeze;
2. report notification completion;
3. escalation-policy completion.

PR #56 is staff/player-visible leak prevention under priority one. Do not expand it into rank-change lifecycle, disable recovery, vanish, freeze, general inventory editing or confiscation.

## Prior verified evidence

PR #55 exact head `c6380aae35cf8c56044faf6dea96c471b14634f3` passed exact-head Coverage `30812589989` and Validate Wiki `30812589424`, had zero unresolved review threads, and merged normally as `d71759aa4f121c82f984e57d6fd0968a80c502ba`. Do not attribute that evidence to PR #56.

The Pi wrapper uses `pull_request_target`, so commit-scoped workflow listings may omit it. Inspect the public wrapper and correlated private `wsg138/EnthusiaStaff-Staging` run directly. Cancelled, superseded, skipped, different-revision and merge-ref-only runs are not exact-head evidence. When Pi is configured and triggered, require a terminal successful public wrapper and correlated private run for the exact feature head; accept an exception only when live workflow configuration proves Pi is not applicable or cannot be triggered for that exact head, and record that verified exception in PR metadata.

## Provider and blocked-work routing

The supported RoseChat private-message callback and privacy presentation boundary remains blocked because no accessible supported provider repository/API defines callback timing, identity, cancellation/delivery semantics, threading, duplicate behavior, versions and privacy-safe evidence fields. Do not invent an API, reflect against unknown classes, copy provider-owned classes or scrape logs as a substitute callback.

Issue #43 is specifically the LiteBans production-cutover acceptance issue and remains open. It is not the general bug-report or blocker queue. External blockers should normally be tracked in a focused issue and the normal handoff.

## Development merge gate

Merge PR #56 only after one unchanged exact feature head is synchronized with current `main` and every configured check for that head has a terminal acceptable result: Java 21 build/tests, applicable MariaDB/Testcontainers and migration checksum/immutability checks, runtime-JAR and provider-leak inspection, aggregate coverage, configured static analysis and coverage upload, wiki/documentation validation, applicable public and private Pi staging, and all human/CodeRabbit/Codacy review gates. Reject cancelled, superseded, skipped, different-revision and merge-ref-only results. A Pi exception is valid only when live workflow configuration proves Pi is not applicable or cannot be triggered for the exact feature head and the exception is recorded in PR metadata. Zero unresolved valid threads must remain. Before merge, record the exact head, every run/job and artifact identity, hashes, review classification and synchronization evidence in the PR without changing the feature SHA. Use a normal merge commit, then record the merge commit, resulting `main`, feature-head containment, absence of unmerged branch commits and branch cleanup.

## Production cutover gate

Issue #43 remains open specifically for production-cutover acceptance. Before it is complete, do not deploy a production cutover candidate, begin a real shadow window, activate EnthusiaStaff authority, disable/remove LiteBans, perform final production migration or authorize live cutover.

## Related repositories

Provider and website repositories remain independent. Their histories must not be flattened into EnthusiaStaff, and provider API classes must not leak into Paper or Velocity runtime JARs. The intended RoseChat provider repository/API remains unavailable.

## Current route

1. Apply the complete development merge gate above to PR #56's final unchanged exact head; verify all terminal checks or a documented valid Pi exception, zero unresolved threads, normal merge evidence, resulting `main`, feature-head containment, no unmerged branch commits and branch cleanup.
2. After PR #56, select one separate bounded staff-mode lifecycle or restriction-enforcement item after fresh reconciliation.
3. Continue owner priority one before report notifications when prerequisites are comparable.
4. Treat escalation-policy completion as third priority.
5. Stop after PR #56 and do not begin the next feature in the same session.

## Release boundaries

- Never combine evidence from different revisions.
- Keep credentials, private JARs, databases, logs and evidence out of Git.
- Never repair Flyway history or edit deployed migration bytes.
- Do not represent hosted tests or isolated staging as production acceptance.
- A merged pull request is a development checkpoint, not deployment authorization.

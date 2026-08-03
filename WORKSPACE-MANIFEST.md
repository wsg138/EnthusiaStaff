# EnthusiaStaff workspace manifest

Last updated: 2026-08-03 (America/Indiana/Indianapolis)

This manifest records development coordination and authority boundaries. It does not authorize deployment, production-data access, LiteBans cutover or a change in punishment authority.

## Repository checkpoint

| Field | Current value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| `main` at PR #55 start | `717d716d34f3e4e524d9b7c744cb5ece3cacaf04` |
| Latest merged product PR | PR #54 — serious-offense decay eligibility metadata |
| Work requiring live verification | PR #55 — Admin staff-mode Ender view-only enforcement |
| Expected committed state | `IDLE — PR #55 requires live merge verification` |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-03-admin-staffmode-ender-view-only.md` |
| Migration boundary | V16 is highest; PR #55 adds no migration; V1–V16 remain immutable |
| Dormant default | Startup remains non-`ACTIVE` |
| Production authority | **LiteBans remains authoritative** |

At PR #55 start there were no open pull requests and no non-main branch remained active. PR #54 had merged normally and its feature branch had been removed.

## Implementation checkpoint

PR #55 is one bounded staff-mode asset-safety correction:

- the previous shared Ender predicate permitted both opening and mutation for Admin and Founder;
- opening and mutation authority are separate policy decisions;
- Helper, Mod, and Developer remain unable to open Ender chests during staff mode;
- Admin may open an Ender chest, but clicks and drags in that view are cancelled so access is view-only;
- Founder retains configured owner-level Ender access;
- Admin creative inventory interaction outside an Ender chest view remains available;
- click and drag use one shared mutation decision;
- `SYSTEM` and unresolved ranks fail closed for Ender opening and mutation;
- existing staff-tool protections remain unchanged;
- focused tests cover the exact combined mutation behavior for every player-assigned rank, the `SYSTEM` boundary, and an unresolved rank.

No database, protocol, provider, command, permission, configuration, vanish, freeze, confiscation or production-authority behavior is part of this PR.

## Harsh-review checkpoint

The complete diff received a separate harsh review. It found and fixed three confirmed defects:

1. the initial open predicate failed open for an unresolved or future rank; only explicit Admin or Founder now open Ender chests, and only Founder may mutate;
2. click and drag duplicated policy conditions while tests proved only leaf predicates; both handlers now share one directly tested combined decision;
3. the non-player `StaffRank.SYSTEM` enum boundary was omitted from the test claim; it is now explicitly denied player Ender access.

No tracked merge blocker remains before exact-head validation. Full Paper event-object staging is useful optional runtime confidence but is not a confirmed defect in the thin handlers.

Exact final-head validation, review and merge evidence belongs in PR #55 live metadata.

## Owner priority checkpoint

When prerequisites are comparable, use this order:

1. staff mode, vanish, and freeze;
2. report notification completion;
3. escalation-policy completion.

PR #55 is staff/player-visible safety work under priority one. Do not expand it into another staff-mode lifecycle slice or combine vanish/freeze work.

## Prior verified evidence

PR #54 exact head `b0b5bef5807da7d60d64ad7c59319ec15c53955f` passed exact-head Coverage `30800091453` and Validate Wiki `30800091459`, had zero unresolved review threads, and merged normally as `717d716d34f3e4e524d9b7c744cb5ece3cacaf04`. Do not attribute that evidence to PR #55.

The historical PR #54 Pi failure came from disposable staging database isolation, not feature migration correctness. Private `EnthusiaStaff-Staging` PR #7 fixed the harness and merged as `635423c64a2254d137002fce32652eb20770db34`; V16 was not edited and Flyway repair was not used.

## Provider and blocked-work routing

The supported RoseChat private-message callback and privacy presentation boundary remains blocked because no accessible supported provider repository/API defines callback timing, identity, cancellation/delivery semantics, threading, duplicate behavior, versions and privacy-safe evidence fields. Do not invent an API, reflect against unknown classes, copy provider-owned classes or scrape logs as a substitute callback.

Issue #43 is specifically the LiteBans production-cutover acceptance issue and remains open. It is not the general bug-report or blocker queue. External blockers should normally be tracked in a focused issue and the normal handoff.

## Development merge gate

Merge PR #55 only after one unchanged exact head is synchronized with `main` and passes Java 21 build/tests, applicable migration checksum/immutability checks, runtime-JAR inspection, provider-leak checks, aggregate coverage, configured static analysis, wiki validation, terminal exact-head Pi when configured and applicable, and all review gates. Zero unresolved valid threads must remain. Record exact evidence in the PR without changing the feature SHA and use a normal merge commit.

## Production cutover gate

Issue #43 remains open specifically for production-cutover acceptance. Before it is complete, do not deploy a production cutover candidate, begin a real shadow window, activate EnthusiaStaff authority, disable/remove LiteBans, perform final production migration or authorize live cutover.

## Related repositories

Provider and website repositories remain independent. Their histories must not be flattened into EnthusiaStaff, and provider API classes must not leak into Paper or Velocity runtime JARs. The intended RoseChat provider repository/API remains unavailable.

## Current route

1. Verify PR #55's frozen exact head, terminal validation and review state, normal merge result, resulting `main`, feature-head containment and branch cleanup.
2. After PR #55, select one separate bounded staff-mode lifecycle or restriction-enforcement item after fresh reconciliation.
3. Continue owner priority one before report notifications when prerequisites are comparable.
4. Treat escalation-policy completion as third priority.
5. Stop after PR #55 and do not begin the next feature in the same session.

## Release boundaries

- Never combine evidence from different revisions.
- Keep credentials, private JARs, databases, logs and evidence out of Git.
- Never repair Flyway history or edit deployed migration bytes.
- Do not represent hosted tests or isolated staging as production acceptance.
- A merged pull request is a development checkpoint, not deployment authorization.

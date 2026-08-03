# EnthusiaStaff workspace state

Last updated: 2026-08-03

This is a routing record, not a substitute for live GitHub reconciliation.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current legitimate `main` at PR #55 start | `717d716d34f3e4e524d9b7c744cb5ece3cacaf04` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `IDLE — PR #55 requires live merge verification` |
| Intended post-merge state | PR #55 merged normally into `main`; resulting `main` contains the reviewed feature head; the feature branch is deleted or otherwise confirmed clean; LiteBans remains authoritative and no deployment, production access, authority activation, shadow window, or cutover occurs |
| Pull request to verify | `#55 — Enforce Admin staff-mode Ender view-only access` |
| Feature branch to verify | `fix/admin-staffmode-ender-view-only` |
| Completed work item | Separate staff-mode Ender open access from mutation authority so Admin is view-only, Founder retains configured owner access, and non-player/unresolved ranks fail closed |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-03-admin-staffmode-ender-view-only.md` |
| Exact validation/merge evidence | Read PR #55 live; exact SHA, Coverage, Wiki, Pi, Codacy, artifacts, reviews and merge evidence belong in PR metadata |
| External blocker | Supported RoseChat private-message provider contract remains unavailable; track it through a focused blocker issue and the normal handoff rather than issue #43 |

## Start-state reconciliation for PR #55

- PR #54 merged normally as `717d716d34f3e4e524d9b7c744cb5ece3cacaf04` from exact feature head `b0b5bef5807da7d60d64ad7c59319ec15c53955f`.
- PR #54's exact feature head had successful Coverage `30800091453` and Validate Wiki `30800091459`; all review threads were resolved before merge.
- No pull request was open or draft at PR #55 start.
- No non-main branch remained active; the merged PR #54 branch had been removed.
- V16 was the live highest migration.
- No supported RoseChat callback/API contract was available.

## PR #55 completed behavior

The authoritative rank contract permits Admin creative staff mode but requires Ender chest access to remain view-only unless a separate destructive workflow authorizes mutation. The previous shared predicate allowed both Admin and Founder to open and mutate Ender contents.

PR #55 now enforces:

- Helper, Mod, and Developer cannot open Ender chests in staff mode;
- Admin may open an Ender chest, but clicks and drags in that view are cancelled;
- Founder retains configured owner-level Ender access;
- Admin creative inventory interaction outside an Ender chest view remains available;
- click and drag use one shared mutation decision;
- `SYSTEM` and unresolved ranks fail closed for Ender opening and mutation;
- existing staff-tool item protections remain unchanged.

`StaffModeAccessPolicyTest` covers the exact ordinary-versus-Ender mutation decision used by both inventory event handlers for every player-assigned rank, the non-player `SYSTEM` boundary, and the unresolved-rank boundary.

## Harsh-review result

The complete PR diff received a separate harsh review. Three confirmed defects were fixed:

1. The initial split open predicate denied only known lower ranks and therefore failed open for an unresolved or future rank. Ender opening now permits only explicit Admin or Founder ranks, and mutation permits only Founder.
2. Click and drag repeated related policy conditions while tests covered only the leaf predicates. Both handlers now share `blocksInventoryMutation`, and focused tests prove that exact combined decision.
3. `StaffRank.SYSTEM` was a current enum boundary missing from the test claim. Coverage now proves that this non-player rank receives no player Ender access.

No merge blocker remains in tracked content. Full Paper event-object staging remains useful optional runtime confidence, not a confirmed defect in the thin handlers.

## Owner priorities and selection guardrails

Current owner priority order:

1. Staff mode, vanish, and freeze.
2. Report notification completion.
3. Escalation-policy completion.

When prerequisites are comparable, prefer owner-prioritized staff/player-visible work over another internal policy or infrastructure slice.

Do not perform more than two consecutive internal infrastructure or policy PRs unless the work:

- fixes a confirmed correctness, security, concurrency, migration, or data-integrity defect;
- directly unblocks a higher-priority feature; or
- is explicitly approved by the owner.

Direct owner instructions in the current conversation override this recorded order.

PR #55 is a bounded staff/player-visible asset-safety correction under priority one. Do not combine vanish, freeze, general inventory editing, confiscation, or another staff-mode lifecycle slice into it.

## Pi evidence routing and historical staging correction

The public Pi wrapper uses `pull_request_target`, so commit-scoped workflow listings may show Coverage and Wiki while omitting the Pi wrapper. Agents must inspect the public wrapper and the correlated private `wsg138/EnthusiaStaff-Staging` run directly rather than treating an omitted commit-scoped result as absent or non-applicable.

Historical PR #54 navigation:

- public wrapper run: `30794945133`;
- public failure artifact: `8848768264`;
- dispatched private staging run: `30794966760`;
- private staging-control correction: PR #7 merged as `635423c64a2254d137002fce32652eb20770db34`.

That prior failure came from a disposable Pi database retaining a mutable-head V16 checksum. Flyway correctly rejected the mismatch. The corrected staging harness resets only a guarded dedicated test database before and after each run, preserves it between restart-test boots, and never calls Flyway `repair` or rewrites migration history.

## Blocked-work routing

- Issue #43 is specifically the LiteBans production-cutover acceptance issue and remains open.
- Issue #43 is not the general bug-report or blocker queue.
- External blockers such as an unavailable provider API should normally be tracked in a focused issue and the normal handoff.
- Do not open a standalone documentation PR solely to record a blocker unless routing would otherwise be materially incorrect or unsafe.

## Migration boundary

| Field | Value |
| --- | --- |
| Highest live migration | V16 |
| PR #55 migration | None |
| Immutable history | V1–V16 |
| Next expected number | V17 unless live state is newer |
| Locked checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an applied migration or use Flyway repair.

## Production boundary

LiteBans remains authoritative. Issue #43 remains open specifically for production-cutover acceptance. PR #55 does not authorize deployment, production access, production Discord use, authority activation, a production shadow window, LiteBans disablement or removal, final production migration, or live cutover.

## Next route

1. Verify PR #55's frozen exact head, terminal Coverage/Wiki/Pi/Codacy and review state, normal merge result, resulting `main`, feature-head containment and branch cleanup.
2. After PR #55 is complete, select one separate bounded staff-mode lifecycle or restriction-enforcement gap after fresh live reconciliation.
3. Continue owner priority one before report notification work when prerequisites are comparable.
4. Track unavailable RoseChat provider APIs through focused blocker routing rather than issue #43.
5. Do not begin the next work item in the PR #55 session.

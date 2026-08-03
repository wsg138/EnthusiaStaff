# EnthusiaStaff workspace state

Last updated: 2026-08-03

This is a routing record, not a substitute for live GitHub reconciliation.

## Repository

| Field | Recorded value |
| --- | --- |
| Repository | `wsg138/EnthusiaStaff` |
| Default branch | `main` |
| Current legitimate `main` at PR #56 start | `d71759aa4f121c82f984e57d6fd0968a80c502ba` |
| Plugin version | `0.1.0-SNAPSHOT` |
| Java/runtime | Java 21; Paper-compatible backends, Velocity, MariaDB |

## Current work

| Field | Value |
| --- | --- |
| State | `IDLE — PR #56 requires live merge verification` |
| Intended post-merge state | PR #56 merged normally into `main`; resulting `main` contains the reviewed feature head; the feature branch is deleted or otherwise confirmed clean; LiteBans remains authoritative and no deployment, production access, authority activation, shadow window or cutover occurs |
| Pull request to verify | `#56 — Block staff-tool hotbar and offhand transfer bypasses` |
| Feature branch to verify | `fix/staffmode-tool-transfer-bypasses` |
| Completed work item | Close staff-mode number-key hotbar and inventory offhand transfer paths that could move protected staff tools without the clicked item or cursor containing the tool |
| Current handoff | `ai-agents/reports/agent-handoffs/2026-08-03-staffmode-tool-transfer-bypasses.md` |
| Exact validation/merge evidence | Read PR #56 live; exact SHA, Coverage, Wiki, Pi, Codacy, artifacts, reviews and merge evidence belong in PR metadata |
| External blocker | Supported RoseChat private-message provider contract remains unavailable; track it through a focused blocker issue and the normal handoff rather than issue #43 |

## Start-state reconciliation for PR #56

- PR #55 merged normally as `d71759aa4f121c82f984e57d6fd0968a80c502ba` from exact feature head `c6380aae35cf8c56044faf6dea96c471b14634f3`.
- PR #55's exact feature head had successful Coverage `30812589989` and Validate Wiki `30812589424`; all review threads were resolved before merge.
- The PR #55 feature branch was removed and its head is contained in `main`.
- No pull request was open or draft at PR #56 start.
- No non-main branch remained active before the PR #56 branch was created.
- V16 was the live highest migration.
- No supported RoseChat callback/API contract was available.

## PR #56 completed behavior

The prior active staff-session click guard inspected only the clicked item and cursor. Number-key and inventory offhand clicks can move different source items, allowing protected staff tools to bypass that guard.

PR #56 now enforces:

- one focused `StaffToolTransferListener` owns active-session staff-tool click transfer protection;
- current-item and cursor staff tools remain blocked for every click type;
- `NUMBER_KEY` checks the exact referenced hotbar source;
- `SWAP_OFFHAND` checks the offhand source;
- non-tool transfers remain available where the rank profile permits ordinary inventory mutation;
- transition-wide and rank-specific inventory restrictions remain in `StaffModeManager`;
- Helper restrictions, Admin view-only Ender access, Founder owner access, drag/drop/pickup/swap-hand protection and cleanup behavior remain unchanged;
- no migration, command, permission, configuration, provider or production-authority behavior changes.

`StaffModeAccessPolicyTest` covers every click type's current/cursor protection, exact number-key and offhand source decisions, negative cases and all prior rank/Ender boundaries.

## Harsh-review result

The complete PR diff received a separate harsh review. One confirmed architecture defect was fixed: the first implementation left the manager's current-item/cursor click guard beside the new listener, creating parallel authority. The manager now owns transition/rank mutation restrictions, and the dedicated listener owns active-session staff-tool click transfers.

No merge blocker remains in tracked content before exact-head validation. A full Paper event-object staging test is useful optional runtime confidence beyond the thin source adapter and directly tested policy, not a confirmed defect.

## Owner priorities and selection guardrails

Current owner priority order:

1. Staff mode, vanish, and freeze.
2. Report notification completion.
3. Escalation-policy completion.

When prerequisites are comparable, prefer owner-prioritized staff/player-visible work over another internal infrastructure or policy slice.

Do not perform more than two consecutive internal infrastructure or policy PRs unless the work:

- fixes a confirmed correctness, security, concurrency, migration, or data-integrity defect;
- directly unblocks a higher-priority feature; or
- is explicitly approved by the owner.

Direct owner instructions in the current conversation override this recorded order.

PR #56 is a bounded staff/player-visible leak-prevention correction under priority one. Do not combine rank-change lifecycle, disable recovery, vanish, freeze, general inventory editing, confiscation or staff-tool action behavior into it.

## Pi evidence routing

The public Pi wrapper uses `pull_request_target`, so commit-scoped workflow listings may show Coverage and Wiki while omitting the Pi wrapper. Agents must inspect the public wrapper and correlated private `wsg138/EnthusiaStaff-Staging` run directly rather than treating an omitted commit-scoped result as absent or non-applicable.

Cancelled and superseded runs are not validation evidence. For PR #56, final exact-head Pi must reach a terminal successful result when configured and triggered before merge.

## Blocked-work routing

- Issue #43 is specifically the LiteBans production-cutover acceptance issue and remains open.
- Issue #43 is not the general bug-report or blocker queue.
- External blockers such as an unavailable provider API should normally be tracked in a focused issue and the normal handoff.
- Do not open a standalone documentation PR solely to record a blocker unless routing would otherwise be materially incorrect or unsafe.

## Migration boundary

| Field | Value |
| --- | --- |
| Highest live migration | V16 |
| PR #56 migration | None |
| Immutable history | V1–V16 |
| Next expected number | V17 unless live state is newer |
| Locked checksums | V11 `-2005375055`; V12 `-1787751803`; V13 `1189066017` |

Never edit an applied migration or use Flyway repair.

## Production boundary

LiteBans remains authoritative. Issue #43 remains open specifically for production-cutover acceptance. PR #56 does not authorize deployment, production access, production Discord use, authority activation, a production shadow window, LiteBans disablement or removal, final production migration, or live cutover.

## Next route

1. Verify PR #56's frozen exact head, terminal Coverage/Wiki/Pi/Codacy and review state, normal merge result, resulting `main`, feature-head containment and branch cleanup.
2. After PR #56 is complete, select one separate bounded staff-mode lifecycle or restriction-enforcement item after fresh live reconciliation; rank-change correction or reload/disable recovery are candidate areas, not preselected work.
3. Continue owner priority one before report notification work when prerequisites are comparable.
4. Track unavailable RoseChat provider APIs through focused blocker routing rather than issue #43.
5. Do not begin the next work item in the PR #56 session.

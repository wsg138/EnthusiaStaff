# `ES-P12` — Staff operational hardening

## 1. Package identity
`ES-P12`; Internal; primary `COMP-STAFF`; owner-directed feature package; priority 95.

## 2. Status
`ACTIVE` — created from the owner's September 23, 2026 request to implement the operational-hardening findings in one cohesive pull request. Implementation branch: `package/es-p12-staff-operational-hardening`.

## 3. Objective
Harden day-to-day staff operations so rank boundaries, freeze visibility, vanish privacy, mute fallback behavior, punishment escalation context, confiscation availability, staff-presence reporting, and bare-Paper presence tracking behave safely and predictably.

## 4. Why the package exists
Live testing exposed several product-level gaps that are individually small but belong to the same staff-operations surface. The owner explicitly requested they be implemented together rather than filed as separate issues.

## 5. Included findings
Owner-requested operational findings from September 23, 2026; no prior audit/package IDs are reassigned by this package.

## 6. Included behavior
- Protect equal/higher-ranked staff from `/freeze` and `/punish` target actions.
- Add `/staffwho` with online staff rank, vanish/staff-mode state, and pending punishment-request count.
- Notify online staff on freeze/unfreeze with a teleport action.
- Suppress vanished-player death/advancement broadcasts and remove vanished players from server-list ping samples/counts.
- Enforce mute state on private-message command aliases when RoseChat is absent, failing closed while mute state is unverified.
- Show current/next punishment ladder position in the punishment review GUI.
- Show frozen players who froze them, the reason, and the effective duration/hold behavior; blocked commands receive a reminder instead of failing silently.
- Keep clean staff-mode exits quiet while retaining warnings for genuine restore/checksum failures.
- Keep item confiscation usable when EnthusiaCurrency is absent without enabling economy confiscation.
- Record Paper disconnect presence without depending on DiscordSRV/Velocity so offline inventory inspection can become reachable on a standalone Paper backend.

## 7. Explicit exclusions
No permission-rank redesign; no changes to configured punishment ladders; no migration of Discord role authority; no production deployment/cutover; no redesign of inventory persistence; no weakening of fail-closed safety; no absorption of `ES-X03` or active issue #216 repair packages.

## 8. Dependencies
Current merged Staff runtime and migrations through the live `main` ceiling. Active concurrent PRs retain ownership of their own branches and findings; this package must reconcile any shared central wiring before merge.

## 9. Component and repository boundaries
`domain/auth`, Paper command/auth/freeze/visibility/staff/inventory/enforcement/runtime wiring, focused tests, plugin metadata, and directly related documentation/package state only.

## 10. Required branch
`package/es-p12-staff-operational-hardening`, created from exact `main` `fd999968ed5ffbd2e47e041482dc9e936528d7a7`.

## 11. Required PR
One draft implementation PR targeting `main`; normal merge only after exact-head evidence and review. No squash, rebase, force-push, auto-merge, or direct `main` push.

## 12. Implementation checklist
- [x] Reconcile live `main`, repository rules, package state, and open PR path ownership.
- [x] Create the owner-directed package branch from exact live `main`.
- [ ] Add and test explicit staff-target hierarchy policy and fresh target-rank resolution.
- [ ] Apply hierarchy checks to freeze and punishment prepare/confirm paths.
- [ ] Add `/staffwho` and pending-request summary.
- [ ] Add freeze alerts and frozen-player context/reminders.
- [ ] Add vanish-safe broadcast/ping handling.
- [ ] Add RoseChat-absent PM mute fallback without colliding with the active mute scheduling repair.
- [ ] Add punishment ladder progression presentation.
- [ ] Correct staff-mode exit verification messaging so clean exits are silent.
- [ ] Decouple item confiscation from EnthusiaCurrency while preserving operation locking guarantees.
- [ ] Add Paper-side presence disconnect tracking for bare-Paper/offline inventory use.
- [ ] Add focused authorization, rejection, fallback, retry/failure, conflict, and presentation tests.
- [ ] Run exact-head hosted clean build/tests/check/runtime-JAR, migration validation, static analysis, coverage, and Codacy; resolve all valid findings.
- [ ] Reconcile concurrent PR changes before final review and merge.

## 13. Acceptance criteria
A lower/equal-rank staff member cannot freeze or punish protected staff; staff can accurately inspect live staff state; vanish does not leak through the covered broadcasts/ping surface; mute fallback blocks PM aliases without RoseChat; punishment review shows escalation context; freezes explain their state; normal staff-mode exit is not noisy; item confiscation does not require the currency plugin; standalone Paper disconnects clear authoritative presence; existing fail-closed safety remains intact.

## 14. Test requirements
Cover allowed and denied hierarchy edges (including equal rank, higher rank, developer/founder/system semantics), rank changes between punishment draft and confirmation, staffwho count/presentation, freeze apply/release/reminder presentation, vanished broadcasts/ping filtering, PM command allowlist and unverified fail-closed behavior, ladder end/max-step behavior, clean-vs-mismatch staff-mode exit outcomes, no-currency confiscation availability and locking, and Paper disconnect persistence including storage/queue failure behavior.

## 15. Static-analysis requirements
No new valid Codacy findings. Complexity remains roughly CCN 8 and 50 lines per method where practical. False positives, if any, must be individually documented rather than globally excluded.

## 16. Documentation requirements
Update command/permission documentation or Wiki pages where the public operator surface changes. Record exact-head evidence in the PR/package handoff before completion.

## 17. Security and privacy requirements
Target hierarchy checks must use current authoritative rank evidence at the mutation boundary, fail closed when staff-rank authority cannot be verified, and never expose vanished staff to ordinary players. `/staffwho` is staff-only. Public messages use explicit presentation fields rather than internal records.

## 18. Migration impact
No schema change is planned. If implementation proves a migration is required, stop and reconcile the live migration ceiling/ownership before creating one.

## 19. Bedrock considerations
All new command/status information must remain usable as plain chat/text; clickable actions are additive convenience only.

## 20. Distributed-runtime considerations
Hierarchy must not trust stale Bukkit presence. Presence clearing is backend-scoped and must not overwrite a newer network observation. Vanish broadcast suppression applies to the local Paper broadcast/ping surface; network-wide proxy presentation remains outside this package unless existing interfaces already support it safely.

## 21. External-provider considerations
LuckPerms is the authoritative staff-rank source where hierarchy protection requires an offline/current target lookup. RoseChat absence activates the explicit PM fallback. EnthusiaCurrency absence disables only economy confiscation, not item confiscation.

## 22. Completion definition
All included behavior is implemented with focused tests; exact-head hosted validation and analyzers pass; zero new valid Codacy findings remain; concurrent branch ownership is reconciled; the PR is review-ready with no hidden production/cutover action.

## 23. Resume state
Active implementation. Resume only this package/branch/PR until it reaches a protocol-valid terminal state.

## 24. Exact-head evidence
Pending implementation head and hosted validation.

## 25. Private acceptance boundary
No live production deployment is authorized by this package. Staging/manual evidence may supplement but does not replace exact-head repository validation.

## 26. Merge and synchronization record
Branch base: `fd999968ed5ffbd2e47e041482dc9e936528d7a7`. Merge record pending.

## 27. Remaining package work
All unchecked checklist items above.

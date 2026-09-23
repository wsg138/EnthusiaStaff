# `ES-P12` — Staff operational hardening

## 1. Package identity
`ES-P12`; Internal; primary `COMP-STAFF`; owner-directed feature package; priority 95.

## 2. Status
`VALIDATING` — implementation, focused tests, and repeated final code-level review are complete on `package/es-p12-staff-operational-hardening`; exact-head hosted validation, Codacy, external review, and final concurrent-path reconciliation remain after the latest executable repair.

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
- [x] Add and test explicit staff-target hierarchy policy and fresh target-rank resolution.
- [x] Apply hierarchy checks to freeze and punishment prepare/confirm paths.
- [x] Add `/staffwho` and pending-request summary.
- [x] Add freeze alerts and frozen-player context/reminders.
- [x] Add vanish-safe broadcast/ping handling.
- [x] Add RoseChat-absent PM mute fallback without colliding with the active mute scheduling repair.
- [x] Add punishment ladder progression presentation.
- [x] Correct staff-mode exit verification messaging so clean exits do not emit false mismatch warnings.
- [x] Decouple item confiscation from EnthusiaCurrency while preserving operation locking guarantees.
- [x] Add Paper-side presence disconnect tracking for bare-Paper/offline inventory use.
- [x] Add focused authorization, rejection, fallback, retry/failure, conflict, and presentation tests.
- [x] Perform final code-level review and repair scheduler/thread-ownership defects found in `/staffwho`, server-list vanish filtering, and freeze-alert fanout.
- [ ] Run exact-head hosted clean build/tests/check/runtime-JAR, migration validation, static analysis, coverage, and Codacy after the latest executable repair; resolve all valid findings.
- [ ] Reconcile concurrent PR changes and external review before final merge transition.

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
LuckPerms is the authoritative staff-rank source where hierarchy protection requires an offline/current target lookup. RoseChat absence activates the explicit PM fallback. EnthusiaCurrency absence disables only economy confiscation, not item confiscation. A present but incompatible EnthusiaCurrency provider fails closed rather than silently weakening cross-asset locking.

## 22. Completion definition
All included behavior is implemented with focused tests; exact-head hosted validation and analyzers pass; zero new valid Codacy findings remain; concurrent branch ownership is reconciled; the PR is review-ready with no hidden production/cutover action.

## 23. Resume state
Validation in progress. Resume only this package/branch/PR until it reaches a protocol-valid terminal state.

## 24. Exact-head evidence
- Repository-native PMD 6.55 diagnostics run `35894852700` exposed 13 concrete findings across changed Java; no broad suppressions were introduced.
- Atomic PMD repair run `35895206799` passed the repository PMD 6.55 rules with zero findings and passed focused `:domain:test :paper:test` before publishing repair commit `419412b4a8615063f5e180a9306c29cc238dee66`.
- Lizard 1.23 diagnostics identified the remaining new constructor-bound complexity. Validated repair run `35896989276` passed the targeted Lizard bounds, repository PMD 6.55 with zero findings, and `:paper:test`, then published `31d8fb455b39d4b88d9fe36d921b15626cb76705` and removed all temporary complexity diagnostic/repair files.
- Final code review found `/staffwho` reading other players from the invoking player's region. Commit `782e865897d7e9c2731df3e65b1687a71b7f52a2` moved every staff snapshot onto the target player's entity scheduler, made retirement/rejection completion exactly-once, and routes player responses back through the sender's entity scheduler. Regression coverage verifies those ownership boundaries.
- Final code review also rejected the original server-roster scan in the ping listener. The first event-iterator replacement on `1db920bc501c46b1efe6c7a332dd59cca045d36e` was correctly rejected by Sentinel run `35930515437` because Paper marks that iterator deprecated-for-removal and this repository compiles with `-Werror`; no suppression was added and that head is not passing evidence.
- Validated repair run `35930958715` replaced the deprecated iterator with `getListedPlayers()` for sample filtering plus a concurrency-safe online UUID snapshot already maintained by `VanishManager`; `:paper:test` and `-Werror` compilation passed before publishing repair commit `6ed79fdf1abc2e2868ab6b1f275413bc09359938` and deleting the temporary repair harness.
- Exact head `91dccbe018484d055563278857b0bf6be0f8cae8` then passed Coverage `35931225668`, Sentinel artifact `35931225551`, Wiki `35931225621`, and Codacy static with zero new issues, but those results became historical when the later freeze-alert ownership defect was found and executable code changed.
- Repeated review found `FreezeStaffNotifier` reading recipient permissions and sending messages from the global region after only moving roster traversal to the global scheduler. Repair workflow `35934026437` moved each staff check and delivery onto the recipient entity scheduler, added rejection/ownership regressions, passed Lizard 1.23 bounds, repository PMD 6.55 with zero findings, full `:paper:test`, and `git diff --check`, then published product repair commit `12397f91da774f61b4bd2f023eba8cf876afd23f` and removed its temporary repair harness.
- PR #220 currently also changes `VanishManager`, but only its `persistState()` transaction path around the later persistence section. ES-P12's added `vanishedOnlineCount()` sits beside `isVanished()` and does not overlap that hunk; final live-head reconciliation is still required before merge.
- Live `main` remains `fd999968ed5ffbd2e47e041482dc9e936528d7a7`, matching the package base, so no mainline reconciliation is currently required.
- Product repair commit `12397f91da774f61b4bd2f023eba8cf876afd23f` was authored by `github-actions[bot]`. This package-state commit intentionally retriggers the standard exact-head Coverage, Sentinel artifact, Wiki validation, Codacy, review, and non-draft staging/supersession controls under the normal repository actor; those results will supersede all earlier acceptance checks.

## 25. Private acceptance boundary
No live production deployment is authorized by this package. Staging/manual evidence may supplement but does not replace exact-head repository validation.

## 26. Merge and synchronization record
Branch base and current live `main`: `fd999968ed5ffbd2e47e041482dc9e936528d7a7`. Active PR #220 has a file-level but hunk-disjoint overlap in `VanishManager`; its transaction-owned `persistState()` repair and this package's scheduler-safe online-count accessor must both survive final reconciliation. Final mergeability/review reconciliation and merge record remain pending.

## 27. Remaining package work
Obtain green exact-head hosted Coverage/build/tests/runtime-JAR, Sentinel artifact, Wiki validation, Codacy zero-new-valid-finding evidence, and terminal external review on the normal-actor head that includes `12397f91da774f61b4bd2f023eba8cf876afd23f`; re-read live main and active overlapping PRs; perform final mergeability/review reconciliation. No merge is authorized by this record alone.

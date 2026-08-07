# `ES-P04` — Staff-mode operational tools

## 1. Package identity
`ES-P04`; Internal; primary `COMP-STAFF`; priority 40; not parallel-safe around staff-mode dispatch/state.

## 2. Status
`ACTIVE` / `ACTIONABLE_CONTINUATION` — implementation and documentation are complete on PR #79; final exact-head validation and terminal publication remain.

## 3. Objective
Make staff-mode hotbar tools functional and complete safe operational entry, use, restoration, and recovery behavior.

## 4. Why the package exists
The audit found tagged tools with no interaction dispatcher while shared staff-session safety requires one coherent runtime package.

## 5. Included audit IDs
`AUD-STAFF-001`, `AUD-STAFF-002`, `AUD-STAFF-003`, `AUD-STAFF-004`.

## 6. Included behavior
Authorized tool dispatch for teleport, inspect, freeze, reports, spectate/follow, vanish, staff chat, and menu; snapshots/restoration, reconnect/reload/shutdown/rank changes, transfer/world/damage isolation, stale tools, cooldowns, and missing dependencies.

## 7. Explicit exclusions
Cheat testers/fake entities (`ES-P10`); fake bases (`ES-P11`); production deployment; unrelated vanish/freeze redesign.

## 8. Dependencies
`ES-P03` must be `COMPLETE`; satisfied by merge `b960e91ea59627a870ff24f89c2f761d0cbb68ab` and canonical completion publication.

## 9. Component and repository boundaries
Root Paper/domain/persistence/tests/resources/docs needed for staff mode. No external source import, permanent component branch, or isolated PR.

## 10. Required branches
Temporary `package/es-p04-staff-mode-tools`; created from exact legitimate `main` `5c820c29c2fe5a498ea7f80454579953ac05b436`; delete after verified merge containment.

## 11. Required PRs
PR #79 to `wsg138/EnthusiaStaff:main`; marked ready for review after the implementation head was frozen.

## 12. Implementation checklist
- [x] Reconcile live GitHub and classify every incomplete package.
- [x] Confirm ES-P02 remains `PARKED_BLOCKED` on the unchanged private `ubuntu-latest` Billing & plans zero-runner failure.
- [x] Select ES-P04 as the lowest-priority-number dependency-complete `READY` package.
- [x] Create the required temporary branch from exact legitimate `main`.
- [x] Map tags/actions/permissions and existing runtime services.
- [x] Implement dispatcher and safe state transitions without duplicating existing moderation/service boundaries.
- [x] Add focused tests for tool identity/session binding, fixed slots/ranks, cooldowns, target suitability, and settings.
- [x] Document commands/permissions, behavior, recovery, troubleshooting, Bedrock fallbacks, filtering, and restart-scoped settings.
- [x] Resolve all 14 Codacy findings from superseded head `c1db641169a40d0be64ad48b4672d3dce3da72c4`.
- [x] Harden Paper interaction-event behavior for predicted-cancelled air clicks and redundant precise entity packets.
- [x] Resolve the final dispatcher file-size Codacy finding by extracting `StaffToolRandomTeleportService`.
- [x] Fix the extracted service compile regression found by exact-head hosted run `31173353511`, job `92849833109`; product head `dd1f10a12680eab6768473aee5b542e840b09b65` restores the missing two-argument entity-scheduler helper overload.
- [ ] Freeze the resulting records checkpoint and complete every applicable validation/review gate on that exact head.
- [ ] Resolve the configured Pi staging disposition without treating unavailable infrastructure as a pass.
- [ ] Merge normally if every completion gate is satisfied; otherwise publish the correct terminal blocked state.

## 13. Acceptance criteria
Each non-excluded tool performs its documented action only for authorized active staff; stale/transferred/spoofed tools fail safely; exact player state restores across exit/reconnect/reload/shutdown/rank loss; no duplication/loss or world bypass.

## 14. Test requirements
Focused staff-tool tests plus full Java 21 repository suites, MariaDB/Testcontainers migrations, coverage, runtime-JAR/provider-leak checks, and applicable review/static gates.

## 15. Static-analysis requirements
Configured Java/static-analysis/review-bot gates; zero valid unresolved findings.

## 16. Documentation requirements
Commands, permissions, tool behavior, recovery/troubleshooting, Bedrock fallback, Wiki, package state, and handoff.

## 17. Security and privacy requirements
Service-boundary authorization; reject forged PDC items and stale sessions; do not expose private evidence or hidden staff state.

## 18. Migration impact
No migration change. Immutable migration boundary remains V17.

## 19. Bedrock considerations
Every new staff-tool surface has a usable command/text fallback. Representative Java/Bedrock acceptance remains assigned to `ES-V02`.

## 20. Distributed-runtime considerations
Session ownership, backend switching, reconnect, duplicate events, and recovery remain fenced through the existing durable runtime; movement target state is sampled on Folia entity schedulers and actor authorization is rechecked before final movement.

## 21. External-provider considerations
Inspector, freeze, reports, vanish, and staff-chat hotbar actions reuse existing command/service paths so unsupported providers fail through established behavior; no replacement callbacks were invented.

## 22. Completion definition
All non-excluded tools and safety criteria are proven; one exact-head PR merges normally; zero valid threads; temporary branch removed when safe.

## 23. Resume state
Active branch `package/es-p04-staff-mode-tools`; PR #79; starting `main` `5c820c29c2fe5a498ea7f80454579953ac05b436`; generic sequential package worker. Latest product-code commit before this checkpoint: `dd1f10a12680eab6768473aee5b542e840b09b65`. Canonical handoff: [`2026-08-07-es-p04-staff-mode-tools.md`](../../reports/package-handoffs/2026-08-07-es-p04-staff-mode-tools.md).

## 24. Last completed checkpoint
All intended product behavior remains implemented and documented. Static-analysis findings were structurally removed without weakening authorization. The first exact head after the final file split, `93858cf68c539a0c1fe62b20d1993607981517ab`, passed Wiki validation and reached a real GitHub-hosted Java runner but failed compilation because `StaffToolRandomTeleportService.message(...)` referenced a missing convenience overload. Hosted run `31173353511`, job `92849833109`, reported that exact compile error. Product commit `dd1f10a12680eab6768473aee5b542e840b09b65` restores only that helper overload and keeps the scheduler-safe implementation unchanged.

The private Pi path remains externally unavailable. On superseded exact head `501cb2a90fab0c28a080a64ce51e81d10f2f0da5`, private staging run `31172664638`, Ubuntu job `92847691164`, had runner ID `0`, empty runner name, steps `[]`, and GitHub's Billing & plans rejection; Pi `92847702006` skipped. This is not a pass and must be re-evidenced on the final records head.

## 25. Remaining checklist
Freeze the records checkpoint produced with this state update; obtain successful exact-head Wiki/Java 21/full-suite/MariaDB-migration/coverage/runtime-JAR/static-analysis/review evidence; record the exact Pi disposition; then merge normally or park the package on the remaining external gate. Do not start ES-P09.

## 26. Known blockers
No dependency blocker and no known product blocker after `dd1f10a12680eab6768473aee5b542e840b09b65`. The configured Pi staging path is still expected to be externally blocked before product execution by the private repository Billing & plans restriction. No owner-approved ES-P04 exception exists in the inspected state.

## 27. Current evidence
- Starting main: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- PR: #79.
- Latest product-code head: `dd1f10a12680eab6768473aee5b542e840b09b65`.
- Superseded exact-head Wiki `31173353489` / `92849855813`: success.
- Superseded exact-head hosted build `31173353511` / `92849833109`: failed compilation on the missing helper overload; fixed by `dd1f10a12680eab6768473aee5b542e840b09b65`.
- Codacy PR summary after file split showed 0 new issues before that compile-only fix; final exact-head rerun remains required.
- PR was marked ready for review and `@coderabbitai review` was requested; final exact-head review disposition remains required.
- No migration changes; V1-V17 unchanged.

## 28. Merge and synchronization record
Unset. Record normal merge commit, resulting `main`, containment, temporary branch cleanup, and final status publication only after all required gates are satisfied. External parity is not applicable.

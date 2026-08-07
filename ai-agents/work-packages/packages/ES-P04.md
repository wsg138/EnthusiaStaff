# `ES-P04` — Staff-mode operational tools

## 1. Package identity
`ES-P04`; Internal; primary `COMP-STAFF`; priority 40; not parallel-safe around staff-mode dispatch/state.

## 2. Status
`ACTIVE` / `ACTIONABLE_CONTINUATION` — implementation and documentation are complete on draft PR #79; exact-head validation and terminal publication remain.

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
Draft PR #79 to `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist
- [x] Reconcile live GitHub and classify every incomplete package.
- [x] Confirm ES-P02 remains `PARKED_BLOCKED` on the unchanged private `ubuntu-latest` Billing & plans zero-runner failure.
- [x] Select ES-P04 as the lowest-priority-number dependency-complete `READY` package.
- [x] Create the required temporary branch from the exact legitimate default head.
- [x] Map tags/actions/permissions and existing runtime services.
- [x] Implement dispatcher and safe state transitions without duplicating existing moderation/service boundaries.
- [x] Add focused policy tests for tool identity/session binding, fixed slots/ranks, cooldowns, target suitability, and settings.
- [x] Document commands/permissions, tool behavior, recovery, troubleshooting, Bedrock fallbacks, target filtering, and restart-scoped settings.
- [x] Review the full product diff and resolve the first Codacy finding set; 14 issues on superseded head `c1db641169a40d0be64ad48b4672d3dce3da72c4` were refactored away in `b83d060b97f9dc46f59fc7d137e0d01672f407fb`.
- [x] Harden Paper interaction-event behavior after API review so predicted-cancelled air clicks are accepted and redundant precise entity packets do not double-dispatch; product-code head `e7321ff2858f06da6d4daeca073fcb5a96bf4246`.
- [ ] Freeze one exact tracked head and complete every applicable validation/review gate.
- [ ] Resolve the configured Pi staging disposition without treating unavailable infrastructure as a pass.
- [ ] Merge normally if all completion gates are satisfied, verify containment, publish completion state, and clean the branch when safe.

## 13. Acceptance criteria
Each non-excluded tool performs its documented action only for authorized active staff; stale/transferred/spoofed tools fail safely; exact player state restores across exit/reconnect/reload/shutdown/rank loss; no duplication/loss or world bypass.

## 14. Test requirements
Listener/dispatcher behavior through hosted build plus focused pure-policy tests; permission/hierarchy, inventory event, snapshot/revision, reconnect/reload/shutdown, concurrency, missing-provider, text-fallback behavior, and full existing suites.

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
Session ownership, backend switching, reconnect, duplicate events, and recovery remain fenced through the existing durable staff-session runtime; direct movement target state is sampled on Folia entity schedulers and actor authorization is rechecked before final movement.

## 21. External-provider considerations
Inspector, freeze, reports, vanish, and staff-chat hotbar actions reuse existing command/service paths so unsupported or unavailable providers fail through the established behavior; no replacement callbacks were invented.

## 22. Completion definition
All non-excluded tools and safety criteria are proven; one exact-head PR merges normally; zero valid threads; temporary branch removed when safe.

## 23. Resume state
Active branch `package/es-p04-staff-mode-tools`; draft PR #79; starting `main` `5c820c29c2fe5a498ea7f80454579953ac05b436`; generic sequential package worker. Latest product-code commit before this checkpoint: `e7321ff2858f06da6d4daeca073fcb5a96bf4246`. Canonical handoff: [`2026-08-07-es-p04-staff-mode-tools.md`](../../reports/package-handoffs/2026-08-07-es-p04-staff-mode-tools.md).

## 24. Last completed checkpoint
The non-excluded operational tools are implemented and documented. Staff tools use fixed canonical slots and carry owner/session PDC bindings that are revalidated against the active staff session, current rank, slot, material, and action permission. Durable moderation actions reuse existing commands. Random teleport filters active staff, vanished/frozen/exempt/unsafe targets and disabled worlds; follow/spectate uses scheduler-safe target snapshots and preserves the rank-required game mode. `/stafftools` provides text/command fallbacks for Bedrock clients. Cheat Tester remains explicitly unavailable and assigned to ES-P10.

Static analysis on superseded head `c1db641169a40d0be64ad48b4672d3dce3da72c4` reported 14 issues, all addressed by structural refactoring. Wiki validation already passed on that superseded head. PR #79 currently has zero inline review threads and zero submitted reviews.

Configured Pi staging remains externally unavailable: prior PR heads reached the aggregate wrapper, whose private ordinary `ubuntu-latest` prerequisite received runner ID `0`, empty runner name, zero product steps, and the GitHub Billing & plans payment/spending-limit annotation; the downstream self-hosted Pi job was skipped. The same condition must be re-evidenced on the frozen exact head if it remains applicable. It is not a pass.

## 25. Remaining checklist
Publish this checkpoint, freeze the resulting exact head if no new review/static-analysis finding appears, obtain successful exact-head Wiki/Java 21/full-suite/MariaDB-migration/coverage/runtime-JAR/static-analysis/review evidence, record the exact Pi staging disposition, and then either merge normally or park the package on the remaining external gate. Do not start ES-P09 or another package.

## 26. Known blockers
No dependency or known product blocker. The configured Pi staging path is currently blocked before product execution by the private repository Billing & plans restriction. Completion cannot claim that gate passed; if ordinary hosted/review gates all pass but Pi remains unavailable, the terminal outcome must follow `VALIDATION-POLICY.md` exactly, including explicit owner approval if an infrastructure exception is used.

## 27. Current evidence
- Starting main: `5c820c29c2fe5a498ea7f80454579953ac05b436`.
- Draft PR: #79.
- First coherent product commit: `b70c6c48976ca176a3ed5c9c327a31d9422ed934`.
- Documentation commit: `c1db641169a40d0be64ad48b4672d3dce3da72c4`.
- Static-analysis remediation: `b83d060b97f9dc46f59fc7d137e0d01672f407fb`.
- Interaction-event hardening / latest product-code commit: `e7321ff2858f06da6d4daeca073fcb5a96bf4246`.
- Superseded Wiki run `31171631905`, job `92844514821`: success.
- Superseded Codacy check `92844755477`: 14 findings, all addressed in later commits; not final evidence.
- PR review threads at checkpoint: zero.
- No migration changes; V1-V17 unchanged.

## 28. Merge and synchronization record
Unset. Record normal merge commit, resulting `main`, containment, temporary branch cleanup, and final status publication only after all required gates are satisfied. External parity is not applicable.

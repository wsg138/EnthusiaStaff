# `ES-P04` — Staff-mode operational tools

## 1. Package identity
`ES-P04`; Internal; primary `COMP-STAFF`; priority 40; not parallel-safe around staff-mode dispatch/state.

## 2. Status
`ACTIVE` / `ACTIONABLE_CONTINUATION` — claimed by the generic sequential package worker after live reconciliation. Starting aggregate `main`: `5c820c29c2fe5a498ea7f80454579953ac05b436`.

## 3. Objective
Make staff-mode hotbar tools functional and complete safe operational entry, use, restoration, and recovery behavior.

## 4. Why the package exists
The audit found tagged tools with no interaction dispatcher while shared staff-session safety requires one coherent runtime package.

## 5. Included audit IDs
`AUD-STAFF-001`, `AUD-STAFF-002`, `AUD-STAFF-003`, `AUD-STAFF-004`.

## 6. Included behavior
Implement authorized tool dispatch for teleport, inspect, freeze, reports, spectate, vanish, staff chat, and menu; prove snapshots/restoration, reconnect/reload/shutdown/rank changes, transfer/world/damage isolation, stale tools, cooldowns, and missing dependencies.

## 7. Explicit exclusions
Cheat testers/fake entities (`ES-P10`); fake bases (`ES-P11`); production deployment; unrelated vanish/freeze redesign.

## 8. Dependencies
`ES-P03` must be `COMPLETE`; satisfied by merge `b960e91ea59627a870ff24f89c2f761d0cbb68ab` and canonical completion publication.

## 9. Component and repository boundaries
Root Paper/domain/persistence/tests/resources/docs needed for staff mode. No external source import, permanent component branch, or isolated PR.

## 10. Required branches
Temporary `package/es-p04-staff-mode-tools`; created from exact legitimate `main` `5c820c29c2fe5a498ea7f80454579953ac05b436`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`; open as draft after the first coherent checkpoint.

## 12. Implementation checklist
- [x] Reconcile live GitHub and classify every incomplete package.
- [x] Confirm ES-P02 remains `PARKED_BLOCKED` on the unchanged private `ubuntu-latest` Billing & plans zero-runner failure.
- [x] Select ES-P04 as the lowest-priority-number dependency-complete `READY` package.
- [x] Create the required temporary branch from the exact legitimate default head.
- [ ] Map tags/actions/permissions and existing runtime services.
- [ ] Implement dispatcher and safe state transitions.
- [ ] Test every non-excluded tool and failure/recovery path.
- [ ] Document commands/permissions, tool behavior, recovery, troubleshooting, and Bedrock fallbacks.
- [ ] Harsh-review the full diff and resolve all valid findings.
- [ ] Freeze one exact head and complete every applicable validation gate.
- [ ] Merge normally, verify containment, publish completion state, and clean the branch when safe.

## 13. Acceptance criteria
Each non-excluded tool performs its documented action only for authorized active staff; stale/transferred/spoofed tools fail safely; exact player state restores across exit/reconnect/reload/shutdown/rank loss; no duplication/loss or world bypass.

## 14. Test requirements
Listener/dispatcher, permission/hierarchy, inventory event, snapshot/revision, reconnect/reload/shutdown, concurrency, missing provider, and text-fallback tests plus full suites.

## 15. Static-analysis requirements
Configured Java/static-analysis/review-bot gates; zero valid unresolved findings.

## 16. Documentation requirements
Commands, permissions, tool behavior, recovery/troubleshooting, Bedrock fallback, Wiki, package state, and handoff.

## 17. Security and privacy requirements
Service-boundary authorization; reject forged PDC items and stale sessions; do not expose private evidence or hidden staff state.

## 18. Migration impact
No migration expected; current immutable boundary is V17. Any essential schema change uses a new post-V17 migration with upgrade/checksum tests.

## 19. Bedrock considerations
Every GUI/tool action needs a usable command/text fallback; representative acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Session ownership, backend switching, reconnect, duplicate events, and recovery must remain fenced across runtimes.

## 21. External-provider considerations
Existing provider actions must be version-checked and explicitly unavailable when unsupported; no invented callbacks.

## 22. Completion definition
All non-excluded tools and safety criteria are proven; one exact-head PR merges normally; zero valid threads; temporary branch removed when safe.

## 23. Resume state
Active branch `package/es-p04-staff-mode-tools`; starting `main` `5c820c29c2fe5a498ea7f80454579953ac05b436`; generic sequential package worker. Canonical handoff: [`2026-08-07-es-p04-staff-mode-tools.md`](../../reports/package-handoffs/2026-08-07-es-p04-staff-mode-tools.md).

## 24. Last completed checkpoint
Live routing reconciled. ES-P02 remains parked because the newest private `plugin-live-test.yml` ordinary build still received `runner_id: 0`, empty runner name, no steps, and the same Billing & plans payment/spending-limit annotation; its Pi job was skipped. ES-P04 was therefore selected and its required branch was created from current `main`.

## 25. Remaining checklist
Map existing tool/action contracts, implement the non-excluded dispatcher and safety hardening, add focused and full-suite tests, document behavior, review, exact-head validate, merge, contain, publish completion, and clean up.

## 26. Known blockers
No dependency blocker. Provider-specific actions may degrade safely but must not be invented. Private/Pi staging is not a completion substitute; representative Java/Bedrock acceptance remains owned by `ES-V02`.

## 27. Final evidence
Unset: exact implementation head, tool coverage matrix, test/check IDs, review disposition, static analysis, coverage, runtime-JAR hashes, and documentation validation.

## 28. Merge and synchronization record
Unset: record normal merge commit, resulting `main`, containment, temporary branch cleanup; external parity is not applicable.

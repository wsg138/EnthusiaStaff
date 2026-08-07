# `ES-P04` — Staff-mode operational tools

## 1. Package identity
`ES-P04`; Internal; primary `COMP-STAFF`; priority 40; not parallel-safe around staff-mode dispatch/state.

## 2. Status
`READY` — dependency satisfied; unassigned; not activated by the ES-X05 recovery worker. Registry is authoritative.

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
Temporary `package/es-p04-staff-mode-tools`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist
Reconcile; map tags/actions/permissions; implement dispatcher and safe state transitions; test every tool and failure/recovery path; document commands/permissions; checkpoint/review/freeze/validate/merge/cleanup.

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
No migration expected; any essential schema change uses a new post-V17 migration with upgrade/checksum tests.

## 19. Bedrock considerations
Every GUI/tool action needs a usable command/text fallback; representative acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Session ownership, backend switching, reconnect, duplicate events, and recovery must remain fenced across runtimes.

## 21. External-provider considerations
Existing provider actions must be version-checked and explicitly unavailable when unsupported; no invented callbacks.

## 22. Completion definition
All non-excluded tools and safety criteria are proven; one exact-head PR merges normally; zero valid threads; temporary branch removed when safe.

## 23. Resume state
Unassigned; no branch/PR/handoff. Eligible for normal sequential selection after outage recovery; priority 40. Live continuation classification still precedes new selection.

## 24. Last completed checkpoint
Dependency `ES-P03` is complete; no ES-P04 product work has begun.

## 25. Remaining checklist
All implementation, tests, review, validation, merge, and evidence remain.

## 26. Known blockers
No dependency blocker. Provider-specific actions may degrade safely but must not be invented.

## 27. Final evidence
Unset: exact heads, tool coverage matrix, test/check IDs, review disposition, and docs.

## 28. Merge and synchronization record
Unset: record merge/containment/temporary branch cleanup; parity not applicable.
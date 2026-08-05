# `ES-P09` — Alt and network-identity completion

## 1. Package identity
`ES-P09`; Internal; primary `COMP-STAFF`; priority 55; conditionally parallel after identity correctness.

## 2. Status
Initial `PLANNED`; registry is authoritative.

## 3. Objective
Complete protected network identity, alt graph/confidence/manual relationships, IP/network sanction inheritance, privacy, retention, and operator workflow.

## 4. Why the package exists
The audit found low-proof graph/storage and false-positive behavior despite protected-token foundations and active network sanctions.

## 5. Included audit IDs
`AUD-ALT-001`, `AUD-ALT-002`, `AUD-ALT-003`, development portions of `AUD-ALT-004`, relevant `AUD-SEC-002`.

## 6. Included behavior
Protected address token/key lifecycle; identity graph/confidence and ambiguity controls; manual relationships/exceptions; `/alts` and `/alt` workflow; inheritance and audit; bounded retention/purge/restart; safe logging; migration interaction.

## 7. Explicit exclusions
Production/private address datasets; Bedrock platform fix (`ES-P03`); production key rotation; acceptance/false-positive campaign (`ES-V02`).

## 8. Dependencies
`ES-P03` must be `COMPLETE`.

## 9. Component and repository boundaries
Root identity/alt/persistence/Velocity/tests/docs only. No external source import, permanent component branch, or isolated PR.

## 10. Required branches
Temporary `package/es-p09-alt-network-identity`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist
Reconcile data flows; model graph/confidence/manual exceptions/retention; implement commands and inheritance safety; test privacy/races/restart; update docs/state/handoff; review/freeze/validate/merge/cleanup.

## 13. Acceptance criteria
Raw addresses never persist/log; deterministic protected tokens and key-version handling work; ambiguous links cannot silently inherit sanctions; manual decisions are authorized/audited; queries/retention are bounded; restart and concurrent proxies are safe.

## 14. Test requirements
Protector/key-version, graph confidence, ambiguity/manual exception, multi-proxy duplicate input, sanction inheritance, retention/purge, restart, migration interaction, authorization and redaction tests.

## 15. Static-analysis requirements
Configured Java/static-analysis/review-bot gates; zero valid unresolved findings.

## 16. Documentation requirements
Privacy model, commands/permissions, confidence/ambiguity/manual workflows, keys/retention, troubleshooting, Wiki, registry/handoff.

## 17. Security and privacy requirements
Never expose raw/reversible addresses; keys from environment/secret store; least-privilege views; bounded retention; fail closed on key/version uncertainty.

## 18. Migration impact
New post-V16 migration only if necessary for graph/manual/retention state, with clean/upgrade/checksum tests.

## 19. Bedrock considerations
Consume corrected platform identity from `ES-P03`; ensure prefixed names do not change network identity semantics; acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Multiple proxies, reconnect, duplicate observations, ordering, inheritance races, process restart, and retention jobs must be safe.

## 21. External-provider considerations
No external source provider required; integrate only through verified Floodgate/runtime identity boundaries.

## 22. Completion definition
Development scope proven and merged in one exact-head PR with zero valid threads and branch cleanup; private false-positive/staging acceptance remains deferred.

## 23. Resume state
Unassigned; no branch/PR/handoff. Start only after `ES-P03` and assignment.

## 24. Last completed checkpoint
Definition only; no product implementation began.

## 25. Remaining checklist
All implementation, tests, review, validation, merge, and evidence remain.

## 26. Known blockers
Dependency `ES-P03`; private representative data unavailable by design.

## 27. Final evidence
Unset: exact heads, privacy review, graph/inheritance test matrix, checks, docs.

## 28. Merge and synchronization record
Unset: normal merge/resulting main/containment/temporary branch cleanup; parity not applicable.

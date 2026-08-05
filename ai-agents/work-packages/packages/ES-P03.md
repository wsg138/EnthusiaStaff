# `ES-P03` — Bedrock identity correctness

## 1. Package identity
`ES-P03`; Internal; primary `COMP-STAFF`; priority 30; sequential after lifecycle recovery.

## 2. Status
Initial `PLANNED`; registry is authoritative.

## 3. Objective
Persist and resolve Java and Floodgate/Bedrock identity correctly across Paper, Velocity, moderation, and network identity.

## 4. Why the package exists
The audit confirmed unconditional Java platform writes and related offline/name-history risks that affect later staff, reports, alts, and staging.

## 5. Included audit IDs
`AUD-ID-001`, `AUD-ID-004`, relevant `AUD-ALT-004`.

## 6. Included behavior
Remove unconditional Java writes; preserve Floodgate UUID/prefixed-name/history semantics; correct join, mute, proxy/backend, offline target, and alt-record updates; add regression tests.

## 7. Explicit exclusions
General alt completion; changing Floodgate rules; claiming live Bedrock acceptance; unrelated identity redesign.

## 8. Dependencies
`ES-P02` must be `COMPLETE`.

## 9. Component and repository boundaries
Root identity/runtime/persistence/tests/docs only. No external source import, permanent component branch, or isolated PR.

## 10. Required branches
Temporary `package/es-p03-bedrock-identity`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist
Reconcile live state; trace every platform write/read; implement canonical identity resolution; test Java/Bedrock/history/offline/races/restart; update durable state; review; freeze; exact-head validate; merge/cleanup.

## 13. Acceptance criteria
Every player platform write derives from verified runtime evidence; Java and Bedrock records remain stable across server changes/reconnects; name/UUID resolution is deterministic and privacy-safe; no Java-only fallback corrupts Bedrock records.

## 14. Test requirements
Paper/Velocity unit/integration tests for join/mute/directory/network identity, historical names, prefixed aliases, duplicate updates, restart, and unavailable Floodgate behavior.

## 15. Static-analysis requirements
Java 21 warnings-as-errors and all configured analysis/review bots with zero valid unresolved findings.

## 16. Documentation requirements
Update identity/Bedrock/operator/developer docs and package registry/handoff; do not claim representative client acceptance.

## 17. Security and privacy requirements
No raw addresses or player rows in evidence; protected identity storage remains fail-closed; prevent identity spoofing/cross-platform collision.

## 18. Migration impact
No migration assumed; new migration only if essential after verifying V16, with immutable upgrade/checksum tests.

## 19. Bedrock considerations
This package owns implementation correctness; `ES-V02` owns representative Java/Bedrock staging acceptance.

## 20. Distributed-runtime considerations
Account for multiple proxies/backends, server switching, duplicate directory writes, reconnect, and eventual consistency.

## 21. External-provider considerations
Use supported Floodgate/Geyser APIs only; explicit safe behavior when absent/incompatible.

## 22. Completion definition
All included behavior/tests/docs pass; exact head reviewed; one PR merged normally; branch cleanup verified; no live-staging claim.

## 23. Resume state
Unassigned; no branch/PR/handoff. Start only after assignment and dependency completion.

## 24. Last completed checkpoint
Package definition only; no product implementation began.

## 25. Remaining checklist
All implementation, regression, validation, review, merge, and evidence remain.

## 26. Known blockers
`ES-P02`; representative Bedrock acceptance intentionally deferred to `ES-V02`.

## 27. Final evidence
Unset: record exact heads, tests, analysis, review threads, and affected platform-write inventory.

## 28. Merge and synchronization record
Unset: record merge/containment/temporary branch cleanup; external parity not applicable.

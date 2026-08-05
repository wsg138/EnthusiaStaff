# `ES-X01` — RoseChat provider and communication integration

## 1. Package identity
`ES-X01`; External/multi-repository; primary `COMP-STAFF`; other `COMP-ROSECHAT`; priority 100; conditional parallel safety.

## 2. Status
Initial `PLANNED`; registry is authoritative. Standalone repository currently unresolved.

## 3. Objective
Use the verified supported RoseChat repository to implement staff chat, private-message evidence, and presence integration safely.

## 4. Why the package exists
Communication routing/audiences and PM evidence are blocked by an unavailable provider implementation; inventing APIs would create privacy and duplication defects.

## 5. Included audit IDs
`AUD-COMMS-001`, `AUD-COMMS-003`, RoseChat portion of `AUD-REPORT-003`, provider portion of `AUD-VANISH-003`.

## 6. Included behavior
First resolve the actual repository/default head; supported callbacks and delivery timing; sender/recipient/cancellation/filter/ignore/spy semantics; staff audiences/formatting/cross-server duplicate prevention/disconnect; privacy-safe PM evidence/retention; presence integration; matching aggregate copy and parity.

## 7. Explicit exclusions
Invented repository/API; reflection against unknown implementation; log scraping as callback; unrelated voice provider; production PM data/routes.

## 8. Dependencies
`ES-P03`, `ES-P04`, and `ES-P05` must be `COMPLETE`.

## 9. Component and repository boundaries
`wsg138/EnthusiaStaff` root integration plus `components/enthusia-rosechat/`, and the verified standalone repository only. No permanent component branch or isolated PR.

## 10. Required branches
Temporary `package/es-x01-rosechat-provider` in EnthusiaStaff and the standalone repository (or stricter compatible standalone convention); delete after verified merges.

## 11. Required PRs
Two cross-referenced same-ID PRs: one standalone and one to `EnthusiaStaff:main`. No third/isolated PR.

## 12. Implementation checklist
Verify repository/license/history/AGENTS and source head; import/update aggregate copy safely; define/version contract; implement both sides; test privacy/audience/duplicates/restart; update metadata/state/handoff; review/freeze/validate both heads; merge both; run deterministic parity; cleanup.

## 13. Acceptance criteria
Repository and contract are verified; staff/PM events obey provider semantics and privacy; no duplicate or fail-open routing; missing/incompatible provider is explicit; both PRs merge and aggregate content equals standalone default head.

## 14. Test requirements
Each repository's full suites plus callback timing, cancellation/filter/ignore/spy, cross-server audiences/duplicates, disconnect/reconnect/restart, missing/version mismatch, evidence retention/redaction, and Bedrock readability tests.

## 15. Static-analysis requirements
All configured checks in both repositories, CodeRabbit/Codacy where available, zero valid unresolved findings.

## 16. Documentation requirements
Provider versions/contracts, staff chat/PM evidence/privacy/retention/presence, configuration/troubleshooting, component metadata, package state/handoff, PR cross-links.

## 17. Security and privacy requirements
Least-privilege audiences; no private messages in logs/artifacts; capture only authorized bounded fields; fail closed on provider uncertainty.

## 18. Migration impact
Any required schema change is a new migration in the owning repository after live boundary verification; never alter deployed history.

## 19. Bedrock considerations
Readable text/command controls and identity-correct evidence; representative client acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Multiple Paper/Velocity processes, delivery ordering, duplicates, reconnect, server switching, provider restart, and stale presence.

## 21. External-provider considerations
This package is provider-owned: verify actual repo/API before work; if unresolved when dependencies complete, set `BLOCKED` with evidence.

## 22. Completion definition
Both exact-head PRs merge normally; all behavior/checks/docs pass; zero valid threads; deterministic aggregate-versus-standalone parity passes; temp branches cleaned.

## 23. Resume state
Unassigned; no branches/PRs/handoff. Repository unresolved; do not start before dependencies and assignment.

## 24. Last completed checkpoint
Definition/metadata only; no product or provider implementation began.

## 25. Remaining checklist
Repository resolution, implementation in both repos, tests/review/merge/parity/evidence all remain.

## 26. Known blockers
Actual supported RoseChat repository is unresolved. Unblock only by verifying the `wsg138` repository/default branch and accessible source/AGENTS.

## 27. Final evidence
Unset: repositories/bases/heads, two PRs/merges, contract version, tests/checks/reviews, manifests/hashes.

## 28. Merge and synchronization record
Unset. If one PR merges first, status becomes `SYNC_PENDING`; `COMPLETE` only after both merge, parity passes, metadata updates, and both temp branches are handled.

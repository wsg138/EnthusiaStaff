# `ES-V02` — Distributed and Java/Bedrock staging

## 1. Package identity
`ES-V02`; Private validation; primary `COMP-STAFF`; all applicable components; priority 250; not parallel-safe.

## 2. Status
Initial `DEFERRED`; registry is authoritative.

## 3. Objective
Run controlled multi-Velocity/HUB/SMP/provider/restart/switching acceptance with representative Java and Bedrock clients against the completed implementation set.

## 4. Why the package exists
The audit found no current-SHA staging proof for distributed topology, TLS/reconnect, staff/vanish/freeze, server switching, providers, or Bedrock usability.

## 5. Included audit IDs
`AUD-RUNTIME-004`, `AUD-RUNTIME-005`, `AUD-RUNTIME-006`, `AUD-ID-005`, `AUD-STAFF-005`, `AUD-VANISH-001`–`004`, `AUD-FREEZE-001`–`004`, acceptance portions of `AUD-INV-004`, `AUD-PUNISH-001`, `AUD-PUNISH-002`, `AUD-PUNISH-005`, `AUD-DISCORD-003`, `AUD-SEC-002`, `AUD-SEC-004`, `AUD-PERF-003`, `AUD-PERF-004`.

## 6. Included behavior
Pin every repository/JAR/config/provider/environment revision; exercise TLS/auth/replay/reconnect, duplicate delivery, multi-proxy/backend switching, restart/reload/shutdown, staff/vanish/freeze/report/inventory/punishment paths, provider presence/outage, Java and representative Bedrock identity/usability, authority fail-closed behavior; record sanitized evidence.

## 7. Explicit exclusions
Production authority/data/credentials/routes; issue #43 shadow/cutover; product fixes inside the validation campaign (create repair packages instead).

## 8. Dependencies
`ES-P06`, `ES-P09`, `ES-P11`, `ES-X01`, `ES-X03`, `ES-X04`, and `ES-X05` must be `COMPLETE`; applicable `ES-X02/P08` behavior must already be integrated where exercised.

## 9. Component and repository boundaries
Controlled private staging across pinned aggregate and standalone heads. Only sanitized evidence/state may be committed. No permanent component branches or isolated PRs.

## 10. Required branches
No branch for execution alone. If sanitized evidence/state is committed, temporary `package/es-v02-distributed-staging`; delete after merge.

## 11. Required PRs
No product PR. At most one EnthusiaStaff documentation/evidence PR for sanitized results; defects become separately assigned repair packages.

## 12. Implementation checklist
Reconcile/pin all heads and hashes; build isolated topology; configure non-production secrets/routes/data; define scenario matrix; execute normal/failure/restart/switch/Java/Bedrock/provider cases; preserve sanitized logs/results; classify defects vs environment; update handoff/registry; review any evidence PR; cleanup environment/branch.

## 13. Acceptance criteria
Every applicable workflow succeeds or has a precise reproducible defect; identities/audiences/authority remain correct across topology/restarts/switches; no duplicate/lost state; Java/Bedrock fallbacks usable; secrets/private data absent from evidence; no production effect.

## 14. Test requirements
Run each repo's exact-head suites first, then controlled runtime scenarios including process kill, reconnect, TLS failure, provider outage/version mismatch, server switch, rank change, login/rejoin, and Java/Bedrock interaction matrices.

## 15. Static-analysis requirements
Pinned implementation heads must already be green; evidence/tooling changes pass all configured checks and review.

## 16. Documentation requirements
Topology, exact versions/hashes, scenario/result matrix, failures, sanitized evidence locations, limitations, and repair-package routing.

## 17. Security and privacy requirements
Isolated non-production credentials/data; no raw players/IPs/PMs/routes/secrets in GitHub/ChatGPT; destroy temporary secrets/data after campaign.

## 18. Migration impact
Validation only; no Flyway edits/repair. Any discovered schema defect requires a new repair package/migration.

## 19. Bedrock considerations
Use representative Floodgate/Geyser clients for identity, commands, GUI fallback, messages, switching, freeze/vanish/staff/report/inventory/punishment behavior.

## 20. Distributed-runtime considerations
This package owns topology acceptance: multiple proxies/backends, ownership/fences, duplicate delivery, latency/reconnect, restart/shutdown, and provider coordination.

## 21. External-provider considerations
Use exact verified provider heads/contracts and test present/missing/incompatible/outage behavior without production routes.

## 22. Completion definition
All required scenarios at pinned heads produce reviewed sanitized evidence; all valid defects are routed; any evidence PR merges; no production authority changed.

## 23. Resume state
Deferred/unassigned; no branch/PR/handoff. Start only after dependencies, environment, and assignment.

## 24. Last completed checkpoint
Definition only; no staging began.

## 25. Remaining checklist
Complete dependencies; build isolated topology; run full matrix; review/sanitize evidence; route defects; close environment.

## 26. Known blockers
Controlled multi-process environment, representative Java/Bedrock clients, provider builds, and non-production secrets/routes.

## 27. Final evidence
Unset: pinned repository/JAR/config hashes, environment, scenario results, failures, privacy review, optional evidence PR.

## 28. Merge and synchronization record
Normally not applicable. Record optional evidence PR merge/containment/temp branch cleanup; confirm aggregate/standalone parity was already satisfied by implementation packages.

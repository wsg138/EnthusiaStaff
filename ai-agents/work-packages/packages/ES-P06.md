# `ES-P06` — Discord notification delivery completion

## 1. Package identity
`ES-P06`; Internal; primary `COMP-STAFF`; priority 60; conditionally parallel only with non-overlapping runtime state.

## 2. Status
Initial `PLANNED`; registry is authoritative.

## 3. Objective
Complete durable Discord notification rendering, delivery, retries, dead letters, reload, and duplicate-safe cross-server operation.

## 4. Why the package exists
The audit found durable producers/stores but weak worker/runtime proof and no accepted route-delivery behavior.

## 5. Included audit IDs
`AUD-REPORT-004`, `AUD-DISCORD-001`, `AUD-DISCORD-002`.

## 6. Included behavior
Verify all required event producers; complete bounded worker delivery, formatting, retries/backoff/circuit state, dead letters, duplicate prevention, reload and restart recovery; support isolated non-production route testing only.

## 7. Explicit exclusions
Production Discord routes; RoseChat PM capture; issue #43; provider unrelated events.

## 8. Dependencies
`ES-P05` must be `COMPLETE`.

## 9. Component and repository boundaries
Root Discord/outbox/report/runtime/tests/docs only. No external component import, permanent branch, or isolated PR.

## 10. Required branches
Temporary `package/es-p06-discord-delivery`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`.

## 12. Implementation checklist
Reconcile producers/store/worker; define event matrix; implement missing rendering/delivery/reload; inject failure/latency/restart; update docs/state/handoff; review/freeze/exact-head validate/merge/cleanup.

## 13. Acceptance criteria
Every included event is durable and idempotent; retries are bounded; dead letters are inspectable/recoverable; rate/circuit state survives correctly; reload is safe; absent/invalid routes fail explicitly without leaking data.

## 14. Test requirements
Worker unit/integration tests, MariaDB queue/dead-letter/restart/concurrency tests, duplicate and poison-event cases, configuration reload, redaction, and isolated fake endpoint tests.

## 15. Static-analysis requirements
Java 21 warnings-as-errors and configured analysis/review bots; zero valid unresolved findings.

## 16. Documentation requirements
Event matrix, route configuration, privacy, retry/dead-letter operations, reload/status, troubleshooting, Wiki, package state/handoff.

## 17. Security and privacy requirements
Environment-backed routes only; strict redaction/allowlisted fields; no production webhook contact; bounded body/logging.

## 18. Migration impact
No migration assumed; new post-V16 migration only if essential with upgrade/checksum tests.

## 19. Bedrock considerations
Notification identities/messages must remain correct/readable for Bedrock records; live staging belongs to `ES-V02`.

## 20. Distributed-runtime considerations
Multiple workers must claim safely, avoid duplicates, handle process death/reconnect, and preserve bounded throughput.

## 21. External-provider considerations
Discord endpoint is an external route, not a source-code component; test with isolated fakes and explicit missing-route behavior.

## 22. Completion definition
All event/delivery criteria and exact-head checks pass; one PR merges normally; zero valid threads; temporary branch cleanup verified.

## 23. Resume state
Unassigned; no branch/PR/handoff. Do not start until dependency completion and assignment.

## 24. Last completed checkpoint
Definition only; no product implementation began.

## 25. Remaining checklist
All implementation, tests, review, validation, merge, and evidence remain.

## 26. Known blockers
`ES-P05`; isolated route credentials/environment may be required for staging, but production routes are prohibited.

## 27. Final evidence
Unset: event matrix, exact heads, tests/checks, redaction review, route-test evidence.

## 28. Merge and synchronization record
Unset: feature head, normal merge, resulting main, containment, temporary branch deletion; parity not applicable.

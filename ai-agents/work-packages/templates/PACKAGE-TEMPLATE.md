# `<PACKAGE-ID>` — <title>

## 1. Package identity
Package ID, type, primary/other components, priority, parallel safety.

## 2. Status
Canonical registry status.

## 3. Objective
One bounded outcome.

## 4. Why the package exists
Audit/dependency rationale.

## 5. Included audit IDs
Exact IDs.

## 6. Included behavior
Complete behavior list.

## 7. Explicit exclusions
What must not be implemented.

## 8. Dependencies
Package IDs and readiness rule.

## 9. Component and repository boundaries
Allowed paths/repositories; no permanent component branches or isolated PRs.

## 10. Required branches
Temporary same-ID branch names only; delete after merge when safe.

## 11. Required PRs
Internal: normally one aggregate PR. External: normally one standalone plus one aggregate PR, cross-referenced. Validation/acceptance/audit: package-specific evidence PR rules.

## 12. Implementation checklist
Startup, implementation, tests, state/handoff, review, freeze, exact-head validation, merge, parity, cleanup.

## 13. Acceptance criteria
Behavior and evidence gates.

## 14. Test requirements
Repository and staging tests.

## 15. Static-analysis requirements
All changed repositories.

## 16. Documentation requirements
Directly affected docs/state/metadata.

## 17. Security and privacy requirements
No private or production data/secrets.

## 18. Migration impact
New migration only; immutable existing history.

## 19. Bedrock considerations
Identity and fallback.

## 20. Distributed-runtime considerations
Concurrency, switching, retries, recovery.

## 21. External-provider considerations
Verified contracts only.

## 22. Completion definition
Exact PR count, merges, review, evidence, and parity when external.

## 23. Resume state
Worker, branches, PRs, handoff, next action.

## 24. Last completed checkpoint
Latest coherent completed section.

## 25. Remaining checklist
Exact remaining work.

## 26. Known blockers
Evidence and unblock condition.

## 27. Final evidence
Exact heads, checks, review, artifacts, parity.

## 28. Merge and synchronization record
Merge commits, resulting heads, branch cleanup, and parity or non-applicability.

# `ES-X05` — Website UX, authentication, and appeals

## 1. Package identity
`ES-X05`; External/multi-repository; primary `COMP-SITE`; other `COMP-STAFF`; priority 35; conditional parallel safety after exact-sanction contract.

## 2. Status
Initial `PLANNED`; registry is authoritative.

## 3. Objective
Complete website authentication, punishment/appeal UX, staff review workflow, privacy, rate limiting, retries, and private-contract integration.

## 4. Why the package exists
User-facing appeal/auth/reviewer behavior is outside the core repo, while site-side privacy/concurrency/rate limiting remains incomplete.

## 5. Included audit IDs
`AUD-APPEAL-001`, `AUD-APPEAL-004`, site-side `AUD-WEB-003`, `AUD-WEB-004`.

## 6. Included behavior
Player authentication/ownership and submission; punishment/appeal pages; reviewer UX/authorization; privacy-safe notifications; explicit rate limits/body/session controls; retry/concurrency/stale decision handling; exact-sanction contract consumption; matching aggregate copy/parity.

## 7. Explicit exclusions
Core appeal mutation defect (`ES-P01`); production deployment/credentials/routes; unrelated website redesign.

## 8. Dependencies
`ES-P01` must be `COMPLETE`.

## 9. Component and repository boundaries
`wsg138/enthusia-site`, `components/enthusia-site/`, and directly necessary EnthusiaStaff private contracts/tests/state only. No permanent component branches or isolated PR.

## 10. Required branches
Temporary `package/es-x05-site-appeals` in both repos (or stricter compatible site convention); delete after verified merges.

## 11. Required PRs
Two same-ID cross-referenced PRs: standalone site and aggregate EnthusiaStaff. No third/isolated PR.

## 12. Implementation checklist
Reconcile both repos/AGENTS/heads/license/build; verify/import aggregate source; confirm exact-sanction contract; implement UX/auth/rate/privacy/retry; test both; update metadata/state/handoff; review/freeze/validate; merge both; parity compare; cleanup.

## 13. Acceptance criteria
Only authenticated owner may submit; reviewers are authorized; exact appealed sanction is displayed/acted upon; retries/stale decisions are duplicate-safe; rate/body/session limits explicit; private/public fields correct; both PRs merged and parity true.

## 14. Test requirements
Site and core suites plus authentication/session/ownership, CSRF/replay/private API, rate limiting, stale/concurrent decisions, visibility/privacy, retry/failure, accessibility/usability, and contract compatibility.

## 15. Static-analysis requirements
All configured checks/security/static analysis/review bots in both repos; zero valid unresolved findings.

## 16. Documentation requirements
User appeal flow, staff review, authentication/privacy/rate limits, deployment-neutral configuration, contract version, component metadata, package state/handoff, PR cross-links.

## 17. Security and privacy requirements
No production credentials/data; secure session/cookie/CSRF/replay boundaries; least-privilege reviewer access; no private punishment/evidence leakage.

## 18. Migration impact
New immutable migration only in owning repo if essential after boundary verification; clean/upgrade/rollback tests; no existing-history edits.

## 19. Bedrock considerations
Website login/ownership must work for Floodgate identities without exposing raw UUIDs or requiring Java-only assumptions.

## 20. Distributed-runtime considerations
Private API retries, stale sanctions, multiple reviewers, core restarts, timeout/ambiguous response, and duplicate submission must be safe.

## 21. External-provider considerations
The site is the external component; follow its own framework/AGENTS/security/CI and verified core contract only.

## 22. Completion definition
Both exact-head PRs merge normally; UX/security/checks/reviews pass; parity true; metadata/evidence recorded; temp branches handled.

## 23. Resume state
Unassigned; no branch/PR/handoff. Start only after `ES-P01` and assignment.

## 24. Last completed checkpoint
Definition/metadata only; no implementation began.

## 25. Remaining checklist
All two-repo implementation, tests, review, merge, parity, and evidence remain.

## 26. Known blockers
Dependency `ES-P01`; production environment/credentials intentionally excluded.

## 27. Final evidence
Unset: bases/heads/PRs/merges, security/UX tests, checks/reviews, parity manifests/hashes.

## 28. Merge and synchronization record
Unset. One-sided merge means `SYNC_PENDING`; completion requires both merges, parity, metadata, containment, and temp branch cleanup.

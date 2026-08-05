# `ES-V01` — Private LiteBans representative-data verification

## 1. Package identity
`ES-V01`; Private validation; primary `COMP-STAFF`; priority 200; conditional execution on a private local/Codex machine.

## 2. Status
Initial `DEFERRED`; registry is authoritative.

## 3. Objective
Verify dormant migration behavior against a representative private LiteBans database without exposing data.

## 4. Why the package exists
Synthetic tests are strong, but representative schema/data mappings, rejected/disappeared rows, and interrupted recovery remain unverified.

## 5. Included audit IDs
`AUD-MIG-001`, `AUD-MIG-003`, representative-data portions of `AUD-MIG-004`.

## 6. Included behavior
Private schema inspection; dry run and rerun; mappings/counts/checksums; rejected/disappeared rows; expiration/history/protected identity; interrupted-run/restart recovery; sanitized conclusions only.

## 7. Explicit exclusions
Uploading DB/rows/derived reconstructable data; production access; 168-hour shadow; cutover/authority; modifying migration history.

## 8. Dependencies
No implementation dependency, but execution requires an owner-provided private local copy and suitable local/Codex environment.

## 9. Component and repository boundaries
Private execution against pinned EnthusiaStaff code. Only sanitized orchestration/evidence docs may enter the repo; no component branch or isolated PR.

## 10. Required branches
No branch for private execution alone. If sanitized tracked evidence/state is committed, use temporary `package/es-v01-litebans-private-verification` and delete after merge.

## 11. Required PRs
No product PR required. At most one EnthusiaStaff documentation/evidence PR for sanitized non-reconstructable results.

## 12. Implementation checklist
Pin exact code/JAR/config/tool versions; keep DB offline/private; back up local copy; run read-only inspection/dry run/rerun/interruption/recovery; verify counts/checksums/mappings; sanitize evidence; update state/handoff; review any evidence PR; merge/cleanup if used.

## 13. Acceptance criteria
Representative cases are mapped or explicitly rejected with reason; rerun/idempotency/restart are proven; no source data changes or leaks; evidence cannot reconstruct rows; limitations recorded.

## 14. Test requirements
Run existing migration/integration/checksum suites at pinned head plus private representative scenarios and interruption/restart on disposable copies.

## 15. Static-analysis requirements
No product code expected. Any changed tooling/docs must pass configured checks and review with zero valid findings.

## 16. Documentation requirements
Sanitized environment/version/count summaries, scenario outcomes, blockers, and operator implications; no raw queries/results identifying players.

## 17. Security and privacy requirements
DB and all raw/derived rows remain local; no ChatGPT upload, GitHub, CI, screenshots, logs, or cloud artifact; remove temporary copies safely.

## 18. Migration impact
Validation only. Never edit V1–V16, repair Flyway, or write to production/source LiteBans DB.

## 19. Bedrock considerations
Verify representative Floodgate/protected-identity mappings only through sanitized aggregate conclusions.

## 20. Distributed-runtime considerations
Not a distributed acceptance package; interruption/restart of migration process is included, topology belongs to `ES-V02/A01`.

## 21. External-provider considerations
LiteBans is the source system; use verified schema inspection rather than assumptions and never ship private plugin/database artifacts.

## 22. Completion definition
All private scenarios conclusively run at a pinned revision, sanitized conclusions reviewed, no data leaked, and any optional evidence PR merged/cleaned.

## 23. Resume state
Deferred/unassigned; no branch/PR/handoff. Start only with explicit assignment and private environment.

## 24. Last completed checkpoint
Package definition only; no private DB accessed.

## 25. Remaining checklist
Obtain local copy/environment; execute all scenarios; sanitize/review evidence; update registry/handoff.

## 26. Known blockers
Representative private database and local Codex/private-machine availability.

## 27. Final evidence
Unset: pinned heads/hashes, environment versions, sanitized counts/outcomes, test commands, privacy review.

## 28. Merge and synchronization record
Usually not applicable. If an evidence PR is used, record its head/merge/main containment/temp branch cleanup; no external parity.

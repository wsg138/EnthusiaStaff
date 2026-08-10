# `ES-V01` — Private LiteBans representative-data verification

## 1. Package identity
`ES-V01`; Private validation; primary `COMP-STAFF`; priority 200; conditional execution on a private local/Codex machine.

## 2. Status
`PARTIAL` / `ACTIONABLE_CONTINUATION`; registry is authoritative. Product branch: `package/es-v01-litebans-private-verification`. Product head: `ea07f55a`, reproduced from local repair `22934e33` on starting `main` `b78a62de3876bfde7fa5f57860fedc1415ef3c53`.

## 3. Objective
Verify dormant migration behavior against a representative private LiteBans database without exposing data.

## 4. Why the package exists
Synthetic tests are strong, but representative data mappings, rejected/disappeared rows, and interrupted recovery remain unverified.

## 5. Included audit IDs
`AUD-MIG-003` and representative-data portions of `AUD-MIG-004`.

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
Private execution and repair reproduction complete; hosted exact-head validation, review, PR merge, containment, and cleanup remain.

## 24. Last completed checkpoint
At `ea07f55a`, the private representative source was exercised only through disposable local databases. The repair accepts UUID-only sanction rows when modern LiteBans omits name columns, while malformed source/history rows retain rejection behavior. Dry-run, import, replay, and abandoned-`RUNNING` recovery passed. No private rows, addresses, credentials, or reconstructable artifacts were tracked.

## 25. Remaining checklist
Complete exact-head hosted validation and review; publish a draft PR; resolve every valid finding; merge normally only after the package acceptance criteria and privacy review are satisfied. Keep the seven rejected rows as an explicit pre-rehearsal data-policy decision.

## 26. Known blockers
No private-environment blocker remains. The remaining gates are hosted exact-head validation, review, PR merge, containment, and temporary-branch cleanup. Production shadow/cutover and the 168-hour authority window remain excluded.

## 27. Final evidence
Sanitized private evidence: MariaDB 10.11.6 with `litebans_` tables; 102 bans, 53 mutes, and 1,747 history rows examined. At the reproduced repair head, 153 supported sanctions imported, then replayed without duplicate cases/events; issue/expiry values matched for mapped sanctions. A local abandoned `RUNNING` migration was recovered as failed before safe replay. Seven rows remained explicit rejections: 2 `INVALID_SOURCE_ROW` and 5 `INVALID_HISTORY_ROW`. Warnings and kicks remain audit-only/unsupported. Full hosted/review evidence is pending and must not be inferred from this local result.

## 28. Merge and synchronization record
Pending. Canonical handoff: [`2026-08-09-es-v01-private-litebans-representative-verification.md`](../../reports/package-handoffs/2026-08-09-es-v01-private-litebans-representative-verification.md).

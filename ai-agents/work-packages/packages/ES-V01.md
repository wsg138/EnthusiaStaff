# `ES-V01` — Private LiteBans representative-data verification

## 1. Package identity
`ES-V01`; Private validation; primary `COMP-STAFF`; priority 200; conditional execution on a private local/Codex machine.

## 2. Status
`PARTIAL` / `ACTIONABLE_CONTINUATION`; registry is authoritative. Active PR: `#110` on `package/es-v01-litebans-private-verification`. Product repair head: `ea07f55a`, reproduced from local repair `22934e33` on starting `main` `b78a62de3876bfde7fa5f57860fedc1415ef3c53`. Pre-review PR head: `2485c8b7a4a80ae306216eb9f66f1e9415d9eac0`; valid review fixes intentionally advance the live PR head, which must then be frozen and revalidated.

## 3. Objective
Verify dormant migration behavior against a representative private LiteBans database without exposing data, including the minimal repository compatibility repair required when representative schema verification exposes a product defect.

## 4. Why the package exists
Synthetic tests are strong, but representative data mappings, rejected/disappeared rows, and interrupted recovery remain unverified until representative execution. If that execution exposes a repository defect that directly prevents the package from validating the real supported schema, the narrow repair and regression coverage remain part of this same package.

## 5. Included audit IDs
`AUD-MIG-003` and representative-data portions of `AUD-MIG-004`.

## 6. Included behavior
Private schema inspection; dry run and rerun; mappings/counts/checksums; rejected/disappeared rows; expiration/history/protected identity; interrupted-run/restart recovery; sanitized conclusions only; and a narrowly scoped repository compatibility repair plus regression coverage when directly required by representative validation.

## 7. Explicit exclusions
Uploading DB/rows/derived reconstructable data; unrelated product work; production access; 168-hour shadow; cutover/authority; modifying migration history.

## 8. Dependencies
No implementation dependency. Private representative execution required an owner-provided local copy and suitable local/Codex environment; that execution is complete and the private database remains local.

## 9. Component and repository boundaries
Private execution stays local against pinned EnthusiaStaff code. No private rows, credentials, database artifacts, screenshots, reconstructable output, or source dump may enter GitHub or chat. A repository-side compatibility defect discovered by that representative execution may be repaired only when it is narrowly necessary to make the supported migration path valid; such a repair and its synthetic tests use this package's single temporary branch/PR. No unrelated provider, cutover, production-authority, or migration-history work belongs here.

## 10. Required branches
Private execution alone requires no branch. The current tracked repair/evidence/state work uses temporary branch `package/es-v01-litebans-private-verification`, which must be deleted after a normal merge only when containment proves no unique work remains.

## 11. Required PRs
PR `#110` is the single ES-V01 package PR. It legitimately contains the minimal UUID-only LiteBans compatibility repair, synthetic regression coverage, and sanitized package evidence/state. Do not create a second product or evidence PR before this PR completes. A later documentation-only terminal publication PR is permitted only if canonical state cannot truthfully be marked `COMPLETE` until after PR `#110` merges.

## 12. Implementation checklist
Pin exact code/JAR/config/tool versions; keep DB offline/private; run read-only inspection/dry run/rerun/interruption/recovery on disposable copies; verify counts/checksums/mappings; preserve explicit rejected-row outcomes; apply only the minimal repository compatibility repair discovered by representative execution; add synthetic regression coverage; sanitize evidence; run exact-head hosted/static/canonical staging gates; complete substantive review with zero valid unresolved findings; merge normally; prove containment; clean temporary branches; publish terminal state.

## 13. Acceptance criteria
Representative cases are mapped or explicitly rejected with reason; rerun/idempotency/restart are proven; the discovered supported-schema compatibility defect has regression coverage; no source data changes or leaks; evidence cannot reconstruct rows; limitations recorded; final merge head passed all applicable exact-head gates and substantive review.

## 14. Test requirements
Run existing migration/integration/checksum suites at pinned head plus private representative scenarios and interruption/restart on disposable copies. Hosted Java 21/Testcontainers is authoritative when local Docker cannot initialize. UUID-only regression coverage must exercise both ban and mute sanction tables while retaining the IP-ban path.

## 15. Static-analysis requirements
Any changed product code, tests, tooling, or docs must pass configured exact-head static checks and substantive review with zero valid unresolved findings.

## 16. Documentation requirements
Sanitized environment/version/count summaries, scenario outcomes, blockers, operator implications, exact PR/gate/merge/containment state, and routing; no raw queries/results identifying players.

## 17. Security and privacy requirements
DB and all raw/derived rows remain local; no ChatGPT upload, GitHub, CI, screenshots, logs, or cloud artifact; remove temporary copies safely.

## 18. Migration impact
No Flyway migration is changed by ES-V01. V18 remains current and immutable. Never rewrite migration history, repair Flyway to accommodate this package, or write to production/source LiteBans DB.

## 19. Bedrock considerations
Verify representative Floodgate/protected-identity mappings only through sanitized aggregate conclusions.

## 20. Distributed-runtime considerations
Not a distributed acceptance package; interruption/restart of migration process is included, topology belongs to `ES-V02/A01`.

## 21. External-provider considerations
LiteBans is the source system; use verified schema inspection rather than assumptions and never ship private plugin/database artifacts.

## 22. Completion definition
All private scenarios conclusively ran at a pinned revision; the discovered UUID-only compatibility defect is repaired with synthetic regression coverage; the final exact PR head passes applicable hosted/static/canonical staging and substantive review gates; sanitized conclusions are reviewed; no data leaked; PR `#110` is normally merged; containment and temporary-branch cleanup are proven; and canonical package/routing state is terminally published.

## 23. Resume state
Private execution and repair reproduction are complete. PR `#110` reached pre-review head `2485c8b7a4a80ae306216eb9f66f1e9415d9eac0`, passed pre-review hosted gates, then received valid review findings. Apply those fixes, freeze the new exact head, rerun invalidated gates, resolve review, merge, prove containment/cleanup, and publish terminal state.

## 24. Last completed checkpoint
At `ea07f55a`, the private representative source was exercised only through disposable local databases. The repair accepts UUID-only sanction rows when modern LiteBans omits name columns, while malformed source/history rows retain rejection behavior. Dry-run, import, replay, and abandoned-`RUNNING` recovery passed. No private rows, addresses, credentials, or reconstructable artifacts were tracked. At later pre-review head `2485c8b7a4a80ae306216eb9f66f1e9415d9eac0`, hosted Java 21/full tests, MariaDB/Testcontainers, Coverage, Codacy, and canonical Pi Staging passed before substantive review identified the valid follow-up fixes now being applied.

## 25. Remaining checklist
Continue existing PR `#110`; freeze the post-review-fix exact head; rerun every hosted/static/canonical staging gate invalidated by the head change; inspect and resolve every valid review finding; require zero valid unresolved threads; merge normally only if the frozen head stays unchanged and all package criteria pass; prove containment; delete temporary branches only when safe; publish ES-V01 `COMPLETE`. Keep the seven rejected rows as an explicit later data-policy decision.

## 26. Known blockers
No private-environment blocker remains. Current blockers are only the required post-review-fix exact-head reruns, substantive review closure, normal PR merge, containment, cleanup, and terminal publication. Production shadow/cutover and the 168-hour authority window remain excluded.

## 27. Final evidence
Sanitized private evidence: MariaDB 10.11.6 with `litebans_` tables; 102 bans, 53 mutes, and 1,747 history rows examined. At the reproduced repair head, 153 supported sanctions imported, then replayed without duplicate cases/events; issue/expiry values matched for mapped sanctions. A local abandoned `RUNNING` migration was recovered as failed before safe replay. Seven rows remained explicit rejections: 2 `INVALID_SOURCE_ROW` and 5 `INVALID_HISTORY_ROW`. Warnings and kicks remain audit-only/unsupported. The pre-review PR head passed hosted Java 21/full tests, MariaDB/Testcontainers, Coverage, Codacy, and canonical Pi Staging, but those results may not be used to merge a changed post-review-fix head; final hosted/review evidence remains pending until the new exact head completes its gates.

## 28. Merge and synchronization record
Pending. Active PR: `#110`; temporary branch: `package/es-v01-litebans-private-verification`; pre-review head: `2485c8b7a4a80ae306216eb9f66f1e9415d9eac0`. Canonical handoff: [`2026-08-09-es-v01-private-litebans-representative-verification.md`](../../reports/package-handoffs/2026-08-09-es-v01-private-litebans-representative-verification.md).

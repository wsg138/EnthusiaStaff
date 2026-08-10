# `ES-V01` — Private LiteBans representative-data verification

## 1. Package identity
`ES-V01`; Private validation; primary `COMP-STAFF`; priority 200; conditional execution on a private local/Codex machine.

## 2. Status
`COMPLETE`. Final frozen PR head: `de39e30232df9bd44d4b4df54a8922e815bada76`. PR `#110` merged normally as `9a6c7240a4f6fffd216af0239709867b79080ddc`. The package branch was safely auto-deleted after containment.

## 3. Objective
Verify dormant migration behavior against a representative private LiteBans database without exposing data, including the minimal repository compatibility repair required when representative schema verification exposes a product defect.

## 4. Why the package exists
Synthetic tests are strong, but representative data mappings, rejected/disappeared rows, and interrupted recovery required representative execution. That execution exposed a supported-schema compatibility defect, so the narrow repair and regression coverage legitimately remained in this package.

## 5. Included audit IDs
`AUD-MIG-003` and representative-data portions of `AUD-MIG-004`.

## 6. Included behavior
Private schema inspection; dry run and rerun; mappings/counts/checksums; rejected/disappeared rows; expiration/history/protected identity; interrupted-run/restart recovery; sanitized conclusions only; and the narrowly scoped UUID-only repository compatibility repair plus regression coverage.

## 7. Explicit exclusions
Uploading DB/rows/derived reconstructable data; unrelated product work; production access; 168-hour shadow; cutover/authority; modifying migration history.

## 8. Dependencies
No implementation dependency. The owner-provided private representative execution completed on a local/Codex environment and the private database remained local.

## 9. Component and repository boundaries
Private execution stayed local against pinned EnthusiaStaff code. No private rows, credentials, database artifacts, screenshots, reconstructable output, or source dump entered GitHub or chat. The repository-side UUID-only compatibility defect was repaired only because it directly prevented the supported migration path from accepting representative UUID-backed sanctions. No unrelated provider, cutover, production-authority, or migration-history work was included.

## 10. Required branches
The product/evidence branch was `package/es-v01-litebans-private-verification`. After PR #110 merged and containment was proven, GitHub auto-deleted it. The later terminal-state publication is documentation-only and does not reopen product work.

## 11. Required PRs
PR `#110` was the single product/evidence PR and merged normally. A documentation-only terminal publication PR is permitted by the package because canonical state could not truthfully be marked `COMPLETE` until after PR #110 merged, containment was proven, and branch cleanup was observed.

## 12. Implementation checklist
Complete: private representative inspection/dry run/rerun/interruption/recovery; explicit rejected-row preservation; minimal UUID-only repair; UUID-backed ban/mute plus IP-only synthetic regression coverage; sanitized evidence; exact-head hosted Java 21/full tests; MariaDB/Testcontainers; Codacy; canonical Pi staging; substantive review closure; normal merge; containment; package-branch cleanup; terminal publication.

## 13. Acceptance criteria
Satisfied for ES-V01. Representative supported sanctions mapped successfully, explicit rejected rows retained their reasons, rerun/idempotency/recovery were proven, the supported-schema compatibility defect has regression coverage, no private data leaked, the final exact PR head passed applicable hosted/static/canonical staging and substantive review gates, and the normal merge is contained.

## 14. Test requirements
Satisfied. Hosted Java 21/full tests on final head `de39e30232df9bd44d4b4df54a8922e815bada76` passed in Coverage run `31353964138` / job `93349968412`, including MariaDB/Testcontainers. The integration fixture exercises UUID-backed bans and mutes while retaining the IP-only ban path. Local Docker initialization failure remains an environment limitation, not a product failure.

## 15. Static-analysis requirements
Satisfied. Codacy static check `93347267178` succeeded on the final head. Codacy diff coverage `93350870761` succeeded at 100.0%, and coverage variation `93350870850` succeeded at +0.01%.

## 16. Documentation requirements
Satisfied by sanitized package evidence, routing state, and terminal handoff. No raw private query/result data or player-identifying rows are published.

## 17. Security and privacy requirements
Satisfied. The private DB and raw/derived rows remained local. No source dump, credentials, private database artifact, reconstructable row data, screenshot, or private query output entered GitHub or chat.

## 18. Migration impact
No Flyway migration changed. V18 remains current and immutable. ES-V01 did not repair/rewrite migration history or write to a production/source LiteBans database.

## 19. Bedrock considerations
Representative protected-identity behavior was verified only through sanitized conclusions. No private Floodgate/player rows were published.

## 20. Distributed-runtime considerations
Not a distributed acceptance package. Migration interruption/restart was covered privately; broader topology remains owned by `ES-V02/A01`.

## 21. External-provider considerations
LiteBans remains the authoritative source system. ES-V01 verified actual supported schema behavior without shipping private plugin/database artifacts or changing production authority.

## 22. Completion definition
Met. Private scenarios ran at pinned revisions; the UUID-only defect is repaired with synthetic regression coverage; the final exact PR head passed hosted/static/canonical staging and substantive review closure; no data leaked; PR #110 merged normally; containment and branch cleanup are proven; and canonical package/routing state is terminally published.

## 23. Resume state
Terminal. Do not resume ES-V01 unless live GitHub shows a concrete regression in its terminal publication. A new sequential worker should reconcile live state and select the highest-priority READY package rather than continuing ES-V01.

## 24. Last completed checkpoint
Final product head `de39e30232df9bd44d4b4df54a8922e815bada76` passed exact hosted validation and canonical staging after substantive review fixes. PR #110 then merged normally as `9a6c7240a4f6fffd216af0239709867b79080ddc`; the merge contains the frozen feature head as its second parent with no unique tree delta, and GitHub auto-deleted the product branch.

## 25. Remaining checklist
None inside ES-V01. The seven rejected rows remain a separate later data-policy decision. Production shadow/cutover, issue #43 acceptance, and authority changes are explicitly outside this package.

## 26. Known blockers
None for ES-V01 completion. External future work remains intentionally blocked/deferred where its own dependencies or owner authorization are missing.

## 27. Final evidence
Private representative evidence, sanitized only:

- source: MariaDB 10.11.6 LiteBans with `litebans_` prefix;
- aggregate source counts: 102 bans, 53 mutes, 1,747 history rows;
- 153 supported sanctions imported successfully and all 153 replayed without duplicate cases/events;
- zero issue/expiry mapping mismatches;
- abandoned `RUNNING` recovery passed;
- 2 `INVALID_SOURCE_ROW` and 5 `INVALID_HISTORY_ROW` remain explicit later-policy rejections;
- 49 warnings and 44 kicks are intentionally audit-only/unsupported;
- 322 invalid historical usernames were ignored while usable UUID/network information remained.

Repair/review evidence:

- local discovery repair `22934e33`, reproduced on the canonical branch as `ea07f55a`;
- pre-review head `2485c8b7a4a80ae306216eb9f66f1e9415d9eac0` passed the first hosted gate set;
- substantive CodeRabbit review found three valid routing/scope documentation findings and a missing UUID-backed ban integration fixture;
- final fix/head `de39e30232df9bd44d4b4df54a8922e815bada76` addressed all of them;
- all three substantive review threads are resolved/outdated and marked addressed; zero valid unresolved threads remain;
- the later incremental CodeRabbit re-review was rate-limited and is not claimed as a second full review; exact-head CodeRabbit status was success.

Final hosted evidence on `de39e30232df9bd44d4b4df54a8922e815bada76`:

- Coverage run `31353964138`, job `93349968412`: success, Java 21/full tests/MariaDB-Testcontainers;
- Codacy static `93347267178`: success; diff coverage `93350870761`: 100.0%; variation `93350870850`: +0.01%;
- canonical public Pi run `31353964382`: build `93349969346` success, bridge `93350945971` success, fork-boundary helper `93349969918` skipped/not applicable;
- private run `31354311211`, job `93350973876`: success on trusted `Lincoln-PI-4`, staging controls `991316917be5116546a3ceab101d0ad9e6b1dca3`;
- exact artifact/provenance/freshness passed; runtime `EnthusiaStaff-Paper-0.1.0-SNAPSHOT.jar`, SHA-256 `65779e5decfa751e3140b972185cc60d1d6a14a4b185c7cc00533a2e1d11b024`;
- guarded disposable DB before-reset passed; first Paper cycle applied V1–V18 and reached `SHADOW_MIGRATION`; second cycle verified schema v18 current/no-op and reached `SHADOW_MIGRATION`; both clean shutdowns and failure scans passed; after-reset removed 69 objects and passed;
- sanitized Pi artifact `9050381344`, digest `sha256:34f77c0fe32fee5c79872daf9487371b17404f3308c4212b736b6f011a194bd0`.

Historical final-head staging failures remain explicit rather than relabeled: private run `31353309582` failed before any completed Paper/storage-ready cycle during shared-host resource contention; private run `31353848239` was rejected by the attempt-bound provenance guard before Paper because the rerun request did not match the manifest attempt. Neither is passing evidence.

## 28. Merge and synchronization record
PR `#110` merged normally as `9a6c7240a4f6fffd216af0239709867b79080ddc` from frozen head `de39e30232df9bd44d4b4df54a8922e815bada76`. The merge commit has the frozen head as its second parent and no unique feature-tree delta remains. GitHub auto-deleted `package/es-v01-litebans-private-verification`. Canonical terminal handoff: [`2026-08-10-es-v01-private-litebans-representative-verification-complete.md`](../../reports/package-handoffs/2026-08-10-es-v01-private-litebans-representative-verification-complete.md).

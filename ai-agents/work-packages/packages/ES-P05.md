# `ES-P05` — Report evidence and staff workflow completion

## 1. Package identity
`ES-P05`; Internal; primary `COMP-STAFF`; priority 50; conditionally parallel only with proven non-overlap.

## 2. Status
`COMPLETE`. Frozen exact implementation head `9e6d5f8afc120b76f5f396a2e3e279bc5f851c85` passed all applicable exact-head hosted, static-analysis, review and canonical Pi gates, then PR #81 merged normally as `52c0dc47efdc2296827b4b6b743d01a86f72c856`.

## 3. Objective
Complete provider-independent report submission, evidence, queue/detail, notes/status, stale-state, privacy, and staff workflow behavior.

## 4. Why the package exists
Report command/GUI runtime paths and evidence handling required one bounded package before Discord delivery and RoseChat PM integration.

## 5. Included audit IDs
`AUD-REPORT-001`, `AUD-REPORT-002`, provider-independent portions of `AUD-REPORT-003`.

## 6. Included behavior
Target resolution; cooldown/merge/duplicate prevention foundations; queue/detail GUI and command fallback; notes/status revisions and stale-state; retained chat/coordinate/client evidence presentation; privacy/retention boundaries; explicit attachment exclusion decision; restart durability proof.

## 7. Explicit exclusions
RoseChat private-message evidence (`ES-X01`); Discord route delivery (`ES-P06`); production evidence/routes; representative distributed/Bedrock acceptance (`ES-V02`).

## 8. Dependencies
`ES-P03` and `ES-P04` are `COMPLETE`.

## 9. Component and repository boundaries
Root report/domain/persistence/Paper/tests/docs only. No RoseChat source import, permanent component branch, or isolated PR.

## 10. Required branches
Implementation branch was `package/es-p05-report-workflow`. GitHub auto-deleted it only after verified normal merge containment. Terminal-record branch is documentation-only and is not a second implementation package.

## 11. Required PRs
Implementation PR #81 to `wsg138/EnthusiaStaff:main`, merged normally. A documentation-only terminal publication PR records canonical completion state.

## 12. Implementation checklist
- [x] Resume existing ES-P05 through canonical routing instead of creating competing implementation work.
- [x] Preserve the repaired ReportStore integration fixture and current-main product state.
- [x] Preserve provider-independent review/privacy implementation, dedicated evidence permission, bounded/scalar-only rendering, delivery-time authorization and restart durability proof.
- [x] Synchronize legitimate `main` through a normal two-parent merge without rebase, squash, force-push or scope expansion.
- [x] Harshly re-review the final live eight-file ES-P05 diff and preserve unrelated newer `main` work.
- [x] Freeze exact implementation candidate `9e6d5f8afc120b76f5f396a2e3e279bc5f851c85`.
- [x] Pass fresh exact-head Wiki, Java 21 full build/tests, MariaDB/Testcontainers, migration integrity, coverage, runtime-JAR and Codacy gates.
- [x] Preserve CodeRabbit success and zero valid unresolved review threads.
- [x] Pass canonical public→private Pi provenance, guarded disposable DB/Paper/restart/persistence/cleanup acceptance on the exact frozen head.
- [x] Merge normally, verify containment, verify safe implementation-branch cleanup and publish terminal `COMPLETE` state.

## 13. Acceptance criteria
Met. Provider-independent report submission and staff review are usable through GUI and text fallback; state/note revisions are exact and stale-safe; retained evidence presentation is bounded and privacy-gated; delivery-time sensitive authorization is rechecked; restart durability is tested; attachment behavior is explicitly excluded from this package; all required exact-head validation and staging proof succeeded.

## 14. Test requirements
Frozen head `9e6d5f8afc120b76f5f396a2e3e279bc5f851c85` passed Coverage run `31348316817` / job `93334330436`, including Java 21 full build/tests, MariaDB/Testcontainers, aggregate JaCoCo generation, runtime-JAR inspection, validation-artifact upload and Codacy upload. Hosted artifact `9048186426` has digest `sha256:62c9ad1cad86fa2e5189445a9a9ff5f5b6761f1b19d4c25ebacbea5860d4fff9`.

## 15. Static-analysis and review requirements
Codacy static `93334591193` succeeded with zero annotations. Codacy diff coverage `93335202690` succeeded at 42.96% with no configured diff-coverage gate. Coverage variation `93335202668` succeeded at +0.03% against the -1.0% target. CodeRabbit exact-head status succeeded; all three historical substantive review threads are resolved and valid unresolved thread count is zero.

## 16. Documentation requirements
`docs/wiki/pages/Reports-and-Evidence.md` documents report commands, permissions, workflow, evidence/privacy/retention, fallbacks, troubleshooting and the attachment decision. Canonical registry/workspace/handoff records publish terminal evidence and routing.

## 17. Security and privacy requirements
Least-privilege `enthusiastaff.reports.evidence` remains separate from broad report management. Sensitive authorization is rechecked immediately before scheduled delivery. Broad helper GUI/text triage does not reveal exact coordinates or retained evidence contents. Chat identity/timestamp/body rendering is bounded; client rendering accepts only allow-listed scalar values; unexpected structured values and opaque Polar metadata are withheld; malformed storage fails closed.

## 18. Migration impact
No ES-P05 migration. V18 is current and immutable; V1–V18 were not rewritten or repaired.

## 19. Bedrock considerations
Text command fallback remains complete/readable and does not require Java-only inventory interaction. Representative live Bedrock acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Existing durable duplicate prevention/query/state ownership, optimistic revisions, row locks, idempotency and transactional persistence remain unchanged. `ReportRestartIntegrationTest` proves report evidence/state/revision/assignment survive repeated MariaDB runtime initialization. Full representative multi-backend acceptance remains `ES-V02`.

## 21. External-provider considerations
RoseChat-specific PM capture remains excluded for `ES-X01`; no provider-specific source dependency was introduced. Discord route delivery remains `ES-P06`.

## 22. Completion definition
Met. The frozen exact implementation head passed hosted validation, static analysis, review and mandatory canonical Pi runtime acceptance; PR #81 then merged normally; containment and safe branch cleanup were verified; terminal package state is published without starting another package.

## 23. Worker resume and synchronization record
This automatic worker started with live `main` `7d438988e2ca6a681b5fba82f25377fc9de8df84` and existing PR #81 at historical hosted-green head `346e764f40b25c98e7d24ce7f863e5629773e814`. The previous transient-release freshness blocker was materially changed by the repaired canonical bridge and later successful exact current-main staging proof. The worker preserved the existing branch, synchronized current `main` through two-parent merge commit `9e6d5f8afc120b76f5f396a2e3e279bc5f851c85`, and froze that SHA for final validation.

## 24. Historical blocker record
The earlier exact candidate `346e764f40b25c98e7d24ce7f863e5629773e814` was hosted-green but private run `31331175023` / job `93289556545` failed during exact public bridge artifact freshness verification with `release created_at is expired for the staging bridge`; guarded DB/Paper/restart execution was skipped. That failure remains historical evidence and was never represented as a pass. The worker did not weaken freshness/provenance or bypass the canonical bridge.

An older candidate `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2` had successful runtime proof, but it was not substituted for the final-head gate.

## 25. Final hosted evidence
- Exact head: `9e6d5f8afc120b76f5f396a2e3e279bc5f851c85`.
- Validate Wiki `31348316809`: success.
- Coverage `31348316817` / job `93334330436`: success.
- Hosted validation artifact `9048186426`, digest `sha256:62c9ad1cad86fa2e5189445a9a9ff5f5b6761f1b19d4c25ebacbea5860d4fff9`.
- Codacy static `93334591193`: success, zero annotations.
- Codacy diff coverage `93335202690`: 42.96%, success; no configured gate.
- Codacy coverage variation `93335202668`: +0.03%, success.
- CodeRabbit exact-head status: success; zero valid unresolved review threads.

## 26. Final canonical Pi evidence
- Public Pi run `31348316060`.
- Public build job `93334328455`: success; exact source validation, Java 21 build/package and verified runtime artifact upload passed.
- Bridge job `93335216891`: success; exact artifact download, bounded transient release publication, private dispatch/correlation, private verdict, transient public transfer removal and final staging result all passed.
- Private run `31348651990`, job `93335243975`: success on trusted `Lincoln-PI-4`, runner ID `2`.
- Private provenance/freshness verification: success.
- Guarded disposable Paper boot/restart: success.
- Sanitized evidence artifact `9048272564`, digest `sha256:55f61484144ef1911e81678ba26d6ca910d915375f0a0425f9d707a4d8904234`.
- Sanitized summary: `result=PASS`; Paper `1.21.11` build `132`; two server starts; 2/2 storage-ready cycles; required mode `SHADOW_MIGRATION`; failure count zero.
- First boot applied V1–V18 to the empty disposable schema and reached v18; second boot saw schema v18 already current. Both storage checks and status/verify mode checks passed; both shutdowns exited 0 with stopping/save markers; both failure scans were clean; guarded pre/post database reset passed.
- Sentinel: non-applicable; `.enthusia-test.yml` is absent.

## 27. Merge, containment and cleanup
PR #81 merged normally as `52c0dc47efdc2296827b4b6b743d01a86f72c856` with parents starting `main` `7d438988e2ca6a681b5fba82f25377fc9de8df84` and frozen feature head `9e6d5f8afc120b76f5f396a2e3e279bc5f851c85`. Compare from feature head to merge commit is `ahead_by=1`, `behind_by=0` with no file delta, proving complete containment and no unique implementation work. GitHub auto-deleted `package/es-p05-report-workflow`. No external repository parity applies.

## 28. Production and next-package boundary
Issue #43 remains open/deferred; LiteBans remains authoritative. No production data, credentials, infrastructure authority, deployment, shadow window, cutover, or migration was changed. ES-P05 completion makes ES-P06 and ES-X01 dependency-ready, while ES-P07 remains lower-priority-number READY and is the next normal sequential selection. This worker does not activate or implement any of them.

## 29. Canonical handoff
[`2026-08-09-es-p05-report-workflow-complete.md`](../../reports/package-handoffs/2026-08-09-es-p05-report-workflow-complete.md)
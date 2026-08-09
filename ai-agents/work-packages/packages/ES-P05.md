# `ES-P05` — Report evidence and staff workflow completion

## 1. Package identity
`ES-P05`; Internal; primary `COMP-STAFF`; priority 50; conditionally parallel only with proven non-overlap.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED` on PR #81. The synchronized exact implementation head is fully hosted-green, but mandatory canonical Pi staging failed at private transient-artifact freshness/provenance verification before database or Paper runtime execution.

## 3. Objective
Complete provider-independent report submission, evidence, queue/detail, notes/status, stale-state, privacy, and staff workflow behavior.

## 4. Why the package exists
Report command/GUI runtime paths and evidence handling need one bounded package before Discord delivery and RoseChat PM integration.

## 5. Included audit IDs
`AUD-REPORT-001`, `AUD-REPORT-002`, provider-independent portions of `AUD-REPORT-003`.

## 6. Included behavior
Prove target resolution, cooldown/merge/duplicate prevention; queue/detail GUI and command fallback; notes/status revisions/stale-state; chat/coordinate/client evidence; privacy/retention boundaries; attachment behavior must be explicitly implemented or excluded by documented decision.

## 7. Explicit exclusions
RoseChat private-message evidence (`ES-X01`); Discord route delivery (`ES-P06`); production evidence/routes.

## 8. Dependencies
`ES-P03` and `ES-P04` are `COMPLETE`.

## 9. Component and repository boundaries
Root report/domain/persistence/Paper/tests/docs only. No RoseChat source import, permanent component branch, or isolated PR.

## 10. Required branches
Temporary implementation branch `package/es-p05-report-workflow`; preserve while blocked. Delete only after verified normal merge containment.

## 11. Required PRs
PR #81 to `wsg138/EnthusiaStaff:main`; open and intentionally unmerged while canonical staging is incomplete.

## 12. Implementation checklist
- [x] Reconcile live GitHub and resume ES-P05 through canonical routing rather than create a competing implementation PR.
- [x] Identify and repair the stale ReportStore integration fixture clock without weakening assertions or production retention behavior.
- [x] Preserve provider-independent review/privacy implementation, dedicated evidence permission, bounded/scalar-only rendering, delivery-time authorization and restart durability proof.
- [x] Verify the previously queued exact-head Pi run completed successfully and record its real runtime evidence.
- [x] Merge current legitimate `main` into the existing implementation branch through a normal two-parent merge; no rebase/force/squash.
- [x] Re-review the synchronized nine-file product diff and preserve newer unrelated `main` work.
- [x] Resolve fresh Codacy maintainability findings with bounded static-only refactors.
- [x] Freeze final candidate `346e764f40b25c98e7d24ce7f863e5629773e814`.
- [x] Pass fresh exact-head Wiki, Java 21 full build/tests, ReportStore integration, MariaDB/Testcontainers, migration, coverage, runtime-JAR and Codacy gates.
- [x] Preserve zero valid unresolved review threads.
- [ ] Obtain successful canonical Pi provenance + guarded DB/Paper/restart/persistence/cleanup acceptance on the exact final candidate.
- [ ] Merge normally, verify containment, finalize `COMPLETE`, delete the implementation branch, and stop.

## 13. Acceptance criteria
Provider-independent product behavior and hosted proof are complete: submission/staff review are usable through GUI and text fallback; durable duplicate/cooldown/merge foundations remain; state/note revisions are exact and stale-safe; evidence is bounded, attributable, retained/purged by current policy, and privacy-gated for staff presentation. Final package completion is withheld because exact-head canonical staging did not reach runtime acceptance.

## 14. Test requirements
Final exact head `346e764f40b25c98e7d24ce7f863e5629773e814` passes Coverage run `31330790942` / job `93288609588`, including the full Java 21 build/tests, both formerly failing ReportStore integration methods, MariaDB/Testcontainers, migration validation, aggregate coverage, runtime-JAR inspection, validation artifact upload and Codacy coverage upload. A separate local focused run is not claimed because the reset execution environment had no checkout/network access.

## 15. Static-analysis requirements
Codacy static `93288694739` succeeds with zero annotations on the final head. Codacy diff coverage `93289365775` succeeds at 42.96% with no configured gate. Three historical CodeRabbit findings remain fixed/resolved and zero valid unresolved review threads remain. No fresh final-head CodeRabbit commit status was present at terminal publication time, so one is not claimed.

## 16. Documentation requirements
Report commands, permissions, workflow, evidence/privacy/retention, fallbacks, troubleshooting and attachment decision remain documented in `docs/wiki/pages/Reports-and-Evidence.md`. Current final staging evidence is published through canonical registry/workspace/handoff records.

## 17. Security and privacy requirements
Least-privilege `enthusiastaff.reports.evidence` remains separate from broad report management. Sensitive authorization is rechecked immediately before scheduled delivery. Broad helper GUI/text triage does not reveal exact coordinates or retained evidence contents. Chat identity/timestamp/body rendering is bounded; client rendering accepts only allow-listed scalar values; unexpected structured values and opaque Polar metadata are withheld; malformed storage fails closed.

## 18. Migration impact
No ES-P05 migration. V18 is current and immutable; V1–V18 were not rewritten.

## 19. Bedrock considerations
Text command fallback remains complete and readable. Evidence inspection is command-based and does not depend on Java-only inventory interactions. Representative live Bedrock acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Existing durable duplicate prevention/query/state ownership, optimistic revisions, row locks, idempotency and transactional persistence remain unchanged. `ReportRestartIntegrationTest` proves evidence/state/revision/assignment survive repeated MariaDB runtime initialization. Full multi-backend acceptance remains deferred to `ES-V02`.

## 21. External-provider considerations
RoseChat-specific PM capture remains excluded/unavailable until `ES-X01`; no provider-specific source dependency was introduced. Discord route delivery remains ES-P06.

## 22. Completion definition
Not yet met. Product/report hosted validation is green on exact final head `346e764f40b25c98e7d24ce7f863e5629773e814`, but the mandatory final canonical Pi run failed at private transient-release freshness verification before any database/Paper runtime assertion executed.

## 23. Resume state
Original selection: 2026-08-07 from `bf9b305ba96d9536f3d111c79eef674bd2e11dc5`. Finalization worker started from live `main` `e210df15ed90cf96757516fd363faa801bc7192c`. Implementation branch `package/es-p05-report-workflow`; PR #81. Current-main synchronization merge `f052fd8177f5b5eed6bc75111411e10b0ced66db`. Final exact hosted-green candidate `346e764f40b25c98e7d24ce7f863e5629773e814`. Preserve this branch/head unless live `main`, review or staging evidence requires a real change.

## 24. Last completed checkpoint
The earlier exact head `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2` later received genuine successful canonical Pi runtime evidence: public run `31301426684`, private run `31301734048` / job `93215499833`, two clean storage-ready Paper cycles, guarded DB reset/cleanup and sanitized artifact `9034945235`. After required synchronization and static remediation, final head `346e764f40b25c98e7d24ce7f863e5629773e814` passed all hosted gates. Its automatic public Pi build also passed, but private artifact verification failed before runtime.

## 25. Remaining checklist
First inspect existing exact final-head public run `31330788773` and private run `31331175023`; do not launch a duplicate merely because time passed. Require material evidence that the canonical bridge freshness/provenance condition changed. If it changes, synchronize any newer legitimate `main`, preserve/freeze the merge candidate, rerun affected exact-head gates as required, allow one normal automatic canonical public→private Pi run, and require real private provenance, guarded pre-reset, Paper cycle 1, clean shutdown/reap, cycle 2/restart/persistence, post-cleanup and sanitized evidence success before normal merge.

## 26. Known blockers
Only current ES-P05 blocker: final exact-head canonical staging stopped at private transfer freshness verification. Public run `31330788773` built and uploaded the exact source successfully; private run `31331175023` allocated trusted `Lincoln-PI-4` runner ID `2`, but job `93289556545` failed in `Retrieve and verify exact public bridge artifact` with `release created_at is expired for the staging bridge`. Guarded database reset and Paper runtime/restart were skipped. The verifier is unchanged from the earlier successful ES-P05 staging control. The evidence does not establish why the live release metadata appeared older than the two-hour bound, so no lower-level cause is invented. Do not weaken the freshness check, bypass provenance, directly dispatch private staging, or change ES-P05 product code in response.

## 27. Final evidence
Final exact candidate: `346e764f40b25c98e7d24ce7f863e5629773e814`.

Historical successful runtime proof on `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2`:
- public Pi `31301426684`, build `93214729981`, bridge `93215481473`: success;
- private run `31301734048`, job `93215499833`: success on `Lincoln-PI-4` runner ID `2`;
- pre-reset PASS; Paper cycle 1 storage ready; clean shutdown/reap; Paper cycle 2 storage ready with v18 current; clean shutdown/reap; post-cleanup PASS; zero failure scans;
- sanitized artifact `9034945235`, digest `sha256:a81af3154c7e561c5ea09ed7072c970d483b25b63e217f34f1976132bab4ef3e`.

Final-head hosted proof:
- Wiki `31330790907`: success;
- Coverage `31330790942` / `93288609588`: success;
- Codacy static `93288694739`: success, zero annotations;
- Codacy diff coverage `93289365775`: success, 42.96%, no configured gate;
- zero valid unresolved review threads.

Final-head canonical staging:
- public Pi run `31330788773`, public build `93288608088`: success;
- bridge `93289540403`: exact artifact download/release publication/private dispatch/correlation/failed-run diagnostics/public transfer cleanup all succeeded; final bridge verdict failure because private staging failed;
- private run `31331175023`, job `93289556545`: `Lincoln-PI-4`, runner ID `2`; runner assertion success; exact bridge artifact freshness/provenance verification failure; Paper/DB/restart skipped;
- sanitized diagnostic artifact `9042975898`, digest `sha256:1b470d2f20a6f263ed424734ff9397663077bbeec2d3ea942e3981ed926d7a93`;
- public transient release/tag cleanup: success.
- Sentinel: non-applicable; `.enthusia-test.yml` is absent.

## 28. Merge and synchronization record
Implementation PR #81 is intentionally **not merged**. Normal main synchronization merge `f052fd8177f5b5eed6bc75111411e10b0ced66db` is incorporated on the branch, followed by two bounded static-only commits ending at `346e764f40b25c98e7d24ce7f863e5629773e814`. The final implementation branch is zero behind finalization-worker starting `main` and contains only the nine ES-P05 report files. No external parity applies.

## 29. Canonical handoff
[`2026-08-09-es-p05-final-staging-prereq-blocked.md`](../../reports/package-handoffs/2026-08-09-es-p05-final-staging-prereq-blocked.md)

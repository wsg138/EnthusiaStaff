# `ES-P05` — Report evidence and staff workflow completion

## 1. Package identity
`ES-P05`; Internal; primary `COMP-STAFF`; priority 50; conditionally parallel only with proven non-overlap.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED` on PR #81. The ReportStore hosted-test regression is fixed and the exact final implementation head passes hosted Java/MariaDB validation, but required canonical private Pi staging has not executed because `Lincoln-PI-4` has not accepted the correlated private job.

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
PR #81 to `wsg138/EnthusiaStaff:main`; open and unmerged while canonical staging is incomplete.

## 12. Implementation checklist
- [x] Reconcile live GitHub and resume ES-P05 through canonical routing rather than create a competing repair PR.
- [x] Reconcile the old ES-P05 branch with current `main` through a normal two-parent merge commit.
- [x] Trace current submission/query/state/evidence runtime and persistence paths.
- [x] Identify the two ReportStore integration failures as a stale test-clock defect rather than a product persistence defect.
- [x] Repair the test clock without weakening assertions or changing production retention/query behavior.
- [x] Preserve provider-independent review/privacy implementation and newer mainline V18/staging/cheat-tester/fake-base work.
- [x] Keep the attachment boundary explicit: arbitrary direct file/screenshot/URL/binary attachments remain unsupported in ES-P05.
- [x] Add/retain direct command/GUI/privacy proof and restart durability proof.
- [x] Update report operational/privacy/retention documentation.
- [x] Harshly review the final product diff and repair all valid review findings.
- [x] Freeze and pass ordinary exact-head hosted/static validation on the final head.
- [ ] Obtain required successful canonical Pi boot/restart acceptance on the exact merge candidate.
- [ ] Merge normally, verify containment, finalize records, delete the temporary branch, and stop.

## 13. Acceptance criteria
Provider-independent product behavior and hosted proof are complete: submission/staff review are usable through GUI and text fallback; durable duplicate/cooldown/merge foundations remain; state/note revisions are exact and stale-safe; evidence is bounded, attributable, retained/purged by current policy, and privacy-gated for staff presentation. Final package completion is withheld only because canonical Pi execution has not run to terminal success on the final implementation head.

## 14. Test requirements
The exact final head passes the configured Java 21 build/test path including the full report integration suite, MariaDB/Testcontainers, migration validation, coverage, and runtime-JAR inspection. The two formerly failing `ReportStoreIntegrationTest` methods therefore pass on the final reviewed head. A separate local focused run is not claimed because the reset execution environment had no checkout/network access.

## 15. Static-analysis requirements
Codacy static succeeds with zero issues on the final head; Codacy diff coverage succeeds at 47.37% with no repository gate defined. CodeRabbit is green after three valid findings were fixed and all review threads resolved. Zero valid unresolved review threads remain.

## 16. Documentation requirements
Report commands, permissions, workflow, evidence/privacy/retention, fallbacks, troubleshooting and attachment decision remain documented in `docs/wiki/pages/Reports-and-Evidence.md`. The current ReportStore repair/staging blocker is published through the canonical registry/workspace/handoff records.

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
Not yet met. Product/report hosted validation and review are green on exact final head `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2`, but mandatory canonical Pi staging has not executed because the correlated self-hosted job remains unallocated.

## 23. Resume state
Original selection: 2026-08-07 from `bf9b305ba96d9536f3d111c79eef674bd2e11dc5`. Current repair worker started from live `main` `b0cf67b880856ec7536cf1385fe1559bb18a42a1`. Implementation branch `package/es-p05-report-workflow`; PR #81. Current reviewed/hosted-validation head `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2`. Main synchronization merge `5d78a9621f7cc3e5f056b417af88424eaa26e555`. Preserve this branch/head unless live staging/review evidence requires a real change.

## 24. Last completed checkpoint
The public hosted ReportStore failure is fixed. Final Coverage run `31301427623` / job `93214731253` succeeded. Public Pi Staging run `31301426684` also completed its exact-source hosted build successfully and dispatched correlated private run `31301734048`; private job `93215499833` remained queued with no runner allocation/steps at terminal publication time.

## 25. Remaining checklist
First inspect the existing public/private runs before triggering anything new. If private run `31301734048` later completed, use its real terminal evidence. If the runner/environment materially becomes available but the existing run cannot complete, allow a fresh normal automatic canonical Pi run only when justified. Require real private provenance/prerequisite/Paper/restart/persistence/cleanup success; merge PR #81 normally; verify containment; finalize COMPLETE state; clean branch; stop.

## 26. Known blockers
Only current package blocker: canonical Pi runtime acceptance has not executed. Correlated private job `93215499833` for exact final source remains queued with `runner_id: 0`, empty runner name, and zero executed steps because `Lincoln-PI-4` has not accepted it. This is temporary self-hosted environment/runner unavailability, not a product failure and not a pass. Do not issue duplicate retries while the condition is unchanged.

## 27. Final evidence
Final reviewed/hosted-validation head: `ebfbaa31d3de2b6a28b9dcbaf2c4366ee8e801e2`.

- Wiki run `31301427600`, job `93214726543`: success.
- Coverage run `31301427623`, job `93214731253`: success on Java 21 with full build/tests, MariaDB/Testcontainers, migration validation, aggregate coverage, runtime-JAR inspection and artifact/Codacy upload.
- Codacy static `93214975215`: success, zero issues.
- Codacy diff coverage `93215398455`: success, 47.37%; no configured gate.
- CodeRabbit: success after three valid findings were fixed; all three review threads resolved; zero valid unresolved threads remain.
- Public Pi Staging run `31301426684`: public build job `93214729981` success; exact runtime artifact upload success; bridge job `93215481473` dispatched and correlated private run `31301734048`.
- Private job `93215499833`: queued, `runner_id: 0`, empty runner name, zero steps. No private runtime assertion executed; staging is **NOT A PASS**.
- Sentinel: non-applicable; no `.enthusia-test.yml` exists on the implementation head.

## 28. Merge and synchronization record
Implementation PR #81 remains intentionally unmerged because required canonical staging has not executed to success. Current main synchronization is already incorporated through normal merge commit `5d78a9621f7cc3e5f056b417af88424eaa26e555`; final implementation head is zero behind current starting `main`. No external parity applies.

## 29. Canonical handoff
[`2026-08-09-es-p05-reportstore-repair-blocked-pi.md`](../../reports/package-handoffs/2026-08-09-es-p05-reportstore-repair-blocked-pi.md)

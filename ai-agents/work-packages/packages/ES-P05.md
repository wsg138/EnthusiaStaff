# `ES-P05` — Report evidence and staff workflow completion

## 1. Package identity
`ES-P05`; Internal; primary `COMP-STAFF`; priority 50; conditionally parallel only with proven non-overlap.

## 2. Status
`ACTIVE` / `ACTIONABLE_CONTINUATION` on `package/es-p05-report-workflow`.

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
Temporary `package/es-p05-report-workflow`; created from exact legitimate `main` `bf9b305ba96d9536f3d111c79eef674bd2e11dc5`; delete after verified merge containment.

## 11. Required PRs
One PR to `wsg138/EnthusiaStaff:main`; draft after the first coherent checkpoint.

## 12. Implementation checklist
- [x] Reconcile live GitHub, classify every incomplete package, and select ES-P05 through canonical routing.
- [x] Confirm no prior ES-P05 branch/PR/handoff and create the documented temporary branch from exact `main`.
- [ ] Trace current submission/query/state/evidence runtime and persistence paths against the audit and requirements matrix.
- [ ] Implement missing provider-independent behavior and make the attachment boundary explicit.
- [ ] Add/strengthen command, GUI, offline-target, cooldown/replay, stale revision, concurrent staff, restart, evidence bound/purge, rollback, and Bedrock/text-fallback tests.
- [ ] Update report operational/privacy documentation, package state, and canonical handoff.
- [ ] Harshly review the full final diff and resolve every valid review/static-analysis finding.
- [ ] Freeze and validate the exact final head with all applicable hosted gates and staging disposition.
- [ ] Merge normally, verify containment, clean the temporary branch when tooling permits, finalize records, and stop.

## 13. Acceptance criteria
Submission and staff review are usable via GUI and text fallback; duplicate/cooldown/merge rules are durable; status/note changes are revision-safe; evidence is bounded, attributable, retained/purged correctly, and privacy-safe.

## 14. Test requirements
Command/GUI wiring, offline target, cooldown/replay, stale revision, concurrent staff, restart, evidence bounds/purge, failure rollback, and Bedrock fallback tests plus full applicable suites.

## 15. Static-analysis requirements
All configured Java/static-analysis/review-bot gates with zero valid unresolved findings.

## 16. Documentation requirements
Report commands, permissions, workflow, evidence/privacy/retention, fallbacks, troubleshooting, Wiki, registry, package, handoff.

## 17. Security and privacy requirements
Least-privilege evidence access; no PM/provider invention; redact coordinates/client evidence where required; no private rows in artifacts.

## 18. Migration impact
No migration assumed. The live package-start boundary is immutable V17; essential schema additions require a new V18-or-later immutable migration and clean/upgrade/checksum tests.

## 19. Bedrock considerations
Complete text fallback and readable messages; live client acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Durable duplicate prevention, cross-backend query/state ownership, concurrent reviewers, reconnect, and restart.

## 21. External-provider considerations
RoseChat-specific PM capture is excluded and must remain explicit/unavailable until `ES-X01`.

## 22. Completion definition
Provider-independent scope proven, documented, exactly reviewed, and merged in one normal PR; no valid threads; branch cleanup verified.

## 23. Resume state
Selected on 2026-08-07 by the generic sequential worker. Starting `main` is `bf9b305ba96d9536f3d111c79eef674bd2e11dc5`; active branch is `package/es-p05-report-workflow`. No prior ES-P05 PR or handoff existed. The separate `docs/wiki-maintenance-2026-08` branch is not a package branch and is outside this worker's state authority.

## 24. Last completed checkpoint
Canonical startup/reconciliation completed; ES-P02 remains unchanged `PARKED_BLOCKED`, ES-P05 was the lowest-priority eligible `READY` package, and the package branch plus durable claim records were created.

## 25. Remaining checklist
Trace, implement, test, document, review, exact-head validate, merge, verify containment/cleanup, and publish final package evidence.

## 26. Known blockers
No current package blocker. RoseChat PM evidence remains intentionally routed to `ES-X01`; Discord route delivery remains `ES-P06`; issue #43 remains open/deferred and excluded. The private Actions Billing & plans restriction affecting ES-P02 is unchanged and does not block ES-P05 hosted implementation work unless an ES-P05 package-specific staging gate later requires that unavailable path.

## 27. Final evidence
Pending. Package-start migration boundary is V17. Exact final head, coverage matrix, DB/runtime tests, checks, review, privacy evidence, and staging disposition will be recorded after freeze.

## 28. Merge and synchronization record
Pending. Record normal merge commit, resulting `main`, containment, divergence, and temporary-branch cleanup; external parity is not applicable.

## 29. Canonical handoff
[`2026-08-07-es-p05-report-workflow.md`](../../reports/package-handoffs/2026-08-07-es-p05-report-workflow.md)

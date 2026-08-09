# `ES-P05` — Report evidence and staff workflow completion

## 1. Package identity
`ES-P05`; Internal; primary `COMP-STAFF`; priority 50; conditionally parallel only with proven non-overlap.

## 2. Status
`BLOCKED` / `PARKED_BLOCKED` on PR #81 because the required private trusted-build/Pi staging path cannot allocate its GitHub-hosted build runner under the account Billing & plans restriction.

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
PR #81 to `wsg138/EnthusiaStaff:main`; open and unmerged while blocked.

## 12. Implementation checklist
- [x] Reconcile live GitHub and select ES-P05 through canonical routing.
- [x] Trace current submission/query/state/evidence runtime and persistence paths.
- [x] Implement confirmed provider-independent review/privacy gaps without expanding into ES-X01 or ES-P06.
- [x] Make the attachment boundary explicit: arbitrary direct file/screenshot/URL/binary attachments are unsupported in ES-P05.
- [x] Add direct command/GUI/privacy wiring proof and restart durability proof while preserving existing cooldown/replay/stale/concurrency/purge/rollback tests.
- [x] Update report operational/privacy/retention documentation.
- [x] Harshly review the final product diff and repair found defects before freeze.
- [x] Freeze and run ordinary exact-head hosted/static validation.
- [ ] Obtain the required successful trusted private build and Pi safe boot/restart on the exact merge candidate.
- [ ] Rerun CodeRabbit when review quota is available and resolve any valid findings.
- [ ] Merge normally, verify containment, finalize records, delete the temporary branch, and stop.

## 13. Acceptance criteria
Product behavior and hosted proof are implemented: submission/staff review are usable through GUI and text fallback; durable duplicate/cooldown/merge foundations remain; state/note revisions are exact and stale-safe; evidence is bounded, attributable, retained/purged by existing policy, and now privacy-gated for staff presentation. Final package completion is withheld until the required staging gate succeeds and the PR merges normally.

## 14. Test requirements
Satisfied on the frozen hosted-validation head for command/GUI wiring, offline target path, cooldown/replay, stale revision, concurrent staff, restart, evidence bounds/purge, failure rollback, and text/Bedrock-safe fallback. Representative live client/distributed acceptance remains ES-V02, while the package's required trusted-build/Pi gate is currently blocked before execution.

## 15. Static-analysis requirements
Codacy static succeeded with zero issues on the frozen head. CodeRabbit was quota-limited after the PR became ready and produced no findings; it must be rerun before merge when ES-P05 resumes. Zero inline review threads currently exist.

## 16. Documentation requirements
Report commands, permissions, workflow, evidence/privacy/retention, fallbacks, troubleshooting and attachment decision are updated in `docs/wiki/pages/Reports-and-Evidence.md`; registry/workspace/package/handoff terminal blocker state is published separately to `main`.

## 17. Security and privacy requirements
Implemented least-privilege `enthusiastaff.reports.evidence`; broad helper-level GUI/text triage does not reveal exact coordinates or retained evidence contents; chat rendering is bounded; client rendering is allow-listed; opaque Polar metadata is withheld; malformed storage fails closed; no private rows or evidence bodies are placed in build artifacts.

## 18. Migration impact
No migration. Immutable V17 remains the package-start and terminal schema boundary.

## 19. Bedrock considerations
Text command fallback remains complete and readable. Evidence inspection itself is command-based and does not depend on Java-only inventory interactions. Representative live Bedrock acceptance remains `ES-V02`.

## 20. Distributed-runtime considerations
Existing durable duplicate prevention/query/state ownership, optimistic revisions, row locks, idempotency and transactional persistence remain. New `ReportRestartIntegrationTest` proves evidence/state/revision/assignment survive repeated MariaDB runtime initialization. Full multi-backend acceptance remains deferred to `ES-V02`.

## 21. External-provider considerations
RoseChat-specific PM capture remains excluded/unavailable until `ES-X01`; no provider-specific source dependency was introduced. Discord route delivery remains ES-P06.

## 22. Completion definition
Not yet met. Provider-independent product scope is implemented and hosted-validated, but PR #81 cannot merge normally until required private staging succeeds on the exact merge candidate and the review bot is rerun.

## 23. Resume state
Selected 2026-08-07 from legitimate `main` `bf9b305ba96d9536f3d111c79eef674bd2e11dc5`. Implementation branch `package/es-p05-report-workflow`; PR #81. Frozen implementation/hosted-validation head `4a38e191395913c6733726e222f0889a2d56d267`. Do not modify product code without a newly confirmed defect. Reconcile any newer `main` through normal merge rules before a new freeze.

## 24. Last completed checkpoint
All product work and ordinary exact-head hosted/static checks completed on `4a38e191395913c6733726e222f0889a2d56d267`; required private staging failed before runner allocation under Billing & plans, so the package was classified `BLOCKED` / `PARKED_BLOCKED` instead of merged.

## 25. Remaining checklist
Resolve the private Actions billing/spending-limit condition; resume PR #81; reconcile legitimate `main`; rerun CodeRabbit and every exact-head hosted/static/staging gate; require trusted private build + Pi safe boot/restart; merge normally; verify containment; finalize/clean branch; stop.

## 26. Known blockers
Primary blocker: private `wsg138/EnthusiaStaff-Staging` cannot start its required `ubuntu-latest` trusted build because GitHub reports failed recent payments or insufficient Actions spending limit. Latest build job `92881577147` has runner ID `0`, empty runner name and `steps: []`; Pi job `92881591391` skipped. This is infrastructure-unavailable evidence, not product failure and not a pass. CodeRabbit review is additionally quota-limited and must rerun on resume; it returned no product finding.

## 27. Final evidence
Frozen implementation / hosted-validation head: `4a38e191395913c6733726e222f0889a2d56d267`.

- Wiki run `31183192145`, job `92881243088`: success.
- Coverage run `31183192068`, job `92881313210`: success on GitHub-hosted Java 21, including full build/tests, MariaDB/Testcontainers, migration integrity, aggregate coverage, runtime-JAR build/inspection and Codacy upload.
- Validation artifact `8995826742` (`java-21-validation`), digest `sha256:ed87314d5eda8286928ce64f11027240898a0823333c6ffa5aa6d98f1697dbe4`.
- Codacy static `92882185524`: success, zero issues/annotations.
- Codacy coverage variation `92882989470`: success, `+0.05%` variation.
- Codacy diff coverage `92882989439`: success, `49.05%`; repository gate undefined.
- Harsh self-review found and fixed three issues before freeze: broad GUI exact-coordinate exposure, raw nested AutoClicker rendering, oldest-snapshot default selection.
- Zero inline review threads. CodeRabbit review quota was exhausted and must be rerun before merge.
- Latest staging wrapper `31183283525` / `92881545286` dispatched private run `31183290816`; trusted build `92881577147` failed before runner allocation with Billing & plans annotation; Pi `92881591391` skipped. Earlier automatic exact-head dispatch had the same result. No manual identical retry followed confirmation.

## 28. Merge and synchronization record
Implementation PR #81 is intentionally **not merged**. `main` remained `bf9b305ba96d9536f3d111c79eef674bd2e11dc5` through the product freeze and blocker classification. Terminal package-state publication is documentation-only and does not merge ES-P05 product code. No external parity applies.

## 29. Canonical handoff
[`2026-08-07-es-p05-report-workflow.md`](../../reports/package-handoffs/2026-08-07-es-p05-report-workflow.md)

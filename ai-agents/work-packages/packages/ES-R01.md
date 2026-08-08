# `ES-R01` — Billing-independent staging bridge recovery

## 1. Package identity
`ES-R01`; Validation-infrastructure recovery; primary `COMP-STAFF`; supporting repository `wsg138/EnthusiaStaff-Staging`; priority 15; not parallel-safe with staging-workflow changes.

## 2. Status
`IN_PROGRESS` / `VERIFYING`. The implementation is merged in both repositories. The first merged-main bridge attempt proved the new hosted-build/transfer/provenance path but failed closed before Paper boot because the disposable MariaDB connection returned SQLState `08000`; a bounded connection-readiness correction is now merged in staging and a fresh exact-main proof remains required.

## 3. Objective
Remove the shared repository-side staging deadlock without weakening validation by moving the mandatory ordinary hosted build for an exact EnthusiaStaff source SHA onto the public `wsg138/EnthusiaStaff` GitHub-hosted workflow, then securely handing the verified exact-head artifact and provenance to the existing private self-hosted Pi staging job for safe boot/restart validation.

## 4. Why the package exists
`ES-P02` and `ES-P05` are otherwise implementation-complete enough to wait only on the same private staging path. Private staging runs began failing before runner allocation on the `ubuntu-latest` build with GitHub's Billing & plans payment/spending-limit rejection, while `wsg138/EnthusiaStaff-Staging` workflows using labels `self-hosted`, `Linux`, `ARM64`, `enthusia-staging` continued to run on `Lincoln-PI-4`. The public EnthusiaStaff repository continues to run ordinary GitHub-hosted Java 21 builds successfully. ES-R01 removes the shared dependency on private GitHub-hosted minutes while preserving both required validation classes.

## 5. Included audit/package IDs
Validation infrastructure for `ES-P02` and `ES-P05`; later reusable staging infrastructure for ordinary development packages. This package does not complete or waive any product audit ID.

## 6. Included behavior
- Build and validate the exact authorized EnthusiaStaff source SHA on public GitHub-hosted `ubuntu-latest` infrastructure.
- Preserve same-repository PR trust checks and exact SHA authorization.
- Produce an immutable runtime package manifest containing exact source SHA, workflow/control revision, runtime filename, size, and SHA-256 digest.
- Transfer or expose that exact artifact to the private staging workflow through a secure, bounded GitHub-supported mechanism that does not require private GitHub-hosted execution.
- Verify artifact identity and provenance again before boot on the Pi.
- Run the guarded disposable Paper safe boot/restart test only on `Lincoln-PI-4` or its explicitly equivalent trusted self-hosted staging labels.
- Return one correlated verdict to the source PR and preserve sanitized evidence.
- Keep fork PRs unable to obtain private staging secrets or execution.
- Add regression fixtures for source selection, provenance/digest mismatch, missing/expired artifact, duplicate dispatch, cancellation, failure propagation, and transient disposable-database connection readiness.

## 7. Explicit exclusions
No product Java behavior changes; no migrations; no production deployment/data/routes/credentials; no issue #43 activation or acceptance; no LiteBans authority change; no validation exception; no relabeling failed/skipped/zero-runner evidence as success; no weakening exact-head or ordinary-hosted-build requirements.

## 8. Dependencies
Package dependency graph: none.

### Operational prerequisites
The public hosted Actions path, current cross-repository dispatch credential, and already-operational private self-hosted Pi staging runner are live operational prerequisites rather than package dependencies. If any is unavailable, or if secure artifact transfer requires a new owner-only credential or runner registration that does not already exist, stop as `BLOCKED` and record that exact external requirement rather than inventing a bypass.

## 9. Component and repository boundaries
Workflow/tooling/test/documentation changes only in `wsg138/EnthusiaStaff` and `wsg138/EnthusiaStaff-Staging`. Product source, runtime behavior, migrations, package implementations, production configuration, and private evidence are excluded.

## 10. Required branches
The primary implementation used temporary `package/es-r01-staging-bridge-recovery` branches in both required repositories. A staging-only follow-up used `package/es-r01-database-readiness` after the first merged-main proof exposed a transient database-readiness failure, and this checkpoint uses `package/es-r01-proof-retry-checkpoint` to record the evidence and trigger a fresh exact-main proof. Temporary branches must contain no unique work before cleanup.

## 11. Required PRs
The required public/private infrastructure PRs merged normally as `wsg138/EnthusiaStaff#93` and `wsg138/EnthusiaStaff-Staging#58`. The same-package staging readiness correction merged normally as `wsg138/EnthusiaStaff-Staging#59`. Neither contains product Java changes.

## 12. Implementation checklist
Startup reconciliation complete; public exact-SHA hosted build implemented; private self-hosted artifact retrieval/provenance verification implemented; trust/fork boundaries retained; negative-path fixtures and operator documentation added; both primary implementation PRs reviewed/frozen/merged normally; staging transient-connection readiness correction reviewed/tested/merged normally. Remaining work is a successful fresh exact-current-main end-to-end proof, final containment/cleanup verification, and terminal canonical state publication.

## 13. Acceptance criteria
- An exact EnthusiaStaff source SHA receives a successful ordinary GitHub-hosted build without using private-repository hosted minutes.
- The private staging phase allocates the trusted self-hosted runner and boots/restarts the exact verified runtime artifact produced by that build, or fails closed before boot on any provenance/digest/source mismatch.
- No private GitHub-hosted build is required for the route.
- Fork/untrusted source cannot dispatch or consume private staging secrets.
- Failed, skipped, cancelled, missing, mismatched, expired, or zero-runner evidence is never reported as a pass.
- A correlated end-to-end dry run against a safe current `main` SHA proves the bridge and produces sanitized source/build/Pi evidence.
- The package does not itself claim ES-P02 or ES-P05 staging completion; after ES-R01 completes, ES-P02 becomes the highest-priority `ACTIONABLE_CONTINUATION` and must rerun its own exact-head gates.

## 14. Test requirements
Run existing staging-control fixtures plus tests for exact source authorization, fork rejection, artifact metadata/digest verification, stale or missing artifact, wrong SHA, duplicate request, workflow cancellation/failure propagation, self-hosted runner selection, disposable-database connection readiness, safe boot/restart evidence, and no dependency on a private `ubuntu-latest` job. Run all applicable repository-configured checks in both repositories.

## 15. Static-analysis requirements
All configured workflow/security/static-analysis/review gates for changed files in both repositories; zero valid unresolved findings. Pin third-party Actions by immutable commit SHA consistent with repository policy.

## 16. Documentation requirements
Document the trust boundary, artifact/provenance handoff, exact-head correlation, failure modes, operator recovery, retention, and how ES-P02/ES-P05 resume after this package. Update registry/workspace/handoffs on completion.

## 17. Security and privacy requirements
No secrets in artifacts/logs; least-privilege tokens; artifact retrieval bound to exact repository/SHA/run metadata and verified digest; no fork access to private environment; sanitized Pi evidence only; reject redirects or alternate artifact sources that break provenance. Disposable database retries apply only to SQL connection-class `08xxx` failures, remain bounded, and do not permit Paper boot until a successful guarded reset.

## 18. Migration impact
None. V18 remains immutable and this package must not add or modify Flyway migrations.

## 19. Bedrock considerations
Not applicable to this infrastructure repair. Representative Java/Bedrock acceptance remains assigned to later validation packages.

## 20. Distributed-runtime considerations
Only staging orchestration is in scope. Do not claim distributed product acceptance from the bridge proof.

## 21. External-provider considerations
GitHub Actions and the existing private self-hosted runner are validation infrastructure, not product providers. No third-party artifact hosting or new external service was introduced.

## 22. Completion definition
Both exact-head infrastructure PRs merge normally with applicable checks/reviews green; a safe current-main end-to-end run proves public hosted build plus private self-hosted Pi boot/restart with exact artifact provenance; branches contain no unique work and are cleaned where tooling permits; canonical state is updated so ES-P02 is the highest-priority `ACTIONABLE_CONTINUATION`.

## 23. Resume state
Assigned to the current generic sequential worker. Primary implementation is merged. Current checkpoint branch: `wsg138/EnthusiaStaff:package/es-r01-proof-retry-checkpoint` from public main `094838fa221476e0832cf821f7b4908b9402d0d9`. Staging main includes PR #58 merge `570f83e41cb80b498a82c8b5a509c42345558a46` and PR #59 merge `313ed2815058eadeb8c823453f4152089cae01d4`.

## 24. Last completed checkpoint
The first merged-main proof used public Pi Staging run `31249125885` at source/control SHA `094838fa221476e0832cf821f7b4908b9402d0d9`. Public hosted job `93082543002` succeeded on GitHub-hosted runner ID `1000009805` and uploaded the exact runtime package. Bridge job `93083229835` successfully created the bounded transfer, dispatched and correlated private run `31249402654`, and removed the transient release/tag afterward. Private job `93083246690` allocated `Lincoln-PI-4` (runner ID `2`) and passed the full release/run/PR/digest/manifest provenance verifier. The guarded database reset then failed before Paper boot with connection-class SQLState `08000`; the workflow correctly reported failure rather than pass. The temporary public release ID `367158184` and tag `es-r01-staging-31249125885-1` were both confirmed absent after cleanup.

A same-package correction in staging PR #59 added bounded retries only for SQLState class `08xxx` connection failures (7 total attempts with 5-second delays) while preserving immediate failure for other reset failures and preserving the rule that Paper cannot start until the guarded pre-reset succeeds. Exact-head Staging Controls CI run `31249532617`, job `93083557688`, succeeded on `Lincoln-PI-4` at head `74dceeba18c603b280449e8fba5a09e789ffd361`, including source-selection fixtures, ES-R01 bridge artifact fixtures, disposable-database wrapper retry fixtures, storage-readiness fixtures, successful-cycle fixture, issue #43 prerequisite fixtures, and Sentinel queue tests. PR #59 then merged normally as `313ed2815058eadeb8c823453f4152089cae01d4`.

## 25. Remaining checklist
Merge this checkpoint normally to produce a fresh public `main` SHA and trigger the repaired bridge; require a successful public hosted build, exact private provenance verification, guarded disposable database reset, two-cycle Paper boot/restart, sanitized evidence upload, correlated public success, and transient release/tag cleanup. Then verify merge containment/temporary branches, publish terminal package/registry/workspace/handoff state, and stop without starting ES-P02.

## 26. Known blockers
No repository-independent blocker is currently established. The first proof's MariaDB SQLState `08000` was connection-class and the same guarded database/Pi path has succeeded historically; the package therefore added bounded readiness handling rather than weakening or bypassing the database gate. If the fresh proof exhausts those bounded connection retries, record the actual database availability prerequisite as the blocker instead of broadening this package or weakening validation.

## 27. Final evidence
Implementation merges so far: private bridge PR #58 → `570f83e41cb80b498a82c8b5a509c42345558a46`; public bridge PR #93 → `094838fa221476e0832cf821f7b4908b9402d0d9`; staging readiness PR #59 → `313ed2815058eadeb8c823453f4152089cae01d4`. Public PR #93's frozen head `cccadbd1885f78db517ff643f941d04bd0fba2a3` passed the full build/tests/runtime inspection, Codacy static analysis with zero issues, coverage variation, and diff coverage; CodeRabbit was rate-limited and produced no review threads. Private PR #58 and #59 exact-head control suites were green with no unresolved review threads. Terminal current-main bridge evidence is pending the fresh proof triggered by this checkpoint.

## 28. Merge and synchronization record
Primary public/private infrastructure and the staging readiness correction are merged normally as recorded above. No component-source parity requirement applies and no product/migration source was changed. Terminal state remains intentionally unpublished until a fresh post-PR59 current-main bridge proof succeeds.

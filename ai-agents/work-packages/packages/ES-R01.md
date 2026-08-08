# `ES-R01` — Billing-independent staging bridge recovery

## 1. Package identity
`ES-R01`; Validation-infrastructure recovery; primary `COMP-STAFF`; supporting repository `wsg138/EnthusiaStaff-Staging`; priority 15; not parallel-safe with staging-workflow changes.

## 2. Status
`READY`. This package was created by the owner-directed canonical deadlock-recovery worker after live evidence proved that private GitHub-hosted jobs are billing-blocked while the existing `Lincoln-PI-4` self-hosted staging runner is healthy.

## 3. Objective
Remove the shared repository-side staging deadlock without weakening validation by moving the mandatory ordinary hosted build for an exact EnthusiaStaff source SHA onto the public `wsg138/EnthusiaStaff` GitHub-hosted workflow, then securely handing the verified exact-head artifact and provenance to the existing private self-hosted Pi staging job for safe boot/restart validation.

## 4. Why the package exists
`ES-P02` and `ES-P05` are otherwise implementation-complete enough to wait only on the same private staging path. Current private staging runs fail before runner allocation on the `ubuntu-latest` build with GitHub's Billing & plans payment/spending-limit rejection, while a current `wsg138/EnthusiaStaff-Staging` workflow using labels `self-hosted`, `Linux`, `ARM64`, `enthusia-staging` successfully ran on `Lincoln-PI-4`. The public EnthusiaStaff repository continues to run ordinary GitHub-hosted Java 21 builds successfully. Finite workflow/tooling work can therefore remove the shared dependency on private GitHub-hosted minutes while preserving both required validation classes.

## 5. Included audit/package IDs
Validation infrastructure for `ES-P02` and `ES-P05`; later reusable staging infrastructure for ordinary development packages. This package does not complete or waive any product audit ID.

## 6. Included behavior
- Build and validate the exact authorized EnthusiaStaff source SHA on public GitHub-hosted `ubuntu-latest` infrastructure.
- Preserve same-repository PR trust checks and exact SHA authorization.
- Produce an immutable runtime package manifest containing exact source SHA, workflow/control revision, runtime filename, size, and SHA-256 digest.
- Transfer or expose that exact artifact to the private staging workflow through a secure, bounded GitHub-supported mechanism that does not require private GitHub-hosted execution.
- Verify artifact identity and provenance again before boot on the Pi.
- Run the existing guarded disposable Paper safe boot/restart test only on `Lincoln-PI-4` or its explicitly equivalent trusted self-hosted staging labels.
- Return one correlated verdict to the source PR and preserve sanitized evidence.
- Keep fork PRs unable to obtain private staging secrets or execution.
- Add regression fixtures for source selection, provenance/digest mismatch, missing/expired artifact, duplicate dispatch, cancellation, and failure propagation.

## 7. Explicit exclusions
No product Java behavior changes; no migrations; no production deployment/data/routes/credentials; no issue #43 activation or acceptance; no LiteBans authority change; no validation exception; no relabeling failed/skipped/zero-runner evidence as success; no weakening exact-head or ordinary-hosted-build requirements.

## 8. Dependencies
None. Existing public hosted Actions, the current cross-repository dispatch credential, and the already-operational private self-hosted Pi staging runner are present live dependencies. If implementation discovers that secure artifact transfer requires a new owner-only credential or runner registration that does not already exist, stop as `BLOCKED` and record that exact new external requirement rather than inventing a bypass.

## 9. Component and repository boundaries
Workflow/tooling/test/documentation changes only in `wsg138/EnthusiaStaff` and `wsg138/EnthusiaStaff-Staging`. Product source, runtime behavior, migrations, package implementations, production configuration, and private evidence are excluded.

## 10. Required branches
Use temporary `package/es-r01-staging-bridge-recovery` branches in both required repositories. Do not reuse this definition-publication branch for implementation. Delete implementation branches after verified merge containment and no unique work.

## 11. Required PRs
Two cross-referenced process/infrastructure PRs: one to `wsg138/EnthusiaStaff:main` for the public trusted-build/dispatch side and one to `wsg138/EnthusiaStaff-Staging:main` for the self-hosted artifact-consumption/Pi side. Use normal merge commits only. Neither PR may contain product Java changes.

## 12. Implementation checklist
Reconcile both default heads and open work; inspect current wrapper/private workflow and tests; define the exact artifact/provenance handoff; implement public exact-SHA hosted build and artifact publication; implement private self-hosted artifact retrieval/verification with no private hosted prerequisite; retain trust/fork boundaries; add negative-path fixtures; harshly review both diffs; freeze both heads; run all applicable workflow/static/documentation tests; execute a safe exact-main bridge proof; normally merge both PRs; verify containment/cleanup; publish terminal state.

## 13. Acceptance criteria
- An exact EnthusiaStaff source SHA receives a successful ordinary GitHub-hosted build without using private-repository hosted minutes.
- The private staging phase allocates the trusted self-hosted runner and boots/restarts the exact verified runtime artifact produced by that build, or fails closed before boot on any provenance/digest/source mismatch.
- No private GitHub-hosted build is required for the route.
- Fork/untrusted source cannot dispatch or consume private staging secrets.
- Failed, skipped, cancelled, missing, mismatched, expired, or zero-runner evidence is never reported as a pass.
- A correlated end-to-end dry run against a safe current `main` SHA proves the bridge and produces sanitized source/build/Pi evidence.
- The package does not itself claim ES-P02 or ES-P05 staging completion; after ES-R01 completes, those packages become `ACTIONABLE_CONTINUATION` and must rerun their own exact-head gates.

## 14. Test requirements
Run existing staging-control fixtures plus new tests for exact source authorization, fork rejection, artifact metadata/digest verification, stale or missing artifact, wrong SHA, duplicate request, workflow cancellation/failure propagation, self-hosted runner selection, safe boot/restart evidence, and no dependency on a private `ubuntu-latest` job. Run all applicable repository-configured checks in both repositories.

## 15. Static-analysis requirements
All configured workflow/security/static-analysis/review gates for changed files in both repositories; zero valid unresolved findings. Pin third-party Actions by immutable commit SHA consistent with repository policy.

## 16. Documentation requirements
Document the trust boundary, artifact/provenance handoff, exact-head correlation, failure modes, operator recovery, retention, and how ES-P02/ES-P05 resume after this package. Update registry/workspace/handoffs on completion.

## 17. Security and privacy requirements
No secrets in artifacts/logs; least-privilege tokens; artifact retrieval must be bound to exact repository/SHA/run metadata and verified digest; no fork access to private environment; sanitized Pi evidence only; reject redirects or alternate artifact sources that break provenance.

## 18. Migration impact
None. V18 remains immutable and this package must not add or modify Flyway migrations.

## 19. Bedrock considerations
Not applicable to this infrastructure repair. Representative Java/Bedrock acceptance remains assigned to later validation packages.

## 20. Distributed-runtime considerations
Only staging orchestration is in scope. Do not claim distributed product acceptance from the bridge proof.

## 21. External-provider considerations
GitHub Actions and the existing private self-hosted runner are validation infrastructure, not product providers. Do not add third-party artifact hosting or new external services unless explicitly owner-approved through a later package-state change.

## 22. Completion definition
Both exact-head infrastructure PRs merge normally with all applicable checks/reviews green; a safe current-main end-to-end run proves public hosted build plus private self-hosted Pi boot/restart with exact artifact provenance; branches are cleaned; canonical state is updated so ES-P02 is the highest-priority `ACTIONABLE_CONTINUATION` (then ES-P05 if P02 cannot proceed for a different reason).

## 23. Resume state
Unassigned and `READY`. No implementation branch or implementation PR exists. The next normal sequential worker must select ES-R01 and implement only this package.

## 24. Last completed checkpoint
Owner-directed deadlock analysis established the package definition and live feasibility evidence only. No workflow/tooling implementation has begun.

## 25. Remaining checklist
All two-repository implementation, tests, review, exact-head validation, safe bridge proof, merge, containment, cleanup, and terminal publication remain.

## 26. Known blockers
No known repository-independent blocker to begin implementation. The current private hosted billing restriction is the condition this package is specifically designed not to depend on. If the self-hosted Pi runner ceases to be available or secure artifact transfer cannot be achieved without a new owner-only credential, record the precise condition and stop rather than weakening the gate.

## 27. Final evidence
Unset: exact repository bases/heads, two PRs/merge commits, workflow run/job IDs, artifact name/ID/digest/source SHA, Pi runner ID/name, boot/restart evidence, review findings, and containment.

## 28. Merge and synchronization record
Unset. Two workflow-infrastructure PRs are required; no component-source parity requirement applies. Record both normal merge commits and resulting default heads before completion.

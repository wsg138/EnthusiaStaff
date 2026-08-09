# ES-R01 staging bridge recovery definition handoff

- Recorded: 2026-08-08
- Recovery task: owner-directed canonical package deadlock recovery
- Starting legitimate `main`: `41659389ba105e099c77966015714067ea6f1ae7`
- Product implementation changed: no
- Migrations changed: no; V18 remains immutable/current
- Issue #43: open and deferred
- LiteBans: remains authoritative

## Blocked-package classification

### ES-P02

`GENUINELY_EXTERNAL_BLOCKER` / canonical `PARKED_BLOCKED`.

PR #70 is open on `package/es-p02-runtime-db-recovery`, current head `99da4103773e0c2ae43e0b0253200cd0d3d2c65c`. Its product implementation remains unique to the branch. Existing hosted validation and review evidence is green/resolved, but the required private ordinary build/Pi route cannot currently complete. A live private staging run created 2026-08-08 at 05:40:54 UTC again produced required `ubuntu-latest` job `93065006558` with runner ID `0`, empty runner name and `steps: []`; GitHub reported the same account payment/spending-limit restriction. Do not rerun ES-P02 until this condition changes through billing repair or ES-R01 completion.

### ES-P05

`GENUINELY_EXTERNAL_BLOCKER` / canonical `PARKED_BLOCKED`.

PR #81 is open on `package/es-p05-report-workflow`, frozen head `4a38e191395913c6733726e222f0889a2d56d267`. Coverage/Wiki/Codacy evidence remains successful and there are zero review threads. The repository commit status now exposes CodeRabbit success, so the old quota-limited review note is stale as a secondary condition; it does not remove the mandatory staging blocker. The same current private Billing & plans condition prevents the required ordinary build/Pi route. Do not sync or rerun ES-P05 while that shared condition remains unchanged.

## Dependency analysis

No existing incomplete package was safely made READY by relaxing product dependencies.

- ES-P07 genuinely depends on ES-P02 lifecycle/database-recovery/Velocity-reload behavior before inventory runtime integration.
- ES-P06 owns Discord delivery plus reload/restart/runtime integration. Although current `main` already has report outbox producers and P05's unique diff does not touch the Discord worker/store, P06 also overlaps Velocity/runtime surfaces changed substantially by the unmerged ES-P02 continuation. Removing the P05 edge alone would not establish a safe integration order, so no dependency was relaxed merely to create work.
- ES-P08 and the destructive provider packages depend on inventory/destructive journal foundations in their declared predecessors.
- ES-X01 remains both P05-dependent for report/private-message evidence integration and independently blocked on a verified RoseChat provider repository.
- ES-V01/V02/V03, ES-A01 and ES-QA01 are validation/acceptance/final-audit packages whose prerequisites or private/owner conditions remain real.

## Shared blocker-resolution evidence

The deadlock is not purely external because repository-side validation orchestration can remove the dependency on private GitHub-hosted minutes without waiving a gate:

- Public `wsg138/EnthusiaStaff` continues to execute ordinary GitHub-hosted Java 21 validation.
- Current source workflow `.github/workflows/pi-staging-check.yml` already runs a public `ubuntu-latest` wrapper and dispatches the private staging workflow.
- Current private workflow `.github/workflows/plugin-live-test.yml` unnecessarily performs its trusted artifact build on private `ubuntu-latest` before the Pi job.
- The private self-hosted runner is available despite the billing restriction: private run `31242140573`, job `93064778261`, executed successfully on runner ID `2`, name `Lincoln-PI-4`, labels `self-hosted`, `Linux`, `ARM64`, `enthusia-staging` immediately before the newest private-hosted failure.
- Therefore finite workflow/tooling work can build the exact authorized source on public hosted infrastructure, preserve immutable source/artifact provenance, and hand the verified artifact to the existing private self-hosted Pi job. This keeps an ordinary hosted build plus actual Pi safe boot/restart and does not use an infrastructure exception.

## Routing result

Created canonical package `ES-R01 — Billing-independent staging bridge recovery` as `READY`, priority 15, with no product dependency. This definition/publication worker does not implement ES-R01.

The next normal sequential worker must select ES-R01, reconcile both `wsg138/EnthusiaStaff` and `wsg138/EnthusiaStaff-Staging`, create the two implementation branches/PRs required by its contract, preserve exact-head artifact provenance and fork/secret boundaries, validate an end-to-end safe current main bridge, merge normally, publish completion, and stop.

After ES-R01 completes, a policy-valid repository-side staging route is available for ES-P02/ES-P05 even if GitHub billing remains blocked. Canonical continuation priority then resumes ES-P02 before ES-P05 and each package must rerun all of its own exact-head hosted/review/staging gates. ES-R01 completion itself is not ES-P02/ES-P05 staging evidence.

## Definition publication evidence

- Definition PR: #90, branch `package/es-r01-staging-bridge-recovery-definition`.
- Frozen exact PR head: `5c68df5b774625ae78edce3b71f86dbc9c47951c`; diff contained exactly six Markdown/package-state files and no product, workflow, configuration, or migration file.
- Exact-head Coverage run `31243460997`, job `93068075572`: success after exact checkout, Java 21 build/tests, runtime-JAR inspection, aggregate coverage, artifact publication, and Codacy upload.
- Exact-head Codacy Static Code Analysis `93068205523`: success with zero issues; Diff Coverage `93068756565`: success with no coverable changed lines; Coverage Variation `93068756646`: success at 0.0% against the -1.0% target.
- Exact-head CodeRabbit status: success. Five valid process/documentation review findings were fixed: staging-route semantics, required workspace handoff fields, post-merge evidence requirements, ambiguous wording, and an end-to-end grammar finding. All five review threads are resolved; zero valid unresolved review threads remain.
- Automatically triggered Pi wrapper run `31243459998` is explicitly **NOT A PASS** and was genuinely non-applicable to the process-only PR under `VALIDATION-POLICY.md`: private run `31243462663` required hosted build job `93068076434` with runner ID `0`, empty runner name, and `steps: []` under the same Billing & plans rejection; Pi job `93068080486` skipped. No product, workflow, runner-label, dispatch, or migration change was under test, and no infrastructure exception was claimed.
- PR #90 merged normally as merge commit `25fee003bd94b605f18f71b54c014fb7b0547b94`.
- Verified merge parents: pre-merge `main` `41659389ba105e099c77966015714067ea6f1ae7` and frozen feature head `5c68df5b774625ae78edce3b71f86dbc9c47951c`.
- Exact containment verified: resulting `main` is one merge commit ahead of the frozen feature head, zero behind, with zero file differences.
- Definition branch cleanup verified: `package/es-r01-staging-bridge-recovery-definition` returns 404 after merge.
- Result after definition merge: `main` `25fee003bd94b605f18f71b54c014fb7b0547b94`; ES-R01 is legitimately `READY`; ES-P02 and ES-P05 remain parked until billing is repaired or ES-R01 implements the alternate route.

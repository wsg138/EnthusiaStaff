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

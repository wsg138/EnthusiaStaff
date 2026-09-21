# ES-T01 — Random staff-teleport stale-target hardening

## 1. Package identity
`ES-T01`; owner-authorized test-hardening repair; internal `COMP-STAFF`; priority 5; parallel-safe only while `paper/.../staff/**` remains disjoint from active package work.

## 2. Status
`PARKED_BLOCKED`.

Frozen executable/product-test candidate: `991315dbe3f90c3a46842ca63a8ae6a76a716572`. Shared staging-control remediation is validated separately on `c9310f02712d7364a9b84e7db3642fbb181cbb73`; later state-only documentation commits do not supersede either frozen validation identity.

## 3. Objective
Remove stale-target behavior from the random staff teleport tool before hands-on test-server validation begins.

## 4. Why the package exists
On 2026-09-20 the owner explicitly authorized creation of review/bug-finding/fixing packages to improve readiness for immediate plugin testing. Review found that random teleport validated targets during candidate collection but later teleported to a stored location without revalidating the selected target.

## 5. Included audit IDs
`T01-STAFF-RTP-001` — execution-time target revalidation and stale-location avoidance.

`T01-STAFF-RTP-002` — move candidate identity/state reads off the global scheduler and onto each target entity scheduler.

## 6. Included behavior
Enumerate only player references globally, then read candidate identity/state on each target entity scheduler; revalidate the selected target on its entity scheduler immediately before actor teleport orchestration; reject targets that became staff-mode, vanished, frozen, exempt, dead, sleeping, mounted, spectator, or located in a disabled world; retry another candidate when a selected target becomes invalid/offline; capture the live location only after successful revalidation; preserve actor authorization revalidation and asynchronous teleport failure handling.

## 7. Explicit exclusions
No X03/D08/D09/D13 product files, migrations, Discord behavior, provider integration, staff-mode persistence redesign, production deployment, authority change, or cutover. Owner instruction on 2026-09-21 explicitly authorized repairing the recurring shared staging-authentication failure on this existing package/branch rather than merely rotating another expiring PAT.

## 8. Dependencies
`ES-P04` is `COMPLETE`. Owner authorization on 2026-09-20 permits this independent test-hardening repair.

## 9. Component and repository boundaries
Only `wsg138/EnthusiaStaff`; product changes remain limited to `paper/.../staff/StaffToolRandomTeleportService.java` and focused staff-tool tests. The owner-authorized staging reliability repair additionally changes only trusted public staging workflows, one local composite authentication action, staging-control tests, and bridge documentation. No migration is added.

## 10. Required branches
`package/es-t01-staff-teleport-hardening` from exact start `c1054da6a8f89b312df2e05e25edc958fceda7ef`.

## 11. Required PRs
One normal-merge PR to `EnthusiaStaff:main`: PR #215.

## 12. Implementation checklist
- [x] Reconcile live `main`, open PRs, migrations, parked packages, and collision paths.
- [x] Implement execution-time target revalidation and fallback retry.
- [x] Move candidate identity/state reads onto each target entity scheduler.
- [x] Add focused success/rejection/retry/retirement/reconnect/authorization/teleport-failure coverage.
- [x] Run focused Java 21 tests and full applicable Paper tests.
- [x] Run changed-code complexity/method-length checks and `git diff --check`.
- [x] Clear the hosted Codacy finding without suppression.
- [x] Run exact-head Coverage and Sentinel Artifact validation on `991315dbe3f90c3a46842ca63a8ae6a76a716572`.
- [x] Replace the recurring cross-repository PAT design with a dedicated GitHub App installation-token flow in both canonical staging and stale-run supersession.
- [x] Pin `actions/create-github-app-token` to immutable commit `bcd2ba49218906704ab6c1aa796996da409d3eb1` (v3.2.0), scope tokens to only `EnthusiaStaff-Staging`, and request only Actions write permission.
- [x] Add staging-auth regression tests and exact-head actionlint/control-plane validation.
- [ ] Complete the one-time GitHub App account/repository configuration that cannot be performed through the available GitHub connector.
- [ ] Reconcile the then-current `main`, rerun invalidated gates, and obtain a successful canonical private staging result.
- [ ] Harsh-review final diff and resolve every valid finding.
- [ ] Merge normally only after every package-required gate is terminal green, then verify containment and publish `COMPLETE`.

## 13. Acceptance criteria
A target that was eligible during collection but is invalid at final selection is not teleported to; another candidate is tried when available; the selected target location is captured only after final revalidation; actor authorization is still checked before teleport; shared staging authentication no longer depends on a user PAT with an expiration/revocation lifecycle.

## 14. Test requirements
Focused `:paper:test` coverage including target-policy state changes; full applicable Java 21 repository validation on the exact final executable PR head; `git diff --check`; changed-code complexity/method-length checks; staging-control Python tests and actionlint for shared workflow changes. Sentinel and canonical Pi remain package-required under the reconciled validation policy for this runtime-sensitive change.

## 15. Static-analysis requirements
Zero new valid Codacy/PMD findings in changed code. No broad suppressions. Exact candidate `991315dbe3f90c3a46842ca63a8ae6a76a716572` has Codacy Static Code Analysis success with zero annotations/new issues; diff coverage is 78.9% and coverage variation is +0.18%.

## 16. Documentation requirements
Registry, workspace state, package handoff, bridge architecture documentation, PR evidence, and terminal state.

## 17. Security and privacy requirements
No private player data, production data, credentials, raw logs, or secrets. The dedicated staging GitHub App must be installed only on `wsg138/EnthusiaStaff-Staging`, request repository `Actions: Read and write` only, and expose only short-lived installation tokens to trusted bridge jobs. Its private key remains an Actions secret and is never committed or reported.

## 18. Migration impact
None. No schema change is introduced by T01 or the staging-auth repair.

## 19. Bedrock considerations
No protocol-specific UI change; target eligibility remains based on server-side player state and permissions for Java and Bedrock players alike.

## 20. Distributed-runtime considerations
All mutable target reads occur on the target entity scheduler; actor authorization and teleport start occur on the actor entity scheduler. Offline/retired targets fail closed into bounded fallback selection.

## 21. External-provider considerations
GitHub is the staging control provider. Cross-repository staging access now uses a dedicated GitHub App that mints per-job installation tokens instead of a personal access token.

## 22. Completion definition
One normal-merge PR; focused and full applicable tests green on exact executable head; zero valid unresolved review/static findings; staging control-plane validation green; canonical Pi private staging green after one-time App setup; merge containment verified; canonical state published.

## 23. Resume state
Branch: `package/es-t01-staff-teleport-hardening`. PR: #215. Start `main`: `c1054da6a8f89b312df2e05e25edc958fceda7ef`. Frozen executable/product-test candidate: `991315dbe3f90c3a46842ca63a8ae6a76a716572`. Durable staging-control validated head: `c9310f02712d7364a9b84e7db3642fbb181cbb73`.

## 24. Last completed checkpoint
Product stale-target and Folia defects are repaired. On `991315dbe3f90c3a46842ca63a8ae6a76a716572`, Coverage `35663450629` succeeded, Sentinel Restart Artifact `35663450669` succeeded, Codacy Static succeeded with zero annotations/new issues, Codacy Diff Coverage succeeded at 78.9%, and Coverage Variation succeeded at +0.18%.

The recurring staging credential failure was then repaired architecturally on the existing branch: `.github/actions/staging-app-token/action.yml` mints a short-lived token with pinned `actions/create-github-app-token`; both `pi-staging-check.yml` and `pi-staging-supersede.yml` use it; the legacy `secrets.ENTHUSIASTAFF_STAGING_TOKEN` reference is removed from those workflows; bridge preflight verifies private Actions access before publishing transfer state; and `scripts/staging/test_staging_app_auth.py` prevents regression to the legacy PAT path. Exact shared-control head `c9310f02712d7364a9b84e7db3642fbb181cbb73` passed Pi Staging Control Validation run `35666780923` / job `106554183248`, including Python/shell syntax, actionlint, 37 canonical control tests, 14 supersession tests, 1 command-permission test, and 3 staging-app-auth tests.

## 25. Remaining checklist
Perform the one-time GitHub-account configuration: create a dedicated staging GitHub App, grant only repository `Actions: Read and write`, install it only on `wsg138/EnthusiaStaff-Staging`, set public-repo variable `ENTHUSIASTAFF_STAGING_APP_CLIENT_ID`, and store one generated private key as public-repo Actions secret `ENTHUSIASTAFF_STAGING_APP_PRIVATE_KEY`. The available GitHub connector exposes repository/workflow writes but intentionally exposes no GitHub-App administration or Actions-secret mutation API, so this configuration cannot be completed from the worker session. After it exists, delete the obsolete `ENTHUSIASTAFF_STAGING_TOKEN` secret, reconcile current `main`, freeze the new branch head, rerun invalidated hosted/static/runtime gates, then run canonical Pi once and require actual private success plus transfer cleanup.

## 26. Known blocker
**One-time GitHub App configuration.** Repository code no longer requires the recurring PAT. The permanent flow is implemented and control-plane validated, but GitHub must have the App installation plus `ENTHUSIASTAFF_STAGING_APP_CLIENT_ID` and `ENTHUSIASTAFF_STAGING_APP_PRIVATE_KEY` configured before an installation token can be minted. This is an account/repository-secret administration prerequisite, not a product defect. Do not add another personal PAT as a workaround.

## 27. Evidence
- Executable/product-test candidate `991315dbe3f90c3a46842ca63a8ae6a76a716572`:
  - Coverage `35663450629`: `SUCCESS`.
  - Sentinel Restart Artifact `35663450669`: `SUCCESS`.
  - Codacy Static `106544003807`: `SUCCESS`, zero annotations/new issues.
  - Codacy Diff Coverage `106546409672`: `SUCCESS`, 78.9%.
  - Codacy Coverage Variation `106546409662`: `SUCCESS`, +0.18%.
- Durable staging-control head `c9310f02712d7364a9b84e7db3642fbb181cbb73`:
  - Pi Staging Control Validation `35666780923` / job `106554183248`: `SUCCESS`.
  - actionlint: PASS.
  - staging control tests: 37 + 14 + 1 + 3 PASS.
- Historical canonical Pi `35641111973`: failed before private dispatch due legacy PAT `HTTP 401: Bad credentials`; not a product/runtime result and not a pass.

## 28. Merge and synchronization record
`BLOCKED` only on the one-time GitHub App account/secret configuration plus the required post-configuration current-main reconciliation and final canonical staging proof. PR #215 remains open and must not merge until those are green. No replacement branch/package/PR was created.

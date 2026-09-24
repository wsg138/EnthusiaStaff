# ES-T01 — Random staff-teleport stale-target hardening

## 1. Package identity
`ES-T01`; owner-authorized test-hardening repair; internal `COMP-STAFF`; priority 5; parallel-safe only while `paper/.../staff/**` remains disjoint from active package work.

## 2. Status
`PARKED_BLOCKED`.

Frozen executable/product-test candidate: `991315dbe3f90c3a46842ca63a8ae6a76a716572`.

Frozen clean staging-auth/control tree: `72ee6f52886eb427e153c143365e573f2988c6f8`. This tree contains the permanent GitHub App staging-auth implementation and no temporary diagnostic/preflight workflow. Later package-state documentation commits do not supersede either frozen validation identity.

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
No X03/D08/D09/D13 product files, migrations, Discord behavior, provider integration, staff-mode persistence redesign, production deployment, authority change, or cutover. Owner instruction on 2026-09-21 explicitly authorized repairing the recurring shared staging-authentication failure on this existing package/branch rather than rotating another expiring PAT.

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
- [x] Clear the original hosted Codacy Performance finding without suppression.
- [x] Run exact-head Coverage and Sentinel Artifact validation for the product candidate.
- [x] Replace the recurring cross-repository PAT design with a dedicated GitHub App installation-token flow in both canonical staging and stale-run supersession.
- [x] Pin `actions/create-github-app-token` to immutable commit `bcd2ba49218906704ab6c1aa796996da409d3eb1` (v3.2.0), scope tokens to only `EnthusiaStaff-Staging`, and request only Actions write permission.
- [x] Add staging-auth regression tests and exact-head actionlint/control-plane validation.
- [x] Reconfirm hosted Codacy at zero new issues after the App-auth work and remove all temporary diagnostic workflows.
- [x] Run a non-canonical branch preflight to determine the live App-bootstrap state without consuming canonical Pi.
- [ ] Complete the one-time GitHub App account/repository configuration that cannot be performed through the available GitHub connector.
- [ ] Reconcile the then-current `main`, rerun invalidated gates, and obtain a successful canonical private staging result.
- [ ] Harsh-review final diff and resolve every valid finding.
- [ ] Merge normally only after every package-required gate is terminal green, then verify containment and publish `COMPLETE`.

## 13. Acceptance criteria
A target that was eligible during collection but is invalid at final selection is not teleported to; another candidate is tried when available; the selected target location is captured only after final revalidation; actor authorization is still checked before teleport; shared staging authentication no longer depends on a user PAT with an expiration/revocation lifecycle.

## 14. Test requirements
Focused `:paper:test` coverage including target-policy state changes; full applicable Java 21 repository validation on the exact final executable PR head; `git diff --check`; changed-code complexity/method-length checks; staging-control Python tests and actionlint for shared workflow changes. Sentinel and canonical Pi remain package-required under the reconciled validation policy for this runtime-sensitive change.

## 15. Static-analysis requirements
Zero new valid Codacy/PMD findings in changed code. No broad suppressions. Product candidate `991315dbe3f90c3a46842ca63a8ae6a76a716572` passed Codacy Static with zero new issues. After the App-auth changes, clean-tree head `255922568d1a94743d4bb5fc8ce9cf4317c5c3fa` passed hosted Codacy check `106579459965` with `Your pull request is up to standards!` and zero annotations. GitHub reports zero file differences from that tree through frozen clean staging-auth/control tree `72ee6f52886eb427e153c143365e573f2988c6f8`.

A transient hosted Performance result observed during temporary diagnostic churn did not reproduce on the restored identical tree. Repository-token Codacy Cloud analysis found no Performance finding from locally supported analyzers; no suppression or source workaround was added. The final hosted check is authoritative and green.

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
Branch: `package/es-t01-staff-teleport-hardening`. PR: #215. Start `main`: `c1054da6a8f89b312df2e05e25edc958fceda7ef`. Frozen executable/product-test candidate: `991315dbe3f90c3a46842ca63a8ae6a76a716572`. Frozen clean staging-auth/control tree: `72ee6f52886eb427e153c143365e573f2988c6f8`.

Last observed live `main` before this state checkpoint: `4ffab626f4f044ef03adcf2d41c25690e1bdaa71`. Re-fetch immediately before reconciliation because `main` may advance while this package is parked.

## 24. Last completed checkpoint
Product stale-target and Folia defects remain repaired. On `991315dbe3f90c3a46842ca63a8ae6a76a716572`, Coverage `35663450629` succeeded, Sentinel Restart Artifact `35663450669` succeeded, Codacy Static succeeded with zero annotations/new issues, Codacy Diff Coverage succeeded at 78.9%, and Coverage Variation succeeded at +0.18%.

The recurring staging credential design is repaired on the frozen clean control tree. `.github/actions/staging-app-token/action.yml` mints a short-lived token with pinned `actions/create-github-app-token`; both `pi-staging-check.yml` and `pi-staging-supersede.yml` use it; the legacy `secrets.ENTHUSIASTAFF_STAGING_TOKEN` reference is removed from those branch workflows; bridge preflight verifies private Actions access before publishing transfer state; and `scripts/staging/test_staging_app_auth.py` prevents regression to the legacy PAT path.

Equivalent clean-tree evidence after the auth repair:
- Coverage `35669089569`: `SUCCESS` on `760fa5d0dfd0c81cbbec18ee5a160b53e5a57372`.
- Pi Staging Control Validation `35674852052`: `SUCCESS` on `255922568d1a94743d4bb5fc8ce9cf4317c5c3fa`.
- Sentinel Restart Artifact `35674852068`: `SUCCESS` on `255922568d1a94743d4bb5fc8ce9cf4317c5c3fa`.
- Codacy Static `106579459965`: `SUCCESS`, zero issues/annotations on `255922568d1a94743d4bb5fc8ce9cf4317c5c3fa`.
- GitHub compare reports zero file differences `760fa5d... -> 255922568...` and zero file differences `255922568... -> 72ee6f528...`; therefore those hosted results cover the same clean repository tree after temporary diagnostic/preflight removal.
- The later Coverage attempt `35674852123` was cancelled by subsequent same-tree pushes; it is not a test failure and does not justify rerunning an unchanged tree merely for activity.

## 25. Remaining checklist
Complete the one-time GitHub-account/repository configuration for the dedicated staging App. A branch-only preflight run `35675037190` / job `106579643419` exercised the real pinned token action and failed before token minting with: `The 'client-id' (or deprecated 'app-id') input must be set to a non-empty string.` This proves repository Actions variable `ENTHUSIASTAFF_STAGING_APP_CLIENT_ID` is currently missing or empty in the workflow context.

The preflight stopped before the private key and App installation could be exercised, so do not assume those remaining bootstrap pieces are valid yet. Required one-time setup remains:
1. Create the dedicated staging GitHub App under `wsg138` if it does not already exist.
2. Grant repository permission `Actions: Read and write` only.
3. Install it only on `wsg138/EnthusiaStaff-Staging`.
4. Set public-repo Actions variable `ENTHUSIASTAFF_STAGING_APP_CLIENT_ID` to the App client ID.
5. Store one generated App private-key PEM as public-repo Actions secret `ENTHUSIASTAFF_STAGING_APP_PRIVATE_KEY`.
6. Prove branch App-token minting/private Actions read succeeds; then reconcile current `main`, freeze the new final candidate, rerun invalidated gates, and run canonical Pi exactly once.
7. After App-backed canonical and supersession paths pass, delete obsolete secret `ENTHUSIASTAFF_STAGING_TOKEN`.

The available GitHub connector exposes repository/workflow writes but intentionally exposes no GitHub-App administration or Actions-secret mutation API. Do not substitute another personal PAT.

## 26. Known blocker
**One-time GitHub App bootstrap — first confirmed missing prerequisite: `ENTHUSIASTAFF_STAGING_APP_CLIENT_ID`.** Repository code no longer requires the recurring PAT. The permanent App-token flow is implemented, statically clean, and control-plane validated, but the live preflight proves the client-ID variable is absent/empty. The private-key secret and staging-repository App installation were not reached by that preflight and therefore remain unverified external prerequisites.

Do not run canonical Pi, merge `main`, or merge PR #215 until App-token minting and private staging access are operational.

## 27. Evidence
- Executable/product-test candidate `991315dbe3f90c3a46842ca63a8ae6a76a716572`:
  - Coverage `35663450629`: `SUCCESS`.
  - Sentinel Restart Artifact `35663450669`: `SUCCESS`.
  - Codacy Static `106544003807`: `SUCCESS`, zero annotations/new issues.
  - Codacy Diff Coverage `106546409672`: `SUCCESS`, 78.9%.
  - Codacy Coverage Variation `106546409662`: `SUCCESS`, +0.18%.
- Frozen equivalent clean staging-auth/control tree `72ee6f52886eb427e153c143365e573f2988c6f8`:
  - Coverage `35669089569`: `SUCCESS` on identical tree ancestor `760fa5d...`.
  - Pi Staging Control Validation `35674852052`: `SUCCESS` on identical tree ancestor `255922568...`.
  - Sentinel Restart Artifact `35674852068`: `SUCCESS` on identical tree ancestor `255922568...`.
  - Codacy Static `106579459965`: `SUCCESS`, zero issues/annotations on identical tree ancestor `255922568...`.
  - Zero-file GitHub comparisons prove the tree equivalence after diagnostic/preflight lifecycle cleanup.
- GitHub App bootstrap preflight `35675037190` / job `106579643419`:
  - token action invoked successfully as branch code;
  - token mint failed because `client-id` input was empty;
  - private staging API verification was skipped;
  - private-key/install validity therefore remains untested.
- Historical canonical Pi `35641111973`: failed before private dispatch due legacy PAT `HTTP 401: Bad credentials`; not a product/runtime result and not a pass.

## 28. Merge and synchronization record
`BLOCKED` only on the one-time GitHub App account/repository configuration plus the required post-configuration current-main reconciliation and final canonical staging proof. PR #215 remains open and must not merge until those are green. No replacement branch/package/PR was created. No canonical Pi run was consumed after the App-auth redesign because the bootstrap prerequisite is not yet operational.

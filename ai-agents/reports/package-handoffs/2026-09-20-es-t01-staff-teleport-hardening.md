# ES-T01 — staff teleport hardening handoff

Status: `PARKED_BLOCKED`.

Owner authorization: 2026-09-20 request to create review/bug-finding/fixing work useful for immediate plugin testing, followed on 2026-09-21 by explicit direction to permanently fix the recurring staging credential failure rather than rotate another expiring token.

Start `main`: `c1054da6a8f89b312df2e05e25edc958fceda7ef`.
Branch: `package/es-t01-staff-teleport-hardening`.
PR: #215.
Frozen executable/product-test candidate: `991315dbe3f90c3a46842ca63a8ae6a76a716572`.
Durable staging-control validated head: `c9310f02712d7364a9b84e7db3642fbb181cbb73`.

The production implementation remains unchanged from `12bfd267a2d9d2dd3709060300efd6d2bc3a220b`. Product repair stores candidate IDs, re-resolves/revalidates the selected target on its entity scheduler, retries invalid/offline candidates, captures location only after final eligibility succeeds, and keeps candidate mutable-state reads on each target entity scheduler with single-settlement guards.

Regression coverage exercises stale/invalid targets, reconnect identity, scheduler rejection/throw/retirement/duplicate callbacks, actor authorization revalidation, retry exhaustion, and teleport failure/cancellation paths.

Codacy blocker: **CLEARED**. The former `MEDIUM Performance` finding was fixed in test code without suppression by removing loop-local `Harness` allocations. On `991315d...`, Codacy Static `106544003807` is `SUCCESS` with zero annotations/new issues, Diff Coverage `106546409672` is `SUCCESS` at `78.9%`, Coverage Variation `106546409662` is `SUCCESS` at `+0.18%`, Coverage run `35663450629` is `SUCCESS`, and Sentinel Restart Artifact `35663450669` is `SUCCESS`.

The previous canonical Pi failures were authentication failures before private dispatch, not runtime failures: ES-T01 run `35641111973` and later supersession run `35663448866` both received `HTTP 401: Bad credentials` from `wsg138/EnthusiaStaff-Staging` while using the old `STAGING_TOKEN` PAT path.

That recurring design has now been removed from the branch. Shared action `.github/actions/staging-app-token/action.yml` pins `actions/create-github-app-token` at `bcd2ba49218906704ab6c1aa796996da409d3eb1` (v3.2.0), scopes minted tokens to only `wsg138/EnthusiaStaff-Staging`, and requests only `Actions: write`. Both canonical Pi staging and stale-run supersession use that action. The old `secrets.ENTHUSIASTAFF_STAGING_TOKEN` reference is absent from both workflows. The canonical bridge verifies private Actions access immediately after token minting and before publishing the transient transfer.

Regression script `scripts/staging/test_staging_app_auth.py` locks in the new authentication contract: no legacy PAT reference, expected GitHub App variable/secret names, pinned token-minting action, staging-only repository scope, and least-privilege Actions permission. Exact staging-control head `c9310f...` passed Pi Staging Control Validation `35666780923` / job `106554183248`: Python/shell syntax PASS, actionlint PASS, 37 canonical control tests PASS, 14 supersession tests PASS, 1 command-permission test PASS, and 3 staging-app-auth tests PASS.

One external setup step remains because the GitHub connector available to this worker can modify repository contents/workflows and run Actions but exposes no GitHub-App administration or Actions-secret mutation API. The owner must perform the one-time GitHub account setup:

1. Create a dedicated staging GitHub App under `wsg138`.
2. Give it repository permission `Actions: Read and write` only.
3. Install it only on `wsg138/EnthusiaStaff-Staging`.
4. In `wsg138/EnthusiaStaff`, set Actions repository variable `ENTHUSIASTAFF_STAGING_APP_CLIENT_ID` to the App client ID.
5. Store one generated App private key PEM as Actions repository secret `ENTHUSIASTAFF_STAGING_APP_PRIVATE_KEY`.
6. After the App-backed canonical and supersession paths pass, delete obsolete secret `ENTHUSIASTAFF_STAGING_TOKEN`.

This is a one-time setup rather than a recurring PAT rotation. Each workflow run will mint its own short-lived installation token and the token action revokes it at job teardown.

After App setup, reconcile the then-current `main` into ES-T01 with a normal merge, recheck collisions, freeze the resulting candidate, rerun every invalidated hosted/static/runtime gate, then run canonical Pi exactly once and require actual private staging success plus cleanup. Only then may PR #215 merge normally and publish `COMPLETE`.

No replacement branch/package/PR was created; no issue #216 finding was selected; no direct-main push, rebase, squash, force-push, or auto-merge is permitted.

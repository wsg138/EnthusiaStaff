# ES-T01 — staff teleport hardening handoff

Status: `PARKED_BLOCKED`.

Owner authorization: 2026-09-20 request to create review/bug-finding/fixing work useful for immediate plugin testing, followed on 2026-09-21 by explicit direction to permanently fix the recurring staging credential failure rather than rotate another expiring token.

Start `main`: `c1054da6a8f89b312df2e05e25edc958fceda7ef`.
Branch: `package/es-t01-staff-teleport-hardening`.
PR: #215.
Frozen executable/product-test candidate: `991315dbe3f90c3a46842ca63a8ae6a76a716572`.
Frozen clean staging-auth/control tree: `72ee6f52886eb427e153c143365e573f2988c6f8`.

The production implementation remains unchanged from `12bfd267a2d9d2dd3709060300efd6d2bc3a220b`. Product repair stores candidate IDs, re-resolves/revalidates the selected target on its entity scheduler, retries invalid/offline candidates, captures location only after final eligibility succeeds, and keeps candidate mutable-state reads on each target entity scheduler with single-settlement guards.

Regression coverage exercises stale/invalid targets, reconnect identity, scheduler rejection/throw/retirement/duplicate callbacks, actor authorization revalidation, retry exhaustion, and teleport failure/cancellation paths.

Product/static evidence remains green. On product candidate `991315d...`, Coverage `35663450629`, Sentinel Restart Artifact `35663450669`, Codacy Static `106544003807`, Codacy Diff Coverage `106546409672` (78.9%), and Coverage Variation `106546409662` (+0.18%) all succeeded.

The recurring staging PAT design has been replaced by a dedicated GitHub App flow. Shared action `.github/actions/staging-app-token/action.yml` pins `actions/create-github-app-token` at `bcd2ba49218906704ab6c1aa796996da409d3eb1` (v3.2.0), scopes minted tokens to only `wsg138/EnthusiaStaff-Staging`, and requests only `Actions: write`. Both canonical Pi staging and stale-run supersession use that action. The old `secrets.ENTHUSIASTAFF_STAGING_TOKEN` reference is absent from those branch workflows. The canonical bridge verifies private Actions access after token minting and before publishing transfer state.

Current clean-tree evidence after the App-auth repair:
- Coverage `35669089569`: `SUCCESS` on `760fa5d0dfd0c81cbbec18ee5a160b53e5a57372`.
- Pi Staging Control Validation `35674852052`: `SUCCESS` on `255922568d1a94743d4bb5fc8ce9cf4317c5c3fa`.
- Sentinel Restart Artifact `35674852068`: `SUCCESS` on `255922568d1a94743d4bb5fc8ce9cf4317c5c3fa`.
- Codacy Static `106579459965`: `SUCCESS`, `Codacy found no issues in your code`, zero annotations on `255922568...`.
- GitHub reports zero file differences `760fa5d... -> 255922568...` and zero file differences `255922568... -> 72ee6f528...`, so these runs cover the same restored clean implementation/auth tree. The later Coverage run `35674852123` was cancelled only because subsequent same-tree pushes superseded it.

Codacy triage is closed. Temporary diagnostics reproduced and removed misleading hardcoded-secret test-constant names without suppression. A later transient hosted Performance item did not reproduce in exact Cloud-config analyzer output and cleared on the restored identical tree; hosted Codacy is now zero-issue. All temporary Codacy/PMD/preflight workflows were removed from the frozen tree.

The previous canonical Pi failures were authentication failures before private dispatch, not runtime failures: ES-T01 run `35641111973` and later supersession runs used the old PAT path and received `HTTP 401: Bad credentials` from `wsg138/EnthusiaStaff-Staging`.

A branch-only App bootstrap preflight was run without consuming canonical Pi. Run `35675037190`, job `106579643419`, invoked the real pinned `actions/create-github-app-token` path and failed during token minting with:

`The 'client-id' (or deprecated 'app-id') input must be set to a non-empty string. If using a secret or variable, ensure it is available in this workflow context.`

Therefore `ENTHUSIASTAFF_STAGING_APP_CLIENT_ID` is currently missing or empty in the `EnthusiaStaff` Actions variable context. The failure occurred before the private-key input and GitHub App installation could be exercised, so those bootstrap pieces remain unverified rather than proven good or bad.

One external setup step remains because the GitHub connector available to this worker can modify repository contents/workflows and run Actions but exposes no GitHub-App administration or Actions-secret mutation API. Required one-time setup:

1. Create the dedicated staging GitHub App under `wsg138` if it does not already exist.
2. Give it repository permission `Actions: Read and write` only.
3. Install it only on `wsg138/EnthusiaStaff-Staging`.
4. In `wsg138/EnthusiaStaff`, set Actions repository variable `ENTHUSIASTAFF_STAGING_APP_CLIENT_ID` to the App client ID.
5. Store one generated App private-key PEM as Actions repository secret `ENTHUSIASTAFF_STAGING_APP_PRIVATE_KEY`.
6. Prove branch App-token minting plus a private staging Actions read succeeds.
7. After the App-backed canonical and supersession paths pass, delete obsolete secret `ENTHUSIASTAFF_STAGING_TOKEN`.

Do not substitute another personal PAT. Do not share the App private key in chat.

After App setup is operational, re-fetch current `main`, reconcile it into ES-T01 with a normal merge, recheck open-PR/path collisions, freeze the resulting candidate, rerun every invalidated hosted/static/runtime gate, then run canonical Pi exactly once and require actual private staging success plus transfer cleanup. Only then may PR #215 merge normally and publish `COMPLETE`.

Last observed `main` before this handoff checkpoint: `4ffab626f4f044ef03adcf2d41c25690e1bdaa71`; re-fetch because it may advance. An old parked X03/#139 branch overlaps `StaffToolRandomTeleportService.java`; its stale scheduler-helper hunk must not overwrite T01's stronger single-settlement/revalidation implementation during future reconciliation.

No replacement branch/package/PR was created; no issue #216 finding was selected; no direct-main push, rebase, squash, force-push, or auto-merge is permitted. Canonical Pi has not been rerun since the App redesign because the bootstrap prerequisite is still incomplete.

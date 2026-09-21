# Pi staging bridge

The ES-R01 staging route keeps the two required validation classes separate: an ordinary GitHub-hosted Java build runs in the public `wsg138/EnthusiaStaff` repository, then the already-trusted self-hosted Raspberry Pi in `wsg138/EnthusiaStaff-Staging` performs the disposable Paper boot/restart test. The private repository no longer needs a GitHub-hosted `ubuntu-latest` job.

AI workers making staging or validation decisions must also follow [`ai-agents/STAGING-TEST-OPERATING-GUIDE.md`](../ai-agents/STAGING-TEST-OPERATING-GUIDE.md). This page remains the bridge architecture/detail reference; the AI guide owns operational phase classification, Sentinel-versus-canonical-staging distinction, commands, and evidence rules.

## Trust boundary

The public build job receives no private staging credential. It checks out a trusted copy of the staging-control scripts at the workflow control SHA, independently authorizes the requested source as either a commit already contained by `main` or the exact current head of an open same-repository pull request, then checks out that exact source SHA detached and runs the normal Java 21 Gradle build.

Fork pull requests never reach private staging authentication or the Pi execution path. A later public bridge job, which does not execute source-controlled build code, mints a short-lived GitHub App installation token only after the trusted hosted build succeeds. The installation token is scoped to `wsg138/EnthusiaStaff-Staging` and requests only `Actions: write`; GitHub metadata read access is implicit. The token is used only to dispatch, locate, observe, and diagnose the correlated private staging workflow, and the pinned `actions/create-github-app-token` action revokes it at job teardown.

The private Pi workflow does not trust the dispatch payload by itself. Before Paper is allowed to boot it re-queries public GitHub metadata and verifies all of the following:

- exact public repository, workflow path, run ID, run attempt, and workflow/control SHA;
- successful public hosted build job;
- for PR sources, the PR is still open, unmerged, targets `main`, is from `wsg138/EnthusiaStaff`, and still has the exact staged head SHA/ref;
- exact transient release ID/tag and asset ID/name on `wsg138/EnthusiaStaff`;
- release publication time (`published_at`) and release-asset upload time (`created_at`) are each within the two-hour bridge window, including the existing future-clock-skew guard;
- canonical GitHub release download URL and bounded transfer size;
- transfer SHA-256 supplied by the trusted bridge job;
- an exact three-file archive allowlist: one bounded Paper runtime JAR, `SHA256SUMS`, and `manifest.json`;
- runtime checksum, size, source SHA, build run, build attempt, and workflow SHA against the schema-v2 manifest.

The release object's `created_at` field is deliberately not used as transport freshness. GitHub defines that field from the commit used for the release, so a newly published transient release may have an old `created_at` whenever its target commit is old. The verifier instead requires a valid recent `published_at` for release publication and independently requires a recent asset `created_at` for the upload itself. The two-hour transport boundary is unchanged.

Any mismatch fails closed before the disposable Paper harness receives database secrets or starts the server.

## Durable cross-repository authentication

The staging bridge intentionally does not use a personal access token as its normal cross-repository credential. Long-lived PATs can expire, be revoked, or inherit broader user permissions; that failure mode repeatedly produced `HTTP 401: Bad credentials` before private staging could start.

The durable setup uses a dedicated GitHub App and two public-repository configuration values:

- repository variable `ENTHUSIASTAFF_STAGING_APP_CLIENT_ID` — the GitHub App client ID;
- repository secret `ENTHUSIASTAFF_STAGING_APP_PRIVATE_KEY` — one private key generated for that App.

The GitHub App should be dedicated to the staging bridge, installed only on `wsg138/EnthusiaStaff-Staging`, and granted repository permission `Actions: Read and write`. No Contents write, Issues, Pull requests, Administration, or user-level permission is required for the cross-repository token. Webhooks are not required.

Both `.github/workflows/pi-staging-check.yml` and `.github/workflows/pi-staging-supersede.yml` use the shared `.github/actions/staging-app-token/action.yml` control. That control pins `actions/create-github-app-token` to an immutable commit, scopes the requested installation token to only `EnthusiaStaff-Staging`, requests only `Actions: write`, masks the token, and exports it only for the current job. Each installation token lasts at most one hour and is revoked by the action when the job finishes; neither workflow depends on an expiring user PAT.

One-time setup:

1. Create a GitHub App dedicated to the EnthusiaStaff staging bridge under the `wsg138` account.
2. Give it repository permission `Actions: Read and write`; leave unrelated repository/account permissions unset.
3. Install it only on `wsg138/EnthusiaStaff-Staging`.
4. In `wsg138/EnthusiaStaff` repository Actions configuration, create variable `ENTHUSIASTAFF_STAGING_APP_CLIENT_ID` with the App client ID.
5. Generate one App private key and store the PEM value as repository Actions secret `ENTHUSIASTAFF_STAGING_APP_PRIVATE_KEY`.
6. Run canonical staging and stale-run supersession once. After both prove GitHub App authentication works, delete the obsolete `ENTHUSIASTAFF_STAGING_TOKEN` PAT secret rather than keeping two credential paths.

The GitHub App private key is not a user PAT and does not participate in the PAT expiration lifecycle that caused the recurring 401s. If the private key is deliberately revoked or rotated, replace only `ENTHUSIASTAFF_STAGING_APP_PRIVATE_KEY`; workflows continue minting fresh least-privilege installation tokens automatically. Keep at least one valid App private key configured, and rotate it deliberately rather than on every staging run.

## Artifact handoff and retention

The hosted build first uploads the verified runtime package as a normal same-run Actions artifact with a two-day retention. The privileged bridge job downloads that artifact, rechecks the checksum, and publishes the three files in a bounded ZIP as a temporary GitHub prerelease asset in the already-public EnthusiaStaff repository. The release tag is `es-r01-staging-<run-id>-<attempt>` and the asset name includes the source SHA prefix plus the same run identity.

The public workflow deletes the transient release and tag after the correlated private run finishes. Cleanup is part of the public verdict: a staging run is not reported as successful if private validation fails, times out, cannot be correlated, or if transient transfer cleanup fails. Private sanitized Pi evidence remains a normal staging Actions artifact for 30 days; it must not contain credentials, database contents, or other private runtime material.

## Failure handling

A failed, skipped, cancelled, missing, expired, mismatched, or unallocated validation step is not a pass. For a failure:

1. Use the public run ID and the exact private run URL recorded in the public job summary to correlate both sides.
2. If the public hosted build failed, repair the source/build or staging-control issue and rerun the exact current head. Do not reuse an older artifact.
3. If GitHub App authentication fails before private dispatch, verify `ENTHUSIASTAFF_STAGING_APP_CLIENT_ID`, `ENTHUSIASTAFF_STAGING_APP_PRIVATE_KEY`, the App installation on `EnthusiaStaff-Staging`, and its `Actions: Read and write` permission. Do not substitute a personal token.
4. If private provenance verification failed, compare the requested source SHA, public workflow SHA/run/attempt, release/asset identity, release `published_at`, asset `created_at`, transport digest, and live PR head. Do not bypass the failed check.
5. If the Pi boot/restart harness failed after provenance verification, use only its sanitized evidence and repair the underlying staging/runtime problem.
6. If the transfer cleanup step failed, delete the identified transient ES-R01 prerelease/tag through the normal GitHub repository controls, then rerun. Never treat a successful Pi run with failed cleanup as package acceptance.
7. If the dedicated staging GitHub App or the `Lincoln-PI-4` runner is unavailable, record that exact operational prerequisite as the blocker instead of introducing an ad hoc credential or alternate runner.

Each rerun must stage the exact current package head. A later successful run does not retroactively validate a different SHA.

## Package resumption

ES-R01 repairs shared validation infrastructure only. It does not complete ES-P02 or ES-P05 on their behalf. After ES-R01 is merged and a current-`main` bridge proof succeeds, the canonical package registry should mark ES-R01 terminal and the next sequential worker must reconcile ES-P02 and ES-P05 under the current priority and continuation rules. Each dependent package must produce its own exact-head evidence through the repaired bridge before merge.

## Scope boundaries

The bridge changes no product Java behavior, production data, deployment route, LiteBans authority, or Flyway migration. V18 remains the immutable migration ceiling for this package. Issue #43 remains outside ES-R01.

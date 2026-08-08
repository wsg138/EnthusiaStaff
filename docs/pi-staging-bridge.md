# Pi staging bridge

The ES-R01 staging route keeps the two required validation classes separate: an ordinary GitHub-hosted Java build runs in the public `wsg138/EnthusiaStaff` repository, then the already-trusted self-hosted Raspberry Pi in `wsg138/EnthusiaStaff-Staging` performs the disposable Paper boot/restart test. The private repository no longer needs a GitHub-hosted `ubuntu-latest` job.

## Trust boundary

The public build job receives no private staging credential. It checks out a trusted copy of the staging-control scripts at the workflow control SHA, independently authorizes the requested source as either a commit already contained by `main` or the exact current head of an open same-repository pull request, then checks out that exact source SHA detached and runs the normal Java 21 Gradle build.

Fork pull requests never reach the private staging credential or Pi execution path. A later public bridge job, which does not execute source-controlled build code, may read `ENTHUSIASTAFF_STAGING_TOKEN` only after the trusted hosted build succeeds. That token is used only to dispatch and observe the private staging workflow.

The private Pi workflow does not trust the dispatch payload by itself. Before Paper is allowed to boot it re-queries public GitHub metadata and verifies all of the following:

- exact public repository, workflow path, run ID, run attempt, and workflow/control SHA;
- successful public hosted build job;
- for PR sources, the PR is still open, unmerged, targets `main`, is from `wsg138/EnthusiaStaff`, and still has the exact staged head SHA/ref;
- exact transient release ID/tag and asset ID/name on `wsg138/EnthusiaStaff`;
- release/asset age is within the two-hour bridge window;
- canonical GitHub release download URL and bounded transfer size;
- transfer SHA-256 supplied by the trusted bridge job;
- an exact three-file archive allowlist: one bounded Paper runtime JAR, `SHA256SUMS`, and `manifest.json`;
- runtime checksum, size, source SHA, build run, build attempt, and workflow SHA against the schema-v2 manifest.

Any mismatch fails closed before the disposable Paper harness receives database secrets or starts the server.

## Artifact handoff and retention

The hosted build first uploads the verified runtime package as a normal same-run Actions artifact with a two-day retention. The privileged bridge job downloads that artifact, rechecks the checksum, and publishes the three files in a bounded ZIP as a temporary GitHub prerelease asset in the already-public EnthusiaStaff repository. The release tag is `es-r01-staging-<run-id>-<attempt>` and the asset name includes the source SHA prefix plus the same run identity.

The public workflow deletes the transient release and tag after the correlated private run finishes. Cleanup is part of the public verdict: a staging run is not reported as successful if private validation fails, times out, cannot be correlated, or if transient transfer cleanup fails. Private sanitized Pi evidence remains a normal staging Actions artifact for 30 days; it must not contain credentials, database contents, or other private runtime material.

## Failure handling

A failed, skipped, cancelled, missing, expired, mismatched, or unallocated validation step is not a pass. For a failure:

1. Use the public run ID and the exact private run URL recorded in the public job summary to correlate both sides.
2. If the public hosted build failed, repair the source/build or staging-control issue and rerun the exact current head. Do not reuse an older artifact.
3. If private provenance verification failed, compare the requested source SHA, public workflow SHA/run/attempt, release/asset identity, transport digest, and live PR head. Do not bypass the failed check.
4. If the Pi boot/restart harness failed after provenance verification, use only its sanitized evidence and repair the underlying staging/runtime problem.
5. If the transfer cleanup step failed, delete the identified transient ES-R01 prerelease/tag through the normal GitHub repository controls, then rerun. Never treat a successful Pi run with failed cleanup as package acceptance.
6. If `ENTHUSIASTAFF_STAGING_TOKEN` or the `Lincoln-PI-4` runner is unavailable, record that exact operational prerequisite as the blocker instead of introducing a new credential or alternate runner ad hoc.

Each rerun must stage the exact current package head. A later successful run does not retroactively validate a different SHA.

## Package resumption

ES-R01 repairs shared validation infrastructure only. It does not complete ES-P02 or ES-P05 on their behalf. After ES-R01 is merged and a current-`main` bridge proof succeeds, the canonical package registry should mark ES-R01 terminal and make ES-P02 the highest-priority `ACTIONABLE_CONTINUATION`. ES-P02 must rerun its own exact-head gates through the repaired bridge; ES-P05 follows the canonical sequence and must likewise produce its own exact-head evidence.

## Scope boundaries

The bridge changes no product Java behavior, production data, deployment route, LiteBans authority, or Flyway migration. V18 remains the immutable migration ceiling for this package. Issue #43 remains outside ES-R01.

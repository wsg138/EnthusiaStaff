# Pi staging bridge

The ES-R01 staging route keeps the two required validation classes separate: an ordinary GitHub-hosted Java build runs in the public `wsg138/EnthusiaStaff` repository, then the already-trusted self-hosted Raspberry Pi in `wsg138/EnthusiaStaff-Staging` performs the disposable Paper boot/restart test. The private repository no longer needs a GitHub-hosted `ubuntu-latest` job.

AI workers making staging or validation decisions must also follow [`ai-agents/STAGING-TEST-OPERATING-GUIDE.md`](../ai-agents/STAGING-TEST-OPERATING-GUIDE.md). This page remains the bridge architecture/detail reference; the AI guide owns operational phase classification, Sentinel-versus-canonical-staging distinction, commands, and evidence rules.

## Trust boundary

The public build job receives no private staging credential. It checks out a trusted copy of the staging-control scripts at the workflow control SHA, independently authorizes the requested source as either a commit already contained by `main` or the exact current head of an open same-repository pull request, then checks out that exact source SHA detached and runs the normal Java 21 Gradle build.

Fork pull requests never reach the private staging credential or Pi execution path. A later public bridge job, which does not execute source-controlled build code, may read `ENTHUSIASTAFF_STAGING_TOKEN` only after the trusted hosted build succeeds. That token is used only to dispatch and observe the private staging workflow.

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

## Artifact handoff and retention

The hosted build first uploads the verified runtime package as a normal same-run Actions artifact with a two-day retention. The privileged bridge job downloads that artifact, rechecks the checksum, and publishes the three files in a bounded ZIP as a temporary GitHub prerelease asset in the already-public EnthusiaStaff repository. The release tag is `es-r01-staging-<run-id>-<attempt>` and the asset name includes the source SHA prefix plus the same run identity.

The public workflow deletes that **transient runtime-transfer** release and tag after the correlated private run finishes. Cleanup is part of the public verdict: a staging run is not reported as successful if private validation fails, times out, cannot be correlated, or if transient transfer cleanup fails.

The private sanitized Pi evidence has a different lifecycle. After the trusted `Lincoln-PI-4` harness has produced its sanitized evidence directory, the private workflow always packages that directory into a bounded ZIP, computes SHA-256, and publishes it as a **private GitHub prerelease asset in `wsg138/EnthusiaStaff-Staging`**. The evidence release tag is `pi-evidence-<private-run-id>-<attempt>` and the asset name is `enthusiastaff-pi-evidence-<exact-source-sha>-<private-run-id>-<attempt>.zip`. This is the canonical Pi evidence store; the private evidence path does not depend on GitHub Actions artifact storage or its quota.

A canonical private run fails closed if the evidence release cannot be created or its asset cannot be uploaded and validated. The evidence release is durable output and is not deleted by the public transient-transfer cleanup. It must contain only already-sanitized evidence; credentials, database contents, unsanitized logs, and other private runtime material remain prohibited.

These evidence release assets are never executable inputs. Sentinel and the public hosted build keep their own exact-SHA Actions-artifact contracts where those artifacts are used as executable/build inputs.

## Failure handling

A failed, skipped, cancelled, missing, expired, mismatched, or unallocated validation step is not a pass. For a failure:

1. Use the public run ID and the exact private run URL recorded in the public job summary to correlate both sides.
2. If the public hosted build failed, repair the source/build or staging-control issue and rerun the exact current head. Do not reuse an older artifact.
3. If private provenance verification failed, compare the requested source SHA, public workflow SHA/run/attempt, release/asset identity, release `published_at`, asset `created_at`, transport digest, and live PR head. Do not bypass the failed check.
4. If the Pi boot/restart harness failed after provenance verification, use only its sanitized evidence and repair the underlying staging/runtime problem.
5. If private evidence-release publication fails, classify the run as failed evidence retention even if the Paper runtime succeeded. Do not substitute an Actions artifact or claim the run passed without durable evidence.
6. If the public transfer cleanup step failed, delete the identified transient ES-R01 prerelease/tag through the normal GitHub repository controls, then rerun. Never treat a successful Pi run with failed cleanup as package acceptance.
7. If `ENTHUSIASTAFF_STAGING_TOKEN` or the `Lincoln-PI-4` runner is unavailable, record that exact operational prerequisite as the blocker instead of introducing a new credential or alternate runner ad hoc.

Each rerun must stage the exact current package head. A later successful run does not retroactively validate a different SHA.

## Package resumption

ES-R01 repairs shared validation infrastructure only. It does not complete dependent packages on their behalf. Each dependent package must produce its own exact-head evidence through the current repaired bridge before merge.

## Scope boundaries

The bridge changes no product Java behavior, production data, deployment route, LiteBans authority, or Flyway migration. Issue #43 remains outside the staging infrastructure boundary.

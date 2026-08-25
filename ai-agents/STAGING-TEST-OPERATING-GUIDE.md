# Staging test operating guide

This guide is the operational decision page for AI workers using Enthusia Sentinel or EnthusiaStaff canonical Pi staging. It does not replace package-specific validation contracts or `ai-agents/work-packages/VALIDATION-POLICY.md`.

For staging decisions, use this authority order:

1. live GitHub/Actions and the exact current source;
2. the selected package's validation contract;
3. `ai-agents/work-packages/VALIDATION-POLICY.md`;
4. this guide;
5. older handoffs and reports.

Live GitHub overrides stale reports. Never combine evidence from different source SHAs into one exact-head pass.

## The two systems are separate

### Sentinel

Sentinel is the separate on-demand PR testing system. Its production controller runs on `Lincoln-PI-4`, polls GitHub outbound through the Enthusia Sentinel GitHub App, validates an exact PR head plus manifest/artifact contract, and runs only the requested approved profile in a disposable sandbox.

A Sentinel pass proves only the profile Sentinel actually executed. In particular, `PAPER_RESTART_OK` proves Sentinel's disposable two-cycle Paper restart behavior. It does **not** prove the independent canonical MariaDB/Flyway Pi-staging contract.

### Canonical Pi staging

Canonical Pi staging is the EnthusiaStaff public-to-private validation bridge:

- public workflow: `wsg138/EnthusiaStaff` → `.github/workflows/pi-staging-check.yml` (`Pi Staging`);
- private execution workflow: `wsg138/EnthusiaStaff-Staging` → `.github/workflows/plugin-live-test.yml`.

**Do not dispatch `plugin-live-test.yml` directly as a normal package test.** The public workflow owns source selection, exact-SHA authorization, hosted build/test, runtime provenance, bounded transient transfer, private dispatch, public/private correlation, and public transfer cleanup.

The private workflow independently verifies the bridge, asserts the trusted `Lincoln-PI-4` identity, runs the guarded disposable MariaDB/Paper test, and durably stores only sanitized evidence.

A pass in one system does not substitute for a required pass in the other.

---

# A. Enthusia Sentinel manual testing

## Admission requirements

Before attempting a normal same-repository Sentinel test, verify on the **exact PR head**:

- repository is `wsg138/EnthusiaStaff`;
- PR is open and non-draft;
- ordinary `status`, `test ...`, and `cancel` requests use a same-repository head, not a fork;
- the immutable 40-character SHA being tested is the current PR head;
- `.enthusia-test.yml` exists at that exact SHA and validates against current Sentinel policy;
- the manifest declares the requested profile and artifact identity;
- a successful workflow run for that exact SHA produced the named executable artifact;
- the requester is authorized by current private Sentinel policy.

Do not assume a new branch inherits Sentinel readiness. Its exact SHA must contain the manifest and must already have the required exact-SHA artifact.

## Exact commands

Current Sentinel recognizes these exact pull-request command bodies:

```text
@enthusia-sentinel status
@enthusia-sentinel test startup
@enthusia-sentinel test restart
@enthusia-sentinel test restart-config
@enthusia-sentinel test reload-config
@enthusia-sentinel test database
@enthusia-sentinel test dependencies
@enthusia-sentinel test java-client
@enthusia-sentinel test java-interaction
@enthusia-sentinel cancel
@enthusia-sentinel approve-test startup
```

Do not add arguments, explanations, suffixes, or multiple lines. Reconcile the live parser before relying on this list if Sentinel changes.

Example restart command:

```bash
gh pr comment <PR_NUMBER> \
  --repo wsg138/EnthusiaStaff \
  --body '@enthusia-sentinel test restart'
```

## Sentinel result rules

A queued, rejected, stale, moved-head, unauthorized, missing-manifest, missing-artifact, wrong-artifact, cancelled, interrupted, timed-out, or otherwise non-terminal-success request is **not** a pass.

For restart, do not claim success unless the durable result is exactly:

```text
PAPER_RESTART_OK
```

Record the exact PR/head, command/comment identity, exact successful artifact-producing run, Sentinel job identity, terminal result, and applicable cleanup/reap evidence. Do not commit raw private reports or credentials merely to prove a result.

Sentinel executable inputs continue to use their exact-SHA GitHub Actions artifact contract. The private GitHub Release evidence mechanism described below is **not** an alternative executable-input path for Sentinel.

---

# B. EnthusiaStaff canonical Pi staging

## Canonical ownership

The public workflow is the only normal package entry point:

```text
wsg138/EnthusiaStaff/.github/workflows/pi-staging-check.yml
```

The private implementation workflow is:

```text
wsg138/EnthusiaStaff-Staging/.github/workflows/plugin-live-test.yml
```

The public workflow owns:

- source selection and exact-SHA authorization;
- hosted Java 21 build/test;
- runtime artifact/checksum/provenance generation;
- bounded transient public prerelease transfer;
- private dispatch and exact run correlation;
- terminal public result publication;
- deletion of the transient public transfer.

The private workflow owns:

- exact bridge/provenance re-verification before Paper boots;
- trusted `Lincoln-PI-4` runner assertion;
- guarded disposable staging-database reset;
- Paper cycle 1 startup/readiness, plugin enablement, MariaDB/Flyway checks, clean stop and reap;
- Paper cycle 2 restart/readiness, persistence assertions, clean stop and reap;
- final guarded cleanup;
- sanitized private evidence publication.

## Private evidence storage

Canonical Pi **sanitized evidence output is stored as a private GitHub prerelease asset in `wsg138/EnthusiaStaff-Staging` on every private run**. It no longer uses GitHub Actions artifact storage.

The deterministic identity is:

```text
release tag: pi-evidence-<PRIVATE_RUN_ID>-<PRIVATE_RUN_ATTEMPT>
asset name: enthusiastaff-pi-evidence-<EXACT_SOURCE_SHA>-<PRIVATE_RUN_ID>-<PRIVATE_RUN_ATTEMPT>.zip
```

The trusted private workflow:

1. produces the sanitized evidence directory;
2. accepts only bounded top-level regular files and rejects symlinks;
3. creates a bounded ZIP;
4. computes SHA-256;
5. creates the private prerelease and uploads the ZIP through GitHub's release API;
6. validates the returned release/asset IDs and hash identity;
7. fails closed if durable evidence publication does not succeed.

The release is evidence **output only**, never an executable input. Credentials, database contents, unsanitized logs, and other raw private runtime material are prohibited.

A successful Paper runtime step with failed evidence publication is **not** a canonical Pi pass.

The separate public runtime-transfer prerelease remains transient and is deleted by the public workflow after the private run. Do not confuse that transfer release with the durable private evidence release.

## Automatic and manual triggers

Current public workflow triggers include same-repository PR events, `push` to `main`, and manual `workflow_dispatch`. For a normal same-repository PR, prefer its automatic canonical run or the exact authorized staging command rather than manually dispatching the private workflow.

The exact PR command accepted by the public control plane is:

```text
@enthusia-staging test
```

Use it as the complete comment body. The command handler re-reads the live PR and dispatches the public workflow with the exact current head binding.

Do not launch duplicate runs for the same exact head while a canonical run is already active.

## Failure classification

Classify only what actually executed.

### 1. Public build/test failure

If the public hosted build fails, the Pi was **not tested**. Fix the hosted source/build/test failure first. Do not claim a private runtime result exists.

### 2. Bridge/provenance failure

If the public build succeeded but transfer, checksum/provenance, private dispatch, or public/private correlation failed, debug that bridge phase. Do not reuse an older artifact or bypass exact provenance.

### 3. Private prerequisite rejection

If the exact package reached the private workflow but runner identity, provenance, database safety, or another guard rejected it before Paper execution, record that exact prerequisite failure. Do not weaken the guard or substitute a production database.

### 4. Paper/runtime failure

If Paper actually executed and startup, plugin enablement, MariaDB/Flyway, shutdown/reap, restart/persistence, or the second cycle failed, that is real runtime evidence. Debug the phase that failed.

### 5. Private evidence-publication failure

If the runtime succeeds but the sanitized private GitHub Release cannot be created/uploaded/validated, canonical Pi is still **failed**. Do not substitute an Actions artifact or claim runtime success as a canonical pass.

### 6. Cleanup failure

Required cleanup is part of acceptance. Guarded database cleanup, process reap, sandbox cleanup, and transient public-transfer cleanup must satisfy the current harness. A cleanup failure is not a pass.

## Canonical pass evidence

Never write “Pi staging passed” merely because public dispatch or Paper startup occurred. Record at minimum:

- exact source SHA and PR number where applicable;
- public `Pi Staging` run ID/attempt and hosted build conclusion;
- public runtime artifact/provenance identity and digest;
- correlated private `plugin-live-test.yml` run ID/attempt and job ID;
- trusted `Lincoln-PI-4` identity;
- guarded pre-reset result;
- cycle 1 readiness/plugin/storage/Flyway result and clean stop/reap;
- cycle 2 readiness/persistence result and clean stop/reap;
- final guarded database/process/sandbox cleanup result;
- private evidence release ID/tag;
- private evidence asset ID/name/SHA-256;
- public transient-transfer cleanup result;
- terminal canonical public conclusion.

Do not put private evidence contents, credentials, database contents, or raw private logs into public documentation. Record only sanitized identities and bounded outcome facts.

## Useful inspection commands

List recent public canonical runs:

```bash
gh run list \
  --repo wsg138/EnthusiaStaff \
  --workflow pi-staging-check.yml \
  --limit 20
```

View one public run:

```bash
gh run view <PUBLIC_RUN_ID> --repo wsg138/EnthusiaStaff
```

The correlated private run title is deterministic:

```text
EnthusiaStaff bridge <PUBLIC_RUN_ID>-<PUBLIC_RUN_ATTEMPT> / <SOURCE_SHA>
```

Find it:

```bash
gh run list \
  --repo wsg138/EnthusiaStaff-Staging \
  --workflow plugin-live-test.yml \
  --event workflow_dispatch \
  --limit 50 \
  --json databaseId,displayTitle,status,conclusion \
  --jq '.[] | select(.displayTitle == "EnthusiaStaff bridge <PUBLIC_RUN_ID>-<PUBLIC_RUN_ATTEMPT> / <SOURCE_SHA>")'
```

Inspect the private run and resolve its attempt:

```bash
gh run view <PRIVATE_RUN_ID> --repo wsg138/EnthusiaStaff-Staging
PRIVATE_RUN_ATTEMPT="$(gh api \
  repos/wsg138/EnthusiaStaff-Staging/actions/runs/<PRIVATE_RUN_ID> \
  --jq '.run_attempt')"
```

Download the canonical private sanitized evidence release:

```bash
EVIDENCE_TAG="pi-evidence-<PRIVATE_RUN_ID>-${PRIVATE_RUN_ATTEMPT}"

gh release download "$EVIDENCE_TAG" \
  --repo wsg138/EnthusiaStaff-Staging \
  --pattern 'enthusiastaff-pi-evidence-*.zip' \
  --dir ./pi-staging-evidence
```

Treat downloaded evidence as private operational material. Do not commit or paste raw evidence into public GitHub or chat.

## Exact same-repository PR manual procedure

When a manual public workflow dispatch is genuinely required, collect all PR provenance from one live PR response and keep it bound together:

```bash
PR_NUMBER=<PR_NUMBER>
PR_JSON="$(gh api "repos/wsg138/EnthusiaStaff/pulls/$PR_NUMBER")"

PR_STATE="$(jq -r '.state' <<<"$PR_JSON")"
PR_DRAFT="$(jq -r '.draft' <<<"$PR_JSON")"
PR_BASE="$(jq -r '.base.ref' <<<"$PR_JSON")"
PR_REPOSITORY="$(jq -r '.head.repo.full_name' <<<"$PR_JSON")"
PR_REF="$(jq -r '.head.ref' <<<"$PR_JSON")"
PR_SHA="$(jq -r '.head.sha' <<<"$PR_JSON")"

[[ "$PR_STATE" == open ]]
[[ "$PR_DRAFT" == false ]]
[[ "$PR_BASE" == main ]]
[[ "$PR_REPOSITORY" == wsg138/EnthusiaStaff ]]

gh workflow run pi-staging-check.yml \
  --repo wsg138/EnthusiaStaff \
  --ref main \
  -f source_sha="$PR_SHA" \
  -f source_pr_number="$PR_NUMBER" \
  -f source_pr_head_repository="$PR_REPOSITORY" \
  -f source_pr_head_ref="$PR_REF" \
  -f source_pr_head_sha="$PR_SHA" \
  -f run_pi_test=true
```

A moved PR head requires a fresh provenance collection and a separate run.

## Current storage migration note

Historical canonical Pi runs may legitimately contain Actions-artifact evidence. Preserve those historical identities as recorded; do not rewrite past evidence.

The current private workflow contract uses private GitHub Release assets for new canonical Pi evidence. The X04 exact-head run `32797271342` / private run `32797866588` is important historical non-passing evidence: its runner/provenance/Paper runtime passed, but its old `actions/upload-artifact` evidence step hit the GitHub Actions artifact-storage quota, so the canonical result correctly remained `failure`. A newer run is required to prove the Release-based path on an exact package head.

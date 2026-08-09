# Staging test operating guide

This guide is the operational decision page for AI workers using Enthusia Sentinel or EnthusiaStaff canonical Pi staging. It does not replace package-specific validation contracts or `ai-agents/work-packages/VALIDATION-POLICY.md`.

For staging decisions, use this authority order:

1. live GitHub/Actions and the exact current source;
2. the selected package's validation contract;
3. `ai-agents/work-packages/VALIDATION-POLICY.md`;
4. this guide;
5. older handoffs and reports.

The **Current known staging state** section is advisory and time-sensitive. Reconcile live Actions before relying on it. The procedural distinction between Sentinel and canonical Pi staging is durable.

## The two systems are separate

### Sentinel overview

Sentinel is a separate, on-demand PR testing system. Its production controller runs on `Lincoln-PI-4`, polls GitHub outbound through the Enthusia Sentinel GitHub App, validates an exact PR head and manifest/artifact contract, and runs only the requested approved profile in a disposable sandbox.

A Sentinel pass proves only the profile Sentinel actually executed. In particular, `PAPER_RESTART_OK` proves Sentinel's disposable two-cycle Paper restart behavior. It does **not** automatically prove EnthusiaStaff's canonical MariaDB/Flyway Pi-staging contract.

### Canonical Pi staging overview

Canonical Pi staging is the EnthusiaStaff public-to-private validation bridge:

- public workflow: `wsg138/EnthusiaStaff` → `.github/workflows/pi-staging-check.yml` (`Pi Staging`);
- private execution workflow: `wsg138/EnthusiaStaff-Staging` → `.github/workflows/plugin-live-test.yml`.

**Do not dispatch `plugin-live-test.yml` directly as a normal package test.** The public `Pi Staging` workflow owns source selection, exact-SHA authorization, hosted build/test, artifact generation, checksum/provenance, bounded transient transfer, private dispatch, correlation, and cleanup.

A pass in Sentinel does not imply a canonical Pi staging pass, and a canonical Pi staging pass does not imply that every Sentinel profile has passed. Apply whichever independent gates the selected package and validation policy require.

---

# A. Enthusia Sentinel manual restart testing

## Current GitHub App onboarding assumptions

The current production Sentinel GitHub integration is outbound-only and GitHub-App-authenticated:

- App name: `Enthusia Sentinel`;
- private/owner-only App;
- webhooks disabled;
- OAuth/user authorization disabled;
- device flow disabled;
- installed only for `wsg138/EnthusiaStaff` for this operating path;
- repository permissions are Actions read, Checks read/write, Contents read, Issues read/write, Pull requests read/write, and Metadata read/implicit;
- the controller uses short-lived installation credentials created from Pi-local protected App material; workers must never copy App private material into Git, Actions secrets, chat, artifacts, logs, or Paper runtime;
- ordinary production Sentinel operation does not depend on a long-lived PAT or a public webhook listener.

Live Sentinel policy and implementation in `wsg138/EnthusiaStaff-Staging` override this summary if they change.

## Admission requirements

Before attempting a same-repository Sentinel test, verify all of the following on the **exact PR head**:

- the PR is in `wsg138/EnthusiaStaff`;
- the PR is open;
- the PR is non-draft;
- the PR head repository is the same repository, not a fork, for the ordinary `status`, `test ...`, and `cancel` path;
- the current head is the exact immutable 40-character SHA being tested;
- `.enthusia-test.yml` exists at that exact SHA and validates against current Sentinel policy;
- the manifest declares the requested profile and artifact identity;
- a successful workflow run for that exact SHA produced the named artifact required by the manifest;
- the requester is authorized by current private Sentinel policy.

Do not assume that a newly created branch inherits Sentinel readiness merely because another branch or `main` is onboarded. The branch's exact SHA must actually contain the required `.enthusia-test.yml` manifest and the artifact-producing contract, and the exact-SHA artifact must already exist successfully before the command can pass admission.

At the time this guide was introduced, PR #97 was the live same-repository restart-onboarding example, with `.enthusia-test.yml` and an exact-head `enthusiastaff-sentinel-paper` artifact workflow. Workers must still re-check the current branch and current `main` rather than relying on that historical example.

## Exact commands

Current live Sentinel source and its dedicated profile documentation recognize these eleven exact pull-request command bodies:

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

Do not add arguments, prefixes, suffixes, explanations, surrounding whitespace, or multiple lines to a command body. Ordinary comments are ignored. Do not invent additional Sentinel commands. Reconcile the live parser and profile documentation before relying on this list if Sentinel has changed since this guide was last updated.

### CLI usage

Use `gh pr comment` with the exact command as the complete body. For restart:

```bash
gh pr comment <PR_NUMBER> \
  --repo wsg138/EnthusiaStaff \
  --body '@enthusia-sentinel test restart'
```

Status:

```bash
gh pr comment <PR_NUMBER> \
  --repo wsg138/EnthusiaStaff \
  --body '@enthusia-sentinel status'
```

Cancel:

```bash
gh pr comment <PR_NUMBER> \
  --repo wsg138/EnthusiaStaff \
  --body '@enthusia-sentinel cancel'
```

The `--body` value must remain exactly the documented command. Do not append a note such as a SHA, reason, package ID, or worker name.

## Command meanings

- `status` — reads durable Sentinel state for the current same-repository PR and exact head. It does not enqueue or execute plugin code.
- `test startup` — validates the exact same-repository PR head, `.enthusia-test.yml`, policy, and exact-SHA successful artifact, then runs the approved one-cycle startup profile.
- `test restart` — runs exactly two sequential rootless Paper cycles in one disposable state. Cycle 1 must stop and be fully reaped before cycle 2 starts; cycle 2 must observe state created by cycle 1. Success is exactly `PAPER_RESTART_OK`.
- `test restart-config` — performs the two-cycle restart profile with declared exact-artifact config fixtures and bounded post-restart scalar assertions. Success is `PAPER_RESTART_CONFIG_OK`.
- `test reload-config` — starts one disposable Paper process, applies only declared atomic config edits, sends exactly one declared reload command, requires declared markers and post-reload assertions, then cleanly stops/reaps. Success is `PAPER_RELOAD_CONFIG_OK`.
- `test database` — runs the current bounded Sentinel database profile against exactly one declared SQLite fixture using fixed internal read-only checks over two stopped/reaped Paper cycles. It does not accept MariaDB URLs, credentials, manifest SQL, or arbitrary clients. Success is `PAPER_DATABASE_OK`.
- `test dependencies` — resolves only private locked-registry dependency coordinates, stages the target plus declared dependencies, requires enablement evidence in one rootless Paper cycle, then cleans up. Success is `PAPER_DEPENDENCIES_OK`.
- `test java-client` — runs the target with one credential-free synthetic offline Java client in the same disposable isolated environment and executes the bounded declared client-command sequence. Success is `JAVA_CLIENT_OK`.
- `test java-interaction` — runs exactly two fixed credential-free offline Java clients against one isolated loopback-only Paper server, preserving the admitted interleaved command order and proof-bound request/result chain. The live profile uses direct `JAVA_INTERACTION_*` terminal codes for setup, protocol, response, timeout, cancellation, exit, and cleanup outcomes; cleanup failure overrides success.
- `cancel` — requests cancellation of active jobs linked to the current same-repository PR and exact head while preserving durable cancellation and cleanup rules. It refuses unsafe shared-scope cancellation.
- `approve-test startup` — the only current fork admission path. It requires an authorized approver, a real fork of the trusted base repository, explicit private-policy permission, exact fork/PR/SHA/manifest/artifact binding, and execution-time revalidation. It admits only the `startup` profile. Ordinary `status` and `cancel` remain same-repository-only for this phase.

## Sentinel restart success and failure

A queued, rejected, stale, moved-head, unauthorized, missing-manifest, missing-artifact, wrong-artifact, cancelled, interrupted, or otherwise non-terminal-success request is **not** a pass.

For `test restart`, do not claim restart success unless the durable Sentinel result is exactly:

```text
PAPER_RESTART_OK
```

`PAPER_RESTART_OK` means Sentinel proved the disposable two-cycle Paper restart profile it owns. It does not prove canonical EnthusiaStaff MariaDB/Flyway staging.

## Sentinel evidence to record

For a Sentinel run used as package evidence, record enough live evidence to reconstruct exactly what was authorized and executed:

- repository and PR number;
- exact PR head SHA;
- PR state/non-draft/same-repository admission facts;
- exact command body and source comment identity;
- `.enthusia-test.yml` presence and requested profile at the exact head;
- exact successful artifact-producing workflow run and job;
- artifact name and exact source binding; checksum/digest when exposed by the current artifact contract;
- Sentinel durable queue/job identity;
- terminal Sentinel result code;
- for restart, cycle 1 readiness, clean stop/exit, complete reap, cycle 2 readiness, persistence/state observation, clean stop/exit, complete reap, and no process overlap;
- cancellation/timeout state if applicable;
- final sandbox/lease/download/process cleanup or residue result;
- GitHub result check/comment identity when available.

Do not commit raw private Sentinel reports or credentials merely to prove the result. Record sanitized identifiers and bounded outcome evidence.

---

# B. EnthusiaStaff Canonical Pi Staging

## Canonical workflow ownership

The public workflow is the only normal package entry point:

```text
wsg138/EnthusiaStaff
.github/workflows/pi-staging-check.yml
```

The private workflow is an implementation detail of the bridge:

```text
wsg138/EnthusiaStaff-Staging
.github/workflows/plugin-live-test.yml
```

**Do not dispatch `plugin-live-test.yml` directly as a normal package test.** A direct private dispatch bypasses the public workflow's source-selection and transfer lifecycle and therefore is not canonical package evidence unless a separate owner-directed infrastructure diagnostic explicitly says otherwise.

The public `Pi Staging` workflow owns:

- source selection;
- exact SHA authorization;
- public hosted Java 21 build/test;
- runtime artifact generation;
- runtime checksum and manifest/provenance;
- bounded transient transfer;
- private dispatch;
- exact public/private correlation;
- final public transfer cleanup.

The private workflow independently re-verifies provenance before Paper is allowed to boot, asserts the trusted `Lincoln-PI-4` runner identity, runs the guarded disposable database/Paper test, and uploads sanitized evidence.

## Automatic triggers

Current public workflow triggers are:

- `pull_request_target` for `opened`, `synchronize`, `reopened`, and `ready_for_review`;
- `push` to `main`;
- manual `workflow_dispatch`.

For a normal same-repository PR, prefer the automatic `pull_request_target` run. The workflow receives the exact PR head metadata from the event, authorizes that same-repository head, builds it publicly, and only after a successful hosted build may bridge it to private Pi staging.

Fork PRs stay outside the private staging credential/Pi path and receive only the public fork-boundary behavior plus ordinary public checks.

Do not launch a duplicate manual run while the automatic run for the same exact head is still executing.

## Failure phases: classify only what actually executed

### 1. Public build/test failure

If the **public hosted build** fails:

- **STOP.**
- The Pi was **not tested**.
- Do not debug the Pi based on that failure.
- Do not claim a private runtime artifact exists.
- Do not claim a correlated private Paper run exists.
- Fix the hosted product/build/test failure first.

The public workflow cannot bridge an artifact that was never successfully built and uploaded.

### 2. Bridge failure

The public artifact exists, but checksum/provenance, bounded transfer, private dispatch, correlation, or public-side transfer cleanup failed.

Debug the bridge phase that actually failed. Do not manually bypass provenance, reuse an expired transfer, substitute an older artifact, or dispatch the private workflow ad hoc to manufacture a pass.

### 3. Private prerequisite rejection

The exact artifact reached the private workflow, but a runner identity, provenance, database-safety, guarded pre-reset, or other prerequisite rejected it before Paper runtime execution.

Inspect the exact sanitized guard failure. Do not weaken the guard, change to a production database, broaden credentials, or allow Paper to boot before the prerequisite succeeds.

### 4. Paper runtime failure

The exact verified artifact passed private prerequisites and actually reached Paper execution, then Paper startup, plugin enablement, MariaDB/Flyway behavior, shutdown/reap, restart/persistence, or the second cycle failed.

This is real runtime evidence. Classify and debug the runtime phase that executed; do not relabel it as infrastructure-unavailable merely because the result is inconvenient.

### 5. Cleanup failure

Required cleanup is part of acceptance. Treat canonical staging as failed if required guarded database cleanup, process reap, sandbox cleanup, or transient public transfer cleanup fails, even when earlier functional assertions succeeded.

## Canonical evidence requirements

Never write “Pi staging passed” merely because the public workflow reached the private-dispatch stage. A canonical pass requires correlated private execution and every applicable runtime and cleanup assertion.

Record, at minimum:

- exact source SHA;
- source PR number where applicable;
- public `Pi Staging` run ID and attempt;
- public hosted build job ID and conclusion;
- public runtime artifact identity and checksum/digest/provenance manifest;
- correlated private `plugin-live-test.yml` run ID;
- private job ID;
- trusted `Lincoln-PI-4` runner identity;
- guarded pre-reset result;
- Paper cycle 1 startup/readiness result;
- EnthusiaStaff plugin enablement result;
- MariaDB/Flyway result where applicable;
- cycle 1 clean shutdown and complete process reap;
- Paper cycle 2 startup/readiness result;
- persistence/restart assertion result;
- cycle 2 clean shutdown and complete process reap;
- final guarded cleanup result;
- confirmation that no staging process/database/sandbox/transfer residue survived where the current harness asserts it;
- sanitized private evidence artifact ID and digest when available;
- public transient-transfer cleanup result.

Do not put credentials, database contents, private logs, raw private evidence, or production routes into GitHub documentation.

## Useful `gh` inspection commands

List recent public Pi Staging runs:

```bash
gh run list \
  --repo wsg138/EnthusiaStaff \
  --workflow pi-staging-check.yml \
  --limit 20
```

Narrow to automatic PR-triggered runs:

```bash
gh run list \
  --repo wsg138/EnthusiaStaff \
  --workflow pi-staging-check.yml \
  --event pull_request_target \
  --limit 30
```

View one public run and its jobs:

```bash
gh run view <PUBLIC_RUN_ID> --repo wsg138/EnthusiaStaff
```

View public logs:

```bash
gh run view <PUBLIC_RUN_ID> --repo wsg138/EnthusiaStaff --log
```

For a successful bridge dispatch, the private run title is deterministically:

```text
EnthusiaStaff bridge <PUBLIC_RUN_ID>-<PUBLIC_RUN_ATTEMPT> / <SOURCE_SHA>
```

Find the correlated private run:

```bash
gh run list \
  --repo wsg138/EnthusiaStaff-Staging \
  --workflow plugin-live-test.yml \
  --event workflow_dispatch \
  --limit 50 \
  --json databaseId,displayTitle,status,conclusion \
  --jq '.[] | select(.displayTitle == "EnthusiaStaff bridge <PUBLIC_RUN_ID>-<PUBLIC_RUN_ATTEMPT> / <SOURCE_SHA>")'
```

View private runtime logs:

```bash
gh run view <PRIVATE_RUN_ID> \
  --repo wsg138/EnthusiaStaff-Staging \
  --log
```

Download the sanitized private evidence artifact:

```bash
gh run download <PRIVATE_RUN_ID> \
  --repo wsg138/EnthusiaStaff-Staging \
  --pattern 'enthusiastaff-pi-evidence-*' \
  --dir ./pi-staging-evidence
```

Treat downloaded evidence as private operational material. Do not commit or paste raw evidence into public GitHub or chat; extract only sanitized outcome facts that policy permits.

## Manual reruns

Prefer the automatic same-repository PR run. Manually rerun only after a material product or infrastructure change justifies new evidence. Do not launch a duplicate manual run while an automatic run for the same exact head is still executing.

### Exact current-`main` manual run

Resolve `main` once, then dispatch that exact SHA through the public workflow:

```bash
MAIN_SHA="$(gh api repos/wsg138/EnthusiaStaff/commits/main --jq '.sha')"

gh workflow run pi-staging-check.yml \
  --repo wsg138/EnthusiaStaff \
  --ref main \
  -f source_sha="$MAIN_SHA" \
  -f run_pi_test=true
```

Do not replace `MAIN_SHA` with a different commit after collecting it. If `main` moves and the newer source must be tested, collect a fresh identity and create a separate new run.

### Exact same-repository PR provenance procedure

Collect all PR provenance from one live PR response before dispatching:

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

Use **all** exact PR provenance values. Never collect PR identity, then substitute another SHA, branch, repository, or PR number. A moved PR head requires a fresh provenance collection and a separate new run.

## Current known staging state

**Time-sensitive: reconcile current GitHub Actions before relying on this section. Live GitHub overrides it.**

Verified against live repository state while this guide was introduced on 2026-08-09:

- Sentinel itself is operational for the delivered manual restart path. The merged Sentinel acceptance record in `wsg138/EnthusiaStaff-Staging` records a real same-repository PR #97 restart at exact head `74e85da69900e8e5e820bf4645984f814d3ff334` ending in `PAPER_RESTART_OK`, followed by clean process/sandbox/lease cleanup.
- The previous canonical Pi staging MariaDB prerequisite was repaired on the guarded staging boundary. MariaDB is active for the dedicated loopback-only staging environment; no production database access is implied by that repair.
- Current `wsg138/EnthusiaStaff:main` at the time of verification is `3ce303ce3097be647091e142e801da9a5fd9a8fc`.
- The latest canonical public `Pi Staging` run for that exact source is run `31298080632`. Its public job `93206301028` (`Build trusted EnthusiaStaff Paper runtime`) failed in `Validate source, build, and package Paper runtime`.
- The runtime artifact upload was skipped and the bridge job `93206929848` was skipped. Therefore the Pi was **not tested** by that run and no private runtime result exists for it.
- Current live staging records identify two `ReportStoreIntegrationTest` failures in that public build:
  - `ReportStoreIntegrationTest.stateLifecycleEnforcesAssignmentRevisionAndQueues()` — expected lifecycle/queue membership was missing (`true` expected, `false` observed);
  - `ReportStoreIntegrationTest.duplicateSubmissionMergesEvidenceAndReplaysWithoutExtraRows()` — expected duplicate-report evidence rows were missing (`2` expected, `0` observed).
- Those symptoms are evidence, not a proven root cause. Plausible investigation areas include `ReportStore` product behavior, queue/state-transition semantics, evidence merge/replay, transaction/persistence behavior, or stale test expectations. Do not claim any one of those as the cause without new evidence.

The owner-supplied staging snapshot is therefore consistent with the live current run: the canonical blocker is presently **earlier than the Pi**, in the public hosted build/tests. Future workers must re-check current Actions and replace this classification if live evidence changes.

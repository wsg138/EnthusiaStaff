# Package validation and merge policy

## Review

Harshly review the complete final diff in every required PR for scope, architecture, lifecycle, thread safety, transactions, concurrency, idempotency, rollback, restart recovery, bounds, indexes, permissions, stale GUI state, Bedrock fallback, configuration, sensitive data, provider mismatch, tests that do not prove claims, and documentation. Resolve every valid human, CodeRabbit, Codacy, or CI finding. Require zero valid unresolved review threads.

## Gate applicability and package-contract integrity

Required acceptance gates come from the selected package contract as it existed on canonical `main` when the package was claimed, this validation policy, repository-configured checks that are actually applicable to the changed scope, and any later explicit owner instruction.

A worker may discover a real defect or a newly applicable existing gate while implementing the package and must address it. A worker must **not** create a new blocking acceptance requirement merely by editing its package file, registry entry, handoff, PR description, or other tracking text after selection. In particular, an optional or diagnostic external test does not become a merge blocker solely because a worker added it to later tracking records.

If changed executable scope genuinely triggers an existing policy requirement, that gate still applies. If the owner explicitly adds a new package gate, record the approval and apply it. If live reconciliation proves that a worker accidentally promoted a non-required diagnostic gate into a blocker, correct the package/routing records, retain every failed or incomplete diagnostic result truthfully as non-passing history, and evaluate completion against the authoritative original contract plus current policy. Never relabel the diagnostic run as a pass.

## Exact-head validation

Freeze tracked content before final validation. Evidence must apply to each exact reviewed PR head. Run every applicable repository-configured gate, including Java 21 clean build/tests, warnings-as-errors, MariaDB/Testcontainers, clean-install/upgrade/checksum migration tests, static analysis, coverage, runtime JAR creation/integrity, provider-leak checks, Wiki/Markdown/link validation, and safe Pi boot/restart when configured and applicable.

Skipped, cancelled, superseded, merge-ref-only, different-revision, queued, or missing checks are not passing evidence. Documentation-only changes may omit runtime/Pi checks only when they are genuinely non-applicable and that reason is recorded.

### Frozen product head with a state-only follow-up delta

A package may preserve executable validation from an already validated frozen product head when a later head changes **only** package-state/documentation records needed to correct or finalize orchestration. This is a narrow exception to avoid rerunning expensive executable gates for a non-executable tracking correction; it is not permission to reuse evidence across code changes.

All of the following must be true:

1. The earlier frozen product head passed every executable gate actually required for that package and its changed product scope.
2. The exact compare from that frozen product head to the later merge candidate contains only canonical package/routing/handoff/documentation files. No product source, product test, migration, workflow, build configuration, runtime configuration, dependency, artifact contract, Sentinel manifest, or other executable input may change.
3. The worker records the frozen product head and proves the later delta is state/documentation-only by exact file comparison.
4. Applicable Markdown/Wiki/package validation, static/document analysis, and review of the state-only delta pass on the later head, with zero valid unresolved review threads.
5. Every executable result remains attributed to the frozen product head; the later head is never described as having executed a gate it did not execute.
6. Any executable or test change, however small, invalidates this allowance and requires new exact-head executable validation.

This rule may be used to remove or correct a mistakenly self-added tracking gate when the authoritative package contract never required that gate and the product tree remains unchanged. It may not be used to excuse a real required failure.

## Sentinel and canonical staging

Operational procedures, exact commands, failure-phase classification, and evidence requirements are defined in `ai-agents/STAGING-TEST-OPERATING-GUIDE.md`.

Sentinel and EnthusiaStaff canonical Pi staging are independent gates. Apply whichever gate or gates the selected package requires. Neither substitutes for the other unless the selected package or this policy explicitly says so.

Classify a failure at the phase that actually executed. A public hosted build failure means no Pi runtime result exists. A queued, rejected, stale, moved-head, unauthorized, cancelled, or missing-artifact Sentinel command is not a pass. Sentinel `test restart` is successful only with terminal result `PAPER_RESTART_OK`. Canonical Pi success requires correlated private execution plus every applicable runtime, persistence/restart, guarded cleanup, process-reap, and public transfer-cleanup assertion.

Do not use Sentinel restart success as replacement evidence for canonical MariaDB/Flyway staging when canonical staging is required. Do not describe a public bridge dispatch, a private prerequisite rejection before Paper, or a cleanup failure as a canonical Pi pass.

## Owner-approved infrastructure exception

Use the evidence label `OWNER-APPROVED INFRASTRUCTURE EXCEPTION — STAGING DEFERRED`. Never label the unavailable gate `PASS`.

An owner-approved infrastructure exception is permitted only when every condition below is verified:

1. The affected gate requires an external or specialized runner or environment.
2. The job received no runner, including evidence such as `runner_id: 0`, an empty runner name, or an equivalent provider record.
3. No product build, test, server boot, restart, migration, artifact validation, or other product-validation step executed.
4. The result is therefore infrastructure-unavailable evidence rather than a product result.
5. Every executable hosted gate for the exact current PR head passes.
6. Every static-analysis finding is resolved.
7. Zero valid unresolved review threads remain.
8. The PR is mergeable and its head has not changed after validation.
9. The package is an ordinary development package. The exception is prohibited for private-validation, staging-acceptance, production-acceptance, cutover, or any package whose main purpose is proving the unavailable environment.
10. The owner explicitly approves the exception for the named package and evidence.
11. The missing staging evidence is assigned to a named later validation package.
12. Every package, registry, handoff, PR, and final-report record states that the gate was unavailable and never calls it passed.

The exception is not a successful gate, staging verification, production verification, or proof that the software booted. It cannot excuse a job that allocated a runner and then executed a failing product step. It cannot excuse compile failures, test failures, static-analysis or security findings, unresolved review findings, migration failures, runtime-JAR failures, documentation failures, or package-orchestration failures.

The record must include the exact current PR head; owner and approval source/date; reason; affected repository, workflow run, parent job when applicable, downstream job, runner ID/name, and executed step list; every exact-head hosted validation run/job; and the named deferred validation package. The record must be visible in the package file, package registry, canonical handoff, PR description, and final report.

### Non-applicability and anti-loophole rules

A zero-execution infrastructure exception cannot be used for:

- a runner that executed failing tests, build steps, migration steps, artifact checks, server boot, or restart;
- a plugin or runtime that actually failed to boot;
- a migration, security, static-analysis, review, documentation, or runtime-JAR failure;
- a private-validation package, staging-acceptance package, production-acceptance package, cutover package, or final production activation;
- issue #43, the 168-hour shadow period, final cutover, production activation, or production rollback;
- a missing ordinary GitHub-hosted build that the repository normally executes;
- a failure caused by the package's own workflow edits, runner-label edits, permissions edits, or dispatch edits;
- evidence from another head, a merge ref, a superseded run, a skipped executable hosted gate, or an unmergeable PR.

If a runner was allocated or any product step executed, the job produced product evidence and must be evaluated normally. An exception may not relabel that evidence as infrastructure-only.

## External repository validation

Each standalone repository must satisfy its own AGENTS, build, test, security, static-analysis, and review rules. The aggregate PR must satisfy EnthusiaStaff rules. Aggregate-versus-standalone parity is an additional post-merge gate, not a substitute for either repository's validation.

## Content parity

For external packages:

1. merge both cross-referenced PRs normally;
2. check out the resulting standalone default-branch head and aggregate `EnthusiaStaff:main` head;
3. compare the designated component directories using `tools/component-sync/component_sync.py`;
4. require no added, removed, or modified product file;
5. record both SHAs, hashes, manifests, and merge commits;
6. update component metadata.

If parity is false or cannot safely be calculated, set `SYNC_PENDING` or `BLOCKED`. Never force-push, rewrite history, or silently overwrite either side.

## Merge gates

An internal package is complete only after its one required PR merges and every acceptance/evidence gate passes. An external package is complete only after both required PRs merge and parity passes. Validation, acceptance, and final-audit packages follow their package-specific PR/evidence rules.

Use normal merge commits only. Do not rebase, squash, force-push, push directly to a default branch, merge a draft PR, or enable auto-merge. After merge, verify resulting heads, feature-head containment, and deletion of temporary branches when safe.

## Private and production boundaries

Never commit private databases, derived rows, credentials, raw IPs, private messages, production routes, logs, secrets, or reconstructable evidence. Hosted tests do not equal staging; staging does not equal production acceptance. LiteBans remains authoritative until issue #43 is separately completed and approved.

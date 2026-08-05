# Package validation and merge policy

## Review

Harshly review the complete final diff in every required PR for scope, architecture, lifecycle, thread safety, transactions, concurrency, idempotency, rollback, restart recovery, bounds, indexes, permissions, stale GUI state, Bedrock fallback, configuration, sensitive data, provider mismatch, tests that do not prove claims, and documentation. Resolve every valid human, CodeRabbit, Codacy, or CI finding. Require zero valid unresolved review threads.

## Exact-head validation

Freeze tracked content before final validation. Evidence must apply to each exact reviewed PR head. Run every applicable repository-configured gate, including Java 21 clean build/tests, warnings-as-errors, MariaDB/Testcontainers, clean-install/upgrade/checksum migration tests, static analysis, coverage, runtime JAR creation/integrity, provider-leak checks, Wiki/Markdown/link validation, and safe Pi boot/restart when configured and applicable.

Skipped, cancelled, superseded, merge-ref-only, different-revision, queued, or missing checks are not passing evidence. Documentation-only changes may omit runtime/Pi checks only when they are genuinely non-applicable and that reason is recorded.

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

# Latest AI handoff

Current package terminal handoff:

[`2026-08-11-es-p08-item-confiscation-complete.md`](../package-handoffs/2026-08-11-es-p08-item-confiscation-complete.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P08 — Item confiscation and restoration` is the sole current package continuation in implementation PR #128. Frozen executable-validation head `27b20bb56e540161f695e624916f91620261457d` has completed the package's required product validation.

Owner-directed reconciliation established that the later live Sentinel `PAPER_RESTART_OK` requirement was added by a worker after package selection and was not part of ES-P08's authoritative start contract. The original contract deferred representative destructive/load acceptance to `ES-V03`. Under `VALIDATION-POLICY.md`, a worker cannot manufacture a new package blocker by changing tracking text after selection.

The failed/timed-out live Sentinel jobs remain explicit non-passing diagnostic history and are not called passes. Required frozen-head evidence includes Java 21 full build/tests with MariaDB/Testcontainers, runtime-JAR/provider-leak checks, Wiki, Codacy/static/coverage, zero valid unresolved review threads, Sentinel artifact production, and canonical public→private Pi staging with exact provenance, two storage-ready Paper cycles, V1–V18 then V18 no-op restart, clean shutdown/reap, sanitized evidence, database cleanup, and public transfer cleanup.

Because blocker-publication and routing-policy work advanced `main` after the product head froze, PR #128 must be normally synchronized. Reuse of frozen executable evidence is allowed only if exact comparison proves every later change is process/state/documentation-only. Any executable/test/migration/workflow/config/dependency/artifact-contract change requires fresh executable validation.

V18 remains immutable; no ES-P08 migration was added. Issue #43 remains open/deferred and LiteBans remains authoritative. No production deployment, destructive production acceptance, cutover, source rewrite, private-data acceptance, or downstream provider work is authorized.

Finish only ES-P08: synchronize PR #128 normally, prove the state-only delta, validate/review that delta, merge PR #128 normally, verify containment/cleanup, and stop. After ES-P08 is canonical `COMPLETE`, a new sequential worker may select dependency-complete ES-X02 if live routing still agrees. ES-X01 remains parked on the unresolved supported RoseChat repository/source contract.

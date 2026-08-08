# Latest AI handoff

Current package handoff:

[`2026-08-08-es-r01-blocked-staging-database.md`](../package-handoffs/2026-08-08-es-r01-blocked-staging-database.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-R01 — Billing-independent staging bridge recovery` is the selected package and is now correctly `BLOCKED` / `PARKED_BLOCKED` once its documentation-only status publication PR #94 is merged. Repository-side bridge implementation and repairs are already merged in both required repositories. The public hosted build and exact private artifact/source provenance path have been proven, but the existing authorized disposable Pi-staging MariaDB endpoint is not accepting connections from trusted runner `Lincoln-PI-4`; guarded pre-reset exhausts bounded SQLState `08xxx` retries before Paper boot. This is not a staging pass and no validation exception is claimed.

`ES-P02` PR #70 and `ES-P05` PR #81 remain `BLOCKED` / `PARKED_BLOCKED`. Their open PRs and branch drift do not make them actionable while ES-R01 is incomplete. No product dependency was relaxed, V18 remains immutable/current, issue #43 remains open/deferred, and LiteBans remains authoritative.

The exact ES-R01 unblock condition is material evidence that the existing authorized disposable staging MariaDB endpoint is reachable again from `Lincoln-PI-4` under the current `pi-staging` environment contract. If that condition changes, the next sequential worker must resume ES-R01 first and run one fresh exact-current-main bridge proof through public hosted build, private provenance verification, guarded pre-reset, two Paper boot cycles with restart, guarded post-reset, sanitized evidence, correlated success, and transient-transfer cleanup. If the condition has not changed, no package is currently actionable or dependency-complete `READY`; report the blockers and stop rather than starting unrelated planned/deferred work.

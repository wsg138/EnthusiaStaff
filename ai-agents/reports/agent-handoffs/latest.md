# Latest AI handoff

Current package handoff:

[`2026-08-08-es-r01-pr-target-provenance-correction.md`](../package-handoffs/2026-08-08-es-r01-pr-target-provenance-correction.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-R01 — Billing-independent staging bridge recovery` remains the selected `IN_PROGRESS` / `VERIFYING` package. Repository-side bridge implementation and repairs are already merged in both required repositories. The public hosted build and exact private artifact/source provenance path have been proven. Repeated live evidence also shows that the existing authorized disposable Pi-staging MariaDB endpoint has not accepted the guarded reset connection from trusted runner `Lincoln-PI-4`; that is a blocked gate, but it is **not yet the terminal package classification**.

PR #94 / `package/es-r01-proof-retry-checkpoint` is the current documentation checkpoint. After its exact head is clean and it merges normally, the merge-triggered fresh current-`main` Pi Staging proof is mandatory before terminal status is published. If that proof succeeds through guarded pre-reset, two Paper cycles with restart, guarded post-reset, sanitized evidence, correlation, and cleanup, publish ES-R01 `COMPLETE`. If it instead confirms the same disposable-database gate with no other safe action, publish `BLOCKED` / `PARKED_BLOCKED` using that current-main evidence. A small documentation-only finalization PR may be required to persist the post-merge result.

`ES-P02` PR #70 and `ES-P05` PR #81 remain `BLOCKED` / `PARKED_BLOCKED` and are not actionable while ES-R01 is unfinished. No product dependency was relaxed, V18 remains immutable/current, issue #43 remains open/deferred, and LiteBans remains authoritative. This worker must not select or prepare a second package.

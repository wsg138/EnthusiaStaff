# Latest AI handoff

Current package handoff:

[`2026-08-07-es-p11-fake-bases.md`](../package-handoffs/2026-08-07-es-p11-fake-bases.md)

Canonical package registry:

[`PACKAGE-REGISTRY.md`](../../work-packages/PACKAGE-REGISTRY.md)

`ES-P11 — Fake-base generation and cleanup` is the selected package and remains in `REVIEW` on PR #88 / `package/es-p11-fake-bases`.

This resumed sequential worker selected ES-P11 as the only live `ACTIONABLE_CONTINUATION`. ES-P02 PR #70 and ES-P05 PR #81 remain unchanged `PARKED_BLOCKED`; exactly one package is being worked. Legitimate `main` remains `68a6d936066383f5b8139304f40b2d01d0dfe036` at the latest reconciliation, issue #43 remains deferred, LiteBans remains authoritative, and ES-P11 adds no migration beyond immutable V18.

ES-P11 implements `AUD-TESTER-003` as a bounded client-only fake base: fixed 7x7 approved template; one already-loaded target chunk; real-air/safe-floor/build-height checks; no chunk loads and no real block writes; one-target/eight-global/two-per-controller bounds; five-minute lifetime with serialized warning/Extend behavior; target plus successful-Teleport staff viewer isolation; idempotent authoritative real-block re-send cleanup; coordinate-free durable audit through existing `audit_events`; and `/cheattester base` command/text fallback with explicit least-privilege permissions.

Harsh self-review and the actual CodeRabbit review have been reconciled. Fixes include exact admission, canonical JSON, Folia/thread-affinity safety, stale async authority, accepted-vs-committed evidence, scheduler failure, render/close cleanup, warning/extension serialization, viewer documentation, handoff routing, permission inheritance, template-height coverage, and stronger metadata/integration assertions. Zero valid unresolved review threads remain at this checkpoint.

Product/review hardening is complete through `1d8502b2e68fe7d400d1981d964d51dbb00417d7`; this package-state checkpoint is intended to become the frozen exact head unless a gate finds a valid defect. Earlier green runs are diagnostic only after a head change.

A fresh automatic private staging attempt for `1d8502b2e68fe7d400d1981d964d51dbb00417d7` again allocated no runner and executed no steps: private run `31238324044`, Ubuntu job `93054868171` runner ID `0` / empty runner name / `steps: []`, Pi `93054872292` skipped, with GitHub's unchanged Billing & plans rejection. This is **NOT A PASS** and is not an ES-P11 completion gate; representative Java/Bedrock/distributed acceptance remains assigned to ES-V02.

Next: freeze this post-checkpoint head, require exact-head Wiki/full Java/MariaDB/Testcontainers/migration/runtime-JAR/coverage/Codacy/CodeRabbit gates and zero valid threads, reconcile fresh `main`, merge PR #88 normally, verify exact containment and implementation-branch deletion, publish terminal `COMPLETE` state through the established documentation-only finalization pattern, and stop. Do not activate another package.
